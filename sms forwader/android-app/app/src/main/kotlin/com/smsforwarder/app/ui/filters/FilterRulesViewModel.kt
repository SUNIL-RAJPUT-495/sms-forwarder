package com.smsforwarder.app.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.app.data.repository.FilterRepository
import com.smsforwarder.app.domain.model.FilterRule
import com.smsforwarder.app.domain.model.FilterRuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterRulesUiState(
    val rules: List<FilterRule> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FilterRulesViewModel @Inject constructor(
    private val filterRepository: FilterRepository
) : ViewModel() {

    val uiState: StateFlow<FilterRulesUiState> = filterRepository.allRulesFlow
        .map { rules -> FilterRulesUiState(rules = rules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilterRulesUiState())

    fun toggleRule(rule: FilterRule) {
        viewModelScope.launch {
            filterRepository.updateRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun addRule(name: String, type: FilterRuleType, pattern: String, extractOtp: Boolean) {
        viewModelScope.launch {
            filterRepository.addRule(name, type, pattern, extractOtp)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            filterRepository.deleteRule(id)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            filterRepository.seedDefaultRulesIfEmpty()
        }
    }
}
