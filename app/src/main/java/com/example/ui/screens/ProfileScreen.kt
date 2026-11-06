package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserProfile
import com.example.domain.localization.AppStrings
import com.example.domain.localization.LanguageCatalog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.io.File
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lastDriveBackupTime by viewModel.lastDriveBackupTime.collectAsStateWithLifecycle()
    val backupDateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    var showTargetEditDialog by remember { mutableStateOf(false) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val currentLang = remember(userProfile.languageCode) {
        LanguageCatalog.getLanguage(userProfile.languageCode)
    }

    // Photo Picker Launcher (Android Zero-Permission Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveProfileAvatar(uri)
        }
    }

    // Create Document Launcher for Google Drive Backup (Export JSON)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val json = viewModel.getExportJson()
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    }
                    viewModel.recordDriveBackup()
                } catch (_: Exception) {}
            }
        }
    }

    // Open Document Launcher for Google Drive Restore (Import JSON)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }
                    if (!json.isNullOrBlank()) {
                        viewModel.importRunsJson(json)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Load custom profile photo bitmap if uploaded
    val avatarBitmap = remember(userProfile.avatarUrl) {
        if (userProfile.avatarUrl.isNotEmpty()) {
            try {
                val file = File(userProfile.avatarUrl)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppStrings.get("profile_title", userProfile.languageCode),
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
            // Profile Header Card (Pure Local Runner Profile with Photo Upload & Name Edit)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with Camera Badge for Photo Upload
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .align(Alignment.Center),
                                shape = CircleShape,
                                color = LimeGreenPrimary,
                                border = BorderStroke(2.dp, LimeGreenDark)
                            ) {
                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = "Foto Profil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsRun,
                                            contentDescription = null,
                                            tint = Color(0xFF09140D),
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }

                            // Camera Icon Badge
                            Surface(
                                modifier = Modifier
                                    .size(26.dp)
                                    .align(Alignment.BottomEnd),
                                shape = CircleShape,
                                color = LimeGreenDark,
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Ganti Foto Profil",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Runner Name & Edit Action
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pelari LariKita",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { showProfileEditDialog = true },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Nama & Foto", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Target Jarak Lari Card (Daily, Weekly, Monthly) - 3 Equal-width Columns
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
                                    text = AppStrings.get("running_targets", userProfile.languageCode),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = { showTargetEditDialog = true }) {
                                Text(
                                    AppStrings.get("btn_change", userProfile.languageCode),
                                    fontWeight = FontWeight.Bold,
                                    color = LimeGreenPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TargetPill(
                                label = AppStrings.get("target_daily", userProfile.languageCode),
                                value = "%.0f %s".format(userProfile.dailyTargetKm, userProfile.distanceUnit),
                                modifier = Modifier.weight(1f)
                            )
                            TargetPill(
                                label = AppStrings.get("target_weekly", userProfile.languageCode),
                                value = "%.0f %s".format(userProfile.weeklyTargetKm, userProfile.distanceUnit),
                                modifier = Modifier.weight(1f)
                            )
                            TargetPill(
                                label = AppStrings.get("target_monthly", userProfile.languageCode),
                                value = "%.0f %s".format(userProfile.monthlyTargetKm, userProfile.distanceUnit),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Tampilan, Satuan & Bahasa Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = AppStrings.get("display_units_title", userProfile.languageCode),
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
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        AppStrings.get("dark_mode", userProfile.languageCode),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        AppStrings.get("dark_mode_sub", userProfile.languageCode),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = userProfile.isDarkMode,
                                onCheckedChange = onToggleDarkMode
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Distance Unit Toggle (km vs mi)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        AppStrings.get("distance_unit", userProfile.languageCode),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (userProfile.distanceUnit == "mi") "Miles (mi)" else "Kilometer (km)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = userProfile.distanceUnit == "km",
                                    onClick = { viewModel.setDistanceUnit("km") },
                                    label = { Text("km", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LimeGreenPrimary,
                                        selectedLabelColor = Color(0xFF09140D)
                                    )
                                )
                                FilterChip(
                                    selected = userProfile.distanceUnit == "mi",
                                    onClick = { viewModel.setDistanceUnit("mi") },
                                    label = { Text("mi", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LimeGreenPrimary,
                                        selectedLabelColor = Color(0xFF09140D)
                                    )
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Language Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        AppStrings.get("app_language", userProfile.languageCode),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${currentLang.flag} ${currentLang.nativeName}",
                                        fontSize = 12.sp,
                                        color = LimeGreenDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(onClick = { showLanguageDialog = true }) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Pilih Bahasa",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // COMPACT CADANGAN GOOGLE DRIVE CARD (Replaces the big Smart Sync section)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = PaceMetricColor.copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = PaceMetricColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cadangan Google Drive",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (lastDriveBackupTime > 0) {
                                        "Terakhir: ${backupDateFormat.format(Date(lastDriveBackupTime))}"
                                    } else {
                                        "Belum pernah dicadangkan"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Compact side-by-side action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val fileName = "larikita_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                                    createDocumentLauncher.launch(fileName)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LimeGreenPrimary,
                                    contentColor = Color(0xFF09140D)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cadangkan ke Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pulihkan Cadangan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Developer / Sample Data Utilities (Compact)
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
            }

            // ABOUT APPLICATION CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .testTag("about_app_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = LimeGreenPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, LimeGreenPrimary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = LimeGreenDark,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.get("about_app_title", userProfile.languageCode),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val isTrialMode = remember { com.example.domain.config.EditionConfig.isTrial(context) }
                            Text(
                                text = "LariKita v1.0.0 • Rafline Dev • ${if (isTrialMode) "Trial Version" else "Full Version"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Buka Tentang Aplikasi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // EDIT PROFILE DIALOG (Name Edit & Photo Upload)
    if (showProfileEditDialog) {
        var nameInput by remember { mutableStateOf(userProfile.name) }

        AlertDialog(
            onDismissRequest = { showProfileEditDialog = false },
            title = {
                Text("Edit Profil Pelari", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar preview inside edit dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = LimeGreenPrimary,
                            border = BorderStroke(2.dp, LimeGreenDark)
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "Foto Profil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = Color(0xFF09140D),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ganti Foto", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nama Pelari") },
                        placeholder = { Text("Contoh: Rendy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.updateUserName(nameInput)
                        }
                        showProfileEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    )
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

    // ABOUT APPLICATION DIALOG
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, LimeGreenPrimary)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_larikita_logo),
                            contentDescription = "Logo LariKita",
                            modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("LariKita", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("GPS Running Tracker", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.get("app_description", userProfile.languageCode),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Edition & License Info (Keterangan Full Version / Trial)
                    val isTrial = remember { com.example.domain.config.EditionConfig.isTrial(context) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Edisi Aplikasi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LimeGreenPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, LimeGreenPrimary.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (isTrial) "Trial Version (24 Jam)" else "Full Version",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LimeGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Status Lisensi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (isTrial) "Uji Coba 24 Jam • Batas 1 km" else "Aktif Selamanya (Tanpa Batas)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isTrial) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sisa Waktu Trial", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = com.example.domain.config.EditionConfig.formatRemainingTrialTime(context),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LimeGreenDark
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Version Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.get("app_version", userProfile.languageCode), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v1.0.0 (Build 2026.11)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LimeGreenDark)
                    }

                    // Publisher / Developer Info (Rafline Dev)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.get("app_publisher", userProfile.languageCode), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = LimeGreenPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, LimeGreenPrimary.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Rafline Dev",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LimeGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Quick Toggle for emulator testing (Trial <-> Full)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ganti Mode Uji:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                com.example.domain.config.EditionConfig.setEditionOverride(context, !isTrial)
                                showAboutDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isTrial) "Beralih ke Full" else "Beralih ke Trial",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LimeGreenDark
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = "© 2026 Rafline Dev. Semua Hak Dilindungi.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    )
                ) {
                    Text(AppStrings.get("btn_close", userProfile.languageCode), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // MULTI-LANGUAGE PICKER DIALOG
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = LimeGreenDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.get("select_language", userProfile.languageCode), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val grouped = LanguageCatalog.supportedLanguages.groupBy { it.region }
                    grouped.forEach { (region, langs) ->
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = region.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        items(langs) { lang ->
                            val isSelected = userProfile.languageCode.equals(lang.code, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setLanguage(lang.code)
                                        showLanguageDialog = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) LimeGreenPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, LimeGreenPrimary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(lang.flag, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = lang.nativeName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = lang.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = LimeGreenDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(AppStrings.get("btn_close", userProfile.languageCode))
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
            title = { Text(AppStrings.get("running_targets", userProfile.languageCode)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dailyInput,
                        onValueChange = { dailyInput = it },
                        label = { Text("${AppStrings.get("target_daily", userProfile.languageCode)} (${userProfile.distanceUnit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weeklyInput,
                        onValueChange = { weeklyInput = it },
                        label = { Text("${AppStrings.get("target_weekly", userProfile.languageCode)} (${userProfile.distanceUnit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = monthlyInput,
                        onValueChange = { monthlyInput = it },
                        label = { Text("${AppStrings.get("target_monthly", userProfile.languageCode)} (${userProfile.distanceUnit})") },
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
                    Text(AppStrings.get("btn_save", userProfile.languageCode), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetEditDialog = false }) {
                    Text(AppStrings.get("btn_cancel", userProfile.languageCode))
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
                    Text(AppStrings.get("btn_cancel", userProfile.languageCode))
                }
            }
        )
    }
}

@Composable
fun TargetPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LimeGreenDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
