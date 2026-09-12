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
import com.example.nasf.weather.ExtendedYieldModelPredictor
import com.example.nasf.weather.FeatureVectorBuilder
import com.example.nasf.weather.MlPredictionClient
import com.example.nasf.weather.MlSubmitHelper
import com.example.nasf.weather.SeasonStatisticsCalculator
import com.example.nasf.weather.WeeklyChartHelper
import com.example.nasf.weather.WindPowerWeekMapper
import com.example.nasf.weather.Year2026ClimateNavigation
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WindPowerModelReportActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var rfText: TextView
    private lateinit var cubicText: TextView
    private lateinit var xgbText: TextView
    private lateinit var annText: TextView
    private lateinit var svmText: TextView
    private lateinit var openYearReportBtn: Button
    private lateinit var continueBtn: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var temperatureChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var solarChart: LineChart
    private lateinit var rainfallChart: BarChart
    private lateinit var comparisonChart: BarChart
    private lateinit var rfImportanceChart: BarChart
    private lateinit var xgbImportanceChart: BarChart
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
    private var flowType = ""
    private var crop = ""
    private var state = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wind_power_model_report)

        summaryText = findViewById(R.id.windPowerSummaryText)
        rfText = findViewById(R.id.windPowerRfText)
        cubicText = findViewById(R.id.windPowerCubicText)
        xgbText = findViewById(R.id.windPowerXgbText)
        annText = findViewById(R.id.windPowerAnnText)
        svmText = findViewById(R.id.windPowerSvmText)
        openYearReportBtn = findViewById(R.id.windPowerOpenYearReportBtn)
        continueBtn = findViewById(R.id.windPowerContinueBtn)
        progressBar = findViewById(R.id.windPowerProgressBar)

        temperatureChart = findViewById(R.id.windPowerTemperatureChart)
        windChart = findViewById(R.id.windPowerWindChart)
        solarChart = findViewById(R.id.windPowerSolarChart)
        rainfallChart = findViewById(R.id.windPowerRainfallChart)
        comparisonChart = findViewById(R.id.windPowerComparisonChart)
        rfImportanceChart = findViewById(R.id.windPowerRfImportanceChart)
        xgbImportanceChart = findViewById(R.id.windPowerXgbImportanceChart)
        cubicChart = findViewById(R.id.windPowerCubicChart)

        readExtras()
        openYearReportBtn.setOnClickListener { openYearReport() }
        continueBtn.setOnClickListener { submitToMlApi() }
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
        continueBtn.isEnabled = false
        openYearReportBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val calendar = CalendarWeeklyAnalyzer.analyze(weather, sowingYmd)
                    val features = FeatureVectorBuilder.fromWindPowerWeeks(calendar.weeks, mlPayloadJson)
                    val stats = SeasonStatisticsCalculator.fromCalendarWeeks(calendar.weeks, calendar.calendarYear)
                    val predictor = ExtendedYieldModelPredictor(applicationContext)
                    val predictions = predictor.predict(features)
                    Triple(calendar, stats, predictions to predictor)
                }

                calendarResult = result.first
                bindUi(result.first, result.second, result.third.first, result.third.second)
                continueBtn.isEnabled = mlUrl.isNotBlank()
                openYearReportBtn.isEnabled = result.first.calendarYear >= 2026
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun bindUi(
        calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult,
        stats: SeasonStatisticsCalculator.WindPowerSeasonStats,
        predictions: ExtendedYieldModelPredictor.ExtendedPredictionResult,
        predictor: ExtendedYieldModelPredictor
    ) {
        val seasonDesc = getString(R.string.wind_power_desc_season)
        val sowDisplay = formatYmdDisplay(sowingYmd)
        val harDisplay = formatYmdDisplay(harvestYmd)

        summaryText.text = getString(
            R.string.wind_power_summary,
            latitude,
            longitude,
            stats.calendarYear,
            WindPowerWeekMapper.seasonLabel()
        ) + "\n" + getString(R.string.wind_power_season_dates, sowDisplay, harDisplay) + "\n" +
            getString(
                R.string.wind_power_stats_line,
                stats.seasonRainfallMm,
                stats.tMinMean,
                stats.tMinStdv,
                stats.tMaxMean,
                stats.tMaxStdv,
                stats.windMean,
                stats.windStdv
            ) + "\n" + getString(
                R.string.analytics_crop_weeks_count,
                WindPowerWeekMapper.filterWeeks(calendar.weeks).size
            )

        WeeklyChartHelper.setupWindPowerTemperatureChart(temperatureChart, calendar.weeks, seasonDesc)
        WeeklyChartHelper.setupWindPowerLineMetricChart(
            windChart, calendar.weeks, "Wind (m/s)", { it.windSpeedMeanMs }, "#5D4037", seasonDesc
        )
        WeeklyChartHelper.setupWindPowerLineMetricChart(
            solarChart, calendar.weeks, "kWh/m²/day", { it.solarMeanKwhM2Day }, "#F9A825", seasonDesc
        )
        WeeklyChartHelper.setupWindPowerRainfallChart(rainfallChart, calendar.weeks, seasonDesc)

        WeeklyChartHelper.setupModelYieldComparisonChart(
            comparisonChart,
            predictions.allModels,
            "Algorithm comparison · weeks 23–44"
        )

        bindModelCard(rfText, predictions.randomForest)
        bindModelCard(cubicText, predictions.cubic)
        bindModelCard(xgbText, predictions.xgboost)
        bindModelCard(annText, predictions.ann)
        bindModelCard(svmText, predictions.svm)

        WeeklyChartHelper.setupFeatureImportanceChart(
            rfImportanceChart,
            predictions.randomForest.topFeatures,
            predictions.randomForest.infoLine
        )
        if (predictions.xgboost.topFeatures.isNotEmpty()) {
            WeeklyChartHelper.setupFeatureImportanceChart(
                xgbImportanceChart,
                predictions.xgboost.topFeatures,
                predictions.xgboost.infoLine
            )
        }

        val features = FeatureVectorBuilder.fromWindPowerWeeks(calendar.weeks, mlPayloadJson)
        val curve = predictor.cubicCurvePoints(features.seasonRainfallMm)
        WeeklyChartHelper.setupCubicChart(
            cubicChart,
            curve,
            features.seasonRainfallMm,
            predictions.cubic.yieldTHa,
            predictions.cubic.infoLine
        )
    }

    private fun bindModelCard(textView: TextView, model: ExtendedYieldModelPredictor.ModelPrediction) {
        textView.text = getString(R.string.wind_power_model_yield, model.name, model.yieldTHa) + "\n" +
            getString(
                R.string.wind_power_model_rmse,
                model.metrics.rmseTHa,
                model.metrics.stdvResidualTHa,
                model.metrics.r2
            ) + "\n" + model.infoLine
    }

    private fun formatYmdDisplay(ymd: String): String {
        if (ymd.length != 8) return ymd
        return "${ymd.substring(0, 4)}-${ymd.substring(4, 6)}-${ymd.substring(6, 8)}"
    }

    private fun openYearReport() {
        val weather = weatherJson ?: return
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

    private fun showError(message: String) {
        summaryText.text = getString(R.string.wind_power_error, message)
        continueBtn.isEnabled = false
        openYearReportBtn.isEnabled = false
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
                    this@WindPowerModelReportActivity,
                    mlClient, mlUrl, mlPayloadJson, weather, analyzed, useHtmlResult
                )
                MlSubmitHelper.showResultDialog(this@WindPowerModelReportActivity, resultTitle, result, useHtmlResult)
            } catch (e: Exception) {
                MlSubmitHelper.showResultDialog(
                    this@WindPowerModelReportActivity,
                    "Error",
                    e.message ?: "Unknown error",
                    false
                )
            } finally {
                progressBar.visibility = View.GONE
                continueBtn.isEnabled = mlUrl.isNotBlank() && calendarResult != null
            }
        }
    }
}
