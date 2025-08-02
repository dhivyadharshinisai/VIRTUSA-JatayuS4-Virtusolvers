package com.example.safemindwatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DrillDownAdapter(private val items: List<DrillDownItem>) :
    RecyclerView.Adapter<DrillDownAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuery: TextView = view.findViewById(R.id.tvQuery)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.drilldown_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvQuery.text = "🔔 Query: ${item.query}"
        holder.tvTime.text = "📅 Time: ${formatDate(item.dateAndTime)}"
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = parser.parse(dateStr)
            val formatter = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
            formatter.format(parsedDate!!)
        } catch (e: Exception) {
            dateStr
        }
    }
}
