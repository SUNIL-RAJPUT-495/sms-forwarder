package com.smsforwarder.app.ui.pairing

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.data.repository.PairingRepository
import com.smsforwarder.app.data.repository.PairingResult
import com.smsforwarder.app.data.repository.PairingSessionInfo
import com.smsforwarder.app.domain.model.DeviceInfo
import com.smsforwarder.app.domain.model.DeviceRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val deviceInfo: DeviceInfo? = null,
    val isInitiating: Boolean = false,
    val pairingSession: PairingSessionInfo? = null,
    val qrBitmap: Bitmap? = null,
    val inputCode: String = "",
    val isPairing: Boolean = false,
    val pairingSuccessMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.deviceInfoFlow.collect { info ->
                _uiState.update { it.copy(deviceInfo = info) }
                if ((info.role == DeviceRole.RECEIVER || info.role == DeviceRole.DUAL) && _uiState.value.pairingSession == null) {
                    initiatePairing()
                }
            }
        }
    }

    fun initiatePairing() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitiating = true, errorMessage = null) }
            val result = pairingRepository.initiatePairing()
            result.onSuccess { session ->
                val bitmap = generateQrBitmap(session.qrCodeUri)
                _uiState.update {
                    it.copy(
                        isInitiating = false,
                        pairingSession = session,
                        qrBitmap = bitmap
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isInitiating = false,
                        errorMessage = err.message
                    )
                }
            }
        }
    }

    fun onCodeChanged(code: String) {
        _uiState.update { it.copy(inputCode = code) }
    }

    fun submitPairing(rawInput: String? = null) {
        val codeToSubmit = rawInput ?: _uiState.value.inputCode
        if (codeToSubmit.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, errorMessage = null, pairingSuccessMessage = null) }
            when (val res = pairingRepository.completePairing(codeToSubmit)) {
                is PairingResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingSuccessMessage = "Successfully paired with ${res.destinationName}!"
                        )
                    }
                }
                is PairingResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
