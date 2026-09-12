package com.example.nasf.weather

import kotlin.math.round
import kotlin.math.sqrt

/**
 * Full-calendar-year aggregates from weekly NASA POWER data (weeks 1–52).
 */
object YearStatisticsCalculator {

    data class FullYearStats(
        val calendarYear: Int,
        val totalRainfallMm: Double,
        val tMinMean: Double,
        val tMinStdv: Double,
        val tMaxMean: Double,
        val tMaxStdv: Double,
        val windMean: Double,
        val windStdv: Double,
        val solarMean: Double,
        val humidityMean: Double,
        val weeksWithData: Int
    )

    fun fromCalendarWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        calendarYear: Int
    ): FullYearStats {
        val validWeeks = weeks.filter { it.weekNumber in 1..CalendarWeeklyAnalyzer.MAX_WEEKS }
        return FullYearStats(
            calendarYear = calendarYear,
            totalRainfallMm = round2(
                validWeeks.mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }.sum()
            ),
            tMinMean = SeasonStatisticsCalculator.meanOf(validWeeks.map { it.tMin }),
            tMinStdv = SeasonStatisticsCalculator.stdvOf(validWeeks.map { it.tMin }),
            tMaxMean = SeasonStatisticsCalculator.meanOf(validWeeks.map { it.tMax }),
            tMaxStdv = SeasonStatisticsCalculator.stdvOf(validWeeks.map { it.tMax }),
            windMean = SeasonStatisticsCalculator.meanOf(validWeeks.map { it.windSpeedMeanMs }),
            windStdv = SeasonStatisticsCalculator.stdvOf(validWeeks.map { it.windSpeedMeanMs }),
            solarMean = SeasonStatisticsCalculator.meanOf(validWeeks.map { it.solarMeanKwhM2Day }),
            humidityMean = SeasonStatisticsCalculator.meanOf(validWeeks.map { it.humidityMeanPct }),
            weeksWithData = validWeeks.count { !it.rainfallTotalMm.isNaN() }
        )
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}
