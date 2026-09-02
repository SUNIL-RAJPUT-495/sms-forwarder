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
import kotlinx.coroutines.flow.first
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
        // Ignore app's own foreground notification
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()
            ?: extras.getCharSequence("android.conversationTitle")?.toString()
            ?: packageName

        val text = extras.getCharSequence("android.text")?.toString()
            ?: extras.getCharSequence("android.subText")?.toString()
            ?: extras.getCharSequence("android.bigText")?.toString()
            ?: sbn.notification?.tickerText?.toString()
            ?: ""

        if (text.isBlank()) return

        Log.d("NotificationListener", "Captured notification from $title: $text")

        serviceScope.launch {
            try {
                val info = deviceRepository.deviceInfoFlow.first()
                val request = DirectSmsRequest(
                    deviceId = if (info.deviceId.isNotBlank()) info.deviceId else "DEV-${System.currentTimeMillis()}",
                    departmentName = info.departmentName.ifBlank { info.deviceName.ifBlank { "Department Phone" } },
                    mobileNumber = info.mobileNumber.ifBlank { "N/A" },
                    address = info.address.ifBlank { "Main Office" },
                    sender = title,
                    body = text,
                    timestamp = System.currentTimeMillis().toString()
                )
                val response = apiService.sendDirectSms(request)
                if (response.isSuccessful) {
                    Log.d("NotificationListener", "Successfully forwarded notification to backend: ${response.code()}")
                } else {
                    Log.e("NotificationListener", "Backend returned error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to relay notification: ${e.message}")
            }
        }
    }
}
