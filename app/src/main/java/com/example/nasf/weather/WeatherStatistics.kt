package com.example.nasf.weather

import kotlin.math.round

data class WeatherSummary(
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val count: Int,
    val units: String = ""
)

object WeatherStatistics {

    const val FILL_VALUE = -999.0

    fun isValidValue(value: Double): Boolean =
        !value.isNaN() && value != FILL_VALUE

    fun collectValidValues(series: Map<String, Double>): List<Double> =
        series.values.filter { isValidValue(it) }

    fun summarize(values: List<Double>, units: String = ""): WeatherSummary? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        return WeatherSummary(
            mean = round2(values.average()),
            median = round2(median),
            min = round2(sorted.first()),
            max = round2(sorted.last()),
            count = values.size,
            units = units
        )
    }

    fun round2(value: Double) = round(value * 100.0) / 100.0
}
