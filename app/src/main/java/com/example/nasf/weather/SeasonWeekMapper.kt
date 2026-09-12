package com.example.nasf.weather

/**
 * Rice crop window on the 52-week calendar year.
 * Sowing ~June (weeks 23–27), harvest ~October (weeks 40–44).
 */
object SeasonWeekMapper {

    const val CROP_SEASON_START_WEEK = 23
    const val CROP_SEASON_END_WEEK = 44
    const val SOWING_MARKER_WEEK = 24
    const val HARVEST_MARKER_WEEK = 41

    fun isInCropSeason(weekNumber: Int): Boolean =
        weekNumber in CROP_SEASON_START_WEEK..CROP_SEASON_END_WEEK

    fun cropSeasonLabel(): String = "June – October (weeks $CROP_SEASON_START_WEEK–$CROP_SEASON_END_WEEK)"

    fun cropSeasonWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>
    ): List<CalendarWeeklyAnalyzer.CalendarWeekStats> =
        weeks.filter { isInCropSeason(it.weekNumber) }

    val cropSeasonWeekCount: Int
        get() = CROP_SEASON_END_WEEK - CROP_SEASON_START_WEEK + 1
}
