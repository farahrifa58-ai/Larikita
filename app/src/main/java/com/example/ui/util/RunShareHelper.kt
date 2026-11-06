package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.db.RunEntity
import com.example.domain.tracker.HaversineCalculator
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunShareHelper {

    /**
     * Loads a bitmap from URI and corrects EXIF rotation (e.g. from camera/selfie).
     */
    fun loadAndRotateBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            context.contentResolver.openInputStream(uri)?.use { stream ->
                try {
                    val exif = ExifInterface(stream)
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) {
                }
            }

            // Decode dimensions first to avoid OutOfMemory on large camera images
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val maxDim = 2560
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a high-resolution, beautifully styled running stats card bitmap (1080x1350)
     * complete with the interactive GPS route map polyline and privacy-filtered location.
     */
    fun generateRunCardBitmap(
        context: Context,
        run: RunEntity,
        privacyOptions: SharePrivacyOptions = SharePrivacyOptions(),
        locationInfo: RunLocationInfo = RunLocationInfo("Rute Lari", "Indonesia")
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Sporty dark background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#091017"), Color.parseColor("#0E1A26"), Color.parseColor("#070B10")),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Accent top line
        val accentLinePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(80f, 50f, width - 80f, 50f, accentLinePaint)

        // 2. Brand Header
        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.15f
        }
        canvas.drawText("LARIKITA", 80f, 110f, brandPaint)

        val brandSubPaint = Paint().apply {
            color = Color.parseColor("#8E9DAE")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.06f
        }
        canvas.drawText("GPS RUNNING TRACKER", 280f, 108f, brandSubPaint)

        // 3. Card Container for Run Overview (Title, Date, Distance, Location)
        val cardPaint = Paint().apply {
            color = Color.parseColor("#131F2E")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#223347")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val heroCardRect = RectF(80f, 140f, width - 80f, 390f)
        canvas.drawRoundRect(heroCardRect, 32f, 32f, cardPaint)
        canvas.drawRoundRect(heroCardRect, 32f, 32f, cardBorderPaint)

        // Title and Date inside Hero Card
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(run.title, 120f, 205f, titlePaint)

        // Location text with privacy filtering
        val locationText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
        if (!locationText.isNullOrBlank()) {
            val locPaint = Paint().apply {
                color = Color.parseColor("#38BDF8")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("📍 $locationText", 120f, 245f, locPaint)
        }

        val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm", Locale("id", "ID")).format(Date(run.startTime))
        val datePaint = Paint().apply {
            color = Color.parseColor("#9CB0C4")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val dateY = if (!locationText.isNullOrBlank()) 280f else 250f
        canvas.drawText(dateStr, 120f, dateY, datePaint)

        // Big Highlighted Distance
        val distValuePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val distStr = "%.2f".format(Locale.US, run.distanceKm)
        val distY = 365f
        canvas.drawText(distStr, 120f, distY, distValuePaint)

        val kmUnitPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val distTextWidth = distValuePaint.measureText(distStr)
        canvas.drawText("KM", 120f + distTextWidth + 16f, distY - 6f, kmUnitPaint)

        // 4. GPS Route Map Container (The requested route maps path)
        val mapBounds = RectF(80f, 415f, width - 80f, 780f)
        val points = run.getGpsPoints()
        RouteMapDrawer.drawRouteOnAndroidCanvas(
            canvas = canvas,
            bounds = mapBounds,
            points = points,
            distanceKm = run.distanceKm,
            showRoute = privacyOptions.showRouteMap,
            isTranslucentBg = false
        )

        // 5. Metric Grid (4 Cards: Durasi, Avg Pace, Kalori, Kec. Rata-rata)
        val metricBoxWidth = (width - 160f - 24f) / 2f
        val metricBoxHeight = 145f

        val metrics = listOf(
            Triple("DURASI", HaversineCalculator.formatDuration(run.durationSeconds), "#FFFFFF"),
            Triple("RATA-RATA PACE", run.averagePace + " /km", "#38BDF8"),
            Triple("KALORI TERBAKAR", "${run.calories} kcal", "#FB923C"),
            Triple("KECEPATAN AVG", "%.1f km/j".format(Locale.US, run.averageSpeed), "#A78BFA")
        )

        val gridStartY = 810f
        metrics.forEachIndexed { index, (label, value, accentHex) ->
            val col = index % 2
            val row = index / 2
            val left = 80f + col * (metricBoxWidth + 24f)
            val top = gridStartY + row * (metricBoxHeight + 20f)
            val rect = RectF(left, top, left + metricBoxWidth, top + metricBoxHeight)

            // Box bg
            val boxPaint = Paint().apply {
                color = Color.parseColor("#121B26")
                isAntiAlias = true
            }
            val boxBorder = Paint().apply {
                color = Color.parseColor("#1F2D3D")
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, 22f, 22f, boxPaint)
            canvas.drawRoundRect(rect, 22f, 22f, boxBorder)

            // Accent strip on left of each metric box
            val stripPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                isAntiAlias = true
            }
            val stripRect = RectF(left, top + 14f, left + 6f, top + metricBoxHeight - 14f)
            canvas.drawRoundRect(stripRect, 3f, 3f, stripPaint)

            // Metric Label
            val lblPaint = Paint().apply {
                color = Color.parseColor("#8E9DAE")
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.08f
            }
            canvas.drawText(label, left + 26f, top + 46f, lblPaint)

            // Metric Value
            val valPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(value, left + 26f, top + 104f, valPaint)
        }

        // 6. Notes or Motivation Banner
        val notesStartY = 1150f
        val notesRect = RectF(80f, notesStartY, width - 80f, notesStartY + 85f)
        val notesBg = Paint().apply {
            color = Color.parseColor("#101720")
            isAntiAlias = true
        }
        canvas.drawRoundRect(notesRect, 18f, 18f, notesBg)

        val quotePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }
        val quoteText = if (run.notes.isNotBlank()) {
            "\"${run.notes}\""
        } else {
            "\"Setiap langkah adalah pencapaian. Taklukkan kilometermu!\""
        }
        canvas.drawText(quoteText, 105f, notesStartY + 50f, quotePaint)

        // 7. Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan LariKita • Rafline Dev", 80f, 1290f, footerPaint)

        val tagPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("#LariKita #Running #TaklukkanKilometer", width - 80f, 1290f, tagPaint)

        return bitmap
    }

    /**
     * Composites a captured photo (e.g. selfie or runner photo) with a sporty modern overlay:
     * Brand, title, date, location (with privacy), distance, 4 metrics, and the GPS route map polyline!
     */
    fun generatePhotoOverlayBitmap(
        context: Context,
        photoBitmap: Bitmap,
        run: RunEntity,
        privacyOptions: SharePrivacyOptions = SharePrivacyOptions(),
        locationInfo: RunLocationInfo = RunLocationInfo("Rute Lari", "Indonesia")
    ): Bitmap {
        val targetWidth = 1080
        val targetHeight = 1920 // 9:16 portrait story format
        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // 1. Draw photo scaled and center-cropped to target
        val scale = maxOf(
            targetWidth.toFloat() / photoBitmap.width,
            targetHeight.toFloat() / photoBitmap.height
        )
        val scaledW = photoBitmap.width * scale
        val scaledH = photoBitmap.height * scale
        val left = (targetWidth - scaledW) / 2f
        val top = (targetHeight - scaledH) / 2f
        val destRect = RectF(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(photoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // 2. Top Scrim Gradient (for top brand readability)
        val topScrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 320f,
                intArrayOf(Color.argb(200, 0, 0, 0), Color.argb(120, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), 320f, topScrim)

        // 3. Bottom Scrim Gradient (for stats and route map card readability)
        val bottomScrim = Paint().apply {
            shader = LinearGradient(
                0f, targetHeight - 1100f, 0f, targetHeight.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Color.argb(235, 7, 11, 16)),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, targetHeight - 1100f, targetWidth.toFloat(), targetHeight.toFloat(), bottomScrim)

        // 4. Top Brand Pill
        val topPillRect = RectF(60f, 80f, 440f, 150f)
        val topPillPaint = Paint().apply {
            color = Color.argb(180, 15, 23, 34)
            isAntiAlias = true
        }
        val topPillBorder = Paint().apply {
            color = Color.argb(120, 0, 230, 118)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(topPillRect, 35f, 35f, topPillPaint)
        canvas.drawRoundRect(topPillRect, 35f, 35f, topPillBorder)

        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.12f
        }
        canvas.drawText("⚡ LARIKITA", 90f, 125f, brandPaint)

        val dateStr = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID")).format(Date(run.startTime))
        val dateTopPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText(dateStr, targetWidth - 60f, 122f, dateTopPaint)

        // 5. Overlay Stats Section at Bottom
        val startY = targetHeight - 960f

        // Activity Title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(8f, 2f, 4f, Color.BLACK)
        }
        canvas.drawText(run.title, 60f, startY + 50f, titlePaint)

        // Location Pin (filtered with privacy)
        val locText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
        var currentY = startY + 60f
        if (!locText.isNullOrBlank()) {
            val locPaint = Paint().apply {
                color = Color.parseColor("#38BDF8")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                setShadowLayer(6f, 1f, 3f, Color.BLACK)
            }
            currentY += 40f
            canvas.drawText("📍 $locText", 60f, currentY, locPaint)
        }

        // Highlight Distance (KM)
        currentY += 80f
        val distPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(10f, 2f, 4f, Color.BLACK)
        }
        val distStr = "%.2f".format(Locale.US, run.distanceKm)
        canvas.drawText(distStr, 60f, currentY, distPaint)

        val kmPaint = Paint().apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(8f, 2f, 4f, Color.BLACK)
        }
        val distW = distPaint.measureText(distStr)
        canvas.drawText("KM", 60f + distW + 18f, currentY - 8f, kmPaint)

        // 4 Metrics Bar
        currentY += 30f
        val barRect = RectF(60f, currentY, targetWidth - 60f, currentY + 110f)
        val barBg = Paint().apply {
            color = Color.argb(200, 15, 23, 34)
            isAntiAlias = true
        }
        val barBorder = Paint().apply {
            color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(barRect, 20f, 20f, barBg)
        canvas.drawRoundRect(barRect, 20f, 20f, barBorder)

        val metrics = listOf(
            Pair("DURASI", HaversineCalculator.formatDuration(run.durationSeconds)),
            Pair("PACE", run.averagePace + "/km"),
            Pair("KALORI", "${run.calories} kcal"),
            Pair("KEC. AVG", "%.1f km/j".format(Locale.US, run.averageSpeed))
        )
        val colW = (targetWidth - 120f) / 4f
        metrics.forEachIndexed { i, (label, value) ->
            val cx = 60f + (i * colW) + (colW / 2f)
            val lblP = Paint().apply {
                color = Color.parseColor("#94A3B8")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val valP = Paint().apply {
                color = Color.WHITE
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(label, cx, currentY + 40f, lblP)
            canvas.drawText(value, cx, currentY + 84f, valP)
        }

        // 6. GPS Route Map Container (The requested route maps path)
        val mapY = currentY + 130f
        val mapBounds = RectF(60f, mapY, targetWidth - 60f, mapY + 420f)
        val points = run.getGpsPoints()
        RouteMapDrawer.drawRouteOnAndroidCanvas(
            canvas = canvas,
            bounds = mapBounds,
            points = points,
            distanceKm = run.distanceKm,
            showRoute = privacyOptions.showRouteMap,
            isTranslucentBg = true
        )

        // 7. Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan LariKita • Running Tracker", 60f, targetHeight - 60f, footerPaint)

        val hashPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("#LariKita #TaklukkanKilometer", targetWidth - 60f, targetHeight - 60f, hashPaint)

        return resultBitmap
    }

    /**
     * Shares a bitmap via Android Share Chooser.
     */
    fun shareBitmap(context: Context, bitmap: Bitmap, fileName: String, shareText: String) {
        try {
            val cacheFolder = File(context.cacheDir, "shared_images")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val imageFile = File(cacheFolder, fileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Aktivitas Lari")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the standard run activity card image.
     */
    fun shareRunAsImage(
        context: Context,
        run: RunEntity,
        privacyOptions: SharePrivacyOptions = SharePrivacyOptions(),
        locationInfo: RunLocationInfo = RunLocationInfo("Rute Lari", "Indonesia")
    ) {
        val bitmap = generateRunCardBitmap(context, run, privacyOptions, locationInfo)
        val locText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
        val locSuffix = if (!locText.isNullOrBlank()) " di $locText" else ""
        val text = "Catatan ${run.title}$locSuffix: %.2f km dalam ${HaversineCalculator.formatDuration(run.durationSeconds)} bersama LariKita! 🏃✨".format(Locale.US, run.distanceKm)
        shareBitmap(context, bitmap, "larikita_card_${run.id}.jpg", text)
    }

    /**
     * Shares a photo with the running stats and route overlay.
     */
    fun sharePhotoWithOverlay(
        context: Context,
        photoBitmap: Bitmap,
        run: RunEntity,
        privacyOptions: SharePrivacyOptions = SharePrivacyOptions(),
        locationInfo: RunLocationInfo = RunLocationInfo("Rute Lari", "Indonesia")
    ) {
        val composite = generatePhotoOverlayBitmap(context, photoBitmap, run, privacyOptions, locationInfo)
        val locText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
        val locSuffix = if (!locText.isNullOrBlank()) " di $locText" else ""
        val text = "Selfie & Catatan Lari: ${run.title}$locSuffix (%.2f km • ${HaversineCalculator.formatDuration(run.durationSeconds)}) 📸🏃 #LariKita".format(Locale.US, run.distanceKm)
        shareBitmap(context, composite, "larikita_photo_${run.id}_${System.currentTimeMillis()}.jpg", text)
    }

    /**
     * Shares the run activity as text summary respecting location privacy options.
     */
    fun shareRunAsText(
        context: Context,
        run: RunEntity,
        privacyOptions: SharePrivacyOptions = SharePrivacyOptions(),
        locationInfo: RunLocationInfo = RunLocationInfo("Rute Lari", "Indonesia")
    ) {
        val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(run.startTime))
        val notesText = if (run.notes.isNotBlank()) "\n📝 Catatan: ${run.notes}" else ""
        val locText = locationInfo.getDisplayText(privacyOptions.locationPrivacy)
        val locationLine = if (!locText.isNullOrBlank()) "\n📍 Lokasi: $locText" else ""
        val routeLine = if (privacyOptions.showRouteMap) "\n🗺️ Jalur Rute GPS: Tercatat Lengkap" else "\n🛡️ Jalur Rute: Disembunyikan (Privasi)"

        val shareContent = """
            🏃 Catatan Lari bersama LariKita
            ────────────────────────
            📌 ${run.title}$locationLine
            📅 $dateStr
            
            📏 Jarak: %.2f km
            ⏱️ Durasi: ${HaversineCalculator.formatDuration(run.durationSeconds)}
            ⚡ Rata-rata Pace: ${run.averagePace}
            🔥 Kalori: ${run.calories} kcal
            💨 Kecepatan Rata-rata: %.1f km/jam$routeLine$notesText
            
            Aplikasi LariKita - GPS Running Tracker Modern & Offline-First
            #LariKita #RunningTracker #SehatSetiapHari
        """.trimIndent().format(Locale.US, run.distanceKm, run.averageSpeed)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareContent)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan Teks Catatan Lari")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
