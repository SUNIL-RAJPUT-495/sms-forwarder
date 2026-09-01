package com.smsforwarder.app.data.repository

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import com.smsforwarder.app.crypto.KeyManager
import com.smsforwarder.app.domain.model.DeviceInfo
import com.smsforwarder.app.domain.model.DeviceRole
import com.smsforwarder.app.network.ApiService
import com.smsforwarder.app.network.AuthInterceptor
import com.smsforwarder.app.network.RegisterDeviceRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_settings")

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val authInterceptor: AuthInterceptor,
    private val keyManager: KeyManager
) {

    companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_DEPARTMENT_NAME = stringPreferencesKey("department_name")
        val KEY_MOBILE_NUMBER = stringPreferencesKey("mobile_number")
        val KEY_ADDRESS = stringPreferencesKey("address")
        val KEY_DEVICE_ROLE = stringPreferencesKey("device_role")
        val KEY_IS_REGISTERED = booleanPreferencesKey("is_registered")
        val KEY_IS_PAIRED = booleanPreferencesKey("is_paired")
        val KEY_PAIRED_DEVICE_ID = stringPreferencesKey("paired_device_id")
        val KEY_PAIRED_DEVICE_NAME = stringPreferencesKey("paired_device_name")
        val KEY_PAIRED_PUBLIC_KEY = stringPreferencesKey("paired_public_key")
    }

    /**
     * Default hardware model name e.g. "Google Pixel 8 Pro", "Samsung SM-S911B", "Xiaomi 23049PCD8G"
     */
    fun getDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    suspend fun saveDepartmentDetails(departmentName: String, mobileNumber: String, address: String) {
        context.deviceDataStore.edit { prefs ->
            prefs[KEY_DEPARTMENT_NAME] = departmentName
            prefs[KEY_MOBILE_NUMBER] = mobileNumber
            prefs[KEY_ADDRESS] = address
        }
    }

    val deviceInfoFlow: Flow<DeviceInfo> = context.deviceDataStore.data.map { prefs ->
        val roleStr = prefs[KEY_DEVICE_ROLE] ?: DeviceRole.SENDER.name
        val role = runCatching { DeviceRole.valueOf(roleStr) }.getOrDefault(DeviceRole.SENDER)
        val name = prefs[KEY_DEVICE_NAME] ?: getDefaultDeviceName()
        val departmentName = prefs[KEY_DEPARTMENT_NAME] ?: name
        val mobileNumber = prefs[KEY_MOBILE_NUMBER] ?: ""
        val address = prefs[KEY_ADDRESS] ?: ""
        val deviceId = prefs[KEY_DEVICE_ID] ?: ""
        val isRegistered = prefs[KEY_IS_REGISTERED] ?: false
        val isPaired = prefs[KEY_IS_PAIRED] ?: false
        val pairedName = prefs[KEY_PAIRED_DEVICE_NAME]
        val pairedId = prefs[KEY_PAIRED_DEVICE_ID]

        DeviceInfo(
            deviceId = deviceId,
            deviceName = name,
            departmentName = departmentName,
            mobileNumber = mobileNumber,
            address = address,
            role = role,
            isRegistered = isRegistered,
            isPaired = isPaired,
            pairedDeviceName = pairedName,
            pairedDeviceId = pairedId
        )
    }

    suspend fun setDeviceRole(role: DeviceRole) {
        context.deviceDataStore.edit { prefs ->
            prefs[KEY_DEVICE_ROLE] = role.name
        }
    }

    suspend fun setDeviceName(name: String) {
        context.deviceDataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = name
        }
    }

    /**
     * Registers this device with the Backend.
     */
    suspend fun registerDevice(): Result<String> {
        return try {
            val prefs = context.deviceDataStore.data.first()
            val roleStr = prefs[KEY_DEVICE_ROLE] ?: DeviceRole.RECEIVER.name
            val role = runCatching { DeviceRole.valueOf(roleStr) }.getOrDefault(DeviceRole.RECEIVER)
            val deviceName = prefs[KEY_DEVICE_NAME] ?: getDefaultDeviceName()
            val departmentName = prefs[KEY_DEPARTMENT_NAME] ?: "Accounts Dept"
            val mobileNumber = prefs[KEY_MOBILE_NUMBER] ?: "N/A"
            val address = prefs[KEY_ADDRESS] ?: "Main Office"

            val backendRole = if (role == DeviceRole.SENDER) "SOURCE" else "DESTINATION"

            var publicKeyPem: String? = null
            var fcmToken: String? = null

            if (role == DeviceRole.RECEIVER || role == DeviceRole.DUAL) {
                publicKeyPem = keyManager.getPublicKeyPem()
                fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            }

            val request = RegisterDeviceRequest(
                deviceName = deviceName,
                departmentName = departmentName,
                mobileNumber = mobileNumber,
                address = address,
                role = backendRole,
                publicKeyPem = publicKeyPem,
                fcmToken = fcmToken
            )

            val response = try {
                apiService.registerNodeDevice(request)
            } catch (e: Exception) {
                apiService.registerDevice(request)
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                authInterceptor.saveApiKey(body.deviceApiKey)

                context.deviceDataStore.edit { p ->
                    p[KEY_DEVICE_ID] = body.deviceId
                    p[KEY_IS_REGISTERED] = true
                }
                Result.success(body.deviceId)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePairedDevice(
        pairedDeviceId: String,
        pairedDeviceName: String,
        pairedPublicKeyPem: String? = null
    ) {
        context.deviceDataStore.edit { prefs ->
            prefs[KEY_IS_PAIRED] = true
            prefs[KEY_PAIRED_DEVICE_ID] = pairedDeviceId
            prefs[KEY_PAIRED_DEVICE_NAME] = pairedDeviceName
            if (pairedPublicKeyPem != null) {
                prefs[KEY_PAIRED_PUBLIC_KEY] = pairedPublicKeyPem
            }
        }
    }

    suspend fun getPairedPublicKey(): String? {
        val prefs = context.deviceDataStore.data.first()
        return prefs[KEY_PAIRED_PUBLIC_KEY]
    }

    suspend fun unpair() {
        runCatching { apiService.revokeDevice() }
        authInterceptor.clearApiKey()
        context.deviceDataStore.edit { prefs ->
            prefs[KEY_IS_PAIRED] = false
            prefs.remove(KEY_PAIRED_DEVICE_ID)
            prefs.remove(KEY_PAIRED_DEVICE_NAME)
            prefs.remove(KEY_PAIRED_PUBLIC_KEY)
            prefs[KEY_IS_REGISTERED] = false
            prefs.remove(KEY_DEVICE_ID)
        }
    }
}
