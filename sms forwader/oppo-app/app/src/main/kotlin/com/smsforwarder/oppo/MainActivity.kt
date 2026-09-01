package com.smsforwarder.oppo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smsforwarder.oppo.data.repository.DeviceRepository
import com.smsforwarder.oppo.ui.navigation.OppoNavGraph
import com.smsforwarder.oppo.ui.pairing.PairingViewModel
import com.smsforwarder.oppo.ui.theme.SmsForwarderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-activity entry point for the OPPO (Source) app.
 *
 * Phase 5 additions:
 *
 * 1. ADB PAIRING INTENT:
 *    When OPPO's screen is broken, pairing is triggered via ADB:
 *      adb shell am start \
 *        -n com.smsforwarder.oppo/.MainActivity \
 *        --es pairing_token "A3F-7K2"
 *    This activity extracts the `pairing_token` extra and routes it to
 *    [PairingViewModel.handleAdbToken]. Works for both:
 *      - Fresh launch (onCreate)
 *      - Re-launch while app is running (onNewIntent) with FLAG_ACTIVITY_SINGLE_TOP
 *
 * 2. DEVICE REGISTRATION:
 *    [DeviceRepository.registerIfNeeded] is called on every launch.
 *    It is idempotent — no-op if already registered.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    // Shared with the PairingScreen composable via hiltViewModel()
    private val pairingViewModel: PairingViewModel by viewModels()

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register OPPO with the backend (idempotent — no-op if already registered)
        activityScope.launch {
            deviceRepository.registerIfNeeded()
        }

        setContent {
            SmsForwarderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OppoNavGraph()
                }
            }
        }

        // Handle pairing intent on launch (ADB or deeplink)
        handlePairingIntent(intent)
    }

    /** Called when the activity is already running and receives a new intent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingIntent(intent)
    }

    /**
     * Extract the pairing_token extra and forward to [PairingViewModel].
     *
     * The token is passed as a String extra under the key "pairing_token".
     * The ViewModel handles navigating to PairingScreen and submitting automatically.
     */
    private fun handlePairingIntent(intent: Intent?) {
        val token = intent?.getStringExtra(EXTRA_PAIRING_TOKEN)?.trim()
        if (!token.isNullOrBlank()) {
            Log.i(TAG, "ADB pairing token received (length=${token.length})")
            pairingViewModel.handleAdbToken(token)
        }
    }

    companion object {
        private const val TAG = "OppoMainActivity"
        const val EXTRA_PAIRING_TOKEN = "pairing_token"
    }
}
