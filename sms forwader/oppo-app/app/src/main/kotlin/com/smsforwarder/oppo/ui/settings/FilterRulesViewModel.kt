package com.smsforwarder.oppo.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.oppo.data.repository.FilterRepository
import com.smsforwarder.oppo.domain.model.FilterRule
import com.smsforwarder.oppo.domain.model.FilterRuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterRulesUiState(
    val rules: List<FilterRule> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val addDialogType: FilterRuleType = FilterRuleType.EXACT_SENDER,
    val addDialogValue: String = "",
    val addDialogError: String? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class FilterRulesViewModel @Inject constructor(
    private val filterRepository: FilterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilterRulesUiState())
    val uiState: StateFlow<FilterRulesUiState> = _uiState.asStateFlow()

    init {
        seedAndLoad()
    }

    private fun seedAndLoad() {
        viewModelScope.launch {
            // Ensure default rules exist on first launch
            filterRepository.ensureDefaultRulesSeeded()
        }

        viewModelScope.launch {
            filterRepository.observeAllRules().collect { rules ->
                _uiState.update { it.copy(rules = rules, isLoading = false) }
            }
        }
    }

    fun toggleRule(rule: FilterRule, enabled: Boolean) {
        viewModelScope.launch {
            filterRepository.setEnabled(rule.id, enabled)
        }
    }

    fun deleteRule(rule: FilterRule) {
        viewModelScope.launch {
            filterRepository.deleteRule(rule)
            _uiState.update { it.copy(snackbarMessage = "Rule deleted") }
        }
    }

    fun openAddDialog(type: FilterRuleType = FilterRuleType.EXACT_SENDER) {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                addDialogType = type,
                addDialogValue = "",
                addDialogError = null
            )
        }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, addDialogError = null) }
    }

    fun updateAddDialogType(type: FilterRuleType) {
        _uiState.update { it.copy(addDialogType = type, addDialogError = null) }
    }

    fun updateAddDialogValue(value: String) {
        _uiState.update { it.copy(addDialogValue = value, addDialogError = null) }
    }

    fun confirmAddRule() {
        val state = _uiState.value
        val value = state.addDialogValue.trim()

        if (value.isBlank()) {
            _uiState.update { it.copy(addDialogError = "Value cannot be empty") }
            return
        }

        if (value.length < 2) {
            _uiState.update { it.copy(addDialogError = "Value must be at least 2 characters") }
            return
        }

        // Check for duplicate
        val exists = state.rules.any { rule ->
            rule.type == state.addDialogType &&
            rule.value.equals(value, ignoreCase = true)
        }

        if (exists) {
            _uiState.update { it.copy(addDialogError = "A rule with this value already exists") }
            return
        }

        viewModelScope.launch {
            filterRepository.addRule(
                FilterRule(
                    type    = state.addDialogType,
                    value   = value,
                    enabled = true // New user-defined rules are enabled by default
                )
            )
            _uiState.update {
                it.copy(
                    showAddDialog    = false,
                    addDialogValue   = "",
                    addDialogError   = null,
                    snackbarMessage  = "Rule added and enabled"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
