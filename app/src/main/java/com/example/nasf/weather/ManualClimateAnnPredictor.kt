package com.example.nasf.weather

import android.content.Context
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.tanh

/**
 * On-device 6-input climate ANN for the manual yield form:
 * Rain, Tmin, Tmax, Wind, Solar, Humidity → yield.
 */
class ManualClimateAnnPredictor(context: Context) {

    data class ClimateInputs(
        val seasonRainfallMm: Double,
        val seasonTMinMean: Double,
        val seasonTMaxMean: Double,
        val seasonWindMean: Double,
        val seasonSolarMean: Double,
        val seasonHumidityMean: Double
    )

    data class ModelMetrics(
        val rmseTHa: Double,
        val stdvResidualTHa: Double,
        val maeTHa: Double,
        val r2: Double
    )

    data class Prediction(
        val yieldTHa: Double,
        val metrics: ModelMetrics,
        val architectureLabel: String,
        val trainedOnFarms: Int,
        val trainWeekStart: Int,
        val trainWeekEnd: Int,
        val defaults: Map<String, Double>
    )

    private val modelJson: JSONObject

    init {
        val raw = context.assets.open("ml/yield_models_manual_climate_ann.json")
            .bufferedReader()
            .use { it.readText() }
        modelJson = JSONObject(raw)
    }

    fun defaultHints(): Map<String, Double> {
        val defaults = modelJson.getJSONObject("defaults")
        return linkedMapOf(
            "season_rainfall_mm" to defaults.optDouble("median_season_rainfall_mm", 0.0),
            "season_t_min_mean" to defaults.optDouble("median_season_t_min_mean", 0.0),
            "season_t_max_mean" to defaults.optDouble("median_season_t_max_mean", 0.0),
            "season_wind_mean" to defaults.optDouble("median_season_wind_mean", 0.0),
            "season_solar_mean" to defaults.optDouble("median_season_solar_mean", 0.0),
            "season_humidity_mean" to defaults.optDouble("median_season_humidity_mean", 0.0)
        )
    }

    fun predict(inputs: ClimateInputs): Prediction {
        val featureMap = linkedMapOf(
            "season_rainfall_mm" to inputs.seasonRainfallMm,
            "season_t_min_mean" to inputs.seasonTMinMean,
            "season_t_max_mean" to inputs.seasonTMaxMean,
            "season_wind_mean" to inputs.seasonWindMean,
            "season_solar_mean" to inputs.seasonSolarMean,
            "season_humidity_mean" to inputs.seasonHumidityMean
        )
        val featureNames = modelJson.getJSONArray("feature_names")
        val defaults = modelJson.getJSONObject("defaults")
        val vector = DoubleArray(featureNames.length()) { i ->
            val name = featureNames.getString(i)
            featureMap[name] ?: defaults.optDouble("median_$name", 0.0)
        }

        val yield = predictAnn(vector)
        val ann = modelJson.getJSONObject("ann")
        val metrics = readMetrics(ann.getJSONObject("metrics"))
        val weeks = modelJson.getJSONObject("crop_season_weeks")
        val hidden = ann.getJSONObject("model").getJSONArray("hidden_layer_sizes")
        val sizes = (0 until hidden.length()).joinToString("-") { hidden.getInt(it).toString() }

        return Prediction(
            yieldTHa = round2(yield),
            metrics = metrics,
            architectureLabel = "ANN (6 → $sizes → 1)",
            trainedOnFarms = modelJson.optInt("trained_on_farms", 0),
            trainWeekStart = weeks.optInt("start", 23),
            trainWeekEnd = weeks.optInt("end", 44),
            defaults = defaultHints()
        )
    }

    private fun predictAnn(vector: DoubleArray): Double {
        val ann = modelJson.getJSONObject("ann")
        val model = ann.getJSONObject("model")
        val mean = ann.getJSONArray("scaler_mean")
        val scale = ann.getJSONArray("scaler_scale")
        val scaled = DoubleArray(vector.size) { i ->
            val s = scale.optDouble(i, 1.0).takeIf { it != 0.0 } ?: 1.0
            (vector[i] - mean.optDouble(i, 0.0)) / s
        }
        val coefs = model.getJSONArray("coefs")
        val intercepts = model.getJSONArray("intercepts")
        val activation = model.optString("activation", "relu")

        var layerInput = scaled
        for (layer in 0 until coefs.length()) {
            val wFlat = coefs.getJSONArray(layer)
            val b = intercepts.getJSONArray(layer)
            val inSize = layerInput.size
            val outSize = b.length()
            val out = DoubleArray(outSize)
            for (j in 0 until outSize) {
                var sum = b.optDouble(j, 0.0)
                for (k in 0 until inSize) {
                    sum += layerInput[k] * wFlat.optDouble(k * outSize + j, 0.0)
                }
                out[j] = if (layer < coefs.length() - 1) activate(sum, activation) else sum
            }
            layerInput = out
        }
        return layerInput.firstOrNull()?.coerceIn(1.0, 12.0) ?: 4.0
    }

    private fun activate(x: Double, activation: String): Double = when (activation) {
        "tanh" -> tanh(x)
        "logistic" -> 1.0 / (1.0 + kotlin.math.exp(-x))
        else -> max(0.0, x)
    }

    private fun readMetrics(obj: JSONObject) = ModelMetrics(
        rmseTHa = obj.optDouble("rmse_t_ha", Double.NaN),
        stdvResidualTHa = obj.optDouble("stdv_residual_t_ha", Double.NaN),
        maeTHa = obj.optDouble("mae_t_ha", Double.NaN),
        r2 = obj.optDouble("r2", Double.NaN)
    )

    private fun round2(v: Double) = (v * 100).toInt() / 100.0
}
