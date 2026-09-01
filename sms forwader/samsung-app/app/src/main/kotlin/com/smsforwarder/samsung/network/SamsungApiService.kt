package com.smsforwarder.samsung.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Samsung app's backend API.
 *
 * Samsung is the DESTINATION device. It:
 *   1. Registers itself and gets a device ID + API key.
 *   2. Calls initiatePairing to get a short-lived token to share with OPPO.
 *   3. Receives encrypted messages via FCM (not REST pull).
 *   4. Sends ACKs back to the backend after successful decryption.
 *   5. Can revoke the pairing.
 */
interface SamsungApiService {

    /**
     * Register Samsung as a destination device.
     * Called once on first launch. Returns a deviceId + apiKey.
     */
    @POST("registerDevice")
    suspend fun registerDevice(
        @Body request: SamsungRegisterRequest
    ): Response<SamsungRegisterResponse>

    /**
     * Initiate a pairing session. Returns a short-lived token (e.g. "A3F-7K2")
     * to display to the user. The OPPO device submits this token to completePairing.
     * Tokens expire after 10 minutes and are single-use.
     */
    @POST("initiatePairing")
    suspend fun initiatePairing(
        @Body request: InitiatePairingRequest
    ): Response<InitiatePairingResponse>

    /**
     * Send an acknowledgement after successfully decrypting a message.
     * This allows the backend to confirm delivery and stop retrying FCM.
     */
    @POST("acknowledgeMessage")
    suspend fun acknowledgeMessage(
        @Body request: AckRequest
    ): Response<Unit>

    @POST("fetchPendingMessages")
    suspend fun fetchPendingMessages(): Response<FetchPendingMessagesResponse>

    /**
     * Revoke Samsung's registration and unlink from OPPO.
     * Clears the pairing on the backend; subsequent FCM messages will fail validation.
     */
    @DELETE("revokeDevice")
    suspend fun revokeDevice(): Response<Unit>
}

// ─────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────

@Serializable
data class SamsungRegisterRequest(
    val deviceName: String,
    val role: String = "DESTINATION",
    /** Samsung's RSA public key PEM — shared with OPPO during pairing. */
    val publicKeyPem: String
)

@Serializable
data class SamsungRegisterResponse(
    val deviceId: String,
    val deviceApiKey: String
)

@Serializable
data class InitiatePairingRequest(
    /** Samsung's registered device ID. */
    val deviceId: String
)

@Serializable
data class InitiatePairingResponse(
    val pairingToken: String = "",
    val expiresAtMs: Long = 0L,
    val isPaired: Boolean = false,
    val sourceDeviceName: String? = null
)

@Serializable
data class AckRequest(
    val messageId: String,
    val deviceId: String
)

@Serializable
data class FetchPendingMessagesResponse(
    val messages: List<com.smsforwarder.samsung.domain.model.EncryptedInboundPayload> = emptyList()
)
