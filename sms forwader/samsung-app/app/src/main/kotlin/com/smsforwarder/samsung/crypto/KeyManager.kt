package com.smsforwarder.samsung.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Samsung device's RSA-2048 key pair in the Android Keystore.
 *
 * ─────────────────────────────────────────────
 * KEY DESIGN:
 *
 * - The PRIVATE KEY never leaves the Keystore. It is used only via the
 *   Keystore API (no raw key bytes are ever accessible).
 *
 * - The PUBLIC KEY is exported as a PEM-encoded SubjectPublicKeyInfo
 *   string and shared with the backend during device registration.
 *   The backend delivers it to OPPO during pairing.
 *
 * - Key Purpose: DECRYPT only (we only unwrap AES keys with the RSA key).
 *   This is enforced by the Keystore's KeyGenParameterSpec.
 *
 * - Key Size: RSA-2048. Sufficient for personal use. RSA-4096 would
 *   be more future-proof but significantly slower on Android.
 *
 * - Extraction: KeyProperties.PURPOSE_DECRYPT without
 *   setIsStrongBoxBacked — StrongBox availability varies across Samsung
 *   models and would require fallback logic. TEE provides adequate
 *   protection for this personal-use application.
 *
 * - Key re-generation: Call [deleteKeyPair] + [ensureKeyPairExists].
 *   This is a breaking operation — all existing encrypted messages
 *   (pending or in-flight) become undecryptable. The user must
 *   re-pair after key rotation. (Phase 8 will surface this in UI.)
 *
 * KEYSTORE_ALIAS is stable across app updates (not tied to any
 * secret). It identifies the key entry in the system Keystore.
 */
@Singleton
class KeyManager @Inject constructor() {

    companion object {
        const val KEYSTORE_ALIAS   = "sms_forwarder_samsung_rsa_v1"
        private const val KEYSTORE  = "AndroidKeyStore"
        private const val KEY_SIZE  = 2048
        private const val TAG       = "SamsungKeyManager"

        // PEM delimiters for SubjectPublicKeyInfo DER encoded in Base64
        private const val PEM_HEADER = "-----BEGIN PUBLIC KEY-----"
        private const val PEM_FOOTER = "-----END PUBLIC KEY-----"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
    }

    /**
     * Ensure the RSA key pair exists in the Keystore.
     *
     * If the key pair already exists (from a previous app launch), this
     * is a no-op. Safe to call on every app startup.
     *
     * @throws KeyGenerationException if key generation fails.
     */
    fun ensureKeyPairExists() {
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            Log.d(TAG, "Key pair already exists in Keystore")
            return
        }

        Log.i(TAG, "Generating RSA-$KEY_SIZE key pair in Keystore…")

        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE
            )

            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_DECRYPT // Only for unwrapping AES keys
            )
                .setKeySize(KEY_SIZE)
                .setAlgorithmParameterSpec(
                    RSAKeyGenParameterSpec(KEY_SIZE, RSAKeyGenParameterSpec.F4)
                )
                // OAEP with SHA-256 digest and MGF1 with SHA-256
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                // Key never leaves Keystore in any form
                .setUnlockedDeviceRequired(false) // Allow decryption when device is locked
                                                  // (needed for background FCM processing)
                .build()

            keyPairGenerator.initialize(keyGenSpec)
            keyPairGenerator.generateKeyPair()

            Log.i(TAG, "RSA key pair generated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Key pair generation failed")
            throw KeyGenerationException("Failed to generate RSA key pair", e)
        }
    }

    /**
     * Retrieve Samsung's RSA private key from the Keystore.
     *
     * The key object is a handle — no raw bytes are accessible.
     * Cryptographic operations are performed inside the Keystore via JCA.
     *
     * @throws IllegalStateException if the key does not exist.
     */
    fun getPrivateKey(): PrivateKey {
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null)
            as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException(
                "RSA private key not found in Keystore — call ensureKeyPairExists() first"
            )
        return entry.privateKey
    }

    /**
     * Retrieve Samsung's RSA public key.
     *
     * @throws IllegalStateException if the key does not exist.
     */
    fun getPublicKey(): PublicKey {
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null)
            as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException(
                "RSA key pair not found in Keystore — call ensureKeyPairExists() first"
            )
        return entry.certificate.publicKey
    }

    /**
     * Export Samsung's RSA public key as a PEM-encoded string.
     *
     * The PEM format is SubjectPublicKeyInfo (DER) encoded in Base64,
     * wrapped with standard PEM headers. This is compatible with
     * Java's X509EncodedKeySpec on the OPPO side.
     *
     * This value is shared with the backend during device registration
     * and forwarded to OPPO during pairing. It is NOT secret.
     */
    fun getPublicKeyPem(): String {
        val publicKey = getPublicKey()
        val b64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return buildString {
            appendLine(PEM_HEADER)
            // Insert line breaks every 64 characters (PEM standard)
            b64.chunked(64).forEach { appendLine(it) }
            append(PEM_FOOTER)
        }
    }

    /**
     * Delete the key pair from the Keystore.
     *
     * ⚠ DESTRUCTIVE: Any messages encrypted with the corresponding
     * public key become permanently undecryptable. Use only for
     * key rotation (Phase 8) with explicit user confirmation.
     */
    fun deleteKeyPair() {
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
            Log.i(TAG, "RSA key pair deleted from Keystore")
        }
    }

    /** Returns true if the key pair exists and is ready for use. */
    fun hasKeyPair(): Boolean = keyStore.containsAlias(KEYSTORE_ALIAS)
}

class KeyGenerationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
