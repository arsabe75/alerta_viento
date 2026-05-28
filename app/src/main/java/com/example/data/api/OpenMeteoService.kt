package com.example.data.api

import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoWeatherService {
    @GET("v1/forecast")
    suspend fun getWindForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        @Query("hourly") hourly: String = "wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

interface OpenMeteoGeocodingService {
    @GET("v1/search")
    suspend fun searchLocations(
        @Query("name") query: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "es",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
