package com.example.safemindwatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.ApiResponse
import com.example.safemindwatch.api.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var phoneVerificationButton: Button
    private lateinit var loginTextView: TextView
    private lateinit var btnVerifyEmail: Button
    private lateinit var etEmailOtp: EditText
    private lateinit var btnConfirmEmailOtp: Button
    private lateinit var imgOtpVerified: ImageView
    private lateinit var otpRow: LinearLayout
    private lateinit var googleSignUpButton: LinearLayout
    private lateinit var imgVerifyReplaced: ImageView


    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_GOOGLE = 101

    private var isEmailVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        nameEditText = findViewById(R.id.etName)
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        confirmPasswordEditText = findViewById(R.id.etConfirmPassword)
        phoneVerificationButton = findViewById(R.id.btnPhoneVerification)
        loginTextView = findViewById(R.id.tvLogin)
        btnVerifyEmail = findViewById(R.id.btnSendOtp)
        etEmailOtp = findViewById(R.id.etOtp)
        btnConfirmEmailOtp = findViewById(R.id.btnVerifyOtp)
        imgVerifyReplaced = findViewById(R.id.imgVerifyReplaced)
        imgOtpVerified = findViewById(R.id.imgOtpVerified)
        otpRow = findViewById(R.id.otpRow)
        googleSignUpButton = findViewById(R.id.btnGoogleSignUp)

        otpRow.visibility = View.VISIBLE
        etEmailOtp.visibility = View.GONE
        btnConfirmEmailOtp.visibility = View.GONE

        passwordEditText.isEnabled = true
        confirmPasswordEditText.isEnabled = true

        passwordEditText.setOnTouchListener { v, event ->
            if (!isEmailVerified) {
                Toast.makeText(this, "Verify your email", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        confirmPasswordEditText.setOnTouchListener { v, event ->
            if (!isEmailVerified) {
                Toast.makeText(this, "Verify your email", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        passwordEditText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && !isEmailVerified) {
                Toast.makeText(this, "Verify your email", Toast.LENGTH_SHORT).show()
                v.clearFocus()
            }
        }

        confirmPasswordEditText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && !isEmailVerified) {
                Toast.makeText(this, "Verify your email", Toast.LENGTH_SHORT).show()
                v.clearFocus()
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignUpButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE)
            }
        }

        btnVerifyEmail.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter Email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            etEmailOtp.visibility = View.VISIBLE
            btnConfirmEmailOtp.visibility = View.VISIBLE

            val requestBody = hashMapOf("email" to email)
            RetrofitClient.apiService.sendEmailOtp(requestBody).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    Toast.makeText(this@SignupActivity, response.body()?.message ?: "OTP Sent", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@SignupActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        btnConfirmEmailOtp.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val otp = etEmailOtp.text.toString().trim()
            if (otp.isEmpty()) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestBody = hashMapOf("email" to email, "otp" to otp)
            RetrofitClient.apiService.verifyEmailOtp(requestBody).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful && response.body()?.message == "Email verified successfully") {
                        Toast.makeText(this@SignupActivity, "Email Verified", Toast.LENGTH_SHORT).show()
                        otpRow.visibility = View.GONE

                        // Hide the Verify button
                        btnVerifyEmail.visibility = View.GONE
                        // Show the image
                        imgVerifyReplaced.visibility = View.VISIBLE

                        // Mark email as verified to allow password input
                        isEmailVerified = true
                    }else {
                        Toast.makeText(this@SignupActivity, "Invalid or Expired OTP", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    Toast.makeText(this@SignupActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        loginTextView.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        phoneVerificationButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (!isEmailVerified) {
                Toast.makeText(this, "Please verify your email before signing up", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length <= 6) {
                Toast.makeText(this, "Password must be more than 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, TermsAndConditions::class.java)
            intent.putExtra("name", name)
            intent.putExtra("email", email)
            intent.putExtra("password", password)
            startActivity(intent)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE) return

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val acct = task.getResult(ApiException::class.java)!!
            val gid = acct.id ?: ""
            val email = acct.email ?: ""
            val name = acct.displayName ?: ""

            val requestBody = hashMapOf("googleId" to gid)
            RetrofitClient.apiService.checkGoogleUser(requestBody).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (!response.isSuccessful || response.body() == null) {
                        Toast.makeText(this@SignupActivity, "Google check failed", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val json = JSONObject(response.body()!!.string())
                    if (!json.getBoolean("userExists")) {
                        val intent = Intent(this@SignupActivity, TermsAndConditions::class.java)
                        intent.putExtra("googleId", gid)
                        intent.putExtra("email", email)
                        intent.putExtra("name", name)
                        intent.putExtra("signUpMethod", "google")
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@SignupActivity, "User already exists. Try Login.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignupActivity, MainActivity::class.java))
                        finish()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@SignupActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            })

        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-Up Failed", Toast.LENGTH_SHORT).show()
        }
    }
}
