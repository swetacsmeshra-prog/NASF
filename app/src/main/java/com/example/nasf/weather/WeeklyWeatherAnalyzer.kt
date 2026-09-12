package com.example.nasf.weather

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

/**
 * Mon–Fri weekly aggregation matching [merge_rice_nasapower.py].
 */
object WeeklyWeatherAnalyzer {

    const val PARAM_RAIN = "PRECTOTCORR"
    const val PARAM_RH = "RH2M"
    const val PARAM_SOLAR = "ALLSKY_SFC_SW_DWN"
    val REQUIRED_PARAMS = listOf(PARAM_RAIN, PARAM_RH, PARAM_SOLAR)

    const val MAX_SEASON_DAYS = 200
    const val MAX_WEEKS = 29

    data class WeekStats(
        val weekNumber: Int,
        val rainfallTotalMm: Double,
        val rainfallMeanMmDay: Double,
        val humidityMeanPct: Double,
        val solarMeanKwhM2Day: Double
    )

    data class AnalysisResult(
        val seasonDays: Int,
        val totalMonFriWeeks: Int,
        val weeks: List<WeekStats>,
        val wideColumns: Map<String, Double>,
        val analyzedJson: JSONObject
    )

    private val ymdFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun analyze(weatherJson: JSONObject, sowingYmd: String, harvestYmd: String): AnalysisResult {
        if (!sowingYmd.matches(Regex("\\d{8}")) || !harvestYmd.matches(Regex("\\d{8}"))) {
            throw IllegalArgumentException("Sowing and harvest dates must be YYYYMMDD.")
        }
        val sow = parseYmd(sowingYmd)
        val harv = parseYmd(harvestYmd)
        if (harv.before(sow)) {
            throw IllegalArgumentException("Harvest date must be on or after sowing date.")
        }
        val seasonDays = daysBetween(sow, harv)
        if (seasonDays > MAX_SEASON_DAYS) {
            throw IllegalArgumentException("Season exceeds $MAX_SEASON_DAYS days.")
        }

        val rain = dailySeries(weatherJson, PARAM_RAIN)
        val rh = dailySeries(weatherJson, PARAM_RH)
        val solar = dailySeries(weatherJson, PARAM_SOLAR)
        if (rain.isEmpty() || rh.isEmpty() || solar.isEmpty()) {
            throw IllegalArgumentException(
                "Weather data must include ${REQUIRED_PARAMS.joinToString(", ")}. " +
                    "Use AG parameters: ${WeatherConfig.DEFAULT_ANALYZED_DAILY_PARAMETERS}"
            )
        }

        val weeks = aggregateMonFriWeeks(sow, harv, rain, rh, solar)
        if (weeks.isEmpty()) {
            throw IllegalArgumentException("No Mon–Fri weeks found for this season.")
        }

        val wide = linkedMapOf<String, Double>()
        weeks.forEach { week ->
            val n = week.weekNumber
            val prefix = "week%02d".format(n)
            wide["${prefix}_rainfall_total_week_mm"] = week.rainfallTotalMm
            wide["${prefix}_rainfall_weekly_mean_mm_day"] = week.rainfallMeanMmDay
            wide["${prefix}_relative_humidity_weekly_mean_pct"] = week.humidityMeanPct
            wide["${prefix}_solar_radiation_weekly_mean_kwh_m2_day"] = week.solarMeanKwhM2Day
        }

        val rainMeans = weeks.map { it.rainfallMeanMmDay }
        val rainTotals = weeks.map { it.rainfallTotalMm }
        val summary = JSONObject()
        rainMeans.let { WeatherStatistics.summarize(it) }?.let { s ->
            summary.put(
                "rainfall_weekly_mean_mm_day",
                JSONObject()
                    .put("mean", s.mean)
                    .put("median", s.median)
                    .put("min", s.min)
                    .put("max", s.max)
                    .put("count", s.count)
            )
        }
        rainTotals.let { WeatherStatistics.summarize(it) }?.let { s ->
            summary.put(
                "rainfall_total_week_mm",
                JSONObject()
                    .put("mean", s.mean)
                    .put("median", s.median)
                    .put("min", s.min)
                    .put("max", s.max)
                    .put("count", s.count)
            )
        }

        val analyzedJson = JSONObject()
        analyzedJson.put("source", "NASA_POWER_WEEKLY_ANALYZED")
        analyzedJson.put("sowing_ymd", sowingYmd)
        analyzedJson.put("harvest_ymd", harvestYmd)
        analyzedJson.put("season_days", seasonDays)
        analyzedJson.put("total_mon_fri_weeks", weeks.size)
        analyzedJson.put("summary", summary)

        val weeksArray = org.json.JSONArray()
        weeks.forEach { week ->
            weeksArray.put(
                JSONObject()
                    .put("week_number", week.weekNumber)
                    .put("rainfall_total_week_mm", round2(week.rainfallTotalMm))
                    .put("rainfall_weekly_mean_mm_day", round2(week.rainfallMeanMmDay))
                    .put("relative_humidity_weekly_mean_pct", round2(week.humidityMeanPct))
                    .put("solar_radiation_weekly_mean_kwh_m2_day", round2(week.solarMeanKwhM2Day))
            )
        }
        analyzedJson.put("weeks", weeksArray)

        val wideJson = JSONObject()
        wide.forEach { (k, v) -> wideJson.put(k, v) }
        analyzedJson.put("wide", wideJson)

        return AnalysisResult(seasonDays, weeks.size, weeks, wide, analyzedJson)
    }

