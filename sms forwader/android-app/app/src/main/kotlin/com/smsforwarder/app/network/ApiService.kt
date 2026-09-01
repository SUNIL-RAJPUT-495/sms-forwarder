package com.smsforwarder.app.network

import com.smsforwarder.app.domain.model.EncryptedOutboundMessage
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("registerDevice")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>

    @POST("api/register-device")
    suspend fun registerNodeDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>

    @POST("initiatePairing")
    suspend fun initiatePairing(): Response<InitiatePairingResponse>

    @POST("completePairing")
    suspend fun completePairing(@Body request: CompletePairingRequest): Response<CompletePairingResponse>

    @POST("sendMessage")
    suspend fun sendMessage(@Body message: EncryptedOutboundMessage): Response<SendMessageResponse>

    @POST("api/send-sms")
    suspend fun sendDirectSms(@Body request: DirectSmsRequest): Response<SendMessageResponse>

    @POST("acknowledgeMessage")
    suspend fun acknowledgeMessage(@Body request: AcknowledgeRequest): Response<AcknowledgeResponse>

    @DELETE("revokeDevice")
    suspend fun revokeDevice(): Response<RevokeDeviceResponse>
}

@Serializable
data class RegisterDeviceRequest(
    val deviceName: String? = null,
    val departmentName: String? = null,
    val mobileNumber: String? = null,
    val address: String? = null,
    val role: String, // "SOURCE" or "DESTINATION"
    val publicKeyPem: String? = null,
    val fcmToken: String? = null
)

@Serializable
data class DirectSmsRequest(
    val deviceId: String? = null,
    val deviceApiKey: String? = null,
    val departmentName: String? = null,
    val mobileNumber: String? = null,
    val address: String? = null,
    val sender: String,
    val body: String,
    val timestamp: String? = null
)

@Serializable
data class RegisterDeviceResponse(
    val deviceId: String,
    val deviceApiKey: String
)

@Serializable
data class InitiatePairingResponse(
    val pairingToken: String,
    val displayCode: String,
    val expiresAt: String
)

@Serializable
data class CompletePairingRequest(
    val pairingToken: String,
    val sourceName: String
)

@Serializable
data class CompletePairingResponse(
    val deviceId: String,
    val deviceApiKey: String,
    val destinationDeviceId: String,
    val destinationPublicKeyPem: String,
    val destinationDeviceName: String
)

@Serializable
data class SendMessageResponse(
    val accepted: Boolean,
    val messageId: String
)

@Serializable
data class AcknowledgeRequest(
    val messageId: String
)

@Serializable
data class AcknowledgeResponse(
    val acknowledged: Boolean,
    val messageId: String
)

@Serializable
data class RevokeDeviceResponse(
    val revoked: Boolean,
    val deviceId: String
)
