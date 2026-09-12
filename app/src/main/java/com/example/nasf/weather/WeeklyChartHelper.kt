package com.example.nasf.weather

import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

object WeeklyChartHelper {

    private const val COLOR_CROP = "#4CAF50"
    private const val COLOR_T_MIN = "#1565C0"
    private const val COLOR_T_MAX = "#C62828"
    private const val COLOR_CUBIC = "#6A1B9A"
    private const val COLOR_USER = "#FF6F00"

    fun setupRainfallChart(
        chart: BarChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        val entries = weeks.map { week ->
            BarEntry(
                week.weekNumber.toFloat(),
                if (week.rainfallTotalMm.isNaN()) 0f else week.rainfallTotalMm.toFloat()
            )
        }
        val cropColor = Color.parseColor(COLOR_CROP)
        val dataSet = BarDataSet(entries, "Rainfall (mm)").apply {
            color = cropColor
            setColors(cropColor)
            valueTextSize = 8f
            setDrawValues(false)
        }
        chart.data = BarData(dataSet)
        styleCropSeasonChart(chart, chartDescription)
    }

    fun setupTemperatureChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        val tMinEntries = weeks.map { Entry(it.weekNumber.toFloat(), safeFloat(it.tMin)) }
        val tMaxEntries = weeks.map { Entry(it.weekNumber.toFloat(), safeFloat(it.tMax)) }
        val tMinSet = LineDataSet(tMinEntries, "T Min (°C)").apply {
            color = Color.parseColor(COLOR_T_MIN)
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }
        val tMaxSet = LineDataSet(tMaxEntries, "T Max (°C)").apply {
            color = Color.parseColor(COLOR_T_MAX)
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }
        chart.data = LineData(tMinSet, tMaxSet)
        styleCropSeasonChart(chart, chartDescription)
    }

    fun setupLineMetricChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        label: String,
        valueSelector: (CalendarWeeklyAnalyzer.CalendarWeekStats) -> Double,
        colorHex: String,
        chartDescription: String = ""
    ) {
        val entries = weeks.map { week ->
            Entry(week.weekNumber.toFloat(), safeFloat(valueSelector(week)))
        }
        val dataSet = LineDataSet(entries, label).apply {
            color = Color.parseColor(colorHex)
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 2f
            setDrawValues(false)
        }
        chart.data = LineData(dataSet)
        styleCropSeasonChart(chart, chartDescription)
    }

    fun setupCubicChart(
        chart: CombinedChart,
        curvePoints: List<Pair<Double, Double>>,
        userRainfall: Double,
        userYield: Double,
        chartDescription: String = ""
    ) {
        val curveEntries = curvePoints.map { (x, y) -> Entry(x.toFloat(), y.toFloat()) }
        val curveSet = LineDataSet(curveEntries, "Cubic fit").apply {
            color = Color.parseColor(COLOR_CUBIC)
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        val userEntry = Entry(userRainfall.toFloat(), userYield.toFloat())
        val userSet = LineDataSet(listOf(userEntry), "Your farm").apply {
            color = Color.parseColor(COLOR_USER)
            setCircleColor(color)
            circleRadius = 6f
            lineWidth = 0f
            setDrawValues(true)
            valueTextSize = 10f
        }
        val combined = CombinedData()
        combined.setData(LineData(curveSet, userSet))
        chart.data = combined
        chart.axisLeft.axisMinimum = 0f
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 50f
        applyChartDescription(chart, chartDescription)
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.invalidate()
    }

    fun setupFeatureImportanceChart(
        chart: BarChart,
        features: List<Pair<String, Double>>,
        chartDescription: String = ""
    ) {
        val entries = features.mapIndexed { index, (_, value) ->
            BarEntry(index.toFloat(), value.toFloat())
        }
        val labels = features.map { (name, _) -> name.replace("_", " ") }
        val dataSet = BarDataSet(entries, "Importance").apply {
            color = Color.parseColor(COLOR_CROP)
            valueTextSize = 9f
        }
        chart.data = BarData(dataSet)
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return labels.getOrElse(idx) { "" }
            }
        }
        chart.xAxis.granularity = 1f
        chart.xAxis.setLabelCount(labels.size, false)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }

    private fun styleCropSeasonChart(chart: BarLineChartBase<*>, chartDescription: String) {
        styleWeekRangeChart(
            chart,
            SeasonWeekMapper.CROP_SEASON_START_WEEK,
            SeasonWeekMapper.CROP_SEASON_END_WEEK,
            chartDescription
        )
    }

    fun setupWindPowerRainfallChart(
        chart: BarChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        val season = WindPowerWeekMapper.filterWeeks(weeks)
        setupRainfallChart(chart, season, chartDescription)
        styleWeekRangeChart(
            chart,
            WindPowerWeekMapper.START_WEEK,
            WindPowerWeekMapper.END_WEEK,
            chartDescription
        )
    }

    fun setupWindPowerTemperatureChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        val season = WindPowerWeekMapper.filterWeeks(weeks)
        setupTemperatureChart(chart, season, chartDescription)
        styleWeekRangeChart(
            chart,
            WindPowerWeekMapper.START_WEEK,
            WindPowerWeekMapper.END_WEEK,
            chartDescription
        )
    }

    fun setupWindPowerLineMetricChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        label: String,
        valueSelector: (CalendarWeeklyAnalyzer.CalendarWeekStats) -> Double,
        colorHex: String,
        chartDescription: String = ""
    ) {
        val season = WindPowerWeekMapper.filterWeeks(weeks)
        setupLineMetricChart(chart, season, label, valueSelector, colorHex, chartDescription)
        styleWeekRangeChart(
            chart,
            WindPowerWeekMapper.START_WEEK,
            WindPowerWeekMapper.END_WEEK,
            chartDescription
        )
    }

    fun setupModelYieldComparisonChart(
        chart: BarChart,
        predictions: List<ExtendedYieldModelPredictor.ModelPrediction>,
        chartDescription: String = ""
    ) {
        val entries = predictions.mapIndexed { index, pred ->
            BarEntry(index.toFloat(), safeFloat(pred.yieldTHa))
        }
        val labels = predictions.map { it.name }
        val dataSet = BarDataSet(entries, "Predicted yield (t/ha)").apply {
            color = Color.parseColor(COLOR_CROP)
            valueTextSize = 9f
            setDrawValues(true)
        }
        chart.data = BarData(dataSet)
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String =
                labels.getOrElse(value.toInt()) { "" }
        }
        chart.xAxis.granularity = 1f
        chart.xAxis.setLabelCount(labels.size, false)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }

    fun setupFullYearRainfallChart(
        chart: BarChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        setupRainfallChart(chart, weeks, chartDescription)
        styleWeekRangeChart(chart, 1, CalendarWeeklyAnalyzer.MAX_WEEKS, chartDescription)
    }

    fun setupFullYearTemperatureChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        chartDescription: String = ""
    ) {
        setupTemperatureChart(chart, weeks, chartDescription)
        styleWeekRangeChart(chart, 1, CalendarWeeklyAnalyzer.MAX_WEEKS, chartDescription)
    }

    fun setupFullYearLineMetricChart(
        chart: LineChart,
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        label: String,
        valueSelector: (CalendarWeeklyAnalyzer.CalendarWeekStats) -> Double,
        colorHex: String,
        chartDescription: String = ""
    ) {
        setupLineMetricChart(chart, weeks, label, valueSelector, colorHex, chartDescription)
        styleWeekRangeChart(chart, 1, CalendarWeeklyAnalyzer.MAX_WEEKS, chartDescription)
    }

    private fun styleWeekRangeChart(
        chart: BarLineChartBase<*>,
        startWeek: Int,
        endWeek: Int,
        chartDescription: String
    ) {
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 2f
        chart.xAxis.axisMinimum = (startWeek - 1).toFloat()
        chart.xAxis.axisMaximum = (endWeek + 1).toFloat()
        chart.xAxis.removeAllLimitLines()
        chart.axisLeft.axisMinimum = 0f
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }

    private fun applyChartDescription(chart: com.github.mikephil.charting.charts.Chart<*>, text: String) {
        if (text.isNotBlank()) {
            chart.description.isEnabled = true
            chart.description.text = text
            chart.description.textSize = 10f
        } else {
            chart.description.isEnabled = false
        }
    }

    private fun safeFloat(value: Double): Float =
        if (value.isNaN()) 0f else value.toFloat()

    fun setupHistoricalRainfallByYear(
        chart: BarChart,
        years: List<MonthlyCropSeasonAnalyzer.YearSeasonStats>,
        chartDescription: String = ""
    ) {
        val entries = years.map { Entry(it.year.toFloat(), safeFloat(it.seasonRainfallMm)) }
        val dataSet = BarDataSet(
            entries.map { BarEntry(it.x, it.y) },
            "Season rainfall (mm)"
        ).apply {
            color = Color.parseColor(COLOR_CROP)
            setDrawValues(false)
        }
        chart.data = BarData(dataSet)
        styleYearAxisChart(chart, years, chartDescription)
    }

    fun setupHistoricalTemperatureByYear(
        chart: LineChart,
        years: List<MonthlyCropSeasonAnalyzer.YearSeasonStats>,
        chartDescription: String = ""
    ) {
        val entries = years.map { Entry(it.year.toFloat(), safeFloat(it.seasonTMaxMean)) }
        val dataSet = LineDataSet(entries, "Mean T Max (°C)").apply {
            color = Color.parseColor(COLOR_T_MAX)
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
        }
        chart.data = LineData(dataSet)
        styleYearAxisChart(chart, years, chartDescription)
    }

    fun setupMonthlyRainfallForYear(
        chart: BarChart,
        yearStats: MonthlyCropSeasonAnalyzer.YearSeasonStats,
        chartDescription: String = ""
    ) {
        val entries = yearStats.months.map { month ->
            BarEntry(month.month.toFloat(), safeFloat(month.rainfallMm))
        }
        val dataSet = BarDataSet(entries, "Rainfall (mm)").apply {
            color = Color.parseColor(COLOR_CROP)
            setDrawValues(false)
        }
        chart.data = BarData(dataSet)
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String =
                MonthlyCropSeasonAnalyzer.monthLabel(value.toInt())
        }
        chart.xAxis.granularity = 1f
        chart.xAxis.axisMinimum = 5.5f
        chart.xAxis.axisMaximum = 10.5f
        chart.axisLeft.axisMinimum = 0f
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }

    fun setupYieldTrendChart(
        chart: LineChart,
        points: List<Pair<Int, Double>>,
        label: String,
        chartDescription: String = ""
    ) {
        val entries = points.map { (year, yield) -> Entry(year.toFloat(), safeFloat(yield)) }
        val dataSet = LineDataSet(entries, label).apply {
            color = Color.parseColor(COLOR_CROP)
            setCircleColor(color)
            lineWidth = 2.5f
            circleRadius = 3f
            setDrawValues(false)
        }
        chart.data = LineData(dataSet)
        if (points.isNotEmpty()) {
            chart.xAxis.axisMinimum = (points.first().first - 1).toFloat()
            chart.xAxis.axisMaximum = (points.last().first + 1).toFloat()
        }
        chart.xAxis.granularity = 5f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.axisLeft.axisMinimum = 0f
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }

    private fun styleYearAxisChart(
        chart: BarLineChartBase<*>,
        years: List<MonthlyCropSeasonAnalyzer.YearSeasonStats>,
        chartDescription: String
    ) {
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        if (years.isNotEmpty()) {
            chart.xAxis.axisMinimum = (years.first().year - 1).toFloat()
            chart.xAxis.axisMaximum = (years.last().year + 1).toFloat()
        }
        chart.xAxis.granularity = 5f
        chart.axisLeft.axisMinimum = 0f
        applyChartDescription(chart, chartDescription)
        chart.invalidate()
    }
}
