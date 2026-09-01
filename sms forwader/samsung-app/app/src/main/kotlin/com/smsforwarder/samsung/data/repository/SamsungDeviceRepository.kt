package com.smsforwarder.samsung.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.smsforwarder.samsung.crypto.KeyManager
import com.smsforwarder.samsung.network.SamsungApiService
import com.smsforwarder.samsung.network.SamsungAuthInterceptor
import com.smsforwarder.samsung.network.SamsungRegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Repository for Samsung device registration and lifecycle.
 *
 * REGISTRATION FLOW:
 *   1. [KeyManager.ensureKeyPairExists] must have been called first (done in App.onCreate).
 *   2. Call [registerIfNeeded] on first launch.
 *   3. Backend returns deviceId + deviceApiKey.
 *   4. Both are stored in EncryptedSharedPreferences.
 *   5. Samsung's RSA public key is included in the registration request.
 *      The backend stores it and returns it to OPPO during completePairing.
 */
@Singleton
class SamsungDeviceRepository @Inject constructor(
    private val apiService: SamsungApiService,
    private val keyManager: KeyManager,
    @Named("encrypted") private val prefs: SharedPreferences
) {

    /**
     * Register Samsung with the backend if not already registered.
     * Idempotent — safe to call on every startup.
     *
     * @return The assigned deviceId, or null on failure.
     */
    suspend fun registerIfNeeded(): String? = withContext(Dispatchers.IO) {
        val existingId = prefs.getString(PREF_DEVICE_ID, null)
        if (existingId != null) {
            Log.d(TAG, "Samsung already registered: id=${existingId.take(8)}…")
            return@withContext existingId
        }

        Log.i(TAG, "Registering Samsung device with backend…")
        try {
            val publicKeyPem = keyManager.getPublicKeyPem()
            val response = apiService.registerDevice(
                SamsungRegisterRequest(deviceName = "Samsung", publicKeyPem = publicKeyPem)
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "Registration failed: HTTP ${response.code()}")
                return@withContext null
            }

            val body = response.body() ?: run {
                Log.e(TAG, "Registration response body null")
                return@withContext null
            }

            prefs.edit()
                .putString(PREF_DEVICE_ID, body.deviceId)
                .putString(SamsungAuthInterceptor.PREF_API_KEY, body.deviceApiKey)
                .apply()

            Log.i(TAG, "Samsung registered: id=${body.deviceId.take(8)}…")
            body.deviceId

        } catch (e: Exception) {
            Log.e(TAG, "Registration network error (details suppressed)")
            null
        }
    }

    fun getDeviceId(): String? = prefs.getString(PREF_DEVICE_ID, null)
    fun isRegistered(): Boolean = prefs.getString(PREF_DEVICE_ID, null) != null
    fun isPaired(): Boolean = prefs.getBoolean(PREF_IS_PAIRED, false)

    fun markPaired(sourceDeviceName: String) {
        prefs.edit()
            .putBoolean(PREF_IS_PAIRED, true)
            .putString(PREF_SOURCE_DEVICE_NAME, sourceDeviceName)
            .apply()
    }

    fun clearPairingState() {
        prefs.edit()
            .putBoolean(PREF_IS_PAIRED, false)
            .remove(PREF_SOURCE_DEVICE_NAME)
            .apply()
    }

    companion object {
        private const val TAG = "SamsungDeviceRepository"
        const val PREF_DEVICE_ID          = "dest_device_id"
        const val PREF_IS_PAIRED          = "is_paired"
        const val PREF_SOURCE_DEVICE_NAME = "source_device_name"
    }
}
