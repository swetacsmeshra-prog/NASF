package com.example.nasf.weather

data class NasaPowerMonthlyRequest(
    val startYear: Int,
    val endYear: Int,
    val latitude: Double,
    val longitude: Double,
    val community: String,
    val parameters: String,
    val format: String,
    val units: String,
    val header: String
) {
    fun validate(): String? {
        if (startYear < WeatherConfig.MONTHLY_MIN_YEAR) {
            return "Start year must be ${WeatherConfig.MONTHLY_MIN_YEAR} or later."
        }
        if (endYear < startYear) return "End year must be on or after start year."
        val span = endYear - startYear + 1
        if (span > WeatherConfig.MAX_MONTHLY_YEAR_SPAN) {
            return "Maximum ${WeatherConfig.MAX_MONTHLY_YEAR_SPAN} years allowed per request."
        }
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
        return null
    }

    fun normalizedParameters(): String =
        parameters.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")

    fun toQueryMap(): Map<String, String> = linkedMapOf(
        "start" to startYear.toString(),
        "end" to endYear.toString(),
        "latitude" to latitude.toString(),
        "longitude" to longitude.toString(),
        "community" to community.lowercase(),
        "parameters" to normalizedParameters(),
        "format" to format.lowercase(),
        "units" to units,
        "header" to header
    )

    companion object {
        fun thirtyYearWindow(): Pair<Int, Int> {
            val endYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1
            val startYear = endYear - (WeatherConfig.MAX_MONTHLY_YEAR_SPAN - 1)
            return startYear to endYear
        }
    }
}
