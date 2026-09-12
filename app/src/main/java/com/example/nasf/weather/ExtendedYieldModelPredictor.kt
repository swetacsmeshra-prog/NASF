package com.example.nasf.weather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/**
 * On-device yield prediction for wind-power season (weeks 23–44):
 * Random Forest, Cubic, XGBoost, ANN, SVM.
 */
class ExtendedYieldModelPredictor(context: Context) {

    data class ModelMetrics(
        val rmseTHa: Double,
        val rmsTHa: Double,
        val stdvResidualTHa: Double,
        val maeTHa: Double,
        val r2: Double
    )

    data class ModelPrediction(
        val name: String,
        val yieldTHa: Double,
        val metrics: ModelMetrics,
        val topFeatures: List<Pair<String, Double>> = emptyList(),
        val infoLine: String = ""
    )

    data class ExtendedPredictionResult(
        val randomForest: ModelPrediction,
        val cubic: ModelPrediction,
        val xgboost: ModelPrediction,
        val ann: ModelPrediction,
        val svm: ModelPrediction,
        val trainedOnFarms: Int
    ) {
        val allModels: List<ModelPrediction> =
            listOf(randomForest, cubic, xgboost, ann, svm)
    }

    private val modelJson: JSONObject

    init {
        val raw = context.assets.open("ml/yield_models_extended.json")
            .bufferedReader()
            .use { it.readText() }
        modelJson = JSONObject(raw)
    }

    fun predict(features: FeatureVectorBuilder.ModelFeatures): ExtendedPredictionResult {
        val vector = featureVector(features)
        val rainfall = features.seasonRainfallMm

        val rf = modelJson.getJSONObject("random_forest")
        val rfImportances = rf.getJSONObject("feature_importance")
        val rfTop = rfImportances.keys()
            .asSequence()
            .map { it to rfImportances.getDouble(it) }
            .sortedByDescending { it.second }
            .take(5)
            .toList()

        val xgb = modelJson.getJSONObject("xgboost")
        val xgbImportances = xgb.optJSONObject("feature_importance")
        val xgbTop = xgbImportances?.let { imp ->
            imp.keys().asSequence()
                .map { it to imp.getDouble(it) }
                .sortedByDescending { it.second }
                .take(5)
                .toList()
        } ?: emptyList()

        return ExtendedPredictionResult(
            randomForest = ModelPrediction(
                name = "Random Forest",
                yieldTHa = round2(predictTreeEnsemble(rf.getJSONArray("trees"), vector)),
                metrics = readMetrics(rf.getJSONObject("metrics")),
                topFeatures = rfTop,
                infoLine = "Algorithm: Random Forest"
            ),
            cubic = ModelPrediction(
                name = "Cubic Model",
                yieldTHa = round2(predictCubic(rainfall)),
                metrics = readMetrics(modelJson.getJSONObject("cubic_model").getJSONObject("metrics")),
                infoLine = "Algorithm: Cubic (degree 3)"
            ),
            xgboost = ModelPrediction(
                name = "XGBoost",
                yieldTHa = round2(predictTreeEnsemble(xgb.getJSONArray("trees"), vector)),
                metrics = readMetrics(xgb.getJSONObject("metrics")),
                topFeatures = xgbTop,
                infoLine = xgb.optString("note", "Algorithm: XGBoost")
                    .ifBlank { "Algorithm: XGBoost" }
            ),
            ann = ModelPrediction(
                name = "ANN",
                yieldTHa = round2(predictAnn(vector)),
                metrics = readMetrics(modelJson.getJSONObject("ann").getJSONObject("metrics")),
                infoLine = annArchitectureLabel()
            ),
            svm = ModelPrediction(
                name = "SVM",
                yieldTHa = round2(predictSvm(vector)),
                metrics = readMetrics(modelJson.getJSONObject("svm").getJSONObject("metrics")),
                infoLine = svmInfoLabel()
            ),
            trainedOnFarms = modelJson.optInt("trained_on_farms", 0)
        )
    }

    fun cubicCurvePoints(seasonRainfallMm: Double, pointCount: Int = 40): List<Pair<Double, Double>> {
        val cubic = modelJson.getJSONObject("cubic_model")
        val xMin = cubic.optDouble("x_min", 0.0)
        val storedMax = cubic.optDouble("x_max", seasonRainfallMm)
        val xMax = max(storedMax, seasonRainfallMm)
        val step = (xMax - xMin) / (pointCount - 1).coerceAtLeast(1)
        return (0 until pointCount).map { i ->
            val x = xMin + step * i
            x to predictCubic(x)
        }
    }

    private fun featureVector(features: FeatureVectorBuilder.ModelFeatures): DoubleArray {
        val featureNames = modelJson.getJSONArray("feature_names")
        val featureMap = FeatureVectorBuilder.reducedFeatureMap(features)
        val defaults = modelJson.getJSONObject("defaults")
        return DoubleArray(featureNames.length()) { i ->
            val name = featureNames.getString(i)
            featureMap[name] ?: defaults.optDouble("median_season_rainfall_mm", 0.0)
        }
    }

    private fun readMetrics(obj: JSONObject) = ModelMetrics(
        rmseTHa = obj.optDouble("rmse_t_ha", Double.NaN),
        rmsTHa = obj.optDouble("rms_t_ha", Double.NaN),
        stdvResidualTHa = obj.optDouble("stdv_residual_t_ha", Double.NaN),
        maeTHa = obj.optDouble("mae_t_ha", Double.NaN),
        r2 = obj.optDouble("r2", Double.NaN)
    )

    private fun predictTreeEnsemble(trees: JSONArray, vector: DoubleArray): Double {
        if (trees.length() == 0) {
            return modelJson.getJSONObject("defaults").optDouble("mean_yield_t_ha", 4.0)
        }
        var sum = 0.0
        for (i in 0 until trees.length()) {
            sum += traverseTree(trees.getJSONObject(i).getJSONArray("nodes"), vector)
        }
        return (sum / trees.length()).coerceIn(1.0, 12.0)
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

    private fun predictSvm(vector: DoubleArray): Double {
        val svm = modelJson.getJSONObject("svm").getJSONObject("model")
        val mean = svm.getJSONArray("scaler_mean")
        val scale = svm.getJSONArray("scaler_scale")
        val coef = svm.getJSONArray("coef")
        var sum = svm.optDouble("intercept", 0.0)
        for (i in vector.indices) {
            val s = scale.optDouble(i, 1.0).takeIf { it != 0.0 } ?: 1.0
            val scaled = (vector[i] - mean.optDouble(i, 0.0)) / s
            sum += scaled * coef.optDouble(i, 0.0)
        }
        return sum.coerceIn(1.0, 12.0)
    }

    private fun annArchitectureLabel(): String {
        val layers = modelJson.getJSONObject("ann")
            .getJSONObject("model")
            .getJSONArray("hidden_layer_sizes")
        val sizes = (0 until layers.length()).joinToString("-") { layers.getInt(it).toString() }
        return "Algorithm: ANN ($sizes hidden)"
    }

    private fun svmInfoLabel(): String {
        val svm = modelJson.getJSONObject("svm").getJSONObject("model")
        return "Algorithm: SVM (${svm.optString("kernel", "linear")}, ${svm.optInt("support_vector_count", 0)} SVs)"
    }

    private fun round2(v: Double) = (v * 100).toInt() / 100.0
}
