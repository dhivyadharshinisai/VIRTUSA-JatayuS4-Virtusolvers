package com.example.safemindwatch

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.example.safemindwatch.api.RetrofitClient
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mental_Health_Prediction : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var btnPickDate: Button
    private lateinit var btnWeekly: Button
    private lateinit var btnPickRange: Button
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var btnApplyRange: Button
    private lateinit var dateRangeLayout: LinearLayout
    private lateinit var tvReportInfo: TextView
    private lateinit var btnGenerate: Button

    private lateinit var userId: String
    private lateinit var childName: String

    private val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    private var lastMode: String = "weekly"
    private var lastDateParam: String? = null

    private var lastSelectedIndex: Int = -1
    private var lastSelectTime: Long = 0L

    private var cachedCounts: MentalHealthCounts? = null
    private var cachedTotal: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_predictioncount)


        pieChart = findViewById(R.id.pieChart)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnPickRange = findViewById(R.id.btnPickRange)
        etFromDate = findViewById(R.id.etFromDateInline)
        etToDate = findViewById(R.id.etToDateInline)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)
        tvReportInfo = findViewById(R.id.tvReportInfo)
        btnGenerate = findViewById(R.id.btnGenerate)

        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: "Your Child"

        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        highlightSelectedButton(btnWeekly)

        btnGenerate.setOnClickListener {
            val popupMenu = PopupMenu(this, btnGenerate)
            popupMenu.menuInflater.inflate(R.menu.menu_export, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_export_image -> exportPieChartAsImage()
                    R.id.menu_export_pdf -> exportPieChartAsPDF()
                }
                true
            }
            popupMenu.show()
        }

        btnPickDate.setOnClickListener {
            highlightSelectedButton(btnPickDate)
            dateRangeLayout.visibility = View.GONE
            btnApplyRange.visibility = View.GONE

            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    val selectedDate = calendar.time
                    val displayDate = displayFormat.format(selectedDate)
                    val backendDate = backendFormat.format(selectedDate)

                    lastMode = "date"
                    lastDateParam = backendDate

                    fetchPredictionData(userId, lastMode, lastDateParam)
                    tvReportInfo.text = Html.fromHtml(
                        "Report generated for <b>$displayDate</b>",
                        Html.FROM_HTML_MODE_LEGACY
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnWeekly.setOnClickListener {
            highlightSelectedButton(btnWeekly)
            dateRangeLayout.visibility = View.GONE
            btnApplyRange.visibility = View.GONE

            val cal = Calendar.getInstance()
            val endDate = cal.time
            cal.add(Calendar.DAY_OF_YEAR, -6)
            val startDate = cal.time

            val fromDisplay = displayFormat.format(startDate)
            val toDisplay = displayFormat.format(endDate)

            etFromDate.setText(fromDisplay)
            etFromDate.tag = backendFormat.format(startDate)
            etFromDate.isEnabled = false

            etToDate.setText(toDisplay)
            etToDate.tag = backendFormat.format(endDate)
            etToDate.isEnabled = false

            lastMode = "weekly"
            lastDateParam = null

            fetchPredictionData(userId, lastMode, null)
            tvReportInfo.text = Html.fromHtml(
                "Report generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
        }

        btnPickRange.setOnClickListener {
            highlightSelectedButton(btnPickRange)
            dateRangeLayout.visibility = View.VISIBLE
            btnApplyRange.visibility = View.VISIBLE

            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            val startDate = cal.time
            val endDate = Calendar.getInstance().time

            val fromBackend = backendFormat.format(startDate)
            val toBackend = backendFormat.format(endDate)
            val fromDisplay = displayFormat.format(startDate)
            val toDisplay = displayFormat.format(endDate)

            etFromDate.setText(fromDisplay)
            etFromDate.tag = fromBackend
            etFromDate.isEnabled = true

            etToDate.setText(toDisplay)
            etToDate.tag = toBackend
            etToDate.isEnabled = true

            lastMode = "range"
            lastDateParam = "$fromBackend|$toBackend"

            fetchPredictionData(userId, lastMode, lastDateParam)
            tvReportInfo.text = Html.fromHtml(
                "Report generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
        }

        etFromDate.setOnClickListener {
            if (etFromDate.isEnabled) showDatePicker(etFromDate)
        }
        etToDate.setOnClickListener {
            if (etToDate.isEnabled) showDatePicker(etToDate)
        }

        btnApplyRange.setOnClickListener {
            val from = etFromDate.tag?.toString() ?: ""
            val to = etToDate.tag?.toString() ?: ""
            if (from.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && to.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                lastMode = "range"
                lastDateParam = "$from|$to"
                fetchPredictionData(userId, lastMode, lastDateParam)
                tvReportInfo.text = Html.fromHtml(
                    "Report generated from <b>${etFromDate.text}</b> to <b>${etToDate.text}</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )
            } else {
                Toast.makeText(this, "Invalid dates", Toast.LENGTH_SHORT).show()
            }
        }

        btnWeekly.performClick()
    }

    private fun highlightSelectedButton(selected: Button) {
        val buttons = listOf(btnPickDate, btnWeekly, btnPickRange)
        buttons.forEach {
            it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            it.setTextColor(Color.WHITE)
        }
        selected.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun showDatePicker(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply { set(year, month, day) }
                val display = displayFormat.format(selected.time)
                val backend = backendFormat.format(selected.time)
                target.setText(display)
                target.tag = backend
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun fetchPredictionData(userId: String, mode: String, date: String? = null) {
        RetrofitClient.apiService.getMentalHealthStats(
            userId = userId,
            mode = mode,
            date = date,
            childName = childName
        ).enqueue(object : Callback<MentalHealthStatsResponse> {
            override fun onResponse(
                call: Call<MentalHealthStatsResponse>,
                response: Response<MentalHealthStatsResponse>
            ) {
                if (!response.isSuccessful) {
                    Toast.makeText(this@Mental_Health_Prediction, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    pieChart.clear()
                    return
                }
                val body = response.body()
                if (body?.success != true) {
                    Toast.makeText(this@Mental_Health_Prediction, "No data", Toast.LENGTH_SHORT).show()
                    pieChart.clear()
                    return
                }
                cachedCounts = body.data
                cachedTotal = body.count
                showDonutChart(body.data)
            }

            override fun onFailure(call: Call<MentalHealthStatsResponse>, t: Throwable) {
                Toast.makeText(this@Mental_Health_Prediction, "Network error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDonutChart(counts: MentalHealthCounts?) {
        val c = counts ?: MentalHealthCounts()
        val categories = listOf("Anxiety", "Isolation", "Depression", "Suicide", "No Risk")
        val values = listOf(c.Anxiety, c.Isolation, c.Depression, c.Suicide, c.No_Risk)

        val colors = listOf(
            ContextCompat.getColor(this, R.color.anxiety_orange),
            ContextCompat.getColor(this, R.color.isolation_gray_blue),
            ContextCompat.getColor(this, R.color.depression_light_blue),
            ContextCompat.getColor(this, R.color.suicide_red),
            ContextCompat.getColor(this, R.color.no_risk_green)
        )

        val entries = mutableListOf<PieEntry>()
        val sliceColors = mutableListOf<Int>()
        for (i in categories.indices) {
            if (values[i] > 0) {
                entries.add(PieEntry(values[i].toFloat(), categories[i]))
                sliceColors.add(colors[i])
            }
        }

        val dataSet = PieDataSet(entries, "").apply {
            setColors(sliceColors)
            valueTextSize = 16f
            valueTextColor = Color.BLACK
            sliceSpace = 3f
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLinePart1Length = 0.35f
            valueLinePart2Length = 0.15f
            valueLineWidth = 1f
            valueLineColor = Color.BLACK
            selectionShift = 10f
        }

        val pieData = PieData(dataSet).apply {
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            })
        }

        val totalSearch = c.Total.takeIf { it >= 0 }
            ?: values.sum()

        pieChart.apply {
            data = pieData
            setUsePercentValues(false)
            isDrawHoleEnabled = true
            holeRadius = 55f
            transparentCircleRadius = 60f
            setDrawEntryLabels(false)
            setExtraOffsets(10f, 10f, 10f, 10f)
            description = Description().apply { text = "" }
            legend.isEnabled = false
            centerText = "$totalSearch\nTotal"
            setCenterTextSize(20f)
            setCenterTextColor(Color.BLACK)
            setTouchEnabled(true)
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            invalidate()
            animateY(1000)
        }

        attachDoubleSelectListener()
    }

    private fun attachDoubleSelectListener() {
        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(
                e: com.github.mikephil.charting.data.Entry?,
                h: Highlight?
            ) {
                h ?: return
                val pieEntry = e as? PieEntry ?: return
                val idx = h.x.toInt()
                openDrillDown(pieEntry, idx)
                lastSelectedIndex = idx
                lastSelectTime = SystemClock.elapsedRealtime()
            }

            override fun onNothingSelected() {}
        })
    }

    private fun openDrillDown(entry: PieEntry, index: Int) {
        val intent = Intent(this, DrillDownActivity::class.java).apply {
            putExtra("label", entry.label ?: "Unknown")
            putExtra("value", entry.value)
            putExtra("index", index)
            putExtra("mode", lastMode)
            putExtra("dateParam", lastDateParam)
            putExtra("userId", userId)
            putExtra("childName", childName)
            putExtra("drillType", "mentalHealth")
        }
        startActivity(intent)
    }

    private fun exportPieChartAsImage() {
        try {
            val bitmap = Bitmap.createBitmap(pieChart.width, pieChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            pieChart.draw(canvas)

            val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "mental_health_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Toast.makeText(this, "Image saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export image: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun exportPieChartAsPDF() {
        try {
            val bitmap = Bitmap.createBitmap(pieChart.width, pieChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            pieChart.draw(canvas)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height + 200, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val pdfCanvas = page.canvas

            pdfCanvas.drawColor(Color.WHITE)
            pdfCanvas.drawBitmap(bitmap, 0f, 100f, null)

            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                textSize = 36f
                isFakeBoldText = true
            }
            pdfCanvas.drawText(tvReportInfo.text.toString(), 40f, 60f, paint)

            pdfDocument.finishPage(page)

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "mental_health_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(this, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export PDF", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    companion object {
        fun exportChartBitmap(
            context: Context,
            userId: String,
            mode: String,
            dateParam: String?,
            childName: String,
            callback: (Bitmap?) -> Unit
        ) {
            val density = context.resources.displayMetrics.density
            val chartSizeDp = 870f
            val chartSizePx = (chartSizeDp * density).toInt()

            val rootView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(50, 50, 50, 50)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }

            val pieChart = PieChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(chartSizePx, chartSizePx).apply {
                    gravity = Gravity.CENTER
                }
            }

            rootView.addView(pieChart)

            val retrofitCall = RetrofitClient.apiService.getMentalHealthStats(
                userId = userId,
                mode = mode,
                date = dateParam,
                childName = childName
            )

            retrofitCall.enqueue(object : Callback<MentalHealthStatsResponse> {
                override fun onResponse(
                    call: Call<MentalHealthStatsResponse>,
                    response: Response<MentalHealthStatsResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val counts = response.body()?.data ?: MentalHealthCounts()
                        val categories = listOf("Anxiety", "Isolation", "Depression", "Suicide", "No Risk")
                        val values = listOf(counts.Anxiety, counts.Isolation, counts.Depression, counts.Suicide, counts.No_Risk)
                        val colors = listOf(
                            ContextCompat.getColor(context, R.color.anxiety_orange),
                            ContextCompat.getColor(context, R.color.isolation_gray_blue),
                            ContextCompat.getColor(context, R.color.depression_light_blue),
                            ContextCompat.getColor(context, R.color.suicide_red),
                            ContextCompat.getColor(context, R.color.no_risk_green)
                        )

                        val entries = mutableListOf<PieEntry>()
                        val sliceColors = mutableListOf<Int>()
                        for (i in categories.indices) {
                            if (values[i] > 0) {
                                entries.add(PieEntry(values[i].toFloat(), categories[i]))
                                sliceColors.add(colors[i])
                            }
                        }

                        val dataSet = PieDataSet(entries, "").apply {
                            setColors(sliceColors)
                            valueTextSize = 16f
                            valueTextColor = Color.BLACK
                            sliceSpace = 3f
                            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
                            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
                            valueLinePart1Length = 0.35f
                            valueLinePart2Length = 0.15f
                            valueLineWidth = 1f
                            valueLineColor = Color.BLACK
                            selectionShift = 10f
                        }

                        val pieData = PieData(dataSet).apply {
                            setValueFormatter(object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String =
                                    value.toInt().toString()
                            })
                        }

                        val total = counts.Total.takeIf { it >= 0 }
                            ?: values.sum()

                        pieChart.apply {
                            data = pieData
                            setUsePercentValues(false)
                            isDrawHoleEnabled = true
                            holeRadius = 60f
                            transparentCircleRadius = 70f
                            setDrawEntryLabels(false)
                            setExtraOffsets(30f, 30f, 30f, 40f)
                            description = Description().apply { text = "" }
                            legend.apply {
                                isEnabled = true
                                form = Legend.LegendForm.CIRCLE
                                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                                orientation = Legend.LegendOrientation.HORIZONTAL
                                setDrawInside(false)
                                isWordWrapEnabled = true
                                textSize = 14f
                                textColor = Color.BLACK
                                yOffset = 10f
                            }
                            centerText = "$total\nTotal"
                            setCenterTextSize(20f)
                            setCenterTextColor(Color.BLACK)
                            setTouchEnabled(false)
                            isRotationEnabled = false
                            isHighlightPerTapEnabled = false
                            invalidate()
                        }

                        Handler(Looper.getMainLooper()).post {
                            rootView.measure(
                                View.MeasureSpec.makeMeasureSpec(chartSizePx + 100, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            )
                            rootView.layout(0, 0, rootView.measuredWidth, rootView.measuredHeight)
                            val bitmap = Bitmap.createBitmap(
                                rootView.measuredWidth,
                                rootView.measuredHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            rootView.draw(canvas)
                            callback(bitmap)
                        }
                    } else {
                        callback(null)
                    }
                }

                override fun onFailure(call: Call<MentalHealthStatsResponse>, t: Throwable) {
                    callback(null)
                }
            })
        }
    }
}
