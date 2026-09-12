package com.example.nasf.weather

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.WeeklyAnalyticsReportActivity
import org.json.JSONObject

object WeeklyAnalyticsNavigation {

    fun launch(
        activity: AppCompatActivity,
        weatherJson: JSONObject,
        latitude: Double,
        longitude: Double,
        sowingDateYmd: String,
        harvestDateYmd: String,
        flowType: String,
        mlUrl: String,
        mlPayloadJson: String,
        crop: String = "",
        state: String = "",
        resultTitle: String = "Yield Prediction",
        useHtmlResult: Boolean = false,
        calendarAnalyzedJson: String = ""
    ) {
        activity.startActivity(
            Intent(activity, WeeklyAnalyticsReportActivity::class.java).apply {
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
                putExtra(CropSessionExtras.WEATHER_JSON, weatherJson.toString())
                putExtra(CropSessionExtras.CALENDAR_ANALYZED_JSON, calendarAnalyzedJson)
            }
        )
    }
}
