package com.smsforwarder.samsung

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smsforwarder.samsung.crypto.KeyManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var keyManager: KeyManager

    override fun onCreate() {
        super.onCreate()

        // Ensure the RSA-2048 key pair exists in the Android Keystore.
        // This is idempotent — no-op if the key already exists.
        // Must run before any FCM message arrives that requires decryption.
        try {
            keyManager.ensureKeyPairExists()
        } catch (e: Exception) {
            // Log at ERROR — the app can still start, but decryption will fail
            // until the key is available. Phase 8 will surface this in Settings UI.
            Log.e("SamsungApp", "Failed to ensure RSA key pair exists — decryption unavailable")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()
}
