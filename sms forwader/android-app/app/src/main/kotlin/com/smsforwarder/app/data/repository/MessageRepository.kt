package com.smsforwarder.app.data.repository

import com.smsforwarder.app.data.local.dao.InboundMessageDao
import com.smsforwarder.app.data.local.dao.PendingMessageDao
import com.smsforwarder.app.data.local.entity.InboundMessageEntity
import com.smsforwarder.app.data.local.entity.PendingMessageEntity
import com.smsforwarder.app.domain.model.EncryptedOutboundMessage
import com.smsforwarder.app.domain.model.ForwardStatus
import com.smsforwarder.app.domain.model.InboundMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val inboundMessageDao: InboundMessageDao,
    private val pendingMessageDao: PendingMessageDao
) {

    // ─────────────────────────────────────────────
    // INBOUND MESSAGES (RECEIVER / DUAL MODE)
    // ─────────────────────────────────────────────

    val allInboundMessages: Flow<List<InboundMessage>> = inboundMessageDao.getAllMessages().map { list ->
        list.map { it.toDomain() }
    }

    fun searchInboundMessages(query: String): Flow<List<InboundMessage>> =
        inboundMessageDao.searchMessages(query).map { list -> list.map { it.toDomain() } }

    suspend fun saveInboundMessage(
        messageId: String,
        sender: String,
        body: String,
        extractedOtp: String?,
        originalTimestampMs: Long,
        sourceDeviceName: String
    ) {
        val entity = InboundMessageEntity(
            messageId = messageId,
            sender = sender,
            body = body,
            extractedOtp = extractedOtp,
            originalTimestampMs = originalTimestampMs,
            sourceDeviceName = sourceDeviceName
        )
        inboundMessageDao.insertMessage(entity)
    }

    suspend fun markMessageCopied(messageId: String) {
        inboundMessageDao.markCopied(messageId)
    }

    suspend fun deleteInboundMessage(id: Long) {
        inboundMessageDao.deleteById(id)
    }

    suspend fun clearAllInboundMessages() {
        inboundMessageDao.clearAll()
    }

    // ─────────────────────────────────────────────
    // OUTBOUND QUEUE (SENDER / DUAL MODE)
    // ─────────────────────────────────────────────

    suspend fun queueOutboundMessage(message: EncryptedOutboundMessage, errorReason: String? = null) {
        val entity = PendingMessageEntity(
            messageId = message.messageId,
            payloadJson = Json.encodeToString(message),
            attemptCount = 1,
            nextRetryAtMs = System.currentTimeMillis() + 10_000L, // Retry in 10s
            status = ForwardStatus.PENDING,
            lastError = errorReason
        )
        pendingMessageDao.insertMessage(entity)
    }

    val pendingCountFlow: Flow<Int> = pendingMessageDao.getPendingCount()

    private fun InboundMessageEntity.toDomain() = InboundMessage(
        id = id,
        messageId = messageId,
        sender = sender,
        body = body,
        extractedOtp = extractedOtp,
        originalTimestampMs = originalTimestampMs,
        receivedTimestampMs = receivedTimestampMs,
        sourceDeviceName = sourceDeviceName,
        isCopied = isCopied
    )
}
