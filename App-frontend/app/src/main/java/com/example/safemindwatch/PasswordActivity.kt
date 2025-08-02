package com.example.safemindwatch

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.RetrofitClient
import com.example.safemindwatch.api.ForgotPasswordRequest
import com.example.safemindwatch.api.ForgotPasswordResponse
import com.example.safemindwatch.api.OtpVerifyRequest
import com.example.safemindwatch.api.OtpVerifyResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnResetPassword: Button
    private lateinit var otpInputLayout: TextInputLayout
    private lateinit var etOtp: TextInputEditText
    private lateinit var btnVerifyOtp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        etEmail = findViewById(R.id.etEmail)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        otpInputLayout = findViewById(R.id.otpInputLayout)
        etOtp = findViewById(R.id.etOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)

        // Hide OTP fields initially
        otpInputLayout.visibility = View.GONE
        btnVerifyOtp.visibility = View.GONE

        btnResetPassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                sendOtpToEmail(email)
            } else {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        btnVerifyOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val otp = etOtp.text.toString().trim()
            if (otp.isNotEmpty()) {
                verifyOtp(email, otp)
            } else {
                Toast.makeText(this, "Please enter the OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendOtpToEmail(email: String) {
        val request = ForgotPasswordRequest(email)
        RetrofitClient.apiService.forgotPassword(request)
            .enqueue(object : Callback<ForgotPasswordResponse> {
                override fun onResponse(
                    call: Call<ForgotPasswordResponse>,
                    response: Response<ForgotPasswordResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@PasswordActivity, "Check your email for the OTP", Toast.LENGTH_SHORT).show()
                        otpInputLayout.visibility = View.VISIBLE
                        btnVerifyOtp.visibility = View.VISIBLE
                    } else {
                        val message = response.body()?.message ?: "Failed to send OTP"
                        Toast.makeText(this@PasswordActivity, message, Toast.LENGTH_SHORT).show()
                        Log.e("ForgotPassword", "Failure: $message")
                    }
                }

                override fun onFailure(call: Call<ForgotPasswordResponse>, t: Throwable) {
                    Toast.makeText(this@PasswordActivity, "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    Log.e("ForgotPassword", "onFailure: ${t.message}", t)
                }
            })
    }

    private fun verifyOtp(email: String, otp: String) {
        val request = OtpVerifyRequest(email, otp)
        RetrofitClient.apiService.verifyOtp(request)
            .enqueue(object : Callback<OtpVerifyResponse> {
                override fun onResponse(
                    call: Call<OtpVerifyResponse>,
                    response: Response<OtpVerifyResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@PasswordActivity, "OTP Verified!", Toast.LENGTH_LONG).show()

                        val intent = Intent(this@PasswordActivity, NewPasswordActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@PasswordActivity, "Invalid OTP!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<OtpVerifyResponse>, t: Throwable) {
                    Toast.makeText(this@PasswordActivity, "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    Log.e("VerifyOTP", "onFailure: ${t.message}", t)
                }
            })
    }
}
