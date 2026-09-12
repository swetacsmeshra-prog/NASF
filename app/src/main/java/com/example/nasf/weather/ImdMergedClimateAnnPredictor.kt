package com.example.nasf.weather

import android.content.Context
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.tanh

/**
 * On-device 6-input ANN for the merged IMD district climatology CSV:
 * annual rain, Tmin, Tmax, and their 30-year daily stds → yield.
 */
class ImdMergedClimateAnnPredictor(context: Context) {

    data class Prediction(
        val yieldTHa: Double,
        val rmseTHa: Double,
        val maeTHa: Double,
        val r2: Double
    )

    private val modelJson: JSONObject

    init {
        val raw = context.assets.open(MODEL_PATH).bufferedReader().use { it.readText() }
        modelJson = JSONObject(raw)
    }

    fun predict(season: ImdClimatologyRepository.SeasonClimate): Prediction {
        val featureMap = linkedMapOf(
            "annual_rainfall_mm" to season.rainfallMm,
            "annual_tmin_mean" to season.tminMean,
            "annual_tmax_mean" to season.tmaxMean,
            "annual_pr_std" to season.prStd,
            "annual_tmin_std" to season.tminStd,
            "annual_tmax_std" to season.tmaxStd
        )
        val featureNames = modelJson.getJSONArray("feature_names")
        val defaults = modelJson.getJSONObject("defaults")
        val vector = DoubleArray(featureNames.length()) { i ->
            val name = featureNames.getString(i)
            featureMap[name] ?: defaults.optDouble("median_$name", 0.0)
        }
        val yieldTHa = predictAnn(vector)
        val metrics = modelJson.getJSONObject("ann").getJSONObject("metrics")
        return Prediction(
            yieldTHa = round2(yieldTHa),
            rmseTHa = metrics.optDouble("rmse_t_ha", Double.NaN),
            maeTHa = metrics.optDouble("mae_t_ha", Double.NaN),
            r2 = metrics.optDouble("r2", Double.NaN)
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

    private fun round2(v: Double) = (v * 100.0).toInt() / 100.0

    companion object {
        const val MODEL_PATH = "ml/yield_models_imd_merged_ann.json"
    }
}
