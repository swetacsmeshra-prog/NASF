package com.example.nasf

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.nasf.weather.ClimatologyAnomalyFormatter
import com.example.nasf.weather.ClimatologyStdvRepository
import com.example.nasf.weather.CropDurationHelper
import com.example.nasf.weather.CsvClimateYieldBackend
import com.example.nasf.weather.ImdClimatologyRepository
import com.example.nasf.weather.JulianDateHelper
import com.example.nasf.weather.LocationAutofill
import com.example.nasf.weather.MockYieldResponse
import com.example.nasf.weather.WeatherDataSource
import com.example.nasf.weather.WeatherSourceToggle
import com.google.android.material.button.MaterialButtonToggleGroup
import org.json.JSONObject


class ManualDetails : AppCompatActivity() {

    private lateinit var stateValue: AutoCompleteTextView
    private lateinit var longValue: TextInputEditText
    private lateinit var latValue: TextInputEditText
    private lateinit var phValue: TextInputEditText
    private lateinit var ecValue: TextInputEditText
    private lateinit var ocValue: TextInputEditText
    private lateinit var availnValue: TextInputEditText
    private lateinit var pValue: TextInputEditText
    private lateinit var availkValue: TextInputEditText
    private lateinit var sulphurValue: TextInputEditText
    private lateinit var nutrientznValue: TextInputEditText
    private lateinit var nutrientfeValue: TextInputEditText
    private lateinit var nutrientmnValue: TextInputEditText
    private lateinit var nutrientcuValue: TextInputEditText

    private lateinit var residueValue: TextInputEditText
    private lateinit var nValue: TextInputEditText
    private lateinit var dapValue: TextInputEditText
    private lateinit var mopValue: TextInputEditText
    private lateinit var zincsulValue: TextInputEditText
    private lateinit var fymtValue: TextInputEditText
    private lateinit var fyminputValue: TextInputEditText
    private lateinit var actvcrop : AutoCompleteTextView
    private lateinit var actvvariety : AutoCompleteTextView
    private lateinit var swoingdateValue: TextInputEditText
    private lateinit var waterlevelValue : TextInputEditText
    private lateinit var graindyieldValue : TextInputEditText

