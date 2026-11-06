package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.db.RunEntity
import com.example.domain.tracker.HaversineCalculator
import com.example.ui.theme.*
import com.example.ui.util.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RunShareDialog(
    run: RunEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var privacyOptions by remember { mutableStateOf(SharePrivacyOptions()) }
    var locationInfo by remember { mutableStateOf(RunLocationInfo("Memuat lokasi...", "...")) }
    var capturedPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOverlayPreview by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
    val dateFormatted = remember(run.startTime) { dateFormat.format(Date(run.startTime)) }
    val timeFormatted = remember(run.startTime) { timeFormat.format(Date(run.startTime)) }
    val points = remember(run.gpsPointsJson) { run.getGpsPoints() }

    // Resolve reverse geocoded address asynchronously
    LaunchedEffect(run.id) {
        locationInfo = RunLocationHelper.resolveLocation(context, points)
    }

    // Temporary URI for camera photo capture
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            val bitmap = RunShareHelper.loadAndRotateBitmap(context, tempPhotoUri!!)
            if (bitmap != null) {
                capturedPhotoBitmap = bitmap
                showPhotoOverlayPreview = true
            } else {
                Toast.makeText(context, "Gagal memproses foto dari kamera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCamera() {
        try {
            val photoDir = File(context.cacheDir, "camera_photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val tempFile = File(photoDir, "larikita_selfie_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Tidak dapat membuka kamera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk selfie / foto lari", Toast.LENGTH_LONG).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = RunShareHelper.loadAndRotateBitmap(context, uri)
            if (bitmap != null) {
                capturedPhotoBitmap = bitmap
                showPhotoOverlayPreview = true
            } else {
                Toast.makeText(context, "Gagal memproses foto yang dipilih", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Sub-dialog: Photo Overlay Preview with route and stats
    if (showPhotoOverlayPreview && capturedPhotoBitmap != null) {
        PhotoOverlayPreviewDialog(
            photoBitmap = capturedPhotoBitmap!!,
            run = run,
            privacyOptions = privacyOptions,
            locationInfo = locationInfo,
            onPrivacyChange = { privacyOptions = it },
            onRetakePhoto = {
                val hasCamPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCamPermission) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onShare = {
                RunShareHelper.sharePhotoWithOverlay(
                    context = context,
                    photoBitmap = capturedPhotoBitmap!!,
                    run = run,
                    privacyOptions = privacyOptions,
                    locationInfo = locationInfo
                )
                showPhotoOverlayPreview = false
                onDismiss()
            },
            onDismiss = {
                showPhotoOverlayPreview = false
            }
        )
    }

    // Main Share Dialog
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        icon = {
            Surface(
                shape = CircleShape,
                color = LimeGreenPrimary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = LimeGreenPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Bagikan Aktivitas Lari",
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pilih opsi privasi lokasi, lalu bagikan foto kamera ber-overlay rute atau kartu statistik:",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: Location & Route Privacy Controls
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = LimeGreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Privasi Lokasi & Rute",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3-Way Location Privacy Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LocationPrivacyLevel.values().forEach { level ->
                                val isSelected = privacyOptions.locationPrivacy == level
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            privacyOptions = privacyOptions.copy(locationPrivacy = level)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) LimeGreenPrimary else Color.Transparent
                                ) {
                                    Text(
                                        text = level.shortLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF09140D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val previewLocationText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
                        Text(
                            text = when (privacyOptions.locationPrivacy) {
                                LocationPrivacyLevel.FULL -> "📍 Tampil: ${previewLocationText ?: "Alamat lengkap"}"
                                LocationPrivacyLevel.CITY_ONLY -> "🏙️ Tampil: ${previewLocationText ?: "Hanya kota/kabupaten"}"
                                LocationPrivacyLevel.HIDDEN -> "🔒 Lokasi disembunyikan sepenuhnya dari kartu & foto"
                            },
                            fontSize = 11.sp,
                            color = if (privacyOptions.locationPrivacy == LocationPrivacyLevel.HIDDEN) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Toggle Map Route Path
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = null,
                                    tint = if (privacyOptions.showRouteMap) LimeGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Tampilkan Gambar Jalur Rute",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (privacyOptions.showRouteMap) "Jalur maps terlihat di kartu/foto" else "Jalur disamarkan untuk privasi",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = privacyOptions.showRouteMap,
                                onCheckedChange = {
                                    privacyOptions = privacyOptions.copy(showRouteMap = it)
                                },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: Live Card Preview (With Route Map & Location)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F1722),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LimeGreenPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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

                        // Live Location Pin with Privacy
                        val displayedLoc = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
                        if (!displayedLoc.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = displayedLoc,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF38BDF8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

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

                        // THE GPS ROUTE MAP PREVIEW
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF090E15))
                                .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(12.dp))
                        ) {
                            if (privacyOptions.showRouteMap && points.size >= 2) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    RouteMapDrawer.drawRouteInCompose(
                                        drawScope = this,
                                        points = points,
                                        showRoute = true
                                    )
                                }
                                Surface(
                                    color = Color(0xAA000000),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "📍 Jalur Maps Terlampir",
                                        color = LimeGreenPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (!privacyOptions.showRouteMap) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Jalur peta disembunyikan (Privasi)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Belum ada jalur titik GPS",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4 Metrics Row
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

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: Action Buttons

                // FEATURE 1: Camera Selfie / Photo with Overlay
                Button(
                    onClick = {
                        val hasCamPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPermission) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("open_camera_selfie_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ambil Foto / Selfie (Kamera)",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FEATURE 1B: Pick from Gallery for Overlay
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("pick_gallery_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gunakan Foto dari Galeri",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FEATURE 2: Share Standard Card Image (With Route Map)
                FilledTonalButton(
                    onClick = {
                        RunShareHelper.shareRunAsImage(context, run, privacyOptions, locationInfo)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_image_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bagikan Kartu Gambar (Dengan Rute)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // FEATURE 3: Share as Text
                OutlinedButton(
                    onClick = {
                        RunShareHelper.shareRunAsText(context, run, privacyOptions, locationInfo)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("share_text_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bagikan Format Teks",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_share_button")
            ) {
                Text("Tutup / Batal", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Dedicated Fullscreen/Dialog Preview for the Camera Photo with Stats & Route Overlay
 */
@Composable
private fun PhotoOverlayPreviewDialog(
    photoBitmap: Bitmap,
    run: RunEntity,
    privacyOptions: SharePrivacyOptions,
    locationInfo: RunLocationInfo,
    onPrivacyChange: (SharePrivacyOptions) -> Unit,
    onRetakePhoto: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewComposite = remember(photoBitmap, privacyOptions, locationInfo) {
        RunShareHelper.generatePhotoOverlayBitmap(
            context = context,
            photoBitmap = photoBitmap,
            run = run,
            privacyOptions = privacyOptions,
            locationInfo = locationInfo
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF091017)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                    Text(
                        text = "Pratinjau Foto & Rute",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onRetakePhoto) {
                        Icon(Icons.Default.Refresh, contentDescription = "Ambil Ulang", tint = LimeGreenPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // The Composite Image Preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = previewComposite.asImageBitmap(),
                        contentDescription = "Foto Lari dengan Keterangan & Rute",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick privacy toggle controls right below the photo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131F2E))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val nextPrivacy = when (privacyOptions.locationPrivacy) {
                                LocationPrivacyLevel.FULL -> LocationPrivacyLevel.CITY_ONLY
                                LocationPrivacyLevel.CITY_ONLY -> LocationPrivacyLevel.HIDDEN
                                LocationPrivacyLevel.HIDDEN -> LocationPrivacyLevel.FULL
                            }
                            onPrivacyChange(privacyOptions.copy(locationPrivacy = nextPrivacy))
                        }
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Privasi: ${privacyOptions.locationPrivacy.shortLabel}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onPrivacyChange(privacyOptions.copy(showRouteMap = !privacyOptions.showRouteMap))
                        }
                    ) {
                        Icon(
                            imageVector = if (privacyOptions.showRouteMap) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (privacyOptions.showRouteMap) LimeGreenPrimary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (privacyOptions.showRouteMap) "Jalur Aktif" else "Jalur Tersembunyi",
                            fontSize = 11.sp,
                            color = if (privacyOptions.showRouteMap) LimeGreenPrimary else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Share Button
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_share_photo_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LimeGreenPrimary,
                        contentColor = Color(0xFF09140D)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bagikan Foto Sekarang",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
