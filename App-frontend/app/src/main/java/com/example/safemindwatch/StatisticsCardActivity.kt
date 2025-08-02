package com.example.safemindwatch

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.safemindwatch.api.RetrofitClient
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class StatisticsCardActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var btnPickDate: Button
    private lateinit var btnWeekly: Button
    private lateinit var btnPickRange: Button
    private lateinit var etFromDateInline: EditText
    private lateinit var etToDateInline: EditText
    private lateinit var btnApplyRange: Button
    private lateinit var dateRangeLayout: LinearLayout
    private lateinit var tvReportInfo: TextView
    private lateinit var xAxisLabel: TextView
    private lateinit var btnGenerate: Button
    private lateinit var tvSelectedRange: TextView


    private val harmfulTimeByDate = LinkedHashMap<String, Float>()

    private val holoBlue = Color.parseColor("#33B5E5")
    private val holoGreen = Color.parseColor("#99CC00")

    private lateinit var userId: String
    private lateinit var childName: String

    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_card)

        // --- Views ---
        barChart = findViewById(R.id.barChart)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnPickRange = findViewById(R.id.btnPickRange)
        etFromDateInline = findViewById(R.id.etFromDateInline)
        etToDateInline = findViewById(R.id.etToDateInline)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)
        tvReportInfo = findViewById(R.id.tvReportInfo)
        xAxisLabel = findViewById(R.id.xAxisLabel)
        btnGenerate = findViewById(R.id.btnGenerate)
        tvSelectedRange = findViewById(R.id.tvSelectedRange)


        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: "Your Child"

        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setAllButtonsToBlue()
        setupChart()
        setupListeners()

        highlightButton(btnWeekly)
        applyWeeklyMode()
    }

    private fun setupListeners() {

        btnPickDate.setOnClickListener {
            highlightButton(btnPickDate)
            hideRangeInputs()
            showDatePicker()
            tvSelectedRange.visibility = View.GONE
        }

        btnWeekly.setOnClickListener {
            highlightButton(btnWeekly)
            applyWeeklyMode()
        }

        btnPickRange.setOnClickListener {
            highlightButton(btnPickRange)
            showRangeInputs()
            tvSelectedRange.visibility = View.GONE
            tvReportInfo.visibility = View.VISIBLE

            val startOfWeekCalendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            }
            val todayCalendar = Calendar.getInstance()

            val startOfWeek = startOfWeekCalendar.time
            val today = todayCalendar.time

            val displayFromDate = displayFormat.format(startOfWeek)
            val displayToDate = displayFormat.format(today)
            val apiFromDate = apiFormat.format(startOfWeek)
            val apiToDate = apiFormat.format(today)

            etFromDateInline.setText(displayFromDate)
            etToDateInline.setText(displayToDate)

            etFromDateInline.isEnabled = true
            etFromDateInline.isClickable = true
            etToDateInline.isEnabled = true
            etToDateInline.isClickable = true
            btnApplyRange.visibility = View.VISIBLE

            tvReportInfo.text = Html.fromHtml(
                "Report generated from <b>$displayFromDate</b> to <b>$displayToDate</b>",
                Html.FROM_HTML_MODE_LEGACY
            )

            fetchStatistics(
                userId = userId,
                mode = "range",
                date = "$apiFromDate|$apiToDate",
                xLabelText = "$displayFromDate to $displayToDate",
                fromDateStr = apiFromDate,
                toDateStr = apiToDate
            )
        }

        btnApplyRange.setOnClickListener {
            val from = etFromDateInline.text.toString().trim()
            val to = etToDateInline.text.toString().trim()
            tvReportInfo.visibility = View.VISIBLE
            tvSelectedRange.visibility = View.GONE

            try {
                val apiFrom = apiFormat.format(displayFormat.parse(from)!!)
                val apiTo = apiFormat.format(displayFormat.parse(to)!!)

                highlightButton(btnApplyRange)
                showRangeInputs()

                tvReportInfo.text = Html.fromHtml(
                    "Report generated from <b>$from</b> to <b>$to</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )

                fetchStatistics(
                    userId = userId,
                    mode = "range",
                    date = "$apiFrom|$apiTo",
                    xLabelText = "$from to $to",
                    fromDateStr = apiFrom,
                    toDateStr = apiTo
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            }
        }

        etFromDateInline.setOnClickListener {
            if (etFromDateInline.isEnabled) showInlineDatePicker(etFromDateInline)
        }

        etToDateInline.setOnClickListener {
            if (etToDateInline.isEnabled) showInlineDatePicker(etToDateInline)
        }

        btnGenerate.setOnClickListener {
            val popupMenu = PopupMenu(this, btnGenerate)
            popupMenu.menuInflater.inflate(R.menu.menu_export, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
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
            setFitBars(true)
            animateY(1000)
            setScaleEnabled(false)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            legend.isEnabled = false
        }
    }

    private fun exportChartAsImage() {
        try {
            val bitmap = getChartBitmapWithAxisLabels()
            val fileName = "Exposure_Time_${System.currentTimeMillis()}.png"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Image saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Image export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportChartAsPDF() {
        try {
            val bitmap = getChartBitmapWithAxisLabels()
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val fileName = "Exposure_Time_${System.currentTimeMillis()}.pdf"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(this, "PDF saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getChartBitmapWithAxisLabels(): Bitmap {
        val chartBitmap =
            Bitmap.createBitmap(barChart.width, barChart.height, Bitmap.Config.ARGB_8888)
        val chartCanvas = Canvas(chartBitmap)
        barChart.draw(chartCanvas)

        val leftPadding = 130
        val topPadding = 120
        val bottomPadding = 220
        val rightPadding = 100

        val totalWidth = chartBitmap.width + leftPadding + rightPadding
        val totalHeight = chartBitmap.height + topPadding + bottomPadding

        val outputBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 50f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 35f
            isAntiAlias = true
        }

        canvas.drawText(tvReportInfo.text.toString(), leftPadding.toFloat(), 60f, titlePaint)

        canvas.drawBitmap(chartBitmap, leftPadding.toFloat(), topPadding.toFloat(), null)

        // Y-axis label
        val yLabel = "Time (min)"
        val yLabelX = 40f
        val yLabelY = (topPadding + chartBitmap.height / 2).toFloat()
        canvas.save()
        canvas.rotate(-90f, yLabelX, yLabelY)
        canvas.drawText(yLabel, yLabelX, yLabelY, labelPaint)
        canvas.restore()

        // X-axis label
        val xLabel = xAxisLabel.text.toString()
        val xLabelWidth = labelPaint.measureText(xLabel)
        val xPos = (totalWidth - xLabelWidth) / 2
        val yPos = (topPadding + chartBitmap.height + 100).toFloat()
        canvas.drawText(xLabel, xPos, yPos, labelPaint)

        return outputBitmap
    }

    private fun applyWeeklyMode() {
        // Hide From/To UI (auto)
        dateRangeLayout.visibility = View.GONE
        btnApplyRange.visibility = View.GONE

        val cal = Calendar.getInstance()
        val end = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val start = cal.time

        val displayFrom = displayFormat.format(start)
        val displayTo = displayFormat.format(end)
        val apiFrom = apiFormat.format(start)
        val apiTo = apiFormat.format(end)

        etFromDateInline.setText(displayFrom)
        etToDateInline.setText(displayTo)

        tvSelectedRange.visibility = View.GONE
        tvSelectedRange.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tvSelectedRange.gravity = Gravity.CENTER
        tvSelectedRange.text = "Past Week\n($displayFrom to $displayTo)"

        tvReportInfo.visibility = View.VISIBLE
        tvReportInfo.text = Html.fromHtml(
            "Report generated from <b>$displayFrom</b> to <b>$displayTo</b>.",
            Html.FROM_HTML_MODE_LEGACY
        )


        fetchStatistics(
            userId = userId,
            mode = "range",
            date = "$apiFrom|$apiTo",
            xLabelText = "$displayFrom to $displayTo",
            fromDateStr = apiFrom,
            toDateStr = apiTo
        )
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val displayDate = String.format("%02d-%02d-%04d", day, month + 1, year)
                val apiDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                tvReportInfo.text = Html.fromHtml(
                    "Report generated for <b>$displayDate</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )
                fetchStatistics(
                    userId = userId,
                    mode = "date",
                    date = apiDate,
                    xLabelText = displayDate
                )
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showInlineDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = String.format("%02d-%02d-%04d", day, month + 1, year)
                target.setText(selected)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    private fun hideRangeInputs() {
        dateRangeLayout.visibility = View.GONE
        btnApplyRange.visibility = View.GONE
    }

    private fun showRangeInputs() {
        dateRangeLayout.visibility = View.VISIBLE
        btnApplyRange.visibility = View.VISIBLE
    }

    private fun setAllButtonsToBlue() {
        btnPickDate.setBackgroundColor(holoBlue)
        btnWeekly.setBackgroundColor(holoBlue)
        btnPickRange.setBackgroundColor(holoBlue)
        btnApplyRange.setBackgroundColor(holoBlue)
    }

    private fun highlightButton(selected: Button) {
        setAllButtonsToBlue()
        selected.setBackgroundColor(holoGreen)
    }

    private fun fetchStatistics(
        userId: String,
        mode: String,
        date: String? = null,
        xLabelText: String,
        fromDateStr: String? = null,
        toDateStr: String? = null
    ) {
        Log.d("STAT_FETCH", "userId=$userId mode=$mode date=$date childName=$childName")

        RetrofitClient.apiService.getStatistics(
            userId = userId,
            mode = mode,
            date = date,
            childName = childName
        ).enqueue(object : Callback<StatisticsResponse> {
            override fun onResponse(
                call: Call<StatisticsResponse>,
                response: Response<StatisticsResponse>
            ) {
                Log.d("STAT_FETCH", "Response code=${response.code()}")
                if (!response.isSuccessful) {
                    Toast.makeText(
                        this@StatisticsCardActivity,
                        "Server error: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    barChart.clear(); barChart.invalidate()
                    return
                }

                val body = response.body()
                if (body == null) {
                    Toast.makeText(
                        this@StatisticsCardActivity,
                        "Empty response",
                        Toast.LENGTH_SHORT
                    ).show()
                    barChart.clear(); barChart.invalidate()
                    return
                }

                val harmMap = LinkedHashMap<String, Float>()
                body.raw?.forEach { (k, v) ->
                    val minutes = when (v) {
                        is Float -> v / 60f
                        is Double -> v.toFloat() / 60f
                        is Int -> v.toFloat() / 60f
                        else -> try {
                            v.toString().toFloat() / 60f
                        } catch (_: Exception) {
                            0f
                        }
                    }
                    harmMap[k] = minutes
                }

                harmfulTimeByDate.clear()

                val sdf = apiFormat

                when {
                    mode == "range" && fromDateStr != null && toDateStr != null -> {
                        try {
                            val fromDate = sdf.parse(fromDateStr)
                            val toDate = sdf.parse(toDateStr)
                            if (fromDate != null && toDate != null) {
                                val cal = Calendar.getInstance()
                                cal.time = fromDate
                                while (!cal.time.after(toDate)) {
                                    val dateStr = sdf.format(cal.time)
                                    val value = harmMap[dateStr] ?: 0f
                                    harmfulTimeByDate[dateStr] = value
                                    cal.add(Calendar.DATE, 1)
                                }
                            }
                        } catch (e: ParseException) {
                            Toast.makeText(
                                this@StatisticsCardActivity,
                                "Date parse error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    (mode == "date") && date != null -> {
                        val value = harmMap[date] ?: 0f
                        harmfulTimeByDate[date] = value
                    }

                    else -> {
                        harmfulTimeByDate.putAll(harmMap)
                    }
                }

                showBarChart(xLabelText)
            }

            override fun onFailure(call: Call<StatisticsResponse>, t: Throwable) {
                Toast.makeText(
                    this@StatisticsCardActivity,
                    "Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("STAT_ERROR", "Failure", t)
                barChart.clear(); barChart.invalidate()
            }
        })
    }

    private fun showBarChart(xLabelText: String) {
        if (harmfulTimeByDate.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            Toast.makeText(this, "No data to show", Toast.LENGTH_SHORT).show()
            xAxisLabel.text = xLabelText
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        val inputFormat = apiFormat // "yyyy-MM-dd"
        val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())  // "13 Jul"

        val sortedMap = harmfulTimeByDate.toSortedMap(compareBy {
            try {
                inputFormat.parse(it)
            } catch (e: Exception) {
                null
            }
        })

        var index = 0f
        for ((date, value) in sortedMap) {
            val label = try {
                outputFormat.format(inputFormat.parse(date)!!)
            } catch (e: Exception) {
                date
            }

            entries.add(BarEntry(index, value))
            labels.add(label)
            index += 1f
        }

        val dataSet = BarDataSet(entries, "Time (min)").apply {
            color = Color.RED
            valueTextSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "%.1f".format(value)
            }
        }

        val barData = BarData(dataSet).apply { barWidth = 0.6f }

        barChart.data = barData
        barChart.setFitBars(true)

        val barWidthDp = 60f
        val minVisibleBars = 4
        val actualBars = labels.size
        val totalBars = max(minVisibleBars, actualBars)
        val chartWidthPx = (totalBars * barWidthDp * resources.displayMetrics.density).toInt()
        barChart.layoutParams.width = chartWidthPx
        barChart.requestLayout()

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f
            isGranularityEnabled = true
            setDrawGridLines(false)
            textSize = 12f
            labelRotationAngle = 0f
            setLabelCount(labels.size, false)
        }

        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.invalidate()

        xAxisLabel.text = xLabelText

        barChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val xIndex = e?.x?.toInt() ?: return
                val dateKey = sortedMap.keys.elementAtOrNull(xIndex) ?: return

                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                val labelFormatted = try {
                    outputFormat.format(inputFormat.parse(dateKey)!!)
                } catch (e: Exception) {
                    dateKey
                }

                val intent = Intent(this@StatisticsCardActivity, DrillDownActivity::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("childName", childName)
                    putExtra("mode", "date")
                    putExtra("label", labelFormatted)
                    putExtra("dateParam", dateKey)
                    putExtra("drillType", "Statistics")
                }
                startActivity(intent)
            }

            override fun onNothingSelected() { }
        })

    }
    companion object {

        fun exportChartBitmap(
            context: Context,
            userId: String,
            mode: String,
            dateParam: String,
            childName: String,
            onBitmapReady: (Bitmap?) -> Unit
        ) {
            val api = RetrofitClient.apiService
            api.getStatistics(userId, mode, dateParam, childName)
                .enqueue(object : Callback<StatisticsResponse> {
                    override fun onResponse(
                        call: Call<StatisticsResponse>,
                        response: Response<StatisticsResponse>
                    ) {
                        if (response.isSuccessful && response.body() != null) {
                            val rawData = response.body()!!.raw
                            val entries = ArrayList<BarEntry>()
                            val labels = ArrayList<String>()

                            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

                            val dateList = mutableListOf<String>()

                            if (mode == "range" && dateParam.contains("|")) {
                                val parts = dateParam.split("|")
                                if (parts.size == 2) {
                                    try {
                                        val startDate = inputFormat.parse(parts[0])
                                        val endDate = inputFormat.parse(parts[1])
                                        if (startDate != null && endDate != null) {
                                            val cal = Calendar.getInstance()
                                            cal.time = startDate
                                            while (!cal.time.after(endDate)) {
                                                dateList.add(inputFormat.format(cal.time))
                                                cal.add(Calendar.DATE, 1)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ChartExport", "Date parse error: ${e.message}")
                                        rawData?.keys?.let { dateList.addAll(it.sorted()) }
                                    }
                                }
                            } else {
                                rawData?.keys?.let { dateList.addAll(it.sorted()) }
                            }

                            var index = 0f
                            for (dateStr in dateList) {
                                val rawValue = rawData?.get(dateStr)
                                val value = (rawValue as? Number)?.toFloat() ?: 0f

                                val label = try {
                                    outputFormat.format(inputFormat.parse(dateStr)!!)
                                } catch (e: Exception) {
                                    dateStr
                                }

                                labels.add(label)
                                entries.add(BarEntry(index++, value))
                            }

                            val barDataSet = BarDataSet(entries, "Harmful Time").apply {
                                color = Color.RED
                                valueTextColor = Color.BLACK
                                valueTextSize = 12f
                            }

                            val barData = BarData(barDataSet)

                            val chart = BarChart(context).apply {
                                this.data = barData
                                description.isEnabled = false
                                setDrawGridBackground(false)
                                setFitBars(true)
                                setDrawValueAboveBar(true)
                                legend.isEnabled = false
                                axisRight.isEnabled = false

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    valueFormatter = IndexAxisValueFormatter(labels)
                                    granularity = 1f
                                    isGranularityEnabled = true
                                    setDrawGridLines(false)
                                    textSize = 12f
                                    labelRotationAngle = 0f
                                    setLabelCount(labels.size, false)
                                }

                                axisLeft.textSize = 12f
                                axisLeft.axisMinimum = 0f
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
                                    val bitmap = Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    chart.draw(canvas)

                                    Log.d("ChartExport", "Chart export success")
                                    onBitmapReady(bitmap)
                                } catch (e: Exception) {
                                    Log.e("ChartExport", "Bitmap creation failed", e)
                                    onBitmapReady(null)
                                }
                            }, 300)
                        } else {
                            Log.e("ChartExport", "Failed response: ${response.code()}")
                            onBitmapReady(null)
                        }
                    }

                    override fun onFailure(call: Call<StatisticsResponse>, t: Throwable) {
                        Log.e("ChartExport", "API failure: ${t.message}")
                        onBitmapReady(null)
                    }
                })
        }
    }
}