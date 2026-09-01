package com.smsforwarder.samsung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smsforwarder.samsung.ui.navigation.SamsungNavGraph
import com.smsforwarder.samsung.ui.theme.SmsForwarderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsForwarderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SamsungNavGraph()
                }
            }
        }
    }
}
