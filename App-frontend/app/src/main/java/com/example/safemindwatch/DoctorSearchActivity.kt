package com.example.safemindwatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DoctorSearchActivity : AppCompatActivity() {

    private lateinit var btnNext: Button

    private lateinit var userId: String
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_home)

        btnNext = findViewById(R.id.btnNext)


        userId = intent.getStringExtra("userId").orEmpty()
        Log.d("DSA", "Home: $userId")
        email = intent.getStringExtra("email").toString()
        var username = intent.getStringExtra("username")
        Log.d("DSA", "UserName: $username")


        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_specialists

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> {
                    Intent(this, HomeActivity::class.java).also { intent ->
                        intent.putExtra("userId", userId)
                        intent.putExtra("email", email)
                        intent.putExtra("username", username)
                        startActivity(intent)
                    }
                    true
                }
                R.id.nav_notification -> {
                    Intent(this, SOS_Notification::class.java).also { intent ->
                        intent.putExtra("userId", userId)
                        intent.putExtra("email", email)
                        intent.putExtra("username", username)
                        startActivity(intent)
                    }
                    true
                }
                R.id.nav_specialists -> {
                    true
                }
                R.id.nav_profile -> {
                    Intent(this, ProfileActivity::class.java).also { intent ->
                        intent.putExtra("userId", userId)
                        intent.putExtra("email", email)
                        intent.putExtra("username", username)
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        btnNext.setOnClickListener {
            Intent(this, NearbyDoctorsActivity::class.java).also { newIntent ->
                newIntent.putExtra("userId", userId)
                newIntent.putExtra("email", email)
                newIntent.putExtra("username", username)
                startActivity(newIntent)
            }
        }
    }
}

