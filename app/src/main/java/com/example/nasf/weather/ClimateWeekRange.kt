package com.example.nasf.weather

/**
 * Week windows for the 3-input climate ANN (Rain, Tmin, Tmax).
 */
enum class ClimateWeekRange {
    CROP_23_44,
    WIND_23_42,
    SOWING_HARVEST;

    fun label(): String = when (this) {
        CROP_23_44 -> "Crop season weeks 23–44"
        WIND_23_42 -> "Wind window weeks 23–42"
        SOWING_HARVEST -> "Sowing → harvest window"
    }

    fun filterWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        sowingYmd: String,
        harvestYmd: String
    ): List<CalendarWeeklyAnalyzer.CalendarWeekStats> = when (this) {
        CROP_23_44 -> SeasonWeekMapper.cropSeasonWeeks(weeks)
        WIND_23_42 -> weeks.filter { it.weekNumber in WIND_START..WIND_END }
        SOWING_HARVEST -> weeksOverlappingSowingHarvest(weeks, sowingYmd, harvestYmd)
    }

    companion object {
        const val WIND_START = 23
        const val WIND_END = 42

        fun weeksOverlappingSowingHarvest(
            weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
            sowingYmd: String,
            harvestYmd: String
        ): List<CalendarWeeklyAnalyzer.CalendarWeekStats> {
            if (sowingYmd.length != 8 || harvestYmd.length != 8) return emptyList()
            return weeks.filter { week ->
                week.weekStartYmd.length == 8 &&
                    week.weekEndYmd.length == 8 &&
                    week.weekStartYmd <= harvestYmd &&
                    week.weekEndYmd >= sowingYmd
            }
        }
    }
}
