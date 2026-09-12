package com.example.nasf

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.CalendarWeeklyAnalyzer
import com.example.nasf.weather.ClimateAnnYieldPredictor
import com.example.nasf.weather.ClimateWeekRange
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.FeatureVectorBuilder
import com.example.nasf.weather.MlPredictionClient
import com.example.nasf.weather.MlSubmitHelper
import com.example.nasf.weather.WeeklyChartHelper
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ClimateAnnYieldReportActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var predictorsText: TextView
    private lateinit var architectureText: TextView
    private lateinit var yieldText: TextView
    private lateinit var metricsText: TextView
    private lateinit var trainingNoteText: TextView
    private lateinit var rangeGroup: RadioGroup
    private lateinit var rangeCrop: RadioButton
    private lateinit var rangeWind: RadioButton
    private lateinit var rangeSowHarvest: RadioButton
    private lateinit var continueBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rainfallChart: BarChart
    private lateinit var temperatureChart: LineChart

    private val mlClient = MlPredictionClient()
    private var weatherJson: JSONObject? = null
    private var calendarResult: CalendarWeeklyAnalyzer.CalendarAnalysisResult? = null
    private var selectedRange = ClimateWeekRange.CROP_23_44

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
        setContentView(R.layout.activity_climate_ann_yield_report)

        summaryText = findViewById(R.id.climateAnnSummaryText)
        predictorsText = findViewById(R.id.climateAnnPredictorsText)
        architectureText = findViewById(R.id.climateAnnArchitectureText)
        yieldText = findViewById(R.id.climateAnnYieldText)
        metricsText = findViewById(R.id.climateAnnMetricsText)
        trainingNoteText = findViewById(R.id.climateAnnTrainingNoteText)
        rangeGroup = findViewById(R.id.climateAnnRangeGroup)
        rangeCrop = findViewById(R.id.climateAnnRangeCrop)
        rangeWind = findViewById(R.id.climateAnnRangeWind)
        rangeSowHarvest = findViewById(R.id.climateAnnRangeSowHarvest)
        continueBtn = findViewById(R.id.climateAnnContinueBtn)
        progressBar = findViewById(R.id.climateAnnProgressBar)
        rainfallChart = findViewById(R.id.climateAnnRainfallChart)
        temperatureChart = findViewById(R.id.climateAnnTemperatureChart)

        readExtras()
        continueBtn.setOnClickListener { submitToMlApi() }
        rangeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedRange = when (checkedId) {
                R.id.climateAnnRangeWind -> ClimateWeekRange.WIND_23_42
                R.id.climateAnnRangeSowHarvest -> ClimateWeekRange.SOWING_HARVEST
                else -> ClimateWeekRange.CROP_23_44
            }
            buildReport()
        }
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
        if (sowingYmd.length != 8) {
            showError("Sowing date is required.")
            return
        }

        progressBar.visibility = View.VISIBLE
        continueBtn.isEnabled = false
        rangeGroup.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val calendar = CalendarWeeklyAnalyzer.analyze(weather, sowingYmd)
                    val features = FeatureVectorBuilder.fromCalendarWeeks(
                        calendar.weeks,
                        selectedRange,
                        sowingYmd,
                        harvestYmd,
                        mlPayloadJson
                    )
                    val predictor = ClimateAnnYieldPredictor(applicationContext)
                    val prediction = predictor.predict(features)
                    Triple(calendar, features, prediction)
                }
                calendarResult = result.first
                bindUi(result.first, result.second, result.third)
                continueBtn.isEnabled = mlUrl.isNotBlank()
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
                rangeGroup.isEnabled = true
            }
        }
    }

    private fun bindUi(
        calendar: CalendarWeeklyAnalyzer.CalendarAnalysisResult,
        features: FeatureVectorBuilder.ModelFeatures,
        prediction: ClimateAnnYieldPredictor.Climate3Prediction
    ) {
        val windowWeeks = selectedRange.filterWeeks(calendar.weeks, sowingYmd, harvestYmd)
        val chartDesc = selectedRange.label()

        summaryText.text = getString(
            R.string.climate_ann_summary,
            latitude,
            longitude,
            calendar.calendarYear,
            selectedRange.label()
        ) + "\n" + getString(R.string.climate_ann_weeks_count, features.cropSeasonWeekCount)

        predictorsText.text = getString(R.string.climate_ann_predictor_rain, features.seasonRainfallMm) + "\n" +
            getString(R.string.climate_ann_predictor_tmin, features.seasonTMinMean) + "\n" +
            getString(R.string.climate_ann_predictor_tmax, features.seasonTMaxMean)

        architectureText.text = getString(R.string.climate_ann_architecture) + "\n" + prediction.architectureLabel

        yieldText.text = getString(R.string.climate_ann_yield_value, prediction.yieldTHa)
        metricsText.text = getString(
            R.string.climate_ann_metrics,
            prediction.metrics.rmseTHa,
            prediction.metrics.stdvResidualTHa,
            prediction.metrics.r2,
            prediction.metrics.maeTHa
        )
        trainingNoteText.text = getString(
            R.string.climate_ann_training_note,
            prediction.trainedOnFarms,
            prediction.trainWeekStart,
            prediction.trainWeekEnd,
            prediction.architectureLabel
        )

        WeeklyChartHelper.setupRainfallChart(rainfallChart, windowWeeks, chartDesc)
        WeeklyChartHelper.setupTemperatureChart(temperatureChart, windowWeeks, chartDesc)
    }

    private fun showError(message: String) {
        summaryText.text = getString(R.string.climate_ann_error, message)
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
                    this@ClimateAnnYieldReportActivity,
                    mlClient, mlUrl, mlPayloadJson, weather, analyzed, useHtmlResult
                )
                MlSubmitHelper.showResultDialog(this@ClimateAnnYieldReportActivity, resultTitle, result, useHtmlResult)
            } catch (e: Exception) {
                MlSubmitHelper.showResultDialog(
                    this@ClimateAnnYieldReportActivity,
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
