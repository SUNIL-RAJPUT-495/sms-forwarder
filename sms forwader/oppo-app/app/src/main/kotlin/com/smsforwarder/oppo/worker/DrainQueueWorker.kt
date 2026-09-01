package com.smsforwarder.oppo.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsforwarder.oppo.data.local.dao.PendingMessageDao
import com.smsforwarder.oppo.domain.model.EncryptedOutboundMessage
import com.smsforwarder.oppo.network.ApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that drains the pending encrypted message queue.
 * Forwards pending messages to the backend via [ApiService.sendMessage].
 */
@HiltWorker
class DrainQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingMessageDao: PendingMessageDao,
    private val apiService: ApiService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val expired = pendingMessageDao.deleteExpired()
            if (expired > 0) {
                Log.i(TAG, "Deleted $expired expired messages from queue")
            }

            val pendingList = pendingMessageDao.getPending()
            Log.i(TAG, "Queue depth: ${pendingList.size} pending messages")

            for (msg in pendingList) {
                val outbound = EncryptedOutboundMessage(
                    messageId           = msg.messageId,
                    sourceDeviceId      = msg.sourceDeviceId,
                    destinationDeviceId = msg.destinationDeviceId,
                    protocolVersion     = msg.protocolVersion,
                    timestamp           = msg.timestampMs,
                    encryptedKey        = msg.encryptedKey,
                    nonce               = msg.nonce,
                    ciphertext          = msg.ciphertext
                )

                val response = apiService.sendMessage(outbound)
                if (response.isSuccessful || response.code() == 202) {
                    pendingMessageDao.deleteByMessageId(msg.messageId)
                    Log.i(TAG, "Sent message ${msg.messageId.take(8)} successfully")
                } else {
                    Log.e(TAG, "Failed to send message ${msg.messageId.take(8)}: HTTP ${response.code()}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DrainQueueWorker failed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        private const val TAG = "DrainQueueWorker"
        private const val MAX_RETRIES = 10
    }
}
