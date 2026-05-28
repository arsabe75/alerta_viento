package com.example.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MonitoredLocation
import com.example.data.local.WindAlertLog
import com.example.data.model.CurrentWind
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WindDashboardScreen(
    viewModel: WindViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe flows reactively
    val locations by viewModel.allLocations.collectAsStateWithLifecycle()
    val primaryLocation by viewModel.primaryLocation.collectAsStateWithLifecycle()
    val alertLogs by viewModel.alertLogs.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val compassAzimuth by viewModel.compassAzimuth.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var locationNameInput by remember { mutableStateOf("") }
    var latitudeInput by remember { mutableStateOf("") }
    var longitudeInput by remember { mutableStateOf("") }
    var showAddressDropdown by remember { mutableStateOf(false) }

    // Request Location Permissions Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.loadGpsLocation(context)
        }
    }

    // Gradient background representing outer atmosphere / wind (Geometric Balance)
    val appBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            GeoBackground,
            GeoBackgroundShift, // Subtle warm hue shift
            GeoBackground
        )
    )

    // Trigger Compass listeners
    DisposableEffect(Unit) {
        viewModel.startCompass()
        onDispose {
            viewModel.stopCompass()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "VientoGuard",
                            fontWeight = FontWeight.SemiBold,
                            color = GeoTextPrimary,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        )
                        val subtitleText = primaryLocation?.name ?: "Cochera Principal"
                        Text(
                            text = subtitleText,
                            color = GeoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            GeometricTheme.isDark = !GeometricTheme.isDark
                            GeometricTheme.isManualOverride = true
                        },
                        modifier = Modifier
                            .testTag("theme_toggle_button")
                            .size(38.dp)
                            .background(GeoAccentSecondary, CircleShape)
                            .border(1.dp, GeoBorderLight, CircleShape)
                    ) {
                        Text(
                            text = if (GeometricTheme.isDark) "☀" else "☾",
                            color = GeoAccentPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = { viewModel.refreshWeather() },
                        modifier = Modifier
                            .testTag("refresh_button")
                            .size(38.dp)
                            .background(GeoAccentSecondary, CircleShape)
                            .border(1.dp, GeoBorderLight, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar clima",
                            tint = GeoAccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeoBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackgroundGradient)
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
            ) {
                // Warning alert banner (displays at top of lists if gust threshold is crossed)
                if (weatherState is WeatherUiState.Success && primaryLocation != null) {
                    val weather = (weatherState as WeatherUiState.Success).response
                    val current = weather.current
                    val threshold = primaryLocation!!.customThreshold

                    if (current != null && current.windGusts >= threshold) {
                        item {
                            WindGaleCriticalWarning(
                                currentGust = current.windGusts,
                                threshold = threshold,
                                locationName = primaryLocation!!.name
                            )
                        }
                    }
                }

                // GPS / Location setup indicator
                if (gpsStatus != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GeoAccentPill),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, GeoBorderLight, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = GeoAccentPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                               )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = gpsStatus ?: "",
                                    color = GeoTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 1. Current Wind Status Dashboard Card
                item {
                    WindStatusCard(
                        weatherUiState = weatherState,
                        primaryLocation = primaryLocation,
                        onUpdateThreshold = { viewModel.updateThreshold(it) },
                        onUseGps = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onManageLocations = { showAddLocationDialog = true }
                    )
                }

                // 2. Instrument: Windy Compass (Visual Direction & Compass Rose)
                item {
                    val currentWind = if (weatherState is WeatherUiState.Success) {
                        (weatherState as WeatherUiState.Success).response.current
                    } else {
                        null
                    }

                    WindCompassInstrument(
                        azimuth = compassAzimuth,
                        windDirection = currentWind?.windDirection ?: 0.0,
                        currentWindGust = currentWind?.windGusts ?: 0.0,
                        currentWindSpeed = currentWind?.windSpeed ?: 0.0
                    )
                }

                // 3. Saved locations list
                item {
                    LocationsController(
                        locations = locations,
                        primaryLocation = primaryLocation,
                        onSelectLocation = { viewModel.makeLocationPrimary(it) },
                        onDeleteLocation = { viewModel.deleteLocation(it) }
                    )
                }

                // 4.的历史/Logs list
                item {
                    AlertLogsWidget(
                        alertLogs = alertLogs,
                        onClearLogs = { viewModel.clearAlerts() }
                    )
                }
            }

            // Dialog configuration
            if (showAddLocationDialog) {
                Dialog(onDismissRequest = {
                    showAddLocationDialog = false
                    searchQuery = ""
                    viewModel.searchAddress("")
                }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .border(1.dp, GeoBorderDark, RoundedCornerShape(24.dp))
                            .testTag("add_location_dialog"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = GeoContainerPrimary)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Añadir Cochera / Zona",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = GeoTextPrimary
                            )

                            // Geocoding search input
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    showAddressDropdown = true
                                    viewModel.searchAddress(it)
                                },
                                label = { Text("Buscar ciudad/población", color = GeoTextSecondary) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GeoTextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = GeoTextPrimary,
                                    unfocusedTextColor = GeoTextPrimary,
                                    focusedBorderColor = GeoAccentPrimary,
                                    unfocusedBorderColor = GeoBorderLight,
                                    focusedLabelColor = GeoAccentPrimary,
                                    unfocusedLabelColor = GeoTextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("location_search_input")
                            )

                            // Dropdown matching results
                            if (showAddressDropdown && (isSearching || searchResults.isNotEmpty())) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                                    colors = CardDefaults.cardColors(containerColor = GeoContainerSecondary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GeoBorderLight)
                                ) {
                                    if (isSearching) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = GeoAccentPrimary, modifier = Modifier.size(24.dp))
                                        }
                                    } else {
                                        LazyColumn {
                                            items(searchResults) { result ->
                                                Text(
                                                    text = "${result.name}, ${result.country ?: ""} (${result.admin1 ?: ""})",
                                                    color = GeoTextPrimary,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.addSearchedLocation(result)
                                                            showAddressDropdown = false
                                                            showAddLocationDialog = false
                                                            searchQuery = ""
                                                        }
                                                        .padding(12.dp)
                                                )
                                                HorizontalDivider(color = GeoBorderLight)
                                            }
                                        }
                                    }
                                }
                            }

                            // Manual Coordinates divider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = GeoBorderLight)
                                Text(" O MANUAL ", color = GeoTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                HorizontalDivider(modifier = Modifier.weight(1f), color = GeoBorderLight)
                            }

                            OutlinedTextField(
                                value = locationNameInput,
                                onValueChange = { locationNameInput = it },
                                label = { Text("Nombre (ej: Cochera Lado Norte)", color = GeoTextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = GeoTextPrimary,
                                    unfocusedTextColor = GeoTextPrimary,
                                    focusedBorderColor = GeoAccentPrimary,
                                    unfocusedBorderColor = GeoBorderLight,
                                    focusedLabelColor = GeoAccentPrimary,
                                    unfocusedLabelColor = GeoTextSecondary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("manual_name_input")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = latitudeInput,
                                    onValueChange = { latitudeInput = it },
                                    label = { Text("Latitud", color = GeoTextSecondary) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = GeoTextPrimary,
                                        unfocusedTextColor = GeoTextPrimary,
                                        focusedBorderColor = GeoAccentPrimary,
                                        unfocusedBorderColor = GeoBorderLight,
                                        focusedLabelColor = GeoAccentPrimary,
                                        unfocusedLabelColor = GeoTextSecondary
                                    ),
                                    modifier = Modifier.weight(1f).testTag("manual_lat_input")
                                )
                                OutlinedTextField(
                                    value = longitudeInput,
                                    onValueChange = { longitudeInput = it },
                                    label = { Text("Longitud", color = GeoTextSecondary) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = GeoTextPrimary,
                                        unfocusedTextColor = GeoTextPrimary,
                                        focusedBorderColor = GeoAccentPrimary,
                                        unfocusedBorderColor = GeoBorderLight,
                                        focusedLabelColor = GeoAccentPrimary,
                                        unfocusedLabelColor = GeoTextSecondary
                                    ),
                                    modifier = Modifier.weight(1f).testTag("manual_lon_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddLocationDialog = false }) {
                                    Text("Cancelar", color = GeoTextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val lat = latitudeInput.toDoubleOrNull()
                                        val lon = longitudeInput.toDoubleOrNull()
                                        if (locationNameInput.isNotBlank() && lat != null && lon != null) {
                                            viewModel.addManualLocation(locationNameInput, lat, lon)
                                            locationNameInput = ""
                                            latitudeInput = ""
                                            longitudeInput = ""
                                            showAddLocationDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GeoAccentPrimary),
                                    modifier = Modifier.testTag("save_manual_location_button")
                                ) {
                                    Text("Añadir", color = if (GeometricTheme.isDark) Color(0xFF1D1B20) else Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 1. Critical Warning Banner
@Composable
fun WindGaleCriticalWarning(
    currentGust: Double,
    threshold: Double,
    locationName: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = GeoWarningRed.copy(alpha = alpha)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, GeoWarningRedText.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .testTag("critical_wind_warning")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = GeoWarningRedText,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "⚠️ ¡ALERTA DE SEGURIDAD!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GeoWarningRedText
                )
                Text(
                    text = "Ráfagas en $locationName alcanzan los $currentGust km/h (límite lona: $threshold km/h). Guarda las lonas para evitar destrozos.",
                    fontSize = 12.sp,
                    color = GeoTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// 2. Main Wind Data Dashboard Wrapper
@Composable
fun WindStatusCard(
    weatherUiState: WeatherUiState,
    primaryLocation: MonitoredLocation?,
    onUpdateThreshold: (Double) -> Unit,
    onUseGps: () -> Unit,
    onManageLocations: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GeoContainerPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorderLight, RoundedCornerShape(24.dp))
            .testTag("wind_status_card")
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Location Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "COCHERA MONITOREADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeoAccentPrimary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = primaryLocation?.name ?: "Sin ubicación",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = primaryLocation?.let { "Lat: %.4f | Lon: %.4f".format(it.latitude, it.longitude) } ?: "",
                        fontSize = 12.sp,
                        color = GeoTextSecondary
                    )
                }

                // Small quick action buttons (Geometric Balance styled)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onUseGps,
                        modifier = Modifier
                            .background(GeoAccentSecondary, CircleShape)
                            .border(1.dp, GeoBorderLight, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Monitorear GPS actual",
                            tint = GeoAccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onManageLocations,
                        modifier = Modifier
                            .background(GeoAccentSecondary, CircleShape)
                            .border(1.dp, GeoBorderLight, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir ubicaciones",
                            tint = GeoAccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = GeoBorderLight)

            // Dynamic States representation
            when (weatherUiState) {
                is WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GeoAccentPrimary)
                    }
                }
                is WeatherUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weatherUiState.error,
                            color = GeoWarningRedText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is WeatherUiState.Success -> {
                    val current = weatherUiState.response.current
                    if (current != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Column actual speed
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(GeoContainerSecondary, RoundedCornerShape(16.dp))
                                    .border(1.dp, GeoBorderLight, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = GeoAccentPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Viento actual", fontSize = 11.sp, color = GeoTextSecondary, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${current.windSpeed}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoTextPrimary
                                )
                                Text("km/h", fontSize = 11.sp, color = GeoTextSecondary)
                            }

                            // Column Max Gusts
                            val isCrit = current.windGusts >= (primaryLocation?.customThreshold ?: 30.0)
                            val testColor = if (isCrit) GeoWarningRedText else GeoAccentPrimary
                            val backCol = if (isCrit) GeoWarningRed else GeoContainerSecondary
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(backCol, RoundedCornerShape(16.dp))
                                    .border(1.dp, if (isCrit) GeoWarningRedText.copy(alpha = 0.3f) else GeoBorderLight, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = testColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rachas ráfagas", fontSize = 11.sp, color = GeoTextSecondary, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${current.windGusts}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = testColor
                                )
                                Text("km/h", fontSize = 11.sp, color = GeoTextSecondary)
                            }
                        }
                    }
                }
                WeatherUiState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay datos de viento disponibles", color = GeoTextSecondary, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(color = GeoBorderLight)

            // Threshold slider configuration
            primaryLocation?.let { loc ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GeoContainerPrimary, RoundedCornerShape(16.dp))
                        .border(1.dp, GeoBorderLight, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Límite tolerado por mis lonas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeoTextPrimary
                            )
                            Text(
                                text = "El sistema alertará al superar este límite",
                                fontSize = 10.sp,
                                color = GeoTextSecondary
                            )
                        }
                        Text(
                            text = "${loc.customThreshold.toInt()} km/h",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoAccentPrimary
                        )
                    }

                    Slider(
                        value = loc.customThreshold.toFloat(),
                        onValueChange = { onUpdateThreshold(it.toDouble()) },
                        valueRange = 10f..80f,
                        steps = 13, // 5km/h steps
                        colors = SliderDefaults.colors(
                            thumbColor = GeoAccentPrimary,
                            activeTrackColor = GeoAccentPrimary,
                            inactiveTrackColor = GeoBorderLight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("threshold_slider")
                    )
                }
            }
        }
    }
}

