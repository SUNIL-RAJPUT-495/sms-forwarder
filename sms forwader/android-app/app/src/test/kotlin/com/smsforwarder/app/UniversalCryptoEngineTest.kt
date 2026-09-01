package com.smsforwarder.app

import android.util.Base64
import com.smsforwarder.app.crypto.KeyManager
import com.smsforwarder.app.crypto.UniversalCryptoEngine
import com.smsforwarder.app.domain.model.EncryptedInboundPayload
import com.smsforwarder.app.domain.model.SmsMessageData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64 as JavaBase64

class UniversalCryptoEngineTest {

    private lateinit var keyPair: KeyPair
    private lateinit var keyManager: KeyManager
    private lateinit var cryptoEngine: UniversalCryptoEngine

    @Before
    fun setup() {
        // Mock android.util.Base64 with standard java.util.Base64 for JVM unit tests
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getDecoder().decode(firstArg<String>())
        }

        // Generate RSA-2048 test keypair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        keyPair = kpg.generateKeyPair()

        keyManager = mockk(relaxed = true)
        every { keyManager.getPrivateKey() } returns keyPair.private
        every { keyManager.getPublicKey() } returns keyPair.public

        val pubKeyBase64 = JavaBase64.getEncoder().encodeToString(keyPair.public.encoded)
        val pubKeyPem = "-----BEGIN PUBLIC KEY-----\n$pubKeyBase64\n-----END PUBLIC KEY-----"
        every { keyManager.getPublicKeyPem() } returns pubKeyPem

        cryptoEngine = UniversalCryptoEngine(keyManager)
    }

    @Test
    fun `encrypt and decrypt message round-trip succeeds with identical content`() {
        val rawSms = SmsMessageData(
            sender = "HDFCBK",
            body = "Your OTP for Rs 4,500.00 at Flipkart is 839201. Valid for 10 mins.",
            timestampMs = System.currentTimeMillis()
        )

        val sourceId = "pixel_device_01"
        val destinationId = "samsung_device_02"
        val pubKeyPem = keyManager.getPublicKeyPem()

        // Encrypt on Sender
        val outbound = cryptoEngine.encryptOutboundMessage(
            sms = rawSms,
            sourceDeviceId = sourceId,
            destinationDeviceId = destinationId,
            destinationPublicKeyPem = pubKeyPem
        )

        assertNotNull(outbound.messageId)
        assertNotNull(outbound.encryptedKey)
        assertNotNull(outbound.nonce)
        assertNotNull(outbound.ciphertext)

        // Decrypt on Receiver
        val inboundPayload = EncryptedInboundPayload(
            messageId = outbound.messageId,
            sourceDeviceId = outbound.sourceDeviceId,
            destinationDeviceId = outbound.destinationDeviceId,
            protocolVersion = outbound.protocolVersion,
            timestamp = outbound.timestamp,
            encryptedKey = outbound.encryptedKey,
            nonce = outbound.nonce,
            ciphertext = outbound.ciphertext
        )

        val (decryptedSender, decryptedBody) = cryptoEngine.decryptInboundPayload(inboundPayload)

        assertEquals("HDFCBK", decryptedSender)
        assertEquals(rawSms.body, decryptedBody)
        assertTrue(decryptedBody.contains("839201"))
    }

    @Test
    fun `AAD formula matches exact wire format`() {
        val aad = cryptoEngine.buildAad(1, "msg_123", "src_456", "dest_789", 1700000000000L)
        assertEquals("v1|msg_123|src_456|dest_789|1700000000000", aad)
    }
}
