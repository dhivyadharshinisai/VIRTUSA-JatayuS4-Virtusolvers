package com.example.safemindwatch

import android.Manifest
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.safemindwatch.api.ApiService
import com.example.safemindwatch.api.RetrofitClient
import com.example.safemindwatch.api.SOSAlertResponse
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvName: String
    private lateinit var username: String
    private lateinit var imgProfile: ShapeableImageView
    private lateinit var cardPieChart: CardView
    private lateinit var cardStatistics: CardView
    private lateinit var abnormalQueriesCard: CardView
    private lateinit var predictionCard: CardView
    private lateinit var imagePrediction: ImageView
    private lateinit var tvPrediction: TextView
    private lateinit var cardSentiment: CardView
    private lateinit var cardPeakHour: CardView
    private lateinit var ic_settings: ImageView

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var keepChecking = false
    private var sosAlertActive = false
    private var alertHandled = false

    private val api by lazy { RetrofitClient.getInstance().create(ApiService::class.java) }
    private val GOOGLE_API_KEY = "YOUR_GOOGLE_API_KEY"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private lateinit var userId: String
    private lateinit var childName: String

    companion object {

        const val SOS_ALERT_REQUEST_CODE = 5678
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvWelcome = findViewById(R.id.tvWelcome)
        imgProfile = findViewById(R.id.ic_menu)
        cardPieChart = findViewById(R.id.cardPieChart)
        cardStatistics = findViewById(R.id.cardStatistics)
        abnormalQueriesCard = findViewById(R.id.abnormalQueriesCard)
        predictionCard = findViewById(R.id.predictionCard)
        imagePrediction = findViewById(R.id.imagePrediction)
        tvPrediction = findViewById(R.id.tvPrediction)
        cardSentiment = findViewById(R.id.cardSentiment)
        cardPeakHour = findViewById(R.id.cardpeakhour)
        ic_settings = findViewById(R.id.ic_settings)

        val sharedPref = getSharedPreferences("SafeMindWatchPrefs", Context.MODE_PRIVATE)
        userId = intent.getStringExtra("userId").toString()
        var email = intent.getStringExtra("email")
        childName = intent.getStringExtra("childName")
            ?: sharedPref.getString("childName", "Child") ?: "Child"
        Log.d("IntentDebug", "Home: $childName")
        username = intent.getStringExtra("username").toString()
        Log.d("HUNNNNNN", "Home: $username")
        if (userId.isNullOrEmpty() || email.isNullOrEmpty()) {
            userId = sharedPref.getString("userId", null).toString()
            email = sharedPref.getString("email", null)
            username = sharedPref.getString("username", null).toString()
        } else {
            sharedPref.edit().apply {
                putString("userId", userId)
                putString("email", email)
                putString("childName", childName)

                apply()
            }
        }
        // In onCreate or after findViewById
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.nav_home


        bottomNav.setOnItemSelectedListener {
            val targetIntent = when (it.itemId) {
                R.id.nav_home -> null
                R.id.nav_specialists -> Intent(this, DoctorSearchActivity::class.java)
                R.id.nav_notification -> Intent(this, SOS_Notification::class.java)
                R.id.nav_profile -> Intent(this, ProfileActivity::class.java)
                else -> null
            }

            if (targetIntent != null) {
                targetIntent.putExtra("userId", userId)
                targetIntent.putExtra("email", email)
                Log.d("Home", "Passing username: $username")
                targetIntent.putExtra("username", username)
                startActivity(targetIntent)

                // For specialists, handle location permission/fetch after navigation, if still needed:
                if (it.itemId == R.id.nav_specialists) {
                    if (checkLocationPermission()) fetchNearbyPsychologists() else requestLocationPermission()
                }
                true
            } else {
                // If you are using fragments or no navigation for settings, return accordingly
                true  // or false, depending on your logic
            }
        }


        imgProfile.setOnClickListener {
            Log.d("HomeActivity", "Opening Profile with userId=$userId")
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                putExtra("userId", userId)
                putExtra("email", email)
            })
        }

        ic_settings.setOnClickListener {
            val popupMenu = PopupMenu(this, ic_settings, Gravity.END, 0, R.style.PopupMenuCompact)
            popupMenu.menu.add("Export Reports")
            popupMenu.setOnMenuItemClickListener {
                if (it.title == "Export Reports") {
                    showDateRangeDialog()
                    true
                } else false
            }
            popupMenu.show()

        }


        tvWelcome.text = "${childName}'s Activity"



        lifecycleScope.launch {
            userId?.let {
                launch { startAlertPolling(it) }
                launch { OverallPrediction(it , childName) }

            }
            email?.let {

                launch { fetchProfileImage(it) }
            }
        }


        cardPieChart.setOnClickListener {
            val intent = Intent(this, PieChartActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }

        cardStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsCardActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }

        abnormalQueriesCard.setOnClickListener {
            val intent = Intent(this, AbnormalQueriesActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }

        cardSentiment.setOnClickListener {
            val intent = Intent(this, SentimentActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }

        predictionCard.setOnClickListener {
            val intent = Intent(this, Mental_Health_Prediction::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }

        cardPeakHour.setOnClickListener {
            val intent = Intent(this, PeakHourActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("email", email)
            intent.putExtra("childName", childName)
            startActivity(intent)
        }
        fetchUserDetails(userId)
    }

    private suspend fun fetchProfileImage(email: String) {
        withContext(Dispatchers.IO) {
            try {
                val json = JsonObject().apply { addProperty("email", email) }
                val response = api.getProfileImage(json).execute()
                if (response.isSuccessful) {
                    val base64Image = response.body()?.imageData
                    if (!base64Image.isNullOrEmpty()) {
                        val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                        withContext(Dispatchers.Main) {
                            imgProfile.setImageBitmap(bitmap)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Failed to load profile image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchUserDetails(userId: String) {
        Log.d("SettingsUpdate", "fetchUserDetails() -> userId=$userId")

        api.getUserById(userId).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                Log.d("SettingsUpdate", "fetchUserDetails() response code=${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    bindUser(user)
                } else {

                    Toast.makeText(this@HomeActivity, "Failed to fetch user info", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {

                Toast.makeText(this@HomeActivity, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun bindUser(user: User) {


        Log.d("Debug", "User name is: ${user.name}")
        tvName = user.name
        username= user.name

    }

    private fun OverallPrediction(
        userId: String,
        childName: String,
        mode: String = "week",       // backend normalizes "weekly" to "week"
        date: String? = null         // "YYYY-MM-DD" or "from|to"
    ) {
        RetrofitClient.apiService.getPredictionSummary(
            userId = userId,
            childName = childName,
            mode = mode,
            date = date
        ).enqueue(object : retrofit2.Callback<PredictionSummaryResponse> {

            override fun onResponse(
                call: retrofit2.Call<PredictionSummaryResponse>,
                response: retrofit2.Response<PredictionSummaryResponse>
            ) {
                if (!response.isSuccessful) {
                    tvPrediction.text = "Prediction: Server ${response.code()}"
                    return
                }

                val body = response.body()
                val counts = body?.data

                // Safely unwrap nullable Int? values
                val suicide   = counts?.suicide   ?: 0
                val anxiety   = counts?.anxiety   ?: 0
                val depression= counts?.depression?: 0
                val isolation = counts?.isolation ?: 0
                val noRisk    = counts?.noRisk    ?: 0  // adjust property name if different
                // total from server if sent, else derive
                val total     = counts?.total ?: body?.count ?: (suicide + anxiety + depression + isolation + noRisk)

                val label: RiskLabel = when {
                    total == 0 -> RiskLabel.NO_HISTORY
                    // any harmful?
                    (suicide + anxiety + depression + isolation) > 0 -> {
                        // Pick *max* harmful category
                        val maxPair = listOf(
                            RiskLabel.SUICIDE    to suicide,
                            RiskLabel.ANXIETY    to anxiety,
                            RiskLabel.DEPRESSION to depression,
                            RiskLabel.ISOLATION  to isolation
                        ).maxBy { it.second }
                        maxPair.first
                    }
                    // all harmful zero, but safe browsing exists
                    noRisk > 0 -> RiskLabel.NO_RISK
                    else -> RiskLabel.NO_HISTORY
                }

                val (imgRes, text) = when (label) {
                    RiskLabel.NO_RISK ->
                        R.drawable.safe_browsing to "Prediction: Safe Browsing"
                    RiskLabel.DEPRESSION ->
                        R.drawable.depression_icon to "Prediction: Depression "
                    RiskLabel.SUICIDE ->
                        R.drawable.suicide_icon to "Prediction: Suicide "
                    RiskLabel.ANXIETY ->
                        R.drawable.anxiety_icon to "Prediction: Anxiety "
                    RiskLabel.ISOLATION ->
                        R.drawable.isolation_icon to "Prediction: Isolation Signals"
                    RiskLabel.NO_HISTORY ->
                        R.drawable.no_browsing_history to "No data "
                    RiskLabel.UNKNOWN ->
                        null to "Prediction: Unknown"
                }

                imgRes?.let { imagePrediction.setImageResource(it) }
                tvPrediction.text = text
            }

            override fun onFailure(
                call: retrofit2.Call<PredictionSummaryResponse>,
                t: Throwable
            ) {
                tvPrediction.text = "Prediction: Network error"
            }
        })
    }


    private fun showDateRangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val fromDate = EditText(this).apply {
            hint = "From Date (dd-MM-yyyy)"
            isFocusable = false
            isClickable = true
        }

        val toDate = EditText(this).apply {
            hint = "To Date (dd-MM-yyyy)"
            isFocusable = false
            isClickable = true
        }

        // Function to show date picker
        fun showDatePicker(editText: EditText) {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(this, { _, y, m, d ->
                val selectedDate = String.format("%02d-%02d-%04d", d, m + 1, y)
                editText.setText(selectedDate)
            }, year, month, day)

            datePicker.show()
        }

        fromDate.setOnClickListener { showDatePicker(fromDate) }
        toDate.setOnClickListener { showDatePicker(toDate) }

        layout.addView(fromDate)
        layout.addView(toDate)

        AlertDialog.Builder(this)
            .setTitle("Export Report")
            .setView(layout)
            .setPositiveButton("Export") { _, _ ->
                val from = fromDate.text.toString().trim()
                val to = toDate.text.toString().trim()
                if (from.isNotEmpty() && to.isNotEmpty()) {
                    exportAllCharts(from, to)
                } else {
                    Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun exportAllCharts(fromDate: String, toDate: String) {
        val chartPages = mutableListOf<ChartPage>()
        val totalCharts = 6
        var loadedCount = 0

        fun checkIfAllChartsReadyAndExportPDF() {
            if (++loadedCount == totalCharts) {
                generatePDF(chartPages, fromDate, toDate)
            }
        }

        try {
            val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val fromParsed = backendFormat.format(displayFormat.parse(fromDate)!!)
            val toParsed = backendFormat.format(displayFormat.parse(toDate)!!)
            val dateParam = "$fromParsed|$toParsed"

            // 1. Pie Chart
            PieChartActivity.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Query Analysis"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[PieChart] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[PieChart] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

            // 2. Statistics Card
            StatisticsCardActivity.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Exposure Time"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[Statistics] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[Statistics] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

            // 3. Abnormal Queries
            AbnormalQueriesActivity.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Abnormal Queries"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[Abnormal] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[Abnormal] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

            // 4. Mental Health Prediction
            Mental_Health_Prediction.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Mind Analysis"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[MentalHealth] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[MentalHealth] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

            // 5. Sentiment Analysis
            SentimentActivity.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Sentiment Value"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[Sentiment] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[Sentiment] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

            // 6. Peak Hour
            PeakHourActivity.exportChartBitmap(
                context = this,
                userId = userId,
                mode = "range",
                dateParam = dateParam,
                childName = childName
            ) { bitmap ->
                val title = "Peak Hours"
                if (bitmap != null) {
                    chartPages.add(ChartPage(bitmap, title))
                    Log.d("PDFExport", "[PeakHour] Bitmap added. Size: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("PDFExport", "[PeakHour] Bitmap is null")
                }
                checkIfAllChartsReadyAndExportPDF()
            }

        } catch (e: Exception) {
            Log.e("ChartExport", "Date parsing error: ${e.message}")
            Toast.makeText(this, "Date format error", Toast.LENGTH_SHORT).show()
        }
    }



    private fun generatePDF(chartPages: List<ChartPage>, from: String, to: String) {
        Log.d("PDFExport", "Starting PDF generation...")

        val doc = PdfDocument()
        var pageNumber = 1

        chartPages.forEachIndexed { i, chartPage ->
            Log.d("PDFExport", "Preparing page ${i + 1} for bitmap of size ${chartPage.bitmap.width}x${chartPage.bitmap.height}")

            val pageInfo = PdfDocument.PageInfo.Builder(chartPage.bitmap.width, chartPage.bitmap.height + 150, pageNumber).create()
            val page = doc.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isFakeBoldText = true
            }

            canvas.drawText("${chartPage.title} ($from to $to)", 40f, 60f, paint)
            canvas.drawBitmap(chartPage.bitmap, 0f, 100f, null)

            doc.finishPage(page)
            pageNumber++
        }

        try {
            val fileName = "${childName}'s_Mental_Health_Report_${System.currentTimeMillis()}.pdf"

            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    Log.e("PDFExport", "❌ Failed to create MediaStore entry.")
                    Toast.makeText(this, "Error: Cannot access Downloads", Toast.LENGTH_SHORT).show()
                    return
                }

                resolver.openOutputStream(uri) ?: throw Exception("Failed to open output stream.")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file)
            }

            doc.writeTo(outputStream)
            outputStream.close()

            Toast.makeText(this, "✅ PDF saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            Log.d("PDFExport", "✅ PDF saved successfully as $fileName")
        } catch (e: Exception) {
            Log.e("PDFExport", "❌ Error writing PDF", e)
            Toast.makeText(this, "❌ PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            doc.close()
            Log.d("PDFExport", "PDF document closed.")
        }
    }


    fun startAlertPolling(userId: String) {
        keepChecking = true
        handler = Handler(Looper.getMainLooper())

        runnable = object : Runnable {
            override fun run() {
                if (!keepChecking) return

                Log.d("SOSPolling", "Polling SOS alert for userId: $userId")

                RetrofitClient.apiService.checkSOSAlert(userId).enqueue(object : Callback<SOSAlertResponse> {
                    override fun onResponse(call: Call<SOSAlertResponse>, response: Response<SOSAlertResponse>) {
                        if (response.isSuccessful) {
                            val sosResponse = response.body()
                            Log.d("SOSPolling", "Received SOS alert response: $sosResponse")
                            if (sosResponse?.sosActive == true) {
                                Log.d("SOSPolling", "SOS alert is ACTIVE for child: ${sosResponse.childName}, query: ${sosResponse.query}")
                                showSosAlert(sosResponse.childName, sosResponse.query)
                            } else {
                                Log.d("SOSPolling", "SOS alert is inactive or missing")
                            }
                        } else {
                            Log.w("SOSPolling", "Response not successful: ${response.code()} ${response.message()}")
                        }
                        // Schedule next polling after delay (e.g., 15 seconds)
                        handler.postDelayed(runnable, 15000)
                    }

                    override fun onFailure(call: Call<SOSAlertResponse>, t: Throwable) {
                        Log.e("SOSPolling", "Failed to check SOS alert", t)
                        // Schedule next polling anyway
                        handler.postDelayed(runnable, 15000)
                    }
                })
            }
        }

        handler.post(runnable)
    }


    private fun showSosAlert(childName: String?, query: String) {
        val displayName = childName ?: "your child"
        // val query = query ?: "searched query"  // Query param is already non-null, no need to redeclare
        Log.d("TriggerAlert", "⚠️ SOS Triggered for $displayName with query: \"$query\"")

        val intent = Intent(this, SosAlertActivity::class.java).apply {
            putExtra("userId", userId)
            putExtra("childName", displayName)
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)

        triggerVibration()
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SOS_ALERT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // User acknowledged the alert; mark it handled
                alertHandled = true
                sosAlertActive = false


            }
        }
    }



    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
    }



    override fun onDestroy() {
        super.onDestroy()
        keepChecking = false
        if (::handler.isInitialized && ::runnable.isInitialized) {
            handler.removeCallbacks(runnable)
        }
    }


    private fun checkLocationPermission() = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) fetchNearbyPsychologists()
        else Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show()
    }

    private fun getLastKnownLocation(): Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) bestLocation = l
        }
        return bestLocation
    }

    private fun fetchNearbyPsychologists() {
        val location = getLastKnownLocation() ?: run {
            Toast.makeText(this, "Unable to get location.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.latitude},${location.longitude}&radius=5000&type=doctor&keyword=psychologist&key=$GOOGLE_API_KEY"
        OkHttpClient().newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "Failed to fetch psychologists", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val jsonData = response.body?.string() ?: return
                val placesList = parsePlacesResponse(jsonData)
                runOnUiThread {
                    startActivity(Intent(this@HomeActivity, NearbyDoctorsActivity::class.java).apply {
                        putParcelableArrayListExtra("places_list", placesList)
                    })
                }
            }
        })
    }


    private fun parsePlacesResponse(jsonData: String): ArrayList<Place> {
        val places = ArrayList<Place>()
        val results = JSONObject(jsonData).getJSONArray("results")
        for (i in 0 until results.length()) {
            val placeObj = results.getJSONObject(i)
            val name = placeObj.getString("name")
            val address = placeObj.optString("vicinity", "No address")
            val location = placeObj.getJSONObject("geometry").getJSONObject("location")
            val lat = location.getDouble("lat")
            val lng = location.getDouble("lng")
            places.add(Place(name, address, lat, lng))
        }
        return places
    }

    data class Place(val name: String, val address: String, val lat: Double, val lng: Double) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readDouble(),
            parcel.readDouble()
        )
        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeString(name)
            parcel.writeString(address)
            parcel.writeDouble(lat)
            parcel.writeDouble(lng)
        }
        override fun describeContents(): Int = 0
        companion object CREATOR : android.os.Parcelable.Creator<Place> {
            override fun createFromParcel(parcel: android.os.Parcel): Place = Place(parcel)
            override fun newArray(size: Int): Array<Place?> = arrayOfNulls(size)
        }
    }
}

data class ChartPage(
    val bitmap: Bitmap,
    val title: String
)
