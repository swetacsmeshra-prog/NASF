package com.example.nasf.weather

/**
 * Exports 30-year June–October monthly climate summary CSV.
 */
object MonthlyCropSeasonCsvExporter {

    fun toCsv(
        latitude: Double,
        longitude: Double,
        result: MonthlyCropSeasonAnalyzer.ThirtyYearAnalysisResult
    ): String {
        val sb = StringBuilder()
        sb.append("Longitude,Latitude,Year,Month,Month_Label,Rainfall_mm,T_Mean,T_Min,T_Max,")
            .append("Humidity_pct,Solar_kWh_m2_day,Wind_ms,")
            .append("Season_Rainfall_mm,Season_T_Max_Mean,Season_Humidity_Mean,Season_Solar_Mean,Season_Wind_Mean")
            .append('\n')

        result.years.forEach { year ->
            year.months.forEach { month ->
                sb.append(longitude).append(',')
                sb.append(latitude).append(',')
                sb.append(year.year).append(',')
                sb.append(month.month).append(',')
                sb.append(MonthlyCropSeasonAnalyzer.monthLabel(month.month)).append(',')
                sb.append(format(month.rainfallMm)).append(',')
                sb.append(format(month.tMean)).append(',')
                sb.append(format(month.tMin)).append(',')
                sb.append(format(month.tMax)).append(',')
                sb.append(format(month.humidityPct)).append(',')
                sb.append(format(month.solarKwhM2Day)).append(',')
                sb.append(format(month.windSpeedMs)).append(',')
                sb.append(format(year.seasonRainfallMm)).append(',')
                sb.append(format(year.seasonTMaxMean)).append(',')
                sb.append(format(year.seasonHumidityMean)).append(',')
                sb.append(format(year.seasonSolarMean)).append(',')
                sb.append(format(year.seasonWindMean))
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    fun buildFileName(latitude: Double, longitude: Double, startYear: Int, endYear: Int): String =
        "climate_30yr_jun_oct_${"%.4f".format(latitude)}_${"%.4f".format(longitude)}_${startYear}_${endYear}.csv"

    private fun format(value: Double): String =
        if (value.isNaN()) "" else value.toString()
}
