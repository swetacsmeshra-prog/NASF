package com.example.nasf.weather

import org.json.JSONObject

/**
 * Exports 52-week wide data in segregated CSV column format
 * (week01_rainfall_total … week52_wind_speed).
 */
object SegregatedCsvExporter {

    fun toCsv(
        latitude: Double,
        longitude: Double,
        sowingYmd: String,
        harvestYmd: String,
        calendarYear: Int,
        wideColumns: Map<String, Double>
    ): String {
        val sb = StringBuilder()
        val headers = buildHeaders()
        sb.append(headers.joinToString(",")).append('\n')

        val row = linkedMapOf<String, String>()
        row["Longitude"] = longitude.toString()
        row["Latitude"] = latitude.toString()
        row["Crop Sowing Time"] = formatYmdDisplay(sowingYmd)
        row["Crop Harvesting Time"] = formatYmdDisplay(harvestYmd)
        row["calendar_year"] = calendarYear.toString()

        headers.forEach { col ->
            when (col) {
                "Longitude", "Latitude", "Crop Sowing Time", "Crop Harvesting Time", "calendar_year" -> Unit
                else -> {
                    val v = wideColumns[col]
                    row[col] = if (v != null && !v.isNaN()) v.toString() else ""
                }
            }
        }

        sb.append(headers.joinToString(",") { escapeCsv(row[it].orEmpty()) })
        return sb.toString()
    }

    fun buildPreviewText(wideColumns: Map<String, Double>, maxWeeks: Int = 8): String {
        val sb = StringBuilder()
        sb.append("Segregated format (week##_metric):\n\n")
        val metrics = listOf(
            "rainfall_total", "rainfall_mean", "relative_humidity",
            "solar_radiation", "t_min", "t_max", "wind_speed"
        )
        for (week in 1..maxWeeks) {
            val prefix = "week%02d".format(week)
            sb.append("Week ").append("%02d".format(week)).append(":\n")
            metrics.forEach { metric ->
                val key = "${prefix}_$metric"
                val v = wideColumns[key]
                val display = if (v != null && !v.isNaN()) "%.2f".format(v) else "—"
                sb.append("  ").append(metric).append(": ").append(display).append('\n')
            }
            sb.append('\n')
        }
        sb.append("… weeks ").append(maxWeeks + 1).append("–52 in downloaded CSV")
        return sb.toString().trim()
    }

    fun buildFileName(latitude: Double, longitude: Double, calendarYear: Int): String =
        "rice_segregated_${"%.4f".format(latitude)}_${"%.4f".format(longitude)}_${calendarYear}.csv"

    private fun buildHeaders(): List<String> {
        val farm = listOf(
            "Longitude", "Latitude", "Crop Sowing Time", "Crop Harvesting Time", "calendar_year"
        )
        val metrics = listOf(
            "rainfall_total", "rainfall_mean", "relative_humidity",
            "solar_radiation", "t_min", "t_max", "wind_speed"
        )
        val weekCols = mutableListOf<String>()
        for (week in 1..CalendarWeeklyAnalyzer.MAX_WEEKS) {
            val prefix = "week%02d".format(week)
            metrics.forEach { weekCols.add("${prefix}_$it") }
        }
        return farm + weekCols
    }

    private fun formatYmdDisplay(ymd: String): String {
        if (ymd.length != 8) return ymd
        return "${ymd.substring(0, 4)}-${ymd.substring(4, 6)}-${ymd.substring(6, 8)}"
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
