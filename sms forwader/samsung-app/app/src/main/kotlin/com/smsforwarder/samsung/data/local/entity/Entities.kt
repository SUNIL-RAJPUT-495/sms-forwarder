package com.smsforwarder.samsung.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locally stored forwarded message on Samsung.
 *
 * SECURITY: This table contains decrypted SMS bodies. It is stored
 * only on the Samsung device's local Room database and is NEVER
 * sent to any server. The database is excluded from cloud backup.
 *
 * If additional at-rest encryption is required (e.g., SQLCipher),
 * that will be evaluated in Phase 8 Security Hardening.
 */
@Entity(
    tableName = "forwarded_messages",
    indices = [Index(value = ["message_id"], unique = true)]
)
data class ForwardedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "original_timestamp_ms")
    val originalTimestampMs: Long,

    @ColumnInfo(name = "received_at_ms")
    val receivedAtMs: Long,

    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String = "DECRYPTED"
)

/**
 * Seen message IDs — used for deduplication.
 * When a duplicate FCM is delivered (FCM guarantees at-least-once),
 * we check this table and discard re-processed messages.
 */
@Entity(
    tableName = "seen_message_ids",
    indices = [Index(value = ["message_id"], unique = true)]
)
data class SeenMessageIdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "seen_at_ms")
    val seenAtMs: Long = System.currentTimeMillis()
)
