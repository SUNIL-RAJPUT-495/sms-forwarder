package com.smsforwarder.oppo.data.local.dao

import androidx.room.*
import com.smsforwarder.oppo.data.local.entity.FilterRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {

    @Query("SELECT * FROM filter_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabled(): List<FilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: FilterRuleEntity): Long

    @Update
    suspend fun update(rule: FilterRuleEntity)

    @Delete
    suspend fun delete(rule: FilterRuleEntity)

    @Query("UPDATE filter_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM filter_rules")
    suspend fun deleteAll()
}
