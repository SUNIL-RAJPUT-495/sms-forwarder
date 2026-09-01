package com.smsforwarder.oppo.filter

import android.util.Log
import com.smsforwarder.oppo.BuildConfig
import com.smsforwarder.oppo.crypto.EncryptionException
import com.smsforwarder.oppo.crypto.OppoCryptoEngine
import com.smsforwarder.oppo.data.local.dao.PendingMessageDao
import com.smsforwarder.oppo.data.local.entity.PendingMessageEntity
import com.smsforwarder.oppo.domain.model.EncryptedOutboundMessage
import com.smsforwarder.oppo.domain.model.SmsMessageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS Forwarding Pipeline — bridges the filter engine and the outbound queue.
 *
 * Phase 3 → Phase 4 change:
 *   CRYPTO_STUB is REPLACED with a real call to [OppoCryptoEngine.encrypt].
 *   The SMS body is encrypted immediately and the plaintext is never written
 *   to any persistent storage.
 *
 * ─────────────────────────────────────────────
 * DEVICE NOT PAIRED BEHAVIOUR:
 *
 *   If [OppoCryptoEngine.isReadyToEncrypt] returns false (no Samsung public
 *   key stored yet — device not paired), the message is stored with status
 *   "AWAITING_KEY". Phase 5 pairing completion will trigger a re-processing
 *   of all AWAITING_KEY messages once the key is received.
 *
 *   Note: AWAITING_KEY entries in [PendingMessageDao] store the plaintext
 *   sender and a PLACEHOLDER body marker — NOT the plaintext body — because
 *   we have no key to encrypt with. This means messages received before
 *   pairing are DROPPED (not held in cleartext). The user must complete
 *   pairing before expecting forwarding to work.
 *
 *   In practice, this scenario is unlikely because the user configures
 *   OPPO before exposing it to real bank SMS.
 *
 * SECURITY INVARIANTS:
 *   - [SmsMessageData.body] is NEVER written to [PendingMessageDao].
 *   - Only [EncryptedOutboundMessage] fields (all ciphertext) are persisted.
 *   - If encryption throws, the message is DROPPED (not queued in cleartext).
 *   - Exception messages are suppressed in logs to prevent body leakage.
 */
@Singleton
class SmsForwardingPipeline @Inject constructor(
    private val cryptoEngine: OppoCryptoEngine,
    private val pendingMessageDao: PendingMessageDao,
    private val apiService: com.smsforwarder.oppo.network.ApiService
) {

    /**
     * Accept a filter-matched SMS, encrypt it, and enqueue for delivery.
     *
     * @param smsData The matched SMS. Its [SmsMessageData.body] is encrypted
     *   immediately and the plaintext is never stored.
     */
    suspend fun enqueue(smsData: SmsMessageData) = withContext(Dispatchers.Default) {
        try {
            if (!cryptoEngine.isReadyToEncrypt()) {
                Log.w(TAG,
                    "Device not paired — no Samsung public key available. " +
                    "Message id=${smsData.messageId.take(8)} dropped. " +
                    "Complete pairing first."
                )
                // SECURITY: We DO NOT store plaintext when unpaired.
                // The message is discarded. Phase 5 pairing is required first.
                return@withContext
            }

            // ─── Encrypt ─────────────────────────────────────────────
            // After this call, smsData.body exists only on the stack.
            // The plaintext is zeroed inside OppoCryptoEngine after use.
            val encrypted: EncryptedOutboundMessage = cryptoEngine.encrypt(smsData)

            // ─── Persist ciphertext to queue ─────────────────────────
            val expiresAtMs = System.currentTimeMillis() +
                    (BuildConfig.MESSAGE_EXPIRY_HOURS.toLong() * 60 * 60 * 1000)

            val entity = PendingMessageEntity(
                messageId           = encrypted.messageId,
                sourceDeviceId      = encrypted.sourceDeviceId,
                destinationDeviceId = encrypted.destinationDeviceId,
                protocolVersion     = encrypted.protocolVersion,
                timestampMs         = encrypted.timestamp,
                encryptedKey        = encrypted.encryptedKey,   // RSA-OAEP wrapped AES key
                nonce               = encrypted.nonce,           // GCM nonce
                ciphertext          = encrypted.ciphertext,     // AES-GCM ciphertext + auth tag
                status              = "PENDING",
                expiresAtMs         = expiresAtMs
            )

            val rowId = withContext(Dispatchers.IO) {
                pendingMessageDao.insert(entity)
            }

            if (rowId == -1L) {
                Log.w(TAG, "Duplicate messageId — already queued: ${smsData.messageId.take(8)}")
            } else {
                Log.i(TAG, "Enqueued encrypted message: id=${smsData.messageId.take(8)} sender=${smsData.sender.take(4)}***")
                try {
                    val response = apiService.sendMessage(encrypted)
                    if (response.isSuccessful || response.code() == 202) {
                        pendingMessageDao.deleteByMessageId(encrypted.messageId)
                        Log.i(TAG, "Sent message ${smsData.messageId.take(8)} directly to backend")
                    } else {
                        Log.w(TAG, "Failed to send message ${smsData.messageId.take(8)} directly: HTTP ${response.code()}")
                    }
                } catch (sendErr: Exception) {
                    Log.w(TAG, "Direct send failed — message queued for retry")
                }
            }

        } catch (e: EncryptionException) {
            // SECURITY: log that encryption failed but NOT the exception message
            // (it may reference key details, though we try to keep it clean).
            Log.e(TAG, "Encryption failed — message dropped (details suppressed)")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected pipeline failure — message dropped (details suppressed)")
        }
    }

    companion object {
        private const val TAG = "SmsForwardingPipeline"
    }
}
