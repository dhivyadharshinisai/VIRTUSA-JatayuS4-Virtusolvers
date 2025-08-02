package com.example.safemindwatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.ResetPasswordRequest
import com.example.safemindwatch.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NewPasswordActivity : AppCompatActivity() {

    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnSubmitPassword: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)

        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSubmitPassword = findViewById(R.id.btnSubmitPassword)

        val email = intent.getStringExtra("email") ?: ""

        btnSubmitPassword.setOnClickListener {
            val newPassword = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please enter both fields", Toast.LENGTH_SHORT).show()
            } else if (newPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            } else {
                val request = ResetPasswordRequest(email, newPassword)

                RetrofitClient.apiService.resetPassword(request)
                    .enqueue(object : Callback<GenericResponse> {
                        override fun onResponse(
                            call: Call<GenericResponse>,
                            response: Response<GenericResponse>
                        ) {
                            if (response.isSuccessful && response.body()?.success == true) {
                                Toast.makeText(this@NewPasswordActivity, "Password reset successful", Toast.LENGTH_LONG).show()
                                // Go back to login
                                startActivity(Intent(this@NewPasswordActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@NewPasswordActivity, "Failed: ${response.body()?.message ?: "Try again"}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                            Toast.makeText(this@NewPasswordActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
    }
}
