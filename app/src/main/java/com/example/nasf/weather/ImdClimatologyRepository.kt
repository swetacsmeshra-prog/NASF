package com.example.nasf.weather

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bundled IMD 365-day climatology (Tmax/Tmin/rainfall, 1995–2025).
 * One row per district-day; IMD toggle uses the full calendar year
 * and the six CSV metric columns (means + stds).
 */
object ImdClimatologyRepository {

    const val ASSET_PATH = "data/Extract_Four_States_IMD_365day_climatology.csv"

    /** NASA-trained RF still needs wind/solar/RH; fill from climate-ANN medians. */
    private const val MEDIAN_WIND_MS = 2.95
    private const val MEDIAN_SOLAR_KWH = 18.05
    private const val MEDIAN_HUMIDITY_PCT = 68.14

    data class DistrictPoint(
        val state: String,
        val district: String,
        val distCode: String,
        val tehsil: String,
        val latitude: Double,
        val longitude: Double,
        val season: SeasonClimate
    )

    data class SeasonClimate(
        val rainfallMm: Double,
        val tminMean: Double,
        val tmaxMean: Double,
        val prStd: Double,
        val tminStd: Double,
        val tmaxStd: Double,
        val dayCount: Int
    )

    data class Lookup(
        val district: DistrictPoint,
        val distanceKm: Double,
        val season: SeasonClimate,
        val climate: ManualClimateAnnPredictor.ClimateInputs
    )

    @Volatile
    private var cachedDistricts: List<DistrictPoint>? = null

    fun nearest(context: Context, latitude: Double, longitude: Double): Lookup {
        val districts = loadDistricts(context)
        require(districts.isNotEmpty()) { "Merged IMD climatology CSV has no districts." }
        val district = districts.minBy { haversineKm(latitude, longitude, it.latitude, it.longitude) }
        return Lookup(
            district = district,
            distanceKm = haversineKm(latitude, longitude, district.latitude, district.longitude),
            season = district.season,
            climate = climateInputs(district.season)
        )
    }

    fun sourceNote(lookup: Lookup): String {
        val km = "%.1f".format(lookup.distanceKm)
        val d = lookup.district
        return "Source: IMD 365-day climatology CSV (1995–2025) · ${d.district}, " +
            "${d.state} (${km} km)"
    }

    fun climateYieldTHa(context: Context, lookup: Lookup): Double =
        ImdMergedClimateAnnPredictor(context).predict(lookup.season).yieldTHa

    fun predictClimateYieldTHa(context: Context, latitude: Double, longitude: Double): Pair<Double, Lookup> {
        val lookup = nearest(context, latitude, longitude)
        return climateYieldTHa(context, lookup) to lookup
    }

    private fun climateInputs(season: SeasonClimate) = ManualClimateAnnPredictor.ClimateInputs(
        seasonRainfallMm = season.rainfallMm,
        seasonTMinMean = season.tminMean,
        seasonTMaxMean = season.tmaxMean,
        seasonWindMean = MEDIAN_WIND_MS,
        seasonSolarMean = MEDIAN_SOLAR_KWH,
        seasonHumidityMean = MEDIAN_HUMIDITY_PCT
    )

    private fun loadDistricts(context: Context): List<DistrictPoint> {
        cachedDistricts?.let { return it }
        return synchronized(this) {
            cachedDistricts ?: parseDistricts(context).also { cachedDistricts = it }
        }
    }

    private fun parseDistricts(context: Context): List<DistrictPoint> {
        val acc = linkedMapOf<String, MutableSeason>()
        context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return emptyList()
            val header = iterator.next().split(',').map { it.trim() }
            val iState = col(header, "STATE")
            val iDistrict = col(header, "District")
            val iCode = col(header, "Dist_Code")
            val iTehsil = col(header, "TEHSIL")
            val iLat = col(header, "lat")
            val iLon = col(header, "lon")
            val iTmax = firstCol(header, "Tmax", "Tmax_mean")
            val iTmaxStd = firstCol(header, "Tmax_std", "Tmax_Std")
            val iTmin = firstCol(header, "Tmin", "Tmin_Mean")
            val iTminStd = firstCol(header, "Tmin_std", "Tmin_Std")
            val iRain = firstCol(header, "rainfall_mm", "IMD_pr_Mean")
            val iRainStd = firstCol(header, "rainfall_mm_std", "IMD_pr_std")
            if (listOf(iCode, iLat, iLon, iTmax, iTmin, iRain).any { it < 0 }) return emptyList()

            iterator.forEach { line ->
                if (line.isBlank()) return@forEach
                val cols = line.split(',')
                val code = cols.getOrNull(iCode)?.trim().orEmpty()
                if (code.isEmpty()) return@forEach
                val bucket = acc.getOrPut(code) {
                    MutableSeason(
                        state = cols.getOrNull(iState)?.trim().orEmpty(),
                        district = cols.getOrNull(iDistrict)?.trim().orEmpty(),
                        distCode = code,
                        tehsil = cols.getOrNull(iTehsil)?.trim().orEmpty(),
                        latitude = cols.getOrNull(iLat)?.toDoubleOrNull() ?: 0.0,
                        longitude = cols.getOrNull(iLon)?.toDoubleOrNull() ?: 0.0
                    )
                }
                bucket.rainfall += cols.getOrNull(iRain)?.toDoubleOrNull() ?: 0.0
                bucket.tmin += cols.getOrNull(iTmin)?.toDoubleOrNull() ?: 0.0
                bucket.tmax += cols.getOrNull(iTmax)?.toDoubleOrNull() ?: 0.0
                bucket.prStd += cols.getOrNull(iRainStd)?.toDoubleOrNull() ?: 0.0
                bucket.tminStd += cols.getOrNull(iTminStd)?.toDoubleOrNull() ?: 0.0
                bucket.tmaxStd += cols.getOrNull(iTmaxStd)?.toDoubleOrNull() ?: 0.0
                bucket.days += 1
            }
        }
        return acc.values.map { it.toDistrict() }
    }

    private fun col(header: List<String>, name: String): Int = header.indexOf(name)

    private fun firstCol(header: List<String>, vararg names: String): Int {
        names.forEach { name ->
            val i = header.indexOf(name)
            if (i >= 0) return i
        }
        return -1
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class MutableSeason(
        val state: String,
        val district: String,
        val distCode: String,
        val tehsil: String,
        val latitude: Double,
        val longitude: Double,
        var rainfall: Double = 0.0,
        var tmin: Double = 0.0,
        var tmax: Double = 0.0,
        var prStd: Double = 0.0,
        var tminStd: Double = 0.0,
        var tmaxStd: Double = 0.0,
        var days: Int = 0
    ) {
        fun toDistrict(): DistrictPoint {
            val n = days.coerceAtLeast(1).toDouble()
            return DistrictPoint(
                state = state,
                district = district,
                distCode = distCode,
                tehsil = tehsil,
                latitude = latitude,
                longitude = longitude,
                season = SeasonClimate(
                    rainfallMm = rainfall,
                    tminMean = tmin / n,
                    tmaxMean = tmax / n,
                    prStd = prStd / n,
                    tminStd = tminStd / n,
                    tmaxStd = tmaxStd / n,
                    dayCount = days
                )
            )
        }
    }
}
