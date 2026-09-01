package com.smsforwarder.samsung.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.samsung.data.local.dao.ForwardedMessageDao
import com.smsforwarder.samsung.data.repository.SamsungPairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class HomeUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val sourceDeviceName: String = "OPPO",
    val totalReceived: Int = 0,
    val lastReceivedAt: Long? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @Named("encrypted") private val prefs: SharedPreferences,
    private val messageDao: ForwardedMessageDao,
    private val pairingRepository: SamsungPairingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPairingState()
        observeMessageCount()
    }

    fun loadPairingState() {
        val isPaired = prefs.getBoolean("is_paired", false)
        val srcName = prefs.getString("source_device_name", "OPPO") ?: "OPPO"
        _uiState.update { it.copy(isLoading = false, isPaired = isPaired, sourceDeviceName = srcName) }

        viewModelScope.launch {
            pairingRepository.initiatePairing()
            pairingRepository.syncPendingMessages()
            val updatedIsPaired = prefs.getBoolean("is_paired", false)
            val updatedSrcName = prefs.getString("source_device_name", "OPPO") ?: "OPPO"
            _uiState.update { it.copy(isPaired = updatedIsPaired, sourceDeviceName = updatedSrcName) }
        }
    }

    fun unpair() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            pairingRepository.revokeDevice()
            loadPairingState()
        }
    }

    private fun observeMessageCount() {
        viewModelScope.launch {
            messageDao.observeCount().collect { count ->
                _uiState.update { it.copy(totalReceived = count) }
            }
        }
    }
}
