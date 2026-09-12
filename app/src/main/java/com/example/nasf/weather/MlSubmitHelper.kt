package com.example.nasf.weather

import android.app.AlertDialog
import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MlSubmitHelper {

    suspend fun submitAndFormat(
        context: Context,
        mlClient: MlPredictionClient,
        mlUrl: String,
        mlPayloadJson: String,
        weatherJson: JSONObject,
        analyzedJson: JSONObject,
        useHtmlResult: Boolean
    ): String = withContext(Dispatchers.IO) {
        // Soil advisory still needs the live Flask HTML API.
        if (useHtmlResult) {
            val enriched = mlClient.mergeAnalyzedIntoPayload(mlPayloadJson, weatherJson, analyzedJson)
            val raw = mlClient.postRaw(mlUrl, enriched)
            return@withContext SoilAdvisoryFormatter.formatFromResponseBody(raw)
        }

        // Phase 1: Automatic / weather Calculate → on-device RF (real numbers, no LAN server).
        if (OnDeviceYieldHelper.PREFER_ON_DEVICE) {
            return@withContext OnDeviceYieldHelper.predictFormatted(
                context, analyzedJson, mlPayloadJson
            )
        }

        val enriched = mlClient.mergeAnalyzedIntoPayload(mlPayloadJson, weatherJson, analyzedJson)
        try {
            val raw = mlClient.postRaw(mlUrl, enriched)
            JSONObject(raw).optString("result", raw)
        } catch (e: Exception) {
            Log.w("MlSubmitHelper", "Live ML API failed, using on-device model: ${e.message}")
            try {
                OnDeviceYieldHelper.predictFormatted(context, analyzedJson, mlPayloadJson)
            } catch (inner: Exception) {
                if (MockYieldResponse.ENABLED) {
                    MockYieldResponse.resultFor(mlPayloadJson)
                } else {
                    throw e
                }
            }
        }
    }

    fun showResultDialog(
        activity: AppCompatActivity,
        title: String,
        message: String,
        html: Boolean
    ) {
        if (html) {
            val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .create()
            dialog.setOnShowListener {
                (dialog.findViewById(android.R.id.message) as TextView?)
                    ?.movementMethod = LinkMovementMethod.getInstance()
            }
            dialog.show()
        } else {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
        }
    }
}
