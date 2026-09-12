package com.example.nasf.weather

import android.app.Activity
import android.widget.Toast
import com.example.nasf.R
import com.google.android.material.textfield.TextInputEditText

/**
 * Shared climate-field binding for Manual / Automatic first pages.
 */
class ClimateYieldFields(activity: Activity) {

    private val rainField: TextInputEditText = activity.findViewById(R.id.manualClimateRain)
    private val tminField: TextInputEditText = activity.findViewById(R.id.manualClimateTmin)
    private val tmaxField: TextInputEditText = activity.findViewById(R.id.manualClimateTmax)
    private val windField: TextInputEditText = activity.findViewById(R.id.manualClimateWind)
    private val solarField: TextInputEditText = activity.findViewById(R.id.manualClimateSolar)
    private val humidityField: TextInputEditText = activity.findViewById(R.id.manualClimateHumidity)

    private val predictor = ManualClimateAnnPredictor(activity.applicationContext)

    fun readOrNull(activity: Activity): ManualClimateAnnPredictor.ClimateInputs? {
        val rain = parse(activity, rainField, R.string.manual_climate_rain_label) ?: return null
        val tmin = parse(activity, tminField, R.string.manual_climate_tmin_label) ?: return null
        val tmax = parse(activity, tmaxField, R.string.manual_climate_tmax_label) ?: return null
        val wind = parse(activity, windField, R.string.manual_climate_wind_label) ?: return null
        val solar = parse(activity, solarField, R.string.manual_climate_solar_label) ?: return null
        val humidity = parse(activity, humidityField, R.string.manual_climate_humidity_label) ?: return null
        return ManualClimateAnnPredictor.ClimateInputs(
            seasonRainfallMm = rain,
            seasonTMinMean = tmin,
            seasonTMaxMean = tmax,
            seasonWindMean = wind,
            seasonSolarMean = solar,
            seasonHumidityMean = humidity
        )
    }

    fun predictTHa(inputs: ManualClimateAnnPredictor.ClimateInputs): Double =
        predictor.predict(inputs).yieldTHa

    fun formatCombined(activity: Activity, farmYieldText: String, climateTHa: Double): String {
        val farmValue = stripUnits(farmYieldText)
        val climateValue = "%.2f".format(climateTHa)
        return "$farmValue\n$climateValue"
    }

    /** Keep numeric yield text only — drop Kg/ha, t/ha, etc. */
    fun stripUnits(raw: String): String =
        raw.trim()
            .replace(Regex("(?i)\\s*(kg/ha|t/ha|tonnes?/ha|q/ha)\\s*"), "")
            .trim()

    private fun parse(activity: Activity, field: TextInputEditText, labelRes: Int): Double? {
        val raw = field.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            Toast.makeText(activity, R.string.manual_climate_error_empty, Toast.LENGTH_SHORT).show()
            field.requestFocus()
            return null
        }
        val value = raw.toDoubleOrNull()
        if (value == null || value.isNaN()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.manual_climate_error_number, activity.getString(labelRes)),
                Toast.LENGTH_SHORT
            ).show()
            field.requestFocus()
            return null
        }
        return value
    }
}
