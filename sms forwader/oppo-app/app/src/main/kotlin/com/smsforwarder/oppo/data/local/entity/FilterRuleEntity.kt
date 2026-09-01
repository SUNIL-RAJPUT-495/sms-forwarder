package com.smsforwarder.oppo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted SMS filter rule configured by the user.
 */
@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** FilterRuleType.name */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis()
)
