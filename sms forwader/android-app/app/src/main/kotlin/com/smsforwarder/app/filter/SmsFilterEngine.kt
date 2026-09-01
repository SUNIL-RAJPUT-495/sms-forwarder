package com.smsforwarder.app.filter

import com.smsforwarder.app.data.repository.FilterRepository
import com.smsforwarder.app.domain.model.FilterRule
import com.smsforwarder.app.domain.model.FilterRuleType
import com.smsforwarder.app.domain.model.SmsMessageData
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class FilterEvaluationResult(
    val shouldForward: Boolean,
    val matchedRuleName: String? = null,
    val extractedOtp: String? = null,
    val rejectionReason: String? = null
)

@Singleton
class SmsFilterEngine @Inject constructor(
    private val filterRepository: FilterRepository
) {

    companion object {
        // Regex patterns for extracting OTPs accurately
        private val OTP_PATTERNS = listOf(
            Pattern.compile("(?i)(?:otp|code|is|passcode|secret|pin)\\s*(?:is\\s*)?[:\\-\\s]?\\s*([0-9]{4,8})"),
            Pattern.compile("(?i)([0-9]{4,8})\\s*(?:is your|is the)\\s*(?:otp|code|verification)"),
            Pattern.compile("\\b([0-9]{6})\\b") // Fallback 6-digit match
        )
    }

    suspend fun evaluate(sms: SmsMessageData): FilterEvaluationResult {
        val rules = filterRepository.getActiveRules()
        if (rules.isEmpty()) {
            // Default allow if no rules configured
            return FilterEvaluationResult(
                shouldForward = true,
                matchedRuleName = "Default Allow (No rules)",
                extractedOtp = extractOtp(sms.body)
            )
        }

        for (rule in rules) {
            val isMatch = evaluateRule(rule, sms)
            if (isMatch) {
                val otp = if (rule.extractOtp) extractOtp(sms.body) else null
                return FilterEvaluationResult(
                    shouldForward = true,
                    matchedRuleName = rule.name,
                    extractedOtp = otp
                )
            }
        }

        return FilterEvaluationResult(
            shouldForward = false,
            rejectionReason = "No matching filter rule for sender '${sms.sender}'"
        )
    }

    private fun evaluateRule(rule: FilterRule, sms: SmsMessageData): Boolean {
        return when (rule.type) {
            FilterRuleType.SENDER_MATCH -> {
                val patterns = rule.pattern.split("|").map { it.trim().uppercase() }
                val senderUpper = sms.sender.uppercase()
                patterns.any { senderUpper.contains(it) }
            }
            FilterRuleType.KEYWORD_CONTAINS -> {
                val keywords = rule.pattern.split("|").map { it.trim().lowercase() }
                val bodyLower = sms.body.lowercase()
                keywords.any { bodyLower.contains(it) }
            }
            FilterRuleType.REGEX_BODY -> {
                runCatching {
                    val regex = rule.pattern.toRegex(RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(sms.body)
                }.getOrDefault(false)
            }
            FilterRuleType.MIN_LENGTH -> {
                val minLen = rule.pattern.toIntOrNull() ?: 0
                sms.body.length >= minLen
            }
        }
    }

    fun extractOtp(body: String): String? {
        for (pattern in OTP_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val group1 = matcher.group(1)
                if (!group1.isNullOrEmpty() && group1.length in 4..8) {
                    return group1
                }
            }
        }
        return null
    }
}
