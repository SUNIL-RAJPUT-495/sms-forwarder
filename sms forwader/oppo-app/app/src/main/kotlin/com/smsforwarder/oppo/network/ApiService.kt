package com.smsforwarder.oppo.network

import com.smsforwarder.oppo.domain.model.EncryptedOutboundMessage
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the backend API.
 *
 * SECURITY: All endpoints are authenticated via the Authorization header
 * injected by [AuthInterceptor] (added in Phase 5).
 *
 * All request/response bodies contain NO plaintext SMS.
 */
interface ApiService {

    /**
     * Register this device (OPPO source) with the backend.
     * Called once on first launch or after unpairing.
     */
    @POST("registerDevice")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<RegisterDeviceResponse>

    /**
     * Complete a pairing initiated by Samsung.
     * Exchanges the pairing token for a deviceApiKey and Samsung's public key.
     */
    @POST("completePairing")
    suspend fun completePairing(@Body request: CompletePairingRequest): Response<CompletePairingResponse>

    /**
     * Send an encrypted message to the backend for FCM delivery to Samsung.
     * Returns 202 Accepted; delivery is asynchronous via FCM.
     */
    @POST("sendMessage")
    suspend fun sendMessage(@Body message: EncryptedOutboundMessage): Response<SendMessageResponse>

    /**
     * Retrieve the destination (Samsung) device's public key.
     * Called after pairing to obtain the key used for encryption.
     */
    @GET("getDestinationPublicKey")
    suspend fun getDestinationPublicKey(): Response<PublicKeyResponse>

    /**
     * Revoke this device's pairing with the backend.
     */
    @DELETE("revokeDevice")
    suspend fun revokeDevice(): Response<Unit>
}

// ─────────────────────────────────────────────
// Request / Response DTOs
// ─────────────────────────────────────────────

@Serializable
data class RegisterDeviceRequest(
    val deviceName: String,
    val role: String = "SOURCE"
)

@Serializable
data class RegisterDeviceResponse(
    val deviceId: String,
    val deviceApiKey: String
)

@Serializable
data class CompletePairingRequest(
    val pairingToken: String,
    val sourceName: String = "OPPO"
)

@Serializable
data class CompletePairingResponse(
    val deviceId: String,           // OPPO's device ID (assigned by backend)
    val deviceApiKey: String,       // OPPO's API key for subsequent requests
    val destinationDeviceId: String, // Samsung's device ID
    val destinationPublicKeyPem: String, // Samsung's RSA public key
    val destinationDeviceName: String
)

@Serializable
data class SendMessageResponse(
    val accepted: Boolean,
    val messageId: String
)

@Serializable
data class PublicKeyResponse(
    val publicKeyPem: String,
    val deviceId: String
)
