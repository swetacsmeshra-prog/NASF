package com.example.nasf

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.AnalyzedNavigation
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.LocationAutofill
import com.example.nasf.weather.NasaPowerClient
import com.example.nasf.weather.NasaPowerMonthlyClient
import com.example.nasf.weather.NasaPowerMonthlyRequest
import com.example.nasf.weather.NasaPowerRequest
import com.example.nasf.weather.NetworkHelper
import com.example.nasf.weather.WeatherConfig
import com.example.nasf.weather.WeatherCsvExporter
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.nasf.weather.WeatherMonthlyCsvExporter
import org.json.JSONObject

class WeatherActivity : AppCompatActivity() {

    private lateinit var startField: TextInputEditText
    private lateinit var endField: TextInputEditText
    private lateinit var latitudeField: TextInputEditText
    private lateinit var longitudeField: TextInputEditText
    private lateinit var communityField: AutoCompleteTextView
    private lateinit var parametersField: TextInputEditText
    private lateinit var formatField: AutoCompleteTextView
    private lateinit var unitsField: AutoCompleteTextView
    private lateinit var userField: TextInputEditText
    private lateinit var headerField: AutoCompleteTextView
    private lateinit var timeStandardField: AutoCompleteTextView
    private lateinit var siteElevationField: TextInputEditText
    private lateinit var windElevationField: TextInputEditText
    private lateinit var windSurfaceField: AutoCompleteTextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fetchBtn: Button
    private lateinit var fetch30YearBtn: Button
    private lateinit var retryBtn: Button
    private lateinit var locationAutofill: LocationAutofill

    private val nasaClient = NasaPowerClient()
    private val nasaMonthlyClient = NasaPowerMonthlyClient()

