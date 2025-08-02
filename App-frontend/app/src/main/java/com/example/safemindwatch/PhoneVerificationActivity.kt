package com.example.safemindwatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.ApiService
import com.example.safemindwatch.api.RetrofitClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PhoneVerificationActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var etOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var ccp: com.hbb20.CountryCodePicker

    private lateinit var name: String
    private lateinit var email: String
    private lateinit var password: String
    private lateinit var googleId: String
    private lateinit var signUpMethod: String
    private lateinit var childProfilesJson: String
    private lateinit var childProfilesList: List<Map<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_verification)

        etPhone = findViewById(R.id.etPhone)
        etOtp = findViewById(R.id.etOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        ccp = findViewById(R.id.countryCodePicker)
        ccp.registerCarrierNumberEditText(etPhone)

        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        tvLogin.setOnClickListener {
            val intent = Intent(this@PhoneVerificationActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnVerifyOtp.isEnabled = false

        signUpMethod = intent.getStringExtra("signUpMethod") ?: "normal"

        if (signUpMethod == "google") {
            name = intent.getStringExtra("name") ?: ""
            email = intent.getStringExtra("email") ?: ""
            googleId = intent.getStringExtra("googleId") ?: ""
            password = ""
        } else {
            name = intent.getStringExtra("name") ?: ""
            email = intent.getStringExtra("email") ?: ""
            password = intent.getStringExtra("password") ?: ""
            googleId = ""
        }

        childProfilesJson = intent.getStringExtra("childProfiles") ?: "[]"
        childProfilesList = parseChildProfiles(childProfilesJson)

        btnSendOtp.setOnClickListener {
            if (!ccp.isValidFullNumber) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
            } else {
                val fullPhoneNumber = ccp.fullNumberWithPlus
                sendOtp(fullPhoneNumber)
            }
        }

        btnVerifyOtp.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            val fullPhoneNumber = ccp.fullNumberWithPlus

            if (otp.isEmpty()) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
            } else {
                verifyOtp(fullPhoneNumber, otp)
            }
        }

    }

    private fun parseChildProfiles(json: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val age = obj.getString("age")
                list.add(mapOf("name" to name, "age" to age))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error parsing child profiles", Toast.LENGTH_SHORT).show()
        }
        return list
    }


    private fun sendOtp(phoneNumber: String) {
        val api = RetrofitClient.getInstance().create(ApiService::class.java)

        val requestData = hashMapOf("phone" to phoneNumber)
        api.sendOtp(requestData).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@PhoneVerificationActivity, "OTP Sent Successfully", Toast.LENGTH_SHORT).show()
                    btnVerifyOtp.isEnabled = true
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        JSONObject(errorBody ?: "{}").optString("error", "Failed to send OTP")
                    } catch (e: Exception) {
                        "Failed to send OTP"
                    }
                    Toast.makeText(this@PhoneVerificationActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    Log.e("SendOtp", errorMessage)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@PhoneVerificationActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("SendOtp", t.toString())
            }
        })
    }

    private fun verifyOtp(phoneNumber: String, otp: String) {
        val api = RetrofitClient.getInstance().create(ApiService::class.java)

        val requestData = hashMapOf(
            "phone" to phoneNumber,
            "otp" to otp
        )

        api.verifyOtp(requestData).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@PhoneVerificationActivity, "OTP Verified", Toast.LENGTH_SHORT).show()
                    if (password.isEmpty()) {
                        registerGoogleUser(name, email, phoneNumber, googleId)
                    } else {
                        registerUser(name, email, phoneNumber, password)
                    }
                } else {
                    Toast.makeText(this@PhoneVerificationActivity, "OTP Verification Failed", Toast.LENGTH_SHORT).show()
                    Log.e("VerifyOtp", response.errorBody()?.string() ?: "unknown error")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@PhoneVerificationActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("VerifyOtp", t.toString())
            }
        })
    }

    private fun registerUser(name: String, email: String, phone: String, password: String) {
        val api = RetrofitClient.getInstance().create(ApiService::class.java)

        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "password" to password,
            "childProfiles" to childProfilesJson  // send as raw JSON string
        )

        api.registerUser(userData).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@PhoneVerificationActivity, "Signup Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@PhoneVerificationActivity, MainActivity::class.java))
                    finish()
                } else {
                    val err = response.errorBody()?.string()
                    Toast.makeText(this@PhoneVerificationActivity, "Signup Failed: $err", Toast.LENGTH_LONG).show()
                    Log.e("RegisterUser", err ?: "unknown error")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@PhoneVerificationActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("RegisterUser", t.toString())
            }
        })
    }

    private fun registerGoogleUser(name: String, email: String, phone: String, googleId: String) {
        val api = RetrofitClient.getInstance().create(ApiService::class.java)

        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "password" to "default_password",
            "googleId" to googleId,
            "isGoogleUser" to "true",
            "childProfiles" to childProfilesJson
        )

        api.registerGoogleUser(userData).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@PhoneVerificationActivity, "Google Signup Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@PhoneVerificationActivity, MainActivity::class.java))
                    finish()
                } else {
                    val err = response.errorBody()?.string()
                    Toast.makeText(this@PhoneVerificationActivity, "Google Signup Failed: $err", Toast.LENGTH_LONG).show()
                    Log.e("GoogleRegisterUser", err ?: "unknown error")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@PhoneVerificationActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("GoogleRegisterUser", t.toString())
            }
        })
    }
}
