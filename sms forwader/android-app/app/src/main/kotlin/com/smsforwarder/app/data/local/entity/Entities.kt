package com.smsforwarder.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smsforwarder.app.domain.model.FilterRuleType
import com.smsforwarder.app.domain.model.ForwardStatus

/**
 * Filter rules for determining which SMS to forward.
 */
@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: FilterRuleType,
    val pattern: String,
    val isEnabled: Boolean = true,
    val extractOtp: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Outbound pending message queue for offline resiliency.
 */
@Entity(
    tableName = "pending_messages",
    indices = [Index(value = ["messageId"], unique = true)]
)
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val payloadJson: String,
    val attemptCount: Int = 0,
    val nextRetryAtMs: Long = System.currentTimeMillis(),
    val status: ForwardStatus = ForwardStatus.PENDING,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastError: String? = null
)

/**
 * Inbound decrypted message history on Receiver / Dual device.
 */
@Entity(
    tableName = "inbound_messages",
    indices = [Index(value = ["messageId"], unique = true)]
)
data class InboundMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val sender: String,
    val body: String,
    val extractedOtp: String?,
    val originalTimestampMs: Long,
    val receivedTimestampMs: Long = System.currentTimeMillis(),
    val sourceDeviceName: String,
    val isCopied: Boolean = false
)

/**
 * Paired remote devices.
 */
@Entity(
    tableName = "paired_devices",
    indices = [Index(value = ["deviceId"], unique = true)]
)
data class PairedDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val publicKeyPem: String? = null,
    val pairedAtMs: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
