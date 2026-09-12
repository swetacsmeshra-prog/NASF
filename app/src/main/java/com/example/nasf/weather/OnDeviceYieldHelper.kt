package com.example.nasf.weather

import android.content.Context
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-device yield for Automatic / weather → Calculate flows when the Flask ML
 * server is unavailable. Uses bundled Random Forest ([YieldModelPredictor]).
 */
object OnDeviceYieldHelper {

    /** Prefer phone model over dead LAN API (avoids long timeouts). */
    const val PREFER_ON_DEVICE = true

    /**
     * Same dialog style as the old API: `"1700 +- 26 Kg/ha"`.
     * Yield is RF prediction (t/ha → Kg/ha); ± uses model MAE.
     */
    fun predictFormatted(
        context: Context,
        analyzedJson: JSONObject,
        farmPayloadJson: String = ""
    ): String {
        val features = FeatureVectorBuilder.fromAnalyzedJson(analyzedJson, farmPayloadJson)
        return formatPrediction(context, features)
    }

    fun predictFormattedFromClimate(
        context: Context,
        climate: ManualClimateAnnPredictor.ClimateInputs,
        farmPayloadJson: String = ""
    ): String {
        val features = FeatureVectorBuilder.fromClimateInputs(climate, farmPayloadJson)
        return formatPrediction(context, features)
    }

    private fun formatPrediction(
        context: Context,
        features: FeatureVectorBuilder.ModelFeatures
    ): String {
        val predictor = YieldModelPredictor(context)
        val prediction = predictor.predict(features)
        val maeTHa = predictor.maeTHa()
        val yieldKg = (prediction.randomForestYieldTHa * 1000.0).roundToInt().coerceAtLeast(0)
        val errorKg = max(1, (maeTHa * 1000.0).roundToInt())
        return "$yieldKg +- $errorKg"
    }
}
