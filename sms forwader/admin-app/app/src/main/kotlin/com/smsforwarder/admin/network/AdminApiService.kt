package com.smsforwarder.admin.network

import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AdminApiService {

    @GET("api/devices")
    suspend fun getAdminDevices(): Response<List<AdminDeviceDto>>

    @GET("api/messages")
    suspend fun getAdminMessages(): Response<List<AdminMessageDto>>

    @POST("api/register-device")
    suspend fun registerAdminDevice(@Body request: RegisterDeviceRequest): Response<AdminDeviceDto>

    @POST("api/send-sms")
    suspend fun sendDirectSms(@Body request: DirectSmsRequest): Response<SendSmsResponse>
}

@Serializable
data class RegisterDeviceRequest(
    val deviceName: String? = null,
    val departmentName: String? = null,
    val mobileNumber: String? = null,
    val address: String? = null,
    val role: String = "SOURCE"
)

@Serializable
data class DirectSmsRequest(
    val deviceId: String? = null,
    val departmentName: String? = null,
    val mobileNumber: String? = null,
    val address: String? = null,
    val sender: String,
    val body: String,
    val timestamp: String? = null
)

@Serializable
data class SendSmsResponse(
    val success: Boolean = true,
    val messageId: String = ""
)
