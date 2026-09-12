package com.example.nasf

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.CalendarWeeklyAnalyzer
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.WeeklyChartHelper
import com.example.nasf.weather.WindPowerModelNavigation
import com.example.nasf.weather.YearStatisticsCalculator
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class Year2026ClimateReportActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var openSeasonBtn: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var temperatureChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var solarChart: LineChart
    private lateinit var humidityChart: LineChart
    private lateinit var rainfallChart: BarChart

    private var weatherJson: JSONObject? = null
    private var calendarResult: CalendarWeeklyAnalyzer.CalendarAnalysisResult? = null

    private var latitude = Double.NaN
    private var longitude = Double.NaN
    private var sowingYmd = ""
    private var harvestYmd = ""
    private var mlUrl = ""
    private var mlPayloadJson = ""
    private var resultTitle = "Yield Prediction"
    private var useHtmlResult = false
    private var flowType = ""
    private var crop = ""
    private var state = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_year2026_climate_report)

        summaryText = findViewById(R.id.year2026SummaryText)
        openSeasonBtn = findViewById(R.id.year2026OpenSeasonReportBtn)
        progressBar = findViewById(R.id.year2026ProgressBar)

        temperatureChart = findViewById(R.id.year2026TemperatureChart)
        windChart = findViewById(R.id.year2026WindChart)
        solarChart = findViewById(R.id.year2026SolarChart)
        humidityChart = findViewById(R.id.year2026HumidityChart)
        rainfallChart = findViewById(R.id.year2026RainfallChart)

        readExtras()
        openSeasonBtn.setOnClickListener { openSeasonModelReport() }
        buildReport()
    }

    private fun readExtras() {
        latitude = intent.getDoubleExtra(CropSessionExtras.LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CropSessionExtras.LONGITUDE, Double.NaN)
        sowingYmd = intent.getStringExtra(CropSessionExtras.SOWING_DATE_YMD).orEmpty()
        harvestYmd = intent.getStringExtra(CropSessionExtras.HARVEST_DATE_YMD).orEmpty()
        mlUrl = intent.getStringExtra(CropSessionExtras.ML_URL).orEmpty()
        mlPayloadJson = intent.getStringExtra(CropSessionExtras.ML_PAYLOAD_JSON).orEmpty()
        resultTitle = intent.getStringExtra(CropSessionExtras.RESULT_TITLE) ?: "Yield Prediction"
        useHtmlResult = intent.getBooleanExtra(CropSessionExtras.USE_HTML_RESULT, false)
        flowType = intent.getStringExtra(CropSessionExtras.FLOW_TYPE).orEmpty()
        crop = intent.getStringExtra(CropSessionExtras.CROP).orEmpty()
        state = intent.getStringExtra(CropSessionExtras.STATE).orEmpty()

        val weatherRaw = intent.getStringExtra(CropSessionExtras.WEATHER_JSON).orEmpty()
        if (weatherRaw.isNotBlank()) {
            weatherJson = JSONObject(weatherRaw)
        }
    }

    private fun buildReport() {
        val weather = weatherJson ?: run {
            showError("Weather data is missing.")
            return
        }
        if (sowingYmd.length != 8) {
            showError("Sowing date is required.")
            return
        }

        progressBar.visibility = View.VISIBLE
        openSeasonBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val calendar = CalendarWeeklyAnalyzer.analyze(weather, sowingYmd)
                    val stats = YearStatisticsCalculator.fromCalendarWeeks(calendar.weeks, calendar.calendarYear)
                    calendar to stats
                }
                calendarResult = result.first
                bindUi(result.first, result.second)
                openSeasonBtn.isEnabled = result.second.calendarYear >= 2026
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun bindUi(
        calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult,
        stats: YearStatisticsCalculator.FullYearStats
    ) {
        val yearDesc = getString(R.string.year2026_desc)

        summaryText.text = getString(
            R.string.year2026_summary,
            latitude,
            longitude,
            stats.calendarYear
        ) + "\n" + getString(
            R.string.year2026_stats_line,
            stats.totalRainfallMm,
            stats.tMinMean,
            stats.tMinStdv,
            stats.tMaxMean,
            stats.tMaxStdv,
            stats.windMean,
            stats.windStdv
        ) + "\n" + getString(R.string.year2026_weeks_count, stats.weeksWithData)

        WeeklyChartHelper.setupFullYearTemperatureChart(temperatureChart, calendar.weeks, yearDesc)
        WeeklyChartHelper.setupFullYearLineMetricChart(
            windChart, calendar.weeks, "Wind (m/s)", { it.windSpeedMeanMs }, "#5D4037", yearDesc
        )
        WeeklyChartHelper.setupFullYearLineMetricChart(
            solarChart, calendar.weeks, "kWh/m²/day", { it.solarMeanKwhM2Day }, "#F9A825", yearDesc
        )
        WeeklyChartHelper.setupFullYearLineMetricChart(
            humidityChart, calendar.weeks, "RH %", { it.humidityMeanPct }, "#00838F", yearDesc
        )
        WeeklyChartHelper.setupFullYearRainfallChart(rainfallChart, calendar.weeks, yearDesc)
    }

    private fun openSeasonModelReport() {
        val weather = weatherJson ?: return
        WindPowerModelNavigation.launch(
            activity = this,
            weatherJson = weather,
            latitude = latitude,
            longitude = longitude,
            sowingDateYmd = sowingYmd,
            harvestDateYmd = harvestYmd,
            flowType = flowType,
            mlUrl = mlUrl,
            mlPayloadJson = mlPayloadJson,
            crop = crop,
            state = state,
            resultTitle = resultTitle,
            useHtmlResult = useHtmlResult
        )
    }

    private fun showError(message: String) {
        summaryText.text = getString(R.string.year2026_error, message)
        openSeasonBtn.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
