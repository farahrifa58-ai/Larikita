package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val altitude: Double = 0.0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", timestamp)
            put("accuracy", accuracy.toDouble())
            put("speed", speed.toDouble())
            put("altitude", altitude)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): GpsPoint {
            return GpsPoint(
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                accuracy = json.optDouble("accuracy", 0.0).toFloat(),
                speed = json.optDouble("speed", 0.0).toFloat(),
                altitude = json.optDouble("altitude", 0.0)
            )
        }

        fun listToJsonString(points: List<GpsPoint>): String {
            val array = JSONArray()
            for (p in points) {
                array.put(p.toJson())
            }
            return array.toString()
        }

        fun listFromJsonString(jsonStr: String?): List<GpsPoint> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<GpsPoint>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
            } catch (_: Exception) {
                // Return whatever parsed
            }
            return list
        }
    }
}
