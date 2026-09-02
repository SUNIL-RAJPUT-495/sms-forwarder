package com.smsforwarder.admin.data.repository

import com.smsforwarder.admin.BuildConfig
import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import com.smsforwarder.admin.network.AdminApiService
import com.smsforwarder.admin.network.DirectSmsRequest
import com.smsforwarder.admin.network.RegisterDeviceRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: AdminApiService,
    @Named("SseClient") private val sseClient: OkHttpClient,
    private val json: Json
) {

    suspend fun fetchDevices(): Result<List<AdminDeviceDto>> {
        return try {
            val response = apiService.getAdminDevices()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch devices: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMessages(): Result<List<AdminMessageDto>> {
        return try {
            val response = apiService.getAdminMessages()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Connects directly to backend SSE stream (/api/stream) for instant real-time message delivery (<100ms)
     */
    fun observeRealtimeMessages(): Flow<AdminMessageDto> = callbackFlow {
        val streamUrl = "${BuildConfig.BACKEND_BASE_URL.removeSuffix("/")}/api/stream"
        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Accept", "text/event-stream")
            .build()

        val factory = EventSources.createFactory(sseClient)
        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == "new_otp") {
                    runCatching {
                        val message = json.decodeFromString<AdminMessageDto>(data)
                        trySend(message)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                // Flow automatically reconnects if needed
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }

    suspend fun simulateRegisterDevice(
        departmentName: String,
        mobileNumber: String,
        address: String
    ): Result<AdminDeviceDto> {
        return try {
            val request = RegisterDeviceRequest(
                deviceName = departmentName,
                departmentName = departmentName,
                mobileNumber = mobileNumber,
                address = address,
                role = "SOURCE"
            )
            val response = apiService.registerAdminDevice(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Simulation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateSendSms(
        deviceId: String,
        departmentName: String,
        mobileNumber: String,
        address: String,
        sender: String,
        body: String
    ): Result<Unit> {
        return try {
            val request = DirectSmsRequest(
                deviceId = deviceId,
                departmentName = departmentName,
                mobileNumber = mobileNumber,
                address = address,
                sender = sender,
                body = body
            )
            val response = apiService.sendDirectSms(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Send SMS failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
