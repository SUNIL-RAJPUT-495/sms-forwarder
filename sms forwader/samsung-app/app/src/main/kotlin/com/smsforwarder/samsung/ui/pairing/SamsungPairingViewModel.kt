package com.smsforwarder.samsung.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.samsung.data.repository.InitiateResult
import com.smsforwarder.samsung.data.repository.SamsungDeviceRepository
import com.smsforwarder.samsung.data.repository.SamsungPairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SamsungPairingUiState(
    val isPaired: Boolean = false,
    val pairingToken: String = "",
    val tokenExpiresAt: Long = 0L,
    val isLoadingToken: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SamsungPairingViewModel @Inject constructor(
    private val pairingRepository: SamsungPairingRepository,
    private val deviceRepository: SamsungDeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SamsungPairingUiState())
    val uiState: StateFlow<SamsungPairingUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(isPaired = deviceRepository.isPaired()) }
    }

    /** Request a fresh pairing token from the backend. */
    fun requestToken() {
        if (_uiState.value.isLoadingToken) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingToken = true, error = null) }
            when (val result = pairingRepository.initiatePairing()) {
                is InitiateResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingToken = false,
                        pairingToken   = result.token,
                        tokenExpiresAt = result.expiresAt,
                        error          = null
                    )
                }
                is InitiateResult.NetworkError -> _uiState.update {
                    it.copy(isLoadingToken = false, error = "Network error — check connection")
                }
                is InitiateResult.Error -> _uiState.update {
                    it.copy(isLoadingToken = false, error = result.message)
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
