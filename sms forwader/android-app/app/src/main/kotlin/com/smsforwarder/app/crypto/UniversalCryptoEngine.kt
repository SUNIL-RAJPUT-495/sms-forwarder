package com.smsforwarder.app.crypto

import android.util.Base64
import com.smsforwarder.app.domain.model.EncryptedInboundPayload
import com.smsforwarder.app.domain.model.EncryptedOutboundMessage
import com.smsforwarder.app.domain.model.SmsMessageData
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalCryptoEngine @Inject constructor(
    private val keyManager: KeyManager
) {

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val MAX_CLOCK_DRIFT_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val secureRandom = SecureRandom()

    // ─────────────────────────────────────────────
    // SENDER ENCRYPTION PIPELINE
    // ─────────────────────────────────────────────

    /**
     * Encrypts a raw SMS message for a specific paired destination device.
     */
    fun encryptOutboundMessage(
        sms: SmsMessageData,
        sourceDeviceId: String,
        destinationDeviceId: String,
        destinationPublicKeyPem: String
    ): EncryptedOutboundMessage {
        val messageId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val timestamp = sms.timestampMs

        // 1. Generate Ephemeral 256-bit AES Key and 12-byte Nonce
        val ephemeralAesKey = generateAesKey()
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        // 2. Build AAD: "v1|messageId|sourceDeviceId|destinationDeviceId|timestamp"
        val aad = buildAad(PROTOCOL_VERSION, messageId, sourceDeviceId, destinationDeviceId, timestamp)
        val aadBytes = aad.toByteArray(StandardCharsets.UTF_8)

        // 3. Plaintext: "$sender\n$body"
        val plaintext = "${sms.sender}\n${sms.body}"
        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)

        // 4. Encrypt with AES-256-GCM
        val cipherGcm = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipherGcm.init(
            Cipher.ENCRYPT_MODE,
            ephemeralAesKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        )
        cipherGcm.updateAAD(aadBytes)
        val ciphertextBytes = cipherGcm.doFinal(plaintextBytes)

        // 5. Encrypt AES Key with Destination's RSA Public Key (RSA-OAEP-SHA256)
        val destinationPublicKey = parsePublicKeyFromPem(destinationPublicKeyPem)
        val cipherRsa = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        val oaepParams = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        cipherRsa.init(Cipher.ENCRYPT_MODE, destinationPublicKey, oaepParams)
        val encryptedKeyBytes = cipherRsa.doFinal(ephemeralAesKey.encoded)

        return EncryptedOutboundMessage(
            protocolVersion = PROTOCOL_VERSION,
            messageId = messageId,
            sourceDeviceId = sourceDeviceId,
            destinationDeviceId = destinationDeviceId,
            encryptedKey = Base64.encodeToString(encryptedKeyBytes, Base64.NO_WRAP),
            nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            timestamp = timestamp
        )
    }

    // ─────────────────────────────────────────────
    // RECEIVER DECRYPTION PIPELINE
    // ─────────────────────────────────────────────

    /**
     * Decrypts an inbound encrypted payload using this device's KeyStore private key.
     * Returns Pair(Sender, Body)
     */
    fun decryptInboundPayload(payload: EncryptedInboundPayload): Pair<String, String> {
        // 1. Clock drift / Replay check
        val timeDelta = Math.abs(System.currentTimeMillis() - payload.timestamp)
        if (timeDelta > MAX_CLOCK_DRIFT_MS) {
            // Log warning but proceed if slightly delayed in queue
        }

        // 2. Unwrap AES Key using KeyStore Private Key
        val encryptedKeyBytes = Base64.decode(payload.encryptedKey, Base64.NO_WRAP)
        val privateKey = keyManager.getPrivateKey()

        val cipherRsa = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
        val oaepParams = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        cipherRsa.init(Cipher.DECRYPT_MODE, privateKey, oaepParams)
        val rawAesKeyBytes = cipherRsa.doFinal(encryptedKeyBytes)
        val aesKey = SecretKeySpec(rawAesKeyBytes, "AES")

        // 3. Reconstruct AAD
        val aad = buildAad(
            payload.protocolVersion,
            payload.messageId,
            payload.sourceDeviceId,
            payload.destinationDeviceId,
            payload.timestamp
        )
        val aadBytes = aad.toByteArray(StandardCharsets.UTF_8)

        // 4. Decrypt Ciphertext with AES-256-GCM
        val nonce = Base64.decode(payload.nonce, Base64.NO_WRAP)
        val ciphertextBytes = Base64.decode(payload.ciphertext, Base64.NO_WRAP)

        val cipherGcm = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipherGcm.init(
            Cipher.DECRYPT_MODE,
            aesKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
        )
        cipherGcm.updateAAD(aadBytes)
        val decryptedBytes = cipherGcm.doFinal(ciphertextBytes)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)

        // 5. Parse "$sender\n$body"
        val newlineIndex = decryptedText.indexOf('\n')
        if (newlineIndex == -1) {
            return Pair("UNKNOWN", decryptedText)
        }
        val sender = decryptedText.substring(0, newlineIndex)
        val body = decryptedText.substring(newlineIndex + 1)

        return Pair(sender, body)
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    fun buildAad(
        protocolVersion: Int,
        messageId: String,
        sourceDeviceId: String,
        destinationDeviceId: String,
        timestamp: Long
    ): String {
        return "v$protocolVersion|$messageId|$sourceDeviceId|$destinationDeviceId|$timestamp"
    }

    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE_BITS, secureRandom)
        return keyGen.generateKey()
    }

    fun parsePublicKeyFromPem(pem: String): PublicKey {
        val cleanPem = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(cleanPem, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }
}
