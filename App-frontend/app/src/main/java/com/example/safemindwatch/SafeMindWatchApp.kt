package com.example.safemindwatch

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class SafeMindWatchApp : Application() {

    companion object {
        private const val WORK_NAME = "sos_alert_worker"
        private const val TAG = "SafeMindWatchApp"
    }

    override fun onCreate() {
        super.onCreate()
        scheduleSosWorker()
    }

    private fun scheduleSosWorker() {
        val prefs = getSharedPreferences("SafeMindWatchPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("userId", null)

        if (!userId.isNullOrBlank()) {

            val workRequest = PeriodicWorkRequestBuilder<SosAlertWorker>(
                15, TimeUnit.MINUTES
            ).setInputData(workDataOf("userId" to userId))
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )

            Log.i(TAG, "Scheduled SosWorker for userId: $userId")
        } else {
            Log.w(TAG, "UserId not found, skipping SosWorker scheduling")
        }
    }
}
