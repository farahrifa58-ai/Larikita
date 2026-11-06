package com.example.ui.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.data.model.GpsPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

object RouteMapDrawer {

    /**
     * Draws the complete GPS route polyline, start/finish badges, and GPS grid onto an Android Canvas.
     * Used for generating high-resolution card and camera photo overlay bitmaps.
     */
    fun drawRouteOnAndroidCanvas(
        canvas: Canvas,
        bounds: RectF,
        points: List<GpsPoint>,
        distanceKm: Double,
        showRoute: Boolean = true,
        isTranslucentBg: Boolean = false
    ) {
        // 1. Draw Container Background
        val bgPaint = Paint().apply {
            color = if (isTranslucentBg) Color.argb(200, 15, 23, 34) else Color.parseColor("#101A26")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#26384E")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val cornerRadius = 24f
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, borderPaint)

        // If route is hidden for privacy
        if (!showRoute) {
            val privacyTextPaint = Paint().apply {
                color = Color.parseColor("#94A3B8")
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val subTextPaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            // Draw lock icon / circle
            val lockBgPaint = Paint().apply {
                color = Color.parseColor("#1B2A3D")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val centerY = bounds.centerY() - 20f
            canvas.drawCircle(bounds.centerX(), centerY, 40f, lockBgPaint)

            canvas.drawText("🔒", bounds.centerX(), centerY + 12f, privacyTextPaint)
            canvas.drawText("Jalur Rute Disembunyikan", bounds.centerX(), centerY + 65f, privacyTextPaint)
            canvas.drawText("Privasi lokasi rute diaktifkan oleh pelari", bounds.centerX(), centerY + 100f, subTextPaint)
            return
        }

        // Filter valid points
        val validPoints = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        // Draw GPS subtle grid
        val gridPaint = Paint().apply {
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val innerPadding = 32f
        val mapLeft = bounds.left + innerPadding
        val mapTop = bounds.top + innerPadding
        val mapRight = bounds.right - innerPadding
        val mapBottom = bounds.bottom - innerPadding
        val mapWidth = mapRight - mapLeft
        val mapHeight = mapBottom - mapTop

        // 2 grid lines horizontally and vertically
        for (i in 1..2) {
            val gx = mapLeft + (mapWidth / 3f) * i
            canvas.drawLine(gx, bounds.top + 16f, gx, bounds.bottom - 16f, gridPaint)
            val gy = mapTop + (mapHeight / 3f) * i
            canvas.drawLine(bounds.left + 16f, gy, bounds.right - 16f, gy, gridPaint)
        }

        // Watermark badge in map: "GPS ROUTE"
        val mapTagPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
            isAntiAlias = true
        }
        canvas.drawText("JALUR RUTE GPS", bounds.left + 24f, bounds.top + 40f, mapTagPaint)

        val distTagPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("%.2f KM".format(Locale.US, distanceKm), bounds.right - 24f, bounds.top + 40f, distTagPaint)

        if (validPoints.size < 2) {
            val emptyPaint = Paint().apply {
                color = Color.parseColor("#8E9DAE")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Mulai aktivitas untuk memetakan rute", bounds.centerX(), bounds.centerY(), emptyPaint)
            return
        }

        // Calculate bounding box and uniform aspect ratio scaling
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (p in validPoints) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }

        val midLat = (minLat + maxLat) / 2.0
        val cosMidLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)

        val rawLatSpan = (maxLat - minLat).coerceAtLeast(0.00005)
        val rawLonSpan = ((maxLon - minLon) * cosMidLat).coerceAtLeast(0.00005)

        // Draw area with safe margins inside bounds
        val renderMarginX = 50f
        val renderMarginY = 60f
        val availableW = bounds.width() - (renderMarginX * 2)
        val availableH = bounds.height() - (renderMarginY * 2)

        val scale = minOf(availableW / rawLonSpan, availableH / rawLatSpan)

        val routePixelW = rawLonSpan * scale
        val routePixelH = rawLatSpan * scale

        val offsetX = bounds.left + renderMarginX + ((availableW - routePixelW) / 2.0)
        val offsetY = bounds.top + renderMarginY + ((availableH - routePixelH) / 2.0)

        fun toScreenX(lon: Double): Float {
            val distFromMin = (lon - minLon) * cosMidLat
            return (offsetX + distFromMin * scale).toFloat()
        }

        fun toScreenY(lat: Double): Float {
            // Latitude increases upwards, Canvas Y increases downwards
            val distFromMin = lat - minLat
            return (offsetY + routePixelH - distFromMin * scale).toFloat()
        }

        // Build route path
        val path = Path()
        validPoints.forEachIndexed { i, p ->
            val px = toScreenX(p.longitude)
            val py = toScreenY(p.latitude)
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        // Draw Glow
        val glowPaint = Paint().apply {
            color = Color.argb(70, 0, 230, 118)
            strokeWidth = 18f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, glowPaint)

        // Draw Sharp Foreground Line
        val linePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, linePaint)

        // Draw Start Point Marker
        val startP = validPoints.first()
        val startX = toScreenX(startP.longitude)
        val startY = toScreenY(startP.latitude)

        val startOuter = Paint().apply {
            color = Color.argb(120, 34, 197, 94)
            isAntiAlias = true
        }
        val startCircle = Paint().apply {
            color = Color.parseColor("#22C55E")
            isAntiAlias = true
        }
        val whiteDot = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(startX, startY, 18f, startOuter)
        canvas.drawCircle(startX, startY, 12f, startCircle)
        canvas.drawCircle(startX, startY, 5f, whiteDot)

        // Draw Start Label
        val badgeBg = Paint().apply {
            color = Color.parseColor("#15803D")
            isAntiAlias = true
        }
        val badgeText = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val startBadgeRect = RectF(startX - 36f, startY - 42f, startX + 36f, startY - 18f)
        canvas.drawRoundRect(startBadgeRect, 8f, 8f, badgeBg)
        canvas.drawText("START", startX, startY - 24f, badgeText)

        // Draw Finish Point Marker
        val endP = validPoints.last()
        val endX = toScreenX(endP.longitude)
        val endY = toScreenY(endP.latitude)

        val endOuter = Paint().apply {
            color = Color.argb(120, 239, 68, 68)
            isAntiAlias = true
        }
        val endCircle = Paint().apply {
            color = Color.parseColor("#EF4444")
            isAntiAlias = true
        }
        canvas.drawCircle(endX, endY, 18f, endOuter)
        canvas.drawCircle(endX, endY, 12f, endCircle)
        canvas.drawCircle(endX, endY, 5f, whiteDot)

        val endBadgeBg = Paint().apply {
            color = Color.parseColor("#B91C1C")
            isAntiAlias = true
        }
        val endBadgeRect = RectF(endX - 38f, endY - 42f, endX + 38f, endY - 18f)
        canvas.drawRoundRect(endBadgeRect, 8f, 8f, endBadgeBg)
        canvas.drawText("FINISH", endX, endY - 24f, badgeText)
    }

    /**
     * Draws the route directly inside a Jetpack Compose DrawScope.
     * Used for real-time interactive previews inside Compose dialogs.
     */
    fun drawRouteInCompose(
        drawScope: DrawScope,
        points: List<GpsPoint>,
        showRoute: Boolean = true
    ) {
        val size = drawScope.size
        val validPoints = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        if (!showRoute || validPoints.size < 2) return

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (p in validPoints) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }

        val midLat = (minLat + maxLat) / 2.0
        val cosMidLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)

