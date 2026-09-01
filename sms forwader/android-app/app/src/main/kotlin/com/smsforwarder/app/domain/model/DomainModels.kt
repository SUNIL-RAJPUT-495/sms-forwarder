package com.smsforwarder.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Operating mode of this device.
 */
enum class DeviceRole {
    /** Captures incoming SMS and forwards encrypted payloads to paired devices. */
    SENDER,
    /** Receives encrypted push notifications via FCM, decrypts them, and alerts the user. */
    RECEIVER,
    /** Dual mode: can both forward SIM SMS and receive forwarded SMS from another device. */
    DUAL
}

/**
 * Raw SMS message captured from Telephony receiver.
 */
data class SmsMessageData(
    val sender: String,
    val body: String,
    val timestampMs: Long,
    val subscriptionId: Int = -1,
    val slotIndex: Int = 0
)

/**
 * SMS filter rule configuration.
 */
data class FilterRule(
    val id: Long = 0,
    val name: String,
    val type: FilterRuleType,
    val pattern: String,
    val isEnabled: Boolean = true,
    val extractOtp: Boolean = true
)

enum class FilterRuleType {
    SENDER_MATCH,
    KEYWORD_CONTAINS,
    REGEX_BODY,
    MIN_LENGTH
}

/**
 * Outbound encrypted message payload sent to the Firebase backend.
 */
@Serializable
data class EncryptedOutboundMessage(
    val protocolVersion: Int = 1,
    val messageId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String,
    val encryptedKey: String,
    val nonce: String,
    val ciphertext: String,
    val timestamp: Long
)

/**
 * Inbound encrypted payload received from FCM.
 */
@Serializable
data class EncryptedInboundPayload(
    val messageId: String,
    val sourceDeviceId: String,
    val destinationDeviceId: String,
    val protocolVersion: Int,
    val timestamp: Long,
    val encryptedKey: String,
    val nonce: String,
    val ciphertext: String
)

/**
 * Decrypted SMS item displayed in local history.
 */
data class InboundMessage(
    val id: Long = 0,
    val messageId: String,
    val sender: String,
    val body: String,
    val extractedOtp: String? = null,
    val originalTimestampMs: Long,
    val receivedTimestampMs: Long,
    val sourceDeviceName: String,
    val isCopied: Boolean = false
)

/**
 * Status of an outbound message in the local queue.
 */
enum class ForwardStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

/**
 * Device metadata & pairing state.
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val departmentName: String = "",
    val mobileNumber: String = "",
    val address: String = "",
    val role: DeviceRole,
    val isRegistered: Boolean,
    val isPaired: Boolean,
    val pairedDeviceName: String? = null,
    val pairedDeviceId: String? = null
)
