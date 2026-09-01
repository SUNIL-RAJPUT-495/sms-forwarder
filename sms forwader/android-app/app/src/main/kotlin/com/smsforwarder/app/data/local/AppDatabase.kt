package com.smsforwarder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smsforwarder.app.data.local.dao.FilterRuleDao
import com.smsforwarder.app.data.local.dao.InboundMessageDao
import com.smsforwarder.app.data.local.dao.PairedDeviceDao
import com.smsforwarder.app.data.local.dao.PendingMessageDao
import com.smsforwarder.app.data.local.entity.FilterRuleEntity
import com.smsforwarder.app.data.local.entity.InboundMessageEntity
import com.smsforwarder.app.data.local.entity.PairedDeviceEntity
import com.smsforwarder.app.data.local.entity.PendingMessageEntity

@Database(
    entities = [
        FilterRuleEntity::class,
        PendingMessageEntity::class,
        InboundMessageEntity::class,
        PairedDeviceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun inboundMessageDao(): InboundMessageDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
}
