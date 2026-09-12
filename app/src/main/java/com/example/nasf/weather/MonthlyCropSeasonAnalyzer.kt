package com.example.nasf.weather

import org.json.JSONObject
import kotlin.math.round

/**
 * Parses NASA POWER 30-year monthly JSON and builds June–October crop-season stats per year.
 */
object MonthlyCropSeasonAnalyzer {

    val CROP_MONTHS = listOf(6, 7, 8, 9, 10)

    const val PARAM_RAIN = "PRECTOTCORR"
    const val PARAM_T2M = "T2M"
    const val PARAM_T_MIN = "T2M_MIN"
    const val PARAM_T_MAX = "T2M_MAX"
    const val PARAM_RH = "RH2M"
    const val PARAM_SOLAR = "ALLSKY_SFC_SW_DWN"
    const val PARAM_WIND = "WS10M"

    data class MonthStats(
        val year: Int,
        val month: Int,
        val rainfallMm: Double,
        val tMean: Double,
        val tMin: Double,
        val tMax: Double,
        val humidityPct: Double,
        val solarKwhM2Day: Double,
        val windSpeedMs: Double
    )

    data class YearSeasonStats(
        val year: Int,
        val months: List<MonthStats>,
        val seasonRainfallMm: Double,
        val seasonTMaxMean: Double,
        val seasonHumidityMean: Double,
        val seasonSolarMean: Double,
        val seasonWindMean: Double
    )

    data class ThirtyYearAnalysisResult(
        val startYear: Int,
        val endYear: Int,
        val years: List<YearSeasonStats>
    )

    fun analyze(climateJson: JSONObject): ThirtyYearAnalysisResult {
        val monthly = climateJson.optJSONObject("parameter_monthly")
            ?: throw IllegalArgumentException("Climate data has no monthly parameter series.")

        val rain = monthlySeries(monthly, PARAM_RAIN)
        val t2m = monthlySeries(monthly, PARAM_T2M)
        val tMin = monthlySeries(monthly, PARAM_T_MIN)
        val tMax = monthlySeries(monthly, PARAM_T_MAX)
        val rh = monthlySeries(monthly, PARAM_RH)
        val solar = monthlySeries(monthly, PARAM_SOLAR)
        val wind = monthlySeries(monthly, PARAM_WIND)

        if (rain.isEmpty()) {
            throw IllegalArgumentException(
                "Monthly data must include rainfall ($PARAM_RAIN). " +
                    "Use: ${WeatherConfig.FULL_YEAR_MONTHLY_PARAMETERS}"
            )
        }

        val yearSet = linkedSetOf<Int>()
        rain.keys.forEach { period ->
            parsePeriod(period)?.let { (y, m) ->
                if (m in CROP_MONTHS) yearSet.add(y)
            }
        }

        val yearStats = yearSet.sorted().map { year ->
            buildYearStats(year, rain, t2m, tMin, tMax, rh, solar, wind)
        }

        if (yearStats.isEmpty()) {
            throw IllegalArgumentException("No June–October monthly data found in climate response.")
        }

        return ThirtyYearAnalysisResult(
            startYear = yearStats.first().year,
            endYear = yearStats.last().year,
            years = yearStats
        )
    }

    private fun buildYearStats(
        year: Int,
        rain: Map<String, Double>,
        t2m: Map<String, Double>,
        tMin: Map<String, Double>,
        tMax: Map<String, Double>,
        rh: Map<String, Double>,
        solar: Map<String, Double>,
        wind: Map<String, Double>
    ): YearSeasonStats {
        val months = CROP_MONTHS.map { month ->
            val key = "%04d%02d".format(year, month)
            MonthStats(
                year = year,
                month = month,
                rainfallMm = round2(rain[key] ?: Double.NaN),
                tMean = round2(t2m[key] ?: Double.NaN),
                tMin = round2(tMin[key] ?: Double.NaN),
                tMax = round2(
                    when {
                        tMax.containsKey(key) -> tMax[key]!!
                        t2m.containsKey(key) -> t2m[key]!!
                        else -> Double.NaN
                    }
                ),
                humidityPct = round2(rh[key] ?: Double.NaN),
                solarKwhM2Day = round2(solar[key] ?: Double.NaN),
                windSpeedMs = round2(wind[key] ?: Double.NaN)
            )
        }

        return YearSeasonStats(
            year = year,
            months = months,
            seasonRainfallMm = round2(months.mapNotNull { it.rainfallMm.takeIf { v -> !v.isNaN() } }.sum()),
            seasonTMaxMean = meanOf(months.map { it.tMax }),
            seasonHumidityMean = meanOf(months.map { it.humidityPct }),
            seasonSolarMean = meanOf(months.map { it.solarKwhM2Day }),
            seasonWindMean = meanOf(months.map { it.windSpeedMs })
        )
    }

    private fun monthlySeries(monthly: JSONObject, param: String): Map<String, Double> {
        val raw = monthly.optJSONObject(param) ?: return emptyMap()
        val map = linkedMapOf<String, Double>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val period = keys.next()
            if (period.length != 6) continue
            val v = raw.optDouble(period, Double.NaN)
            if (WeatherStatistics.isValidValue(v)) map[period] = v
        }
        return map
    }

    private fun parsePeriod(period: String): Pair<Int, Int>? {
        if (period.length != 6) return null
        return try {
            period.substring(0, 4).toInt() to period.substring(4, 6).toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun meanOf(values: List<Double>): Double {
        val valid = values.filter { !it.isNaN() }
        if (valid.isEmpty()) return Double.NaN
        return round2(valid.sum() / valid.size)
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0

    fun monthLabel(month: Int): String = when (month) {
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        else -> month.toString()
    }
}
