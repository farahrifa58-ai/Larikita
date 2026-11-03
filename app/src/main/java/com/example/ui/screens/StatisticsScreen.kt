package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.RunEntity
import com.example.domain.tracker.HaversineCalculator
import com.example.ui.theme.*
import com.example.ui.viewmodel.AllTimeStats
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class StatsPeriod {
    MINGGU,
    BULAN,
    SEMUA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MainViewModel,
    runs: List<RunEntity>
) {
    var period by remember { mutableStateOf(StatsPeriod.MINGGU) }
    val allTimeStats = remember(runs) { viewModel.calculateAllTimeStats(runs) }

    // Filter runs for chart display based on selected period
    val periodRuns = remember(runs, period) {
        val cal = Calendar.getInstance()
        when (period) {
            StatsPeriod.MINGGU -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                runs.filter { it.startTime >= cal.timeInMillis }
            }
            StatsPeriod.BULAN -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                runs.filter { it.startTime >= cal.timeInMillis }
            }
            StatsPeriod.SEMUA -> runs
        }
    }

    val periodDistanceKm = remember(periodRuns) {
        periodRuns.sumOf { it.distanceMeters } / 1000.0
    }
    val periodDurationSec = remember(periodRuns) {
        periodRuns.sumOf { it.durationSeconds }
    }
    val periodAvgPace = remember(periodDistanceKm, periodDurationSec) {
        val paceSec = HaversineCalculator.calculatePaceSecondsPerKm(periodDurationSec, periodDistanceKm * 1000.0)
        HaversineCalculator.formatPace(paceSec)
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
                    text = "Statistik Lari",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Period Selector
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = period == StatsPeriod.MINGGU,
                        onClick = { period = StatsPeriod.MINGGU },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = LimeGreenPrimary,
                            activeContentColor = Color(0xFF09140D)
                        )
                    ) {
                        Text("Minggu", fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = period == StatsPeriod.BULAN,
                        onClick = { period = StatsPeriod.BULAN },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = LimeGreenPrimary,
                            activeContentColor = Color(0xFF09140D)
                        )
                    ) {
                        Text("Bulan", fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = period == StatsPeriod.SEMUA,
                        onClick = { period = StatsPeriod.SEMUA },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = LimeGreenPrimary,
                            activeContentColor = Color(0xFF09140D)
                        )
                    ) {
                        Text("Semua", fontWeight = FontWeight.Bold)
                    }
                }
            }
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
            // Period Primary Distance Summary
            item {
                Spacer(modifier = Modifier.height(4.dp))
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
                            text = when (period) {
                                StatsPeriod.MINGGU -> "JARAK MINGGU INI"
                                StatsPeriod.BULAN -> "JARAK BULAN INI"
                                StatsPeriod.SEMUA -> "TOTAL JARAK KESELURUHAN"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.2f".format(periodDistanceKm),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = LimeGreenPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "KM",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Aktivitas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${periodRuns.size} sesi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Durasi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(HaversineCalculator.formatDuration(periodDurationSec), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Rata-rata Pace", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(periodAvgPace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PaceMetricColor)
                            }
                        }
                    }
                }
            }

            // Interactive Activity Progression Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Grafik Riwayat Jarak Lari",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${periodRuns.size} Sesi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (periodRuns.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Belum ada data untuk periode ini",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val maxKm = (periodRuns.maxOfOrNull { it.distanceKm } ?: 1.0).coerceAtLeast(1.0)
                            val displayRuns = periodRuns.takeLast(7) // show last 7 runs
                            val itemDateFormat = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                displayRuns.forEach { r ->
                                    val barFraction = (r.distanceKm / maxKm).toFloat().coerceIn(0.08f, 1f)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "%.1f".format(r.distanceKm),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LimeGreenPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .fillMaxHeight(barFraction)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(LimeGreenPrimary)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = itemDateFormat.format(Date(r.startTime)),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hall of Fame / Records Cards
            item {
                Text(
                    text = "Rekor & Pencapaian Pribadi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecordCard(
                        modifier = Modifier.weight(1f),
                        title = "Jarak Terjauh",
                        value = "%.2f km".format(allTimeStats.farthestDistanceKm),
                        icon = Icons.Default.EmojiEvents,
                        accentColor = SpeedMetricColor
                    )
                    RecordCard(
                        modifier = Modifier.weight(1f),
                        title = "Pace Terbaik",
                        value = allTimeStats.fastestPace,
                        icon = Icons.Default.Bolt,
                        accentColor = LimeGreenPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecordCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Kalori",
                        value = "${allTimeStats.totalCalories} kcal",
                        icon = Icons.Default.LocalFireDepartment,
                        accentColor = CalorieMetricColor
                    )
                    RecordCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Waktu Lari",
                        value = HaversineCalculator.formatDuration(allTimeStats.totalDurationSeconds),
                        icon = Icons.Default.AccessTime,
                        accentColor = PaceMetricColor
                    )
                }
            }

            // Overall Summary Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Rangkuman Sepanjang Waktu",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        SummaryRow(label = "Total Jarak", value = "%.2f km".format(allTimeStats.totalDistanceKm))
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        SummaryRow(label = "Total Sesi Selesai", value = "${allTimeStats.totalRunsCount} sesi")
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        SummaryRow(label = "Pace Rata-Rata Kumulatif", value = allTimeStats.averagePace)
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        SummaryRow(label = "Penyimpanan Database", value = "Room SQLite (Offline-First)")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun RecordCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
