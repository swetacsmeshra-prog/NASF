package com.example.nasf.weather

object MlUrlResolver {
    private const val BASE = "http://172.17.30.99:5000"

    fun manualYieldUrl(state: String, crop: String): String = when {
        state in listOf("UP", "Bihar") && crop == "चावल / Rice" -> "$BASE/rice_up_bihar"
        state in listOf("Punjab", "Haryana") && crop == "चावल / Rice" -> "$BASE/rice_pb_hr"
        state in listOf("UP", "Bihar") && crop == "गेहूँ / Wheat" -> "$BASE/wheat_up_bihar"
        state in listOf("Punjab", "Haryana") && crop == "गेहूँ / Wheat" -> "$BASE/wheat_pb_hr"
        else -> ""
    }

    fun automaticYieldUrl(state: String, crop: String): String = when {
        state in listOf("UP", "Bihar") && crop == "चावल / Rice" -> "$BASE/rice_up_bihar_automatic"
        state in listOf("Punjab", "Haryana") && crop == "चावल / Rice" -> "$BASE/rice_pb_hr_automatic"
        state in listOf("UP", "Bihar") && crop == "गेहूँ / Wheat" -> "$BASE/wheat_up_bihar_automatic"
        state in listOf("Punjab", "Haryana") && crop == "गेहूँ / Wheat" -> "$BASE/wheat_pb_hr_automatic"
        else -> ""
    }

    fun soilAdvisoryUrl(crop: String): String = when {
        "चावल / Rice" in crop -> "$BASE/process_data_rice"
        "गेहूँ / Wheat" in crop -> "$BASE/process_data_wheat"
        else -> ""
    }
}
