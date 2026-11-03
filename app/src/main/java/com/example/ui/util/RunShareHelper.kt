package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.db.RunEntity
import com.example.domain.tracker.HaversineCalculator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunShareHelper {

    /**
     * Generates a high-resolution, beautifully styled running stats card bitmap (1080x1080).
     */
    fun generateRunCardBitmap(context: Context, run: RunEntity): Bitmap {
        val width = 1080
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Sporty dark background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#0B131D"), Color.parseColor("#121F2E"), Color.parseColor("#080E16")),
                floatArrayOf(0.0f, 0.6f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle decorative accent lines at top and bottom
        val accentLinePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(80f, 60f, width - 80f, 60f, accentLinePaint)

        // 2. Brand Header
        val brandPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.15f
        }
        canvas.drawText("LARIKITA", 80f, 120f, brandPaint)

        val brandSubPaint = Paint().apply {
            color = Color.parseColor("#8E9DAE")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.05f
        }
        canvas.drawText("GPS RUNNING TRACKER", 280f, 118f, brandSubPaint)

        // 3. Card Container for Run Overview
        val cardPaint = Paint().apply {
            color = Color.parseColor("#182638")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#26384E")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val heroCardRect = RectF(80f, 160f, width - 80f, 440f)
        canvas.drawRoundRect(heroCardRect, 32f, 32f, cardPaint)
        canvas.drawRoundRect(heroCardRect, 32f, 32f, cardBorderPaint)

        // Title and Date inside Hero Card
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(run.title, 120f, 230f, titlePaint)

        val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm", Locale("id", "ID")).format(Date(run.startTime))
        val datePaint = Paint().apply {
            color = Color.parseColor("#9CB0C4")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText(dateStr, 120f, 275f, datePaint)

        // Big Highlighted Distance inside Hero Card
        val distLabelPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.12f
        }
        canvas.drawText("JARAK TEMPUH", 120f, 335f, distLabelPaint)

        val distValuePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val distStr = "%.2f".format(Locale.US, run.distanceKm)
        canvas.drawText(distStr, 120f, 415f, distValuePaint)

        val kmUnitPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val distTextWidth = distValuePaint.measureText(distStr)
        canvas.drawText("KM", 120f + distTextWidth + 18f, 410f, kmUnitPaint)

        // 4. Metric Grid (4 Cards: Durasi, Avg Pace, Kalori, Kec. Rata-rata)
        val metricBoxWidth = (width - 160f - 30f) / 2f
        val metricBoxHeight = 160f

        val metrics = listOf(
            Triple("DURASI", HaversineCalculator.formatDuration(run.durationSeconds), "#FFFFFF"),
            Triple("RATA-RATA PACE", run.averagePace + " /km", "#38BDF8"),
            Triple("KALORI TERBAKAR", "${run.calories} kcal", "#FB923C"),
            Triple("KECEPATAN AVG", "%.1f km/j".format(Locale.US, run.averageSpeed), "#A78BFA")
        )

        val gridStartY = 470f
        metrics.forEachIndexed { index, (label, value, accentHex) ->
            val col = index % 2
            val row = index / 2
            val left = 80f + col * (metricBoxWidth + 30f)
            val top = gridStartY + row * (metricBoxHeight + 24f)
            val rect = RectF(left, top, left + metricBoxWidth, top + metricBoxHeight)

            // Box bg
            val boxPaint = Paint().apply {
                color = Color.parseColor("#15202E")
                isAntiAlias = true
            }
            val boxBorder = Paint().apply {
                color = Color.parseColor("#223347")
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, 24f, 24f, boxPaint)
            canvas.drawRoundRect(rect, 24f, 24f, boxBorder)

            // Accent strip on left of each metric box
            val stripPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                isAntiAlias = true
            }
            val stripRect = RectF(left, top + 16f, left + 6f, top + metricBoxHeight - 16f)
            canvas.drawRoundRect(stripRect, 3f, 3f, stripPaint)

            // Metric Label
            val lblPaint = Paint().apply {
                color = Color.parseColor("#8E9DAE")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.08f
            }
            canvas.drawText(label, left + 30f, top + 52f, lblPaint)

            // Metric Value
            val valPaint = Paint().apply {
                color = Color.parseColor(accentHex)
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(value, left + 30f, top + 115f, valPaint)
        }

        // 5. Notes or Motivation Banner
        val notesStartY = 850f
        val notesRect = RectF(80f, notesStartY, width - 80f, notesStartY + 100f)
        val notesBg = Paint().apply {
            color = Color.parseColor("#121C28")
            isAntiAlias = true
        }
        canvas.drawRoundRect(notesRect, 20f, 20f, notesBg)

        val quotePaint = Paint().apply {
            color = Color.parseColor("#D0DCE7")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }
        val quoteText = if (run.notes.isNotBlank()) {
            "\"${run.notes}\""
        } else {
            "\"Setiap langkah adalah pencapaian. Taklukkan kilometermu!\""
        }
        canvas.drawText(quoteText, 110f, notesStartY + 58f, quotePaint)

        // 6. Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("Dibuat dengan LariKita • Rafline Dev", 80f, 1020f, footerPaint)

        val tagPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("#LariKita #Running", width - 80f, 1020f, tagPaint)

        return bitmap
    }

    /**
     * Shares the run activity as an image card.
     */
    fun shareRunAsImage(context: Context, run: RunEntity) {
        try {
            val bitmap = generateRunCardBitmap(context, run)
            val cacheFolder = File(context.cacheDir, "shared_images")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }
            val imageFile = File(cacheFolder, "larikita_run_${run.id}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Catatan ${run.title}: %.2f km dalam ${HaversineCalculator.formatDuration(run.durationSeconds)} bersama LariKita! 🏃✨".format(Locale.US, run.distanceKm)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Kartu Statistik Lari")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the run activity as detailed text summary.
     */
    fun shareRunAsText(context: Context, run: RunEntity) {
        val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID")).format(Date(run.startTime))
        val notesText = if (run.notes.isNotBlank()) "\n📝 Catatan: ${run.notes}" else ""
        val shareContent = """
            🏃 Catatan Lari bersama LariKita
            ────────────────────────
            📌 ${run.title}
            📅 $dateStr
            
            📏 Jarak: %.2f km
            ⏱️ Durasi: ${HaversineCalculator.formatDuration(run.durationSeconds)}
            ⚡ Rata-rata Pace: ${run.averagePace}
            🔥 Kalori: ${run.calories} kcal
            💨 Kecepatan Rata-rata: %.1f km/jam$notesText
            
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
