package com.smsforwarder.admin.network

import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AdminApiService {

    @GET("api/devices")
    suspend fun getAdminDevices(): Response<List<AdminDeviceDto>>

    @GET("api/messages")
    suspend fun getAdminMessages(): Response<List<AdminMessageDto>>

    @POST("api/register-device")
    suspend fun registerAdminDevice(@Body request: RegisterDeviceRequest): Response<AdminDeviceDto>

    @POST("api/send-sms")
    suspend fun sendDirectSms(@Body request: DirectSmsRequest): Response<SendSmsResponse>

    @POST("api/devices/{id}/commission")
    suspend fun addCommission(
        @Path("id") deviceId: String,
        @Body request: AddCommissionRequest
    ): Response<Map<String, Boolean>>

    @GET("api/withdrawals")
    suspend fun getWithdrawals(): Response<List<WithdrawalDto>>

    @POST("api/withdrawals/{id}/status")
    suspend fun updateWithdrawalStatus(
        @Path("id") withdrawalId: String,
        @Body request: UpdateWithdrawalStatusRequest
    ): Response<Map<String, Boolean>>
}

@Serializable
data class AddCommissionRequest(
    val amount: Double
)

@Serializable
data class WithdrawalDto(
    val withdrawalId: String = "",
    val deviceId: String = "",
    val userName: String = "User",
    val mobileNumber: String = "N/A",
    val bankName: String = "N/A",
    val accountNumber: String = "N/A",
    val ifscCode: String = "N/A",
    val amount: Double = 0.0,
    val status: String = "PENDING",
    val createdAt: String? = null
)

@Serializable
data class UpdateWithdrawalStatusRequest(
    val status: String
)

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
