package com.example.safemindwatch

data class MentalHealthStatsResponse(
    val success: Boolean,
    val count: Int,
    val data: MentalHealthCounts?
)

data class MentalHealthCounts(
    val Anxiety: Int = 0,
    val Depression: Int = 0,
    val Isolation: Int = 0,
    val Suicide: Int = 0,
    val No_Risk: Int = 0,
    val Total: Int = 0
)
