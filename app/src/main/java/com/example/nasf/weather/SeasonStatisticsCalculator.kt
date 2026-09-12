package com.example.nasf.weather

import kotlin.math.round
import kotlin.math.sqrt

/**
 * Season aggregates and dispersion stats for wind-power weeks 23–44.
 */
object SeasonStatisticsCalculator {

    data class WindPowerSeasonStats(
        val calendarYear: Int,
        val seasonRainfallMm: Double,
        val tMinMean: Double,
        val tMinStdv: Double,
        val tMaxMean: Double,
        val tMaxStdv: Double,
        val windMean: Double,
        val windStdv: Double,
        val solarMean: Double,
        val humidityMean: Double
    )

    fun fromCalendarWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        calendarYear: Int
    ): WindPowerSeasonStats {
        val season = WindPowerWeekMapper.filterWeeks(weeks)
        return WindPowerSeasonStats(
            calendarYear = calendarYear,
            seasonRainfallMm = round2(season.mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }.sum()),
            tMinMean = meanOf(season.map { it.tMin }),
            tMinStdv = stdvOf(season.map { it.tMin }),
            tMaxMean = meanOf(season.map { it.tMax }),
            tMaxStdv = stdvOf(season.map { it.tMax }),
            windMean = meanOf(season.map { it.windSpeedMeanMs }),
            windStdv = stdvOf(season.map { it.windSpeedMeanMs }),
            solarMean = meanOf(season.map { it.solarMeanKwhM2Day }),
            humidityMean = meanOf(season.map { it.humidityMeanPct })
        )
    }

    fun featuresFromCalendarWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        farmPayloadJson: String
    ): FeatureVectorBuilder.ModelFeatures =
        FeatureVectorBuilder.fromWindPowerWeeks(weeks, farmPayloadJson)

    fun meanOf(values: List<Double>): Double {
        val valid = values.filter { !it.isNaN() }
        if (valid.isEmpty()) return Double.NaN
        return round2(valid.sum() / valid.size)
    }

    fun stdvOf(values: List<Double>): Double {
        val valid = values.filter { !it.isNaN() }
        if (valid.size < 2) return Double.NaN
        val m = valid.sum() / valid.size
        val variance = valid.map { (it - m) * (it - m) }.sum() / valid.size
        return round2(sqrt(variance))
    }

    fun rmse(actual: List<Double>, predicted: List<Double>): Double {
        if (actual.size != predicted.size || actual.isEmpty()) return Double.NaN
        val mse = actual.zip(predicted) { a, p -> (a - p) * (a - p) }.sum() / actual.size
        return round2(sqrt(mse))
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}
