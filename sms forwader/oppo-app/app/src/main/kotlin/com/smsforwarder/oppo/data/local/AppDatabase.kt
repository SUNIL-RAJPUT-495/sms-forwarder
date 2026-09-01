package com.smsforwarder.oppo.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.smsforwarder.oppo.data.local.dao.FilterRuleDao
import com.smsforwarder.oppo.data.local.dao.PendingMessageDao
import com.smsforwarder.oppo.data.local.entity.FilterRuleEntity
import com.smsforwarder.oppo.data.local.entity.PendingMessageEntity

/**
 * Room database for the OPPO app.
 *
 * Tables:
 *  - [PendingMessageEntity]: encrypted messages awaiting delivery.
 *    NEVER contains plaintext SMS.
 *  - [FilterRuleEntity]: user-configured SMS filter rules.
 *
 * The database file itself is NOT encrypted at rest in Phase 2
 * (pending messages contain only ciphertext, so exposure risk is low).
 * Phase 8 security hardening will evaluate SQLCipher if required.
 */
@Database(
    entities = [
        PendingMessageEntity::class,
        FilterRuleEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun filterRuleDao(): FilterRuleDao

    companion object {
        const val DATABASE_NAME = "sms_forwarder_oppo.db"
    }
}
