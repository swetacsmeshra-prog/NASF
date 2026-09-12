package com.example.nasf.weather

object AnalyzedCsvExporter {

    fun toCsv(
        latitude: Double,
        longitude: Double,
        sowingYmd: String,
        harvestYmd: String,
        result: WeeklyWeatherAnalyzer.AnalysisResult
    ): String {
        val sb = StringBuilder()
        sb.append("Longitude,Latitude,sowing_ymd,harvest_ymd,season_days,total_mon_fri_weeks")
        for (weekNum in 1..WeeklyWeatherAnalyzer.MAX_WEEKS) {
            val prefix = "week%02d".format(weekNum)
            sb.append(',').append(prefix).append("_rainfall_total_week_mm")
            sb.append(',').append(prefix).append("_rainfall_weekly_mean_mm_day")
            sb.append(',').append(prefix).append("_relative_humidity_weekly_mean_pct")
            sb.append(',').append(prefix).append("_solar_radiation_weekly_mean_kwh_m2_day")
        }
        sb.append('\n')

        sb.append(longitude).append(',').append(latitude)
        sb.append(',').append(sowingYmd).append(',').append(harvestYmd)
        sb.append(',').append(result.seasonDays).append(',').append(result.totalMonFriWeeks)

        for (weekNum in 1..WeeklyWeatherAnalyzer.MAX_WEEKS) {
            val prefix = "week%02d".format(weekNum)
            sb.append(',')
            val total = result.wideColumns["${prefix}_rainfall_total_week_mm"]
            val mean = result.wideColumns["${prefix}_rainfall_weekly_mean_mm_day"]
            val rh = result.wideColumns["${prefix}_relative_humidity_weekly_mean_pct"]
            val solar = result.wideColumns["${prefix}_solar_radiation_weekly_mean_kwh_m2_day"]
            if (total != null) sb.append(total) else sb.append("")
            sb.append(',')
            if (mean != null) sb.append(mean) else sb.append("")
            sb.append(',')
            if (rh != null) sb.append(rh) else sb.append("")
            sb.append(',')
            if (solar != null) sb.append(solar) else sb.append("")
        }
        sb.append('\n')

        sb.append('\n')
        sb.append("STAT,metric,mean,median\n")
        result.analyzedJson.optJSONObject("summary")?.let { summary ->
            appendStatRow(sb, "rainfall_total_week_mm", summary.optJSONObject("rainfall_total_week_mm"))
            appendStatRow(sb, "rainfall_weekly_mean_mm_day", summary.optJSONObject("rainfall_weekly_mean_mm_day"))
        }
        return sb.toString()
    }

    private fun appendStatRow(sb: StringBuilder, metric: String, stats: org.json.JSONObject?) {
        if (stats == null) return
        sb.append("summary,").append(metric)
            .append(',').append(stats.optDouble("mean"))
            .append(',').append(stats.optDouble("median"))
            .append('\n')
    }

    fun buildFileName(latitude: Double, longitude: Double, sowingYmd: String, harvestYmd: String): String {
        val lat = latitude.toString().replace('.', '_')
        val lon = longitude.toString().replace('.', '_')
        return "nasa_power_analyzed_${sowingYmd}_${harvestYmd}_${lat}_${lon}.csv"
    }
}
