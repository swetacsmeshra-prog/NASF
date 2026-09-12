package com.example.nasf.weather

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

/**
 * 52 calendar-week aggregation matching [merge_rice_nasapower_long.py].
 */
object CalendarWeeklyAnalyzer {

    const val PARAM_RAIN = "PRECTOTCORR"
    const val PARAM_RH = "RH2M"
    const val PARAM_SOLAR = "ALLSKY_SFC_SW_DWN"
    const val PARAM_T_MIN = "T2M_MIN"
    const val PARAM_T_MAX = "T2M_MAX"
    const val PARAM_WIND = "WS10M"

    val FULL_YEAR_PARAMS = listOf(
        PARAM_RAIN, PARAM_RH, PARAM_SOLAR, PARAM_T_MIN, PARAM_T_MAX, PARAM_WIND
    )

    const val MAX_WEEKS = 52

    data class CalendarWeekStats(
        val weekNumber: Int,
        val weekStartYmd: String,
        val weekEndYmd: String,
        val rainfallTotalMm: Double,
        val rainfallMeanMmDay: Double,
        val humidityMeanPct: Double,
        val solarMeanKwhM2Day: Double,
        val tMin: Double,
        val tMax: Double,
        val windSpeedMeanMs: Double
    )

    data class CalendarAnalysisResult(
        val calendarYear: Int,
        val weeks: List<CalendarWeekStats>,
        val wideColumns: Map<String, Double>,
        val analyzedJson: JSONObject
    )

    private val ymdFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun analyze(weatherJson: JSONObject, sowingYmd: String): CalendarAnalysisResult {
        require(sowingYmd.matches(Regex("\\d{8}"))) { "Sowing date must be YYYYMMDD." }

        val calendarYear = CalendarWeekGenerator.calendarYearFromSowingYmd(sowingYmd)
        val rain = dailySeries(weatherJson, PARAM_RAIN)
        val rh = dailySeries(weatherJson, PARAM_RH)
        val solar = dailySeries(weatherJson, PARAM_SOLAR)
        val tMin = dailySeries(weatherJson, PARAM_T_MIN)
        val tMax = dailySeries(weatherJson, PARAM_T_MAX)
        val wind = dailySeries(weatherJson, PARAM_WIND)

        if (rain.isEmpty()) {
            throw IllegalArgumentException(
                "Weather data must include rainfall ($PARAM_RAIN). " +
                    "Use: ${WeatherConfig.FULL_YEAR_ANALYZED_PARAMETERS}"
            )
        }

        val calendarWeeks = CalendarWeekGenerator.weeksForYear(calendarYear)
        val weeks = calendarWeeks.map { cw ->
            aggregateWeek(cw, rain, rh, solar, tMin, tMax, wind)
        }

        val wide = buildWideColumns(weeks)
        val analyzedJson = buildAnalyzedJson(calendarYear, sowingYmd, weeks, wide)

        return CalendarAnalysisResult(calendarYear, weeks, wide, analyzedJson)
    }

    private fun aggregateWeek(
        cw: CalendarWeekGenerator.CalendarWeek,
        rain: Map<String, Double>,
        rh: Map<String, Double>,
        solar: Map<String, Double>,
        tMin: Map<String, Double>,
        tMax: Map<String, Double>,
        wind: Map<String, Double>
    ): CalendarWeekStats {
        val start = parseYmd(cw.startYmd)
        val end = parseYmd(cw.endYmd)

        val rainVals = mutableListOf<Double>()
        val rhVals = mutableListOf<Double>()
        val solarVals = mutableListOf<Double>()
        val tMinVals = mutableListOf<Double>()
        val tMaxVals = mutableListOf<Double>()
        val windVals = mutableListOf<Double>()

        var day = start.clone() as Calendar
        while (!day.after(end)) {
            val key = ymdFormat.format(day.time)
            rain[key]?.let { rainVals.add(it) }
            rh[key]?.let { rhVals.add(it) }
            solar[key]?.let { solarVals.add(it) }
            tMin[key]?.let { tMinVals.add(it) }
            tMax[key]?.let { tMaxVals.add(it) }
            wind[key]?.let { windVals.add(it) }
            day.add(Calendar.DAY_OF_MONTH, 1)
        }

        return CalendarWeekStats(
            weekNumber = cw.indWeek,
            weekStartYmd = cw.startYmd,
            weekEndYmd = cw.endYmd,
            rainfallTotalMm = round2(if (rainVals.isNotEmpty()) rainVals.sum() else Double.NaN),
            rainfallMeanMmDay = round2(if (rainVals.isNotEmpty()) rainVals.average() else Double.NaN),
            humidityMeanPct = round2(if (rhVals.isNotEmpty()) rhVals.average() else Double.NaN),
            solarMeanKwhM2Day = round2(if (solarVals.isNotEmpty()) solarVals.average() else Double.NaN),
            tMin = round2(if (tMinVals.isNotEmpty()) tMinVals.min() else Double.NaN),
            tMax = round2(if (tMaxVals.isNotEmpty()) tMaxVals.max() else Double.NaN),
            windSpeedMeanMs = round2(if (windVals.isNotEmpty()) windVals.average() else Double.NaN)
        )
    }

