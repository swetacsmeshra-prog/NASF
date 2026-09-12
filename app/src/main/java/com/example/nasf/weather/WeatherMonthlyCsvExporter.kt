package com.example.nasf.weather

import org.json.JSONObject

object WeatherMonthlyCsvExporter {

    fun toCsv(climateJson: JSONObject): String {
        val monthly = climateJson.optJSONObject("parameter_monthly")
            ?: throw IllegalStateException("Climate data has no monthly parameter series.")

        val paramNames = mutableListOf<String>()
        val keys = monthly.keys()
        while (keys.hasNext()) paramNames.add(keys.next())
        paramNames.sort()

        val periods = linkedSetOf<String>()
        paramNames.forEach { name ->
            monthly.optJSONObject(name)?.keys()?.forEach { periods.add(it) }
        }

        val sb = StringBuilder()
        sb.append("Period")
        paramNames.forEach { sb.append(',').append(it) }
        sb.append('\n')

        periods.sorted().forEach { period ->
            sb.append(formatPeriodLabel(period))
            paramNames.forEach { name ->
                sb.append(',')
                val value = monthly.optJSONObject(name)?.optDouble(period, Double.NaN) ?: Double.NaN
                if (WeatherStatistics.isValidValue(value)) sb.append(value)
            }
            sb.append('\n')
        }

        val annual = climateJson.optJSONObject("parameter_annual")
        if (annual != null && annual.length() > 0) {
            val annualPeriods = linkedSetOf<String>()
            paramNames.forEach { name ->
                annual.optJSONObject(name)?.keys()?.forEach { annualPeriods.add(it) }
            }
            annualPeriods.sorted().forEach { period ->
                sb.append(formatPeriodLabel(period))
                paramNames.forEach { name ->
                    sb.append(',')
                    val value = annual.optJSONObject(name)?.optDouble(period, Double.NaN) ?: Double.NaN
                    if (WeatherStatistics.isValidValue(value)) sb.append(value)
                }
                sb.append('\n')
            }
        }

        sb.append('\n')
        sb.append("STAT")
        paramNames.forEach { sb.append(',').append(it) }
        sb.append('\n')

        val summary = climateJson.optJSONObject("summary")
        appendStatRow(sb, "mean", paramNames, summary)
        appendStatRow(sb, "median", paramNames, summary)

        return sb.toString()
    }

    private fun appendStatRow(
        sb: StringBuilder,
        statName: String,
        paramNames: List<String>,
        summary: JSONObject?
    ) {
        sb.append(statName)
        paramNames.forEach { name ->
            sb.append(',')
            summary?.optJSONObject(name)?.let { stats ->
                val value = when (statName) {
                    "median" -> stats.optDouble("median", Double.NaN)
                    else -> stats.optDouble("mean", Double.NaN)
                }
                if (!value.isNaN()) sb.append(value)
            }
        }
        sb.append('\n')
    }

    /** YYYYMM → YYYY-MM; YYYY13 → YYYY-annual */
    fun formatPeriodLabel(period: String): String {
        if (period.length != 6) return period
        val year = period.substring(0, 4)
        val suffix = period.substring(4, 6)
        return when (suffix) {
            "13" -> "${year}-annual"
            else -> "$year-$suffix"
        }
    }

    fun buildFileName(request: NasaPowerMonthlyRequest): String {
        val lat = request.latitude.toString().replace('.', '_')
        val lon = request.longitude.toString().replace('.', '_')
        return "nasa_power_monthly_${request.startYear}_${request.endYear}_${lat}_${lon}.csv"
    }
}
