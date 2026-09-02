package com.smsforwarder.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.admin.data.repository.AdminRepository
import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminHomeUiState(
    val devices: List<AdminDeviceDto> = emptyList(),
    val messages: List<AdminMessageDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchDeviceQuery: String = "",
    val searchMessageQuery: String = "",
    val selectedDeviceForModal: AdminDeviceDto? = null,
    val isSoundEnabled: Boolean = true
)

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminHomeUiState())
    val uiState: StateFlow<AdminHomeUiState> = _uiState.asStateFlow()

    init {
        fetchData(showLoading = true)
        startRealtimeSseStream()
        startAutoRefresh()
    }

    /**
     * Connects to backend SSE stream for instant (<100ms) live notification delivery
     */
    private fun startRealtimeSseStream() {
        viewModelScope.launch {
            adminRepository.observeRealtimeMessages().collectLatest { newMsg ->
                _uiState.update { state ->
                    val existingIdx = state.messages.indexOfFirst {
                        (it.messageId.isNotBlank() && it.messageId == newMsg.messageId) ||
                                (it.id.isNotBlank() && it.id == newMsg.id)
                    }
                    val updatedList = if (existingIdx != -1) {
                        state.messages.toMutableList().apply { set(existingIdx, newMsg) }
                    } else {
                        listOf(newMsg) + state.messages
                    }
                    state.copy(messages = updatedList, errorMessage = null)
                }
                // Refresh devices to update notification counters
                fetchData(showLoading = false)
            }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(3000) // Fast 3-second fallback polling
                fetchData(showLoading = false)
            }
        }
    }

    fun fetchData(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isRefreshing = true) }
            }

            val devicesResult = adminRepository.fetchDevices()
            val messagesResult = adminRepository.fetchMessages()

            val devices = devicesResult.getOrDefault(emptyList())
            val messages = messagesResult.getOrDefault(emptyList())

            _uiState.update { state ->
                state.copy(
                    devices = devices,
                    messages = messages,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = if (devicesResult.isFailure && messagesResult.isFailure) "Offline or server unreachable" else null
                )
            }
        }
    }

    fun setSearchDeviceQuery(query: String) {
        _uiState.update { it.copy(searchDeviceQuery = query) }
    }

    fun setSearchMessageQuery(query: String) {
        _uiState.update { it.copy(searchMessageQuery = query) }
    }

    fun selectDeviceForModal(device: AdminDeviceDto?) {
        _uiState.update { it.copy(selectedDeviceForModal = device, searchMessageQuery = "") }
    }

    fun toggleSound() {
        _uiState.update { it.copy(isSoundEnabled = !it.isSoundEnabled) }
    }
}
