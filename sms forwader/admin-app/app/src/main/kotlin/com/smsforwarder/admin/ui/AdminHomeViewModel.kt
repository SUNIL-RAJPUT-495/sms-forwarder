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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

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
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(5000)
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

    fun simulateDemoDevice() {
        viewModelScope.launch {
            val sampleDepts = listOf(
                Triple("Accounts Dept", "+91 98765 11111", "Floor 2 - Room 201"),
                Triple("Sales Team", "+91 98765 22222", "Floor 1 - Main Desk"),
                Triple("HR Department", "+91 98765 33333", "Floor 3 - Cabin 4"),
                Triple("Operations", "+91 98765 44444", "Ground Floor - Gate 1"),
                Triple("Finance Wing", "+91 98765 55555", "Floor 4 - Block B")
            )
            val sample = sampleDepts.random()
            adminRepository.simulateRegisterDevice(sample.first, sample.second, sample.third)
            fetchData(showLoading = false)
        }
    }

    fun simulateTestSms() {
        viewModelScope.launch {
            var targetDevice = _uiState.value.devices.randomOrNull()
            if (targetDevice == null) {
                simulateDemoDevice()
                delay(300)
                targetDevice = _uiState.value.devices.randomOrNull()
            }

            val sampleNotifications = listOf(
                "HDFC-BANK" to "Your OTP for online payment of Rs. 14,500 at Amazon is ${Random.nextInt(100000, 999999)}. Valid for 10 mins.",
                "ICICI-ALERT" to "Dear Customer, A/c XX8920 has been debited by Rs 2,500.00 on 01-Sep-26. Info: UPI/VendorPay.",
                "SBI-BANK" to "Salary credit of Rs 65,000.00 done in A/c XX4091 on 01-Sep-26. Available Bal: Rs 1,42,900.",
                "SWIGGY" to "Your order #91024 has been delivered to Gate 2 reception by delivery partner.",
                "RAZORPAY" to "Use ${Random.nextInt(100000, 999999)} as your verification code to complete sign-in to Razorpay Dashboard."
            )

            val (sender, body) = sampleNotifications.random()

            adminRepository.simulateSendSms(
                deviceId = targetDevice?.deviceId ?: "DEV-TEST",
                departmentName = targetDevice?.departmentName ?: "Accounts Dept",
                mobileNumber = targetDevice?.mobileNumber ?: "+91 98765 11111",
                address = targetDevice?.address ?: "Main Office",
                sender = sender,
                body = body
            )

            fetchData(showLoading = false)
        }
    }
}
