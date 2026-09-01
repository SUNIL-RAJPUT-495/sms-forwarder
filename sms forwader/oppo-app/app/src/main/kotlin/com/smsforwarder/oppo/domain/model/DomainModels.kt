package com.smsforwarder.oppo.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a single SMS message received on the OPPO device.
 *
 * Multipart SMS segments are merged before this object is created.
 * The [messageId] is a UUID generated at receive time and used as
 * an idempotency key throughout the system.
 *
 * IMPORTANT: This object is never persisted in plaintext. It is
 * immediately encrypted into [EncryptedOutboundMessage] before any
 * storage or network operation.
 */
@Serializable
data class SmsMessageData(
    /** UUID generated on OPPO when SMS arrives. Used for dedup end-to-end. */
    val messageId: String,
    /** The sender address (alpha or numeric). e.g. "SBIINB", "VM-HDFCBK", "+919876543210" */
    val sender: String,
    /** Full merged message body (multipart segments concatenated in order). */
    val body: String,
    /** Epoch milliseconds at which the SMS was received. */
    val timestampMs: Long
)

/**
 * Filter rule defining which incoming SMS messages should be forwarded.
 *
 * Rules are evaluated with OR logic across the enabled rule set:
 * a message is forwarded if ANY enabled rule matches.
 */
data class FilterRule(
    val id: Long = 0,
    val type: FilterRuleType,
    /** The value to match against. */
    val value: String,
    val enabled: Boolean = true
)

enum class FilterRuleType {
    /** Exact match on sender address (case-insensitive, strips VM-/VD- prefix). */
    EXACT_SENDER,
    /** Sender address contains this value (case-insensitive). */
    SENDER_CONTAINS,
    /** Message body contains this keyword (case-insensitive). */
    BODY_CONTAINS
}

/**
 * Current pairing/connection state between OPPO and Samsung.
 */
data class PairingState(
    val isPaired: Boolean = false,
    val sourceDeviceId: String? = null,
    val sourceDeviceApiKey: String? = null,
    val destinationDeviceId: String? = null,
    val destinationPublicKeyPem: String? = null,
    val destinationDeviceName: String? = null,
    val pairedAt: Long? = null
)

/**
 * Encrypted message payload ready to be sent to the backend.
 * Contains NO plaintext. The [ciphertext] and [encryptedKey] can
 * only be decrypted by the Samsung device's private key.
 */
@Serializable
data class EncryptedOutboundMessage(
    val messageId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String,
    val protocolVersion: Int,
    val timestamp: Long,
    /** Base64-encoded RSA-OAEP wrapped AES-256 key. */
    val encryptedKey: String,
    /** Base64-encoded 12-byte GCM nonce/IV. */
    val nonce: String,
    /** Base64-encoded AES-256-GCM ciphertext + auth tag. */
    val ciphertext: String
)

/**
 * Forwarding status for an individual message in the outbound queue.
 */
enum class ForwardStatus {
    PENDING,
    IN_FLIGHT,
    DELIVERED,
    FAILED,
    EXPIRED
}
