package com.example.nasf.weather

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Builds 52 calendar weeks for a given year (Jan 1 – Dec 31).
 * Matches [merge_rice_nasapower_long.py] week aggregation windows.
 */
object CalendarWeekGenerator {

    data class CalendarWeek(
        val indWeek: Int,
        val startYmd: String,
        val endYmd: String
    )

    private val ymdFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun weeksForYear(calendarYear: Int): List<CalendarWeek> {
        val weeks = mutableListOf<CalendarWeek>()
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(calendarYear, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yearEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(calendarYear, Calendar.DECEMBER, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var weekNum = 1
        var cursor = start.clone() as Calendar
        while (!cursor.after(yearEnd) && weekNum <= CalendarWeeklyAnalyzer.MAX_WEEKS) {
            val weekStart = cursor.clone() as Calendar
            val weekEnd = cursor.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_MONTH, 6)
            if (weekEnd.after(yearEnd)) {
                weekEnd.timeInMillis = yearEnd.timeInMillis
            }
            weeks.add(
                CalendarWeek(
                    indWeek = weekNum,
                    startYmd = ymdFormat.format(weekStart.time),
                    endYmd = ymdFormat.format(weekEnd.time)
                )
            )
            cursor.add(Calendar.DAY_OF_MONTH, 7)
            weekNum++
        }
        return weeks
    }

    fun calendarYearFromSowingYmd(sowingYmd: String): Int {
        require(sowingYmd.length == 8) { "Sowing date must be YYYYMMDD" }
        return sowingYmd.substring(0, 4).toInt()
    }
}
