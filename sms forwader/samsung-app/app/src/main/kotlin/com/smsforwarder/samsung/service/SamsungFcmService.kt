package com.smsforwarder.samsung.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smsforwarder.samsung.MainActivity
import com.smsforwarder.samsung.crypto.DecryptionException
import com.smsforwarder.samsung.crypto.DuplicateMessageException
import com.smsforwarder.samsung.crypto.SamsungCryptoEngine
import com.smsforwarder.samsung.data.local.dao.ForwardedMessageDao
import com.smsforwarder.samsung.data.local.entity.ForwardedMessageEntity
import com.smsforwarder.samsung.domain.model.EncryptedInboundPayload
import com.smsforwarder.samsung.domain.model.NotificationMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named

/**
 * Firebase Cloud Messaging service for the Samsung (Destination) app.
 *
 * Phase 4: Fully wired to [SamsungCryptoEngine] for real E2E decryption.
 *
 * FLOW:
 *   1. FCM delivers an ENCRYPTED_SMS data message.
 *   2. Parse the payload JSON → [EncryptedInboundPayload].
 *   3. Call [SamsungCryptoEngine.decrypt]:
 *      - Validates destination device ID
 *      - Validates timestamp (replay protection ±5 min)
 *      - Checks deduplication table
 *      - RSA-OAEP unwraps AES key (from Keystore)
 *      - AES-256-GCM decrypts + authenticates
 *   4. Store decrypted [ForwardedMessageEntity] in local Room DB.
 *   5. Show notification (mode controlled by [NotificationMode] preference).
 *   6. Phase 6 will schedule an ACK back to the backend.
 *
 * SECURITY:
 *   - Duplicate messages: silently ignored (logged at DEBUG level only).
 *   - Decryption failures: logged at ERROR without any payload content.
 *   - The notification content is the ONLY place plaintext appears on Samsung.
 *     The Room DB entry stores plaintext body for the History screen.
 *
 * NOTIFICATION MODE (preference key: PREF_NOTIFICATION_MODE):
 *   FULL_MESSAGE  — Shows sender + full body in notification
 *   SENDER_ONLY   — Shows "Bank SMS from SBIINB" (no body)
 *   MINIMAL       — Shows "New bank SMS received"
 */
@AndroidEntryPoint
class SamsungFcmService : FirebaseMessagingService() {

    @Inject lateinit var cryptoEngine: SamsungCryptoEngine
    @Inject lateinit var messageDao: ForwardedMessageDao
    @Inject @Named("encrypted") lateinit var prefs: SharedPreferences
    @Inject lateinit var json: kotlinx.serialization.json.Json

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val messageType = data["type"] ?: "ENCRYPTED_SMS"

        Log.d(TAG, "FCM received — type: $messageType")

        when (messageType) {
            "ENCRYPTED_SMS"    -> handleEncryptedSms(data)
            "PAIRING_COMPLETE" -> handlePairingComplete(data)
            "PAIRING_REVOKED"  -> handlePairingRevoked()
            else               -> Log.w(TAG, "Unknown FCM message type: $messageType")
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed — updating backend (Phase 6)")
        // Phase 6: DeviceRepository.updateFcmToken(token)
    }

    // ─────────────────────────────────────────────
    // ENCRYPTED_SMS handler
    // ─────────────────────────────────────────────

