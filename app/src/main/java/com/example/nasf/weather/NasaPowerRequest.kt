package com.example.nasf.weather

data class NasaPowerRequest(
    val start: String,
    val end: String,
    val latitude: Double,
    val longitude: Double,
    val community: String,
    val parameters: String,
    val format: String,
    val units: String,
    val user: String?,
    val header: String,
    val timeStandard: String,
    val siteElevation: Double,
    val windElevation: Double,
    val windSurface: String
) {
    fun validate(): String? {
        if (!start.matches(Regex("\\d{8}"))) return "Start date must be YYYYMMDD (8 digits)."
        if (!end.matches(Regex("\\d{8}"))) return "End date must be YYYYMMDD (8 digits)."
        if (end < start) return "End date must be on or after start date."
        if (latitude !in -90.0..90.0) return "Latitude must be between -90 and 90."
        if (longitude !in -180.0..180.0) return "Longitude must be between -180 and 180."
        val paramList = parameters.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (paramList.isEmpty()) return "At least one parameter is required."
        if (paramList.size > WeatherConfig.MAX_DAILY_PARAMETERS) {
            return "Maximum ${WeatherConfig.MAX_DAILY_PARAMETERS} parameters allowed per request."
        }
        if (community.isBlank()) return "Community is required."
        if (format.isBlank()) return "Format is required."
        if (units.isBlank()) return "Units are required."
        if (timeStandard.isBlank()) return "Time standard is required."
        if (windSurface.isBlank()) return "Wind surface is required."
        return null
    }

    fun normalizedParameters(): String =
        parameters.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    fun toQueryMap(): Map<String, String> {
        val map = linkedMapOf(
            "start" to start,
            "end" to end,
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString(),
            "community" to community.lowercase(),
            "parameters" to normalizedParameters(),
            "format" to format.lowercase(),
            "units" to units,
            "header" to header,
            "time-standard" to timeStandard.lowercase(),
            "site-elevation" to siteElevation.toString(),
            "wind-elevation" to windElevation.toString(),
            "wind-surface" to windSurface
        )
        if (!user.isNullOrBlank()) {
            map["user"] = user.trim()
        }
        return map
    }
}
