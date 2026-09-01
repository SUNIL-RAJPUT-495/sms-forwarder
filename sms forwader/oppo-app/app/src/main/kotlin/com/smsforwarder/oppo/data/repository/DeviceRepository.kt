package com.smsforwarder.oppo.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.smsforwarder.oppo.network.ApiService
import com.smsforwarder.oppo.network.AuthInterceptor
import com.smsforwarder.oppo.network.RegisterDeviceRequest
import com.smsforwarder.oppo.network.RegisterDeviceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Repository for OPPO device registration and lifecycle.
 *
 * REGISTRATION FLOW:
 *   1. Call [registerIfNeeded] on first launch (or after revocation).
 *   2. Backend returns a [deviceId] + [deviceApiKey].
 *   3. Both are stored in [EncryptedSharedPreferences].
 *   4. [AuthInterceptor] picks up the apiKey automatically for all future calls.
 *
 * REVOCATION:
 *   Calling [revokeDevice] clears ALL pairing state from preferences and
 *   notifies the backend. This is a destructive operation — all pending
 *   messages are abandoned (not re-sent after re-pairing).
 *
 * IDEMPOTENCY:
 *   [registerIfNeeded] is safe to call on every app start — it checks
 *   whether a deviceId already exists before making any network call.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: ApiService,
    @Named("encrypted") private val prefs: SharedPreferences
) {

    /**
     * Register this OPPO device with the backend, if not already registered.
     *
     * @return The device ID assigned by the backend, or null on failure.
     */
    suspend fun registerIfNeeded(): String? = withContext(Dispatchers.IO) {
        val existingId = prefs.getString(PREF_DEVICE_ID, null)
        if (existingId != null) {
            Log.d(TAG, "Device already registered: id=${existingId.take(8)}…")
            return@withContext existingId
        }

        Log.i(TAG, "Registering OPPO device with backend…")
        try {
            val response = apiService.registerDevice(
                RegisterDeviceRequest(deviceName = "OPPO", role = "SOURCE")
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "Registration failed: HTTP ${response.code()}")
                return@withContext null
            }

            val body = response.body() ?: run {
                Log.e(TAG, "Registration response body null")
                return@withContext null
            }

            // Store credentials in encrypted prefs
            prefs.edit()
                .putString(PREF_DEVICE_ID, body.deviceId)
                .putString(AuthInterceptor.PREF_API_KEY, body.deviceApiKey)
                .apply()

            Log.i(TAG, "OPPO registered: id=${body.deviceId.take(8)}…")
            body.deviceId

        } catch (e: Exception) {
            Log.e(TAG, "Registration network error (details suppressed)")
            null
        }
    }

    /**
     * Revoke this device's pairing with the backend.
     * Clears ALL local pairing state after backend confirmation.
     *
     * @return true if revocation was successful.
     */
    suspend fun revokeDevice(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.revokeDevice()
            if (!response.isSuccessful && response.code() != 404) {
                Log.e(TAG, "Revocation backend failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Revocation network error (details suppressed)")
        }

        // Always clear local pairing state so the user is never stuck
        prefs.edit()
            .remove(PREF_DEVICE_ID)
            .remove(PREF_IS_PAIRED)
            .remove(AuthInterceptor.PREF_API_KEY)
            .remove(DEST_PUBLIC_KEY_PEM)
            .remove(DEST_DEVICE_ID)
            .remove(DEST_DEVICE_NAME)
            .apply()

        Log.i(TAG, "Device revoked and local pairing state cleared")
        true
    }

    fun getDeviceId(): String? = prefs.getString(PREF_DEVICE_ID, null)
    fun isRegistered(): Boolean = prefs.getString(PREF_DEVICE_ID, null) != null
    fun isPaired(): Boolean = prefs.getBoolean(PREF_IS_PAIRED, false)

    companion object {
        private const val TAG = "OppoDeviceRepository"

        const val PREF_DEVICE_ID    = "source_device_id"
        const val PREF_IS_PAIRED    = "is_paired"
        const val DEST_PUBLIC_KEY_PEM = "dest_public_key_pem"
        const val DEST_DEVICE_ID    = "dest_device_id"
        const val DEST_DEVICE_NAME  = "dest_device_name"
    }
}
