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
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.safemindwatch.api.RetrofitClient
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class PieChartActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var tvReportInfo: TextView
    private lateinit var btnPickDate: Button
    private lateinit var btnWeekly: Button
    private lateinit var btnPickRange: Button
    private lateinit var btnApplyRange: Button
    private lateinit var etFromDateInline: EditText
    private lateinit var etToDateInline: EditText
    private lateinit var dateRangeLayout: LinearLayout
    private lateinit var tvSelectedRange: TextView
    private lateinit var btnGenerate: Button

    private lateinit var userId: String
    private lateinit var childName: String

    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val backendFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var lastSelectedIndex: Int = -1

    private var currentMode: String = "weekly"
    private var currentDateParam: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pie_chart)

        pieChart = findViewById(R.id.pieChart)
        tvReportInfo = findViewById(R.id.tvReportInfo)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnPickRange = findViewById(R.id.btnPickRange)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        etFromDateInline = findViewById(R.id.etFromDateInline)
        etToDateInline = findViewById(R.id.etToDateInline)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)
        tvSelectedRange = findViewById(R.id.tvSelectedRange)
        btnGenerate = findViewById(R.id.btnGenerate)

        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: "Your Child"

        setupPieChart()
        attachPieListener()

        highlightSelectedButton(btnWeekly)
        setPreviousWeekRange()

        btnPickDate.setOnClickListener {
            showDatePicker()
            highlightSelectedButton(btnPickDate)
            dateRangeLayout.visibility = View.GONE
            tvReportInfo.visibility = View.VISIBLE
            tvSelectedRange.visibility = View.GONE
        }

        btnWeekly.setOnClickListener {
            highlightSelectedButton(btnWeekly)
            setPreviousWeekRange()
        }

        btnPickRange.setOnClickListener {
            highlightSelectedButton(btnPickRange)
            setDefaultPickRange()
            tvReportInfo.visibility = View.VISIBLE
            tvSelectedRange.visibility = View.GONE
        }

        etFromDateInline.setOnClickListener {
            showInlineDatePicker(etFromDateInline)
        }

        etToDateInline.setOnClickListener {
            showInlineDatePicker(etToDateInline)
        }

        btnApplyRange.setOnClickListener {
            val fromDisplay = etFromDateInline.text.toString()
            val toDisplay = etToDateInline.text.toString()
            try {
                val from = backendFormat.format(displayFormat.parse(fromDisplay)!!)
                val to = backendFormat.format(displayFormat.parse(toDisplay)!!)
                fetchPieChartData("range", "$from|$to")
                tvReportInfo.text = "Report generated from $fromDisplay to $toDisplay"
                currentMode = "range"
                currentDateParam = "$from|$to"
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            }
        }

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
    }

    private fun setupPieChart() {
        pieChart.setUsePercentValues(false)
        pieChart.description = Description().apply { text = "" }
        pieChart.isDrawHoleEnabled = false
        pieChart.setDrawCenterText(false)
        pieChart.rotationAngle = 0f
        pieChart.isRotationEnabled = true
        pieChart.isHighlightPerTapEnabled = true
        pieChart.legend.isEnabled = false
        pieChart.animateY(1200)
    }

    private fun attachPieListener() {
        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                h ?: return
                val entry = e as? PieEntry ?: return
                openDrillDown(entry.label, entry.value)
            }
            override fun onNothingSelected() {}
        })
    }

    private fun openDrillDown(label: String, value: Float) {
        val intent = Intent(this, DrillDownActivity::class.java).apply {
            putExtra("label", label)
            putExtra("value", value)
            putExtra("index", lastSelectedIndex)
            putExtra("mode", currentMode)
            putExtra("dateParam", currentDateParam)
            putExtra("userId", userId)
            putExtra("childName", childName)
            putExtra("drillType", "harmfulSafe")
        }
        startActivity(intent)
    }

    private fun fetchPieChartData(mode: String, date: String? = null) {
        currentMode = mode
        currentDateParam = date

        RetrofitClient.apiService.getPieChartData(
            userId = userId,
            mode = mode,
            date = date,
            childName = childName
        ).enqueue(object : Callback<PieChartResponse> {
            override fun onResponse(
                call: Call<PieChartResponse>,
                response: Response<PieChartResponse>
            ) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        showPieChart(it.harmfulCount.toFloat(), it.safeCount.toFloat())
                    } ?: pieChart.clear()
                } else pieChart.clear()
            }

            override fun onFailure(call: Call<PieChartResponse>, t: Throwable) {
                pieChart.clear()
            }
        })
    }

    private fun showPieChart(harmful: Float, safe: Float) {
        val total = harmful + safe
        if (total == 0f) {
            pieChart.clear()
            Toast.makeText(this, "No data to show", Toast.LENGTH_SHORT).show()
            return
        }

        val harmfulPercent = ((harmful / total) * 100).roundToInt()
        val safePercent = 100 - harmfulPercent

        Log.d("PieChartDebug", "Harmful Percentage: $harmfulPercent")
        Log.d("PieChartDebug", "Safe Percentage: $safePercent")

        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        if (harmful > 0) {
            entries.add(PieEntry(harmfulPercent.toFloat(), "Harmful searched"))
            colors.add(Color.parseColor("#EE4B2B"))
        }
        if (safe > 0) {
            entries.add(PieEntry(safePercent.toFloat(), "Safe searched"))
            colors.add(Color.parseColor("#39AD48"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextColor = Color.WHITE
            valueTextSize = 16f
            sliceSpace = 6f
            selectionShift = 12f
        }

        val pieData = PieData(dataSet).apply {
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}%"
                }
            })
        }

        pieChart.data = pieData
        pieChart.invalidate()
    }


    private fun showInlineDatePicker(target: EditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                calendar.set(y, m, d)
                target.setText(displayFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            val backendDate = backendFormat.format(cal.time)
            val displayDate = displayFormat.format(cal.time)
            fetchPieChartData("date", backendDate)
            tvReportInfo.text = Html.fromHtml(
                "Report generated for <b>$displayDate</b>",
                Html.FROM_HTML_MODE_LEGACY
            )
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setPreviousWeekRange() {
        val calTo = Calendar.getInstance()
        val calFrom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        val from = calFrom.time
        val to = calTo.time

        etFromDateInline.isEnabled = false
        etToDateInline.isEnabled = false
        btnApplyRange.visibility = View.GONE
        dateRangeLayout.visibility = View.GONE

        etFromDateInline.setText(displayFormat.format(from))
        etToDateInline.setText(displayFormat.format(to))

        fetchPieChartData("range", "${backendFormat.format(from)}|${backendFormat.format(to)}")
        tvReportInfo.visibility = View.VISIBLE
        tvSelectedRange.visibility = View.GONE
        tvSelectedRange.textAlignment = View.TEXT_ALIGNMENT_INHERIT
        tvReportInfo.text = Html.fromHtml(
            "Report generated from <b>${displayFormat.format(from)}</b> to <b>${displayFormat.format(to)}</b>",
            Html.FROM_HTML_MODE_LEGACY
        )
    }

    private fun setDefaultPickRange() {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
        val from = cal.time
        val to = Date()

        etFromDateInline.isEnabled = true
        etToDateInline.isEnabled = true
        btnApplyRange.visibility = View.VISIBLE
        dateRangeLayout.visibility = View.VISIBLE

        etFromDateInline.setText(displayFormat.format(from))
        etToDateInline.setText(displayFormat.format(to))

        fetchPieChartData("range", "${backendFormat.format(from)}|${backendFormat.format(to)}")
        tvReportInfo.text = Html.fromHtml(
            "Report generated from <b>${displayFormat.format(from)}</b> to <b>${displayFormat.format(to)}</b>",
            Html.FROM_HTML_MODE_LEGACY
        )
    }

    private fun highlightSelectedButton(selected: Button) {
        listOf(btnPickDate, btnWeekly, btnPickRange).forEach {
            it.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            it.setTextColor(Color.WHITE)
        }
        selected.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
    }

    private fun exportPieChartAsImage() {
        try {
            val bitmap =
                Bitmap.createBitmap(pieChart.width, pieChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            pieChart.draw(canvas)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "pie_chart_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.flush()
            }
            Toast.makeText(this, "Image saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPieChartAsPDF() {
        try {
            val bitmap =
                Bitmap.createBitmap(pieChart.width, pieChart.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            pieChart.draw(canvas)

            val doc = PdfDocument()
            val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height + 200, 1).create()
            val page = doc.startPage(info)
            page.canvas.apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 100f, null)
                drawText(tvReportInfo.text.toString(), 40f, 60f, android.graphics.Paint().apply {
                    color = Color.BLACK
                    textSize = 36f
                    isFakeBoldText = true
                })
            }
            doc.finishPage(page)

            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "pie_chart_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()

            Toast.makeText(this, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed", Toast.LENGTH_SHORT).show()
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
            Log.d("ChartExport", "Export called with: $userId, $childName, $dateParam")

            val chartSizeDp = 900f
            val density = context.resources.displayMetrics.density
            val chartSizePx = (chartSizeDp * density).toInt()

            val chart = com.github.mikephil.charting.charts.PieChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(chartSizePx, chartSizePx)
                setUsePercentValues(true)
                description.isEnabled = false
                isDrawHoleEnabled = false
                setDrawCenterText(false)
                isRotationEnabled = false
                isHighlightPerTapEnabled = false
                legend.isEnabled = true
            }

            val api = com.example.safemindwatch.api.RetrofitClient.apiService
            api.getPieChartData(userId, mode, dateParam, childName)
                .enqueue(object : retrofit2.Callback<PieChartResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<PieChartResponse>,
                        response: retrofit2.Response<PieChartResponse>
                    ) {
                        if (response.isSuccessful) {
                            val res = response.body()
                            Log.d("ChartExport", "API success: Safe=${res?.safeCount}, Harmful=${res?.harmfulCount}")

                            val harmful = res?.harmfulCount?.toFloat() ?: 0f
                            val safe = res?.safeCount?.toFloat() ?: 0f
                            val total = harmful + safe
                            val harmfulPercent = if (total == 0f) 0f else harmful / total * 100
                            val safePercent = 100f - harmfulPercent

                            val entries = mutableListOf<com.github.mikephil.charting.data.PieEntry>()
                            val colors = mutableListOf<Int>()
                            if (harmful > 0) {
                                entries.add(com.github.mikephil.charting.data.PieEntry(harmfulPercent, "Harmful searched"))
                                colors.add(Color.parseColor("#EE4B2B"))
                            }
                            if (safe > 0) {
                                entries.add(com.github.mikephil.charting.data.PieEntry(safePercent, "Safe searched"))
                                colors.add(Color.parseColor("#39AD48"))
                            }

                            val dataSet = com.github.mikephil.charting.data.PieDataSet(entries, "").apply {
                                this.colors = colors
                                valueTextColor = Color.WHITE
                                valueTextSize = 20f
                                sliceSpace = 6f
                            }

                            chart.data = com.github.mikephil.charting.data.PieData(dataSet).apply {
                                setValueFormatter(object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return "${value.toInt()}%"
                                    }
                                })
                            }

                            chart.measure(
                                View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY)
                            )
                            chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)

                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    Log.d("ChartExport", "Drawing chart to bitmap...")
                                    val bitmap = Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    chart.draw(canvas)
                                    Log.d("ChartExport", "Bitmap created successfully")
                                    onBitmapReady(bitmap)
                                } catch (e: Exception) {
                                    Log.e("ChartExport", "Error during bitmap creation: ${e.message}")
                                    onBitmapReady(null)
                                }
                            }, 500)
                        } else {
                            Log.e("ChartExport", "API error: ${response.code()}")
                            onBitmapReady(null)
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<PieChartResponse>, t: Throwable) {
                        Log.e("ChartExport", "Network failure: ${t.message}")
                        onBitmapReady(null)
                    }
                })
        }
    }
}