        val rawLatSpan = (maxLat - minLat).coerceAtLeast(0.00005)
        val rawLonSpan = ((maxLon - minLon) * cosMidLat).coerceAtLeast(0.00005)

        val marginX = 24f
        val marginY = 24f
        val availableW = size.width - (marginX * 2)
        val availableH = size.height - (marginY * 2)

        val scale = minOf(availableW / rawLonSpan, availableH / rawLatSpan)
        val routePixelW = rawLonSpan * scale
        val routePixelH = rawLatSpan * scale

        val offsetX = marginX + ((availableW - routePixelW) / 2.0)
        val offsetY = marginY + ((availableH - routePixelH) / 2.0)

        fun toScreenX(lon: Double): Float {
            val distFromMin = (lon - minLon) * cosMidLat
            return (offsetX + distFromMin * scale).toFloat()
        }

        fun toScreenY(lat: Double): Float {
            val distFromMin = lat - minLat
            return (offsetY + routePixelH - distFromMin * scale).toFloat()
        }

        val composePath = androidx.compose.ui.graphics.Path()
        validPoints.forEachIndexed { i, p ->
            val px = toScreenX(p.longitude)
            val py = toScreenY(p.latitude)
            if (i == 0) composePath.moveTo(px, py) else composePath.lineTo(px, py)
        }

        // Draw glow
        drawScope.drawPath(
            path = composePath,
            color = androidx.compose.ui.graphics.Color(0x5500E676),
            style = Stroke(width = 12f)
        )
        // Draw polyline
        drawScope.drawPath(
            path = composePath,
            color = androidx.compose.ui.graphics.Color(0xFF00E676),
            style = Stroke(width = 5f)
        )

        // Draw start circle
        val sX = toScreenX(validPoints.first().longitude)
        val sY = toScreenY(validPoints.first().latitude)
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFF22C55E),
            radius = 6f,
            center = Offset(sX, sY)
        )

        // Draw end circle
        val eX = toScreenX(validPoints.last().longitude)
        val eY = toScreenY(validPoints.last().latitude)
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFFEF4444),
            radius = 6f,
            center = Offset(eX, eY)
        )
    }
}
