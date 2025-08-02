package com.example.safemindwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.safemindwatch.api.RetrofitClient

class SosAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SosAlertWorker"
        private const val CHANNEL_ID = "safemindwatch_sos_alert"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val userId = inputData.getString("userId")
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "No userId provided in input data")
            return Result.failure()
        }

        try {
            // Synchronously check SOS alert status in IO thread
            val response = RetrofitClient.apiService.checkSOSAlert(userId).execute()

            if (response.isSuccessful) {
                val sosResponse = response.body()
                if (sosResponse?.sosActive == true) {
                    val childName = sosResponse.childName ?: "your child"
                    showFullScreenNotification(childName)
                    Log.i(TAG, "SOS alert active - notification posted for $childName")
                } else {
                    Log.i(TAG, "No active SOS alert at this time")
                }
            } else {
                Log.w(TAG, "Backend returned error code: ${response.code()}")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error polling SOS alert", e)
            return Result.retry()
        }
    }

    private fun showFullScreenNotification(childName: String) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Alert Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification channel for SOS alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(null, null) // Customize if you want a sound
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, SosAlertActivity::class.java).apply {
            putExtra("childName", childName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS Alert for $childName")
            .setContentText("Tap to respond")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}