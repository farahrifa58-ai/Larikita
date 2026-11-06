package com.example.ui.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.GpsPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

object RouteMapDrawer {

    // Approximately 111,000 meters per degree of latitude
    private const val METERS_PER_DEGREE = 111000.0
    // Minimum geographic span (~300m) to keep short runs (e.g. 0.04 km) realistic without distortion
    private const val MIN_VIEW_SPAN_DEGREES = 0.0028 // ~310 meters

    /**
     * Draws the complete GPS route polyline, start/finish badges, scale bar, compass rose,
     * and geographic grid onto an Android Canvas.
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
            color = if (isTranslucentBg) Color.argb(215, 12, 20, 30) else Color.parseColor("#0C1622")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E3247")
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

        // Draw GPS subtle geographic grid
        val gridPaint = Paint().apply {
            color = Color.argb(35, 255, 255, 255)
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
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

        // Grid lines horizontally and vertically
        for (i in 1..3) {
            val gx = mapLeft + (mapWidth / 4f) * i
            canvas.drawLine(gx, bounds.top + 16f, gx, bounds.bottom - 16f, gridPaint)
            val gy = mapTop + (mapHeight / 4f) * i
            canvas.drawLine(bounds.left + 16f, gy, bounds.right - 16f, gy, gridPaint)
        }

        // Watermark badge in map: "GPS ROUTE"
        val mapTagPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
            isAntiAlias = true
        }
        canvas.drawText("JALUR RUTE NYATA", bounds.left + 24f, bounds.top + 40f, mapTagPaint)

        val distTagPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("%.2f KM".format(Locale.US, distanceKm), bounds.right - 80f, bounds.top + 40f, distTagPaint)

        // Draw Compass Rose (North Arrow) in top-right
        drawCompassRose(canvas, bounds.right - 42f, bounds.top + 40f)

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

        // Calculate bounding box and realistic viewport
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
        val midLon = (minLon + maxLon) / 2.0
        val cosMidLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)

        val rawLatSpan = maxLat - minLat
        val rawLonSpan = (maxLon - minLon) * cosMidLat

        // Enforce minimum realistic view span so short runs (e.g. 0.04 km = 40m)
        // are framed in realistic scale with surrounding context rather than stretched to 100%
        val effectiveLatSpan = max(rawLatSpan * 1.3, MIN_VIEW_SPAN_DEGREES)
        val effectiveLonSpan = max(rawLonSpan * 1.3, MIN_VIEW_SPAN_DEGREES)

        val viewMinLat = midLat - (effectiveLatSpan / 2.0)
        val viewMinLon = midLon - ((effectiveLonSpan / 2.0) / cosMidLat)

        // Draw area with safe margins inside bounds
        val renderMarginX = 50f
        val renderMarginY = 64f
        val availableW = bounds.width() - (renderMarginX * 2)
        val availableH = bounds.height() - (renderMarginY * 2)

        val scale = minOf(availableW / effectiveLonSpan, availableH / effectiveLatSpan)

        val routePixelW = effectiveLonSpan * scale
        val routePixelH = effectiveLatSpan * scale

        val offsetX = bounds.left + renderMarginX + ((availableW - routePixelW) / 2.0)
        val offsetY = bounds.top + renderMarginY + ((availableH - routePixelH) / 2.0)

        fun toScreenX(lon: Double): Float {
            val distFromMin = (lon - viewMinLon) * cosMidLat
            return (offsetX + distFromMin * scale).toFloat()
        }

        fun toScreenY(lat: Double): Float {
            val distFromMin = lat - viewMinLat
            return (offsetY + routePixelH - distFromMin * scale).toFloat()
        }

        // Build smoothed route path using quadratic Bezier curve
        val path = Path()
        val screenPoints = validPoints.map { PointF(toScreenX(it.longitude), toScreenY(it.latitude)) }

        if (screenPoints.size == 2) {
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            path.lineTo(screenPoints[1].x, screenPoints[1].y)
        } else {
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 0 until screenPoints.size - 1) {
                val current = screenPoints[i]
                val next = screenPoints[i + 1]
                val midX = (current.x + next.x) / 2f
                val midY = (current.y + next.y) / 2f
                if (i == 0) {
                    path.lineTo(midX, midY)
                } else {
                    path.quadTo(current.x, current.y, midX, midY)
                }
            }
            path.lineTo(screenPoints.last().x, screenPoints.last().y)
        }

        // Draw Glow
        val glowPaint = Paint().apply {
            color = Color.argb(60, 0, 230, 118)
            strokeWidth = 16f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, glowPaint)

        // Draw Sharp Foreground Line
        val linePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            strokeWidth = 7f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, linePaint)

        // Draw Start Point Marker
        val startX = screenPoints.first().x
        val startY = screenPoints.first().y

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
        canvas.drawCircle(startX, startY, 16f, startOuter)
        canvas.drawCircle(startX, startY, 11f, startCircle)
        canvas.drawCircle(startX, startY, 4.5f, whiteDot)

        // Draw Start Label
        val badgeBg = Paint().apply {
            color = Color.parseColor("#15803D")
            isAntiAlias = true
        }
        val badgeText = Paint().apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val startBadgeRect = RectF(startX - 34f, startY - 38f, startX + 34f, startY - 16f)
        canvas.drawRoundRect(startBadgeRect, 6f, 6f, badgeBg)
        canvas.drawText("START", startX, startY - 22f, badgeText)

        // Draw Finish Point Marker
        val endX = screenPoints.last().x
        val endY = screenPoints.last().y

        val endOuter = Paint().apply {
            color = Color.argb(120, 239, 68, 68)
            isAntiAlias = true
        }
        val endCircle = Paint().apply {
            color = Color.parseColor("#EF4444")
            isAntiAlias = true
        }
        canvas.drawCircle(endX, endY, 16f, endOuter)
        canvas.drawCircle(endX, endY, 11f, endCircle)
        canvas.drawCircle(endX, endY, 4.5f, whiteDot)

        val endBadgeBg = Paint().apply {
            color = Color.parseColor("#B91C1C")
            isAntiAlias = true
        }
        val endBadgeRect = RectF(endX - 36f, endY - 38f, endX + 36f, endY - 16f)
        canvas.drawRoundRect(endBadgeRect, 6f, 6f, endBadgeBg)
        canvas.drawText("FINISH", endX, endY - 22f, badgeText)

        // Draw Geographically Accurate Scale Bar
        drawScaleBar(canvas, bounds, scale)
    }

    /**
     * Draws a subtle North Arrow (Compass Rose)
     */
    private fun drawCompassRose(canvas: Canvas, cx: Float, cy: Float) {
        val bgPaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, 14f, bgPaint)

