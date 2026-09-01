package com.smsforwarder.oppo.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.smsforwarder.oppo.crypto.OppoCryptoEngine
import com.smsforwarder.oppo.network.ApiService
import com.smsforwarder.oppo.network.AuthInterceptor
import com.smsforwarder.oppo.network.CompletePairingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Handles the OPPO side of the device-pairing flow.
 *
 * PAIRING FLOW (OPPO side):
 *
 *   1. Samsung calls `initiatePairing` and receives a short-lived token
 *      (e.g. "A3F-7K2") which it displays on screen.
 *
 *   2. The user reads the token and enters it via ADB:
 *      `adb shell am start -n com.smsforwarder.oppo/.MainActivity
 *       --es pairing_token "A3F-7K2"`
 *
 *   3. OPPO's [MainActivity] receives the intent and calls [completePairing].
 *
 *   4. [completePairing] POSTs to the backend `completePairing` endpoint.
 *      The backend:
 *        a. Validates the token (exists, not expired, not used).
 *        b. Links the source (OPPO) and destination (Samsung) devices.
 *        c. Returns Samsung's RSA public key PEM + device IDs.
 *        d. Sends "PAIRING_COMPLETE" FCM to Samsung.
 *
 *   5. OPPO stores Samsung's public key + device ID in [EncryptedSharedPreferences].
 *      From this point, [OppoCryptoEngine.isReadyToEncrypt] returns true.
 *
 *   6. [SmsForwardingPipeline] can now encrypt and queue real messages.
 */
@Singleton
class PairingRepository @Inject constructor(
    private val apiService: ApiService,
    private val deviceRepository: DeviceRepository,
    @Named("encrypted") private val prefs: SharedPreferences
) {

    /**
     * Complete the pairing handshake using the token from Samsung's screen.
     *
     * This is the main entry point triggered by the ADB intent.
     *
     * @param pairingToken The 6-char alphanumeric code shown on Samsung (e.g. "A3F-7K2").
     * @return [PairingResult] describing the outcome.
     */
    suspend fun completePairing(pairingToken: String): PairingResult = withContext(Dispatchers.IO) {

        if (pairingToken.isBlank()) {
            return@withContext PairingResult.Error("Pairing token is empty")
        }

        // Ensure OPPO is registered before pairing
        val sourceDeviceId = deviceRepository.registerIfNeeded()
            ?: return@withContext PairingResult.Error("Failed to register OPPO device with backend")

        Log.i(TAG, "Completing pairing with token: ${pairingToken.take(3)}***")

        try {
            val response = apiService.completePairing(
                CompletePairingRequest(pairingToken = pairingToken.trim())
            )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                        ?: return@withContext PairingResult.Error("Empty response from backend")

                    // ── Store all pairing state atomically ────────────────
                    prefs.edit()
                        // OPPO's own credentials (may already exist from registerIfNeeded)
                        .putString(DeviceRepository.PREF_DEVICE_ID,    body.deviceId)
                        .putString(AuthInterceptor.PREF_API_KEY,        body.deviceApiKey)
                        // Samsung's pairing data
                        .putString(DeviceRepository.DEST_DEVICE_ID,    body.destinationDeviceId)
                        .putString(DeviceRepository.DEST_DEVICE_NAME,  body.destinationDeviceName)
                        // Samsung's RSA public key — enables OppoCryptoEngine
                        .putString(OppoCryptoEngine.PREF_DEST_PUBLIC_KEY, body.destinationPublicKeyPem)
                        .putString(OppoCryptoEngine.PREF_SOURCE_DEVICE_ID, body.deviceId)
                        .putString(OppoCryptoEngine.PREF_DEST_DEVICE_ID,   body.destinationDeviceId)
                        // Pairing flag
                        .putBoolean(DeviceRepository.PREF_IS_PAIRED, true)
                        .apply()

                    Log.i(TAG, "Pairing complete: Samsung id=${body.destinationDeviceId.take(8)}")
                    PairingResult.Success(body.destinationDeviceName)
                }

                response.code() == 401 || response.code() == 400 -> {
                    Log.w(TAG, "Pairing failed: invalid or expired token (HTTP ${response.code()})")
                    PairingResult.InvalidToken
                }

                response.code() == 409 -> {
                    Log.w(TAG, "Pairing failed: token already used")
                    PairingResult.TokenAlreadyUsed
                }

                else -> {
                    Log.e(TAG, "Pairing failed: HTTP ${response.code()}")
                    PairingResult.Error("Backend error: HTTP ${response.code()}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Pairing network error (details suppressed)")
            PairingResult.NetworkError
        }
    }

    /** Clear local pairing state (called when revocation completes). */
    fun clearPairingState() {
        prefs.edit()
            .putBoolean(DeviceRepository.PREF_IS_PAIRED, false)
            .remove(DeviceRepository.DEST_DEVICE_ID)
            .remove(DeviceRepository.DEST_DEVICE_NAME)
            .remove(OppoCryptoEngine.PREF_DEST_PUBLIC_KEY)
            .remove(OppoCryptoEngine.PREF_DEST_DEVICE_ID)
            .apply()
    }

    companion object {
        private const val TAG = "OppoPairingRepository"
    }
}

/**
 * Result of a [PairingRepository.completePairing] call.
 */
sealed class PairingResult {
    /** Pairing successful. [destinationName] is Samsung's device name. */
    data class Success(val destinationName: String) : PairingResult()
    /** Token was invalid, expired, or not found. */
    object InvalidToken : PairingResult()
    /** Token was already used (each token is single-use). */
    object TokenAlreadyUsed : PairingResult()
    /** Network unavailable or timed out. */
    object NetworkError : PairingResult()
    /** Any other error. */
    data class Error(val message: String) : PairingResult()
}
