package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.RunEntity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.domain.tracker.HaversineCalculator
import com.example.ui.components.LeafletMapView
import com.example.ui.theme.*
import com.example.ui.viewmodel.HistoryFilter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    runs: List<RunEntity>,
    currentFilter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onDeleteRun: (String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    var selectedRunForDetail by remember { mutableStateOf<RunEntity?>(null) }
    var runToDelete by remember { mutableStateOf<RunEntity?>(null) }
    var runToShare by remember { mutableStateOf<RunEntity?>(null) }

    // Filter runs
    val filteredRuns = remember(runs, currentFilter) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (currentFilter) {
            HistoryFilter.SEMUA -> runs
            HistoryFilter.MINGGU_INI -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfWeek = cal.timeInMillis
                runs.filter { it.startTime >= startOfWeek }
            }
            HistoryFilter.BULAN_INI -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startOfMonth = cal.timeInMillis
                runs.filter { it.startTime >= startOfMonth }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Riwayat Lari",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentFilter == HistoryFilter.SEMUA,
                        onClick = { onFilterChange(HistoryFilter.SEMUA) },
                        label = { Text("Semua (${runs.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LimeGreenPrimary,
                            selectedLabelColor = Color(0xFF09140D)
                        )
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.MINGGU_INI,
                        onClick = { onFilterChange(HistoryFilter.MINGGU_INI) },
                        label = { Text("Minggu Ini") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LimeGreenPrimary,
                            selectedLabelColor = Color(0xFF09140D)
                        )
                    )
                    FilterChip(
                        selected = currentFilter == HistoryFilter.BULAN_INI,
                        onClick = { onFilterChange(HistoryFilter.BULAN_INI) },
                        label = { Text("Bulan Ini") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LimeGreenPrimary,
                            selectedLabelColor = Color(0xFF09140D)
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (filteredRuns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tidak Ada Riwayat Aktivitas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Belum ada catatan lari untuk filter ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(filteredRuns, key = { it.id }) { run ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRunForDetail = run }
                            .testTag("history_item_${run.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = run.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${dateFormat.format(Date(run.startTime))} • ${timeFormat.format(Date(run.startTime))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { runToShare = run },
                                        modifier = Modifier.size(36.dp).testTag("share_run_${run.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Bagikan",
                                            tint = LimeGreenPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { runToDelete = run },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricColumn(
                                    label = "Jarak",
                                    value = "%.2f km".format(run.distanceKm),
                                    accentColor = LimeGreenPrimary
                                )
                                MetricColumn(
                                    label = "Durasi",
                                    value = HaversineCalculator.formatDuration(run.durationSeconds),
                                    accentColor = MaterialTheme.colorScheme.onSurface
                                )
                                MetricColumn(
                                    label = "Pace",
                                    value = run.averagePace,
                                    accentColor = PaceMetricColor
                                )
                                MetricColumn(
                                    label = "Kalori",
                                    value = "${run.calories} kcal",
                                    accentColor = CalorieMetricColor
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    // Detail Dialog with Route Map
    if (selectedRunForDetail != null) {
        val run = selectedRunForDetail!!
        val points = remember(run.gpsPointsJson) { run.getGpsPoints() }

        AlertDialog(
            onDismissRequest = { selectedRunForDetail = null },
            modifier = Modifier.fillMaxWidth(),
            title = {
                Column {
                    Text(text = run.title, fontWeight = FontWeight.Bold)
                    Text(
                        text = dateFormat.format(Date(run.startTime)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Map preview
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LeafletMapView(
                            modifier = Modifier.fillMaxSize(),
                            points = points,
                            currentPoint = points.lastOrNull(),
                            isLiveTracking = false,
                            showEndMarker = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricColumn(label = "Jarak", value = "%.2f km".format(run.distanceKm), accentColor = LimeGreenPrimary)
                        MetricColumn(label = "Durasi", value = HaversineCalculator.formatDuration(run.durationSeconds), accentColor = MaterialTheme.colorScheme.onSurface)
                        MetricColumn(label = "Avg Pace", value = run.averagePace, accentColor = PaceMetricColor)
                        MetricColumn(label = "Kecepatan", value = "%.1f km/j".format(run.averageSpeed), accentColor = SpeedMetricColor)
                    }

                    if (run.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Catatan: ${run.notes}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            val r = selectedRunForDetail
                            selectedRunForDetail = null
                            runToShare = r
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan", fontWeight = FontWeight.Bold, color = LimeGreenPrimary)
                    }
                    TextButton(onClick = { selectedRunForDetail = null }) {
                        Text("Tutup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Share Options Dialog (Image Card vs Text)
    if (runToShare != null) {
        val run = runToShare!!
        val dateFormatted = dateFormat.format(Date(run.startTime))
        val timeFormatted = timeFormat.format(Date(run.startTime))

        AlertDialog(
            onDismissRequest = { runToShare = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = LimeGreenPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Bagikan Aktivitas Lari",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pilih format untuk membagikan pencapaian larimu ke media sosial atau teman:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preview Card for Run Stat Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F1722),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LimeGreenPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = LimeGreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LARIKITA",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = LimeGreenPrimary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "$dateFormatted • $timeFormatted",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = run.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%.2f".format(Locale.US, run.distanceKm),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp,
                                    color = LimeGreenPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Durasi", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        HaversineCalculator.formatDuration(run.durationSeconds),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column {
                                    Text("Pace", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        run.averagePace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PaceMetricColor
                                    )
                                }
                                Column {
                                    Text("Kalori", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "${run.calories} kcal",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CalorieMetricColor
                                    )
                                }
                                Column {
                                    Text("Kec. Avg", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        "%.1f km/j".format(Locale.US, run.averageSpeed),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpeedMetricColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option 1: Share as Image Card
                    Button(
                        onClick = {
                            com.example.ui.util.RunShareHelper.shareRunAsImage(context, run)
                            runToShare = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("share_image_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LimeGreenPrimary,
                            contentColor = Color(0xFF09140D)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bagikan Kartu Gambar",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Share as Text
                    OutlinedButton(
                        onClick = {
                            com.example.ui.util.RunShareHelper.shareRunAsText(context, run)
                            runToShare = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_text_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bagikan Format Teks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { runToShare = null },
                    modifier = Modifier.testTag("cancel_share_button")
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (runToDelete != null) {
        AlertDialog(
            onDismissRequest = { runToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusDanger) },
            title = { Text("Hapus Catatan Lari?") },
            text = { Text("Apakah kamu yakin ingin menghapus '${runToDelete?.title}'? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = runToDelete?.id
                        if (id != null) onDeleteRun(id)
                        runToDelete = null
                    }
                ) {
                    Text("Ya, Hapus", color = StatusDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { runToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}
