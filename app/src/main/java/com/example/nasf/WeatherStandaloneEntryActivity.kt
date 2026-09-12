package com.example.nasf

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.JulianDateHelper
import com.example.nasf.weather.MlUrlResolver
import com.example.nasf.weather.WeatherNavigation
import com.example.nasf.weather.WeatherPayloadBuilder
import com.google.android.material.textfield.TextInputEditText

/**
 * Standalone entry for the NASA POWER weather flow.
 * Use this to test the feature without modifying ManualDetails, AutomaticDetails, or SoilManagement.
 */
class WeatherStandaloneEntryActivity : AppCompatActivity() {

    private lateinit var flowDropdown: AutoCompleteTextView
    private lateinit var stateDropdown: AutoCompleteTextView
    private lateinit var cropDropdown: AutoCompleteTextView
    private lateinit var latField: TextInputEditText
    private lateinit var longField: TextInputEditText
    private lateinit var sowingDate: TextInputEditText
    private lateinit var harvestDate: TextInputEditText
    private lateinit var grainYield: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_standalone_entry)

        flowDropdown = findViewById(R.id.weatherFlowDropdown)
        stateDropdown = findViewById(R.id.weatherStateDropdown)
        cropDropdown = findViewById(R.id.weatherCropDropdown)
        latField = findViewById(R.id.weatherLat)
        longField = findViewById(R.id.weatherLong)
        sowingDate = findViewById(R.id.weatherSowingDate)
        harvestDate = findViewById(R.id.weatherHarvestDate)
        grainYield = findViewById(R.id.weatherGrainYield)

        JulianDateHelper.attachDatePicker(this, sowingDate)
        JulianDateHelper.attachDatePicker(this, harvestDate)

        val flows = listOf("Manual yield", "Automatic yield", "Soil advisory")
        flowDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, flows)
        )
        flowDropdown.setText(flows[1], false)

        val states = listOf("UP", "Bihar", "Punjab", "Haryana")
        stateDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, states)
        )
        stateDropdown.setText(states[0], false)

        val crops = listOf("चावल / Rice", "गेहूँ / Wheat")
        cropDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops)
        )
        cropDropdown.setText(crops[0], false)

        findViewById<Button>(R.id.weatherProceedBtn).setOnClickListener { proceed() }
    }

    private fun proceed() {
        val lat = latField.text.toString().toDoubleOrNull()
        val long = longField.text.toString().toDoubleOrNull()
        val yield = grainYield.text.toString().toDoubleOrNull()
        val sowingYmd = JulianDateHelper.getDateYmd(sowingDate)
        val harvestYmd = JulianDateHelper.getDateYmd(harvestDate)
        val sowingDoy = sowingDate.text.toString().toDoubleOrNull()
        val harvestDoy = harvestDate.text.toString().toDoubleOrNull()
        val crop = cropDropdown.text.toString()
        val state = stateDropdown.text.toString()
        val flow = flowDropdown.text.toString()

        if (lat == null || long == null || yield == null || sowingDoy == null || harvestDoy == null) {
            Toast.makeText(this, "Fill all fields including dates.", Toast.LENGTH_LONG).show()
            return
        }

        val (flowType, mlUrl, payload, html) = when (flow) {
            "Manual yield" -> Quad(
                CropSessionExtras.FLOW_MANUAL,
                MlUrlResolver.manualYieldUrl(state, crop),
                WeatherPayloadBuilder.manualPayload(long, lat, sowingDoy, harvestDoy, yield),
                false
            )
            "Automatic yield" -> Quad(
                CropSessionExtras.FLOW_AUTOMATIC,
                MlUrlResolver.automaticYieldUrl(state, crop),
                WeatherPayloadBuilder.automaticPayload(long, lat, sowingDoy, harvestDoy, yield),
                false
            )
            else -> Quad(
                CropSessionExtras.FLOW_SOIL,
                MlUrlResolver.soilAdvisoryUrl(crop),
                WeatherPayloadBuilder.soilPayload(long, lat, yield),
                true
            )
        }

        WeatherNavigation.launch(
            activity = this,
            flowType = flowType,
            latitude = lat,
            longitude = long,
            sowingDateYmd = sowingYmd,
            harvestDateYmd = harvestYmd,
            mlUrl = mlUrl,
            mlPayloadJson = payload,
            crop = crop,
            state = state,
            resultTitle = if (html) "" else "Yield Prediction",
            useHtmlResult = html
        )
    }

    private data class Quad(
        val flowType: String,
        val mlUrl: String,
        val payload: String,
        val html: Boolean
    )
}
