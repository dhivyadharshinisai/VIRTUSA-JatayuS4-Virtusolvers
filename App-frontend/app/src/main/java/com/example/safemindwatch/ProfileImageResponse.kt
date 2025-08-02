package com.example.safemindwatch

data class ProfileImageResponse(
    val success: Boolean,
    val imageData: String? = null,
    val message: String? = null
)
