package com.example.safemindwatch

data class ChildProfileResponse(
    val message: String,
    val children: List<ChildProfile>
)

data class ChildUpdateRequest(
    val email: String,
    val index: Int,
    val name: String
)

data class ChildDeleteRequest(
    val email: String,
    val index: Int
)