    fun buildSummaryText(
        latitude: Double,
        longitude: Double,
        sowingYmd: String,
        harvestYmd: String,
        result: AnalysisResult
    ): String {
        val sb = StringBuilder()
        sb.append("Location: ").append(latitude).append("° lat, ").append(longitude).append("° lon\n")
        sb.append("Season: ").append(formatYmdDisplay(sowingYmd))
            .append(" – ").append(formatYmdDisplay(harvestYmd))
            .append(" (").append(result.seasonDays).append(" days)\n")
        sb.append("Mon–Fri weeks: ").append(result.totalMonFriWeeks).append("\n\n")

        result.weeks.take(5).forEach { week ->
            sb.append("Week ").append("%02d".format(week.weekNumber)).append(": ")
                .append("rain ").append(round2(week.rainfallTotalMm)).append(" mm, ")
                .append("RH ").append(round2(week.humidityMeanPct)).append("%, ")
                .append("solar ").append(round2(week.solarMeanKwhM2Day)).append(" kWh/m²/day\n")
        }
        if (result.weeks.size > 5) {
            sb.append("… ").append(result.weeks.size - 5).append(" more weeks\n")
        }

        result.analyzedJson.optJSONObject("summary")?.let { summary ->
            sb.append("\n")
            summary.optJSONObject("rainfall_total_week_mm")?.let { stats ->
                sb.append("Weekly rainfall total (mm): mean ")
                    .append(stats.optDouble("mean"))
                    .append(", median ").append(stats.optDouble("median")).append("\n")
            }
            summary.optJSONObject("rainfall_weekly_mean_mm_day")?.let { stats ->
                sb.append("Weekly rainfall mean (mm/day): mean ")
                    .append(stats.optDouble("mean"))
                    .append(", median ").append(stats.optDouble("median")).append("\n")
            }
        }
        return sb.toString().trim()
    }

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

    private fun aggregateMonFriWeeks(
        sow: Calendar,
        harv: Calendar,
        rain: Map<String, Double>,
        rh: Map<String, Double>,
        solar: Map<String, Double>
    ): List<WeekStats> {
        val weeks = mutableListOf<WeekStats>()
        var monday = mondayOf(sow)
        var weekNum = 1
        while (!monday.after(harv) && weekNum <= MAX_WEEKS) {
            val friday = monday.clone() as Calendar
            friday.add(Calendar.DAY_OF_MONTH, 4)
            if (!friday.before(sow) && !monday.after(harv)) {
                val rainVals = mutableListOf<Double>()
                val rhVals = mutableListOf<Double>()
                val solarVals = mutableListOf<Double>()
                var day = monday.clone() as Calendar
                while (!day.after(friday)) {
                    val key = ymdFormat.format(day.time)
                    rain[key]?.let { rainVals.add(it) }
                    rh[key]?.let { rhVals.add(it) }
                    solar[key]?.let { solarVals.add(it) }
                    day.add(Calendar.DAY_OF_MONTH, 1)
                }
                if (rainVals.isNotEmpty()) {
                    weeks.add(
                        WeekStats(
                            weekNumber = weekNum,
                            rainfallTotalMm = round2(rainVals.sum()),
                            rainfallMeanMmDay = round2(rainVals.average()),
                            humidityMeanPct = round2(if (rhVals.isNotEmpty()) rhVals.average() else 0.0),
                            solarMeanKwhM2Day = round2(if (solarVals.isNotEmpty()) solarVals.average() else 0.0)
                        )
                    )
                    weekNum++
                }
            }
            monday.add(Calendar.DAY_OF_MONTH, 7)
        }
        return weeks
    }

    private fun mondayOf(cal: Calendar): Calendar {
        val mon = cal.clone() as Calendar
        val dow = mon.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (dow + 5) % 7
        mon.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        clearTime(mon)
        return mon
    }

    private fun parseYmd(ymd: String): Calendar {
        val parsed = ymdFormat.parse(ymd) ?: throw IllegalArgumentException("Invalid date: $ymd")
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = parsed
            clearTime(this)
        }
    }

    private fun clearTime(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun daysBetween(start: Calendar, end: Calendar): Int {
        val ms = end.timeInMillis - start.timeInMillis
        return (ms / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun formatYmdDisplay(ymd: String): String =
        "${ymd.substring(0, 4)}-${ymd.substring(4, 6)}-${ymd.substring(6, 8)}"

    private fun round2(value: Double) = round(value * 100.0) / 100.0
}
