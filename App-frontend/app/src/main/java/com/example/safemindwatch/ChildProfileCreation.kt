package com.example.safemindwatch

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class ChildProfileCreation : AppCompatActivity() {

    private lateinit var etNumberOfProfiles: EditText
    private lateinit var profilesContainer: LinearLayout
    private lateinit var btnNext: Button

    private val imagePickerMap = mutableMapOf<Int, ImageView>()
    private val savedProfileStatus = mutableMapOf<Int, Boolean>()
    private var currentImagePickerIndex = -1

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageUri: Uri? = result.data?.data
                imageUri?.let {
                    val inputStream: InputStream? = contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imagePickerMap[currentImagePickerIndex]?.setImageBitmap(bitmap)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_child_profile)

        val name = intent.getStringExtra("name")
        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")
        val googleId = intent.getStringExtra("googleId")
        val signUpMethod = intent.getStringExtra("signUpMethod")

        Log.d("IntentExtras", "name: $name")
        Log.d("IntentExtras", "email: $email")
        Log.d("IntentExtras", "password: $password")
        Log.d("IntentExtras", "googleId: $googleId")
        Log.d("IntentExtras", "signUpMethod: $signUpMethod")


        etNumberOfProfiles = findViewById(R.id.etNumberOfProfiles)
        profilesContainer = findViewById(R.id.profilesContainer)
        btnNext = findViewById(R.id.btnNext)

        etNumberOfProfiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s.toString().toIntOrNull()
                if (count != null && count in 1..10) {
                    generateProfileCards(count)
                } else {
                    profilesContainer.removeAllViews()
                    btnNext.isEnabled = false
                }
            }
        })

        btnNext.setOnClickListener {
            val childProfiles = JSONArray()

            for (i in 0 until profilesContainer.childCount) {
                val profileView = profilesContainer.getChildAt(i)
                val etChildName = profileView.findViewById<EditText>(R.id.etChildName)
                val etAge = profileView.findViewById<EditText>(R.id.etAge)

                val childObject = JSONObject()
                childObject.put("name", etChildName.text.toString().trim())
                childObject.put("age", etAge.text.toString().trim().toIntOrNull() ?: 0)

                childProfiles.put(childObject)
            }

            val intent = Intent(this, PhoneVerificationActivity::class.java).apply {
                putExtra("name", name)
                putExtra("email", email)
                putExtra("password", password)
                putExtra("googleId", googleId)
                putExtra("signUpMethod", signUpMethod)
                putExtra("childProfiles", childProfiles.toString())
            }

            startActivity(intent)
        }
    }

    private fun generateProfileCards(count: Int) {
        profilesContainer.removeAllViews()
        imagePickerMap.clear()
        savedProfileStatus.clear()
        btnNext.isEnabled = false

        for (i in 0 until count) {
            val profileView =
                layoutInflater.inflate(R.layout.item_child_profile, profilesContainer, false)

            val tvTitle = profileView.findViewById<TextView>(R.id.tvProfileTitle)
            val etChildName = profileView.findViewById<EditText>(R.id.etChildName)
            val etAge = profileView.findViewById<EditText>(R.id.etAge)
            val imgProfile = profileView.findViewById<ImageView>(R.id.imgProfile)
            val tvEligibility = profileView.findViewById<TextView>(R.id.tvEligibility)
            val btnSaveProfile = profileView.findViewById<Button>(R.id.btnSaveProfile)

            tvTitle.text = "Child ${i + 1}"
            imagePickerMap[i] = imgProfile

            imgProfile.setOnClickListener {
                currentImagePickerIndex = i
                pickImageFromGallery()
            }

            btnSaveProfile.setOnClickListener {
                val name = etChildName.text.toString().trim()
                val ageText = etAge.text.toString().trim()
                val age = ageText.toIntOrNull()

                if (name.isEmpty() || age == null || age <= 0 || age > 100) {
                    Toast.makeText(
                        this,
                        "Enter valid name and age for Child ${i + 1}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                when {
                    age > 18 -> {
                        tvEligibility.text = "Not eligible: Age is above 18"
                        tvEligibility.setTextColor(getColor(android.R.color.holo_red_dark))
                        tvEligibility.visibility = View.VISIBLE
                        Toast.makeText(this, "Child must be 18 or younger", Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }
                    else -> {
                        tvEligibility.text = "Eligible"
                        tvEligibility.setTextColor(getColor(android.R.color.holo_green_dark))
                        tvEligibility.visibility = View.VISIBLE
                    }
                }

                savedProfileStatus[i] = true
                btnSaveProfile.text = "Saved"
                btnSaveProfile.isEnabled = false
                etChildName.isEnabled = false
                etAge.isEnabled = false
                imgProfile.isEnabled = false

                Toast.makeText(this, "Child ${i + 1} profile saved", Toast.LENGTH_SHORT).show()

                btnNext.isEnabled =
                    savedProfileStatus.size == count && savedProfileStatus.values.all { it }
            }

            profilesContainer.addView(profileView)
        }
    }

    private fun pickImageFromGallery() {
        val intent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }
}
