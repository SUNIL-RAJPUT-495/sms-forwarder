package com.smsforwarder.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsforwarder.app.data.repository.DeviceRepository
import com.smsforwarder.app.domain.model.DeviceRole
import com.smsforwarder.app.notification.UniversalNotificationManager
import com.smsforwarder.app.service.SmsForwarderService
import com.smsforwarder.app.ui.filters.FilterRulesScreen
import com.smsforwarder.app.ui.filters.FilterRulesViewModel
import com.smsforwarder.app.ui.history.HistoryScreen
import com.smsforwarder.app.ui.history.HistoryViewModel
import com.smsforwarder.app.ui.home.HomeScreen
import com.smsforwarder.app.ui.home.HomeViewModel
import com.smsforwarder.app.ui.mode.ModeSelectionScreen
import com.smsforwarder.app.ui.navigation.Destination
import com.smsforwarder.app.ui.pairing.PairingScreen
import com.smsforwarder.app.ui.pairing.PairingViewModel
import com.smsforwarder.app.ui.settings.BatteryOptimizationScreen
import com.smsforwarder.app.ui.settings.SettingsScreen
import com.smsforwarder.app.ui.theme.UniversalSmsForwarderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    private val homeViewModel: HomeViewModel by viewModels()
    private val pairingViewModel: PairingViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val filterRulesViewModel: FilterRulesViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        startRelayServiceIfSender()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()
        handleIntent(intent)

        setContent {
            UniversalSmsForwarderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == UniversalNotificationManager.ACTION_COPY_OTP) {
            val otp = intent.getStringExtra(UniversalNotificationManager.EXTRA_OTP)
            if (!otp.isNullOrBlank()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("OTP", otp))
                Toast.makeText(this, "OTP $otp copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }

        val pairingToken = intent.getStringExtra("pairing_token")
            ?: intent.getStringExtra("pairing_code")
            ?: intent.data?.getQueryParameter("token")
            ?: intent.data?.getQueryParameter("code")

        if (!pairingToken.isNullOrBlank()) {
            pairingViewModel.submitPairing(pairingToken)
            Toast.makeText(this, "Submitting pairing code: $pairingToken", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECEIVE_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CAMERA)
        }

        if (perms.isNotEmpty()) {
            requestPermissionLauncher.launch(perms.toTypedArray())
        } else {
            startRelayServiceIfSender()
        }
    }

    private fun startRelayServiceIfSender() {
        CoroutineScope(Dispatchers.IO).launch {
            val info = deviceRepository.deviceInfoFlow.first()
            if (info.role == DeviceRole.SENDER || info.role == DeviceRole.DUAL) {
                val serviceIntent = Intent(this@MainActivity, SmsForwarderService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Destination.Home.route
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigatePairing = { navController.navigate(Destination.Pairing.route) },
                    onNavigateHistory = { navController.navigate(Destination.History.route) },
                    onNavigateFilters = { navController.navigate(Destination.Filters.route) },
                    onNavigateSettings = { navController.navigate(Destination.Settings.route) },
                    onNavigateBatteryGuide = { navController.navigate(Destination.BatteryGuide.route) },
                    onNavigateModeSelection = { navController.navigate(Destination.ModeSelection.route) }
                )
            }

            composable(Destination.Pairing.route) {
                PairingScreen(
                    viewModel = pairingViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Destination.History.route) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Destination.Filters.route) {
                FilterRulesScreen(
                    viewModel = filterRulesViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Destination.Settings.route) {
                SettingsScreen(
                    deviceRepository = deviceRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateModeSelection = { navController.navigate(Destination.ModeSelection.route) },
                    onNavigateBatteryGuide = { navController.navigate(Destination.BatteryGuide.route) }
                )
            }

            composable(Destination.BatteryGuide.route) {
                BatteryOptimizationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Destination.ModeSelection.route) {
                val currentRole = homeViewModel.uiState.value.deviceInfo?.role ?: DeviceRole.RECEIVER
                ModeSelectionScreen(
                    currentRole = currentRole,
                    onRoleSelected = { newRole ->
                        homeViewModel.setDeviceRole(newRole)
                        startRelayServiceIfSender()
                    },
                    onNavigateHome = { navController.popBackStack() }
                )
            }
        }
    }
}
