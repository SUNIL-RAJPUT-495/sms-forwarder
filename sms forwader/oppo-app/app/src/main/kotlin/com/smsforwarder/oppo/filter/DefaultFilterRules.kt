package com.smsforwarder.oppo.filter

import com.smsforwarder.oppo.domain.model.FilterRule
import com.smsforwarder.oppo.domain.model.FilterRuleType

/**
 * Default SMS filter rules shipped with the app.
 *
 * ALL RULES ARE DISABLED BY DEFAULT. The user must explicitly enable
 * the rules they want. This is intentional — we never forward arbitrary
 * SMS without the user's explicit opt-in.
 *
 * ─────────────────────────────────────────────
 * Indian Bank Sender IDs (TRAI Alpha Sender ID format)
 *
 * TRAI mandates that transactional SMS from financial institutions use
 * registered Sender IDs. Banks register these with their telecom
 * operator. The sender ID appears as the originating address on Android.
 *
 * Common patterns:
 *   HDFCBK, SBIINB, ICICIB, AXISBK, KOTAKB, PNBSMS, BOIIND, SCISMS
 *
 * Note: Operator prefixes (VM-, VD-, TP-) are stripped before matching
 * by SmsBroadcastReceiver.normaliseSenderId(), so only the bare ID is
 * needed in the rule value.
 *
 * ─────────────────────────────────────────────
 * BODY_CONTAINS keywords:
 *
 * These are common terms that appear in transactional bank SMS:
 * OTP, debit, credit, transaction, alert, balance, withdrawn,
 * credited, debited, transfer, payment, IMPS, NEFT, RTGS, UPI
 *
 * Using BODY_CONTAINS rules as a catch-all allows you to forward
 * bank SMS from senders not in your EXACT_SENDER list.
 * ─────────────────────────────────────────────
 */
object DefaultFilterRules {

    val SENDER_RULES: List<FilterRule> = listOf(
        // HDFC Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "HDFCBK",  enabled = false),
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "HDFCBN",  enabled = false),

        // State Bank of India
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "SBIINB",  enabled = false),
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "SBIPSG",  enabled = false),

        // ICICI Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "ICICIB",  enabled = false),
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "ICICIN",  enabled = false),

        // Axis Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "AXISBK",  enabled = false),

        // Kotak Mahindra Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "KOTAKB",  enabled = false),

        // Punjab National Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "PNBSMS",  enabled = false),

        // Bank of India
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "BOIIND",  enabled = false),

        // Standard Chartered
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "SCISMS",  enabled = false),

        // Paytm Payments Bank
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "PAYTMB",  enabled = false),

        // PhonePe / UPI alerts (varies by bank)
        FilterRule(type = FilterRuleType.SENDER_CONTAINS, value = "PHONEPE", enabled = false),

        // Test rule for Test Mode — always pre-added, enabled by default in test builds
        FilterRule(type = FilterRuleType.EXACT_SENDER, value = "TESTBANK", enabled = false),
    )

    val KEYWORD_RULES: List<FilterRule> = listOf(
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "OTP",         enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "debit",       enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "credit",      enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "debited",     enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "credited",    enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "transaction", enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "alert",       enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "balance",     enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "transfer",    enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "payment",     enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "IMPS",        enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "NEFT",        enabled = false),
        FilterRule(type = FilterRuleType.BODY_CONTAINS, value = "UPI",         enabled = false),
    )

    val ALL: List<FilterRule> = SENDER_RULES + KEYWORD_RULES
}
