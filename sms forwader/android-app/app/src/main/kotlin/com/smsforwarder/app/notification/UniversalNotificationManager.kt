package com.smsforwarder.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smsforwarder.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_INCOMING_SMS = "channel_incoming_forwarded_sms"
        const val CHANNEL_PAIRING = "channel_pairing_events"
        const val CHANNEL_SERVICE = "channel_relay_service"

        const val ACTION_COPY_OTP = "com.smsforwarder.app.ACTION_COPY_OTP"
        const val ACTION_COPY_BODY = "com.smsforwarder.app.ACTION_COPY_BODY"
        const val EXTRA_OTP = "extra_otp_text"
        const val EXTRA_BODY = "extra_body_text"

        const val SERVICE_NOTIFICATION_ID = 1001
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Incoming SMS Heads-up Channel
            val smsChannel = NotificationChannel(
                CHANNEL_INCOMING_SMS,
                "Forwarded SMS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent bank SMS & OTP notifications forwarded from paired devices"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Pairing channel
            val pairingChannel = NotificationChannel(
                CHANNEL_PAIRING,
                "Pairing Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pairing success and device connection alerts"
            }

            // Foreground service keep-alive channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "SMS Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SMS forwarder running reliably in the background"
                setShowBadge(false)
            }

            nm.createNotificationChannels(listOf(smsChannel, pairingChannel, serviceChannel))
        }
    }

    /**
     * Builds the persistent notification for the SMS forwarder foreground service.
     */
    fun buildServiceNotification(): Notification {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("SMS Relay Active")
            .setContentText("Monitoring & encrypting incoming bank SMS")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Shows a heads-up notification for an incoming decrypted SMS.
     */
    fun showDecryptedSmsNotification(
        messageId: String,
        sender: String,
        body: String,
        extractedOtp: String?,
        sourceDeviceName: String
    ) {
        val notifId = messageId.hashCode()

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context, notifId, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (extractedOtp != null) {
            "🔑 OTP: $extractedOtp ($sender)"
        } else {
            "📩 SMS from $sender"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING_SMS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setSummaryText("Forwarded from $sourceDeviceName")
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        // Add 1-tap "Copy OTP" action if OTP is present
        if (extractedOtp != null) {
            val copyOtpIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_COPY_OTP
                putExtra(EXTRA_OTP, extractedOtp)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val copyOtpPendingIntent = PendingIntent.getActivity(
                context, notifId + 1, copyOtpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy $extractedOtp",
                copyOtpPendingIntent
            )
        }

        nm.notify(notifId, builder.build())
    }

    /**
     * Shows pairing completion notification.
     */
    fun showPairingSuccessNotification(sourceDeviceName: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_PAIRING)
            .setContentTitle("Pairing Complete 🎉")
            .setContentText("Successfully connected with $sourceDeviceName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(2001, builder.build())
    }
}
