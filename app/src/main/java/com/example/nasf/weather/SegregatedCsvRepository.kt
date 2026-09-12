package com.example.nasf.weather

import android.content.Context
import kotlin.math.round

/**
 * Loads reference crop-season weekly metrics from the bundled segregated CSV
 * ([exports/rice_nasapower_weekly_segregated.csv]).
 */
object SegregatedCsvRepository {

    private const val ASSET_PATH = "data/rice_nasapower_weekly_segregated.csv"

    private val soilColumnMap = linkedMapOf(
        "pH (1:2)" to "ph",
        "OC %" to "oc_pct",
        "Major-nutrient Avail N. (Kg/ha)" to "n_kg_ha",
        "Major-nutrient P(kg/ha)" to "p_kg_ha",
        "Major-nutrient Avail. K (kg/ha)" to "k_kg_ha",
        "EC (dS/m)" to "ec_ds_m"
    )

    data class YearReferenceData(
        val calendarYear: Int,
        val farmCount: Int,
        val wideColumns: Map<String, Double>,
        val cropSeasonWeeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        val soilFeatures: Map<String, Double>,
        val meanYieldTHa: Double?
    )

    @Volatile
    private var cachedByYear: Map<Int, YearReferenceData>? = null

    fun load(context: Context): Map<Int, YearReferenceData> {
        cachedByYear?.let { return it }
        val parsed = synchronized(this) {
            cachedByYear ?: parseAsset(context).also { cachedByYear = it }
        }
        return parsed
    }

    fun availableYears(context: Context): List<Int> =
        load(context).keys.sorted()

    fun yearData(context: Context, calendarYear: Int): YearReferenceData? =
        load(context)[calendarYear]

    private fun parseAsset(context: Context): Map<Int, YearReferenceData> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val rows = parseCsv(text)
        if (rows.size < 2) return emptyMap()

        val headers = rows.first()
        val dataRows = rows.drop(1).filter { it.size == headers.size }
        val yearIndex = headers.indexOf("calendar_year")
        if (yearIndex < 0) return emptyMap()

        val grouped = linkedMapOf<Int, MutableList<List<String>>>()
        dataRows.forEach { row ->
            val year = row[yearIndex].trim().toIntOrNull() ?: return@forEach
            grouped.getOrPut(year) { mutableListOf() }.add(row)
        }

        return grouped.mapValues { (year, yearRows) ->
            buildYearReference(headers, year, yearRows)
        }
    }

    private fun buildYearReference(
        headers: List<String>,
        year: Int,
        rows: List<List<String>>
    ): YearReferenceData {
        val wide = linkedMapOf<String, Double>()
        headers.forEachIndexed { index, header ->
            if (header in setOf("Longitude", "Latitude", "Crop Sowing Time", "Crop Harvesting Time", "calendar_year")) {
                return@forEachIndexed
            }
            if (!header.startsWith("week")) return@forEachIndexed
            val values = rows.mapNotNull { row -> parseDouble(row.getOrNull(index)) }
            if (values.isNotEmpty()) {
                wide[header] = round2(values.sum() / values.size)
            }
        }

        val soil = linkedMapOf<String, Double>()
        soilColumnMap.forEach { (csvCol, featureKey) ->
            val index = headers.indexOf(csvCol)
            if (index >= 0) {
                val values = rows.mapNotNull { row -> parseDouble(row.getOrNull(index)) }
                if (values.isNotEmpty()) soil[featureKey] = round2(values.sum() / values.size)
            }
        }

        val yieldIndex = headers.indexOf("Yield Level (t/ha)")
        val meanYield = if (yieldIndex >= 0) {
            val yields = rows.mapNotNull { row -> parseDouble(row.getOrNull(yieldIndex)) }
            if (yields.isNotEmpty()) round2(yields.sum() / yields.size) else null
        } else {
            null
        }

        val cropWeeks = (SeasonWeekMapper.CROP_SEASON_START_WEEK..SeasonWeekMapper.CROP_SEASON_END_WEEK)
            .map { week -> weekStatsFromWide(week, wide) }

        return YearReferenceData(
            calendarYear = year,
            farmCount = rows.size,
            wideColumns = wide,
            cropSeasonWeeks = cropWeeks,
            soilFeatures = soil,
            meanYieldTHa = meanYield
        )
    }

    private fun weekStatsFromWide(
        week: Int,
        wide: Map<String, Double>
    ): CalendarWeeklyAnalyzer.CalendarWeekStats {
        val prefix = "week%02d".format(week)
        return CalendarWeeklyAnalyzer.CalendarWeekStats(
            weekNumber = week,
            weekStartYmd = "",
            weekEndYmd = "",
            rainfallTotalMm = wide["${prefix}_rainfall_total"] ?: Double.NaN,
            rainfallMeanMmDay = wide["${prefix}_rainfall_mean"] ?: Double.NaN,
            humidityMeanPct = wide["${prefix}_relative_humidity"] ?: Double.NaN,
            solarMeanKwhM2Day = wide["${prefix}_solar_radiation"] ?: Double.NaN,
            tMin = wide["${prefix}_t_min"] ?: Double.NaN,
            tMax = wide["${prefix}_t_max"] ?: Double.NaN,
            windSpeedMeanMs = wide["${prefix}_wind_speed"] ?: Double.NaN
        )
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = StringBuilder()
        val row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> current.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(current.toString())
                    current.clear()
                }
                c == '\n' || c == '\r' -> {
                    if (current.isNotEmpty() || row.isNotEmpty()) {
                        row.add(current.toString())
                        current.clear()
                        if (row.any { it.isNotBlank() }) rows.add(row.toList())
                        row.clear()
                    }
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty() || row.isNotEmpty()) {
            row.add(current.toString())
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
        }
        return rows
    }

    private fun parseDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().toDoubleOrNull()
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}
