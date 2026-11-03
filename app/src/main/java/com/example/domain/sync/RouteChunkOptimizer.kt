package com.example.domain.sync

import com.example.data.model.GpsPoint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class RouteChunk(
    val chunkId: String,
    val activityId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val pointCount: Int,
    val pointsJson: String
)

object RouteChunkOptimizer {
    private const val CHUNK_SIZE = 150

    /**
     * Simplifies GPS points safely using Douglas-Peucker algorithm
     * with small epsilon (~4-5 meters) to preserve route fidelity.
     */
    fun simplifyRoute(points: List<GpsPoint>, epsilonMeters: Double = 3.5): List<GpsPoint> {
        if (points.size <= 2) return points
        // Convert epsilon meters to approx degree tolerance (1 deg ~ 111,320m)
        val epsilonDeg = epsilonMeters / 111320.0
        return douglasPeucker(points, epsilonDeg)
    }

    private fun douglasPeucker(points: List<GpsPoint>, epsilon: Double): List<GpsPoint> {
        if (points.size <= 2) return points

        var maxDist = 0.0
        var index = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, index + 1), epsilon)
            val right = douglasPeucker(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(p: GpsPoint, p1: GpsPoint, p2: GpsPoint): Double {
        val dx = p2.longitude - p1.longitude
        val dy = p2.latitude - p1.latitude
        val mag = hypot(dx, dy)
        if (mag < 1e-9) {
            return hypot(p.longitude - p1.longitude, p.latitude - p1.latitude)
        }
        val u = ((p.longitude - p1.longitude) * dx + (p.latitude - p1.latitude) * dy) / (mag * mag)
        val clampedU = u.coerceIn(0.0, 1.0)
        val nearestX = p1.longitude + clampedU * dx
        val nearestY = p1.latitude + clampedU * dy
        return hypot(p.longitude - nearestX, p.latitude - nearestY)
    }

    /**
     * Splits list of GPS points into optimized RouteChunks.
     */
    fun splitIntoChunks(activityId: String, rawPoints: List<GpsPoint>): List<RouteChunk> {
        val optimized = simplifyRoute(rawPoints)
        if (optimized.isEmpty()) {
            return listOf(
                RouteChunk(
                    chunkId = "${activityId}_chunk_0",
                    activityId = activityId,
                    chunkIndex = 0,
                    totalChunks = 1,
                    pointCount = 0,
                    pointsJson = "[]"
                )
            )
        }

        val chunks = mutableListOf<RouteChunk>()
        val chunkList = optimized.chunked(CHUNK_SIZE)
        val total = chunkList.size

        chunkList.forEachIndexed { idx, pointsGroup ->
            val jsonStr = GpsPoint.listToJsonString(pointsGroup)
            chunks.add(
                RouteChunk(
                    chunkId = "${activityId}_chunk_$idx",
                    activityId = activityId,
                    chunkIndex = idx,
                    totalChunks = total,
                    pointCount = pointsGroup.size,
                    pointsJson = jsonStr
                )
            )
        }
        return chunks
    }

    /**
     * Reconstructs full route in order from chunks.
     */
    fun assembleRoute(chunks: List<RouteChunk>): List<GpsPoint> {
        val sorted = chunks.sortedBy { it.chunkIndex }
        val allPoints = mutableListOf<GpsPoint>()
        for (c in sorted) {
            allPoints.addAll(GpsPoint.listFromJsonString(c.pointsJson))
        }
        return allPoints
    }
}
