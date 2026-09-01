package com.smsforwarder.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsforwarder.app.data.local.dao.PendingMessageDao
import com.smsforwarder.app.domain.model.EncryptedOutboundMessage
import com.smsforwarder.app.domain.model.ForwardStatus
import com.smsforwarder.app.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json

@HiltWorker
class DrainQueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val apiService: ApiService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val messages = pendingMessageDao.getMessagesToDrain()
        if (messages.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false

        for (item in messages) {
            try {
                val outbound = Json.decodeFromString<EncryptedOutboundMessage>(item.payloadJson)
                val response = apiService.sendMessage(outbound)

                if (response.isSuccessful && response.body()?.accepted == true) {
                    pendingMessageDao.updateStatus(item.messageId, ForwardStatus.SENT)
                    pendingMessageDao.deleteByMessageId(item.messageId)
                } else {
                    anyFailed = true
                    handleFailure(item)
                }
            } catch (e: Exception) {
                anyFailed = true
                handleFailure(item, e.message)
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun handleFailure(item: com.smsforwarder.app.data.local.entity.PendingMessageEntity, error: String? = null) {
        val nextAttempt = item.attemptCount + 1
        if (nextAttempt >= 10) {
            pendingMessageDao.updateStatus(item.messageId, ForwardStatus.FAILED)
        } else {
            val backoffMs = (1L shl nextAttempt) * 5_000L // Exponential backoff: 10s, 20s, 40s...
            val updated = item.copy(
                attemptCount = nextAttempt,
                nextRetryAtMs = System.currentTimeMillis() + backoffMs,
                lastError = error ?: "Retry attempt $nextAttempt"
            )
            pendingMessageDao.updateMessage(updated)
        }
    }
}
