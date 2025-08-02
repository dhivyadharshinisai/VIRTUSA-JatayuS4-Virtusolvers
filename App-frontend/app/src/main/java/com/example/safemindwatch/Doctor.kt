package com.example.safemindwatch

data class Review(
    val reviewerName: String,
    val reviewText: String,
    val rating: Float,
    val timestamp: String
)

data class Doctor(
    val name: String,
    val address: String,
    val phone: String?,
    val distanceMeters: Float,
    val lat: Double,
    val lng: Double,
    val openingHours: String?,
    val openNow: Boolean?,
    val photoUrl: String?,
    val rating: Float,
    val reviews: List<Review>? = null
)
