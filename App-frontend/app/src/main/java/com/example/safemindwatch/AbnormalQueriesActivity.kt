package com.example.safemindwatch

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.content.Context
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safemindwatch.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AbnormalQueriesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var tvReportInfo: TextView

    private lateinit var btnPickDate: Button
    private lateinit var btnPickRange: Button
    private lateinit var btnWeekly: Button
    private lateinit var btnApplyRange: Button
    private lateinit var btnGenerate: Button

    private lateinit var etFromDateInline: EditText
    private lateinit var etToDateInline: EditText
    private lateinit var dateRangeLayout: LinearLayout

    private lateinit var userId: String
    private lateinit var childName: String

    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_abnormal_queries)

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        tvReportInfo = findViewById(R.id.tvReportInfo)

        btnPickDate = findViewById(R.id.btnPickDate)
        btnPickRange = findViewById(R.id.btnPickRange)
        btnWeekly = findViewById(R.id.btnWeekly)
        btnApplyRange = findViewById(R.id.btnApplyRange)
        btnGenerate = findViewById(R.id.btnGenerate)

        etFromDateInline = findViewById(R.id.etFromDate)
        etToDateInline = findViewById(R.id.etToDate)
        dateRangeLayout = findViewById(R.id.dateRangeLayout)

        recyclerView.layoutManager = LinearLayoutManager(this)

        userId = intent.getStringExtra("userId") ?: ""
        childName = intent.getStringExtra("childName") ?: "Your Child"

        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        highlightSelectedButton(btnWeekly)
        applyPastWeekRange()

        btnPickDate.setOnClickListener {
            highlightSelectedButton(btnPickDate)
            hideRangeInputs()
            showDatePicker()
        }

        btnWeekly.setOnClickListener {
            highlightSelectedButton(btnWeekly)
            applyPastWeekRange()
        }

        btnPickRange.setOnClickListener {
            highlightSelectedButton(btnPickRange)
            showRangeInputs()
            preloadCurrentWeekRangeAndFetch()
        }

        etFromDateInline.setOnClickListener { showInlineDatePicker(etFromDateInline) }
        etToDateInline.setOnClickListener { showInlineDatePicker(etToDateInline) }

        btnApplyRange.setOnClickListener {
            val fromDisplay = etFromDateInline.text.toString().trim()
            val toDisplay = etToDateInline.text.toString().trim()
            try {
                val apiFrom = apiFormat.format(displayFormat.parse(fromDisplay)!!)
                val apiTo = apiFormat.format(displayFormat.parse(toDisplay)!!)
                fetchAbnormalQueries(mode = "range", date = "$apiFrom|$apiTo")
                tvReportInfo.text = Html.fromHtml(
                    "Report generated from <b>$fromDisplay</b> to <b>$toDisplay</b>",
                    Html.FROM_HTML_MODE_LEGACY
                )

            } catch (_: Exception) {
                Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            }
        }

        btnGenerate.setOnClickListener {
            val popupMenu = PopupMenu(this, btnGenerate)
            popupMenu.menuInflater.inflate(R.menu.menu_export, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_export_image -> exportRecyclerViewAsImageWithHeader()
                    R.id.menu_export_pdf -> exportRecyclerViewAsPDFWithHeader()
                }
                true
            }
            popupMenu.show()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(year, month, day)
                val displayDate = displayFormat.format(cal.time)
                val apiDate = apiFormat.format(cal.time)
                fetchAbnormalQueries(mode = "date", date = apiDate)
                tvReportInfo.text = Html.fromHtml(
                    "Report generated for <b>$displayDate</b>",
                    Html.FROM_HTML_MODE_LEGACY
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
                cal.set(year, month, day)
                target.setText(displayFormat.format(cal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun applyPastWeekRange() {
        val calTo = Calendar.getInstance()
        val to = calTo.time
        val calFrom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        val from = calFrom.time

        hideRangeInputs()

        etFromDateInline.setText(displayFormat.format(from))
        etToDateInline.setText(displayFormat.format(to))

        val apiFrom = apiFormat.format(from)
        val apiTo = apiFormat.format(to)

        Log.d("ABN_RANGE", "PastWeek from=$apiFrom to=$apiTo child=$childName")
        fetchAbnormalQueries(mode = "range", date = "$apiFrom|$apiTo")

        tvReportInfo.text = Html.fromHtml(
            "Report generated for <b>${displayFormat.format(from)} to ${displayFormat.format(to)}</b>",
            Html.FROM_HTML_MODE_LEGACY
        )

    }

    private fun preloadCurrentWeekRangeAndFetch() {
        val calStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
        val calEnd = Calendar.getInstance()
        val from = calStart.time
        val to = calEnd.time

        showRangeInputs()

        etFromDateInline.setText(displayFormat.format(from))
        etToDateInline.setText(displayFormat.format(to))

        val apiFrom = apiFormat.format(from)
        val apiTo = apiFormat.format(to)
        fetchAbnormalQueries(mode = "range", date = "$apiFrom|$apiTo")
        tvReportInfo.text = "Report generated from ${displayFormat.format(from)} to ${displayFormat.format(to)}"
    }

    private fun hideRangeInputs() {
        dateRangeLayout.visibility = View.GONE
        btnApplyRange.visibility = View.GONE
    }

    private fun showRangeInputs() {
        dateRangeLayout.visibility = View.VISIBLE
        btnApplyRange.visibility = View.VISIBLE
    }

    private fun highlightSelectedButton(selected: Button) {
        val allButtons = listOf(btnPickRange, btnPickDate, btnWeekly)
        for (btn in allButtons) {
            btn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
            btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        selected.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        selected.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun fetchAbnormalQueries(mode: String, date: String? = null) {
        Log.d("ABN_FETCH", "userId=$userId childName=$childName mode=$mode date=$date")

        RetrofitClient.apiService.getAbnormalQueries(
            userId = userId,
            mode = mode,
            date = date,
            childName = childName
        ).enqueue(object : Callback<AbnormalQueryResponse> {
            override fun onResponse(
                call: Call<AbnormalQueryResponse>,
                response: Response<AbnormalQueryResponse>
            ) {
                Log.d("ABN_FETCH", "Response code=${response.code()}")
                if (!response.isSuccessful || response.body()?.success != true) {
                    Toast.makeText(this@AbnormalQueriesActivity, "Server error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    showAbnormalQueries(emptyList())
                    return
                }
                val list = response.body()?.data ?: emptyList()
                showAbnormalQueries(list)
            }

            override fun onFailure(call: Call<AbnormalQueryResponse>, t: Throwable) {
                Log.e("ABN_FETCH", "Failure", t)
                Toast.makeText(this@AbnormalQueriesActivity, "Network Error: ${t.message}", Toast.LENGTH_LONG).show()
                showAbnormalQueries(emptyList())
            }
        })
    }

    private fun showAbnormalQueries(list: List<AbnormalQuery>) {
        val sorted = list.sortedByDescending { parseBackendDate(it.dateAndTime) }
        recyclerView.adapter = AbnormalQueryAdapter(sorted)
        recyclerView.visibility = if (sorted.isNotEmpty()) View.VISIBLE else View.GONE
        emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        if (sorted.isEmpty()) {
            emptyView.text = "No data available for this selection."
        }
    }

    private fun parseBackendDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(raw)
        } catch (_: Exception) {
            try {
                SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale.getDefault()).parse(raw)
            } catch (_: Exception) {
                try {
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).parse(raw)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun exportRecyclerViewAsImageWithHeader() {
        try {
            val recyclerAdapter = recyclerView.adapter ?: return
            val itemCount = recyclerAdapter.itemCount
            val header = layoutInflater.inflate(R.layout.query_header_row, null)
            header.measure(
                View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            header.layout(0, 0, header.measuredWidth, header.measuredHeight)
            val rowViews = mutableListOf<View>()
            for (i in 0 until itemCount) {
                val holder = recyclerAdapter.createViewHolder(recyclerView, recyclerAdapter.getItemViewType(i))
                recyclerAdapter.onBindViewHolder(holder, i)
                val itemView = holder.itemView
                itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                itemView.layout(0, 0, itemView.measuredWidth, itemView.measuredHeight)
                rowViews.add(itemView)
            }
            val totalHeight = rowViews.sumOf { it.measuredHeight } + header.measuredHeight
            val bitmap = Bitmap.createBitmap(recyclerView.width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            header.draw(canvas)
            var top = header.measuredHeight
            for (row in rowViews) {
                canvas.save(); canvas.translate(0f, top.toFloat()); row.draw(canvas); canvas.restore();
                top += row.measuredHeight
            }
            val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "abnormal_queries_report_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            Toast.makeText(this, "Image saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Image export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportRecyclerViewAsPDFWithHeader() {
        try {
            val recyclerAdapter = recyclerView.adapter ?: return
            val itemCount = recyclerAdapter.itemCount
            val header = layoutInflater.inflate(R.layout.query_header_row, null)
            header.measure(
                View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            header.layout(0, 0, header.measuredWidth, header.measuredHeight)
            val rowViews = mutableListOf<View>()
            for (i in 0 until itemCount) {
                val holder = recyclerAdapter.createViewHolder(recyclerView, recyclerAdapter.getItemViewType(i))
                recyclerAdapter.onBindViewHolder(holder, i)
                val itemView = holder.itemView
                itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                itemView.layout(0, 0, itemView.measuredWidth, itemView.measuredHeight)
                rowViews.add(itemView)
            }
            val totalHeight = rowViews.sumOf { it.measuredHeight } + header.measuredHeight
            val bitmap = Bitmap.createBitmap(recyclerView.width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            header.draw(canvas)
            var top = header.measuredHeight
            for (row in rowViews) {
                canvas.save(); canvas.translate(0f, top.toFloat()); row.draw(canvas); canvas.restore();
                top += row.measuredHeight
            }
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
            val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "abnormal_queries_report_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            Toast.makeText(this, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    inner class AbnormalQueryAdapter(private val queryList: List<AbnormalQuery>) :
        RecyclerView.Adapter<AbnormalQueryAdapter.QueryViewHolder>() {

        inner class QueryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val queryText: TextView = view.findViewById(R.id.textQuery)
            val timeSpentText: TextView = view.findViewById(R.id.textTimeSpent)
            val dateText: TextView = view.findViewById(R.id.textDateTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.query_card_item, parent, false)
            return QueryViewHolder(view)
        }

        override fun onBindViewHolder(holder: QueryViewHolder, position: Int) {
            val item = queryList[position]
            holder.queryText.text = item.query
            holder.timeSpentText.text = item.totalTimeSpent.toString()
            holder.dateText.text = item.dateAndTime
        }

        override fun getItemCount(): Int = queryList.size
    }

    companion object {

        fun exportChartBitmap(
            context: Context,
            userId: String,
            mode: String,
            dateParam: String,
            childName: String,
            callback: (Bitmap?) -> Unit
        ) {
            val activity = AbnormalQueriesActivity()
            activity.userId = userId
            activity.childName = childName

            val inflater = LayoutInflater.from(context)
            val rootView = inflater.inflate(R.layout.activity_abnormal_queries, null)

            val density = context.resources.displayMetrics.density
            val chartSizeDp = 900f
            val chartSizePx = (chartSizeDp * density).toInt()


            rootView.measure(
                View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            rootView.layout(0, 0, rootView.measuredWidth, rootView.measuredHeight)

            val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerView)

            val retrofitCall = RetrofitClient.apiService.getAbnormalQueries(
                userId = userId,
                mode = mode,
                date = dateParam,
                childName = childName
            )

            retrofitCall.enqueue(object : Callback<AbnormalQueryResponse> {
                override fun onResponse(
                    call: Call<AbnormalQueryResponse>,
                    response: Response<AbnormalQueryResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val list = response.body()?.data ?: emptyList()
                        val sorted = list.sortedByDescending { activity.parseBackendDate(it.dateAndTime) }

                        recyclerView.layoutManager = LinearLayoutManager(context)
                        recyclerView.adapter = activity.AbnormalQueryAdapter(sorted)

                        val adapter = recyclerView.adapter ?: return callback(null)

                        val header = inflater.inflate(R.layout.query_header_row, null)
                        header.measure(
                            View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        header.layout(0, 0, chartSizePx, header.measuredHeight)

                        val rowViews = mutableListOf<View>()
                        for (i in 0 until adapter.itemCount) {
                            val holder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(i))
                            adapter.onBindViewHolder(holder, i)
                            val itemView = holder.itemView
                            itemView.measure(
                                View.MeasureSpec.makeMeasureSpec(chartSizePx, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            )
                            itemView.layout(0, 0, chartSizePx, itemView.measuredHeight)
                            rowViews.add(itemView)
                        }

                        val totalHeight = header.measuredHeight + rowViews.sumOf { it.measuredHeight }
                        val bitmap = Bitmap.createBitmap(chartSizePx, totalHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        header.draw(canvas)

                        var top = header.measuredHeight
                        for (row in rowViews) {
                            canvas.save()
                            canvas.translate(0f, top.toFloat())
                            row.draw(canvas)
                            canvas.restore()
                            top += row.measuredHeight
                        }

                        callback(bitmap)
                    } else {
                        callback(null)
                    }
                }

                override fun onFailure(call: Call<AbnormalQueryResponse>, t: Throwable) {
                    callback(null)
                }
            })
        }
    }
}

