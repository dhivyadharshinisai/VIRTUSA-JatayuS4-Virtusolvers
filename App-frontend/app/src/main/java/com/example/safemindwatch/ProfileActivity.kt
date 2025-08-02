package com.example.safemindwatch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.safemindwatch.api.ApiService
import com.example.safemindwatch.api.RetrofitClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvMobile: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnSwitchAccount : LinearLayout
    private lateinit var imgProfile: ImageView

    private lateinit var switchSms: SwitchCompat
    private lateinit var switchEmail: SwitchCompat
    private lateinit var switchSos: SwitchCompat
    private lateinit var username : String

    private lateinit var tvStatusSms: TextView
    private lateinit var tvStatusEmail: TextView
    private lateinit var tvStatusSos: TextView

    private lateinit var userId: String
    private lateinit var email: String
    private var isInitializingSwitches = true

    private val api: ApiService by lazy {
        RetrofitClient.apiService
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uploadProfileImage(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        Log.d("ProfileActivityTest", "ProfileActivity started")

        initViews()

        switchEmail.thumbTintList = null
        switchEmail.trackTintList = null
        switchSos.thumbTintList = null
        switchSos.trackTintList = null

        userId = intent.getStringExtra("userId").orEmpty()
        email = intent.getStringExtra("email").toString()
        Log.d("ProfileActivity", "Received userId: $email")
        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d("ProfileActivity", "Received userId: $userId")

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener {
            val targetIntent = when (it.itemId) {
                R.id.nav_home -> Intent(this, HomeActivity::class.java)
                R.id.nav_specialists -> Intent(this, DoctorSearchActivity::class.java)
                R.id.nav_notification -> Intent(this, SOS_Notification::class.java)
                R.id.nav_profile ->  null
                else -> null
            }

            if (targetIntent != null) {
                targetIntent.putExtra("userId", userId)
                targetIntent.putExtra("email", email)
                Log.d("PA", "Passing username: $username")
                targetIntent.putExtra("username", username)
                startActivity(targetIntent)
                true
            } else {
                true
            }
        }

        fetchUserDetails(userId)
        fetchProfileImage(userId)

        setupSwitchListeners()
    }

    private fun initViews() {
        val sharedPref = getSharedPreferences("SafeMindPrefs", Context.MODE_PRIVATE)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvMobile = findViewById(R.id.tvPhone)
        btnLogout = findViewById(R.id.btnLogout)
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount)
        imgProfile = findViewById(R.id.imgProfile)

        switchSms = findViewById(R.id.switchSms)
        switchEmail = findViewById(R.id.switchEmail)
        switchSos = findViewById(R.id.switchSos)

        // status TextViews for ON/OFF display
        tvStatusSms = findViewById(R.id.tvStatusSms)
        tvStatusEmail = findViewById(R.id.tvStatusEmail)
        tvStatusSos = findViewById(R.id.tvStatusSos)

        btnLogout.setOnClickListener {
            showLogoutConfirmation(sharedPref)
        }
        btnSwitchAccount.setOnClickListener {
            showSwitchAccountConfirmation(sharedPref)
        }

        imgProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

    }

    private fun showLogoutConfirmation(sharedPref: android.content.SharedPreferences) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Do you want to log out?")
            .setCancelable(true)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                sharedPref.edit().clear().apply()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun showSwitchAccountConfirmation(sharedPref: android.content.SharedPreferences) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Do you want to switch account?")
            .setCancelable(true)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Switch Account") { _, _ ->
                sharedPref.edit().clear().apply()
                Toast.makeText(this, "Choose Account", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, ProfileSelectionActivity::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("email", email)
                })
            }
            .show()
    }

    /** Helper: Set status text and color for ON/OFF beside switch */
    private fun setSwitchStatus(tv: TextView, enabled: Boolean) {
        if (enabled) {
            tv.text = "ON"
            tv.setTextColor(ContextCompat.getColor(this, R.color.background_color)) // Use your green color here
        } else {
            tv.text = "OFF"
            tv.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    // ----------------------- Switch Setup -----------------------
    private fun setupSwitchListeners() {
        val gray = ContextCompat.getColor(this, android.R.color.darker_gray)
        val blue = ContextCompat.getColor(this, R.color.background_color)

        val thumbColors = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(blue, gray)
        )
        val trackColors = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(blue, gray)
        )

        // Make sure tints are always applied
        fun applyTints(sw: SwitchCompat) {
            sw.thumbTintList = thumbColors
            sw.trackTintList = trackColors
        }

        // Listeners which update the ON/OFF TextView status and backend
        switchSms.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializingSwitches) return@setOnCheckedChangeListener
            setSwitchStatus(tvStatusSms, isChecked)
            applyTints(switchSms)
            updateUserSettings(userId)
        }
        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializingSwitches) return@setOnCheckedChangeListener
            setSwitchStatus(tvStatusEmail, isChecked)
            applyTints(switchEmail)
            updateUserSettings(userId)
        }
        switchSos.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializingSwitches) return@setOnCheckedChangeListener
            setSwitchStatus(tvStatusSos, isChecked)
            applyTints(switchSos)
            updateUserSettings(userId)
        }

        // Ensure tints are applied on create
        applyTints(switchSms)
        applyTints(switchEmail)
        applyTints(switchSos)
    }

    // ----------------------- Fetch User -----------------------
    private fun fetchUserDetails(userId: String) {
        Log.d("SettingsUpdate", "fetchUserDetails() -> userId=$userId")

        api.getUserById(userId).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                Log.d("SettingsUpdate", "fetchUserDetails() response code=${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    bindUser(user)
                } else {
                    isInitializingSwitches = false
                    Toast.makeText(this@ProfileActivity, "Failed to fetch user info", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                isInitializingSwitches = false
                Toast.makeText(this@ProfileActivity, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun bindUser(user: User) {
        val sharedPref = getSharedPreferences("SafeMindPrefs", Context.MODE_PRIVATE)
        val smsLocal = sharedPref.getBoolean("smsAlerts", user.settings?.smsAlerts ?: false)
        val emailLocal = sharedPref.getBoolean("emailAlerts", user.settings?.emailAlerts ?: false)
        val sosLocal = sharedPref.getBoolean("sosAlerts", user.settings?.sosAlerts ?: false)

        isInitializingSwitches = true
        switchSms.isChecked = smsLocal
        switchEmail.isChecked = emailLocal
        switchSos.isChecked = sosLocal

        setSwitchStatus(tvStatusSms, smsLocal)
        setSwitchStatus(tvStatusEmail, emailLocal)
        setSwitchStatus(tvStatusSos, sosLocal)
        isInitializingSwitches = false

        Log.d("Debug", "User name is: ${user.name}")
        tvName.text = user.name
        tvEmail.text = user.email
        tvMobile.text = user.phone ?: "N/A"
        username= user.name

        Log.d("ProfileActivity", "Fetched settings: $smsLocal, $emailLocal, $sosLocal")
    }

    // ----------------------- Fetch Profile Image -----------------------
    private fun fetchProfileImage(userId: String) {
        Log.d("ProfileActivity", "fetchProfileImage() -> userId=$userId")
        api.getProfileImageById(userId).enqueue(object : Callback<ProfileImageResponse> {
            override fun onResponse(
                call: Call<ProfileImageResponse>,
                response: Response<ProfileImageResponse>
            ) {
                Log.d("ProfileActivity", "fetchProfileImage() code=${response.code()}")
                if (response.isSuccessful) {
                    val base64Image = response.body()?.imageData
                    if (!base64Image.isNullOrEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val decoded = Base64.decode(base64Image, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                            withContext(Dispatchers.Main) { imgProfile.setImageBitmap(bitmap) }
                        }
                    }
                } else {
                    Log.e("API", "Image fetch error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ProfileImageResponse>, t: Throwable) {
                Log.e("API", "Failed to fetch image: ${t.message}")
            }
        })
    }

    // ----------------------- Upload Profile Image -----------------------
    private fun uploadProfileImage(uri: Uri) {
        Log.d("ProfileActivity", "uploadProfileImage() -> userId=$userId uri=$uri")

        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: return

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val compressedBytes = outputStream.toByteArray()
        val base64Image = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

        val body = mapOf("imageData" to base64Image)

        api.uploadProfileImageById(userId, body).enqueue(object : Callback<GenericResponse> {
            override fun onResponse(
                call: Call<GenericResponse>,
                response: Response<GenericResponse>
            ) {
                Log.d("ProfileActivity", "uploadProfileImage() code=${response.code()}")
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@ProfileActivity, "Image uploaded!", Toast.LENGTH_SHORT).show()
                    imgProfile.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this@ProfileActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                Toast.makeText(this@ProfileActivity, "Upload error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ----------------------- Update User Settings -----------------------
    private fun updateUserSettings(userId: String) {
        Log.d("SettingsUpdate", "updateUserSettings() called for userId: $userId")

        val request = UpdateSettingsRequest(
            userId = userId,
            smsAlerts = switchSms.isChecked,
            emailAlerts = switchEmail.isChecked,
            sosAlerts = switchSos.isChecked
        )

        // Save locally
        val sharedPref = getSharedPreferences("SafeMindPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putBoolean("smsAlerts", request.smsAlerts)
            putBoolean("emailAlerts", request.emailAlerts)
            putBoolean("sosAlerts", request.sosAlerts)
            apply()
        }

        api.updateSettings(userId, request).enqueue(object : Callback<GenericResponse> {
            override fun onResponse(call: Call<GenericResponse>, response: Response<GenericResponse>) {
                Log.d("SettingsUpdate", "updateUserSettings() response code=${response.code()}")
                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d("SettingsUpdate", "Settings updated successfully")
                } else {
                    Log.e("SettingsUpdate", "Update failed code=${response.code()}")
                }
            }

            override fun onFailure(call: Call<GenericResponse>, t: Throwable) {
                Log.e("SettingsUpdate", "Error: ${t.message}")
            }
        })
    }
}
