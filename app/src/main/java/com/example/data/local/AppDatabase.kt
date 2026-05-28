package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM monitored_locations ORDER BY id ASC")
    fun getAllLocations(): Flow<List<MonitoredLocation>>

    @Query("SELECT * FROM monitored_locations WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryLocationFlow(): Flow<MonitoredLocation?>

    @Query("SELECT * FROM monitored_locations WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryLocation(): MonitoredLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: MonitoredLocation): Long

    @Update
    suspend fun updateLocation(location: MonitoredLocation)

    @Delete
    suspend fun deleteLocation(location: MonitoredLocation)

    @Query("UPDATE monitored_locations SET isPrimary = 0")
    suspend fun clearPrimaryStatus()

    @Transaction
    suspend fun setAsPrimary(locationId: Int) {
        clearPrimaryStatus()
        updatePrimaryStatus(locationId, 1)
    }

    @Query("UPDATE monitored_locations SET isPrimary = :isPrimary WHERE id = :id")
    suspend fun updatePrimaryStatus(id: Int, isPrimary: Int)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM wind_alert_logs ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<WindAlertLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: WindAlertLog)

    @Query("DELETE FROM wind_alert_logs")
    suspend fun clearAllAlerts()
}

@Database(entities = [MonitoredLocation::class, WindAlertLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun alertDao(): AlertDao
}
