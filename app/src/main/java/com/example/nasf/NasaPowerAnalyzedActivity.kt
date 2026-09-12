package com.example.nasf

import android.app.AlertDialog
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.AnalyzedCsvExporter
import com.example.nasf.weather.CalendarWeeklyAnalyzer
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.MlPredictionClient
import com.example.nasf.weather.MlSubmitHelper
import com.example.nasf.weather.ClimateAnnYieldNavigation
import com.example.nasf.weather.WeeklyAnalyticsNavigation
import com.example.nasf.weather.WindPowerModelNavigation
import com.example.nasf.weather.Year2026ClimateNavigation
import com.example.nasf.weather.WeatherCsvExporter
import com.example.nasf.weather.WeeklyWeatherAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NasaPowerAnalyzedActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var downloadBtn: Button
    private lateinit var reportBtn: Button
    private lateinit var climateAnnReportBtn: Button
    private lateinit var windPowerReportBtn: Button
    private lateinit var year2026ReportBtn: Button
    private lateinit var continueBtn: Button
    private lateinit var progressBar: ProgressBar

    private val mlClient = MlPredictionClient()
    private var weatherJson: JSONObject? = null
    private var analysisResult: WeeklyWeatherAnalyzer.AnalysisResult? = null
    private var calendarAnalysisResult: CalendarWeeklyAnalyzer.CalendarAnalysisResult? = null

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
        setContentView(R.layout.activity_nasa_power_analyzed)

        summaryText = findViewById(R.id.analyzedSummaryText)
        downloadBtn = findViewById(R.id.analyzedDownloadBtn)
        reportBtn = findViewById(R.id.analyzedReportBtn)
        climateAnnReportBtn = findViewById(R.id.analyzedClimateAnnReportBtn)
        windPowerReportBtn = findViewById(R.id.analyzedWindPowerReportBtn)
        year2026ReportBtn = findViewById(R.id.analyzedYear2026ReportBtn)
        continueBtn = findViewById(R.id.analyzedContinueBtn)
        progressBar = findViewById(R.id.analyzedProgressBar)

        readExtras()
        setupListeners()
        runAnalysis()
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

    private fun setupListeners() {
        downloadBtn.setOnClickListener { downloadAnalyzedCsv() }
        reportBtn.setOnClickListener { openAnalyticsReport() }
        climateAnnReportBtn.setOnClickListener { openClimateAnnReport() }
        windPowerReportBtn.setOnClickListener { openWindPowerReport() }
        year2026ReportBtn.setOnClickListener { openYear2026Report() }
        continueBtn.setOnClickListener { submitToMlApi() }
    }

    private fun runAnalysis() {
        val weather = weatherJson ?: run {
            showAnalysisError("Weather data is missing.")
            return
        }
        if (latitude.isNaN() || longitude.isNaN()) {
            showAnalysisError("Location is missing.")
            return
        }

        progressBar.visibility = View.VISIBLE
        downloadBtn.isEnabled = false
        reportBtn.isEnabled = false
        climateAnnReportBtn.isEnabled = false
        windPowerReportBtn.isEnabled = false
        year2026ReportBtn.isEnabled = false
        continueBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val calendar = try {
                        CalendarWeeklyAnalyzer.analyze(weather, sowingYmd)
                    } catch (_: Exception) {
                        null
                    }
                    val season = try {
                        WeeklyWeatherAnalyzer.analyze(weather, sowingYmd, harvestYmd)
                    } catch (_: Exception) {
                        null
                    }
                    Pair(season, calendar)
                }
                analysisResult = result.first
                calendarAnalysisResult = result.second

                when {
                    result.first != null -> {
                        summaryText.text = WeeklyWeatherAnalyzer.buildSummaryText(
                            latitude, longitude, sowingYmd, harvestYmd, result.first!!
                        )
                    }
                    result.second != null -> {
                        summaryText.text = buildCalendarSummary(result.second!!)
                    }
                    else -> throw IllegalStateException("Could not analyze weather data.")
                }

                downloadBtn.isEnabled = result.first != null
                reportBtn.isEnabled = sowingYmd.length == 8 && weatherJson != null
                climateAnnReportBtn.isEnabled = isClimateAnnReportAvailable()
                windPowerReportBtn.isEnabled = isWindPowerReportAvailable()
                year2026ReportBtn.isEnabled = isYear2026ReportAvailable()
                continueBtn.isEnabled = mlUrl.isNotBlank() &&
                    (result.first != null || result.second != null)

                if (result.second == null && sowingYmd.length == 8) {
                    summaryText.append("\n\n${getString(R.string.analytics_analyzed_note_open_report)}")
                } else if (result.second != null && countCropSeasonWeeksWithData(result.second!!) < 15) {
                    summaryText.append("\n\n${getString(R.string.analytics_analyzed_note_refetch)}")
                }
            } catch (e: Exception) {
                showAnalysisError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun buildCalendarSummary(calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult): String {
        val cropWeeks = com.example.nasf.weather.SeasonWeekMapper.cropSeasonWeeks(calendar.weeks)
        val sb = StringBuilder()
        sb.append("Location: ").append(latitude).append("° lat, ").append(longitude).append("° lon\n")
        sb.append("Calendar year: ").append(calendar.calendarYear).append("\n")
        sb.append("Crop season: ").append(com.example.nasf.weather.SeasonWeekMapper.cropSeasonLabel()).append('\n')
        sb.append(getString(R.string.analytics_crop_weeks_count, cropWeeks.size))
        sb.append("\n\n").append(getString(R.string.analytics_analyzed_tap_report))
        return sb.toString().trim()
    }

    private fun countCropSeasonWeeksWithData(calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult): Int =
        com.example.nasf.weather.SeasonWeekMapper.cropSeasonWeeks(calendar.weeks)
            .count { !it.rainfallTotalMm.isNaN() }

    private fun openAnalyticsReport() {
        val weather = weatherJson ?: return
        WeeklyAnalyticsNavigation.launch(
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
            useHtmlResult = useHtmlResult,
            calendarAnalyzedJson = calendarAnalysisResult?.analyzedJson?.toString().orEmpty()
        )
    }

    private fun isClimateAnnReportAvailable(): Boolean =
        weatherJson != null && sowingYmd.length == 8

    private fun openClimateAnnReport() {
        val weather = weatherJson ?: return
        if (!isClimateAnnReportAvailable()) {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        ClimateAnnYieldNavigation.launch(
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

    private fun showAnalysisError(message: String) {
        summaryText.text = getString(R.string.analyzed_error, message)
        downloadBtn.isEnabled = false
        reportBtn.isEnabled = false
        climateAnnReportBtn.isEnabled = false
        windPowerReportBtn.isEnabled = false
        year2026ReportBtn.isEnabled = false
        continueBtn.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun isWindPowerReportAvailable(): Boolean {
        if (weatherJson == null || sowingYmd.length != 8) return false
        val year = sowingYmd.substring(0, 4).toIntOrNull() ?: return false
        return year >= 2026
    }

    private fun isYear2026ReportAvailable(): Boolean = isWindPowerReportAvailable()

    private fun openWindPowerReport() {
        val weather = weatherJson ?: return
        if (!isWindPowerReportAvailable()) {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
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

    private fun openYear2026Report() {
        val weather = weatherJson ?: return
        if (!isYear2026ReportAvailable()) {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        Year2026ClimateNavigation.launch(
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

    private fun downloadAnalyzedCsv() {
        val result = analysisResult ?: run {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        downloadBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val csv = AnalyzedCsvExporter.toCsv(
                        latitude, longitude, sowingYmd, harvestYmd, result
                    )
                    val fileName = AnalyzedCsvExporter.buildFileName(
                        latitude, longitude, sowingYmd, harvestYmd
                    )
                    WeatherCsvExporter.save(this@NasaPowerAnalyzedActivity, csv, fileName)
                }
                Toast.makeText(
                    this@NasaPowerAnalyzedActivity,
                    getString(R.string.analyzed_csv_download_success, savedPath),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NasaPowerAnalyzedActivity,
                    getString(R.string.analyzed_error, e.message ?: "Export failed"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                downloadBtn.isEnabled = analysisResult != null
            }
        }
    }

    private fun submitToMlApi() {
        val weather = weatherJson ?: run {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val analyzed = calendarAnalysisResult?.analyzedJson
            ?: analysisResult?.analyzedJson
            ?: run {
                Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
                return
            }
        if (mlUrl.isBlank()) {
            Toast.makeText(this, R.string.weather_error_ml_url, Toast.LENGTH_SHORT).show()
            return
        }

        continueBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = MlSubmitHelper.submitAndFormat(
                    this@NasaPowerAnalyzedActivity,
                    mlClient, mlUrl, mlPayloadJson, weather, analyzed, useHtmlResult
                )
                MlSubmitHelper.showResultDialog(this@NasaPowerAnalyzedActivity, resultTitle, result, useHtmlResult)
            } catch (e: Exception) {
                MlSubmitHelper.showResultDialog(this@NasaPowerAnalyzedActivity, "Error", e.message ?: "Unknown error", false)
            } finally {
                progressBar.visibility = View.GONE
                continueBtn.isEnabled = (analysisResult != null || calendarAnalysisResult != null) &&
                    mlUrl.isNotBlank()
            }
        }
    }
}