    private var mlUrl = ""
    private var mlPayloadJson = ""
    private var resultTitle = "Yield Prediction"
    private var useHtmlResult = false
    private var flowType = ""
    private var crop = ""
    private var state = ""
    private var cropSowingYmd = ""
    private var cropHarvestYmd = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        bindViews()
        setupDropdowns()
        readMlExtras()
        prefillForm()
        LocationAutofill(this, latitudeField, longitudeField).also { locationAutofill = it }.start()
        setupListeners()
    }

    private fun bindViews() {
        startField = findViewById(R.id.weatherStart)
        endField = findViewById(R.id.weatherEnd)
        latitudeField = findViewById(R.id.weatherLatitude)
        longitudeField = findViewById(R.id.weatherLongitude)
        communityField = findViewById(R.id.weatherCommunity)
        parametersField = findViewById(R.id.weatherParameters)
        formatField = findViewById(R.id.weatherFormat)
        unitsField = findViewById(R.id.weatherUnits)
        userField = findViewById(R.id.weatherUser)
        headerField = findViewById(R.id.weatherHeader)
        timeStandardField = findViewById(R.id.weatherTimeStandard)
        siteElevationField = findViewById(R.id.weatherSiteElevation)
        windElevationField = findViewById(R.id.weatherWindElevation)
        windSurfaceField = findViewById(R.id.weatherWindSurface)
        progressBar = findViewById(R.id.weatherProgressBar)
        fetchBtn = findViewById(R.id.weatherFetchBtn)
        fetch30YearBtn = findViewById(R.id.weatherFetch30YearBtn)
        retryBtn = findViewById(R.id.weatherRetryBtn)
    }

    private fun setupDropdowns() {
        setupDropdown(communityField, WeatherConfig.COMMUNITIES, WeatherConfig.DEFAULT_COMMUNITY)
        setupDropdown(formatField, WeatherConfig.FORMATS, WeatherConfig.DEFAULT_FORMAT)
        setupDropdown(unitsField, WeatherConfig.UNITS, WeatherConfig.DEFAULT_UNITS)
        setupDropdown(headerField, WeatherConfig.HEADER_OPTIONS, WeatherConfig.DEFAULT_HEADER)
        setupDropdown(timeStandardField, WeatherConfig.TIME_STANDARDS, WeatherConfig.DEFAULT_TIME_STANDARD)
        setupDropdown(windSurfaceField, WeatherConfig.WIND_SURFACES, WeatherConfig.DEFAULT_WIND_SURFACE)
    }

    private fun setupDropdown(view: AutoCompleteTextView, options: List<String>, default: String) {
        view.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
        )
        view.setText(default, false)
    }

    private fun readMlExtras() {
        flowType = intent.getStringExtra(CropSessionExtras.FLOW_TYPE).orEmpty()
        crop = intent.getStringExtra(CropSessionExtras.CROP).orEmpty()
        state = intent.getStringExtra(CropSessionExtras.STATE).orEmpty()
        mlUrl = intent.getStringExtra(CropSessionExtras.ML_URL).orEmpty()
        mlPayloadJson = intent.getStringExtra(CropSessionExtras.ML_PAYLOAD_JSON).orEmpty()
        resultTitle = intent.getStringExtra(CropSessionExtras.RESULT_TITLE) ?: "Yield Prediction"
        useHtmlResult = intent.getBooleanExtra(CropSessionExtras.USE_HTML_RESULT, false)
        cropSowingYmd = intent.getStringExtra(CropSessionExtras.SOWING_DATE_YMD).orEmpty()
        cropHarvestYmd = intent.getStringExtra(CropSessionExtras.HARVEST_DATE_YMD).orEmpty()
    }

    private fun prefillForm() {
        val sowingYmd = intent.getStringExtra(CropSessionExtras.SOWING_DATE_YMD).orEmpty()
        val harvestYmd = intent.getStringExtra(CropSessionExtras.HARVEST_DATE_YMD).orEmpty()
        val lat = intent.getDoubleExtra(CropSessionExtras.LATITUDE, Double.NaN)
        val lon = intent.getDoubleExtra(CropSessionExtras.LONGITUDE, Double.NaN)

        if (sowingYmd.length == 8) startField.setText(sowingYmd)
        if (harvestYmd.length == 8) endField.setText(harvestYmd)
        if (!lat.isNaN()) latitudeField.setText(lat.toString())
        if (!lon.isNaN()) longitudeField.setText(lon.toString())

        val fromCropFlow = mlPayloadJson.isNotBlank()
        if (fromCropFlow) {
            communityField.setText(WeatherConfig.DEFAULT_MONTHLY_COMMUNITY, false)
            parametersField.setText(WeatherConfig.FULL_YEAR_ANALYZED_PARAMETERS)
            if (sowingYmd.length == 8) {
                val year = sowingYmd.substring(0, 4)
                startField.setText("${year}0101")
                endField.setText("${year}1231")
            }
        } else {
            parametersField.setText(WeatherConfig.DEFAULT_PARAMETERS)
        }
        userField.setText(WeatherConfig.DEFAULT_USER)
        siteElevationField.setText(WeatherConfig.DEFAULT_SITE_ELEVATION)
        windElevationField.setText(WeatherConfig.DEFAULT_WIND_ELEVATION)
    }

    private fun setupListeners() {
        fetchBtn.setOnClickListener { fetchNasaPower() }
        fetch30YearBtn.setOnClickListener { fetch30YearMonthly() }
        retryBtn.setOnClickListener { fetchNasaPower() }
    }

    private fun buildRequestFromForm(): NasaPowerRequest? {
        val start = startField.text?.toString()?.trim().orEmpty()
        val end = endField.text?.toString()?.trim().orEmpty()
        val lat = latitudeField.text?.toString()?.toDoubleOrNull()
        val lon = longitudeField.text?.toString()?.toDoubleOrNull()
        val siteElev = siteElevationField.text?.toString()?.toDoubleOrNull()
        val windElev = windElevationField.text?.toString()?.toDoubleOrNull()

        if (lat == null || lon == null) {
            Toast.makeText(this, R.string.weather_error_lat_lon, Toast.LENGTH_LONG).show()
            return null
        }
        if (siteElev == null || windElev == null) {
            Toast.makeText(this, R.string.weather_error_elevation, Toast.LENGTH_LONG).show()
            return null
        }

        val request = NasaPowerRequest(
            start = start,
            end = end,
            latitude = lat,
            longitude = lon,
            community = communityField.text?.toString()?.trim().orEmpty(),
            parameters = parametersField.text?.toString()?.trim().orEmpty(),
            format = formatField.text?.toString()?.trim().orEmpty(),
            units = unitsField.text?.toString()?.trim().orEmpty(),
            user = userField.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() },
            header = headerField.text?.toString()?.trim().orEmpty(),
            timeStandard = timeStandardField.text?.toString()?.trim().orEmpty(),
            siteElevation = siteElev,
            windElevation = windElev,
            windSurface = windSurfaceField.text?.toString()?.trim().orEmpty()
        )

        request.validate()?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return null
        }
        return request
    }

    private fun buildMonthlyRequestFromForm(): NasaPowerMonthlyRequest? {
        val lat = latitudeField.text?.toString()?.toDoubleOrNull()
        val lon = longitudeField.text?.toString()?.toDoubleOrNull()

        if (lat == null || lon == null) {
            Toast.makeText(this, R.string.weather_error_lat_lon, Toast.LENGTH_LONG).show()
            return null
        }

        val (startYear, endYear) = NasaPowerMonthlyRequest.thirtyYearWindow()
        val params = parametersField.text?.toString()?.trim().orEmpty()
            .ifEmpty { WeatherConfig.FULL_YEAR_MONTHLY_PARAMETERS }
        val community = communityField.text?.toString()?.trim().orEmpty()
            .ifEmpty { WeatherConfig.DEFAULT_MONTHLY_COMMUNITY }

        val request = NasaPowerMonthlyRequest(
            startYear = startYear,
            endYear = endYear,
            latitude = lat,
            longitude = lon,
            community = community,
            parameters = params,
            format = formatField.text?.toString()?.trim().orEmpty()
                .ifEmpty { WeatherConfig.DEFAULT_FORMAT },
            units = unitsField.text?.toString()?.trim().orEmpty()
                .ifEmpty { WeatherConfig.DEFAULT_UNITS },
            header = headerField.text?.toString()?.trim().orEmpty()
                .ifEmpty { WeatherConfig.DEFAULT_HEADER }
        )

        request.validate()?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return null
        }
        return request
    }

    private fun fetch30YearMonthly() {
        val request = buildMonthlyRequestFromForm() ?: return

        if (!NetworkHelper.isOnline(this)) {
            val offlineMsg = getString(R.string.weather_error_no_internet)
            Toast.makeText(this, offlineMsg, Toast.LENGTH_LONG).show()
            return
        }

        fetchBtn.isEnabled = false
        fetch30YearBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    nasaMonthlyClient.fetchMonthlyPoint(request)
                }
                val savedPath = withContext(Dispatchers.IO) {
                    val csv = WeatherMonthlyCsvExporter.toCsv(result.climateJson)
                    val fileName = WeatherMonthlyCsvExporter.buildFileName(request)
                    WeatherCsvExporter.save(this@WeatherActivity, csv, fileName)
                }
                Toast.makeText(
                    this@WeatherActivity,
                    getString(R.string.weather_monthly_csv_download_success, savedPath),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                val friendly = NetworkHelper.friendlyMessage(e)
                Toast.makeText(
                    this@WeatherActivity,
                    getString(R.string.weather_fetch_error, friendly),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                fetchBtn.isEnabled = true
                fetch30YearBtn.isEnabled = true
            }
        }
    }

    private fun fetchNasaPower() {
        var request = buildRequestFromForm() ?: return
        if (mlPayloadJson.isNotBlank() && cropSowingYmd.length == 8) {
            val year = cropSowingYmd.substring(0, 4)
            request = request.copy(
                start = "${year}0101",
                end = "${year}1231",
                community = WeatherConfig.DEFAULT_MONTHLY_COMMUNITY,
                parameters = WeatherConfig.FULL_YEAR_ANALYZED_PARAMETERS
            )
        }

        if (!NetworkHelper.isOnline(this)) {
            val offlineMsg = getString(R.string.weather_error_no_internet)
            retryBtn.visibility = View.VISIBLE
            Toast.makeText(this, offlineMsg, Toast.LENGTH_LONG).show()
            return
        }

        retryBtn.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    nasaClient.fetchDailyPoint(request)
                }
                val savedPath = withContext(Dispatchers.IO) {
                    val csv = WeatherCsvExporter.toCsv(result.weatherJson)
                    val fileName = WeatherCsvExporter.buildFileName(request)
                    WeatherCsvExporter.save(this@WeatherActivity, csv, fileName)
                }
                Toast.makeText(
                    this@WeatherActivity,
                    getString(R.string.weather_csv_download_success, savedPath),
                    Toast.LENGTH_LONG
                ).show()
                openAnalyzedScreen(result.weatherJson, request)
            } catch (e: Exception) {
                val friendly = NetworkHelper.friendlyMessage(e)
                retryBtn.visibility = View.VISIBLE
                Toast.makeText(
                    this@WeatherActivity,
                    getString(R.string.weather_fetch_error, friendly),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun openAnalyzedScreen(weatherJson: JSONObject, request: NasaPowerRequest) {
        val fromCropFlow = mlPayloadJson.isNotBlank()
        val sowingForAnalysis = if (fromCropFlow && cropSowingYmd.length == 8) {
            cropSowingYmd
        } else {
            request.start
        }
        val harvestForAnalysis = if (fromCropFlow && cropHarvestYmd.length == 8) {
            cropHarvestYmd
        } else {
            request.end
        }
        AnalyzedNavigation.launch(
            activity = this,
            weatherJson = weatherJson,
            latitude = request.latitude,
            longitude = request.longitude,
            sowingDateYmd = sowingForAnalysis,
            harvestDateYmd = harvestForAnalysis,
            flowType = flowType,
            mlUrl = mlUrl,
            mlPayloadJson = mlPayloadJson,
            crop = crop,
            state = state,
            resultTitle = resultTitle,
            useHtmlResult = useHtmlResult
        )
    }
}
