package com.example.safemindwatch

data class StatisticsResponse(
    val userId: String,
    val raw: Map<String, Any>?,
    val formatted: Map<String, String>?
)

