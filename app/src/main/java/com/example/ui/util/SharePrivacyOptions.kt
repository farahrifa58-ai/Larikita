package com.example.ui.util

enum class LocationPrivacyLevel(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    FULL(
        title = "Lokasi Lengkap",
        shortLabel = "Lengkap",
        description = "Tampilkan detail alamat/kelurahan dan kota"
    ),
    CITY_ONLY(
        title = "Kota / Wilayah Saja",
        shortLabel = "Kota Saja",
        description = "Samarkan detail, hanya tampilkan nama kota/kabupaten"
    ),
    HIDDEN(
        title = "Sembunyikan Lokasi",
        shortLabel = "Sembunyi",
        description = "Lokasi dan ikon pin tidak ditampilkan sama sekali"
    )
}

data class SharePrivacyOptions(
    val locationPrivacy: LocationPrivacyLevel = LocationPrivacyLevel.FULL,
    val showRouteMap: Boolean = true
)

data class RunLocationInfo(
    val fullAddress: String,
    val cityOnly: String
) {
    fun getDisplayText(level: LocationPrivacyLevel): String? {
        return when (level) {
            LocationPrivacyLevel.FULL -> fullAddress.ifBlank { null }
            LocationPrivacyLevel.CITY_ONLY -> cityOnly.ifBlank { null }
            LocationPrivacyLevel.HIDDEN -> null
        }
    }
}
