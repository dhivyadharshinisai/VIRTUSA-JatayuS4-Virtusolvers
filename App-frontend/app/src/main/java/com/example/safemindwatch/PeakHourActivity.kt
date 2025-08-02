package com.example.safemindwatch

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.safemindwatch.api.RetrofitClient
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PeakHourActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var btnPickDate: Button
    private lateinit var btnWeekly: Button
    private lateinit var btnPickRange: Button
    private lateinit var btnApplyRange: Button
    private lateinit var btnGenerate: Button
    private lateinit var etFromDateInline: EditText
    private lateinit var etToDateInline: EditText
    private lateinit var dateRangeLayout: LinearLayout
    private lateinit var tvReportInfo: TextView

    private lateinit var userId: String
    private lateinit var childName: String

    private val hourLabels12 = arrayOf(
        "12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM",
        "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM",
        "7 PM", "8 PM", "9 PM", "10 PM", "11 PM"
    )

    private val barEntries = mutableListOf<BarEntry>()
    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peak_hour)

        barChart = findViewById(R.id.barChart)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnPickRange = findViewById(R.id.btnPickRange)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        btnGenerate = findViewById(R.id.btnGenerate)
        etFromDateInline = findViewById(R.id.etFromDateInline)
        etToDateInline = findViewById(R.id.etToDateInline)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)
        tvReportInfo = findViewById(R.id.tvReportInfo)

        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: ""

        if (userId.isBlank() || childName.isBlank()) {
            Toast.makeText(this, "User / Child info missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupChart()
        highlightSelectedButton(btnWeekly)
        setPreviousWeekRange()

        btnPickDate.setOnClickListener {
            highlightSelectedButton(btnPickDate)
            dateRangeLayout.visibility = View.GONE
            btnApplyRange.visibility = View.GONE
            tvReportInfo.visibility = View.VISIBLE
            showDatePicker()
        }

        btnWeekly.setOnClickListener {
            highlightSelectedButton(btnWeekly)
            tvReportInfo.visibility = View.VISIBLE
            setPreviousWeekRange()
        }

        btnPickRange.setOnClickListener {
            highlightSelectedButton(btnPickRange)
            setDefaultPickRange()
        }

        etFromDateInline.setOnClickListener { showInlineDatePicker(etFromDateInline) }
        etToDateInline.setOnClickListener { showInlineDatePicker(etToDateInline) }

        btnApplyRange.setOnClickListener {
            val fromDateStr = etFromDateInline.text.toString()
            val toDateStr = etToDateInline.text.toString()
            if (fromDateStr.isBlank() || toDateStr.isBlank()) {
                Toast.makeText(this, "Please select both from and to dates.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            try {
                val fromDate = backendFormat.format(displayFormat.parse(fromDateStr)!!)
                val toDate = backendFormat.format(displayFormat.parse(toDateStr)!!)
                val label = Html.fromHtml(
                    "Report generated from <b>$fromDateStr</b> to <b>$toDateStr</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )
                tvReportInfo.text = label
                fetchPeakData("range", "$fromDate|$toDate", label)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid date format selected.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGenerate.setOnClickListener {
            val popupMenu = PopupMenu(this, btnGenerate)
            popupMenu.menuInflater.inflate(R.menu.menu_export, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_export_image -> exportChartAsImage()
                    R.id.menu_export_pdf -> exportChartAsPDF()
                }
                true
            }
            popupMenu.show()
        }
    }

    private fun setupChart() {
        barChart.apply {
            description.isEnabled = false
            animateY(1000)
            setScaleEnabled(false)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
        }
    }

    private fun highlightSelectedButton(selected: Button) {
        listOf(btnPickDate, btnWeekly, btnPickRange).forEach {
            it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            it.setTextColor(Color.WHITE)
        }
        selected.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        selected.setTextColor(Color.WHITE)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            cal.set(year, month, dayOfMonth)
            val displayDate = displayFormat.format(cal.time)
            val backendDate = backendFormat.format(cal.time)
            val label = Html.fromHtml(
                "Report generated for <b>$displayDate</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
            tvReportInfo.text = label
            tvReportInfo.visibility = View.VISIBLE
            fetchPeakData("date", backendDate, label)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dpd.show()
    }

    private fun showInlineDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            cal.set(year, month, dayOfMonth)
            target.setText(displayFormat.format(cal.time))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dpd.show()
    }

    private fun setPreviousWeekRange() {
        val cal = Calendar.getInstance()
        val end = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val start = cal.time

        etFromDateInline.isEnabled = false
        etToDateInline.isEnabled = false
        btnApplyRange.visibility = View.GONE
        dateRangeLayout.visibility = View.GONE
        tvReportInfo.visibility = View.VISIBLE

        etFromDateInline.setText(displayFormat.format(start))
        etToDateInline.setText(displayFormat.format(end))

        val label = Html.fromHtml(
            "Report generated from <b>${displayFormat.format(start)}</b> to <b>${
                displayFormat.format(
                    end
                )
            }</b>",
            Html.FROM_HTML_MODE_LEGACY
        )
        tvReportInfo.text = label
        fetchPeakData("range", "${backendFormat.format(start)}|${backendFormat.format(end)}", label)
    }

    private fun setDefaultPickRange() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        val start = cal.time
        val end = Date()

        etFromDateInline.isEnabled = true
        etToDateInline.isEnabled = true
        btnApplyRange.visibility = View.VISIBLE
        dateRangeLayout.visibility = View.VISIBLE

        val fromDisplay = displayFormat.format(start)
        val toDisplay = displayFormat.format(end)

        etFromDateInline.setText(fromDisplay)
        etToDateInline.setText(toDisplay)

        try {
            val fromDate = backendFormat.format(displayFormat.parse(fromDisplay)!!)
            val toDate = backendFormat.format(displayFormat.parse(toDisplay)!!)

            val label = Html.fromHtml(
                "Report generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
            tvReportInfo.text = label
            fetchPeakData("range", "$fromDate|$toDate", label)
        } catch (e: Exception) {
            Toast.makeText(etFromDateInline.context, "Invalid date format", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun fetchPeakData(mode: String, date: String?, label: CharSequence) {
        RetrofitClient.apiService.getPeakHours(userId, childName, mode, date)
            .enqueue(object : Callback<PeakHourResponse> {
                override fun onResponse(
                    call: Call<PeakHourResponse>,
                    response: Response<PeakHourResponse>
                ) {
                    if (response.isSuccessful) {
                        val map = response.body()?.data ?: emptyMap()
                        renderChart(map, label)
                    } else {
                        Toast.makeText(
                            this@PeakHourActivity,
                            "API error: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        renderChart(emptyMap(), label)
                    }
                }

                override fun onFailure(call: Call<PeakHourResponse>, t: Throwable) {
                    Toast.makeText(this@PeakHourActivity, "Network error", Toast.LENGTH_SHORT)
                        .show()
                    renderChart(emptyMap(), label)
                }
            })
    }

    private fun renderChart(data: Map<String, Number>, label: CharSequence) {
        barEntries.clear()
        val hourMap = mutableMapOf<Int, Float>()
        tvReportInfo.text = label

        data.forEach { (key, value) ->
            val hourIndex = extractHourIndex(key)
            if (hourIndex != null) {
                hourMap[hourIndex] = value.toFloat()
            }
        }

        for (i in 0..23) {
            barEntries.add(BarEntry(i.toFloat(), hourMap[i] ?: 0f))
        }

        val dataSet = BarDataSet(barEntries, "Hourly Harmful Activity").apply {
            color = Color.parseColor("#800080")
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else value.toInt().toString()
                }
            }
        }

        barChart.data = BarData(dataSet).apply { barWidth = 0.9f }

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textSize = 10f
            axisMinimum = -0.5f
            axisMaximum = 23.5f
            labelCount = 24
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val i = value.toInt()
                    return if (i in 0..23) hourLabels12[i] else ""
                }
            }
        }

        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val hourIndex = e?.x?.toInt() ?: return

                val hourDisplayLabel = hourLabels12[hourIndex]
                val labelFormatted = hourDisplayLabel.lowercase().replace(" ", "")
                val from = etFromDateInline.text.toString()
                val to = etToDateInline.text.toString()
                val currentMode = when {
                    btnPickDate.isPressed -> "date"
                    else -> "range"
                }
                val dateParam = if (currentMode == "date") {
                    backendFormat.format(displayFormat.parse(from)!!)
                } else {
                    "${backendFormat.format(displayFormat.parse(from)!!)}|${
                        backendFormat.format(displayFormat.parse(to)!!)
                    }"
                }

                val intent = Intent(this@PeakHourActivity, DrillDownActivity::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("childName", childName)
                    putExtra("mode", "hour")
                    putExtra("label", labelFormatted)
                    putExtra("dateParam", dateParam)
                    putExtra("drillType", "hourly")
                }
                startActivity(intent)
            }

            override fun onNothingSelected() {}
        })

        barChart.invalidate()
    }


    private fun extractHourIndex(key: String?): Int? {
        if (key.isNullOrBlank()) return null
        val match = Regex("""^(\d{1,2})(am|pm)$""", RegexOption.IGNORE_CASE).find(key.trim())
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val ampm = match.groupValues[2].lowercase()
            return when {
                ampm == "am" -> if (hour == 12) 0 else hour
                ampm == "pm" -> if (hour == 12) 12 else hour + 12
                else -> null
            }
        }
        return null
    }

    private fun exportChartAsImage() {
        try {
            val bitmap =
                Bitmap.createBitmap(barChart.width, barChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            barChart.draw(canvas)

            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "peak_hours_${System.currentTimeMillis()}.png"
            )

            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            Toast.makeText(this, "Saved Image: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Image export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportChartAsPDF() {
        try {
            val bitmap =
                Bitmap.createBitmap(barChart.width, barChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            barChart.draw(canvas)

            val pdfDoc = PdfDocument()
            val pageInfo =
                PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height + 200, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val pdfCanvas = page.canvas
            pdfCanvas.drawColor(Color.WHITE)
            pdfCanvas.drawBitmap(bitmap, 0f, 100f, null)

            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isFakeBoldText = true
            }
            pdfCanvas.drawText(
                Html.fromHtml(
                    tvReportInfo.text.toString(),
                    Html.FROM_HTML_MODE_LEGACY
                ).toString(), 40f, 60f, paint
            )

            pdfDoc.finishPage(page)
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "peak_hours_${System.currentTimeMillis()}.pdf"
            )

            pdfDoc.writeTo(FileOutputStream(file))
            pdfDoc.close()
            Toast.makeText(this, "Saved PDF: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    companion object {
        private val hourLabels12 = arrayOf(
            "12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM",
            "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM",
            "7 PM", "8 PM", "9 PM", "10 PM", "11 PM"
        )

        fun exportChartBitmap(
            context: Context,
            userId: String,
            mode: String,
            dateParam: String?,
            childName: String,
            onBitmapReady: (Bitmap?) -> Unit
        ) {
            Log.d("ChartExport", "[PeakHour] Export called with: $userId, $childName, $dateParam")

            val chart = BarChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.WHITE)
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setScaleEnabled(false)
                animateY(1000)
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                legend.isEnabled = false
            }

            RetrofitClient.apiService.getPeakHours(userId, childName, mode, dateParam)
                .enqueue(object : Callback<PeakHourResponse> {
                    override fun onResponse(
                        call: Call<PeakHourResponse>,
                        response: Response<PeakHourResponse>
                    ) {
                        if (!response.isSuccessful) {
                            Log.e("ChartExport", "[PeakHour] API error code: ${response.code()}")
                            onBitmapReady(null)
                            return
                        }

                        val data = response.body()?.data ?: emptyMap()
                        val barEntries = mutableListOf<BarEntry>()
                        val hourMap = mutableMapOf<Int, Float>()

                        // Same fixed key parsing here
                        fun extractHourIndex(key: String?): Int? {
                            if (key.isNullOrBlank()) return null
                            val match = Regex("""^(\d{1,2})(am|pm)$""", RegexOption.IGNORE_CASE).find(key.trim())
                            if (match != null) {
                                val hour = match.groupValues[1].toIntOrNull() ?: return null
                                val ampm = match.groupValues[2].lowercase()
                                return when {
                                    ampm == "am" -> if (hour == 12) 0 else hour
                                    ampm == "pm" -> if (hour == 12) 12 else hour + 12
                                    else -> null
                                }
                            }
                            return null
                        }

                        data.forEach { (key, value) ->
                            val hourIndex = extractHourIndex(key)
                            if (hourIndex != null) {
                                hourMap[hourIndex] = value.toFloat()
                            }
                        }

                        for (i in 0..23) {
                            barEntries.add(BarEntry(i.toFloat(), hourMap[i] ?: 0f))
                        }

                        val dataSet = BarDataSet(barEntries, "Hourly Harmful Activity").apply {
                            color = Color.parseColor("#800080")
                            valueTextSize = 10f
                            valueTextColor = Color.BLACK
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return if (value == 0f) "" else value.toInt().toString()
                                }
                            }
                        }

                        chart.data = BarData(dataSet).apply { barWidth = 0.9f }

                        chart.xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            granularity = 1f
                            setDrawGridLines(false)
                            textSize = 10f
                            axisMinimum = -0.5f
                            axisMaximum = 23.5f
                            labelCount = 24
                            valueFormatter = object : ValueFormatter() {
                                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                                    val i = value.toInt()
                                    return if (i in 0..23) hourLabels12[i] else ""
                                }
                            }
                        }

                        val chartSizeDp = 900
                        val density = context.resources.displayMetrics.density
                        val chartSizePx = (chartSizeDp * density).toInt()

                        chart.layoutParams = LinearLayout.LayoutParams(chartSizePx, chartSizePx)
                        chart.measure(
                            View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY)
                        )
                        chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)

                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val bitmap = Bitmap.createBitmap(
                                    chart.width,
                                    chart.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bitmap)
                                chart.draw(canvas)
                                Log.d("ChartExport", "[PeakHour] Chart export success")
                                onBitmapReady(bitmap)
                            } catch (e: Exception) {
                                Log.e("ChartExport", "[PeakHour] Bitmap creation failed", e)
                                onBitmapReady(null)
                            }
                        }, 300)
                    }

                    override fun onFailure(call: Call<PeakHourResponse>, t: Throwable) {
                        Log.e("ChartExport", "[PeakHour] Chart export failed: ${t.message}")
                        onBitmapReady(null)
                    }
                })
        }
    }
}