        val northPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("▲", cx, cy - 1f, northPaint)
        canvas.drawText("U", cx, cy + 11f, northPaint)
    }

    /**
     * Draws an accurate cartographic scale bar (e.g. 50m, 100m, 500m, 1km)
     */
    private fun drawScaleBar(canvas: Canvas, bounds: RectF, scale: Double) {
        val pxPerMeter = scale / METERS_PER_DEGREE
        val candidateMeters = listOf(25.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)
        val chosenMeters = candidateMeters.firstOrNull { it * pxPerMeter >= 60f && it * pxPerMeter <= 180f }
            ?: if (50.0 * pxPerMeter > 180f) 25.0 else 1000.0

        val barPx = (chosenMeters * pxPerMeter).toFloat()
        val label = if (chosenMeters >= 1000.0) "${(chosenMeters / 1000.0).toInt()} km" else "${chosenMeters.toInt()} m"

        val rightX = bounds.right - 24f
        val leftX = rightX - barPx
        val barY = bounds.bottom - 22f

        val barPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        // Main line
        canvas.drawLine(leftX, barY, rightX, barY, barPaint)
        // Left tick
        canvas.drawLine(leftX, barY - 5f, leftX, barY + 5f, barPaint)
        // Right tick
        canvas.drawLine(rightX, barY - 5f, rightX, barY + 5f, barPaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(label, (leftX + rightX) / 2f, barY - 8f, textPaint)
    }

    /**
     * Draws the route directly inside a Jetpack Compose DrawScope.
     * Maintains realistic geographic proportions so short tracks are not blown out.
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
        val midLon = (minLon + maxLon) / 2.0
        val cosMidLat = cos(Math.toRadians(midLat)).coerceAtLeast(0.1)

        val rawLatSpan = maxLat - minLat
        val rawLonSpan = (maxLon - minLon) * cosMidLat

        val effectiveLatSpan = max(rawLatSpan * 1.3, MIN_VIEW_SPAN_DEGREES)
        val effectiveLonSpan = max(rawLonSpan * 1.3, MIN_VIEW_SPAN_DEGREES)

        val viewMinLat = midLat - (effectiveLatSpan / 2.0)
        val viewMinLon = midLon - ((effectiveLonSpan / 2.0) / cosMidLat)

        val marginX = 24f
        val marginY = 24f
        val availableW = size.width - (marginX * 2)
        val availableH = size.height - (marginY * 2)

        val scale = minOf(availableW / effectiveLonSpan, availableH / effectiveLatSpan)
        val routePixelW = effectiveLonSpan * scale
        val routePixelH = effectiveLatSpan * scale

        val offsetX = marginX + ((availableW - routePixelW) / 2.0)
        val offsetY = marginY + ((availableH - routePixelH) / 2.0)

        fun toScreenX(lon: Double): Float {
            val distFromMin = (lon - viewMinLon) * cosMidLat
            return (offsetX + distFromMin * scale).toFloat()
        }

        fun toScreenY(lat: Double): Float {
            val distFromMin = lat - viewMinLat
            return (offsetY + routePixelH - distFromMin * scale).toFloat()
        }

        val screenPts = validPoints.map { Offset(toScreenX(it.longitude), toScreenY(it.latitude)) }

        val composePath = androidx.compose.ui.graphics.Path()
        if (screenPts.size == 2) {
            composePath.moveTo(screenPts[0].x, screenPts[0].y)
            composePath.lineTo(screenPts[1].x, screenPts[1].y)
        } else {
            composePath.moveTo(screenPts[0].x, screenPts[0].y)
            for (i in 0 until screenPts.size - 1) {
                val curr = screenPts[i]
                val next = screenPts[i + 1]
                val midX = (curr.x + next.x) / 2f
                val midY = (curr.y + next.y) / 2f
                if (i == 0) {
                    composePath.lineTo(midX, midY)
                } else {
                    composePath.quadraticTo(curr.x, curr.y, midX, midY)
                }
            }
            composePath.lineTo(screenPts.last().x, screenPts.last().y)
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
        val s = screenPts.first()
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFF22C55E),
            radius = 6f,
            center = s
        )
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 2.5f,
            center = s
        )

        // Draw end circle
        val e = screenPts.last()
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFFEF4444),
            radius = 6f,
            center = e
        )
        drawScope.drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 2.5f,
            center = e
        )
    }

    private data class PointF(val x: Float, val y: Float)
}
