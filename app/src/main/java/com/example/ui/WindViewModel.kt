package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.MonitoredLocation
import com.example.data.local.WindAlertLog
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.example.repository.WindRepository
import com.example.sensor.CompassManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val response: WeatherResponse) : WeatherUiState
    data class Error(val error: String) : WeatherUiState
    object Empty : WeatherUiState
}

class WindViewModel(application: Application) : AndroidViewModel(application) {

    private val db = RoomDatabaseHolder.getDatabase(application)
    private val repository = WindRepository(db.locationDao(), db.alertDao())
    private val compassManager = CompassManager(application)

    // UI state flows
    val allLocations: StateFlow<List<MonitoredLocation>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryLocation: StateFlow<MonitoredLocation?> = repository.primaryLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val alertLogs: StateFlow<List<WindAlertLog>> = repository.alertLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Empty)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _compassAzimuth = MutableStateFlow(0f)
    val compassAzimuth: StateFlow<Float> = _compassAzimuth.asStateFlow()

    // Geocoding Search
    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // GPS status
    private val _gpsStatus = MutableStateFlow<String?>(null)
    val gpsStatus: StateFlow<String?> = _gpsStatus.asStateFlow()

    private var weatherUpdateJob: Job? = null

    init {
        // Observe primary location changes, fetch weather once it loaded or changes
        viewModelScope.launch {
            primaryLocation.collect { location ->
                if (location != null) {
                    refreshWeatherForLocation(location)
                } else {
                    // Seed a default location if list is empty
                    seedDefaultLocationIfNeeded()
                }
            }
        }

        // Connect compass values
        viewModelScope.launch {
            compassManager.azimuth.collect { deg ->
                _compassAzimuth.value = deg
            }
        }

        createNotificationChannel()
    }

    fun startCompass() {
        compassManager.startListening()
    }

    fun stopCompass() {
        compassManager.stopListening()
    }

    private suspend fun seedDefaultLocationIfNeeded() {
        val locations = repository.allLocations.first()
        if (locations.isEmpty()) {
            // Seed "Cochera Principal" in a typical default coordinates (e.g. Madrid/Tijuana etc.)
            val defaultLoc = MonitoredLocation(
                name = "Mi Cochera (Local)",
                latitude = 40.4168, // Madrid
                longitude = -3.7038,
                isPrimary = true,
                customThreshold = 30.0
            )
            repository.saveLocation(defaultLoc)
        } else {
            // If locations are not empty but primary is null, make first one primary
            repository.setLocationAsPrimary(locations.first().id)
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            primaryLocation.value?.let { refreshWeatherForLocation(it) }
        }
    }

    private fun refreshWeatherForLocation(location: MonitoredLocation) {
        weatherUpdateJob?.cancel()
        weatherUpdateJob = viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            try {
                val forecast = repository.fetchWindForecast(location.latitude, location.longitude)
                _weatherState.value = WeatherUiState.Success(forecast)
                
                // Alert checking engine
                val current = forecast.current
                if (current != null) {
                    checkAndTriggerAlert(current.windGusts, current.windSpeed, current.windDirection, location)
                }
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error("Error al conectar: ${e.localizedMessage ?: "Verifica tu conexión"}")
            }
        }
    }

    private fun checkAndTriggerAlert(gust: Double, speed: Double, direction: Double, location: MonitoredLocation) {
        if (gust >= location.customThreshold) {
            viewModelScope.launch {
                // Throttle alerts: only write alert log once per 10 minutes for this location
                val pastAlerts = repository.alertLogs.first()
                val recentMatch = pastAlerts.firstOrNull { 
                    it.locationName == location.name && 
                    (System.currentTimeMillis() - it.timestamp) < 10 * 60 * 1000 
                }

                if (recentMatch == null) {
                    val msg = "⚠️ ¡Rachas de viento críticas! Soplan a ${gust} km/h en ${location.name}, superando el límite de seguridad de ${location.customThreshold} km/h. ¡Asegura las lonas!"
                    val log = WindAlertLog(
                        locationName = location.name,
                        windGustSpeed = gust,
                        windSpeed = speed,
                        windDirection = direction,
                        threshold = location.customThreshold,
                        message = msg
                    )
                    repository.logWindAlert(log)
                    showSystemNotification(location.name, gust, location.customThreshold)
                }
            }
        }
    }

    // Settings actions
    fun updateThreshold(threshold: Double) {
        viewModelScope.launch {
            val primary = primaryLocation.value
            if (primary != null) {
                val updated = primary.copy(customThreshold = threshold)
                repository.updateLocation(updated)
                // Force check if succeeded
                if (weatherState.value is WeatherUiState.Success) {
                    val current = (weatherState.value as WeatherUiState.Success).response.current
                    if (current != null) {
                        checkAndTriggerAlert(current.windGusts, current.windSpeed, current.windDirection, updated)
                    }
                }
            }
        }
    }

    fun makeLocationPrimary(id: Int) {
        viewModelScope.launch {
            repository.setLocationAsPrimary(id)
        }
    }

    fun deleteLocation(location: MonitoredLocation) {
        viewModelScope.launch {
            repository.deleteLocation(location)
            // Ensure we have a primary
            val remaining = repository.allLocations.first()
            if (remaining.isNotEmpty() && primaryLocation.value == null) {
                repository.setLocationAsPrimary(remaining.first().id)
            }
        }
    }

    // Geocoding functions
    fun searchAddress(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val items = repository.searchLocations(query)
                _searchResults.value = items
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addSearchedLocation(result: GeocodingResult) {
        viewModelScope.launch {
            val nameCapitalized = result.name + (if (result.admin1 != null) ", ${result.admin1}" else "")
            val newLoc = MonitoredLocation(
                name = nameCapitalized,
                latitude = result.latitude,
                longitude = result.longitude,
                isPrimary = true,
                customThreshold = primaryLocation.value?.customThreshold ?: 30.0
            )
            val id = repository.saveLocation(newLoc)
            repository.setLocationAsPrimary(id.toInt())
            _searchResults.value = emptyList() // clear search results
        }
    }

    fun addManualLocation(name: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val newLoc = MonitoredLocation(
                name = name,
                latitude = lat,
                longitude = lon,
                isPrimary = true,
                customThreshold = primaryLocation.value?.customThreshold ?: 30.0
            )
            val id = repository.saveLocation(newLoc)
            repository.setLocationAsPrimary(id.toInt())
        }
    }

    fun clearAlerts() {
        viewModelScope.launch {
            repository.clearAlertLogs()
        }
    }

    // GPS location tracker launcher
    @SuppressLint("MissingPermission")
    fun loadGpsLocation(context: Context) {
        _gpsStatus.value = "Obteniendo ubicación GPS..."
        
        val hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFine && !hasCoarse) {
            _gpsStatus.value = "Permisos de GPS no otorgados"
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    _gpsStatus.value = null
                    // Create or update a custom location
                    viewModelScope.launch {
                        val name = "Soplo GPS (Mi Cochera)"
                        val newLoc = MonitoredLocation(
                            name = name,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            isPrimary = true,
                            customThreshold = primaryLocation.value?.customThreshold ?: 30.0
                        )
                        val id = repository.saveLocation(newLoc)
                        repository.setLocationAsPrimary(id.toInt())
                    }
                } else {
                    _gpsStatus.value = "No se pudo obtener señal GPS activa. Introduce coordenadas manualmente o busca tu ciudad."
                }
            }.addOnFailureListener {
                _gpsStatus.value = "Fallo al consultar GPS: ${it.localizedMessage}"
            }
        } catch (e: Throwable) {
            _gpsStatus.value = "Error de GPS: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    // Notification builder

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Alertas de Viento"
                val desc = "Se activa cuando las ráfagas superan los límites configurados para las lonas."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = desc
                }
                val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun showSystemNotification(locationName: String, gustSpeed: Double, threshold: Double) {
        try {
            val application = getApplication<Application>()
            val intent = Intent(application, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                application, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(application, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning) // fallback warning system drawable
                .setContentTitle("💨 ¡Peligro de Viento!")
                .setContentText("Rachas de $gustSpeed km/h superan el límite de $threshold km/h en $locationName. ¡Guarda las lonas!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(locationName.hashCode(), notification)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    companion object {
        private const val CHANNEL_ID = "wind_guard_alerts"
    }
}

// Thread-safe singleton for Room AppDatabase to hold connection safely
object RoomDatabaseHolder {
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            val db = androidx.room.Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wind_guard_database"
            ).fallbackToDestructiveMigration().build()
            instance = db
            db
        }
    }
}
