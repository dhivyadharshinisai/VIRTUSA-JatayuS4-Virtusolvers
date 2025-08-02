package com.example.safemindwatch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class DoctorAdapter(
    private val doctorList: List<Doctor>,
    private val context: Context
) : RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()

    class DoctorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imageDoctor)
        val name: TextView = itemView.findViewById(R.id.textDoctorName)
        val specialization: TextView = itemView.findViewById(R.id.textSpecialization)
        val address: TextView = itemView.findViewById(R.id.textDoctorAddress)
        val phone: TextView = itemView.findViewById(R.id.textDoctorPhone)
        val hours: TextView = itemView.findViewById(R.id.textDoctorHours)
        val status: TextView = itemView.findViewById(R.id.textDoctorOpenStatus)
        val ratingText: TextView = itemView.findViewById(R.id.textDoctorRating)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val distance: TextView = itemView.findViewById(R.id.textDoctorDistance)
        val call: TextView = itemView.findViewById(R.id.buttonCall)
        val directs: TextView = itemView.findViewById(R.id.buttonDirections)
        val reviewsHeading: TextView = itemView.findViewById(R.id.textReviewsHeading)
        val reviewsContainer: LinearLayout = itemView.findViewById(R.id.reviewsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_doctor, parent, false)
        return DoctorViewHolder(view)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        val d = doctorList[position]

        holder.name.text = d.name
        holder.specialization.text = "Child Psychologist"
        holder.address.text = "Location: ${d.address}"
        holder.phone.text = "Phone: ${d.phone ?: "N/A"}"
        holder.hours.text = "Timings: ${d.openingHours ?: "N/A"}"

        val ratingDisplay = if (d.rating % 1.0f == 0.0f) d.rating.toInt().toString() else d.rating.toString()
        holder.ratingText.text = "Rating: $ratingDisplay/5"
        holder.ratingBar.rating = d.rating

        holder.distance.text = if (d.distanceMeters >= 0) {
            val km = "%.2f".format(d.distanceMeters / 1000f)
            "Distance: $km km"
        } else {
            "Distance: N/A"
        }

        holder.status.text = if (d.openNow == true) "Status: Open" else "Status: Closed"
        holder.status.setTextColor(
            if (d.openNow == true) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")
        )

        Glide.with(context)
            .load(d.photoUrl)
            .placeholder(R.drawable.ic_launcher_foreground)
            .into(holder.image)

        holder.call.setOnClickListener {
            d.phone?.let { phoneNum ->
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum")))
            } ?: Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
        }

        holder.directs.setOnClickListener {
            val locationUri = if (d.lat != 0.0 && d.lng != 0.0) {
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${d.lat},${d.lng}")
            } else {
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(d.address)}")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, locationUri))
        }

        // ==== Reviews Section ====
        val isExpanded = expandedPositions.contains(position)
        val reviewCount = d.reviews?.size ?: 0
        holder.reviewsHeading.text = if (isExpanded) "Reviews ($reviewCount) ▲" else "Reviews ($reviewCount) ▼"
        holder.reviewsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.reviewsHeading.setOnClickListener {
            if (isExpanded) expandedPositions.remove(position) else expandedPositions.add(position)
            notifyItemChanged(position)
        }

        holder.reviewsContainer.removeAllViews()
        if (isExpanded) {
            if (d.reviews.isNullOrEmpty()) {
                val noReview = TextView(context).apply {
                    text = "No reviews available"
                    setTextColor(Color.parseColor("#888888"))
                    textSize = 13f
                    setPadding(0, 4, 0, 4)
                    setTypeface(null, Typeface.ITALIC)
                }
                holder.reviewsContainer.addView(noReview)
            } else {
                for (review in d.reviews) {
                    // Review Card Layout
                    val reviewLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 16, 0, 20)
                    }

                    // Name + Gold Stars row
                    val nameStarRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                    val nameView = TextView(context).apply {
                        text = review.reviewerName
                        textSize = 14f
                        setTextColor(Color.BLACK)
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, 0, 8, 0)
                    }
                    val reviewStars = RatingBar(context, null, android.R.attr.ratingBarStyleSmall).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        rating = review.rating
                        numStars = 5
                        stepSize = 0.5f
                        setIsIndicator(true)
                        scaleX = 0.85f
                        scaleY = 0.85f
                        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
                        thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
                    }
                    nameStarRow.addView(nameView)
                    nameStarRow.addView(reviewStars)

                    // Review text under name/stars
                    val reviewTextView = TextView(context).apply {
                        text = review.reviewText
                        textSize = 13.5f
                        setTextColor(Color.parseColor("#444444"))
                        setPadding(0, 4, 0, 0)
                    }

                    // Timestamp, right aligned at bottom
                    val timestampView = TextView(context).apply {
                        text = review.timestamp
                        textSize = 12f
                        setTextColor(Color.parseColor("#888888"))
                        setPadding(0, 4, 0, 0)
                        gravity = Gravity.END
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    reviewLayout.addView(nameStarRow)
                    reviewLayout.addView(reviewTextView)
                    reviewLayout.addView(timestampView)

                    holder.reviewsContainer.addView(reviewLayout)
                }
            }
        }
    }

    override fun getItemCount(): Int = doctorList.size
}
