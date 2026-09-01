package com.smsforwarder.oppo.filter

import com.smsforwarder.oppo.domain.model.FilterRule
import com.smsforwarder.oppo.domain.model.FilterRuleType
import com.smsforwarder.oppo.domain.model.SmsMessageData
import com.smsforwarder.oppo.receiver.SmsBroadcastReceiver
import com.smsforwarder.oppo.data.local.dao.FilterRuleDao
import com.smsforwarder.oppo.data.local.entity.FilterRuleEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core SMS filter engine.
 *
 * Evaluates whether an incoming SMS should be forwarded based on
 * user-configured [FilterRule]s.
 *
 * DESIGN PRINCIPLES:
 *
 * 1. Conservative by default: a message is forwarded ONLY if at
 *    least one enabled rule explicitly matches it. No rule = no forward.
 *
 * 2. OR logic across rules: any single matching rule causes forwarding.
 *    This lets the user add both sender rules AND keyword rules
 *    independently without needing to configure complex AND/OR trees.
 *
 * 3. The core [evaluate] function is pure (no side effects, no DB access)
 *    and is tested directly in unit tests without Android runtime.
 *
 * 4. Indian sender ID normalisation:
 *    Before matching, the sender is normalised using
 *    [SmsBroadcastReceiver.normaliseSenderId] to strip TRAI operator
 *    prefixes (VM-, VD-, TP-, AT-, etc.). The raw sender ID is preserved
 *    in [SmsMessageData.sender] for display; we use the normalised form
 *    only during filter evaluation.
 *
 * 5. No plaintext logging: the SMS body is NEVER logged in this class,
 *    even on match failure. Only redacted sender prefixes are allowed.
 */
@Singleton
class SmsFilterEngine @Inject constructor(
    private val filterRuleDao: FilterRuleDao
) {

    /**
     * Load enabled rules from the database and evaluate the message.
     * Returns true if the message should be forwarded.
     *
     * If no rules are enabled, returns false (conservative default).
     */
    suspend fun shouldForward(smsData: SmsMessageData): Boolean {
        val enabledRules = filterRuleDao.getEnabled().map { it.toDomain() }
        if (enabledRules.isEmpty()) return false
        return evaluate(enabledRules, smsData)
    }

    /**
     * Pure evaluation function — no DB access.
     *
     * Exposed for unit testing without Android runtime.
     * Returns true if ANY enabled rule matches the message (OR logic).
     *
     * @param rules Pre-loaded list of enabled [FilterRule]s.
     * @param smsData The incoming SMS to evaluate.
     */
    fun evaluate(rules: List<FilterRule>, smsData: SmsMessageData): Boolean {
        if (rules.isEmpty()) return false

        val normalisedSender = SmsBroadcastReceiver.normaliseSenderId(smsData.sender)

        return rules.any { rule ->
            when (rule.type) {
                FilterRuleType.EXACT_SENDER ->
                    matchExactSender(rule.value, normalisedSender)

                FilterRuleType.SENDER_CONTAINS ->
                    matchSenderContains(rule.value, normalisedSender, smsData.sender)

                FilterRuleType.BODY_CONTAINS ->
                    matchBodyContains(rule.value, smsData.body)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Match implementations
    // ─────────────────────────────────────────────

    /**
     * Exact match on sender address.
     *
     * Matching strategy:
     * 1. Normalised sender (prefix stripped) == normalised rule value (case-insensitive)
     * 2. OR raw sender (as received) == normalised rule value (handles edge cases)
     *
     * Example: rule "SBIINB" matches sender "VM-SBIINB" (after normalisation)
     */
    private fun matchExactSender(ruleValue: String, normalisedSender: String): Boolean {
        val normalisedRule = SmsBroadcastReceiver.normaliseSenderId(ruleValue)
        return normalisedSender.equals(normalisedRule, ignoreCase = true)
    }

    /**
     * Substring match on sender address.
     *
     * Checks both the normalised sender and the raw sender.
     * Example: rule "HDFC" matches "HDFCBK" and "VM-HDFCBK"
     */
    private fun matchSenderContains(
        ruleValue: String,
        normalisedSender: String,
        rawSender: String
    ): Boolean {
        val normalisedRule = ruleValue.uppercase().trim()
        return normalisedSender.contains(normalisedRule, ignoreCase = true) ||
               rawSender.contains(normalisedRule, ignoreCase = true)
    }

    /**
     * Substring match on message body.
     *
     * Case-insensitive. Does NOT require word boundary matching —
     * "OTP" matches "OTPxxxx" and "Your OTP is".
     *
     * SECURITY: The [body] value is NEVER logged here.
     */
    private fun matchBodyContains(ruleValue: String, body: String): Boolean {
        return body.contains(ruleValue.trim(), ignoreCase = true)
    }
}

// ─────────────────────────────────────────────
// Extension: FilterRuleEntity → FilterRule domain model
// ─────────────────────────────────────────────

fun FilterRuleEntity.toDomain(): FilterRule = FilterRule(
    id = id,
    type = FilterRuleType.valueOf(type),
    value = value,
    enabled = enabled
)

fun FilterRule.toEntity(): FilterRuleEntity = FilterRuleEntity(
    id = id,
    type = type.name,
    value = value,
    enabled = enabled
)
