package com.smsforwarder.oppo.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.oppo.data.repository.DeviceRepository
import com.smsforwarder.oppo.data.repository.PairingRepository
import com.smsforwarder.oppo.data.repository.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val isPaired: Boolean = false,
    val destinationName: String = "",
    val isLoading: Boolean = false,
    /** Outcome of the most recent pairing attempt. Null = no attempt yet. */
    val pairingResult: PairingOutcome? = null,
    /** Set when pairing is triggered via ADB intent — auto-submitted. */
    val adbToken: String? = null
)

sealed class PairingOutcome {
    data class Success(val deviceName: String) : PairingOutcome()
    object InvalidToken    : PairingOutcome()
    object TokenUsed       : PairingOutcome()
    object NetworkError    : PairingOutcome()
    data class Error(val msg: String) : PairingOutcome()
}

/**
 * ViewModel for the OPPO Pairing screen.
 *
 * Handles two entry paths:
 *   1. **ADB intent** (primary for broken-screen OPPO):
 *      Intent extra `pairing_token` is received by [MainActivity] and
 *      forwarded here via [handleAdbToken]. Pairing is triggered automatically.
 *
 *   2. **Manual entry** (if screen is partially visible):
 *      User types the code via [submitToken].
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                isPaired        = deviceRepository.isPaired(),
                destinationName = "" // loaded from prefs in DeviceRepository
            )
        }
    }

    /**
     * Called by [MainActivity] when the app is launched with an ADB intent
     * containing the `pairing_token` extra.
     *
     * Immediately kicks off the pairing handshake — no user interaction needed.
     */
    fun handleAdbToken(token: String) {
        _uiState.update { it.copy(adbToken = token) }
        submitToken(token)
    }

    /**
     * Submit a pairing token (from ADB or manual entry).
     */
    fun submitToken(token: String) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pairingResult = null) }

            val outcome = when (val result = pairingRepository.completePairing(token)) {
                is PairingResult.Success       -> {
                    _uiState.update { it.copy(isPaired = true, destinationName = result.destinationName) }
                    PairingOutcome.Success(result.destinationName)
                }
                is PairingResult.InvalidToken  -> PairingOutcome.InvalidToken
                is PairingResult.TokenAlreadyUsed -> PairingOutcome.TokenUsed
                is PairingResult.NetworkError  -> PairingOutcome.NetworkError
                is PairingResult.Error         -> PairingOutcome.Error(result.message)
            }

            _uiState.update { it.copy(isLoading = false, pairingResult = outcome) }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(pairingResult = null, adbToken = null) }
    }
}
