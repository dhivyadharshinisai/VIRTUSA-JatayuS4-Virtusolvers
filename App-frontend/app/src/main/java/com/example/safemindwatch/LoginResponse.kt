package com.example.safemindwatch

data class LoginResponse(
    val token: String,
    val user: UserData
)

data class UserData(
    val name: String,
    val email: String,
    val phone: String?,
    val children: List<Child>?
)

data class Child(
    val _id: String,
    val name: String
)