// 3. Dynamic Instrument: Combined Real-time Compass & Wind direction Pointer
@Composable
fun WindCompassInstrument(
    azimuth: Float,            // Phone rotation bearing
    windDirection: Double,     // Weather direction angle
    currentWindGust: Double,
    currentWindSpeed: Double
) {
    // Smooth transitions for rotation sensors
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "azimuth"
    )

    // Animated wind direction for arrow changes
    val animatedWindDirection by animateFloatAsState(
        targetValue = windDirection.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "wind_dir"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GeoContainerPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorderDark, RoundedCornerShape(24.dp))
            .testTag("wind_compass_instrument")
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "BRÚJULA DIGITAL Y CORRIENTES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GeoAccentPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Apunta el teléfono hacia el frente para alinear con el norte real",
                    fontSize = 10.sp,
                    color = GeoTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // High Fidelity Custom Styled Compass Instrument Canvas (Geometric Balance)
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .background(GeoContainerSecondary, CircleShape)
                    .border(2.dp, GeoBorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle dashed/light ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = GeoBorderLight.copy(alpha = 0.6f),
                        radius = size.minDimension / 2.3f,
                        style = Stroke(width = 1f)
                    )
                }

                // Rotating Compass Dial
                // The physical compass points north, so we rotate by -azimuth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-animatedAzimuth),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.minDimension / 2 - 20f

                        // Draw ticks for compass rose
                        for (i in 0 until 360 step 15) {
                            val angleRad = Math.toRadians(i.toDouble() - 90)
                            val innerMultiplier = if (i % 90 == 0) 0.82f else if (i % 30 == 0) 0.88f else 0.92f
                            val strokeWidthVal = if (i % 90 == 0) 3.5f else if (i % 30 == 0) 2f else 1f
                            val colorVal = if (i % 90 == 0) GeoAccentPrimary else GeoBorderDark

                            val startX = centerX + radius * innerMultiplier * cos(angleRad).toFloat()
                            val startY = centerY + radius * innerMultiplier * sin(angleRad).toFloat()
                            val endX = centerX + radius * 0.96f * cos(angleRad).toFloat()
                            val endY = centerY + radius * 0.96f * sin(angleRad).toFloat()

                            drawLine(
                                color = colorVal,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = strokeWidthVal
                            )
                        }

                        // North subtle visual highlight
                        val angleNorth = Math.toRadians(-90.0)
                        drawLine(
                            color = GeoAccentPrimary,
                            start = Offset(centerX, centerY - radius * 0.5f),
                            end = Offset(centerX, centerY - radius * 0.82f),
                            strokeWidth = 4f
                        )
                    }

                    // Ticks text indicators
                    CompassCardinalLabel(text = "N", degrees = 0f, offsetRadius = 75)
                    CompassCardinalLabel(text = "E", degrees = 90f, offsetRadius = 75)
                    CompassCardinalLabel(text = "S", degrees = 180f, offsetRadius = 75)
                    CompassCardinalLabel(text = "O", degrees = 270f, offsetRadius = 75) // Oeste
                }

                // Superimposed independent Rotating WIND DIRECTION Indicator
                // Represents the exact vector current directed.
                // We rotate it by (windDirection - animatedAzimuth) so it reflects real physical direction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(animatedWindDirection - animatedAzimuth),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.minDimension / 2 - 25f

                        // Draw wind arrow coming from the direction
                        // Arrows points TO the center, indicating wind entry
                        val path = Path().apply {
                            // Arrow Head pointing to center
                            moveTo(centerX, centerY - 28f)
                            lineTo(centerX - 12f, centerY - 28f - 18f)
                            lineTo(centerX - 4f, centerY - 28f - 14f)
                            lineTo(centerX - 4f, centerY - radius * 0.75f)
                            lineTo(centerX + 4f, centerY - radius * 0.75f)
                            lineTo(centerX + 4f, centerY - 28f - 14f)
                            lineTo(centerX + 12f, centerY - 28f - 18f)
                            close()
                        }

                        // Draw vibrant solid pointing wind arrow
                        drawPath(
                            path = path,
                            color = GeoAccentPrimary
                        )

                        // Outline of the arrow to make it pop
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(width = 1.5f)
                        )

                        // Streamlines on the tail to signify active dynamic breeze
                        drawLine(
                            color = GeoAccentPrimary.copy(alpha = 0.5f),
                            start = Offset(centerX - 9f, centerY - radius * 0.8f),
                            end = Offset(centerX - 9f, centerY - radius * 0.9f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = GeoAccentPrimary.copy(alpha = 0.5f),
                            start = Offset(centerX + 9f, centerY - radius * 0.8f),
                            end = Offset(centerX + 9f, centerY - radius * 0.9f),
                            strokeWidth = 2f
                        )
                    }
                }

                // Glass Center Hub matching the HTML exactly
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .background(GeoBackground, CircleShape)
                        .border(1.dp, GeoAccentPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dirText = _azimuthToText(windDirection.toFloat())
                        Text(
                            text = dirText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoTextPrimary
                        )
                        Text(
                            text = "${windDirection.toInt()}°",
                            fontSize = 10.sp,
                            color = GeoTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Real-time telemetry summaries
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TU RUMBO", fontSize = 10.sp, color = GeoTextSecondary, fontWeight = FontWeight.Bold)
                    Text("${animatedAzimuth.toInt()}° ${_azimuthToText(animatedAzimuth)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GeoTextPrimary)
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(GeoBorderLight))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PROVENIENCIA VIENTO", fontSize = 10.sp, color = GeoTextSecondary, fontWeight = FontWeight.Bold)
                    Text("${windDirection.toInt()}° ${_azimuthToText(windDirection.toFloat())}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GeoAccentPrimary)
                }
            }
        }
    }
}

