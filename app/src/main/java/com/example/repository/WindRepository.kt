package com.example.repository

import com.example.data.api.OpenMeteoGeocodingService
import com.example.data.api.OpenMeteoWeatherService
import com.example.data.local.AlertDao
import com.example.data.local.LocationDao
import com.example.data.local.MonitoredLocation
import com.example.data.local.WindAlertLog
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class WindRepository(
    private val locationDao: LocationDao,
    private val alertDao: AlertDao
) {
    // Set up Moshi with Kotlin adapter
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // OkHttp Client with Logging interceptor for clean responses
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    // Create Retrofit Weather API Service
    private val weatherService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoWeatherService::class.java)

    // Create Retrofit Geocoding API Service for city and coord queries
    private val geocodingService = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoGeocodingService::class.java)

    // DB Operations exposing Flow lists
    val allLocations: Flow<List<MonitoredLocation>> = locationDao.getAllLocations()
    val primaryLocation: Flow<MonitoredLocation?> = locationDao.getPrimaryLocationFlow()
    val alertLogs: Flow<List<WindAlertLog>> = alertDao.getAllAlerts()

    suspend fun getPrimaryLocationDirect(): MonitoredLocation? {
        return locationDao.getPrimaryLocation()
    }

    suspend fun saveLocation(location: MonitoredLocation): Long {
        return locationDao.insertLocation(location)
    }

    suspend fun updateLocation(location: MonitoredLocation) {
        locationDao.updateLocation(location)
    }

    suspend fun deleteLocation(location: MonitoredLocation) {
        locationDao.deleteLocation(location)
    }

    suspend fun setLocationAsPrimary(locationId: Int) {
        locationDao.setAsPrimary(locationId)
    }

    suspend fun logWindAlert(alert: WindAlertLog) {
        alertDao.insertAlert(alert)
    }

    suspend fun clearAlertLogs() {
        alertDao.clearAllAlerts()
    }

    // Network Operations
    suspend fun fetchWindForecast(latitude: Double, longitude: Double): WeatherResponse {
        return weatherService.getWindForecast(latitude, longitude)
    }

    suspend fun searchLocations(query: String): List<GeocodingResult> {
        return try {
            val response = geocodingService.searchLocations(query)
            response.results ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
