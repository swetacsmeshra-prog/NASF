package com.example.nasf

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import com.example.nasf.weather.CropDurationHelper
import com.example.nasf.weather.CsvClimateYieldBackend
import com.example.nasf.weather.ImdClimatologyRepository
import com.example.nasf.weather.JulianDateHelper
import com.example.nasf.weather.LocationAutofill
import com.example.nasf.weather.MlUrlResolver
import com.example.nasf.weather.OnDeviceYieldHelper
import com.example.nasf.weather.WeatherDataSource
import com.example.nasf.weather.WeatherSourceToggle
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutomaticDetails : AppCompatActivity() {

    private lateinit var stateValue: AutoCompleteTextView
    private lateinit var longValue: TextInputEditText
    private lateinit var latValue: TextInputEditText

    private lateinit var residueValue: TextInputEditText
    private lateinit var nValue: TextInputEditText
    private lateinit var dapValue: TextInputEditText
    private lateinit var mopValue: TextInputEditText
    private lateinit var zincsulValue: TextInputEditText
    private lateinit var fymtValue: TextInputEditText
    private lateinit var fyminputValue: TextInputEditText
    private lateinit var actvcrop: AutoCompleteTextView
    private lateinit var actvvariety: AutoCompleteTextView
    private lateinit var swoingdateValue: TextInputEditText
    private lateinit var waterlevelValue: TextInputEditText
    private lateinit var graindyieldValue: TextInputEditText

    private lateinit var progressbarManagement: ProgressBar
    private lateinit var calculateBtn: Button
    private lateinit var weatherSourceToggle: MaterialButtonToggleGroup
    private lateinit var locationAutofill: LocationAutofill

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_automatic_details)

        stateValue = findViewById(R.id.autoCompleteTextViewState)
        longValue = findViewById(R.id.Long)
        latValue = findViewById(R.id.Lat)

        residueValue = findViewById(R.id.Residue_Incor_t_ha)
        nValue = findViewById(R.id.Appl_N_kg_ha)
        dapValue = findViewById(R.id.Appl_P2O5_kg_ha)
        mopValue = findViewById(R.id.Appl_K2O_kg_ha)
        zincsulValue = findViewById(R.id.Zinc_Sul_kg_ha)
        fymtValue = findViewById(R.id.FYM_t_ha)
        fyminputValue = findViewById(R.id.FYM_input_interval_Yr)
        actvcrop = findViewById(R.id.autoCompleteTextViewCrop)
        actvvariety = findViewById(R.id.autoCompleteTextViewVariety)
        swoingdateValue = findViewById(R.id.Sowing_DOY)
        waterlevelValue = findViewById(R.id.Irr_no)
        graindyieldValue = findViewById(R.id.Grain_Yield_t_ha)

        progressbarManagement = findViewById(R.id.progressbarManagement)
        calculateBtn = findViewById(R.id.calculatebtn)
        weatherSourceToggle = findViewById(R.id.weatherSourceToggle)
        WeatherSourceToggle.bind(weatherSourceToggle, findViewById(R.id.weatherSourceHint))
        LocationAutofill(this, latValue, longValue).also { locationAutofill = it }.start()

        calculateBtn.setOnClickListener {
            val form = readValidatedForm() ?: return@setOnClickListener
            calculateYieldOnSameScreen(
                latitude = form.latitude,
                longitude = form.longitude,
                payloadJson = form.payloadJson
            )
        }

        val states = listOf("UP", "Bihar", "Punjab", "Haryana")
        val adapterStates = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, states)
        stateValue.setAdapter(adapterStates)

        val crops = listOf("चावल / Rice", "गेहूँ / Wheat")
        val adapterCrops = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops)
        actvcrop.setAdapter(adapterCrops)

        JulianDateHelper.attachDatePicker(this, swoingdateValue)

        val grains = listOf("चावल / Rice", "गेहूँ / Wheat")
        val riceVarieties = listOf(
            "कम अवधि / Short Duration",
            "मध्यम अवधि / Medium Duration",
            "लंबी अवधि  / Long Duration"
        )
        val wheatVarieties = listOf(
            "कम अवधि / Short Duration",
            "मध्यम अवधि / Medium Duration",
            "लंबी अवधि  / Long Duration"
        )

        val adapterGrains = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grains)

        val dropdownGrains = findViewById<TextInputLayout>(R.id.dropdown_menu_crop)
        val cropDropdown = dropdownGrains.editText as? AutoCompleteTextView
        cropDropdown?.setAdapter(adapterGrains)

        cropDropdown?.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = adapterGrains.getItem(position)
            val dropdownVarieties = findViewById<TextInputLayout>(R.id.dropdown_menu_variety)
            dropdownVarieties.visibility = View.VISIBLE
            val varietyDropdown = dropdownVarieties.editText as? AutoCompleteTextView

            if (selectedItem == "चावल / Rice") {
                varietyDropdown?.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, riceVarieties)
                )
            } else if (selectedItem == "गेहूँ / Wheat") {
                varietyDropdown?.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, wheatVarieties)
                )
            }
        }
    }

    /**
     * Same-screen Automatic yield from bundled CSVs (no internet).
     * Dialog shows only Farm Yield.
     */
    private fun calculateYieldOnSameScreen(
        latitude: Double,
        longitude: Double,
        payloadJson: String
    ) {
        val source = WeatherSourceToggle.selected(weatherSourceToggle)
        showProgressBar(true)
        calculateBtn.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val farmYield = withContext(Dispatchers.IO) {
                    val climate = when (source) {
                        WeatherDataSource.IMD -> ImdClimatologyRepository.nearest(
                            this@AutomaticDetails,
                            latitude,
                            longitude
                        ).climate
                        WeatherDataSource.NASA_POWER -> CsvClimateYieldBackend.nearest(
                            this@AutomaticDetails,
                            latitude,
                            longitude
                        ).climate
                    }
                    OnDeviceYieldHelper.predictFormattedFromClimate(
                        this@AutomaticDetails,
                        climate,
                        payloadJson
                    )
                }
                showResultDialog(
                    getString(R.string.combined_yield_title),
                    CsvClimateYieldBackend.stripUnits(farmYield)
                )
            } catch (e: Exception) {
                showResultDialog("Error", e.message ?: "Unknown error")
            } finally {
                showProgressBar(false)
                calculateBtn.isEnabled = true
            }
        }
    }

    private data class FormData(
        val latitude: Double,
        val longitude: Double,
        val payloadJson: String
    )

    private fun readValidatedForm(): FormData? {
        val state = stateValue.text.toString()
        val lon = longValue.text.toString()
        val lat = latValue.text.toString()
        val crop = actvcrop.text.toString()
        val variety = actvvariety.text.toString()
        val residue = residueValue.text.toString()
        val n = nValue.text.toString()
        val dap = dapValue.text.toString()
        val mop = mopValue.text.toString()
        val zinc = zincsulValue.text.toString()
        val fym = fymtValue.text.toString()
        val fymInterval = fyminputValue.text.toString()
        val sowing = swoingdateValue.text.toString()
        val irr = waterlevelValue.text.toString()
        val grain = graindyieldValue.text.toString()

        if (state.isEmpty() || lon.isEmpty() || lat.isEmpty() || crop.isEmpty() || variety.isEmpty() ||
            residue.isEmpty() || n.isEmpty() || dap.isEmpty() || mop.isEmpty() || zinc.isEmpty() ||
            fym.isEmpty() || fymInterval.isEmpty() || sowing.isEmpty() || irr.isEmpty() ||
            grain.isEmpty()
        ) {
            showResultDialog("Input Error", "Please enter all values.")
            return null
        }

        val durationDays = CropDurationHelper.durationDays(variety)
        if (durationDays == null) {
            showResultDialog("Input Error", "Please select Short / Medium / Long duration variety.")
            return null
        }

        val sowingYmd = JulianDateHelper.getDateYmd(swoingdateValue)
        if (sowingYmd == null) {
            showResultDialog("Input Error", "Please select a valid sowing date.")
            return null
        }
        val (_, harvestDoy) = CropDurationHelper.harvestFromSowingYmd(sowingYmd, durationDays)

        val mlUrl = MlUrlResolver.automaticYieldUrl(state, crop)
        if (mlUrl.isBlank()) {
            showResultDialog("Input Error", "Invalid crop or state selection.")
            return null
        }

        val adjustedN = convertAndDisplayNValue()
        val adjustedP = convertAndDisplayPValue()
        val adjustedK = convertAndDisplayKValue()
        val payload = """{"Long": ${lon.toDouble()}, "Lat": ${lat.toDouble()}, "Residue_Incor_t_ha": ${residue.toDouble()}, "Appl_N_kg_ha": $adjustedN, 
                "Appl_P2O5_kg_ha": $adjustedP, "Appl_K2O_kg_ha": $adjustedK, "Zinc_Sul_kg_ha": ${zinc.toDouble()}, 
                "FYM_t_ha": ${fym.toDouble()}, "FYM_input_interval_Yr": ${fymInterval.toDouble()}, "Sowing_DOY": ${sowing.toDouble()}, 
                "Irr_no": ${irr.toDouble()}, "Harvest_DOY": $harvestDoy, "Grain_Yield_t_ha": ${grain.toDouble()} }"""

        return FormData(
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
            payloadJson = payload
        )
    }

    private fun showProgressBar(show: Boolean) {
        progressbarManagement.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun convertAndDisplayNValue(): Double {
        val nInput = nValue.text.toString().toDoubleOrNull() ?: 0.0
        val pInput = dapValue.text.toString().toDoubleOrNull() ?: 0.0
        return (nInput * 0.46) + (pInput * 0.18)
    }

    private fun convertAndDisplayPValue(): Double {
        val nInput = dapValue.text.toString().toDoubleOrNull() ?: 0.0
        return nInput * 0.46
    }

    private fun convertAndDisplayKValue(): Double {
        val nInput = mopValue.text.toString().toDoubleOrNull() ?: 0.0
        return nInput * 0.6
    }

    private fun showResultDialog(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
