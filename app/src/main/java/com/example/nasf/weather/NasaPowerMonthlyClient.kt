package com.example.nasf.weather

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * NASA POWER Monthly API — keys are YYYYMM (months 01–12) and YYYY13 (annual aggregate).
 */
class NasaPowerMonthlyClient(
    private val httpClient: OkHttpClient = NasaPowerHttpClient.shared
) {

    data class FetchResult(val climateJson: JSONObject)

    suspend fun fetchMonthlyPoint(request: NasaPowerMonthlyRequest): FetchResult =
        withContext(Dispatchers.IO) {
            request.validate()?.let { throw IllegalArgumentException(it) }

            val builder = Uri.parse(WeatherConfig.MONTHLY_BASE_URL).buildUpon()
            request.toQueryMap().forEach { (key, value) ->
                builder.appendQueryParameter(key, value)
            }
            val url = builder.build().toString()

            httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body?.string()
                    ?: throw IllegalStateException("Empty response from NASA POWER")
                if (!response.isSuccessful) {
                    throw IllegalStateException("NASA POWER error ${response.code}: $body")
                }
                FetchResult(buildClimatePayload(JSONObject(body), request))
            }
        }

    private fun buildClimatePayload(root: JSONObject, request: NasaPowerMonthlyRequest): JSONObject {
        val parameterData = root.optJSONObject("properties")?.optJSONObject("parameter")
            ?: throw IllegalStateException("Unexpected NASA POWER response: missing properties.parameter")

        val climate = JSONObject()
        climate.put("source", "NASA_POWER_MONTHLY")
        val requestJson = JSONObject()
        request.toQueryMap().forEach { (key, value) -> requestJson.put(key, value) }
        climate.put("request", requestJson)

        root.optJSONObject("header")?.let { climate.put("header", it) }
        root.optJSONObject("geometry")?.let { climate.put("geometry", it) }
        root.optJSONObject("parameters")?.let { climate.put("parameter_metadata", it) }

        val messages = root.optJSONArray("messages")
        if (messages != null && messages.length() > 0) {
            climate.put("messages", messages)
        }

        val monthlySeries = JSONObject()
        val annualSeries = JSONObject()
        val monthlySummaries = JSONObject()

        val paramKeys = parameterData.keys()
        while (paramKeys.hasNext()) {
            val name = paramKeys.next()
            val raw = parameterData.optJSONObject(name) ?: continue

            val monthly = JSONObject()
            val annual = JSONObject()
            val monthlyValues = mutableListOf<Double>()

            val periodKeys = raw.keys()
            while (periodKeys.hasNext()) {
                val period = periodKeys.next()
                val v = raw.optDouble(period, Double.NaN)
                if (!WeatherStatistics.isValidValue(v)) continue

                when {
                    isAnnualKey(period) -> annual.put(period, v)
                    isMonthlyKey(period) -> {
                        monthly.put(period, v)
                        monthlyValues.add(v)
                    }
                }
            }

            if (monthly.length() > 0) monthlySeries.put(name, monthly)
            if (annual.length() > 0) annualSeries.put(name, annual)

            val units = root.optJSONObject("parameters")
                ?.optJSONObject(name)
                ?.optString("units", "")
                ?: ""

            WeatherStatistics.summarize(monthlyValues, units)?.let { summary ->
                monthlySummaries.put(
                    name,
                    JSONObject()
                        .put("mean", summary.mean)
                        .put("median", summary.median)
                        .put("min", summary.min)
                        .put("max", summary.max)
                        .put("count", summary.count)
                        .put("units", summary.units)
                )
            }
        }

        climate.put("parameter_monthly", monthlySeries)
        climate.put("parameter_annual", annualSeries)
        climate.put("summary", monthlySummaries)
        return climate
    }

    /** YYYYMM where MM is 01–12. */
    fun isMonthlyKey(period: String): Boolean =
        period.length == 6 && period.substring(4, 6).toIntOrNull() in 1..12

    /** YYYY13 is the annual aggregate in NASA POWER monthly responses. */
    fun isAnnualKey(period: String): Boolean =
        period.length == 6 && period.endsWith("13")
}
