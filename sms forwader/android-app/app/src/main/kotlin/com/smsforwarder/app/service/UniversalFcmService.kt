package com.smsforwarder.app.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smsforwarder.app.crypto.UniversalCryptoEngine
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.data.repository.MessageRepository
import com.smsforwarder.app.domain.model.EncryptedInboundPayload
import com.smsforwarder.app.filter.SmsFilterEngine
import com.smsforwarder.app.network.AcknowledgeRequest
import com.smsforwarder.app.network.ApiService
import com.smsforwarder.app.notification.UniversalNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class UniversalFcmService : FirebaseMessagingService() {

    @Inject
    lateinit var cryptoEngine: UniversalCryptoEngine

    @Inject
    lateinit var filterEngine: SmsFilterEngine

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var notificationManager: UniversalNotificationManager

    @Inject
    lateinit var apiService: ApiService

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            // Re-register if device is already registered
            val info = deviceRepository.deviceInfoFlow.first()
            if (info.isRegistered) {
                deviceRepository.registerDevice()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "ENCRYPTED_SMS" -> {
                val payloadJson = data["payload"] ?: return
                handleEncryptedSms(payloadJson)
            }
            "PAIRING_COMPLETE" -> {
                val sourceDeviceId = data["sourceDeviceId"] ?: return
                val sourceDeviceName = data["sourceDeviceName"] ?: "Paired Sender Device"
                handlePairingComplete(sourceDeviceId, sourceDeviceName)
            }
            "PAIRING_REVOKED" -> {
                serviceScope.launch {
                    deviceRepository.unpair()
                }
            }
        }
    }

    private fun handleEncryptedSms(payloadJson: String) {
        serviceScope.launch {
            try {
                val payload = Json.decodeFromString<EncryptedInboundPayload>(payloadJson)

                // 1. Decrypt ciphertext
                val (sender, body) = cryptoEngine.decryptInboundPayload(payload)

                // 2. Extract OTP if present
                val extractedOtp = filterEngine.extractOtp(body)

                // 3. Get source device name
                val deviceInfo = deviceRepository.deviceInfoFlow.first()
                val sourceName = deviceInfo.pairedDeviceName ?: "Paired Sender"

                // 4. Save to local Room database
                messageRepository.saveInboundMessage(
                    messageId = payload.messageId,
                    sender = sender,
                    body = body,
                    extractedOtp = extractedOtp,
                    originalTimestampMs = payload.timestamp,
                    sourceDeviceName = sourceName
                )

                // 5. Trigger heads-up notification
                notificationManager.showDecryptedSmsNotification(
                    messageId = payload.messageId,
                    sender = sender,
                    body = body,
                    extractedOtp = extractedOtp,
                    sourceDeviceName = sourceName
                )

                // 6. Send delivery ACK back to relay
                runCatching {
                    apiService.acknowledgeMessage(AcknowledgeRequest(payload.messageId))
                }
            } catch (e: Exception) {
                // Log decryption / parsing error
            }
        }
    }

    private fun handlePairingComplete(sourceDeviceId: String, sourceDeviceName: String) {
        serviceScope.launch {
            deviceRepository.savePairedDevice(
                pairedDeviceId = sourceDeviceId,
                pairedDeviceName = sourceDeviceName
            )
            notificationManager.showPairingSuccessNotification(sourceDeviceName)
        }
    }
}
