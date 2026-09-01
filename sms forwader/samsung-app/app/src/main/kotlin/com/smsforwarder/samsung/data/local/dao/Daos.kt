package com.smsforwarder.samsung.data.local.dao

import androidx.room.*
import com.smsforwarder.samsung.data.local.entity.ForwardedMessageEntity
import com.smsforwarder.samsung.data.local.entity.SeenMessageIdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardedMessageDao {

    @Query("SELECT * FROM forwarded_messages ORDER BY received_at_ms DESC")
    fun observeAll(): Flow<List<ForwardedMessageEntity>>

    @Query("SELECT * FROM forwarded_messages ORDER BY received_at_ms DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ForwardedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ForwardedMessageEntity): Long

    @Query("UPDATE forwarded_messages SET delivery_status = :status WHERE message_id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("DELETE FROM forwarded_messages WHERE received_at_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM forwarded_messages")
    fun observeCount(): Flow<Int>
}

@Dao
interface SeenMessageIdDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(entity: SeenMessageIdEntity): Long

    @Query("SELECT COUNT(*) > 0 FROM seen_message_ids WHERE message_id = :messageId")
    suspend fun isAlreadySeen(messageId: String): Boolean

    /** Housekeeping: remove IDs older than 7 days to bound table size. */
    @Query("DELETE FROM seen_message_ids WHERE seen_at_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
