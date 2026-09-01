package com.smsforwarder.samsung.crypto

import android.util.Base64
import android.util.Log
import com.smsforwarder.samsung.data.local.dao.SeenMessageIdDao
import com.smsforwarder.samsung.data.local.entity.SeenMessageIdEntity
import com.smsforwarder.samsung.domain.model.EncryptedInboundPayload
import com.smsforwarder.samsung.domain.model.ForwardedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung-side cryptographic engine.
 *
 * Decrypts incoming [EncryptedInboundPayload] messages using the device's
 * RSA-2048 private key (stored in the Android Keystore) to recover the
 * ephemeral AES-256-GCM session key, then decrypts the ciphertext.
 *
 * ─────────────────────────────────────────────
 * ALGORITHM SUMMARY:
 *
 *   1. Decode all Base64 fields from the payload.
 *   2. Build the AAD string (same formula as OPPO's CryptoEngine).
 *      AAD = "v{protocolVersion}|{messageId}|{sourceDeviceId}|{destinationDeviceId}|{timestamp}"
 *      This binds the ciphertext to its metadata — tampering any field
 *      causes AES-GCM authentication to fail.
 *   3. Unwrap the AES session key using RSA-OAEP-SHA256:
 *      Cipher("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
 *      with the private key from Keystore.
 *   4. Decrypt the ciphertext using AES-256-GCM with the recovered key,
 *      the nonce, and the AAD.
 *   5. Deserialise the plaintext back to [ForwardedMessage].
 *
 * SECURITY INVARIANTS:
 *   - Duplicate messageIds are detected via [SeenMessageIdDao] BEFORE decryption.
 *   - Timestamp staleness is checked BEFORE decryption (replay protection).
 *   - Decryption failures throw [DecryptionException] with NO plaintext in the message.
 *   - The GCM auth tag automatically detects any tampering of encrypted_key, nonce,
 *     ciphertext, or AAD fields.
 *   - If authentication fails (GCM tag mismatch), a [DecryptionException] is thrown
 *     and the message is discarded. It is NOT stored.
 *
 * ─────────────────────────────────────────────
 * THREAD SAFETY:
 *   All operations run on [Dispatchers.Default] (CPU-bound). The Keystore
 *   RSA operation is internally synchronised by the Keystore service.
 */
@Singleton
class SamsungCryptoEngine @Inject constructor(
    private val keyManager: KeyManager,
    private val seenMessageIdDao: SeenMessageIdDao
) {

    companion object {
        private const val TAG = "SamsungCryptoEngine"

        /** Must match OppoCryptoEngine.AAD_VERSION exactly. */
        const val AAD_VERSION = 1

        // Cipher algorithm strings — must match OppoCryptoEngine exactly.
        internal const val RSA_CIPHER   = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        internal const val AES_CIPHER   = "AES/GCM/NoPadding"

        internal const val AES_KEY_SIZE = 256 // bits
        internal const val GCM_TAG_LEN  = 128 // bits
        internal const val NONCE_BYTES  = 12

        /** Maximum clock drift allowed between OPPO and Samsung (ms). */
        private const val MAX_TIMESTAMP_DRIFT_MS = 5L * 60 * 1000 // 5 minutes
    }

    /**
     * Decrypt an inbound encrypted message.
     *
     * @param payload The FCM-delivered encrypted payload.
     * @param expectedDestinationDeviceId Samsung's registered device ID.
     *   Used to reject messages not intended for this device.
     *
     * @return The decrypted [ForwardedMessage] ready for storage + notification.
     * @throws DecryptionException if any security check or crypto operation fails.
     */
    suspend fun decrypt(
        payload: EncryptedInboundPayload,
        expectedDestinationDeviceId: String
    ): ForwardedMessage = withContext(Dispatchers.Default) {

        // ─── 1. Destination device ID check ──────────────────────
        if (payload.destinationDeviceId != expectedDestinationDeviceId) {
            throw DecryptionException("Destination device ID mismatch — possible misdirected message")
        }

        // ─── 2. Timestamp validation (replay protection) ─────────
        val ageMs = System.currentTimeMillis() - payload.timestamp
        if (Math.abs(ageMs) > MAX_TIMESTAMP_DRIFT_MS) {
            throw DecryptionException("Message timestamp outside ±5 min window — rejected as stale/future")
        }

        // ─── 3. Deduplication check ───────────────────────────────
        if (seenMessageIdDao.isAlreadySeen(payload.messageId)) {
            throw DuplicateMessageException("Duplicate messageId: ${payload.messageId.take(8)}…")
        }

        // ─── 4. Protocol version check ────────────────────────────
        if (payload.protocolVersion != AAD_VERSION) {
            throw DecryptionException("Unsupported protocol version: ${payload.protocolVersion}")
        }

        // ─── 5. Decode Base64 payload fields ─────────────────────
        val encryptedKeyBytes = decodeBase64(payload.encryptedKey, "encryptedKey")
        val nonceBytes        = decodeBase64(payload.nonce,         "nonce")
        val ciphertextBytes   = decodeBase64(payload.ciphertext,    "ciphertext")

        if (nonceBytes.size != NONCE_BYTES) {
            throw DecryptionException("Invalid nonce length: ${nonceBytes.size} (expected $NONCE_BYTES)")
        }

        // ─── 6. Build AAD (must match OppoCryptoEngine exactly) ──
        val aad = buildAad(
            protocolVersion   = payload.protocolVersion,
            messageId         = payload.messageId,
            sourceDeviceId    = payload.sourceDeviceId,
            destinationDeviceId = payload.destinationDeviceId,
            timestamp         = payload.timestamp
        )

        // ─── 7. Unwrap AES session key via RSA-OAEP ───────────────
        val aesKey = try {
            val privateKey = keyManager.getPrivateKey()
            val cipher = Cipher.getInstance(RSA_CIPHER).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    privateKey,
                    OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                    )
                )
            }
            cipher.doFinal(encryptedKeyBytes)
        } catch (e: Exception) {
            // SECURITY: suppress exception message — may contain key material details
            Log.e(TAG, "RSA key unwrap failed")
            throw DecryptionException("RSA decryption failed", e)
        }

        if (aesKey.size * 8 != AES_KEY_SIZE) {
            throw DecryptionException("Recovered AES key has wrong size: ${aesKey.size * 8} bits")
        }

        // ─── 8. Decrypt ciphertext with AES-256-GCM + AAD ────────
        val plaintextBytes = try {
            val secretKey = SecretKeySpec(aesKey, "AES")
            val gcmSpec   = GCMParameterSpec(GCM_TAG_LEN, nonceBytes)

            Cipher.getInstance(AES_CIPHER).apply {
                init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                updateAAD(aad)
            }.doFinal(ciphertextBytes)
        } catch (e: Exception) {
            // SECURITY: This typically means the ciphertext was tampered with,
            // or AAD mismatched. Do NOT log the exception message.
            Log.e(TAG, "AES-GCM decryption failed — possible tampering")
            throw DecryptionException("AES-GCM authentication/decryption failed", e)
        } finally {
            // Zero out the AES key bytes to minimise key exposure in memory
            aesKey.fill(0)
        }

        // ─── 9. Deserialise decrypted payload ─────────────────────
        // Plaintext format: "SENDER\nBODY" (pipe-delimited for safety)
        val plaintext = plaintextBytes.toString(Charsets.UTF_8)
        val (sender, body) = parsePlaintext(plaintext)

        // ─── 10. Mark as seen (idempotency) ──────────────────────
        seenMessageIdDao.markSeen(SeenMessageIdEntity(messageId = payload.messageId))

        ForwardedMessage(
            messageId          = payload.messageId,
            sender             = sender,
            body               = body,
            originalTimestampMs = payload.timestamp,
            receivedAtMs       = System.currentTimeMillis()
        )
    }

    // ─────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────

    /**
     * Build the Additional Authenticated Data (AAD) byte array.
     *
     * CRITICAL: This formula must be IDENTICAL to [OppoCryptoEngine.buildAad].
     * Any difference causes AES-GCM authentication to fail on every message.
     *
     * Format: "v{version}|{messageId}|{sourceDeviceId}|{destDeviceId}|{timestamp}"
     */
    internal fun buildAad(
        protocolVersion: Int,
        messageId: String,
        sourceDeviceId: String,
        destinationDeviceId: String,
        timestamp: Long
    ): ByteArray = "v$protocolVersion|$messageId|$sourceDeviceId|$destinationDeviceId|$timestamp"
        .toByteArray(Charsets.UTF_8)

    private fun decodeBase64(value: String, fieldName: String): ByteArray {
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw DecryptionException("Invalid Base64 in field '$fieldName'", e)
        }
    }

    /**
     * Parse the plaintext byte array back into sender + body.
     *
     * Wire format: "{sender}\n{body}"
     * The sender cannot contain newlines (it's an alpha/numeric ID).
     * The body is everything after the first newline.
     */
    private fun parsePlaintext(plaintext: String): Pair<String, String> {
        val idx = plaintext.indexOf('\n')
        if (idx == -1) throw DecryptionException("Invalid plaintext format — missing separator")
        val sender = plaintext.substring(0, idx)
        val body   = plaintext.substring(idx + 1)
        if (sender.isBlank()) throw DecryptionException("Invalid plaintext format — empty sender")
        if (body.isBlank())   throw DecryptionException("Invalid plaintext format — empty body")
        return Pair(sender, body)
    }
}

// ─────────────────────────────────────────────
// Exception types
// ─────────────────────────────────────────────

/**
 * Thrown when decryption fails for any reason.
 *
 * SECURITY: The [message] field MUST NOT contain any plaintext SMS content.
 * Use only descriptions of what failed, not the values involved.
 */
class DecryptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Thrown when a duplicate messageId is detected (FCM at-least-once delivery).
 * This is NOT an error — it's an expected, safe condition.
 */
class DuplicateMessageException(message: String) : Exception(message)
