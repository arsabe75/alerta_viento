package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWind?,
    val hourly: HourlyWind?
)

@JsonClass(generateAdapter = true)
data class CurrentWind(
    val time: String,
    @Json(name = "wind_speed_10m") val windSpeed: Double,
    @Json(name = "wind_direction_10m") val windDirection: Double,
    @Json(name = "wind_gusts_10m") val windGusts: Double
)

@JsonClass(generateAdapter = true)
data class HourlyWind(
    val time: List<String>,
    @Json(name = "wind_speed_10m") val windSpeed: List<Double>,
    @Json(name = "wind_direction_10m") val windDirection: List<Double>,
    @Json(name = "wind_gusts_10m") val windGusts: List<Double>
)
