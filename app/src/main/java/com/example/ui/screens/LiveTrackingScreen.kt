package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.tracker.HaversineCalculator
import com.example.domain.tracker.TrackingUiState
import com.example.ui.components.LeafletMapView
import com.example.ui.theme.*

@Composable
fun LiveTrackingScreen(
    trackingState: TrackingUiState,
    onStartTracking: (isSimulation: Boolean) -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit,
    onFinishTracking: () -> Unit,
    onCancelTracking: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fineGranted || coarseGranted
        if (hasLocationPermission && !trackingState.isTracking) {
            onStartTracking(trackingState.isSimulationMode)
        }
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showFinishConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back button while active tracking
    BackHandler {
        if (trackingState.isTracking) {
            showCancelDialog = true
        } else {
            onBackToHome()
        }
    }

    // Launch permission request if not granted and not in simulation
    LaunchedEffect(Unit) {
        if (!hasLocationPermission && !trackingState.isSimulationMode) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (trackingState.isTracking) {
                            showCancelDialog = true
                        } else {
                            onBackToHome()
                        }
                    },
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Keluar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // GPS Status Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (trackingState.isGpsWeak) StatusWarning.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (trackingState.isGpsWeak) StatusWarning else LimeGreenPrimary.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (trackingState.isGpsWeak) StatusWarning else LimeGreenPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trackingState.gpsStatusMessage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Spacer to balance TopBar
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!hasLocationPermission && !trackingState.isSimulationMode) {
            // Permission Request State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Izin Lokasi GPS Diperlukan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "LariKita membutuhkan akses lokasi untuk menghitung jarak, pace, dan menggambar rute lari secara akurat di peta.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("request_permission_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreenPrimary,
                                contentColor = Color(0xFF09140D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Izinkan Akses GPS", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { onStartTracking(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gunakan Mode Simulasi Demo")
                        }
                    }
                }
            }
        } else if (!trackingState.isSimulationMode && run {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                lm != null && !lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            }) {
            // GPS Hardware Disabled State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Layanan Lokasi (GPS) Mati",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GPS pada perangkat Anda belum diaktifkan. Silakan aktifkan GPS agar LariKita dapat melacak koordinat lari secara akurat.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreenPrimary,
                                contentColor = Color(0xFF09140D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buka Pengaturan Lokasi", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { onStartTracking(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gunakan Mode Simulasi Demo")
                        }
                    }
                }
            }
        } else {
            // Live Tracking Active UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Leaflet Map Section (Elevated, rounded, interactive)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("live_map_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    LeafletMapView(
                        modifier = Modifier.fillMaxSize(),
                        points = trackingState.points,
                        currentPoint = trackingState.currentPoint,
                        isLiveTracking = true,
                        showEndMarker = false
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Distance Display (Biggest and clearest as mandated in Section D)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "JARAK TEMPUH",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "%.2f".format(trackingState.distanceMeters / 1000.0),
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Black,
                                color = LimeGreenPrimary,
                                modifier = Modifier.testTag("distance_display")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KM",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        // Duration Timer
                        Text(
                            text = HaversineCalculator.formatDuration(trackingState.durationSeconds),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("duration_timer")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary Metrics Row: Pace, Speed, Calories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "PACE",
                        value = trackingState.currentPace,
                        unit = "/km",
                        accentColor = PaceMetricColor
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "KECEPATAN",
                        value = "%.1f".format(trackingState.currentSpeedKmH),
                        unit = "km/j",
                        accentColor = SpeedMetricColor
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "KALORI",
                        value = "${trackingState.calories}",
                        unit = "kcal",
                        accentColor = CalorieMetricColor
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Controls Row: Pause / Resume / Finish
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!trackingState.isPaused) {
                        // PAUSE BUTTON
                        Button(
                            onClick = onPauseTracking,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("pause_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusWarning,
                                contentColor = Color(0xFF09140D)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("JEDA", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    } else {
                        // RESUME BUTTON
                        Button(
                            onClick = onResumeTracking,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("resume_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreenPrimary,
                                contentColor = Color(0xFF09140D)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("LANJUTKAN", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }

                        // FINISH BUTTON (Appears when paused)
                        Button(
                            onClick = {
                                if (trackingState.distanceMeters < 50.0 && trackingState.durationSeconds < 10) {
                                    showFinishConfirmDialog = true
                                } else {
                                    onFinishTracking()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("finish_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusDanger,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELESAI", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Cancel / Exit Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning) },
            title = { Text("Batalkan Aktivitas?") },
            text = { Text("Aktivitas yang sedang berjalan akan dibatalkan dan data rekaman tidak akan disimpan.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelTracking()
                    }
                ) {
                    Text("Ya, Batalkan", color = StatusDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Lanjutkan Lari")
                }
            }
        )
    }

    // Short run finish confirm
    if (showFinishConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmDialog = false },
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
            title = { Text("Akhiri Aktivitas Singkat?") },
            text = { Text("Jarak atau durasi masih sangat pendek. Apakah kamu tetap ingin menyimpan sesi ini?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirmDialog = false
                        onFinishTracking()
                    }
                ) {
                    Text("Simpan Saja", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmDialog = false }) {
                    Text("Kembali")
                }
            }
        )
    }
}

@Composable
fun MetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
            Text(
                text = unit,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
