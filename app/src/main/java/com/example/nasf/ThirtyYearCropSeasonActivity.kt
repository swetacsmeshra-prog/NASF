package com.example.nasf

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.CalendarWeeklyAnalyzer
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.FeatureVectorBuilder
import com.example.nasf.weather.MonthlyCropSeasonAnalyzer
import com.example.nasf.weather.MonthlyCropSeasonCsvExporter
import com.example.nasf.weather.SegregatedCsvRepository
import com.example.nasf.weather.WeatherCsvExporter
import com.example.nasf.weather.WeeklyChartHelper
import com.example.nasf.weather.YieldModelPredictor
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ThirtyYearCropSeasonActivity : AppCompatActivity() {

    private lateinit var summaryText: TextView
    private lateinit var dataSourceText: TextView
    private lateinit var yearDetailTitle: TextView
    private lateinit var rfTitle: TextView
    private lateinit var cubicTitle: TextView
    private lateinit var rfYieldText: TextView
    private lateinit var modelInfoText: TextView
    private lateinit var cubicYieldText: TextView
    private lateinit var seasonRainText: TextView
    private lateinit var yearDropdownLayout: TextInputLayout
    private lateinit var yearDropdown: AutoCompleteTextView
    private lateinit var downloadCsvBtn: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var historicalRainChart: BarChart
    private lateinit var historicalTempChart: LineChart
    private lateinit var yieldTrendChart: LineChart
    private lateinit var rainfallChart: BarChart
    private lateinit var temperatureChart: LineChart
    private lateinit var humidityChart: LineChart
    private lateinit var solarChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var importanceChart: BarChart
    private lateinit var cubicChart: CombinedChart

    private var climateJson: JSONObject? = null
    private var analysisResult: MonthlyCropSeasonAnalyzer.ThirtyYearAnalysisResult? = null
    private var predictor: YieldModelPredictor? = null
    private var predictionsByYear: Map<Int, YieldModelPredictor.PredictionResult> = emptyMap()
    private var segregatedByYear: Map<Int, SegregatedCsvRepository.YearReferenceData> = emptyMap()
    private var yearLabels: List<String> = emptyList()
    private var suppressDropdownCallback = false

    private var latitude = Double.NaN
    private var longitude = Double.NaN
    private var mlPayloadJson = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thirty_year_crop_season)

        summaryText = findViewById(R.id.thirtyYearSummaryText)
        dataSourceText = findViewById(R.id.thirtyYearDataSourceText)
        yearDetailTitle = findViewById(R.id.thirtyYearYearDetailTitle)
        rfTitle = findViewById(R.id.thirtyYearRfTitle)
        cubicTitle = findViewById(R.id.thirtyYearCubicTitle)
        rfYieldText = findViewById(R.id.thirtyYearRfYieldText)
        modelInfoText = findViewById(R.id.thirtyYearModelInfoText)
        cubicYieldText = findViewById(R.id.thirtyYearCubicYieldText)
        seasonRainText = findViewById(R.id.thirtyYearSeasonRainText)
        yearDropdownLayout = findViewById(R.id.thirtyYearYearDropdownLayout)
        yearDropdown = findViewById(R.id.thirtyYearYearDropdown)
        downloadCsvBtn = findViewById(R.id.thirtyYearDownloadCsvBtn)
        progressBar = findViewById(R.id.thirtyYearProgressBar)

        historicalRainChart = findViewById(R.id.thirtyYearHistoricalRainChart)
        historicalTempChart = findViewById(R.id.thirtyYearHistoricalTempChart)
        yieldTrendChart = findViewById(R.id.thirtyYearYieldTrendChart)
        rainfallChart = findViewById(R.id.thirtyYearRainfallChart)
        temperatureChart = findViewById(R.id.thirtyYearTemperatureChart)
        humidityChart = findViewById(R.id.thirtyYearHumidityChart)
        solarChart = findViewById(R.id.thirtyYearSolarChart)
        windChart = findViewById(R.id.thirtyYearWindChart)
        importanceChart = findViewById(R.id.thirtyYearImportanceChart)
        cubicChart = findViewById(R.id.thirtyYearCubicChart)

        readExtras()
        downloadCsvBtn.setOnClickListener { downloadCsv() }
        buildReport()
    }

    private fun readExtras() {
        latitude = intent.getDoubleExtra(CropSessionExtras.LATITUDE, Double.NaN)
        longitude = intent.getDoubleExtra(CropSessionExtras.LONGITUDE, Double.NaN)
        mlPayloadJson = intent.getStringExtra(CropSessionExtras.ML_PAYLOAD_JSON).orEmpty()

        val climateRaw = intent.getStringExtra(CropSessionExtras.CLIMATE_MONTHLY_JSON).orEmpty()
        if (climateRaw.isNotBlank()) {
            climateJson = JSONObject(climateRaw)
        }
    }

    private fun buildReport() {
        val climate = climateJson ?: run {
            showError("30-year climate data is missing.")
            return
        }

        progressBar.visibility = View.VISIBLE
        yearDropdownLayout.isEnabled = false
        downloadCsvBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val analysis = MonthlyCropSeasonAnalyzer.analyze(climate)
                    val segregated = SegregatedCsvRepository.load(applicationContext)
                    val pred = YieldModelPredictor(applicationContext)
                    val byYear = analysis.years.associate { yearStats ->
                        val features = featuresForYear(yearStats, segregated[yearStats.year])
                        yearStats.year to pred.predict(features)
                    }
                    Triple(analysis, segregated, pred to byYear)
                }

                analysisResult = result.first
                segregatedByYear = result.second
                predictor = result.third.first
                predictionsByYear = result.third.second

                bindUi(result.first, result.third.first, result.third.second)
                downloadCsvBtn.isEnabled = true
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                progressBar.visibility = View.GONE
                yearDropdownLayout.isEnabled = analysisResult != null
            }
        }
    }

    private fun bindUi(
        analysis: MonthlyCropSeasonAnalyzer.ThirtyYearAnalysisResult,
        predictor: YieldModelPredictor,
        predictions: Map<Int, YieldModelPredictor.PredictionResult>
    ) {
        val csvYears = segregatedByYear.keys.sorted()
        summaryText.text = getString(
            R.string.thirty_year_summary,
            latitude,
            longitude,
            analysis.startYear,
            analysis.endYear
        ) + "\n" + getString(
            R.string.thirty_year_csv_years_note,
            csvYears.joinToString(", ")
        )

        val monthlyDesc = getString(R.string.thirty_year_desc_monthly)
        val rfTrendDesc = getString(R.string.thirty_year_desc_rf_trend)

        WeeklyChartHelper.setupHistoricalRainfallByYear(
            historicalRainChart, analysis.years, monthlyDesc
        )
        WeeklyChartHelper.setupHistoricalTemperatureByYear(
            historicalTempChart, analysis.years, monthlyDesc
        )

        val trendPoints = analysis.years.mapNotNull { year ->
            predictions[year.year]?.randomForestYieldTHa?.let { year.year to it }
        }
        WeeklyChartHelper.setupYieldTrendChart(
            yieldTrendChart,
            trendPoints,
            "RF yield (t/ha)",
            rfTrendDesc
        )

        yearLabels = analysis.years.map { it.year.toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, yearLabels)
        yearDropdown.setAdapter(adapter)

        yearDropdown.setOnItemClickListener { _, _, position, _ ->
            if (suppressDropdownCallback) return@setOnItemClickListener
            val year = analysis.years.getOrNull(position)?.year ?: return@setOnItemClickListener
            bindSelectedYear(year, predictions[year], predictor)
        }

        val defaultIndex = yearLabels.lastIndex.coerceAtLeast(0)
        selectYearAt(defaultIndex, analysis, predictions, predictor)
    }

    private fun selectYearAt(
        index: Int,
        analysis: MonthlyCropSeasonAnalyzer.ThirtyYearAnalysisResult,
        predictions: Map<Int, YieldModelPredictor.PredictionResult>,
        predictor: YieldModelPredictor
    ) {
        suppressDropdownCallback = true
        yearDropdown.setText(yearLabels[index], false)
        suppressDropdownCallback = false
        analysis.years.getOrNull(index)?.let { yearStats ->
            bindSelectedYear(yearStats.year, predictions[yearStats.year], predictor)
        }
    }

    private fun bindSelectedYear(
        year: Int,
        prediction: YieldModelPredictor.PredictionResult?,
        predictor: YieldModelPredictor
    ) {
        val csvYear = segregatedByYear[year]
        val yearStats = analysisResult?.years?.firstOrNull { it.year == year }
        val features = featuresForYear(yearStats, csvYear)
        val pred = prediction ?: predictor.predict(features)
        val cropDesc = getString(R.string.analytics_desc_crop_season)
        val rfDesc = getString(R.string.analytics_desc_random_forest)
        val cubicDesc = getString(R.string.analytics_desc_cubic)

        yearDetailTitle.text = getString(R.string.thirty_year_year_detail_title, year)
        rfTitle.text = getString(R.string.thirty_year_rf_title, year)
        cubicTitle.text = getString(R.string.thirty_year_cubic_title, year)

        dataSourceText.text = if (csvYear != null) {
            getString(R.string.thirty_year_data_source_csv, csvYear.farmCount)
        } else {
            getString(R.string.thirty_year_data_source_nasa, year)
        }

        rfYieldText.text = getString(R.string.thirty_year_rf_value, pred.randomForestYieldTHa)
        modelInfoText.text = pred.modelInfo
        cubicYieldText.text = getString(R.string.thirty_year_rf_value, pred.cubicYieldTHa)
        seasonRainText.text = getString(
            R.string.thirty_year_season_rain,
            year,
            features.seasonRainfallMm
        )

        val cropWeeks = csvYear?.cropSeasonWeeks
            ?: yearStats?.let { monthlyAsWeeklyProxy(it) }
            ?: emptyList()

        if (cropWeeks.isNotEmpty()) {
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
        }

        WeeklyChartHelper.setupFeatureImportanceChart(importanceChart, pred.topFeatures, rfDesc)

        val curve = predictor.cubicCurvePoints(features.seasonRainfallMm)
        WeeklyChartHelper.setupCubicChart(
            cubicChart,
            curve,
            features.seasonRainfallMm,
            pred.cubicYieldTHa,
            cubicDesc
        )
    }

    private fun featuresForYear(
        yearStats: MonthlyCropSeasonAnalyzer.YearSeasonStats?,
        csvYear: SegregatedCsvRepository.YearReferenceData?
    ): FeatureVectorBuilder.ModelFeatures {
        if (csvYear != null) {
            val soil = csvYear.soilFeatures.ifEmpty {
                yearStats?.let { FeatureVectorBuilder.fromYearSeason(it, mlPayloadJson).soilFeatures }
                    ?: emptyMap()
            }
            return FeatureVectorBuilder.fromWideColumns(csvYear.wideColumns, soil)
        }
        val stats = yearStats ?: throw IllegalStateException("No climate data for selected year.")
        return FeatureVectorBuilder.fromYearSeason(stats, mlPayloadJson)
    }

    private fun monthlyAsWeeklyProxy(
        yearStats: MonthlyCropSeasonAnalyzer.YearSeasonStats
    ): List<CalendarWeeklyAnalyzer.CalendarWeekStats> {
        val monthToWeek = mapOf(6 to 24, 7 to 28, 8 to 33, 9 to 37, 10 to 41)
        return yearStats.months.mapNotNull { month ->
            val week = monthToWeek[month.month] ?: return@mapNotNull null
            CalendarWeeklyAnalyzer.CalendarWeekStats(
                weekNumber = week,
                weekStartYmd = "",
                weekEndYmd = "",
                rainfallTotalMm = month.rainfallMm,
                rainfallMeanMmDay = Double.NaN,
                humidityMeanPct = month.humidityPct,
                solarMeanKwhM2Day = month.solarKwhM2Day,
                tMin = month.tMin,
                tMax = month.tMax,
                windSpeedMeanMs = month.windSpeedMs
            )
        }
    }

    private fun downloadCsv() {
        val analysis = analysisResult ?: run {
            Toast.makeText(this, R.string.analyzed_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        downloadCsvBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val csv = MonthlyCropSeasonCsvExporter.toCsv(latitude, longitude, analysis)
                    val fileName = MonthlyCropSeasonCsvExporter.buildFileName(
                        latitude, longitude, analysis.startYear, analysis.endYear
                    )
                    WeatherCsvExporter.save(this@ThirtyYearCropSeasonActivity, csv, fileName)
                }
                Toast.makeText(
                    this@ThirtyYearCropSeasonActivity,
                    getString(R.string.thirty_year_csv_success, savedPath),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                showError(e.message ?: "Export failed")
            } finally {
                progressBar.visibility = View.GONE
                downloadCsvBtn.isEnabled = analysisResult != null
            }
        }
    }

    private fun showError(message: String) {
        summaryText.text = getString(R.string.thirty_year_error, message)
        downloadCsvBtn.isEnabled = false
        yearDropdownLayout.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}
