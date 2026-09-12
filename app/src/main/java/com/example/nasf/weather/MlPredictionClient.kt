package com.example.nasf.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MlPredictionClient(private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun postRaw(url: String, payloadJson: String): String = withContext(Dispatchers.IO) {
        val body = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        httpClient.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
            val responseString = response.body?.string()
                ?: throw IllegalStateException("Empty response from prediction API")
            if (!response.isSuccessful) {
                throw IllegalStateException("Prediction API error ${response.code}: $responseString")
            }
            responseString
        }
    }

    fun mergeWeatherIntoPayload(basePayloadJson: String, weatherJson: JSONObject): String {
        val base = JSONObject(basePayloadJson)
        base.put("weather", weatherJson)
        return base.toString()
    }

    fun mergeAnalyzedIntoPayload(
        basePayloadJson: String,
        weatherJson: JSONObject,
        analyzedJson: JSONObject
    ): String {
        val base = JSONObject(basePayloadJson)
        base.put("weather", weatherJson)
        base.put("weather_analyzed", analyzedJson)
        return base.toString()
    }
}
