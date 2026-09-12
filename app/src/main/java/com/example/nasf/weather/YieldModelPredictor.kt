package com.example.nasf.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

/**
 * On-device yield prediction using bundled Random Forest and cubic models.
 */
class YieldModelPredictor(context: Context) {

    data class PredictionResult(
        val randomForestYieldTHa: Double,
        val cubicYieldTHa: Double,
        val topFeatures: List<Pair<String, Double>>,
        val modelInfo: String
    )

    private val modelJson: JSONObject

    init {
        val raw = context.assets.open("ml/yield_models.json")
            .bufferedReader()
            .use { it.readText() }
        modelJson = JSONObject(raw)
    }

    /** Validation MAE of the bundled RF model (t/ha), used for ± Kg/ha display. */
    fun maeTHa(): Double =
        modelJson.getJSONObject("random_forest").optDouble("mae_t_ha", 0.26)

    fun predict(features: FeatureVectorBuilder.ModelFeatures): PredictionResult {
        val featureMap = FeatureVectorBuilder.reducedFeatureMap(features)
        val featureNames = modelJson.getJSONArray("feature_names")
        val defaults = modelJson.getJSONObject("defaults")
        val vector = DoubleArray(featureNames.length()) { i ->
            val name = featureNames.getString(i)
            val raw = featureMap[name]
            when {
                raw != null && !raw.isNaN() -> raw
                name == "season_rainfall_mm" -> defaults.optDouble("median_season_rainfall_mm", 0.0)
                else -> 0.0
            }
        }

        val rfYield = predictRandomForest(vector)
        val cubicYield = predictCubic(features.seasonRainfallMm)
        val importances = modelJson.getJSONObject("random_forest").getJSONObject("feature_importance")
        val topFeatures = importances.keys()
            .asSequence()
            .map { key -> key to importances.getDouble(key) }
            .sortedByDescending { it.second }
            .take(5)
            .toList()

        val trainedOn = modelJson.optInt("trained_on_farms", 0)
        val rfMae = maeTHa()
        val info = "Random Forest · Jun–Oct season data · $trainedOn farms · MAE ${"%.2f".format(rfMae)} t/ha"

        return PredictionResult(
            randomForestYieldTHa = round2(rfYield),
            cubicYieldTHa = round2(cubicYield),
            topFeatures = topFeatures,
            modelInfo = info
        )
    }

    fun cubicCurvePoints(
        seasonRainfallMm: Double,
        pointCount: Int = 40
    ): List<Pair<Double, Double>> {
        val cubic = modelJson.getJSONObject("cubic_model")
        val xMin = cubic.optDouble("x_min", 0.0)
        val storedMax = cubic.optDouble("x_max", seasonRainfallMm)
        val xMax = maxOf(storedMax, seasonRainfallMm)
        val step = (xMax - xMin) / (pointCount - 1).coerceAtLeast(1)
        return (0 until pointCount).map { i ->
            val x = xMin + step * i
            x to predictCubic(x)
        }
    }

    private fun predictRandomForest(vector: DoubleArray): Double {
        val rf = modelJson.getJSONObject("random_forest")
        val trees = rf.getJSONArray("trees")
        if (trees.length() == 0) {
            return modelJson.getJSONObject("defaults").optDouble("mean_yield_t_ha", 4.0)
        }
        var sum = 0.0
        for (i in 0 until trees.length()) {
            sum += traverseTree(trees.getJSONObject(i).getJSONArray("nodes"), vector)
        }
        return sum / trees.length()
    }

    private fun traverseTree(nodes: JSONArray, vector: DoubleArray): Double {
        var idx = 0
        while (true) {
            val node = nodes.getJSONObject(idx)
            val featureIndex = node.getInt("feature_index")
            if (featureIndex < 0) {
                return node.getDouble("value")
            }
            val threshold = node.getDouble("threshold")
            val value = vector.getOrElse(featureIndex) { 0.0 }
            idx = if (value <= threshold) node.getInt("left") else node.getInt("right")
        }
    }

    private fun predictCubic(seasonRainfallMm: Double): Double {
        val cubic = modelJson.getJSONObject("cubic_model")
        val coefs = cubic.getJSONArray("coefficients")
        val x = seasonRainfallMm
        var y = cubic.optDouble("intercept", 0.0)
        for (d in 0 until coefs.length()) {
            y += coefs.getDouble(d) * x.pow(d.toDouble())
        }
        return y.coerceIn(1.0, 12.0)
    }

    private fun round2(v: Double) = (v * 100).toInt() / 100.0
}
