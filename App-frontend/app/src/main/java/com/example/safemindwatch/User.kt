package com.example.safemindwatch

data class User(
    val name: String,
    val email: String,
    val phone: String?,
    val settings: UserSettings? = null,
    val children: List<Children>? = null
)

data class UserSettings(
    val smsAlerts: Boolean = true,
    val emailAlerts: Boolean = true,
    val sosAlerts: Boolean = false
)

data class Children(
    val name: String,
    val age: String?,
    val profileImage: String?
)
