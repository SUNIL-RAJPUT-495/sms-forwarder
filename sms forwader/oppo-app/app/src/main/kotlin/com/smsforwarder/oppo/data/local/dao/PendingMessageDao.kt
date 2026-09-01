package com.smsforwarder.oppo.data.local.dao

import androidx.room.*
import com.smsforwarder.oppo.data.local.entity.PendingMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: PendingMessageEntity): Long

    @Query("SELECT * FROM pending_messages WHERE status = 'PENDING' OR status = 'IN_FLIGHT' ORDER BY created_at_ms ASC")
    fun observePending(): Flow<List<PendingMessageEntity>>

    @Query("SELECT * FROM pending_messages WHERE status = 'PENDING' ORDER BY created_at_ms ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 20): List<PendingMessageEntity>

    @Query("UPDATE pending_messages SET status = :status, retry_count = retry_count + 1 WHERE message_id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("DELETE FROM pending_messages WHERE message_id = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    /** Delete expired messages that will never be delivered. */
    @Query("DELETE FROM pending_messages WHERE expires_at_ms < :nowMs")
    suspend fun deleteExpired(nowMs: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}