    private fun buildWideColumns(weeks: List<CalendarWeekStats>): Map<String, Double> {
        val wide = linkedMapOf<String, Double>()
        weeks.forEach { week ->
            val n = week.weekNumber
            val prefix = "week%02d".format(n)
            wide["${prefix}_rainfall_total"] = week.rainfallTotalMm
            wide["${prefix}_rainfall_mean"] = week.rainfallMeanMmDay
            wide["${prefix}_relative_humidity"] = week.humidityMeanPct
            wide["${prefix}_solar_radiation"] = week.solarMeanKwhM2Day
            wide["${prefix}_t_min"] = week.tMin
            wide["${prefix}_t_max"] = week.tMax
            wide["${prefix}_wind_speed"] = week.windSpeedMeanMs
        }
        return wide
    }

    private fun buildAnalyzedJson(
        calendarYear: Int,
        sowingYmd: String,
        weeks: List<CalendarWeekStats>,
        wide: Map<String, Double>
    ): JSONObject {
        val analyzedJson = JSONObject()
        analyzedJson.put("source", "NASA_POWER_CALENDAR_52W")
        analyzedJson.put("calendar_year", calendarYear)
        analyzedJson.put("sowing_ymd", sowingYmd)
        analyzedJson.put("total_calendar_weeks", weeks.size)
        analyzedJson.put(
            "crop_season",
            JSONObject()
                .put("start_week", SeasonWeekMapper.CROP_SEASON_START_WEEK)
                .put("end_week", SeasonWeekMapper.CROP_SEASON_END_WEEK)
                .put("label", SeasonWeekMapper.cropSeasonLabel())
        )

        val weeksArray = org.json.JSONArray()
        weeks.forEach { week ->
            weeksArray.put(
                JSONObject()
                    .put("week_number", week.weekNumber)
                    .put("week_start_ymd", week.weekStartYmd)
                    .put("week_end_ymd", week.weekEndYmd)
                    .put("in_crop_season", SeasonWeekMapper.isInCropSeason(week.weekNumber))
                    .put("rainfall_total", jsonNumber(week.rainfallTotalMm))
                    .put("rainfall_mean", jsonNumber(week.rainfallMeanMmDay))
                    .put("relative_humidity", jsonNumber(week.humidityMeanPct))
                    .put("solar_radiation", jsonNumber(week.solarMeanKwhM2Day))
                    .put("t_min", jsonNumber(week.tMin))
                    .put("t_max", jsonNumber(week.tMax))
                    .put("wind_speed", jsonNumber(week.windSpeedMeanMs))
            )
        }
        analyzedJson.put("weeks", weeksArray)

        val wideJson = JSONObject()
        wide.forEach { (k, v) ->
            if (!v.isNaN()) wideJson.put(k, v)
        }
        analyzedJson.put("wide", wideJson)

        return analyzedJson
    }

    fun seasonRainfallTotal(weeks: List<CalendarWeekStats>): Double =
        weeks
            .filter { SeasonWeekMapper.isInCropSeason(it.weekNumber) }
            .mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }
            .sum()
            .let { round2(it) }

    private fun dailySeries(weatherJson: JSONObject, param: String): Map<String, Double> {
        val raw = weatherJson.optJSONObject("parameter")?.optJSONObject(param) ?: return emptyMap()
        val map = linkedMapOf<String, Double>()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val date = keys.next()
            val v = raw.optDouble(date, Double.NaN)
            if (WeatherStatistics.isValidValue(v)) map[date] = v
        }
        return map
    }

    private fun parseYmd(ymd: String): Calendar {
        val parsed = ymdFormat.parse(ymd) ?: throw IllegalArgumentException("Invalid date: $ymd")
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun round2(value: Double) = round(value * 100.0) / 100.0

    /** JSONObject rejects NaN/Infinity; use null for missing weekly values. */
    private fun jsonNumber(value: Double): Any? =
        if (value.isNaN() || value.isInfinite()) null else value
}
