package com.example.safemindwatch

import com.google.gson.annotations.SerializedName

data class SentimentData(
    @SerializedName("date") val date: String,
    @SerializedName("averageSentimentScore") val averageSentimentScore: Float? = null
)

data class SentimentResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<SentimentData>?
)
