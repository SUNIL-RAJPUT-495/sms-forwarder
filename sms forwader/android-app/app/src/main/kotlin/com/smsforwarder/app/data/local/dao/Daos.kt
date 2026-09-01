package com.smsforwarder.app.data.local.dao

import androidx.room.*
import com.smsforwarder.app.data.local.entity.FilterRuleEntity
import com.smsforwarder.app.data.local.entity.InboundMessageEntity
import com.smsforwarder.app.data.local.entity.PairedDeviceEntity
import com.smsforwarder.app.data.local.entity.PendingMessageEntity
import com.smsforwarder.app.domain.model.ForwardStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun getActiveRules(): List<FilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FilterRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<FilterRuleEntity>)

    @Update
    suspend fun updateRule(rule: FilterRuleEntity)

    @Delete
    suspend fun deleteRule(rule: FilterRuleEntity)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT COUNT(*) FROM filter_rules")
    suspend fun getRuleCount(): Int
}

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages WHERE status = 'PENDING' AND nextRetryAtMs <= :currentTime ORDER BY createdAtMs ASC LIMIT :limit")
    suspend fun getMessagesToDrain(currentTime: Long = System.currentTimeMillis(), limit: Int = 20): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages ORDER BY createdAtMs DESC")
    fun getAllPendingMessages(): Flow<List<PendingMessageEntity>>

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: PendingMessageEntity): Long

    @Update
    suspend fun updateMessage(message: PendingMessageEntity)

    @Query("UPDATE pending_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: ForwardStatus)

    @Query("DELETE FROM pending_messages WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("DELETE FROM pending_messages WHERE status = 'SENT' AND createdAtMs < :olderThanMs")
    suspend fun cleanOldSentMessages(olderThanMs: Long)
}

@Dao
interface InboundMessageDao {
    @Query("SELECT * FROM inbound_messages ORDER BY receivedTimestampMs DESC")
    fun getAllMessages(): Flow<List<InboundMessageEntity>>

    @Query("SELECT * FROM inbound_messages WHERE sender LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' ORDER BY receivedTimestampMs DESC")
    fun searchMessages(query: String): Flow<List<InboundMessageEntity>>

    @Query("SELECT * FROM inbound_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): InboundMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: InboundMessageEntity): Long

    @Query("UPDATE inbound_messages SET isCopied = 1 WHERE messageId = :messageId")
    suspend fun markCopied(messageId: String)

    @Query("DELETE FROM inbound_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM inbound_messages")
    suspend fun clearAll()
}

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices WHERE isActive = 1")
    fun getActivePairedDevices(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): PairedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PairedDeviceEntity)

    @Update
    suspend fun updateDevice(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun removeDevice(deviceId: String)

    @Query("DELETE FROM paired_devices")
    suspend fun clearAll()
}
