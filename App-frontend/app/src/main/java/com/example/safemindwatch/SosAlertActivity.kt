package com.example.safemindwatch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class SosAlertActivity : AppCompatActivity() {

    private var vibrator: Vibrator? = null
    private val vibrationDurationMs = 60_000L
    private val vibrationPattern = longArrayOf(0, 1000, 500, 1000)
    private lateinit var userId: String
    private val handler = Handler(Looper.getMainLooper())

    private var clearedByButton = false

    private val stopVibrationRunnable = Runnable {
        Log.d("SosAlertActivity", "Vibration timeout reached, stopping vibration")
        stopVibration()
        finishWithResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_sos_alert)

        userId = intent.getStringExtra("userId").toString()
        val childName = intent.getStringExtra("childName") ?: "your child"
        val searchedQuery = intent.getStringExtra("query") ?: ""

        // SOS message
        val tvMessage = findViewById<TextView>(R.id.tvSosMessage)
        tvMessage.text = "🆘 SOS Alert for $childName"

        // searched query
        val tvQuerySearched = findViewById<TextView>(R.id.tvQuerySearched)
        val queryText = "Searched Query\n\"$searchedQuery\""
        val spannable = SpannableString(queryText)


        val start = queryText.indexOf("\"")
        val end = queryText.lastIndexOf("\"") + 1

        if (start != -1 && end > start) {
            spannable.setSpan(
                ForegroundColorSpan(Color.RED),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvQuerySearched.setText(spannable, TextView.BufferType.SPANNABLE)

        findViewById<Button>(R.id.btnDismiss).setOnClickListener {
            Log.d("SosAlertActivity", "\"I Understand\" button clicked")
            clearedByButton = true
            stopVibration()
            finishWithResult()
            acknowledgeAlertAndFinish()
        }
        initVibration()
    }

    private fun initVibration() {
        Log.d("SosAlertActivity", "Starting vibration")

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(vibrationPattern, 0)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(vibrationPattern, 0)
        }
        handler.postDelayed(stopVibrationRunnable, vibrationDurationMs)
    }

    private fun stopVibration() {
        vibrator?.cancel()
        handler.removeCallbacks(stopVibrationRunnable)
        Log.d("SosAlertActivity", "Vibration stopped")
    }

    private fun finishWithResult() {
        val data = Intent().apply {
            putExtra("clearedByButton", clearedByButton)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
        Log.d("SosAlertActivity", "Activity destroyed")
    }

    private fun acknowledgeAlertAndFinish() {

        val userId =userId;

        RetrofitClient.apiService.acknowledgeSOSAlert(userId).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("SosAlertActivity", "SOS alert acknowledged successfully on backend")
                } else {
                    Log.w("SosAlertActivity", "Failed to acknowledge SOS alert, response code: ${response.code()}")
                }

                finishWithResult()
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("SosAlertActivity", "Error acknowledging SOS alert", t)
                finishWithResult()
            }
        })
    }
    override fun onBackPressed() {
        Log.d("SosAlertActivity", "Back press ignored during SOS alert")
    }
}
