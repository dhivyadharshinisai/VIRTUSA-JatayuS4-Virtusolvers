package com.example.safemindwatch

data class AbnormalQuery(
    val query: String,
    val totalTimeSpent: Int,
    val dateAndTime: String,
    val isHarmful: Boolean
)

data class AbnormalQueryResponse(
    val success: Boolean,
    val count: Int,
    val data: List<AbnormalQuery>
)
