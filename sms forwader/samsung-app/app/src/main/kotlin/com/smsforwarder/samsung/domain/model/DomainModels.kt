package com.smsforwarder.samsung.domain.model

import kotlinx.serialization.Serializable

/**
 * A forwarded SMS message as stored locally on Samsung.
 * Populated after successful decryption of an FCM payload.
 * This is the ONLY place plaintext SMS exists on the Samsung device —
 * in memory and local Room DB. It is never sent to any server.
 */
data class ForwardedMessage(
    val id: Long = 0,
    val messageId: String,          // UUID from OPPO (idempotency key)
    val sender: String,             // e.g. "SBIINB"
    val body: String,               // Decrypted SMS body
    val originalTimestampMs: Long,  // When SMS arrived on OPPO
    val receivedAtMs: Long,         // When Samsung received + decrypted it
    val deliveryStatus: DeliveryStatus = DeliveryStatus.DECRYPTED
)

enum class DeliveryStatus {
    DECRYPTED,      // Decrypted successfully
    ACK_PENDING,    // Waiting to send acknowledgement to backend
    ACKNOWLEDGED,   // Backend ACK sent successfully
    DUPLICATE       // Duplicate messageId — discarded
}

/**
 * Encrypted FCM payload from the backend.
 * Fields mirror [EncryptedOutboundMessage] on the OPPO side.
 */
@Serializable
data class EncryptedInboundPayload(
    val messageId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String,
    val protocolVersion: Int,
    val timestamp: Long,
    val encryptedKey: String,   // Base64 RSA-OAEP wrapped AES key
    val nonce: String,          // Base64 12-byte GCM nonce
    val ciphertext: String      // Base64 AES-GCM ciphertext + auth tag
)

/**
 * Notification privacy mode — controls how much of the message
 * is shown in the Android notification.
 */
enum class NotificationMode {
    FULL_MESSAGE,   // Show full SMS body in notification
    SENDER_ONLY,    // Show "Bank SMS from SBIINB" — no body
    MINIMAL         // Show only "New bank SMS received"
}
