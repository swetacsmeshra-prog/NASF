package com.example.nasf.weather

/**
 * Rice wind-power crop window: calendar weeks 23–44 (June–October crop season).
 */
object WindPowerWeekMapper {

    const val START_WEEK = SeasonWeekMapper.CROP_SEASON_START_WEEK
    const val END_WEEK = SeasonWeekMapper.CROP_SEASON_END_WEEK

    fun isInWindPowerSeason(weekNumber: Int): Boolean =
        SeasonWeekMapper.isInCropSeason(weekNumber)

    fun seasonLabel(): String = SeasonWeekMapper.cropSeasonLabel()

    fun filterWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>
    ): List<CalendarWeeklyAnalyzer.CalendarWeekStats> =
        SeasonWeekMapper.cropSeasonWeeks(weeks)

    val weekCount: Int get() = END_WEEK - START_WEEK + 1
}
