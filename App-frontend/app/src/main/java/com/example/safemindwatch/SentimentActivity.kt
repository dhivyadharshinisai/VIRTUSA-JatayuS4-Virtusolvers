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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.example.safemindwatch.api.RetrofitClient
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class SentimentActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var btnApplyRange: Button
    private lateinit var dateRangeLayout: LinearLayout
    private lateinit var tvReportInfo: TextView
    private lateinit var btnWeekly: Button
    private lateinit var btnPickDate: Button
    private lateinit var btnPickRange: Button
    private lateinit var btnGenerate: Button

    private lateinit var userId: String
    private lateinit var childName: String

    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var lastMode: String = "range"
    private var lastDateParam: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sentiment)

        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: "Your Child"

        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID missing. Please login again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        barChart = findViewById(R.id.barChart)
        etFromDate = findViewById(R.id.etFromDateInline)
        etToDate = findViewById(R.id.etToDateInline)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)
        tvReportInfo = findViewById(R.id.tvReportInfo)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnPickRange = findViewById(R.id.btnPickRange)
        btnGenerate = findViewById(R.id.btnGenerate)

        setupListeners()
        btnWeekly.performClick()
    }

    private fun setupListeners() {
        btnWeekly.setOnClickListener {
            highlightSelectedButton(btnWeekly)
            dateRangeLayout.visibility = LinearLayout.GONE
            tvReportInfo.visibility = TextView.VISIBLE

            val calEnd = Calendar.getInstance()
            val endDate = calEnd.time
            val calStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            val startDate = calStart.time

            etFromDate.setText(displayDateFormat.format(startDate))
            etToDate.setText(displayDateFormat.format(endDate))

            val fromDisplay = etFromDate.text.toString()
            val toDisplay = etToDate.text.toString()

            tvReportInfo.text = Html.fromHtml(
                "Report generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                Html.FROM_HTML_MODE_LEGACY
            )

            val fromApi = apiDateFormat.format(startDate)
            val toApi = apiDateFormat.format(endDate)
            lastMode = "range"
            lastDateParam = "$fromApi|$toApi"
            fetchSentimentData(lastMode, lastDateParam)
        }

        btnApplyRange.setOnClickListener {
            if (dateRangeLayout.visibility != LinearLayout.VISIBLE) {
                Toast.makeText(this, "Please select Pick Range first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fromDisplay = etFromDate.text.toString().trim()
            val toDisplay = etToDate.text.toString().trim()
            if (fromDisplay.isEmpty() || toDisplay.isEmpty()) {
                Toast.makeText(this, "Please enter both From and To dates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fromApi = safeDisplayToApi(fromDisplay)
            val toApi = safeDisplayToApi(toDisplay)
            if (fromApi == null || toApi == null) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tvReportInfo.text = Html.fromHtml(
                "Report Generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
            lastMode = "range"
            lastDateParam = "$fromApi|$toApi"
            fetchSentimentData(lastMode, lastDateParam)
        }

        btnPickDate.setOnClickListener {
            highlightSelectedButton(btnPickDate)
            dateRangeLayout.visibility = LinearLayout.GONE
            tvReportInfo.visibility = TextView.VISIBLE
            showSingleDatePicker()
        }

        btnPickRange.setOnClickListener {
            highlightSelectedButton(btnPickRange)
            setDefaultPickRange()
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

        etFromDate.setOnClickListener { showInlineDatePicker(etFromDate) }
        etToDate.setOnClickListener { showInlineDatePicker(etToDate) }
    }

    private fun showSingleDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val apiDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            val displayDate = safeApiToDisplay(apiDate) ?: apiDate
            tvReportInfo.text = Html.fromHtml(
                "Report Generated for <b>$displayDate</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
            lastMode = "date"
            lastDateParam = apiDate
            fetchSentimentData(lastMode, lastDateParam)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showInlineDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val apiDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            val displayDate = safeApiToDisplay(apiDate) ?: apiDate
            target.setText(displayDate)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setDefaultPickRange() {
        val calStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
        val calEnd = Calendar.getInstance()
        val from = calStart.time
        val to = calEnd.time

        etFromDate.setText(displayDateFormat.format(from))
        etToDate.setText(displayDateFormat.format(to))

        etFromDate.isEnabled = true
        etFromDate.isClickable = true
        etToDate.isEnabled = true
        etToDate.isClickable = true

        btnApplyRange.visibility = Button.VISIBLE
        dateRangeLayout.visibility = LinearLayout.VISIBLE

        val fromApi = apiDateFormat.format(from)
        val toApi = apiDateFormat.format(to)

        lastMode = "range"
        lastDateParam = "$fromApi|$toApi"
        fetchSentimentData(lastMode, lastDateParam)

        tvReportInfo.text = Html.fromHtml(
            "Report generated from <b>${etFromDate.text}</b> to <b>${etToDate.text}</b>",
            Html.FROM_HTML_MODE_LEGACY
        )
    }

    private fun safeDisplayToApi(displayDate: String): String? = try {
        apiDateFormat.format(displayDateFormat.parse(displayDate)!!)
    } catch (e: Exception) {
        null
    }

    private fun safeApiToDisplay(apiDate: String): String? = try {
        displayDateFormat.format(apiDateFormat.parse(apiDate)!!)
    } catch (e: Exception) {
        null
    }

    private fun fetchSentimentData(mode: String, dateParam: String?) {
        if (dateParam == null) {
            Toast.makeText(this, "Date parameter missing", Toast.LENGTH_SHORT).show()
            return
        }
        RetrofitClient.apiService.getSentimentData(userId, mode, dateParam, childName)
            .enqueue(object : Callback<SentimentResponse> {
                override fun onResponse(call: Call<SentimentResponse>, response: Response<SentimentResponse>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@SentimentActivity, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                        barChart.clear()
                        return
                    }
                    val body = response.body()
                    if (body?.success != true) {
                        Toast.makeText(this@SentimentActivity, "No data", Toast.LENGTH_SHORT).show()
                        barChart.clear()
                        return
                    }
                    val dataList = body.data ?: emptyList()
                    val (fromApi, toApi) = when {
                        mode == "date" -> dateParam to dateParam
                        mode == "range" && dateParam.contains("|") -> {
                            val parts = dateParam.split("|")
                            parts[0] to parts[1]
                        }
                        else -> {
                            val today = apiDateFormat.format(Date())
                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                            apiDateFormat.format(cal.time) to today
                        }
                    }
                    val expanded = fillMissingDates(fromApi, toApi, dataList)
                    showChart(expanded)
                }
                override fun onFailure(call: Call<SentimentResponse>, t: Throwable) {
                    Toast.makeText(this@SentimentActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                    Log.e("SENT_FETCH", "Failure", t)
                }
            })
    }

    private fun fillMissingDates(fromApi: String, toApi: String, data: List<SentimentData>): List<SentimentData> {
        val formatter = DateTimeFormatter.ISO_DATE
        val from = try { LocalDate.parse(fromApi, formatter) } catch (_: Exception) { return data }
        val to = try { LocalDate.parse(toApi, formatter) } catch (_: Exception) { return data }
        val dataMap = data.associateBy { it.date }
        val full = mutableListOf<SentimentData>()
        var cur = from
        while (!cur.isAfter(to)) {
            val dateStr = cur.format(formatter)
            val entry = dataMap[dateStr]
            full.add(SentimentData(dateStr, entry?.averageSentimentScore ?: 0f))
            cur = cur.plusDays(1)
        }
        return full
    }

    private fun highlightSelectedButton(selected: Button) {
        val buttons = listOf(btnWeekly, btnPickDate, btnPickRange)
        buttons.forEach {
            it.setBackgroundColor(Color.parseColor("#33B5E5"))
            it.setTextColor(Color.WHITE)
        }
        selected.setBackgroundColor(Color.parseColor("#99CC00"))
        selected.setTextColor(Color.WHITE)
    }

    private fun showChart(data: List<SentimentData>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val colors = ArrayList<Int>()
        val inputFormatter = DateTimeFormatter.ISO_DATE
        val outputFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

        data.forEachIndexed { index, item ->
            val sentimentValue = item.averageSentimentScore ?: 0f
            val sentimentFlipped = sentimentValue * -1
            entries.add(BarEntry(index.toFloat(), sentimentFlipped))
            val label = try {
                LocalDate.parse(item.date, inputFormatter).format(outputFormatter)
            } catch (ex: Exception) {
                item.date
            }
            labels.add(label)
            val color = when {
                sentimentValue < -1.40f -> Color.parseColor("#d00000")
                sentimentValue <= -0.60f -> Color.parseColor("#f48c06")
                else -> Color.parseColor("#ffdd00")
            }
            colors.add(color)
        }

        val dataSet = BarDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.BLACK
            setDrawValues(true)
            isHighlightEnabled = true // <-- the bar must be highlightable!
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    val originalValue = barEntry?.y?.times(-1) ?: 0f
                    return String.format("%.2f", originalValue)
                }
            }
        }
        val barData = BarData(dataSet).apply { barWidth = 0.4f }
        barChart.data = barData

        barChart.setTouchEnabled(true)
        barChart.isHighlightPerTapEnabled = true
        barChart.isDoubleTapToZoomEnabled = false
        barChart.isDragEnabled = false
        barChart.setScaleEnabled(false)
        barChart.isClickable = true
        barChart.isFocusable = true

        // Chart width for all bars
        val screenWidth = resources.displayMetrics.widthPixels
        val barCountToFit = 5
        val barSpacingFactor = 1.2f
        val barWidthPx = screenWidth / barCountToFit / barSpacingFactor
        val totalChartWidthPx = (labels.size * barWidthPx).toInt().coerceAtLeast(screenWidth)
        barChart.layoutParams.width = totalChartWidthPx
        barChart.requestLayout()

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = 0f
        xAxis.textSize = 12f
        xAxis.setLabelCount(labels.size, false)
        xAxis.axisMinimum = -0.5f
        xAxis.axisMaximum = entries.size - 0.5f

        barChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = (entries.maxOfOrNull { it.y } ?: 1f) + 0.2f
            isInverted = false
            setDrawGridLines(true)
            textSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("-%.2f", value)
                }
            }
        }

        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setFitBars(true)
        barChart.invalidate()
        barChart.requestFocus()

        // Drill-down with logs:
        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null || h == null) {
                    Log.d("DRILL_DEBUG", "onValueSelected called with null Entry or Highlight")
                    return
                }
                val xIndex = e.x.toInt()
                Log.d("DRILL_DEBUG", "Bar tapped at xIndex = $xIndex, totalBars=${data.size}")

                if (xIndex < 0 || xIndex >= data.size) {
                    Log.w("DRILL_DEBUG", "Tapped index $xIndex out of data bounds (size=${data.size})")
                    return
                }

                val dateKey = data[xIndex].date
                Log.d("DRILL_DEBUG", "Tapped bar corresponds to date: $dateKey")

                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                val labelFormatted = try {
                    outputFormat.format(inputFormat.parse(dateKey)!!)
                } catch (ex: Exception) {
                    dateKey
                }
                Log.d("DRILL_DEBUG", "Formatted label for drill-down: $labelFormatted")

                val intent = Intent(this@SentimentActivity, DrillDownActivity::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("childName", childName)
                    putExtra("mode", "date")
                    putExtra("label", labelFormatted)
                    putExtra("dateParam", dateKey)
                    putExtra("drillType", "Sentiment")
                }
                Log.d("DRILL_DEBUG", "Starting DrillDownActivity with date: $dateKey")
                startActivity(intent)
            }

            override fun onNothingSelected() {
                Log.d("DRILL_DEBUG", "onNothingSelected called - No bar selected")
            }
        })
    }

