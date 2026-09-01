package com.smsforwarder.oppo.crypto

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.smsforwarder.oppo.BuildConfig
import com.smsforwarder.oppo.domain.model.EncryptedOutboundMessage
import com.smsforwarder.oppo.domain.model.SmsMessageData
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * OPPO-side cryptographic engine.
 *
 * Encrypts an [SmsMessageData] using hybrid encryption:
 *   - AES-256-GCM for the SMS body (symmetric, authenticated)
 *   - RSA-2048-OAEP-SHA256 for the AES key (asymmetric, using Samsung's public key)
 *
 * ─────────────────────────────────────────────
 * ENCRYPTION ALGORITHM DETAIL:
 *
 *   1. Load Samsung's RSA-2048 public key (PEM, stored in EncryptedSharedPreferences
 *      during pairing). Parse via X509EncodedKeySpec.
 *
 *   2. Generate a fresh 256-bit AES key using SecureRandom (per-message ephemeral key).
 *      NEVER reuse AES keys.
 *
 *   3. Generate a fresh 12-byte (96-bit) GCM nonce using SecureRandom.
 *      NEVER reuse nonce+key pairs (GCM is catastrophically broken on nonce reuse).
 *
 *   4. Build the AAD byte array:
 *      "v{protocolVersion}|{messageId}|{sourceDeviceId}|{destinationDeviceId}|{timestamp}"
 *      This binds the ciphertext to its metadata. If any field is modified in transit,
 *      AES-GCM authentication will fail on Samsung.
 *
 *   5. Serialize the SMS to plaintext wire format:
 *      "{sender}\n{body}"
 *
 *   6. Encrypt with AES-256-GCM:
 *      Cipher("AES/GCM/NoPadding") with the AES key, nonce, and AAD.
 *      Output includes the 128-bit GCM auth tag appended to the ciphertext.
 *
 *   7. Wrap the AES key with Samsung's RSA public key using OAEP:
 *      Cipher("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
 *
 *   8. Base64-encode all byte array outputs and package into [EncryptedOutboundMessage].
 *
 *   9. Zero-fill the AES key bytes before returning.
 *
 * ─────────────────────────────────────────────
 * SHARED PREFERENCES KEYS:
 *   PREF_DEST_PUBLIC_KEY  — Samsung's RSA public key PEM
 *   PREF_SOURCE_DEVICE_ID — OPPO's registered device ID
 *   PREF_DEST_DEVICE_ID   — Samsung's registered device ID
 *
 * These are set by the pairing flow (Phase 5) via completePairing API response.
 */
@Singleton
class OppoCryptoEngine @Inject constructor(
    @Named("encrypted") private val prefs: SharedPreferences
) {

    companion object {
        private const val TAG = "OppoCryptoEngine"

        const val AAD_VERSION = BuildConfig.PROTOCOL_VERSION

        internal const val RSA_CIPHER     = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        internal const val AES_CIPHER     = "AES/GCM/NoPadding"
        internal const val RSA_ALGORITHM  = "RSA"

        internal const val AES_KEY_BITS   = 256
        internal const val GCM_TAG_LEN    = 128 // bits
        internal const val NONCE_BYTES    = 12

        const val PREF_DEST_PUBLIC_KEY  = "dest_public_key_pem"
        const val PREF_SOURCE_DEVICE_ID = "source_device_id"
        const val PREF_DEST_DEVICE_ID   = "dest_device_id"

        // PEM header/footer for stripping before Base64 decode
        private const val PEM_HEADER = "-----BEGIN PUBLIC KEY-----"
        private const val PEM_FOOTER = "-----END PUBLIC KEY-----"
    }

    private val secureRandom = SecureRandom()

    /**
     * Returns true if the destination public key is available (i.e. device is paired).
     * Call this before [encrypt] to provide meaningful UI feedback.
     */
    fun isReadyToEncrypt(): Boolean {
        val pem = prefs.getString(PREF_DEST_PUBLIC_KEY, null)
        val srcId = prefs.getString(PREF_SOURCE_DEVICE_ID, null)
        val dstId = prefs.getString(PREF_DEST_DEVICE_ID, null)
        return !pem.isNullOrBlank() && !srcId.isNullOrBlank() && !dstId.isNullOrBlank()
    }

    /**
     * Encrypt an SMS message for delivery to Samsung.
     *
     * @param smsData The incoming SMS to encrypt. [SmsMessageData.body] is
     *   never written to storage — only the returned [EncryptedOutboundMessage] is.
     *
     * @return [EncryptedOutboundMessage] containing ONLY ciphertext fields.
     *
     * @throws EncryptionException if the device is not paired, or if any
     *   crypto operation fails. The exception message contains NO plaintext.
     */
    fun encrypt(smsData: SmsMessageData): EncryptedOutboundMessage {
        // ─── Prerequisite: pairing state ─────────────────────────
        val destPublicKeyPem  = prefs.getString(PREF_DEST_PUBLIC_KEY, null)
            ?: throw EncryptionException("No destination public key — device not paired")
        val sourceDeviceId    = prefs.getString(PREF_SOURCE_DEVICE_ID, null)
            ?: throw EncryptionException("No source device ID — device not registered")
        val destinationDeviceId = prefs.getString(PREF_DEST_DEVICE_ID, null)
            ?: throw EncryptionException("No destination device ID — device not paired")

        Log.d(TAG, "Encrypting message id=${smsData.messageId.take(8)}…")

        // ─── 1. Parse Samsung's RSA public key ───────────────────
        val rsaPublicKey = try {
            parsePemPublicKey(destPublicKeyPem)
        } catch (e: Exception) {
            throw EncryptionException("Failed to parse destination public key", e)
        }

        // ─── 2. Generate ephemeral AES-256 key ───────────────────
        val aesKey: ByteArray = try {
            KeyGenerator.getInstance("AES").apply {
                init(AES_KEY_BITS, secureRandom)
            }.generateKey().encoded
        } catch (e: Exception) {
            throw EncryptionException("AES key generation failed", e)
        }

        try {
            // ─── 3. Generate random 12-byte GCM nonce ────────────
            val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }

            // ─── 4. Build AAD ─────────────────────────────────────
            val aad = buildAad(
                protocolVersion     = AAD_VERSION,
                messageId           = smsData.messageId,
                sourceDeviceId      = sourceDeviceId,
                destinationDeviceId = destinationDeviceId,
                timestamp           = smsData.timestampMs
            )

            // ─── 5. Serialise SMS to wire format ──────────────────
            // Format: "{sender}\n{body}"
            // The sender field is always a single-line alpha/numeric ID.
            val plaintext = "${smsData.sender}\n${smsData.body}".toByteArray(Charsets.UTF_8)

            // ─── 6. AES-256-GCM encrypt ───────────────────────────
            val ciphertextWithTag = try {
                Cipher.getInstance(AES_CIPHER).apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LEN, nonce))
                    updateAAD(aad)
                }.doFinal(plaintext)
            } catch (e: Exception) {
                throw EncryptionException("AES-GCM encryption failed", e)
            } finally {
                plaintext.fill(0) // Zero-fill plaintext buffer immediately
            }

            // ─── 7. RSA-OAEP wrap AES key ────────────────────────
            val wrappedAesKey = try {
                Cipher.getInstance(RSA_CIPHER).apply {
                    init(
                        Cipher.ENCRYPT_MODE,
                        rsaPublicKey,
                        OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT
                        )
                    )
                }.doFinal(aesKey)
            } catch (e: Exception) {
                throw EncryptionException("RSA key wrap failed", e)
            }

            // ─── 8. Base64-encode and package ─────────────────────
            Log.i(TAG, "Encryption successful — id=${smsData.messageId.take(8)}")

            return EncryptedOutboundMessage(
                messageId           = smsData.messageId,
                sourceDeviceId      = sourceDeviceId,
                destinationDeviceId = destinationDeviceId,
                protocolVersion     = AAD_VERSION,
                timestamp           = smsData.timestampMs,
                encryptedKey        = Base64.encodeToString(wrappedAesKey,     Base64.NO_WRAP),
                nonce               = Base64.encodeToString(nonce,             Base64.NO_WRAP),
                ciphertext          = Base64.encodeToString(ciphertextWithTag, Base64.NO_WRAP)
            )

        } finally {
            // ─── 9. Zero AES key ──────────────────────────────────
            aesKey.fill(0)
        }
    }

    // ─────────────────────────────────────────────
    // Internal helpers — also used in CryptoEngineTest via package-private access
    // ─────────────────────────────────────────────

    /**
     * Build the Additional Authenticated Data (AAD) byte array.
     *
     * CRITICAL: This formula is mirrored EXACTLY in [SamsungCryptoEngine.buildAad].
     * Any divergence breaks authentication on every message.
     */
    internal fun buildAad(
        protocolVersion: Int,
        messageId: String,
        sourceDeviceId: String,
        destinationDeviceId: String,
        timestamp: Long
    ): ByteArray = "v$protocolVersion|$messageId|$sourceDeviceId|$destinationDeviceId|$timestamp"
        .toByteArray(Charsets.UTF_8)

    /**
     * Parse a PEM-encoded RSA public key (SubjectPublicKeyInfo DER in Base64).
     *
     * Strips PEM headers, decodes Base64, and constructs a [java.security.PublicKey]
     * via [X509EncodedKeySpec].
     */
    internal fun parsePemPublicKey(pem: String): java.security.PublicKey {
        val b64 = pem
            .lines()
            .filter { !it.startsWith("-----") && it.isNotBlank() }
            .joinToString("")
        val derBytes = Base64.decode(b64, Base64.NO_WRAP)
        return java.security.KeyFactory.getInstance(RSA_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(derBytes))
    }
}

class EncryptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
