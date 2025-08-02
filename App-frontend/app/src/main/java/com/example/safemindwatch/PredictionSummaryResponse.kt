package com.example.safemindwatch

import com.google.gson.annotations.SerializedName

data class PredictionSummaryResponse(
    val success: Boolean,
    val count: Int,
    val data: PredictionSummaryCounts?
)

data class PredictionSummaryCounts(
    @SerializedName("Anxiety")    val anxiety: Int? = 0,
    @SerializedName("Depression") val depression: Int? = 0,
    @SerializedName("Isolation")  val isolation: Int? = 0,
    @SerializedName("Suicide")    val suicide: Int? = 0,
    @SerializedName("No_Risk")    val noRisk: Int? = 0,
    @SerializedName("Total")      val total: Int? = 0
)

