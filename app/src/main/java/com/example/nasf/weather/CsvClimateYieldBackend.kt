package com.example.nasf.weather

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Local "backend" for climate yield: season climate values come from the bundled
 * segregated CSV (nearest farm by lat/lon, weeks 23–44), then the CSV-trained
 * on-device ANN predicts yield. No climate UI / separate screen.
 */
object CsvClimateYieldBackend {

    private const val ASSET_PATH = "data/rice_nasapower_weekly_segregated.csv"
    private val seasonWeeks = SeasonWeekMapper.CROP_SEASON_START_WEEK..SeasonWeekMapper.CROP_SEASON_END_WEEK

    private data class FarmRow(
        val latitude: Double,
        val longitude: Double,
        val climate: ManualClimateAnnPredictor.ClimateInputs,
        val yieldTHa: Double?
    )

    @Volatile
    private var cachedFarms: List<FarmRow>? = null

    data class Lookup(
        val climate: ManualClimateAnnPredictor.ClimateInputs,
        val distanceKm: Double,
        val csvFarmLat: Double,
        val csvFarmLon: Double
    )

    fun nearest(context: Context, latitude: Double, longitude: Double): Lookup {
        val farms = loadFarms(context)
        require(farms.isNotEmpty()) { "Climate CSV has no farm rows" }
        val nearest = farms.minBy { haversineKm(latitude, longitude, it.latitude, it.longitude) }
        val distanceKm = haversineKm(latitude, longitude, nearest.latitude, nearest.longitude)
        return Lookup(
            climate = nearest.climate,
            distanceKm = distanceKm,
            csvFarmLat = nearest.latitude,
            csvFarmLon = nearest.longitude
        )
    }

    /** Climate ANN yield (t/ha) from CSV climate nearest to [latitude]/[longitude]. */
    fun predictTHa(context: Context, latitude: Double, longitude: Double): Double {
        val lookup = nearest(context, latitude, longitude)
        return ManualClimateAnnPredictor(context).predict(lookup.climate).yieldTHa
    }

    fun formatCombined(farmYieldText: String, climateTHa: Double): String {
        return stripUnits(farmYieldText)
    }

    fun stripUnits(raw: String): String =
        raw.trim()
            .replace(Regex("(?i)\\s*(kg/ha|t/ha|tonnes?/ha|q/ha)\\s*"), "")
            .trim()

    private fun loadFarms(context: Context): List<FarmRow> {
        cachedFarms?.let { return it }
        return synchronized(this) {
            cachedFarms ?: parseFarms(context).also { cachedFarms = it }
        }
    }

    private fun parseFarms(context: Context): List<FarmRow> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val rows = parseCsv(text)
        if (rows.size < 2) return emptyList()
        val headers = rows.first()
        val latIdx = headers.indexOf("Latitude")
        val lonIdx = headers.indexOf("Longitude")
        val yieldIdx = headers.indexOf("Yield Level (t/ha)")
        if (latIdx < 0 || lonIdx < 0) return emptyList()

        val out = mutableListOf<FarmRow>()
        rows.drop(1).forEach { row ->
            if (row.size != headers.size) return@forEach
            val lat = row.getOrNull(latIdx)?.toDoubleOrNull() ?: return@forEach
            val lon = row.getOrNull(lonIdx)?.toDoubleOrNull() ?: return@forEach
            val climate = climateFromRow(headers, row) ?: return@forEach
            val y = if (yieldIdx >= 0) row.getOrNull(yieldIdx)?.toDoubleOrNull() else null
            out.add(FarmRow(lat, lon, climate, y))
        }
        return out
    }

    private fun climateFromRow(headers: List<String>, row: List<String>): ManualClimateAnnPredictor.ClimateInputs? {
        val rain = mutableListOf<Double>()
        val tmin = mutableListOf<Double>()
        val tmax = mutableListOf<Double>()
        val wind = mutableListOf<Double>()
        val solar = mutableListOf<Double>()
        val humidity = mutableListOf<Double>()

        seasonWeeks.forEach { week ->
            val p = "week%02d".format(week)
            col(headers, row, "${p}_rainfall_total")?.let { rain.add(it) }
            col(headers, row, "${p}_t_min")?.let { tmin.add(it) }
            col(headers, row, "${p}_t_max")?.let { tmax.add(it) }
            col(headers, row, "${p}_wind_speed")?.let { wind.add(it) }
            col(headers, row, "${p}_solar_radiation")?.let { solar.add(it) }
            col(headers, row, "${p}_relative_humidity")?.let { humidity.add(it) }
        }
        if (rain.isEmpty()) return null
        return ManualClimateAnnPredictor.ClimateInputs(
            seasonRainfallMm = rain.sum(),
            seasonTMinMean = mean(tmin),
            seasonTMaxMean = mean(tmax),
            seasonWindMean = mean(wind),
            seasonSolarMean = mean(solar),
            seasonHumidityMean = mean(humidity)
        )
    }

    private fun col(headers: List<String>, row: List<String>, name: String): Double? {
        val i = headers.indexOf(name)
        if (i < 0) return null
        return row.getOrNull(i)?.toDoubleOrNull()
    }

    private fun mean(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
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
}
