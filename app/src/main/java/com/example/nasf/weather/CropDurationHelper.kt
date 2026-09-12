package com.example.nasf.weather

import java.util.Calendar

/**
 * Crop duration from variety selection:
 * Short = 100 days, Medium = 125 days, Long = 150 days after sowing.
 */
object CropDurationHelper {

    fun durationDays(varietyLabel: String): Int? {
        val v = varietyLabel.lowercase()
        return when {
            v.contains("short") || v.contains("कम") -> 100
            v.contains("medium") || v.contains("मध्यम") -> 125
            v.contains("long") || v.contains("लंबी") || v.contains("लम्बी") -> 150
            else -> null
        }
    }

    /**
     * @return Pair(harvestYmd YYYYMMDD, harvestDayOfYear)
     */
    fun harvestFromSowingYmd(sowingYmd: String, durationDays: Int): Pair<String, Double> {
        require(sowingYmd.length == 8) { "Sowing date must be YYYYMMDD" }
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(
            sowingYmd.substring(0, 4).toInt(),
            sowingYmd.substring(4, 6).toInt() - 1,
            sowingYmd.substring(6, 8).toInt()
        )
        cal.add(Calendar.DAY_OF_YEAR, durationDays)
        val ymd = JulianDateHelper.formatYmd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val doy = cal.get(Calendar.DAY_OF_YEAR).toDouble()
        return ymd to doy
    }

    /** When only Julian DOY is known (no year): harvest DOY = sowing DOY + days (may exceed 365). */
    fun harvestDoyFromSowingDoy(sowingDoy: Double, durationDays: Int): Double =
        sowingDoy + durationDays
}
