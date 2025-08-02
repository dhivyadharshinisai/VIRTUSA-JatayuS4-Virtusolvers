package com.example.safemindwatch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.bumptech.glide.Glide
import com.example.safemindwatch.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.InputStream

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var btnEdit: TextView
    private var isEditMode = false

    private lateinit var userEmail: String
    private lateinit var userName: String
    private lateinit var userId: String

    private val childProfiles = mutableListOf<ChildProfile>()
    private val TAG = "ProfileSelection"

    private var currentImagePickerIndex = 0
    private val imagePickerMap = mutableMapOf<Int, ImageView>()

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

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        userId = intent.getStringExtra("userId").toString()
        userEmail = intent.getStringExtra("email") ?: ""
        userName = intent.getStringExtra("name") ?: ""

        val sharedPref = getSharedPreferences("SafeMindPrefs",  Context.MODE_PRIVATE)
        if (userId.isEmpty() || userEmail.isEmpty()) {
            userId = userId.ifEmpty { sharedPref.getString("userId", "").orEmpty() }
            userEmail = userEmail.ifEmpty { sharedPref.getString("email", "").orEmpty() }
            userName = userName.ifEmpty { sharedPref.getString("name", "").orEmpty() }
        }

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "User email missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 3. Save user data to SharedPreferences again for consistency
        sharedPref.edit().apply {
            putString("userId", userId)
            putString("email", userEmail)
            putString("name", userName)
            apply()
        }

        gridLayout = findViewById(R.id.gridLayoutProfiles)
        btnEdit = findViewById(R.id.btnEdit)

        btnEdit.setOnClickListener {
            isEditMode = !isEditMode
            btnEdit.text = if (isEditMode) "Done" else "Edit"
            renderProfiles()
        }

        loadChildProfiles()
    }

    private fun loadChildProfiles() {
        RetrofitClient.apiService.getChildren(userEmail)
            .enqueue(object : Callback<List<ChildProfile>> {
                override fun onResponse(call: Call<List<ChildProfile>>, response: Response<List<ChildProfile>>) {
                    if (response.isSuccessful) {
                        childProfiles.clear()
                        childProfiles.addAll(response.body() ?: emptyList())
                        renderProfiles()
                    } else {
                        Toast.makeText(this@ProfileSelectionActivity, "Failed to load profiles", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<List<ChildProfile>>, t: Throwable) {
                    Toast.makeText(this@ProfileSelectionActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun renderProfiles() {
        gridLayout.removeAllViews()
        childProfiles.forEachIndexed { index, profile ->
            gridLayout.addView(createProfileCard(profile, index))
        }
        gridLayout.addView(createAddProfileCard())
    }

    private fun createProfileCard(profile: ChildProfile, index: Int): LinearLayout {
        val displayMetrics = Resources.getSystem().displayMetrics
        val cardSize = displayMetrics.widthPixels / 2 - 200

        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = cardSize
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(50, 40, 50, 50)
            }
        }

        val frame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(cardSize, cardSize)
            background = ContextCompat.getDrawable(this@ProfileSelectionActivity, R.drawable.rounded_card_bg)
            elevation = 8f
        }

        val imagePath = profile.profileImage ?: "/default-profile.png"
        val imageUrl = "http://192.168.220.213:3000$imagePath"

        val thumbView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            textSize = 40f
            setTextColor(Color.WHITE)
            text = profile.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(getColorForName(profile.name))
        }

        if (!imagePath.contains("default-profile")) {
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(imageView)

            frame.addView(imageView)
        } else {
            frame.addView(thumbView)
        }

        val editIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_edit)
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            layoutParams = FrameLayout.LayoutParams(40, 40, Gravity.TOP or Gravity.END).apply {
                setMargins(0, 6, 6, 0)
            }
        }

        val nameText = TextView(this).apply {
            text = profile.name
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        frame.addView(editIcon)
        outerLayout.addView(frame)
        outerLayout.addView(nameText)

        outerLayout.setOnClickListener {
            if (isEditMode) {
                showEditDeleteDialog(index, profile)
            } else {
                goHome(userEmail, profile.name)
            }
        }

        return outerLayout
    }

    private fun getColorForName(name: String): Int {
        val colors = listOf(
            R.color.profile_color_1, R.color.profile_color_2, R.color.profile_color_3,
            R.color.profile_color_4, R.color.profile_color_5, R.color.profile_color_6,
            R.color.profile_color_7, R.color.profile_color_8, R.color.profile_color_9,
            R.color.profile_color_10
        )
        val index = kotlin.math.abs(name.hashCode()) % colors.size
        return ContextCompat.getColor(this, colors[index])
    }

    private fun showEditDeleteDialog(index: Int, profile: ChildProfile) {
        val input = EditText(this).apply {
            hint = "New Name"
            setText(profile.name)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateChildProfile(index, newName)
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteChildProfile(index)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun updateChildProfile(index: Int, newName: String) {
        val request = ChildUpdateRequest(email = userEmail, index = index, name = newName)
        RetrofitClient.apiService.updateChild(request)
            .enqueue(object : Callback<ChildProfileResponse> {
                override fun onResponse(call: Call<ChildProfileResponse>, response: Response<ChildProfileResponse>) {
                    if (response.isSuccessful) {
                        childProfiles.clear()
                        childProfiles.addAll(response.body()?.children ?: emptyList())
                        renderProfiles()
                    } else {
                        Toast.makeText(this@ProfileSelectionActivity, "Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ChildProfileResponse>, t: Throwable) {
                    Toast.makeText(this@ProfileSelectionActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun deleteChildProfile(index: Int) {
        val request = ChildDeleteRequest(email = userEmail, index = index)
        RetrofitClient.apiService.deleteChild(request)
            .enqueue(object : Callback<ChildProfileResponse> {
                override fun onResponse(call: Call<ChildProfileResponse>, response: Response<ChildProfileResponse>) {
                    if (response.isSuccessful) {
                        childProfiles.clear()
                        childProfiles.addAll(response.body()?.children ?: emptyList())
                        renderProfiles()
                    } else {
                        Toast.makeText(this@ProfileSelectionActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ChildProfileResponse>, t: Throwable) {
                    Toast.makeText(this@ProfileSelectionActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun createAddProfileCard(): FrameLayout {
        val displayMetrics = Resources.getSystem().displayMetrics
        val cardSize = displayMetrics.widthPixels / 2 - 200

        val frame = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cardSize
                height = cardSize + 32
                setMargins(50, 40, 50, 50)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@ProfileSelectionActivity, android.R.color.darker_gray))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val plus = TextView(this).apply {
            text = "+"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        val label = TextView(this).apply {
            text = "Add Profile"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 0)
            gravity = Gravity.CENTER
        }

        layout.setPadding(20, 20, 20, 20)
        layout.addView(plus)
        layout.addView(label)
        frame.addView(layout)

        frame.setOnClickListener {
            showAddProfileDialog()
        }

        return frame
    }

    private fun showAddProfileDialog() {
        val inflater = layoutInflater
        val profileView = inflater.inflate(R.layout.item_child_profile, null)

        val tvTitle = profileView.findViewById<TextView>(R.id.tvProfileTitle)
        val etChildName = profileView.findViewById<EditText>(R.id.etChildName)
        val etAge = profileView.findViewById<EditText>(R.id.etAge)
        val imgProfile = profileView.findViewById<ImageView>(R.id.imgProfile)
        val tvEligibility = profileView.findViewById<TextView>(R.id.tvEligibility)
        val btnSaveProfile = profileView.findViewById<Button>(R.id.btnSaveProfile)

        tvTitle.text = "Child ${childProfiles.size + 1}"
        imagePickerMap[currentImagePickerIndex] = imgProfile

        imgProfile.setOnClickListener {
            pickImageFromGallery()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(profileView)
            .create()

        btnSaveProfile.setOnClickListener {
            val name = etChildName.text.toString().trim()
            val ageText = etAge.text.toString().trim()
            val age = ageText.toIntOrNull()

            if (name.isEmpty() || age == null || age <= 0 || age > 100) {
                Toast.makeText(this, "Enter valid name and age", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (age > 18) {
                tvEligibility.text = "Not eligible: Age is above 18"
                tvEligibility.setTextColor(getColor(android.R.color.holo_red_dark))
                tvEligibility.visibility = View.VISIBLE
                return@setOnClickListener
            }

            tvEligibility.text = "Eligible"
            tvEligibility.setTextColor(getColor(android.R.color.holo_green_dark))
            tvEligibility.visibility = View.VISIBLE

            RetrofitClient.apiService.addChild(
                mapOf("email" to userEmail, "name" to name, "age" to "$age")
            ).enqueue(object : Callback<ChildProfileResponse> {
                override fun onResponse(call: Call<ChildProfileResponse>, response: Response<ChildProfileResponse>) {
                    if (response.isSuccessful) {
                        childProfiles.clear()
                        childProfiles.addAll(response.body()?.children ?: emptyList())
                        renderProfiles()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this@ProfileSelectionActivity, "Failed to save profile", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ChildProfileResponse>, t: Throwable) {
                    Toast.makeText(this@ProfileSelectionActivity, "Error saving profile", Toast.LENGTH_SHORT).show()
                }
            })
        }

        dialog.show()
    }

    private fun goHome(email: String, selectedProfileName: String) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("userId", userId)
        intent.putExtra("email", email)
        intent.putExtra("childName", selectedProfileName)
        startActivity(intent)
        finish()
    }
}
