package com.smsforwarder.app.filter

import android.content.Context
import androidx.work.*
import com.smsforwarder.app.crypto.UniversalCryptoEngine
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.data.repository.MessageRepository
import com.smsforwarder.app.domain.model.SmsMessageData
import com.smsforwarder.app.network.ApiService
import com.smsforwarder.app.worker.DrainQueueWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ForwardingResult {
    data class Success(val messageId: String, val matchedRule: String) : ForwardingResult()
    data class QueuedOffline(val messageId: String, val reason: String) : ForwardingResult()
    data class FilteredOut(val reason: String) : ForwardingResult()
    data class Error(val message: String) : ForwardingResult()
}

@Singleton
class SmsForwardingPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val filterEngine: SmsFilterEngine,
    private val cryptoEngine: UniversalCryptoEngine,
    private val deviceRepository: DeviceRepository,
    private val messageRepository: MessageRepository,
    private val apiService: ApiService
) {

    suspend fun processAndForward(sms: SmsMessageData): ForwardingResult {
        // 1. Evaluate SMS filter rules
        val eval = filterEngine.evaluate(sms)
        if (!eval.shouldForward) {
            return ForwardingResult.FilteredOut(eval.rejectionReason ?: "Filtered out")
        }

        // 2. Check pairing state
        val deviceInfo = deviceRepository.deviceInfoFlow.first()
        if (!deviceInfo.isPaired || deviceInfo.pairedDeviceId == null) {
            return ForwardingResult.Error("Device is not paired with any destination receiver")
        }

        val destPublicKey = deviceRepository.getPairedPublicKey()
        if (destPublicKey == null) {
            return ForwardingResult.Error("No public key found for paired destination")
        }

        // 3. Encrypt payload
        val encryptedMessage = try {
            cryptoEngine.encryptOutboundMessage(
                sms = sms,
                sourceDeviceId = deviceInfo.deviceId,
                destinationDeviceId = deviceInfo.pairedDeviceId,
                destinationPublicKeyPem = destPublicKey
            )
        } catch (e: Exception) {
            return ForwardingResult.Error("Encryption failed: ${e.message}")
        }

        // 4. Attempt immediate network send
        return try {
            val response = apiService.sendMessage(encryptedMessage)
            if (response.isSuccessful && response.body()?.accepted == true) {
                ForwardingResult.Success(
                    messageId = encryptedMessage.messageId,
                    matchedRule = eval.matchedRuleName ?: "Rule match"
                )
            } else {
                // Queue for offline retry
                messageRepository.queueOutboundMessage(
                    encryptedMessage,
                    "Server response ${response.code()}: ${response.message()}"
                )
                scheduleQueueDrain()
                ForwardingResult.QueuedOffline(
                    messageId = encryptedMessage.messageId,
                    reason = "Server returned ${response.code()}, queued for retry"
                )
            }
        } catch (e: Exception) {
            // Network error — queue for retry
            messageRepository.queueOutboundMessage(encryptedMessage, e.message)
            scheduleQueueDrain()
            ForwardingResult.QueuedOffline(
                messageId = encryptedMessage.messageId,
                reason = "Network unreachable, queued offline: ${e.localizedMessage}"
            )
        }
    }

    private fun scheduleQueueDrain() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val drainWork = OneTimeWorkRequestBuilder<DrainQueueWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "drain_sms_queue_worker",
            ExistingWorkPolicy.REPLACE,
            drainWork
        )
    }
}