private fun exportChartAsImage() {
        try {
            val bitmap = Bitmap.createBitmap(barChart.width, barChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            barChart.draw(canvas)
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir == null) {
                Toast.makeText(this, "Export directory not available", Toast.LENGTH_SHORT).show()
                return
            }
            val file = File(dir, "sentiment_chart_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Toast.makeText(this, "Image saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportChartAsPDF() {
        try {
            val bitmap = Bitmap.createBitmap(barChart.width, barChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            barChart.draw(canvas)

            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height + 200, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val pdfCanvas = page.canvas

            pdfCanvas.drawColor(Color.WHITE)
            pdfCanvas.drawBitmap(bitmap, 0f, 100f, null)

            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isFakeBoldText = true
            }
            pdfCanvas.drawText(tvReportInfo.text.toString(), 40f, 60f, paint)

            pdfDoc.finishPage(page)

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir == null) {
                Toast.makeText(this, "Export directory not available", Toast.LENGTH_SHORT).show()
                return
            }
            val file = File(dir, "sentiment_chart_${System.currentTimeMillis()}.pdf")
            pdfDoc.writeTo(FileOutputStream(file))
            pdfDoc.close()

            Toast.makeText(this, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

            api.getSentimentData(userId, mode, dateParam, childName)
                .enqueue(object : Callback<SentimentResponse> {
                    override fun onResponse(
                        call: Call<SentimentResponse>,
                        response: Response<SentimentResponse>
                    ) {
                        if (!response.isSuccessful || response.body() == null) {
                            onBitmapReady(null)
                            return
                        }

                        val rawList = response.body()?.data ?: emptyList()

                        val (fromApi, toApi) = when {
                            mode == "date" -> dateParam to dateParam
                            mode == "range" && dateParam.contains("|") -> {
                                val parts = dateParam.split("|")
                                parts[0] to parts[1]
                            }
                            else -> {
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                                val start = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                start to today
                            }
                        }

                        val inputFormatter = DateTimeFormatter.ISO_DATE
                        val outputFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
                        val fromDate = try { LocalDate.parse(fromApi, inputFormatter) } catch (e: Exception) { LocalDate.now() }
                        val toDate = try { LocalDate.parse(toApi, inputFormatter) } catch (e: Exception) { LocalDate.now() }

                        val sentimentMap = rawList.associateBy { it.date }
                        val fullList = mutableListOf<SentimentData>()
                        var curDate = fromDate
                        while (!curDate.isAfter(toDate)) {
                            val dateStr = curDate.toString()
                            val score = sentimentMap[dateStr]?.averageSentimentScore ?: 0f
                            fullList.add(SentimentData(dateStr, score))
                            curDate = curDate.plusDays(1)
                        }

                        val entries = ArrayList<BarEntry>()
                        val labels = ArrayList<String>()
                        val colors = ArrayList<Int>()

                        fullList.forEachIndexed { index, item ->
                            val flipped = (item.averageSentimentScore ?: 0f) * -1f
                            entries.add(BarEntry(index.toFloat(), flipped))
                            labels.add(LocalDate.parse(item.date, inputFormatter).format(outputFormatter))

                            val color = when {
                                item.averageSentimentScore != null && item.averageSentimentScore < -1.40f -> Color.parseColor("#d00000")
                                item.averageSentimentScore != null && item.averageSentimentScore <= -0.60f -> Color.parseColor("#f48c06")
                                else -> Color.parseColor("#ffdd00")
                            }
                            colors.add(color)
                        }

                        val dataSet = BarDataSet(entries, "").apply {
                            this.colors = colors
                            valueTextSize = 12f
                            valueTextColor = Color.BLACK
                            setDrawValues(true)
                            isHighlightEnabled = false
                            valueFormatter = object : ValueFormatter() {
                                override fun getBarLabel(barEntry: BarEntry?): String {
                                    val original = barEntry?.y?.times(-1) ?: 0f
                                    return String.format("%.2f", original)
                                }
                            }
                        }
                        val barData = BarData(dataSet)
                        val chart = BarChart(context).apply {
                            data = barData
                            description.isEnabled = false
                            setDrawGridBackground(false)
                            setFitBars(true)
                            setDrawValueAboveBar(true)
                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                valueFormatter = IndexAxisValueFormatter(labels)
                                granularity = 1f
                                setDrawGridLines(false)
                                textSize = 12f
                                labelRotationAngle = 0f
                            }
                            axisLeft.apply {
                                axisMinimum = 0f
                                axisMaximum = (entries.maxOfOrNull { it.y } ?: 1f) + 0.2f
                                isInverted = false
                                setDrawGridLines(true)
                                textSize = 12f
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return String.format("-%.2f", value)
                                    }
                                }
                            }
                            axisRight.isEnabled = false
                            legend.isEnabled = false
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
                                onBitmapReady(bitmap)
                            } catch (_: Exception) {
                                onBitmapReady(null)
                            }
                        }, 300)
                    }
                    override fun onFailure(call: Call<SentimentResponse>, t: Throwable) {
                        onBitmapReady(null)
                    }
                })
        }
    }
}