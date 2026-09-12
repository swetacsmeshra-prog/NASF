package com.example.nasf

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.NasaPowerMonthlyClient
import com.example.nasf.weather.NasaPowerMonthlyRequest
import com.example.nasf.weather.NetworkHelper
import com.example.nasf.weather.ThirtyYearNavigation
import com.example.nasf.weather.WeatherConfig
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var manual: Button
    private lateinit var automiatic: Button
    private lateinit var soilmanagement: Button
    private lateinit var thirtyYearClimateBtn: Button

    private val nasaMonthlyClient = NasaPowerMonthlyClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manual = findViewById(R.id.manual_btn)
        automiatic = findViewById(R.id.automatic_btn)
        soilmanagement = findViewById(R.id.system_btn)
        thirtyYearClimateBtn = findViewById(R.id.thirty_year_climate_btn)

        manual.setOnClickListener {
            startActivity(Intent(this, ManualDetails::class.java))
            Toast.makeText(applicationContext, "Manual System", Toast.LENGTH_SHORT).show()
        }

        automiatic.setOnClickListener {
            startActivity(Intent(this, AutomaticDetails::class.java))
            Toast.makeText(applicationContext, "Automatic System", Toast.LENGTH_SHORT).show()
        }

        soilmanagement.setOnClickListener {
            startActivity(Intent(this, SoilManagement::class.java))
            Toast.makeText(applicationContext, "Management System", Toast.LENGTH_SHORT).show()
        }

        thirtyYearClimateBtn.setOnClickListener { showLatLonThenOpenThirtyYearReport() }
    }

    private fun showLatLonThenOpenThirtyYearReport() {
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        val latLayout = TextInputLayout(this).apply {
            hint = getString(R.string.weather_hint_latitude)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val latField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("26.46")
        }
        latLayout.addView(latField)

        val lonLayout = TextInputLayout(this).apply {
            hint = getString(R.string.weather_hint_longitude)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * density).toInt() }
        }
        val lonField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setText("85.02")
        }
        lonLayout.addView(lonField)

        val progress = ProgressBar(this).apply {
            visibility = ProgressBar.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (12 * density).toInt()
                it.gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }

        container.addView(latLayout)
        container.addView(lonLayout)
        container.addView(progress)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.analyzed_view_30_year_report_btn)
            .setMessage(R.string.dashboard_thirty_year_prompt)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val lat = latField.text?.toString()?.toDoubleOrNull()
                val lon = lonField.text?.toString()?.toDoubleOrNull()
                if (lat == null || lon == null) {
                    Toast.makeText(this, R.string.weather_error_lat_lon, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                openThirtyYearReport(lat, lon, dialog, progress)
            }
        }
        dialog.show()
    }

    private fun openThirtyYearReport(
        latitude: Double,
        longitude: Double,
        dialog: AlertDialog,
        progress: ProgressBar
    ) {
        if (!NetworkHelper.isOnline(this)) {
            Toast.makeText(this, R.string.weather_error_no_internet, Toast.LENGTH_LONG).show()
            return
        }

        val (startYear, endYear) = NasaPowerMonthlyRequest.thirtyYearWindow()
        val request = NasaPowerMonthlyRequest(
            startYear = startYear,
            endYear = endYear,
            latitude = latitude,
            longitude = longitude,
            community = WeatherConfig.DEFAULT_MONTHLY_COMMUNITY,
            parameters = WeatherConfig.FULL_YEAR_MONTHLY_PARAMETERS,
            format = WeatherConfig.DEFAULT_FORMAT,
            units = WeatherConfig.DEFAULT_UNITS,
            header = WeatherConfig.DEFAULT_HEADER
        )
        request.validate()?.let { error ->
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        progress.visibility = ProgressBar.VISIBLE
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        thirtyYearClimateBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val climateJson = withContext(Dispatchers.IO) {
                    nasaMonthlyClient.fetchMonthlyPoint(request).climateJson
                }
                dialog.dismiss()
                ThirtyYearNavigation.launch(
                    activity = this@MainActivity,
                    climateJson = climateJson,
                    latitude = latitude,
                    longitude = longitude
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.weather_fetch_error, NetworkHelper.friendlyMessage(e)),
                    Toast.LENGTH_LONG
                ).show()
                progress.visibility = ProgressBar.GONE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            } finally {
                thirtyYearClimateBtn.isEnabled = true
            }
        }
    }
}
