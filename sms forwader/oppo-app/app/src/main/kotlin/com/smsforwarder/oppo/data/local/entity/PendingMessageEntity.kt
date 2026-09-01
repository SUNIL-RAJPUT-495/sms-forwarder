package com.smsforwarder.oppo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encrypted message waiting to be delivered to the backend.
 *
 * SECURITY: This table contains ONLY ciphertext. The [encryptedKey]
 * and [ciphertext] fields cannot be decrypted without Samsung's
 * private key. Plaintext SMS body is NEVER stored here.
 */
@Entity(
    tableName = "pending_messages",
    indices = [Index(value = ["message_id"], unique = true)]
)
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "source_device_id")
    val sourceDeviceId: String,

    @ColumnInfo(name = "destination_device_id")
    val destinationDeviceId: String,

    @ColumnInfo(name = "protocol_version")
    val protocolVersion: Int,

    /** Epoch ms when original SMS was received. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    /** Base64-encoded RSA-OAEP wrapped AES-256 key. */
    @ColumnInfo(name = "encrypted_key")
    val encryptedKey: String,

    /** Base64-encoded 12-byte GCM nonce. */
    @ColumnInfo(name = "nonce")
    val nonce: String,

    /** Base64-encoded AES-GCM ciphertext + auth tag. */
    @ColumnInfo(name = "ciphertext")
    val ciphertext: String,

    @ColumnInfo(name = "status")
    val status: String = "PENDING",    // ForwardStatus.name

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    /** After this epoch ms, the message should be abandoned. */
    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long
)
