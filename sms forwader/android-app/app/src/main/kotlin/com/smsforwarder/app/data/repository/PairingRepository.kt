package com.smsforwarder.app.data.repository

import com.smsforwarder.app.network.ApiService
import com.smsforwarder.app.network.AuthInterceptor
import com.smsforwarder.app.network.CompletePairingRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class PairingSessionInfo(
    val pairingToken: String,
    val displayCode: String,
    val qrCodeUri: String,
    val expiresAt: String
)

sealed class PairingResult {
    data class Success(val destinationName: String, val destinationDeviceId: String) : PairingResult()
    data class Error(val message: String) : PairingResult()
}

@Singleton
class PairingRepository @Inject constructor(
    private val apiService: ApiService,
    private val authInterceptor: AuthInterceptor,
    private val deviceRepository: DeviceRepository
) {

    /**
     * Initiates pairing on the RECEIVER device.
     * Returns a 6-digit display code and QR Code URI.
     */
    suspend fun initiatePairing(): Result<PairingSessionInfo> {
        return try {
            val response = apiService.initiatePairing()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val deviceInfo = deviceRepository.deviceInfoFlow.first()

                val qrUri = "smsforwarder://pair?token=${body.pairingToken}&code=${body.displayCode}&device=${deviceInfo.deviceName}"

                Result.success(
                    PairingSessionInfo(
                        pairingToken = body.pairingToken,
                        displayCode = body.displayCode,
                        qrCodeUri = qrUri,
                        expiresAt = body.expiresAt
                    )
                )
            } else {
                Result.failure(Exception("Failed to initiate pairing: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Completes pairing on the SENDER device using a 6-digit code or raw token.
     */
    suspend fun completePairing(rawInput: String): PairingResult {
        return try {
            // Extract token or code from deep link or user input
            val token = parsePairingInput(rawInput)
            val deviceInfo = deviceRepository.deviceInfoFlow.first()

            val request = CompletePairingRequest(
                pairingToken = token,
                sourceName = deviceInfo.deviceName
            )

            val response = apiService.completePairing(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Save sender's new API key
                authInterceptor.saveApiKey(body.deviceApiKey)

                // Save paired receiver details
                deviceRepository.savePairedDevice(
                    pairedDeviceId = body.destinationDeviceId,
                    pairedDeviceName = body.destinationDeviceName,
                    pairedPublicKeyPem = body.destinationPublicKeyPem
                )

                PairingResult.Success(
                    destinationName = body.destinationDeviceName,
                    destinationDeviceId = body.destinationDeviceId
                )
            } else {
                PairingResult.Error("Pairing rejected (${response.code()}): ${response.message()}")
            }
        } catch (e: Exception) {
            PairingResult.Error("Pairing error: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Parses manual PIN, deep link URI, or QR string.
     */
    fun parsePairingInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("smsforwarder://pair")) {
            val uri = android.net.Uri.parse(trimmed)
            val token = uri.getQueryParameter("token")
            val code = uri.getQueryParameter("code")
            return token ?: code ?: trimmed
        }
        return trimmed
    }
}
