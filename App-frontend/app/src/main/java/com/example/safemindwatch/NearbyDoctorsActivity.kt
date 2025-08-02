package com.example.safemindwatch

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

class NearbyDoctorsActivity : AppCompatActivity() {
    private lateinit var etPlace: EditText
    private lateinit var btnFind: Button
    private lateinit var spinnerOpenStatus: Spinner
    private lateinit var spinnerRatings: Spinner
    private lateinit var rv: RecyclerView
    private lateinit var prog: ProgressBar
    private lateinit var dropdownRow: LinearLayout
    private lateinit var tvusername: TextView

    private val tempList = mutableListOf<Doctor>()
    private val displayList = mutableListOf<Doctor>()
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private var currentLat: Double? = null
    private var currentLng: Double? = null

    companion object {
        const val GOOGLE_API_KEY = "AIzaSyDZPtvWR7c4oQB9lyKNDzn93fCjcI1hx8I"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby_doctors)

        etPlace = findViewById(R.id.editTextPlace)
        btnFind = findViewById(R.id.buttonFindDoctors)
        spinnerOpenStatus = findViewById(R.id.spinnerOpenStatus)
        spinnerRatings = findViewById(R.id.spinnerRatings)
        rv = findViewById(R.id.recyclerViewDoctors)
        prog = findViewById(R.id.progressBarLoading)
        dropdownRow = findViewById(R.id.dropdownRow)
        tvusername= findViewById<TextView>(R.id.tvusername)

        rv.layoutManager = LinearLayoutManager(this)


        val userId = intent.getStringExtra("userId")
        Log.d("NDA", "userId: $userId")
        val email = intent.getStringExtra("email")
        var username = intent.getStringExtra("username")
        Log.d("NDA", "Home: $username")

        tvusername.text = if (!username.isNullOrEmpty()) "$username" else "User"



        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener {
                if (it != null) {
                    currentLat = it.latitude
                    currentLng = it.longitude
                } else {
                    Toast.makeText(this, "Enable location services for accurate distance", Toast.LENGTH_LONG).show()
                }
            }

        val filterListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                filterAndDisplayDoctors()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerOpenStatus.onItemSelectedListener = filterListener
        spinnerRatings.onItemSelectedListener = filterListener

