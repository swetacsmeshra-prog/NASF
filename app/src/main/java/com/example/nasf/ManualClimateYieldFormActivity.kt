package com.example.nasf

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.ClimatologyAnomalyFormatter
import com.example.nasf.weather.ClimatologyStdvRepository
import com.example.nasf.weather.ManualClimateAnnPredictor
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class ManualClimateYieldFormActivity : AppCompatActivity() {

    private lateinit var latField: TextInputEditText
    private lateinit var lonField: TextInputEditText
    private lateinit var rainField: TextInputEditText
    private lateinit var tminField: TextInputEditText
    private lateinit var tmaxField: TextInputEditText
    private lateinit var windField: TextInputEditText
    private lateinit var solarField: TextInputEditText
    private lateinit var humidityField: TextInputEditText
    private lateinit var rainLayout: TextInputLayout
    private lateinit var tminLayout: TextInputLayout
    private lateinit var tmaxLayout: TextInputLayout
    private lateinit var windLayout: TextInputLayout
    private lateinit var solarLayout: TextInputLayout
    private lateinit var humidityLayout: TextInputLayout
    private lateinit var calculateBtn: Button
    private lateinit var fillTypicalBtn: Button
    private lateinit var yieldText: TextView
    private lateinit var yieldChangeText: TextView
    private lateinit var inputsUsedText: TextView
    private lateinit var zScoreText: TextView
    private lateinit var metricsText: TextView
    private lateinit var trainingNoteText: TextView

    private lateinit var predictor: ManualClimateAnnPredictor
    private var previousYieldTHa: Double? = null
    private var lastLookup: ClimatologyStdvRepository.Lookup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_climate_yield_form)

        latField = findViewById(R.id.manualClimateLat)
        lonField = findViewById(R.id.manualClimateLon)
        rainField = findViewById(R.id.manualClimateRain)
        tminField = findViewById(R.id.manualClimateTmin)
        tmaxField = findViewById(R.id.manualClimateTmax)
        windField = findViewById(R.id.manualClimateWind)
        solarField = findViewById(R.id.manualClimateSolar)
        humidityField = findViewById(R.id.manualClimateHumidity)
        rainLayout = findViewById(R.id.manualClimateRainLayout)
        tminLayout = findViewById(R.id.manualClimateTminLayout)
        tmaxLayout = findViewById(R.id.manualClimateTmaxLayout)
        windLayout = findViewById(R.id.manualClimateWindLayout)
        solarLayout = findViewById(R.id.manualClimateSolarLayout)
        humidityLayout = findViewById(R.id.manualClimateHumidityLayout)
        calculateBtn = findViewById(R.id.manualClimateCalculateBtn)
        fillTypicalBtn = findViewById(R.id.manualClimateFillTypicalBtn)
        yieldText = findViewById(R.id.manualClimateYieldText)
        yieldChangeText = findViewById(R.id.manualClimateYieldChangeText)
        inputsUsedText = findViewById(R.id.manualClimateInputsUsedText)
        zScoreText = findViewById(R.id.manualClimateZScoreText)
        metricsText = findViewById(R.id.manualClimateMetricsText)
        trainingNoteText = findViewById(R.id.manualClimateTrainingNoteText)

        predictor = ManualClimateAnnPredictor(applicationContext)

        ClimatologyAnomalyFormatter.bindLatLonHint(
            this,
            latField,
            lonField,
            findViewById(R.id.climatologyHint)
        )

        fillTypicalBtn.setOnClickListener { fillTypicalClimate() }
        calculateBtn.setOnClickListener { calculateYield() }
    }

    private fun fillTypicalClimate() {
        val lookup = lookupOrToast() ?: return
        lastLookup = lookup
        val f = lookup.farm
        rainField.setText(fmt(f.rainSeasonTotalMm.mean, 0))
        tminField.setText(fmt(f.tminSeason.mean, 1))
        tmaxField.setText(fmt(f.tmaxSeason.mean, 1))
        windField.setText(fmt(f.windSeason.mean, 2))
        solarField.setText(fmt(f.solarSeason.mean, 1))
        humidityField.setText(fmt(f.rhSeason.mean, 1))
        rainLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.rainSeasonTotalMm, "mm")
        tminLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.tminSeason, "°C")
        tmaxLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.tmaxSeason, "°C")
        windLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.windSeason, "m/s")
        solarLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.solarSeason, "")
        humidityLayout.helperText = ClimatologyAnomalyFormatter.helperForField(f.rhSeason, "%")
    }

    private fun calculateYield() {
        val rain = parseRequired(rainField, getString(R.string.manual_climate_rain_label)) ?: return
        val tmin = parseRequired(tminField, getString(R.string.manual_climate_tmin_label)) ?: return
        val tmax = parseRequired(tmaxField, getString(R.string.manual_climate_tmax_label)) ?: return
        val wind = parseRequired(windField, getString(R.string.manual_climate_wind_label)) ?: return
        val solar = parseRequired(solarField, getString(R.string.manual_climate_solar_label)) ?: return
        val humidity = parseRequired(humidityField, getString(R.string.manual_climate_humidity_label)) ?: return

        val inputs = ManualClimateAnnPredictor.ClimateInputs(
            seasonRainfallMm = rain,
            seasonTMinMean = tmin,
            seasonTMaxMean = tmax,
            seasonWindMean = wind,
            seasonSolarMean = solar,
            seasonHumidityMean = humidity
        )

        val lookup = lastLookup ?: lookupSilent()
        lastLookup = lookup
        val outlier = lookup?.let { firstOutlier(it, inputs) }
        if (outlier != null) {
            AlertDialog.Builder(this)
                .setTitle(R.string.climatology_outlier_title)
                .setMessage(
                    getString(
                        R.string.climatology_outlier_message,
                        outlier.label,
                        ClimatologyAnomalyFormatter.bandLabel(outlier.band),
                        ClimatologyAnomalyFormatter.formatZ(outlier.z)
                    )
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> applyPrediction(inputs, lookup) }
                .show()
            return
        }
        applyPrediction(inputs, lookup)
    }

    private fun firstOutlier(
        lookup: ClimatologyStdvRepository.Lookup,
        inputs: ManualClimateAnnPredictor.ClimateInputs
    ): ClimatologyStdvRepository.ZScore? {
        val f = lookup.farm
        val checks = listOf(
            inputs.seasonRainfallMm to f.rainSeasonTotalMm,
            inputs.seasonTMinMean to f.tminSeason,
            inputs.seasonTMaxMean to f.tmaxSeason,
            inputs.seasonWindMean to f.windSeason,
            inputs.seasonSolarMean to f.solarSeason,
            inputs.seasonHumidityMean to f.rhSeason
        )
        val rangeHit = checks.any { ClimatologyStdvRepository.outsideSeasonRange(it.first, it.second) }
        val scores = ClimatologyStdvRepository.seasonZScores(lookup, inputs)
        val extreme = scores.firstOrNull { it.band == ClimatologyStdvRepository.Band.OUTLIER }
        return if (rangeHit && extreme == null) scores.maxByOrNull { kotlin.math.abs(it.z) } else extreme
    }

    private fun applyPrediction(
        inputs: ManualClimateAnnPredictor.ClimateInputs,
        lookup: ClimatologyStdvRepository.Lookup?
    ) {
        val prediction = predictor.predict(inputs)
        val newYield = prediction.yieldTHa
        yieldText.text = getString(R.string.manual_climate_yield_value, newYield)

        val prev = previousYieldTHa
        if (prev == null) {
            yieldChangeText.visibility = View.VISIBLE
            yieldChangeText.text = getString(R.string.manual_climate_yield_first, newYield)
        } else {
            yieldChangeText.visibility = View.VISIBLE
            yieldChangeText.text = getString(
                R.string.manual_climate_yield_changed,
                prev,
                newYield,
                "%+.2f".format(newYield - prev)
            )
        }
        previousYieldTHa = newYield

        inputsUsedText.visibility = View.VISIBLE
        inputsUsedText.text = getString(
            R.string.manual_climate_inputs_used,
            inputs.seasonRainfallMm,
            inputs.seasonTMinMean,
            inputs.seasonTMaxMean,
            inputs.seasonWindMean,
            inputs.seasonSolarMean,
            inputs.seasonHumidityMean
        )

        if (lookup != null) {
            val scores = ClimatologyStdvRepository.seasonZScores(lookup, inputs)
            zScoreText.visibility = View.VISIBLE
            zScoreText.text = getString(
                R.string.climatology_z_line,
                ClimatologyAnomalyFormatter.compactStrip(scores)
            )
        } else {
            zScoreText.visibility = View.GONE
        }

        metricsText.text = getString(
            R.string.manual_climate_metrics,
            prediction.metrics.rmseTHa,
            prediction.metrics.stdvResidualTHa,
            prediction.metrics.r2,
            prediction.metrics.maeTHa
        )
        trainingNoteText.text = getString(
            R.string.manual_climate_training_note,
            prediction.trainedOnFarms,
            prediction.trainWeekStart,
            prediction.trainWeekEnd,
            prediction.architectureLabel
        )
    }

    private fun lookupOrToast(): ClimatologyStdvRepository.Lookup? {
        val lat = latField.text?.toString()?.trim()?.toDoubleOrNull()
        val lon = lonField.text?.toString()?.trim()?.toDoubleOrNull()
        if (lat == null || lon == null) {
            Toast.makeText(this, R.string.manual_climate_error_empty, Toast.LENGTH_SHORT).show()
            latField.requestFocus()
            return null
        }
        val lookup = ClimatologyStdvRepository.nearest(this, lat, lon)
        if (lookup == null) {
            Toast.makeText(this, "No climatology data", Toast.LENGTH_SHORT).show()
        }
        return lookup
    }

    private fun lookupSilent(): ClimatologyStdvRepository.Lookup? {
        val lat = latField.text?.toString()?.trim()?.toDoubleOrNull() ?: return null
        val lon = lonField.text?.toString()?.trim()?.toDoubleOrNull() ?: return null
        return runCatching { ClimatologyStdvRepository.nearest(this, lat, lon) }.getOrNull()
    }

    private fun parseRequired(field: TextInputEditText, label: String): Double? {
        val raw = field.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.manual_climate_error_empty, Toast.LENGTH_SHORT).show()
            field.requestFocus()
            return null
        }
        val value = raw.toDoubleOrNull()
        if (value == null || value.isNaN()) {
            Toast.makeText(
                this,
                getString(R.string.manual_climate_error_number, label),
                Toast.LENGTH_SHORT
            ).show()
            field.requestFocus()
            return null
        }
        return value
    }

    private fun fmt(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
}
