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
import com.example.nasf.weather.FeatureVectorBuilder
import com.example.nasf.weather.MlPredictionClient
import com.example.nasf.weather.MlSubmitHelper
import com.example.nasf.weather.SeasonWeekMapper
import com.example.nasf.weather.SegregatedCsvExporter
import com.example.nasf.weather.WeatherCsvExporter
import com.example.nasf.weather.WeeklyChartHelper
import com.example.nasf.weather.YieldModelPredictor
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeeklyAnalyticsReportActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var rfYieldText: TextView
    private lateinit var modelInfoText: TextView
    private lateinit var cubicYieldText: TextView
    private lateinit var segregatedPreviewText: TextView
    private lateinit var downloadSegregatedBtn: Button
    private lateinit var continueBtn: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var rainfallChart: BarChart
    private lateinit var temperatureChart: LineChart
    private lateinit var humidityChart: LineChart
    private lateinit var solarChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var importanceChart: BarChart
    private lateinit var cubicChart: CombinedChart

    private val mlClient = MlPredictionClient()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_analytics_report)

        summaryText = findViewById(R.id.analyticsSummaryText)
        rfYieldText = findViewById(R.id.analyticsRfYieldText)
        modelInfoText = findViewById(R.id.analyticsModelInfoText)
        cubicYieldText = findViewById(R.id.analyticsCubicYieldText)
        segregatedPreviewText = findViewById(R.id.analyticsSegregatedPreviewText)
        downloadSegregatedBtn = findViewById(R.id.analyticsDownloadSegregatedBtn)
        continueBtn = findViewById(R.id.analyticsContinueBtn)
        progressBar = findViewById(R.id.analyticsProgressBar)

        rainfallChart = findViewById(R.id.analyticsRainfallChart)
        temperatureChart = findViewById(R.id.analyticsTemperatureChart)
        humidityChart = findViewById(R.id.analyticsHumidityChart)
        solarChart = findViewById(R.id.analyticsSolarChart)
        windChart = findViewById(R.id.analyticsWindChart)
        importanceChart = findViewById(R.id.analyticsImportanceChart)
        cubicChart = findViewById(R.id.analyticsCubicChart)

        readExtras()
        continueBtn.setOnClickListener { submitToMlApi() }
        downloadSegregatedBtn.setOnClickListener { downloadSegregatedCsv() }
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

        progressBar.visibility = View.VISIBLE
        continueBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val calendar = CalendarWeeklyAnalyzer.analyze(weather, sowingYmd)
                    val features = FeatureVectorBuilder.fromCalendarAnalysis(calendar, mlPayloadJson)
                    val predictor = YieldModelPredictor(applicationContext)
                    val prediction = predictor.predict(features)
                    Triple(calendar, features, prediction to predictor)
                }

                val calendar = result.first
                val features = result.second
                val (prediction, predictor) = result.third

                calendarResult = calendar
                bindUi(calendar, features, prediction, predictor)
                continueBtn.isEnabled = mlUrl.isNotBlank()
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun bindUi(
        calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult,
        features: FeatureVectorBuilder.ModelFeatures,
        prediction: YieldModelPredictor.PredictionResult,
        predictor: YieldModelPredictor
    ) {
        summaryText.text = getString(
            R.string.analytics_farm_summary,
            latitude,
            longitude,
            calendar.calendarYear,
            SeasonWeekMapper.cropSeasonLabel()
        ) + "\n" + getString(
            R.string.analytics_crop_weeks_count,
            features.cropSeasonWeekCount
        ) + "\n" + getString(
            R.string.analytics_season_rain,
            features.seasonRainfallMm
        )

        rfYieldText.text = getString(R.string.analytics_rf_value, prediction.randomForestYieldTHa)
        modelInfoText.text = prediction.modelInfo
        cubicYieldText.text = getString(R.string.analytics_rf_value, prediction.cubicYieldTHa)

        segregatedPreviewText.text = SegregatedCsvExporter.buildPreviewText(calendar.wideColumns)
        downloadSegregatedBtn.isEnabled = true

        val cropWeeks = SeasonWeekMapper.cropSeasonWeeks(calendar.weeks)
        val cropDesc = getString(R.string.analytics_desc_crop_season)
        val rfDesc = getString(R.string.analytics_desc_random_forest)
        val cubicDesc = getString(R.string.analytics_desc_cubic)

        WeeklyChartHelper.setupRainfallChart(rainfallChart, cropWeeks, cropDesc)
        WeeklyChartHelper.setupTemperatureChart(temperatureChart, cropWeeks, cropDesc)
        WeeklyChartHelper.setupLineMetricChart(
            humidityChart, cropWeeks, "RH %", { it.humidityMeanPct }, "#00838F", cropDesc
        )
        WeeklyChartHelper.setupLineMetricChart(
            solarChart, cropWeeks, "kWh/m²/day", { it.solarMeanKwhM2Day }, "#F9A825", cropDesc
        )
        WeeklyChartHelper.setupLineMetricChart(
            windChart, cropWeeks, "m/s", { it.windSpeedMeanMs }, "#5D4037", cropDesc
        )
        WeeklyChartHelper.setupFeatureImportanceChart(importanceChart, prediction.topFeatures, rfDesc)

        val curve = predictor.cubicCurvePoints(features.seasonRainfallMm)
        WeeklyChartHelper.setupCubicChart(
            cubicChart,
            curve,
            features.seasonRainfallMm,
            prediction.cubicYieldTHa,
            cubicDesc
        )
    }

    private fun downloadSegregatedCsv() {
        val calendar = calendarResult ?: run {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        downloadSegregatedBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val csv = SegregatedCsvExporter.toCsv(
                        latitude, longitude, sowingYmd, harvestYmd,
                        calendar.calendarYear, calendar.wideColumns
                    )
                    val fileName = SegregatedCsvExporter.buildFileName(
                        latitude, longitude, calendar.calendarYear
                    )
                    WeatherCsvExporter.save(this@WeeklyAnalyticsReportActivity, csv, fileName)
                }
                Toast.makeText(
                    this@WeeklyAnalyticsReportActivity,
                    getString(R.string.analytics_segregated_download_success, savedPath),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                showError(e.message ?: "Export failed")
            } finally {
                progressBar.visibility = View.GONE
                downloadSegregatedBtn.isEnabled = calendarResult != null
            }
        }
    }

    private fun showError(message: String) {
        summaryText.text = getString(R.string.analytics_error, message)
        continueBtn.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun submitToMlApi() {
        val weather = weatherJson ?: run {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val analyzed = calendarResult?.analyzedJson ?: run {
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
                    this@WeeklyAnalyticsReportActivity,
                    mlClient, mlUrl, mlPayloadJson, weather, analyzed, useHtmlResult
                )
                MlSubmitHelper.showResultDialog(this@WeeklyAnalyticsReportActivity, resultTitle, result, useHtmlResult)
            } catch (e: Exception) {
                MlSubmitHelper.showResultDialog(this@WeeklyAnalyticsReportActivity, "Error", e.message ?: "Unknown error", false)
            } finally {
                progressBar.visibility = View.GONE
                continueBtn.isEnabled = calendarResult != null && mlUrl.isNotBlank()
            }
        }
    }
}