    private fun handleEncryptedSms(data: Map<String, String>) {
        serviceScope.launch {
            try {
                val payload = parseEncryptedPayload(data) ?: run {
                    Log.w(TAG, "Failed to parse encrypted payload from FCM data — discarded")
                    return@launch
                }

                Log.d(TAG, "Processing encrypted message id=${payload.messageId.take(8)}…")

                // Load this device's ID from pairing state
                val myDeviceId = prefs.getString(PREF_DEST_DEVICE_ID, null) ?: run {
                    Log.e(TAG, "Destination device ID not found in preferences — cannot validate")
                    return@launch
                }

                // ─── Decrypt ───────────────────────────────────────────
                val forwardedMessage = try {
                    cryptoEngine.decrypt(payload, myDeviceId)
                } catch (e: DuplicateMessageException) {
                    Log.d(TAG, "Duplicate message ignored: id=${payload.messageId.take(8)}")
                    return@launch
                } catch (e: DecryptionException) {
                    // SECURITY: log that it failed, but NOT the exception message
                    // (which may contain details about WHY — could hint at metadata)
                    Log.e(TAG, "Decryption failed — message discarded")
                    return@launch
                }

                Log.i(TAG, "Decryption successful: id=${forwardedMessage.messageId.take(8)} sender=${forwardedMessage.sender.take(4)}***")

                // ─── Persist to local history ──────────────────────────
                // This is the ONLY place plaintext body is written to storage on Samsung.
                val entity = ForwardedMessageEntity(
                    messageId           = forwardedMessage.messageId,
                    sender              = forwardedMessage.sender,
                    body                = forwardedMessage.body,
                    originalTimestampMs = forwardedMessage.originalTimestampMs,
                    receivedAtMs        = forwardedMessage.receivedAtMs,
                    deliveryStatus      = "ACK_PENDING"
                )
                messageDao.insert(entity)

                // ─── Show notification ────────────────────────────────
                val notifMode = loadNotificationMode()
                showNotification(
                    notificationId = forwardedMessage.messageId.hashCode(),
                    sender         = forwardedMessage.sender,
                    body           = forwardedMessage.body,
                    mode           = notifMode
                )

                // Phase 6: schedule AcknowledgementWorker to POST /acknowledgeMessage

            } catch (e: Exception) {
                // SECURITY: never log exception details — may contain plaintext
                Log.e(TAG, "Unexpected error in FCM handler (details suppressed)")
            }
        }
    }

    // ─────────────────────────────────────────────
    // Pairing handlers
    // ─────────────────────────────────────────────

    private fun handlePairingComplete(data: Map<String, String>) {
        Log.i(TAG, "Pairing complete FCM received")
        // Phase 5: update pairing state in DataStore, show in-app confirmation
    }

    private fun handlePairingRevoked() {
        Log.i(TAG, "Pairing revoked FCM received — clearing local pairing state")
        // Phase 5: clear pairing state, show user notification
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private fun parseEncryptedPayload(data: Map<String, String>): EncryptedInboundPayload? {
        return try {
            val payloadJson = data["payload"] ?: return null
            json.decodeFromString<EncryptedInboundPayload>(payloadJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse FCM payload JSON")
            null
        }
    }

    private fun loadNotificationMode(): NotificationMode {
        val raw = prefs.getString(PREF_NOTIFICATION_MODE, NotificationMode.FULL_MESSAGE.name)
        return try {
            NotificationMode.valueOf(raw ?: NotificationMode.FULL_MESSAGE.name)
        } catch (_: Exception) {
            NotificationMode.FULL_MESSAGE
        }
    }

    private fun showNotification(
        notificationId: Int,
        sender: String,
        body: String,
        mode: NotificationMode
    ) {
        createNotificationChannel()

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, contentText) = when (mode) {
            NotificationMode.FULL_MESSAGE -> Pair(
                "Bank SMS — $sender",
                body
            )
            NotificationMode.SENDER_ONLY -> Pair(
                "Bank SMS",
                "New message from $sender"
            )
            NotificationMode.MINIMAL -> Pair(
                "Bank SMS",
                "New bank SMS received"
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .apply {
                // Only BigTextStyle for FULL_MESSAGE mode
                if (mode == NotificationMode.FULL_MESSAGE) {
                    setStyle(NotificationCompat.BigTextStyle().bigText(body))
                }
            }
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            ?.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bank SMS", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Forwarded bank SMS messages" }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG                  = "SamsungFcmService"
        const val CHANNEL_ID                   = "bank_sms_channel"
        const val PREF_DEST_DEVICE_ID          = "dest_device_id"
        const val PREF_NOTIFICATION_MODE       = "notification_mode"
    }
}
