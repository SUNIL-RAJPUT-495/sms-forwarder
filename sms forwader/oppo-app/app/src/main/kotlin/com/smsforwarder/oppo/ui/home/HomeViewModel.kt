package com.smsforwarder.oppo.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smsforwarder.oppo.data.local.dao.PendingMessageDao
import com.smsforwarder.oppo.domain.model.SmsMessageData
import com.smsforwarder.oppo.filter.SmsFilterEngine
import com.smsforwarder.oppo.filter.SmsForwardingPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

data class HomeUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val destinationDeviceName: String = "Samsung",
    val pendingCount: Int = 0,
    val isBatteryOptimized: Boolean = false,
    val testSmsResult: TestSmsResult? = null
)

sealed class TestSmsResult {
    object Sending : TestSmsResult()
    data class FilteredOut(val reason: String) : TestSmsResult()
    data class Success(val messageId: String) : TestSmsResult()
    data class Error(val message: String) : TestSmsResult()
}

/**
 * ViewModel for the OPPO Home screen.
 *
 * Phase 3: sendTestSms() is fully wired into the real
 * [SmsFilterEngine] + [SmsForwardingPipeline] pipeline.
 *
 * The test SMS has sender "TESTBANK" and body containing "OTP".
 * To see it forwarded, enable either:
 *   - EXACT_SENDER rule: TESTBANK
 *   - BODY_CONTAINS rule: OTP
 * in the Filter Rules screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @Named("encrypted") private val prefs: SharedPreferences,
    private val pendingMessageDao: PendingMessageDao,
    private val filterEngine: SmsFilterEngine,
    private val forwardingPipeline: SmsForwardingPipeline,
    private val deviceRepository: com.smsforwarder.oppo.data.repository.DeviceRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPairingState()
        observePendingCount()
        checkBatteryOptimization()
    }

    fun loadPairingState() {
        val isPaired = prefs.getBoolean(PREF_IS_PAIRED, false)
        val destName = prefs.getString(PREF_DEST_NAME, "Samsung") ?: "Samsung"
        _uiState.update {
            it.copy(isLoading = false, isPaired = isPaired, destinationDeviceName = destName)
        }
    }

    fun unpair() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            deviceRepository.revokeDevice()
            loadPairingState()
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            pendingMessageDao.observePendingCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun checkBatteryOptimization() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == false
        _uiState.update { it.copy(isBatteryOptimized = isOptimized) }
    }

    /**
     * Sends a test SMS through the full filter + pipeline path.
     *
     * Creates a synthetic [SmsMessageData] with sender="TESTBANK" and
     * body "Test transaction OTP: 123456", then runs it through the
     * [SmsFilterEngine] and [SmsForwardingPipeline] exactly as a real
     * bank SMS would be processed.
     *
     * RESULT:
     *   - If no rule matches TESTBANK or "OTP" → shows [TestSmsResult.FilteredOut]
     *     with a hint to enable the TESTBANK sender rule.
     *   - If a rule matches → enqueued in PendingMessageDao → shows [TestSmsResult.Success]
     *     with the message ID. Phase 4 will encrypt it; Phase 6 will deliver via FCM.
     */
    fun sendTestSms() {
        viewModelScope.launch {
            _uiState.update { it.copy(testSmsResult = TestSmsResult.Sending) }

            try {
                val testSms = SmsMessageData(
                    messageId   = UUID.randomUUID().toString(),
                    sender      = "TESTBANK",
                    body        = "Test transaction OTP: 123456. Your account has been debited Rs.1.00.",
                    timestampMs = System.currentTimeMillis()
                )

                val matches = filterEngine.shouldForward(testSms)

                if (!matches) {
                    _uiState.update {
                        it.copy(
                            testSmsResult = TestSmsResult.FilteredOut(
                                "No active rule matches sender 'TESTBANK'. " +
                                "Enable the TESTBANK sender rule or the 'OTP' keyword rule " +
                                "in Filter Rules to test forwarding."
                            )
                        )
                    }
                    return@launch
                }

                forwardingPipeline.enqueue(testSms)

                _uiState.update {
                    it.copy(testSmsResult = TestSmsResult.Success(testSms.messageId))
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(testSmsResult = TestSmsResult.Error("Internal error (details suppressed)"))
                }
            }
        }
    }

    fun clearTestResult() {
        _uiState.update { it.copy(testSmsResult = null) }
    }

    companion object {
        const val PREF_IS_PAIRED = "is_paired"
        const val PREF_DEST_NAME = "dest_device_name"
    }
}

