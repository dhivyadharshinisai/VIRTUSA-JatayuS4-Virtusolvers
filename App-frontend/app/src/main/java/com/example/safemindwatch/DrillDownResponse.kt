package com.example.safemindwatch

data class DrillDownResponse(
    val success: Boolean,
    val data: List<DrillDownItem>
)
data class DrillDownItem(
    val query: String,
    val dateAndTime: String,
    val isHarmful: Boolean? = null,
    val predictedResult: String? = null,
    val sentimentScore: Double? = null
)



