package com.example.nasf.weather

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.WeatherActivity

/**
 * Launch the weather step from existing screens without modifying their internals.
 * Copy the snippets in docs/WEATHER_WIRING_GUIDE.md into Manual/Automatic/Soil when ready.
 */
object WeatherNavigation {

    fun launch(
        activity: AppCompatActivity,
        flowType: String,
        latitude: Double,
        longitude: Double,
        sowingDateYmd: String?,
        harvestDateYmd: String?,
        mlUrl: String,
        mlPayloadJson: String,
        crop: String = "",
        state: String = "",
        resultTitle: String = "Yield Prediction",
        useHtmlResult: Boolean = false
    ) {
        if (!JulianDateHelper.isValidSeason(sowingDateYmd, harvestDateYmd)) {
            Toast.makeText(
                activity,
                "Please select valid sowing and harvest dates.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (mlUrl.isBlank()) {
            Toast.makeText(activity, "Invalid crop or state selection.", Toast.LENGTH_SHORT).show()
            return
        }
        activity.startActivity(
            Intent(activity, WeatherActivity::class.java).apply {
                putExtra(CropSessionExtras.FLOW_TYPE, flowType)
                putExtra(CropSessionExtras.LATITUDE, latitude)
                putExtra(CropSessionExtras.LONGITUDE, longitude)
                putExtra(CropSessionExtras.SOWING_DATE_YMD, sowingDateYmd)
                putExtra(CropSessionExtras.HARVEST_DATE_YMD, harvestDateYmd)
                putExtra(CropSessionExtras.CROP, crop)
                putExtra(CropSessionExtras.STATE, state)
                putExtra(CropSessionExtras.ML_URL, mlUrl)
                putExtra(CropSessionExtras.ML_PAYLOAD_JSON, mlPayloadJson)
                putExtra(CropSessionExtras.RESULT_TITLE, resultTitle)
                putExtra(CropSessionExtras.USE_HTML_RESULT, useHtmlResult)
            }
        )
    }
}
