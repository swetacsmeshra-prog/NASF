package com.example.nasf.weather

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class NasaPowerClient(
    private val httpClient: OkHttpClient = NasaPowerHttpClient.shared
) {

    data class FetchResult(
        val summaryText: String,
        val weatherJson: JSONObject
    )

    suspend fun fetchDailyPoint(request: NasaPowerRequest): FetchResult = withContext(Dispatchers.IO) {
        request.validate()?.let { throw IllegalArgumentException(it) }

        val builder = Uri.parse(WeatherConfig.BASE_URL).buildUpon()
        request.toQueryMap().forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }
        val url = builder.build().toString()

        val httpRequest = Request.Builder().url(url).get().build()
        httpClient.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from NASA POWER")
            if (!response.isSuccessful) {
                throw IllegalStateException("NASA POWER error ${response.code}: $body")
            }
            val root = JSONObject(body)
            val weatherJson = buildWeatherPayload(root, request)
            FetchResult(buildSummaryText(root, weatherJson), weatherJson)
        }
    }

    private fun buildWeatherPayload(root: JSONObject, request: NasaPowerRequest): JSONObject {
        val parameterData = root.optJSONObject("properties")?.optJSONObject("parameter")
            ?: throw IllegalStateException("Unexpected NASA POWER response: missing properties.parameter")

        val weather = JSONObject()
        weather.put("source", "NASA_POWER")
        val requestJson = JSONObject()
        request.toQueryMap().forEach { (key, value) -> requestJson.put(key, value) }
        weather.put("request", requestJson)

        root.optJSONObject("header")?.let { weather.put("header", it) }
        root.optJSONObject("geometry")?.let { weather.put("geometry", it) }
        root.optJSONObject("parameters")?.let { weather.put("parameter_metadata", it) }

        val messages = root.optJSONArray("messages")
        if (messages != null && messages.length() > 0) {
            weather.put("messages", messages)
        }

        val paramSeries = JSONObject()
        val paramSummaries = JSONObject()

        val keys = parameterData.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val daily = parameterData.optJSONObject(name) ?: continue
            paramSeries.put(name, daily)

            val values = mutableListOf<Double>()
            val dateKeys = daily.keys()
            while (dateKeys.hasNext()) {
                val key = dateKeys.next()
                val v = daily.optDouble(key, Double.NaN)
                if (WeatherStatistics.isValidValue(v)) values.add(v)
            }

            val units = root.optJSONObject("parameters")
                ?.optJSONObject(name)
                ?.optString("units", "")
                ?: ""

            val summary = WeatherStatistics.summarize(values, units) ?: continue
            paramSummaries.put(
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

        weather.put("parameter", paramSeries)
        weather.put("summary", paramSummaries)
        return weather
    }

    private fun buildSummaryText(root: JSONObject, weather: JSONObject): String {
        val sb = StringBuilder()
        val header = root.optJSONObject("header")
        if (header != null) {
            val apiName = header.optJSONObject("api")?.optString("name", "NASA POWER")
            val apiVersion = header.optJSONObject("api")?.optString("version").orEmpty()
            sb.append(apiName)
            if (apiVersion.isNotEmpty()) sb.append(" ").append(apiVersion)
            sb.append("\n")
            sb.append("Period: ")
                .append(header.optString("start", weather.optJSONObject("request")?.optString("start", "")))
                .append(" – ")
                .append(header.optString("end", weather.optJSONObject("request")?.optString("end", "")))
            sb.append(" (").append(header.optString("time_standard", "UTC")).append(")\n")
        }

        root.optJSONObject("geometry")?.optJSONArray("coordinates")?.let { coords ->
            if (coords.length() >= 2) {
                sb.append("Location: ")
                    .append(coords.optDouble(1)).append("° lat, ")
                    .append(coords.optDouble(0)).append("° lon")
                if (coords.length() >= 3) {
                    sb.append(", elev ").append(coords.optDouble(2)).append(" m")
                }
                sb.append("\n")
            }
        }

        sb.append("\n")
        weather.optJSONObject("summary")?.let { summary ->
            val keys = summary.keys()
            while (keys.hasNext()) {
                val param = keys.next()
                val stats = summary.optJSONObject(param) ?: continue
                val units = stats.optString("units", "")
                val unitSuffix = if (units.isNotEmpty()) " ($units)" else ""
                sb.append(param).append(unitSuffix).append(": ")
                    .append("min ").append(stats.optDouble("min"))
                    .append(", max ").append(stats.optDouble("max"))
                    .append(", mean ").append(stats.optDouble("mean"))
                    .append(", median ").append(stats.optDouble("median"))
                    .append(", days ").append(stats.optInt("count"))
                    .append("\n")
            }
        }

        val messages = root.optJSONArray("messages")
        if (messages != null && messages.length() > 0) {
            sb.append("\nMessages:\n")
            for (i in 0 until messages.length()) {
                sb.append("• ").append(messages.optString(i)).append("\n")
            }
        }

        return sb.toString().trim()
    }
}
