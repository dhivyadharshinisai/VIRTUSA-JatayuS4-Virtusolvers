package com.example.safemindwatch

data class UpdateSettingsRequest(
    val userId: String,
    val smsAlerts: Boolean,
    val emailAlerts: Boolean,
    val sosAlerts: Boolean
)