@Composable
fun CompassCardinalLabel(text: String, degrees: Float, offsetRadius: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .rotate(degrees),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            color = if (text == "N") GeoAccentPrimary else GeoTextSecondary,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .rotate(-degrees) // Inverse rotate text itself so letters stay upright
        )
    }
}

// Translate raw bearings to letters
private fun _azimuthToText(az: Float): String {
    val norm = (az % 360 + 360) % 360
    return when {
        norm >= 337.5 || norm < 22.5 -> "N"
        norm >= 22.5 && norm < 67.5 -> "NE"
        norm >= 67.5 && norm < 112.5 -> "E"
        norm >= 112.5 && norm < 157.5 -> "SE"
        norm >= 157.5 && norm < 202.5 -> "S"
        norm >= 202.5 && norm < 247.5 -> "SO" // Suroeste
        norm >= 247.5 && norm < 292.5 -> "O"  // Oeste
        else -> "NO" // Noroeste
    }
}

// 4. Saved Locations management component
@Composable
fun LocationsController(
    locations: List<MonitoredLocation>,
    primaryLocation: MonitoredLocation?,
    onSelectLocation: (Int) -> Unit,
    onDeleteLocation: (MonitoredLocation) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GeoContainerPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorderDark, RoundedCornerShape(24.dp))
            .testTag("locations_controller")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "COCHERAS Y ZONAS REGISTRADAS",
                color = GeoAccentPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )

            locations.forEach { loc ->
                val isSelected = loc.id == primaryLocation?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) GeoAccentPill else GeoContainerSecondary)
                        .clickable { onSelectLocation(loc.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isSelected) GeoAccentPrimary else GeoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = loc.name,
                                fontWeight = FontWeight.Bold,
                                color = GeoTextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Límite: ${loc.customThreshold.toInt()} km/h | Lat: ${"%.3f".format(loc.latitude)} Lon: ${"%.3f".format(loc.longitude)}",
                                color = GeoTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Delete button, prevented if it's the last remaining location
                    if (locations.size > 1) {
                        IconButton(
                            onClick = { onDeleteLocation(loc) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar de la lista",
                                tint = GeoWarningRedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 5. Historical Critical wind Logs list
@Composable
fun AlertLogsWidget(
    alertLogs: List<WindAlertLog>,
    onClearLogs: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GeoContainerPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorderDark, RoundedCornerShape(24.dp))
            .testTag("alert_logs_widget")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORIAL DE ALERTAS LOCALES",
                    color = GeoAccentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                if (alertLogs.isNotEmpty()) {
                    Text(
                        text = "Limpiar Todo",
                        color = GeoWarningRedText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClearLogs() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("clear_logs_button")
                    )
                }
            }

            if (alertLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin ráfagas críticas registradas. ¡Tus lonas están seguras!",
                        color = GeoTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alertLogs.take(8).forEach { log ->
                        val dateText = android.text.format.DateFormat.format("dd/MM, HH:mm", log.timestamp).toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GeoContainerSecondary, RoundedCornerShape(12.dp))
                                .border(1.dp, GeoBorderLight, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(GeoWarningRedText, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Rachas de ${log.windGustSpeed} km/h en ${log.locationName}",
                                        color = GeoTextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Lona resiste ${log.threshold} km/h | Compás v: ${log.windDirection.toInt()}°",
                                        color = GeoTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Text(
                                text = dateText,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
