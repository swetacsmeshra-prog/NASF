package com.example.nasf.weather

import android.content.Context
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearest-point 30-year season climatology from the bundled compact CSV.
 */
object ClimatologyStdvRepository {

    private const val ASSET_PATH = "data/indian_weather_stdv_climatology_compact.csv"

    enum class Band { TYPICAL, UNUSUAL, OUTLIER }

    data class Stat(
        val mean: Double,
        val sd: Double,
        val min: Double,
        val max: Double
    )

    data class FarmClimatology(
        val latitude: Double,
        val longitude: Double,
        val rainSeasonTotalMm: Stat,
        val tminSeason: Stat,
        val tmaxSeason: Stat,
        val windSeason: Stat,
        val solarSeason: Stat,
        val rhSeason: Stat
    )

    data class Lookup(
        val farm: FarmClimatology,
        val distanceKm: Double,
        val inputLat: Double,
        val inputLon: Double
    )

    data class ZScore(
        val label: String,
        val z: Double,
        val band: Band
    )

    @Volatile
    private var cachedFarms: List<FarmClimatology>? = null

    fun nearest(context: Context, latitude: Double, longitude: Double): Lookup? {
        val farms = loadFarms(context)
        if (farms.isEmpty()) return null
        val farm = farms.minBy { haversineKm(latitude, longitude, it.latitude, it.longitude) }
        return Lookup(
            farm = farm,
            distanceKm = haversineKm(latitude, longitude, farm.latitude, farm.longitude),
            inputLat = latitude,
            inputLon = longitude
        )
    }

    fun outsideSeasonRange(value: Double, stat: Stat): Boolean {
        if (value.isNaN() || stat.min.isNaN() || stat.max.isNaN()) return false
        return value < stat.min || value > stat.max
    }

    fun seasonZScores(
        lookup: Lookup,
        inputs: ManualClimateAnnPredictor.ClimateInputs
    ): List<ZScore> {
        val farm = lookup.farm
        return listOf(
            score("Rain", inputs.seasonRainfallMm, farm.rainSeasonTotalMm),
            score("Tmin", inputs.seasonTMinMean, farm.tminSeason),
            score("Tmax", inputs.seasonTMaxMean, farm.tmaxSeason),
            score("Wind", inputs.seasonWindMean, farm.windSeason),
            score("Solar", inputs.seasonSolarMean, farm.solarSeason),
            score("RH", inputs.seasonHumidityMean, farm.rhSeason)
        )
    }

    private fun score(label: String, value: Double, stat: Stat): ZScore {
        val z = if (stat.sd == 0.0 || stat.sd.isNaN() || value.isNaN()) 0.0 else (value - stat.mean) / stat.sd
        val band = when {
            abs(z) >= 2.0 -> Band.OUTLIER
            abs(z) >= 1.0 -> Band.UNUSUAL
            else -> Band.TYPICAL
        }
        return ZScore(label, z, band)
    }

    private fun loadFarms(context: Context): List<FarmClimatology> {
        cachedFarms?.let { return it }
        return synchronized(this) {
            cachedFarms ?: parseFarms(context).also { cachedFarms = it }
        }
    }

    private fun parseFarms(context: Context): List<FarmClimatology> {
        val out = mutableListOf<FarmClimatology>()
        context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
            val header = lines.firstOrNull()?.split(',') ?: return@useLines
            val idx = header.withIndex().associate { it.value to it.index }
            fun col(row: List<String>, name: String): Double =
                row.getOrNull(idx[name] ?: -1)?.toDoubleOrNull() ?: Double.NaN
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val row = line.split(',')
                val lat = col(row, "latitude")
                val lon = col(row, "longitude")
                if (lat.isNaN() || lon.isNaN()) return@forEach
                out.add(
                    FarmClimatology(
                        latitude = lat,
                        longitude = lon,
                        rainSeasonTotalMm = Stat(
                            mean = col(row, "rain_season_total_mm"),
                            sd = col(row, "rain_season_total_sd"),
                            min = Double.NaN,
                            max = Double.NaN
                        ),
                        tminSeason = stat(row, ::col, "tmin_season"),
                        tmaxSeason = stat(row, ::col, "tmax_season"),
                        windSeason = stat(row, ::col, "wind_season"),
                        solarSeason = stat(row, ::col, "solar_season"),
                        rhSeason = stat(row, ::col, "rh_season")
                    )
                )
            }
        }
        return out
    }

    private fun stat(
        row: List<String>,
        col: (List<String>, String) -> Double,
        prefix: String
    ): Stat = Stat(
        mean = col(row, "${prefix}_mean"),
        sd = col(row, "${prefix}_sd"),
        min = col(row, "${prefix}_min"),
        max = col(row, "${prefix}_max")
    )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
