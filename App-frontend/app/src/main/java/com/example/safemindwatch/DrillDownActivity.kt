package com.example.safemindwatch

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safemindwatch.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DrillDownActivity : AppCompatActivity() {

    private lateinit var rvDrillResults: RecyclerView
    private lateinit var adapter: DrillDownAdapter
    private val resultList = mutableListOf<DrillDownItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drilldown)

        val label = intent.getStringExtra("label") ?: ""
        val mode = intent.getStringExtra("mode") ?: ""
        val dateParam = intent.getStringExtra("dateParam") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        val childName = intent.getStringExtra("childName") ?: ""
        val drillType = intent.getStringExtra("drillType") ?: ""

        Log.d("DrillDownActivity", "label=$label, mode=$mode, date=$dateParam, type=$drillType")

        findViewById<TextView>(R.id.tvDetails).text = "$label"

        rvDrillResults = findViewById(R.id.rvDrillResults)
        rvDrillResults.layoutManager = LinearLayoutManager(this)
        adapter = DrillDownAdapter(resultList)
        rvDrillResults.adapter = adapter

        fetchDrillDownData(userId, childName, label, mode, dateParam, drillType)
    }

    private fun fetchDrillDownData(
        userId: String,
        childName: String,
        label: String,
        mode: String,
        dateParam: String,
        drillType: String
    ) {
        val requestData = hashMapOf(
            "userId" to userId,
            "childName" to childName,
            "label" to label,
            "mode" to mode,
            "dateParam" to dateParam,
            "drillType" to drillType
        )

        RetrofitClient.apiService.getDrillDownData(requestData)
            .enqueue(object : Callback<DrillDownResponse> {
                override fun onResponse(
                    call: Call<DrillDownResponse>,
                    response: Response<DrillDownResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val dataList = response.body()!!.data

                        if (dataList.isNotEmpty()) {
                            resultList.clear()
                            resultList.addAll(dataList)
                            adapter.notifyDataSetChanged()
                            Log.d("DrillDownActivity", "Received ${dataList.size} items")
                        } else {
                            Toast.makeText(this@DrillDownActivity, "No data found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@DrillDownActivity, "No data found", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<DrillDownResponse>, t: Throwable) {
                    Toast.makeText(this@DrillDownActivity, "API failed: ${t.message}", Toast.LENGTH_SHORT).show()
                    Log.e("DrillDownActivity", "API error", t)
                }
            })
    }
}
