package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.RunEntity
import com.example.domain.tracker.HaversineCalculator
import com.example.ui.components.LeafletMapView
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(
    run: RunEntity?,
    onSaveRun: (title: String, notes: String) -> Unit,
    onViewHistory: () -> Unit,
    onBackHome: () -> Unit
) {
    if (run == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tidak ada data lari yang ditampilkan")
        }
        return
    }

    var titleText by remember { mutableStateOf(run.title) }
    var notesText by remember { mutableStateOf(run.notes) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showTrialShareNoticeDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTrial = remember { com.example.domain.config.EditionConfig.isTrial(context) }
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Hasil Lari Hebat!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Distance & Badge Card
            item {
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dateFormat.format(Date(run.startTime)),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${timeFormat.format(Date(run.startTime))} - ${timeFormat.format(Date(run.endTime))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "TOTAL JARAK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.2f".format(run.distanceKm),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = LimeGreenPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "KM",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Sync Status Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "✓ Tersimpan di Perangkat (Offline-Ready)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Route Map Preview
            item {
                Text(
                    text = "Peta Rute Lari",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))

                val points = remember(run.gpsPointsJson) { run.getGpsPoints() }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LeafletMapView(
                        modifier = Modifier.fillMaxSize(),
                        points = points,
                        currentPoint = points.lastOrNull(),
                        isLiveTracking = false,
                        showEndMarker = true
                    )
                }
            }

            // Detailed Metrics Grid (Duration, Pace, Speeds, Calories)
            item {
                Text(
                    text = "Statistik Lengkap",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "TOTAL DURASI",
                        value = HaversineCalculator.formatDuration(run.durationSeconds),
                        unit = "waktu aktif",
                        accentColor = MaterialTheme.colorScheme.onSurface
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "RATA-RATA PACE",
                        value = run.averagePace,
                        unit = "/km",
                        accentColor = PaceMetricColor
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "KECEPATAN RATA-RATA",
                        value = "%.1f".format(run.averageSpeed),
                        unit = "km/jam",
                        accentColor = SpeedMetricColor
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "KECEPATAN MAKS",
                        value = "%.1f".format(run.maxSpeed),
                        unit = "km/jam",
                        accentColor = LimeGreenPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = CalorieMetricColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Estimasi Kalori Terbakar",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${run.calories} kcal",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Edit Title & Notes Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Catatan Aktivitas",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            label = { Text("Nama Aktivitas") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            label = { Text("Catatan atau Evaluasi Lari") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // Action Buttons
            item {
                // Share Achievement Button (Camera Selfie, Card with Route, Location Privacy)
                if (!isTrial) {
                    FilledTonalButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("summary_share_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = LimeGreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BAGIKAN PENCAPAIAN / FOTO SELFIE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showTrialShareNoticeDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("summary_share_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LimeGreenDark
                        )
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = LimeGreenDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BAGIKAN PENCAPAIAN (KHUSUS FULL VERSION)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onSaveRun(titleText, notesText)
                        onBackHome()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_run_summary_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SIMPAN & SELESAI", fontWeight = FontWeight.Black, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewHistory,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lihat Riwayat", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onBackHome,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Kembali ke Beranda")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showShareDialog) {
        com.example.ui.components.RunShareDialog(
            run = run.copy(title = titleText, notes = notesText),
            onDismiss = { showShareDialog = false }
        )
    }

    // Friendly Upgrade to Full Version Dialog (Calm colors, NO RED)
    if (showTrialShareNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showTrialShareNoticeDialog = false },
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LimeGreenPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = LimeGreenDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Fitur Berbagi Khusus Full Version",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fitur berbagi rute, pencapaian lari, dan kartu foto ke media sosial hanya tersedia di Full Version LariKita.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Silakan perbarui ke Full Version untuk menikmati fitur berbagi tanpa batas bersama komunitas pelari Anda.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTrialShareNoticeDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mengerti", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
