package com.example.safemindwatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private val api = RetrofitClient.apiService
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var btnGoogleSignIn: LinearLayout
    private val RC_GOOGLE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("SafeMindPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("isLoggedIn", false)) {
            val uid = prefs.getString("userId", "")!!
            val em = prefs.getString("email", "")!!
            val nm = prefs.getString("name", "")!!
            goToProfileSelection(uid, em, nm)
            return
        }

        setContentView(R.layout.activity_main)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            api.loginUser(hashMapOf("email" to email, "password" to password))
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        if (!response.isSuccessful || response.body() == null) {
                            Toast.makeText(this@MainActivity, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val loginResp = response.body()!!
                        val token = loginResp.token
                        val user = loginResp.user
                        val uid = decodeJWT(token).getString("userId")

                        saveSession(uid, user.email, user.name)
                        goToProfileSelection(uid, user.email, user.name)
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "Network Error", Toast.LENGTH_SHORT).show()
                        Log.e("LoginError", t.message ?: "")
                    }
                })
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, PasswordActivity::class.java))
        }

        btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_GOOGLE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account.email ?: ""
                val name = account.displayName ?: ""

                if (email.isEmpty()) {
                    Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
                    return
                }

                val body = HashMap<String, String>()
                body["email"] = email

                api.getUserByEmail1(body).enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                        if (response.isSuccessful && response.body() != null) {
                            val user = response.body()!!.user
                            val token = response.body()!!.token
                            val uid = decodeJWT(token).getString("userId")

                            saveSession(uid, user.email, user.name)
                            goToProfileSelection(uid, user.email, user.name)
                        } else {
                            Toast.makeText(this@MainActivity, "Google user not registered", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                        Log.e("GoogleFetchError", t.message ?: "")
                    }
                })

            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
                Log.e("GoogleSignInErr", e.message ?: "")
            }
        }
    }

    private fun decodeJWT(token: String): JSONObject {
        val parts = token.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid JWT")
        val decoded = Base64.decode(parts[1], Base64.URL_SAFE)
        return JSONObject(String(decoded))
    }

    private fun saveSession(uid: String, email: String, name: String) {
        getSharedPreferences("SafeMindPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("isLoggedIn", true)
            .putString("userId", uid)
            .putString("email", email)
            .putString("name", name)
            .apply()
    }

    private fun goToProfileSelection(uid: String, email: String, name: String) {
        val intent = Intent(this, ProfileSelectionActivity::class.java)
        intent.putExtra("userId", uid)
        Log.d("IntentDebug", "Passing userId : $uid")
        intent.putExtra("email", email)
        intent.putExtra("name", name)
        startActivity(intent)
        finish()
    }
}
