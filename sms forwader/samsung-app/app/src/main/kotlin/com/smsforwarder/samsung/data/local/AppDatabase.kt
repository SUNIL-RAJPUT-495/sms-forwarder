package com.smsforwarder.samsung.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smsforwarder.samsung.data.local.dao.ForwardedMessageDao
import com.smsforwarder.samsung.data.local.dao.SeenMessageIdDao
import com.smsforwarder.samsung.data.local.entity.ForwardedMessageEntity
import com.smsforwarder.samsung.data.local.entity.SeenMessageIdEntity

@Database(
    entities = [
        ForwardedMessageEntity::class,
        SeenMessageIdEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun forwardedMessageDao(): ForwardedMessageDao
    abstract fun seenMessageIdDao(): SeenMessageIdDao

    companion object {
        const val DATABASE_NAME = "sms_forwarder_samsung.db"
    }
}
