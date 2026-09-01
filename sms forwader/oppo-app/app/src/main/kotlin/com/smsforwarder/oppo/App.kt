package com.smsforwarder.oppo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class — entry point for Hilt dependency injection.
 *
 * We implement [Configuration.Provider] to supply Hilt-aware WorkerFactory,
 * which allows WorkManager workers to receive @Inject constructor parameters.
 *
 * WorkManager's default initializer is disabled in the manifest; initialization
 * happens lazily the first time WorkManager.getInstance() is called.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()
}
