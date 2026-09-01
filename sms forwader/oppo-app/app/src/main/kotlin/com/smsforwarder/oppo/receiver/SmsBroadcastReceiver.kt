package com.smsforwarder.oppo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.smsforwarder.oppo.domain.model.SmsMessageData
import com.smsforwarder.oppo.filter.SmsFilterEngine
import com.smsforwarder.oppo.filter.SmsForwardingPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject

/**
 * Manifest-registered BroadcastReceiver for incoming SMS.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. Manifest-registered (not dynamic): The OS wakes this receiver even
 *    if the app process was killed by ColorOS. This is the most reliable
 *    mechanism for SMS reception on aggressive OEMs.
 *
 * 2. android:exported="false" + android:permission="BROADCAST_SMS":
 *    Only the system (with BROADCAST_SMS permission) can trigger this.
 *    Prevents malicious apps from injecting fake SMS broadcasts.
 *
 * 3. goAsync(): Allows async work beyond the 10-second ANR window.
 *    The PendingResult is held until coroutine work completes.
 *
 * 4. Multipart SMS handling:
 *    Indian bank SMS may span multiple segments. We extract ALL PDUs
 *    from the broadcast intent and concatenate body parts in PDU order.
 *    DO NOT call SmsMessage.getDisplayMessageBody() on a single PDU
 *    and assume it's the complete message.
 *
 * 5. Indian sender ID normalisation:
 *    TRAI-mandated alpha sender IDs may have operator prefixes:
 *    "VM-SBIINB", "VD-HDFCBK", "TP-ICICIB", etc.
 *    We strip the prefix before filter evaluation but preserve the
 *    original address in [SmsMessageData.sender].
 *
 * PHASE 3: Fully wired to [SmsFilterEngine] and [SmsForwardingPipeline].
 *   1. Extract SMS from broadcast intent
 *   2. Ask SmsFilterEngine if it matches configured rules
 *   3. If YES → pass to SmsForwardingPipeline (encrypt + enqueue)
 *   4. If NO  → silently drop (never stored or forwarded)
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var filterEngine: SmsFilterEngine

    @Inject
    lateinit var forwardingPipeline: SmsForwardingPipeline

    // Coroutine scope tied to the async receiver lifecycle.
    // SupervisorJob: child failures don't cancel sibling coroutines.
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Hold the broadcast open: prevents BroadcastReceiver from
        // finishing before our coroutine completes (would skip processing).
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val smsData = extractSmsData(intent) ?: run {
                    Log.w(TAG, "Could not extract SMS data from broadcast")
                    return@launch
                }

                // SECURITY: Only log redacted sender prefix — never the body.
                Log.d(TAG, "SMS received from: ${smsData.sender.take(4)}*** id=${smsData.messageId.take(8)}")

                // ─── Step 1: Filter evaluation ─────────────────────────
                val shouldForward = filterEngine.shouldForward(smsData)

                if (!shouldForward) {
                    Log.d(TAG, "SMS from ${smsData.sender.take(4)}*** did not match any filter rule — dropped")
                    return@launch
                }

                // ─── Step 2: Enqueue for forwarding ────────────────────
                // The pipeline handles encryption (Phase 4) + queue storage.
                // Plaintext SMS is NEVER written to disk past this point.
                Log.i(TAG, "Filter matched — enqueueing id=${smsData.messageId.take(8)}")
                forwardingPipeline.enqueue(smsData)

            } catch (e: Exception) {
                // SECURITY: Never log 'e.message' — it may contain SMS content
                // if a parsing library or crypto layer includes payload in its exception.
                Log.e(TAG, "Error processing SMS broadcast (details suppressed)")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Extracts a merged [SmsMessageData] from the broadcast intent.
     *
     * Handles:
     *   - Single-part SMS (1 PDU in the intent)
     *   - Multi-part SMS (N PDUs, merged in delivery order)
     *   - Null/empty extras (returns null, logged by caller)
     *
     * @return null if no valid SMS could be extracted.
     */
    private fun extractSmsData(intent: Intent): SmsMessageData? {
        val messages: Array<SmsMessage>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else {
                @Suppress("DEPRECATION")
                extractLegacyMessages(intent)
            }

        if (messages.isNullOrEmpty()) return null

        // All PDUs in a multipart message share the same originating address.
        val sender = messages[0].originatingAddress ?: return null

        // Concatenate body segments in delivery order.
        // getMessagesFromIntent returns PDUs already in sequence order.
        val body = messages.joinToString(separator = "") {
            it.displayMessageBody.orEmpty()
        }

        if (body.isBlank()) return null

        return SmsMessageData(
            messageId  = UUID.randomUUID().toString(),
            sender     = sender,
            body       = body,
            timestampMs = messages[0].timestampMillis
        )
    }

    @Suppress("DEPRECATION")
    private fun extractLegacyMessages(intent: Intent): Array<SmsMessage>? {
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return null
        val format = intent.extras?.getString("format")
        return pdus
            .filterIsInstance<ByteArray>()
            .map { SmsMessage.createFromPdu(it, format) }
            .toTypedArray()
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"

        /**
         * Normalise Indian TRAI operator sender ID prefixes.
         *
         * Examples:
         *   "VM-SBIINB"  → "SBIINB"
         *   "VD-HDFCBK"  → "HDFCBK"
         *   "TP-ICICIB"  → "ICICIB"
         *   "AXISBK"     → "AXISBK"  (no-op — no prefix)
         *   "+919876543210" → "+919876543210" (no-op — numeric)
         *
         * Used by [SmsFilterEngine] before applying EXACT_SENDER /
         * SENDER_CONTAINS rules.
         */
        fun normaliseSenderId(raw: String): String {
            val prefixes = listOf("VM-", "VD-", "TP-", "AT-", "JD-", "BW-", "TF-", "CP-", "AX-")
            return prefixes.fold(raw.uppercase()) { acc, prefix ->
                if (acc.startsWith(prefix)) acc.removePrefix(prefix) else acc
            }
        }
    }
}
