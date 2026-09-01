package com.smsforwarder.samsung.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.smsforwarder.samsung.crypto.SamsungCryptoEngine
import com.smsforwarder.samsung.data.local.dao.ForwardedMessageDao
import com.smsforwarder.samsung.data.local.entity.ForwardedMessageEntity
import com.smsforwarder.samsung.network.InitiatePairingRequest
import com.smsforwarder.samsung.network.SamsungApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Handles the Samsung side of the pairing flow and message sync.
 */
@Singleton
class SamsungPairingRepository @Inject constructor(
    private val apiService: SamsungApiService,
    private val deviceRepository: SamsungDeviceRepository,
    private val cryptoEngine: SamsungCryptoEngine,
    private val messageDao: ForwardedMessageDao,
    @Named("encrypted") private val prefs: SharedPreferences
) {

    /**
     * Initiate a pairing session with the backend.
     */
    suspend fun initiatePairing(): InitiateResult = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.registerIfNeeded()
            ?: return@withContext InitiateResult.Error("Failed to register Samsung device")

        Log.i(TAG, "Initiating pairing for device id=${deviceId.take(8)}…")

        try {
            val response = apiService.initiatePairing(InitiatePairingRequest(deviceId = deviceId))

            if (!response.isSuccessful) {
                Log.e(TAG, "initiatePairing failed: HTTP ${response.code()}")
                return@withContext InitiateResult.Error("Backend error: HTTP ${response.code()}")
            }

            val body = response.body()
                ?: return@withContext InitiateResult.Error("Empty response from backend")

            if (body.isPaired || body.sourceDeviceName != null) {
                val srcName = body.sourceDeviceName ?: "OPPO"
                deviceRepository.markPaired(srcName)
                Log.i(TAG, "Device is paired with $srcName")
            }

            Log.i(TAG, "Pairing initiated — token=***-*** (redacted) expires=${body.expiresAtMs}")
            InitiateResult.Success(
                token     = body.pairingToken,
                expiresAt = body.expiresAtMs
            )

        } catch (e: Exception) {
            Log.e(TAG, "initiatePairing network error (details suppressed)")
            InitiateResult.NetworkError
        }
    }

    /**
     * Fetch pending encrypted messages from backend, decrypt them, and store in Room.
     */
    suspend fun syncPendingMessages(): Int = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.getDeviceId() ?: return@withContext 0
        try {
            val response = apiService.fetchPendingMessages()
            if (!response.isSuccessful) return@withContext 0

            val body = response.body() ?: return@withContext 0
            var count = 0

            for (payload in body.messages) {
                try {
                    val forwardedMessage = cryptoEngine.decrypt(payload, deviceId)
                    val entity = ForwardedMessageEntity(
                        messageId           = forwardedMessage.messageId,
                        sender              = forwardedMessage.sender,
                        body                = forwardedMessage.body,
                        originalTimestampMs = forwardedMessage.originalTimestampMs,
                        receivedAtMs        = forwardedMessage.receivedAtMs,
                        deliveryStatus      = "ACKNOWLEDGED"
                    )
                    messageDao.insert(entity)
                    acknowledgeMessage(payload.messageId)
                    count++
                    Log.i(TAG, "Synced and decrypted message ${payload.messageId.take(8)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt synced message ${payload.messageId.take(8)}", e)
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "syncPendingMessages network error", e)
            0
        }
    }

    /**
     * Send an ACK to the backend after a message has been decrypted.
     */
    suspend fun acknowledgeMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val deviceId = deviceRepository.getDeviceId() ?: return@withContext false
        try {
            val response = apiService.acknowledgeMessage(
                com.smsforwarder.samsung.network.AckRequest(
                    messageId = messageId,
                    deviceId  = deviceId
                )
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "ACK failed: HTTP ${response.code()} for id=${messageId.take(8)}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "ACK network error (details suppressed)")
            false
        }
    }

    suspend fun revokeDevice(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiService.revokeDevice()
        } catch (e: Exception) {
            Log.e(TAG, "Revoke device network error", e)
        }
        deviceRepository.clearPairingState()
        true
    }

    companion object {
        private const val TAG = "SamsungPairingRepository"
    }
}

sealed class InitiateResult {
    data class Success(val token: String, val expiresAt: Long) : InitiateResult()
    object NetworkError : InitiateResult()
    data class Error(val message: String) : InitiateResult()
}
