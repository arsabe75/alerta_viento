package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_locations")
data class MonitoredLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isPrimary: Boolean,
    val customThreshold: Double = 30.0
)

@Entity(tableName = "wind_alert_logs")
data class WindAlertLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val locationName: String,
    val windGustSpeed: Double,
    val windSpeed: Double,
    val windDirection: Double,
    val threshold: Double,
    val message: String
)
