package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.theme.*
import com.example.domain.sync.SyncState
import com.example.ui.viewmodel.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    userProfile: UserProfile,
    onToggleDarkMode: (Boolean) -> Unit,
    onPopulateSample: () -> Unit,
    onClearData: () -> Unit,
    onManualSync: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val syncTimeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")) }

    var showTargetEditDialog by remember { mutableStateOf(false) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showFirebaseGuideDialog by remember { mutableStateOf(false) }
    var showLogoutSwitchDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profil & Pengaturan",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
            // Profile Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(68.dp),
                                shape = CircleShape,
                                color = LimeGreenPrimary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = Color(0xFF09140D),
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userProfile.email,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Akun Aktif • Offline-Ready",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { showProfileEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profil")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showLogoutSwitchDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ganti Akun / Masuk", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Target Lari Card (Daily, Weekly, Monthly)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = null,
                                    tint = LimeGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Target Jarak Lari",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = { showTargetEditDialog = true }) {
                                Text("Ubah", fontWeight = FontWeight.Bold, color = LimeGreenPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TargetPill(label = "Target Harian", value = "%.0f km".format(userProfile.dailyTargetKm))
                            TargetPill(label = "Target Mingguan", value = "%.0f km".format(userProfile.weeklyTargetKm))
                            TargetPill(label = "Target Bulanan", value = "%.0f km".format(userProfile.monthlyTargetKm))
                        }
                    }
                }
            }

            // Tampilan & Satuan
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pengaturan Tampilan & Satuan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dark Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Mode Gelap (Sporty Navy)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Hemat baterai & kontras tinggi saat lari", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = userProfile.isDarkMode,
                                onCheckedChange = onToggleDarkMode
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Unit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Satuan Jarak", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                text = "Kilometer (km, menit/km)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = LimeGreenPrimary
                            )
                        }
                    }
                }
            }

            // Backup, Offline Storage & Smart Sync
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = PaceMetricColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Smart Sync (Firebase & Lokal)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Online / Offline Indicator Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOnline) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) Color(0xFF16A34A) else Color(0xFFD97706))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOnline) "Online" else "Offline",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOnline) Color(0xFF14532D) else Color(0xFF78350F)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sync metadata row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Antrean Belum Dicadangkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (pendingSyncCount > 0) "$pendingSyncCount aktivitas" else "0 (Semua aman)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pendingSyncCount > 0) StatusWarning else LimeGreenDark
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Terakhir Disinkronkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (lastSyncTimestamp > 0) syncTimeFormat.format(Date(lastSyncTimestamp)) else "Belum pernah",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status message box
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = syncStatusMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action button: Sinkronkan Sekarang
                        Button(
                            onClick = { viewModel.triggerSmartSync() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = syncState != SyncState.SYNCING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeGreenPrimary,
                                contentColor = Color(0xFF09140D)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (syncState == SyncState.SYNCING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF09140D)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menyinkronkan...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sinkronkan Sekarang", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restoreFromCloud() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pulihkan Cloud", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        exportedJsonText = viewModel.getExportJson()
                                        showExportDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ekspor JSON", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pulihkan JSON", fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = { showFirebaseGuideDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Panduan Spark", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Developer / Testing Utilities
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pengujian & Pengelolaan Data",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPopulateSample,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AddChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Isi Data Contoh", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showClearConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bersihkan Data", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Export JSON Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Ekspor Data Cadangan") },
            text = {
                Column {
                    Text(
                        "Data riwayat aktivitas Anda dalam format JSON dapat dicopy dan disimpan sebagai file cadangan:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(exportedJsonText))
                        showExportDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin ke Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Import / Restore JSON Dialog
    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Pulihkan Data dari JSON") },
            text = {
                Column {
                    Text(
                        "Tempelkan format JSON cadangan LariKita di bawah ini. Data akan otomatis digabungkan tanpa membuat duplikat:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("[{\"id\": \"...\", \"distanceMeters\": ...}]", fontSize = 11.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importInput.isNotBlank()) {
                            viewModel.importRunsJson(importInput)
                        }
                        showImportDialog = false
                    }
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Firebase Cloud Guide Dialog
    if (showFirebaseGuideDialog) {
        AlertDialog(
            onDismissRequest = { showFirebaseGuideDialog = false },
            title = { Text("Panduan Integrasi Firebase Cloud") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Offline-First: Aplikasi ini 100% berfungsi normal secara offline tanpa internet menggunakan Room SQLite.",
                        fontSize = 12.sp
                    )
                    Text(
                        "2. Cloud Sync: Jika menghendaki cadangan akun online, hubungkan google-services.json dari Firebase Console ke proyek ini.",
                        fontSize = 12.sp
                    )
                    Text(
                        "3. Keamanan Akun: Setiap sesi lari memiliki 'userId' tersendiri sehingga data antar akun terisolasi dan tidak bercampur.",
                        fontSize = 12.sp
                    )
                    Text(
                        "4. Anti Duplikasi: Setiap aktivitas lari memiliki UUID unik. Sinkronisasi berulang kali tidak akan menghasilkan data ganda.",
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFirebaseGuideDialog = false }) {
                    Text("Mengerti", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Logout / Switch Account Dialog
    if (showLogoutSwitchDialog) {
        var switchName by remember { mutableStateOf(userProfile.name) }
        var switchEmail by remember { mutableStateOf(userProfile.email) }

        AlertDialog(
            onDismissRequest = { showLogoutSwitchDialog = false },
            title = { Text("Ganti Akun Pelari") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ubah profil atau beralih ke akun pelari lain. Data lokal tersimpan dengan aman.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = switchName,
                        onValueChange = { switchName = it },
                        label = { Text("Nama Pengguna") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = switchEmail,
                        onValueChange = { switchEmail = it },
                        label = { Text("Email Akun") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(userProfile.copy(name = switchName, email = switchEmail))
                        showLogoutSwitchDialog = false
                    }
                ) {
                    Text("Beralih Akun")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutSwitchDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Edit Targets Dialog
    if (showTargetEditDialog) {
        var dailyInput by remember { mutableStateOf(userProfile.dailyTargetKm.toString()) }
        var weeklyInput by remember { mutableStateOf(userProfile.weeklyTargetKm.toString()) }
        var monthlyInput by remember { mutableStateOf(userProfile.monthlyTargetKm.toString()) }

        AlertDialog(
            onDismissRequest = { showTargetEditDialog = false },
            title = { Text("Atur Target Lari") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dailyInput,
                        onValueChange = { dailyInput = it },
                        label = { Text("Target Harian (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weeklyInput,
                        onValueChange = { weeklyInput = it },
                        label = { Text("Target Mingguan (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthlyInput,
                        onValueChange = { monthlyInput = it },
                        label = { Text("Target Bulanan (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val d = dailyInput.toDoubleOrNull() ?: userProfile.dailyTargetKm
                        val w = weeklyInput.toDoubleOrNull() ?: userProfile.weeklyTargetKm
                        val m = monthlyInput.toDoubleOrNull() ?: userProfile.monthlyTargetKm
                        viewModel.updateTargets(d, w, m)
                        showTargetEditDialog = false
                    }
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showProfileEditDialog) {
        var nameInput by remember { mutableStateOf(userProfile.name) }
        var emailInput by remember { mutableStateOf(userProfile.email) }

        AlertDialog(
            onDismissRequest = { showProfileEditDialog = false },
            title = { Text("Edit Profil Pelari") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nama Pelari") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateProfile(userProfile.copy(name = nameInput, email = emailInput))
                        showProfileEditDialog = false
                    }
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDanger) },
            title = { Text("Hapus Semua Data Lari?") },
            text = { Text("Semua riwayat lari, rute GPS, dan pencapaian akan dihapus secara permanen dari perangkat ini.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Ya, Bersihkan", color = StatusDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun TargetPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LimeGreenPrimary)
        }
    }
}
