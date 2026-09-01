package com.smsforwarder.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.network.ApiService
import com.smsforwarder.app.network.DirectSmsRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var apiService: ApiService

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        // Ignore system ongoing notifications or app's own foreground notification
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: packageName
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (text.isBlank()) return

        Log.d("NotificationListener", "Captured notification from $title: $text")

        serviceScope.launch {
            try {
                apiService.sendDirectSms(
                    DirectSmsRequest(
                        sender = title,
                        body = text,
                        timestamp = System.currentTimeMillis().toString()
                    )
                )
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to relay notification: ${e.message}")
            }
        }
    }
}
