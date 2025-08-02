package com.example.safemindwatch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class SOS_Notification : AppCompatActivity() {

    private lateinit var sosLayout: LinearLayout
    private lateinit var userId: String
    private lateinit var childName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_notification)

        sosLayout = findViewById(R.id.sosLayout)
        val sharedPref = getSharedPreferences("SafeMindWatchPrefs", Context.MODE_PRIVATE)

        userId = intent.getStringExtra("userId") ?: ""
        val email = intent.getStringExtra("email") ?: ""
        childName = intent.getStringExtra("childName")
            ?: sharedPref.getString("childName", "Child") ?: "Child"

        Log.d("IntentDebug", "SOS Notification Screen: $userId, $childName")
        var username = intent.getStringExtra("username")

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_notification

        bottomNav.setOnItemSelectedListener {
            val targetIntent = when (it.itemId) {
                R.id.nav_home -> Intent(this, HomeActivity::class.java)
                R.id.nav_specialists -> Intent(this, DoctorSearchActivity::class.java)
                R.id.nav_notification -> null
                R.id.nav_profile -> Intent(this, ProfileActivity::class.java)
                else -> null
            }
            targetIntent?.let {
                it.putExtra("userId", userId)
                it.putExtra("email", email)
                it.putExtra("username", username)
                startActivity(it)
            }
            true
        }

        fetchSosLogs(userId, childName)
    }

    private fun fetchSosLogs(userId: String, childName: String) {
        val api = RetrofitClient.getInstance().create(ApiService::class.java)
        api.getSosLogs(userId, childName).enqueue(object : Callback<List<SosLog>> {
            override fun onResponse(call: Call<List<SosLog>>, response: Response<List<SosLog>>) {
                if (response.isSuccessful && response.body() != null) {
                    val logs = response.body()!!
                    if (logs.isNotEmpty()) {
                        showSosLogs(logs)
                    } else {
                        showEmptyState()
                    }
                } else {
                    showErrorState("No SOS logs found.")
                }
            }

            override fun onFailure(call: Call<List<SosLog>>, t: Throwable) {
                Log.e("SOSNotification", "API call failed", t)
                showErrorState("Failed to fetch data. Check internet connection.")
            }
        })
    }

    private fun showSosLogs(logs: List<SosLog>) {
        sosLayout.removeAllViews()
        for (log in logs) {
            val (dateStr, timeStr) = formatDateTime(log.alertTime ?: log.createdAt ?: log.updatedAt)
            val card = TextView(this).apply {
                text = """
                    🔔 Query: ${log.query}
                    📅 Date: $dateStr
                    🕒 Alert Received At: $timeStr
                """.trimIndent()
                setTextColor(Color.BLACK)
                textSize = 16f
                setLineSpacing(12f, 1f)
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
                elevation = 8f
            }
            sosLayout.addView(card)
        }
    }

    private fun showEmptyState() {
        sosLayout.removeAllViews()
        sosLayout.addView(TextView(this).apply {
            text = "No SOS alerts found."
            setTextColor(Color.DKGRAY)
            textSize = 18f
            setPadding(40, 60, 40, 60)
        })
    }

    private fun showErrorState(message: String) {
        sosLayout.removeAllViews()
        sosLayout.addView(TextView(this).apply {
            text = message
            setTextColor(Color.RED)
            textSize = 18f
            setPadding(40, 60, 40, 60)
        })
    }

    private fun formatDateTime(timestamp: String?): Pair<String, String> {
        if (timestamp == null) return Pair("Unknown", "Unknown")
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (fmt in formats) {
            try {
                val parser = SimpleDateFormat(fmt, Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(timestamp)
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                return Pair(
                    dateFormat.format(date),
                    timeFormat.format(date)
                )
            } catch (_: Exception) {}
        }
        return Pair("Unknown", "Unknown")
    }
}
