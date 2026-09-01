package com.smsforwarder.oppo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.smsforwarder.oppo.worker.DrainQueueWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives BOOT_COMPLETED and MY_PACKAGE_REPLACED broadcasts.
 *
 * On reboot:
 * 1. Schedules a one-time WorkManager job to drain any pending encrypted
 *    messages that were queued before the reboot.
 * 2. Starts the foreground SmsForwarderService to ensure the process
 *    is running and battery optimization is respected.
 *
 * Note: The manifest-registered SmsBroadcastReceiver is automatically
 * re-armed after reboot — no explicit action required for that.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Boot/update received — scheduling queue drain")
                scheduleQueueDrain(context)
            }
        }
    }

    private fun scheduleQueueDrain(context: Context) {
        val drainWork = OneTimeWorkRequestBuilder<DrainQueueWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("boot_drain")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "boot_queue_drain",
            ExistingWorkPolicy.KEEP, // Don't replace if already queued
            drainWork
        )
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