        btnFind.setOnClickListener {
            val loc = etPlace.text.toString().trim()
            if (loc.isEmpty()) {
                Toast.makeText(this, "Enter city/place/postal code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prog.visibility = View.VISIBLE
            tempList.clear()
            displayList.clear()
            rv.adapter = null
            dropdownRow.visibility = View.VISIBLE
            geocodeAndSearch(loc)
        }
    }

    private fun geocodeAndSearch(query: String) {
        val enc = URLEncoder.encode(query, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$enc&key=$GOOGLE_API_KEY"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                prog.visibility = View.GONE
                Toast.makeText(this@NearbyDoctorsActivity, "Location lookup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onResponse(call: Call, resp: Response) {
                val body = resp.body?.string() ?: "{}"
                val json = JSONObject(body)
                val status = json.optString("status")

                if (status != "OK") {
                    runOnUiThread {
                        prog.visibility = View.GONE
                        Toast.makeText(this@NearbyDoctorsActivity, "Geocoding error: $status", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val resultsArr = json.optJSONArray("results")
                if (resultsArr != null && resultsArr.length() > 0) {
                    val result = resultsArr.getJSONObject(0)
                    val geometry = result.getJSONObject("geometry")
                    val location = geometry.getJSONObject("location")
                    val lat = location.getDouble("lat")
                    val lng = location.getDouble("lng")

                    fetchNearby(lat, lng)
                } else runOnUiThread {
                    prog.visibility = View.GONE
                    Toast.makeText(this@NearbyDoctorsActivity, "No location found", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun fetchNearby(lat: Double, lng: Double) {
        val radius = 50000
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=$lat,$lng&radius=$radius&keyword=child psychologist&key=$GOOGLE_API_KEY"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                prog.visibility = View.GONE
                Toast.makeText(this@NearbyDoctorsActivity, "Search failed", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call, resp: Response) {
                val arr = JSONObject(resp.body?.string() ?: "{}").optJSONArray("results") ?: return
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name")
                    val addr = obj.optString("vicinity")
                    val pid = obj.optString("place_id")
                    val loc = obj.getJSONObject("geometry").getJSONObject("location")
                    val doctorLat = loc.getDouble("lat")
                    val doctorLng = loc.getDouble("lng")

                    fetchExactDistanceAndDetails(name, addr, pid, doctorLat, doctorLng)
                }
            }
        })
    }

    private fun fetchExactDistanceAndDetails(name: String, addr: String, placeId: String, lat: Double, lng: Double) {
        val origin = "${currentLat},${currentLng}"
        val destination = "$lat,$lng"
        val distanceUrl = "https://maps.googleapis.com/maps/api/directions/json?origin=$origin&destination=$destination&key=$GOOGLE_API_KEY"

        client.newCall(Request.Builder().url(distanceUrl).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fetchDetails(name, addr, placeId, -1f, lat, lng, 0f)
            }

            override fun onResponse(call: Call, resp: Response) {
                val j = JSONObject(resp.body?.string() ?: "{}")
                val legs = j.optJSONArray("routes")
                    ?.optJSONObject(0)
                    ?.optJSONArray("legs")
                    ?.optJSONObject(0)
                val distMeters = legs?.optJSONObject("distance")?.optInt("value") ?: -1
                val dist = if (distMeters >= 0) distMeters.toFloat() else -1f
                fetchDetails(name, addr, placeId, dist, lat, lng, 0f)
            }
        })
    }

    private fun fetchDetails(name: String, addr: String, placeId: String, dist: Float, lat: Double, lng: Double, rating: Float) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                "place_id=$placeId&fields=formatted_phone_number,opening_hours,photos,reviews,rating,address_components&key=$GOOGLE_API_KEY"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                addDoctor(name, addr, null, dist, lat, lng, null, null, null, rating, null)
            }

            override fun onResponse(call: Call, resp: Response) {
                val j = JSONObject(resp.body?.string() ?: "{}").optJSONObject("result") ?: JSONObject()
                val phone = j.optString("formatted_phone_number")
                val oh = j.optJSONObject("opening_hours")
                val open = oh?.optBoolean("open_now")
                val hours = oh?.optJSONArray("weekday_text")?.optString(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1)
                val photoRef = j.optJSONArray("photos")?.optJSONObject(0)?.optString("photo_reference")
                val photoUrl = photoRef?.let {
                    "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference=$it&key=$GOOGLE_API_KEY"
                }

                val ratingValue = j.optDouble("rating", 0.0).toFloat()

                // Parse detailed reviews into List<Review>
                val reviewArr = j.optJSONArray("reviews")
                val reviews = mutableListOf<Review>()
                if (reviewArr != null) {
                    for (i in 0 until reviewArr.length()) {
                        val revObj = reviewArr.getJSONObject(i)
                        val reviewerName = revObj.optString("author_name", "Anonymous")
                        val reviewText = revObj.optString("text", "")
                        val rating = revObj.optDouble("rating", 0.0).toFloat()
                        val timestamp = revObj.optString("relative_time_description", "")
                        reviews.add(Review(reviewerName, reviewText, rating, timestamp))
                    }
                }

                val components = j.optJSONArray("address_components")
                val pinCode = (0 until (components?.length() ?: 0))
                    .mapNotNull { components?.optJSONObject(it) }
                    .firstOrNull { it.optJSONArray("types")?.toString()?.contains("postal_code") == true }
                    ?.optString("long_name")

                val fullAddr = if (!pinCode.isNullOrEmpty()) "$addr ($pinCode)" else addr

                addDoctor(name, fullAddr, phone, dist, lat, lng, hours, open, photoUrl, ratingValue, reviews)
            }
        })
    }

    private fun calculateDistance(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(fromLat, fromLng, toLat, toLng, result)
        return result[0]
    }

    @Synchronized
    private fun addDoctor(
        name: String,
        addr: String,
        phone: String?,
        dist: Float,
        lat: Double,
        lng: Double,
        hours: String?,
        open: Boolean?,
        photoUrl: String?,
        rating: Float,
        reviews: List<Review>?
    ) {
        val doctor = Doctor(name, addr, phone, dist, lat, lng, hours, open, photoUrl, rating, reviews)
        tempList.add(doctor)
        runOnUiThread { filterAndDisplayDoctors() }
    }

    private fun filterAndDisplayDoctors() {
        val selectedOpenStatus = spinnerOpenStatus.selectedItem?.toString() ?: "All"
        val selectedRating = spinnerRatings.selectedItem?.toString() ?: "All"
        val filtered = tempList.filter {
            val openMatch = when (selectedOpenStatus) {
                "All" -> true
                "Open" -> it.openNow == true
                "Closed" -> it.openNow == false
                else -> true
            }
            val ratingMatch = when (selectedRating) {
                "All Ratings" -> true
                else -> it.rating >= selectedRating.toFloatOrNull() ?: 0f
            }
            openMatch && ratingMatch
        }.sortedWith(compareByDescending<Doctor> { it.rating }.thenBy { it.distanceMeters })

        displayList.clear()
        displayList.addAll(filtered)
        rv.adapter = DoctorAdapter(displayList, this)
        prog.visibility = View.GONE
    }
}
