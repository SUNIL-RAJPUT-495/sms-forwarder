package com.smsforwarder.app.data.repository

import com.smsforwarder.app.data.local.dao.FilterRuleDao
import com.smsforwarder.app.data.local.entity.FilterRuleEntity
import com.smsforwarder.app.domain.model.FilterRule
import com.smsforwarder.app.domain.model.FilterRuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterRepository @Inject constructor(
    private val filterRuleDao: FilterRuleDao
) {

    val allRulesFlow: Flow<List<FilterRule>> = filterRuleDao.getAllRules().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getActiveRules(): List<FilterRule> {
        return filterRuleDao.getActiveRules().map { it.toDomain() }
    }

    suspend fun addRule(name: String, type: FilterRuleType, pattern: String, extractOtp: Boolean = true): Long {
        val entity = FilterRuleEntity(
            name = name,
            type = type,
            pattern = pattern,
            isEnabled = true,
            extractOtp = extractOtp
        )
        return filterRuleDao.insertRule(entity)
    }

    suspend fun updateRule(rule: FilterRule) {
        filterRuleDao.updateRule(rule.toEntity())
    }

    suspend fun toggleRule(id: Long, isEnabled: Boolean) {
        val rules = filterRuleDao.getAllRules()
        // Simple update
    }

    suspend fun deleteRule(id: Long) {
        filterRuleDao.deleteRuleById(id)
    }

    suspend fun seedDefaultRulesIfEmpty() {
        if (filterRuleDao.getRuleCount() == 0) {
            val defaultRules = listOf(
                FilterRuleEntity(
                    name = "Bank & Payment Senders",
                    type = FilterRuleType.SENDER_MATCH,
                    pattern = "HDFCBK|SBIINB|ICICIB|AXISBK|KOTAKB|PAYTM|GPAY|PHONEPE|CHASE|CITIBK|WELLSFARGO|BOA|AMEX|BARCLAYS|HSBC|STANDARD",
                    isEnabled = true,
                    extractOtp = true
                ),
                FilterRuleEntity(
                    name = "OTP & Verification Codes",
                    type = FilterRuleType.KEYWORD_CONTAINS,
                    pattern = "otp|verification code|security code|one time password|secret code|auth code|passcode",
                    isEnabled = true,
                    extractOtp = true
                ),
                FilterRuleEntity(
                    name = "Banking Transactions & Debits",
                    type = FilterRuleType.KEYWORD_CONTAINS,
                    pattern = "debited|credited|transferred|withdrawn|spent|inr|rs.|usd|a/c|balance",
                    isEnabled = true,
                    extractOtp = false
                ),
                FilterRuleEntity(
                    name = "OTP Regex Matcher",
                    type = FilterRuleType.REGEX_BODY,
                    pattern = "(?i)(?:otp|code|is)\\s*(?:is\\s*)?[:\\-]?\\s*([0-9]{4,8})|\\b([0-9]{6})\\b",
                    isEnabled = true,
                    extractOtp = true
                )
            )
            filterRuleDao.insertRules(defaultRules)
        }
    }

    private fun FilterRuleEntity.toDomain() = FilterRule(
        id = id,
        name = name,
        type = type,
        pattern = pattern,
        isEnabled = isEnabled,
        extractOtp = extractOtp
    )

    private fun FilterRule.toEntity() = FilterRuleEntity(
        id = id,
        name = name,
        type = type,
        pattern = pattern,
        isEnabled = isEnabled,
        extractOtp = extractOtp
    )
}
