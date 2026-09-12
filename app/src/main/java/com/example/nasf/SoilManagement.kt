package com.example.nasf

import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.nasf.weather.CsvClimateYieldBackend
import com.example.nasf.weather.LocationAutofill
import com.example.nasf.weather.MockSoilAdvisory
import com.example.nasf.weather.MlUrlResolver
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.memberProperties

class SoilManagement : AppCompatActivity() {

    private lateinit var longValue: TextInputEditText
    private lateinit var latValue: TextInputEditText
    private lateinit var cropName: AutoCompleteTextView
    private lateinit var cropVariety: AutoCompleteTextView
    private lateinit var grainYield: TextInputEditText
    private lateinit var progressbarSoilManagement: ProgressBar
    private lateinit var getdetailsBtn: Button
    private lateinit var locationAutofill: LocationAutofill
    private var pendingClimateYieldTHa: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soil_management)

        longValue = findViewById(R.id.Long)
        latValue = findViewById(R.id.Lat)
        cropName = findViewById(R.id.autoCompleteTextViewCrop)
        cropVariety = findViewById(R.id.autoCompleteTextViewVariety)
        grainYield = findViewById(R.id.Grain_Yield_t_ha)
        getdetailsBtn = findViewById(R.id.getdetailsbtn)
        progressbarSoilManagement = findViewById(R.id.progressbarsoilManagement)
        LocationAutofill(this, latValue, longValue).also { locationAutofill = it }.start()

        getdetailsBtn.setOnClickListener {
            val Long = longValue.text.toString()
            val Lat = latValue.text.toString()
            val autoCompleteTextViewCrop = cropName.text.toString()
            val autoCompleteTextViewVariety = cropVariety.text.toString()
            val Grain_Yield_t_ha = grainYield.text.toString()

            if (Long.isNotEmpty() && Lat.isNotEmpty() && autoCompleteTextViewCrop.isNotEmpty() &&
                autoCompleteTextViewVariety.isNotEmpty() && Grain_Yield_t_ha.isNotEmpty()
            ) {
                showProgressBar(true)
                getdetailsBtn.isEnabled = false
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        pendingClimateYieldTHa = withContext(Dispatchers.IO) {
                            CsvClimateYieldBackend.predictTHa(
                                this@SoilManagement,
                                Lat.toDouble(),
                                Long.toDouble()
                            )
                        }
                        sendRequest(Long.toDouble(), Lat.toDouble(), Grain_Yield_t_ha.toDouble())
                    } catch (e: Exception) {
                        showProgressBar(false)
                        getdetailsBtn.isEnabled = true
                        showResultDialog("Error", e.message ?: "Climate yield failed")
                    }
                }
            } else {
                showResultDialog("Input Error", "Please enter all values.")
            }
        }

        val crops = listOf("चावल / Rice", "गेहूँ / Wheat")
        val adapterCrops = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops)
        cropName.setAdapter(adapterCrops)

        val grains = listOf("चावल / Rice", "गेहूँ / Wheat")
        val riceVarieties = listOf("कम अवधि / Short Duration", "मध्यम अवधि / Medium Duration", "लंबी अवधि  / Long Duration")
        val wheatVarieties = listOf("कम अवधि / Short Duration", "मध्यम अवधि / Medium Duration", "लंबी अवधि  / Long Duration")

        val adapterGrains = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grains)

        val dropdownGrains = findViewById<TextInputLayout>(R.id.dropdown_menu_crop)
        val cropName = dropdownGrains.editText as? AutoCompleteTextView
        cropName?.setAdapter(adapterGrains)

        cropName?.setOnItemClickListener { _, _, position, _ ->
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
    // Function to show result like a pop-up screen
    private fun showResultDialog(title: String, result: String) {
        runOnUiThread {
            val message = result
            val dialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .create()
            (dialog.findViewById(android.R.id.message) as TextView?)?.movementMethod =
                LinkMovementMethod.getInstance()
            dialog.show()
            getdetailsBtn.isEnabled = true
        }
    }

    // Function to Handle the ProgressBar
    private fun showProgressBar(show: Boolean) {
        progressbarSoilManagement.visibility = if (show) View.VISIBLE else View.GONE
    }
    // Function to Handle the PostJson
    private fun postJson(
        json: String,
        url: String,
        longitude: Double,
        latitude: Double,
        grainYieldTHa: Double,
        crop: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseString = response.body?.string()
                    if (response.isSuccessful && !responseString.isNullOrBlank()) {
                        val gson = Gson()
                        val apiResponse = gson.fromJson(responseString, ApiResponse::class.java)
                        val results = constructDisplayString(apiResponse)
                        withContext(Dispatchers.Main) {
                            showResultDialog("", results)
                            showProgressBar(false)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showProgressBar(false)
                            if (MockSoilAdvisory.ENABLED) {
                                showMockAdvisory(longitude, latitude, grainYieldTHa, crop)
                            } else {
                                showResultDialog("Error", "Empty response")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showProgressBar(false)
                    if (MockSoilAdvisory.ENABLED) {
                        Log.w("SoilManagement", "API failed, showing offline advisory: ${e.message}", e)
                        showMockAdvisory(longitude, latitude, grainYieldTHa, crop)
                    } else {
                        showResultDialog("Error", "Failed to fetch result: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showMockAdvisory(
        longitude: Double,
        latitude: Double,
        grainYieldTHa: Double,
        crop: String
    ) {
        val apiResponse = MockSoilAdvisory.build(longitude, latitude, grainYieldTHa, crop)
        showResultDialog("", constructDisplayString(apiResponse))
    }

    // Function to Handle the Hindi Translation
    private fun getHindiTranslation(englishKey: String, isPixelValue: Boolean = true): String {
        // Mappings for Pixel Values (English to Hindi)
        val pixelValuesMapping = mapOf(
            "Soil Organic Carbon" to "मिट्टी जैविक कार्बन",
            "pH" to "पी एच",
            "EC" to "ई सी",
            "Available Nitrogen" to "उपलब्ध नाइट्रोजन",
            "Available Phosphorus" to "उपलब्ध फास्फोरस",
            "Available Potassium" to "उपलब्ध पोटेशियम",
            "Sulphur" to "गंधक",
            "Zinc" to "जस्ता",
            "Iron" to "आयरन",
            "Manganese" to "मैंगनीज़",
            "Copper" to "कॉपर"
        )

        // Mappings for Parameter Values (English to Hindi)
        val parameterValuesMapping = mapOf(
            "FYM" to "एफ वाई एम",
            "Residue Incorporation" to "अवशेष निगमन",
            "Sowing Date" to "बुवाई की तारीख",
            "Urea" to "यूरिया",
            "DAP" to "डी ए पी",
            "MOP" to "एम ओ पी",
            "Irrigation Number" to "सिंचाई संख्या",
            "Zinc Sulphate" to "जस्ता सल्फ़ेट",
        )

        return if (isPixelValue) {
            pixelValuesMapping[englishKey] ?: ""
        } else {
            parameterValuesMapping[englishKey] ?: ""
        }
    }

    // Function to get the unit for a given property
    private fun getUnitForProperty(propertyName: String): String {
        val unitsMapping = mapOf(
            "Soil Organic Carbon" to "%",
            "pH" to "", // pH doesn't usually have a unit
            "EC" to "dS/m",
            "Available Nitrogen" to "kg/ha",
            "Available Phosphorus" to "mg/kg",
            "Available Potassium" to "mg/kg",
            "Sulphur" to "mg/kg",
            "Zinc" to "mg/kg",
            "Iron" to "mg/kg",
            "Manganese" to "mg/kg",
            "Copper" to "mg/kg",
            "FYM" to "t/ha",
            "Residue Incorporation" to "q/ha",
            "Sowing Date" to "",
            "Urea" to "kg/ha",
            "DAP" to "kg/ha",
            "MOP" to "kg/ha",
            "Irrigation Number" to "",
            "Zinc Sulphate" to "kg/ha"
        )
        return unitsMapping[propertyName] ?: ""
    }


    private fun constructDisplayString(apiResponse: ApiResponse): String {
        val stringBuilder = StringBuilder()

        // Bold Titles for Sections
        val soilPropertiesTitle = "<big><b>Soil Properties</b></big>"
        val cropManagementAdvisoryTitle = "<big><b>Crop Management Advisory</b></big>"

        // Adding Pixel Values to the HTML string
        stringBuilder.append("$soilPropertiesTitle<br><br>")
        var counter = 1 // Initialize counter for numbering
        apiResponse.pixel_values.let { pixelValues ->
            PixelValues::class.memberProperties.forEach { property ->
                val englishName = property.name.replace("_", " ").replace("tif", "").trim()
                val hindiName = getHindiTranslation(englishName)
                val unit = getUnitForProperty(englishName)
                stringBuilder.append("\t${counter++}. $englishName / $hindiName: <span style=\"color:red;\">${property.get(pixelValues)} $unit</span><br>")
            }
        }

        // Adding a separator and resetting counter
        stringBuilder.append("<br>")
        counter = 1 // Reset counter for the next section

        // Bold title for "Crop Management Advisory"
        stringBuilder.append("$cropManagementAdvisoryTitle<br><br>")
        apiResponse.parameter_values.let { parameterValues ->
            ParameterValues::class.memberProperties.forEach { property ->
                val englishName = property.name.replace("_", " ")
                val hindiName = getHindiTranslation(englishName, isPixelValue = false)
                val unit = getUnitForProperty(englishName)
                stringBuilder.append("\t${counter++}. $englishName / $hindiName: <span style=\"color:red;\">${property.get(parameterValues)} $unit</span><br>")
            }
        }

        return stringBuilder.toString()
    }

    // Function to Handle the Send Request
    private fun sendRequest(Long: Double, Lat: Double, Grain_Yield_t_ha: Double) {
        val cropType = cropName.text.toString()
        val url = MlUrlResolver.soilAdvisoryUrl(cropType)
        if (url.isNotEmpty()) {
            val json = """{"Long": $Long, "Lat": $Lat, "Grain_Yield_t_ha": $Grain_Yield_t_ha}"""
            postJson(json, url, Long, Lat, Grain_Yield_t_ha, cropType)
        } else {
            runOnUiThread {
                showProgressBar(false)
                showResultDialog("Error", "Invalid crop selected or URL configuration error")
            }
        }
    }
}
