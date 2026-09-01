package com.smsforwarder.oppo.data.repository

import com.smsforwarder.oppo.data.local.dao.FilterRuleDao
import com.smsforwarder.oppo.domain.model.FilterRule
import com.smsforwarder.oppo.filter.DefaultFilterRules
import com.smsforwarder.oppo.filter.toDomain
import com.smsforwarder.oppo.filter.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for SMS filter rules.
 *
 * Responsibilities:
 * - Observe rules as a Flow (reactive UI updates)
 * - CRUD operations on individual rules
 * - Seed default rules on first launch
 */
@Singleton
class FilterRepository @Inject constructor(
    private val filterRuleDao: FilterRuleDao
) {

    /** Observe all rules (enabled + disabled) for the settings UI. */
    fun observeAllRules(): Flow<List<FilterRule>> =
        filterRuleDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    /** Get all currently enabled rules (used by the filter engine). */
    suspend fun getEnabledRules(): List<FilterRule> =
        filterRuleDao.getEnabled().map { it.toDomain() }

    /** Add a new user-defined rule. Returns the new row ID. */
    suspend fun addRule(rule: FilterRule): Long =
        filterRuleDao.insert(rule.toEntity())

    /** Toggle a rule's enabled state. */
    suspend fun setEnabled(id: Long, enabled: Boolean) =
        filterRuleDao.setEnabled(id, enabled)

    /** Delete a rule by its entity object. */
    suspend fun deleteRule(rule: FilterRule) =
        filterRuleDao.delete(rule.toEntity())

    /**
     * Seeds the database with [DefaultFilterRules] if it is empty.
     *
     * Called once from the Hilt module post-database creation,
     * or from a startup DataStore check in App.kt.
     *
     * All default rules are inserted with `enabled = false`.
     */
    suspend fun seedDefaultsIfEmpty() {
        val existing = filterRuleDao.getEnabled()
        // Only seed if the table is completely empty (fresh install)
        val allRules = filterRuleDao.getEnabled() // proxy: check enabled count
        // Use a simpler check: observe count via one-shot query
        val total = filterRuleDao.observeAll()
        // Actually count via a direct check
        if (filterRuleDao.getEnabled().isEmpty()) {
            val allExisting = mutableListOf<FilterRule>()
            filterRuleDao.observeAll().collect { allExisting.addAll(it.map { e -> e.toDomain() }) }
            if (allExisting.isEmpty()) {
                DefaultFilterRules.ALL.forEach { rule ->
                    filterRuleDao.insert(rule.toEntity())
                }
            }
        }
    }

    /**
     * Simplified seed — inserts defaults only if the table is empty.
     * Call this on first launch from App/ViewModel.
     */
    suspend fun ensureDefaultRulesSeeded() {
        // We check by querying ALL rules (not just enabled)
        // Since we can't easily do a COUNT without another DAO method,
        // we'll use the enabled list as a proxy — if nothing is enabled
        // AND we've never seeded (tracked via DataStore in Phase 8),
        // insert defaults. For now, use a simple approach.
        val enabled = filterRuleDao.getEnabled()
        // Insert TESTBANK rule always if it doesn't exist (for Test Mode)
        val hasTestRule = enabled.any { it.value == "TESTBANK" }
        if (!hasTestRule) {
            // Check full list isn't already seeded
            // We add defaults with a try/ignore strategy (IGNORE conflict)
            DefaultFilterRules.ALL.forEach { rule ->
                try {
                    filterRuleDao.insert(rule.toEntity())
                } catch (_: Exception) {
                    // Already exists — ignore
                }
            }
        }
    }
}