    private lateinit var progressbarManagement: ProgressBar
    private lateinit var calculateBtn: Button
    private lateinit var weatherSourceToggle: MaterialButtonToggleGroup
    private lateinit var locationAutofill: LocationAutofill
    private var pendingClimateYieldTHa: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_details)

        stateValue = findViewById(R.id.autoCompleteTextViewState)
        longValue = findViewById(R.id.Long)
        latValue = findViewById(R.id.Lat)
        phValue = findViewById(R.id.pH)
        ecValue = findViewById(R.id.EC_dS_m)
        ocValue = findViewById(R.id.OC)
        availnValue = findViewById(R.id.Avail_N_Kg_ha)
        pValue = findViewById(R.id.Avail_P_kg_ha)
        availkValue = findViewById(R.id.Avail_K_kg_ha)
        sulphurValue = findViewById(R.id.Soil_S_kg_ha)
        nutrientznValue = findViewById(R.id.Soil_Zn_ppm)
        nutrientfeValue = findViewById(R.id.Soil_Fe_ppm)
        nutrientmnValue = findViewById(R.id.Soil_Mn_ppm)
        nutrientcuValue = findViewById(R.id.Soil_Cu_ppm)

        residueValue = findViewById(R.id.Residue_Incor_Q_ha)
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
        graindyieldValue = findViewById(R.id.Grain_Yield_Q_ha)

        progressbarManagement = findViewById(R.id.progressbarManagement)
        calculateBtn = findViewById(R.id.calculatebtn)
        weatherSourceToggle = findViewById(R.id.weatherSourceToggle)
        WeatherSourceToggle.bind(weatherSourceToggle, findViewById(R.id.weatherSourceHint))

        ClimatologyAnomalyFormatter.bindLatLonHint(
            this,
            latValue,
            longValue,
            findViewById(R.id.climatologyHint)
        )
        LocationAutofill(this, latValue, longValue).also { locationAutofill = it }.start()

        calculateBtn.setOnClickListener {
            // Validate input and then call the sendRequest function with user input
            val autoCompleteTextViewState = stateValue.text.toString()
            val Long = longValue.text.toString()
            val Lat = latValue.text.toString()
            val pH = phValue.text.toString()
            val EC_dS_m = ecValue.text.toString()
            val OC = ocValue.text.toString()
            val Avail_N_Kg_ha = availnValue.text.toString()
            val Avail_P_kg_ha = pValue.text.toString()
            val Avail_K_kg_ha = availkValue.text.toString()
            val Soil_S_kg_ha = sulphurValue.text.toString()
            val Soil_Zn_ppm = nutrientznValue.text.toString()
            val Soil_Fe_ppm = nutrientfeValue.text.toString()
            val Soil_Mn_ppm = nutrientmnValue.text.toString()
            val Soil_Cu_ppm = nutrientcuValue.text.toString()

            val autoCompleteTextViewCrop = actvcrop.text.toString()
            val autoCompleteTextViewVariety = actvvariety.text.toString()
            val Residue_Incor_Q_ha = residueValue.text.toString()
            val Appl_N_kg_ha = nValue.text.toString()
            val Appl_P2O5_kg_ha = dapValue.text.toString()
            val Appl_K2O_kg_ha = mopValue.text.toString()
            val Zinc_Sul_kg_ha = zincsulValue.text.toString()
            val FYM_t_ha = fymtValue.text.toString()
            val FYM_input_interval_Yr = fyminputValue.text.toString()
            val Sowing_DOY = swoingdateValue.text.toString()
            val Irr_no = waterlevelValue.text.toString()
            val Grain_Yield_Q_ha = graindyieldValue.text.toString()

            val durationDays = CropDurationHelper.durationDays(autoCompleteTextViewVariety)
            if (durationDays == null) {
                showResultDialog("Input Error", "Please select Short / Medium / Long duration variety.")
                return@setOnClickListener
            }

            if (autoCompleteTextViewState.isNotEmpty() && Long.isNotEmpty() && Lat.isNotEmpty() && pH.isNotEmpty() &&
                EC_dS_m.isNotEmpty() && OC.isNotEmpty() && Avail_N_Kg_ha.isNotEmpty() &&
                Avail_P_kg_ha.isNotEmpty() && Avail_K_kg_ha.isNotEmpty() && Soil_S_kg_ha.isNotEmpty() &&
                Soil_Zn_ppm.isNotEmpty() && Soil_Fe_ppm.isNotEmpty() && Soil_Mn_ppm.isNotEmpty() &&
                Soil_Cu_ppm.isNotEmpty() && autoCompleteTextViewCrop.isNotEmpty() && autoCompleteTextViewVariety.isNotEmpty() &&
                Residue_Incor_Q_ha.isNotEmpty() && Appl_N_kg_ha.isNotEmpty() &&
                Appl_P2O5_kg_ha.isNotEmpty() && Appl_K2O_kg_ha.isNotEmpty() && Zinc_Sul_kg_ha.isNotEmpty() &&
                FYM_t_ha.isNotEmpty() && FYM_input_interval_Yr.isNotEmpty() && Sowing_DOY.isNotEmpty() &&
                Irr_no.isNotEmpty() && Grain_Yield_Q_ha.isNotEmpty()) {

                val sowingYmd = JulianDateHelper.getDateYmd(swoingdateValue)
                val harvestDoy = if (sowingYmd != null) {
                    CropDurationHelper.harvestFromSowingYmd(sowingYmd, durationDays).second
                } else {
                    CropDurationHelper.harvestDoyFromSowingDoy(Sowing_DOY.toDouble(), durationDays)
                }

                val adjustedNValue = convertAndDisplayNValue()
                val adjustedPValue = convertAndDisplayPValue()
                val adjustedKValue = convertAndDisplayKValue()

                showProgressBar(true)
                calculateBtn.isEnabled = false
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val source = WeatherSourceToggle.selected(weatherSourceToggle)
                        val climateYield = withContext(Dispatchers.IO) {
                            when (source) {
                                WeatherDataSource.IMD -> {
                                    ImdClimatologyRepository.predictClimateYieldTHa(
                                        this@ManualDetails,
                                        Lat.toDouble(),
                                        Long.toDouble()
                                    ).first
                                }
                                WeatherDataSource.NASA_POWER -> {
                                    CsvClimateYieldBackend.predictTHa(
                                        this@ManualDetails,
                                        Lat.toDouble(),
                                        Long.toDouble()
                                    )
                                }
                            }
                        }
                        pendingClimateYieldTHa = climateYield
                        sendRequest(
                            Long.toDouble(), Lat.toDouble(), pH.toDouble(), EC_dS_m.toDouble(),
                            OC.toDouble(), Avail_N_Kg_ha.toDouble(), Avail_P_kg_ha.toDouble(), Avail_K_kg_ha.toDouble(),
                            Soil_S_kg_ha.toDouble(), Soil_Zn_ppm.toDouble(), Soil_Fe_ppm.toDouble(), Soil_Mn_ppm.toDouble(),
                            Soil_Cu_ppm.toDouble(), Residue_Incor_Q_ha.toDouble(), adjustedNValue, adjustedPValue,
                            adjustedKValue, Zinc_Sul_kg_ha.toDouble(), FYM_t_ha.toDouble(), FYM_input_interval_Yr.toDouble(),
                            Sowing_DOY.toDouble(), Irr_no.toDouble(), harvestDoy, Grain_Yield_Q_ha.toDouble()
                        )
                    } catch (e: Exception) {
                        showProgressBar(false)
                        calculateBtn.isEnabled = true
                        showResultDialog("Error", e.message ?: "Climate yield failed")
                    }
                }
            } else {
                showResultDialog("Input Error", "Please enter all values.")
            }
        }

        val states = listOf("UP", "Bihar", "Punjab", "Haryana")
        val adapterStates = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, states)
        stateValue.setAdapter(adapterStates)

        val crops = listOf("चावल / Rice", "गेहूँ / Wheat")
        val adapterCrops = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops)
        actvcrop.setAdapter(adapterCrops)

        JulianDateHelper.attachDatePicker(this, swoingdateValue)

        val grains = listOf("चावल / Rice", "गेहूँ / Wheat")
        val riceVarieties = listOf("कम अवधि / Short Duration", "मध्यम अवधि / Medium Duration", "लंबी अवधि  / Long Duration")
        val wheatVarieties = listOf("कम अवधि / Short Duration", "मध्यम अवधि / Medium Duration", "लंबी अवधि  / Long Duration")

        val adapterGrains = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grains)

        val dropdownGrains = findViewById<TextInputLayout>(R.id.dropdown_menu_crop)
        val actvcrop = dropdownGrains.editText as? AutoCompleteTextView
        actvcrop?.setAdapter(adapterGrains)

        actvcrop?.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = adapterGrains.getItem(position)
            val dropdownVarieties = findViewById<TextInputLayout>(R.id.dropdown_menu_variety)
            dropdownVarieties.visibility = View.VISIBLE
            val actvvariety = dropdownVarieties.editText as? AutoCompleteTextView

            if (selectedItem == "चावल / Rice") {
                val adapterVarieties =
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, riceVarieties)
                actvvariety?.setAdapter(adapterVarieties)
            } else if (selectedItem == "गेहूँ / Wheat") {
                val adapterVarieties =
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, wheatVarieties)
                actvvariety?.setAdapter(adapterVarieties)
            }
        }
    }

    private fun convertAndDisplayNValue(): Double{
        //val convertedValue = n * 0.46
        //nValue.setText(String.format("%.2f", convertedValue))
        val nInput = nValue.text.toString().toDoubleOrNull() ?: 0.0
        val pInput = dapValue.text.toString().toDoubleOrNull() ?: 0.0

        // Perform the calculations as per the new requirement
        return (nInput * 0.46) + (pInput * 0.18)

        // Update the Appl_N_kg_ha TextInputEditText with the adjusted value
        //nValue.setText(String.format("%.2f", adjustedNValue))
    }


    private fun convertAndDisplayPValue(): Double{
        //val convertedValue = p * 0.46
        //dapValue.setText(String.format("%.2f", convertedValue))
        val nInput = dapValue.text.toString().toDoubleOrNull() ?: 0.0
        return (nInput * 0.46)
    }
    

    private fun convertAndDisplayKValue(): Double {
        //val convertedValue = k * 0.6
        //mopValue.setText(String.format("%.2f", convertedValue))
        val nInput = mopValue.text.toString().toDoubleOrNull() ?: 0.0
        return (nInput * 0.6)
    }

    // Function to show result like a pop-up screen
    private fun showResultDialog(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
                .show()
        }
    }

    private fun showCombinedYield(farmYieldText: String) {
        showResultDialog(
            getString(R.string.combined_yield_title),
            CsvClimateYieldBackend.stripUnits(farmYieldText)
        )
        calculateBtn.isEnabled = true
    }

    // Function to Handle the ProgressBar
    private fun showProgressBar(show: Boolean) {
        progressbarManagement.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun postJson(json: String, url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string()
                    if (responseString != null) {
                        val jsonResponse = JSONObject(responseString)
                        val result = jsonResponse.optString("result", "No result found")
                        Log.i("result", result)
                        withContext(Dispatchers.Main){
                            showCombinedYield(result)
                            showProgressBar(false)
                            Log.i("result", result)
                        }
                    }else{
                        withContext(Dispatchers.Main) {
                            showProgressBar(false)
                            if (MockYieldResponse.ENABLED) {
                                showCombinedYield(MockYieldResponse.resultFor(json))
                            } else {
                                calculateBtn.isEnabled = true
                                showResultDialog("Error", "Empty response")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main){
                    showProgressBar(false)
                    if (MockYieldResponse.ENABLED) {
                        Log.w("PostJsonError", "API failed, showing mock: ${e.message}", e)
                        showCombinedYield(MockYieldResponse.resultFor(json))
                    } else {
                        calculateBtn.isEnabled = true
                        showResultDialog("Error", "Failed to fetch result: ${e.message}")
                        Log.e("PostJsonError", "Failed to fetch result: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun sendRequest(Long: Double, Lat: Double, pH: Double,
                            EC_dS_m: Double, OC: Double, Avail_N_Kg_ha: Double,
                            Avail_P_kg_ha: Double, Avail_K_kg_ha: Double, Soil_S_kg_ha: Double,
                            Soil_Zn_ppm: Double, Soil_Fe_ppm: Double, Soil_Mn_ppm: Double,
                            Soil_Cu_ppm: Double, Residue_Incor_Q_ha: Double, adjustedNValue: Double,
                            adjustedPValue: Double, adjustedKValue: Double, Zinc_Sul_kg_ha: Double,
                            FYM_t_ha: Double, FYM_input_interval_Yr: Double, Sowing_DOY: Double,
                            Irr_no: Double, Harvest_DOY: Double, Grain_Yield_Q_ha: Double) {
        val state = stateValue.text.toString()
        val crop = actvcrop.text.toString()
        val url = when {
            state in listOf("UP", "Bihar") && crop == "चावल / Rice" -> "http://172.17.30.99:5000/rice_up_bihar"
            state in listOf("Punjab", "Haryana") && crop == "चावल / Rice" -> "http://172.17.30.99:5000/rice_pb_hr"
            state in listOf("UP", "Bihar") && crop == "गेहूँ / Wheat" -> "http://172.17.30.99:5000/wheat_up_bihar"
            state in listOf("Punjab", "Haryana") && crop == "गेहूँ / Wheat" -> "http://172.17.30.99:5000/wheat_pb_hr"
            else -> "" // Ideally this shouldn't happen but handle it as per your need
        }
        if (url.isNotEmpty()) {
            val json = """{"Long": $Long, "Lat": $Lat, "pH": $pH, 
                "EC_dS_m": $EC_dS_m, "OC": $OC, "Avail_N_Kg_ha": $Avail_N_Kg_ha, "Avail_P_kg_ha": $Avail_P_kg_ha, 
                "Avail_K_kg_ha": $Avail_K_kg_ha, "Soil_S_kg_ha": $Soil_S_kg_ha, "Soil_Zn_ppm": $Soil_Zn_ppm, 
                "Soil_Fe_ppm": $Soil_Fe_ppm, "Soil_Mn_ppm": $Soil_Mn_ppm, "Soil_Cu_ppm": $Soil_Cu_ppm, 
                "Residue_Incor_Q_ha": $Residue_Incor_Q_ha, "Appl_N_kg_ha": $adjustedNValue, "Appl_P2O5_kg_ha": $adjustedPValue, "Appl_K2O_kg_ha": $adjustedKValue, 
                "Zinc_Sul_kg_ha": $Zinc_Sul_kg_ha, "FYM_t_ha": $FYM_t_ha, "FYM_input_interval_Yr": $FYM_input_interval_Yr, 
                "Sowing_DOY": $Sowing_DOY, "Irr_no": $Irr_no, "Harvest_DOY": $Harvest_DOY, "Grain_Yield_Q_ha": $Grain_Yield_Q_ha}"""
            postJson(json, url)
        } else {
            runOnUiThread {
                showProgressBar(false)
                calculateBtn.isEnabled = true
                showResultDialog("Error", "Invalid state selected or URL configuration error")
            }
        }
    }
}