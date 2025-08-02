package com.example.safemindwatch

data class PlacesApiResponse(
    val results: List<PlaceResult>
)

data class PlaceResult(
    val name: String,
    val vicinity: String,
    val geometry: Geometry,
    val place_id: String,
    val opening_hours: OpeningHours? = null
)

data class Geometry(
    val location: LocationLatLng
)

data class LocationLatLng(
    val lat: Double,
    val lng: Double
)

data class OpeningHours(
    val open_now: Boolean,
    val weekday_text: List<String>? = null
)

