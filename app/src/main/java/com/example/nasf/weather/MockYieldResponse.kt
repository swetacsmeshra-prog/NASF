package com.example.nasf.weather

import org.json.JSONObject
import kotlin.math.abs

/**
 * Fallback when the ML server at 172.17.30.99:5000 is unreachable.
 * Set [ENABLED] to false once the live API is back.
 *
 * Yield varies with request payload (lat/long/dates/nutrients) so each
 * different input set produces a different "XXXX +- YY Kg/ha" string.
 */
object MockYieldResponse {
    const val ENABLED = true

    fun resultFor(payloadJson: String = ""): String {
        val seed = seedFromPayload(payloadJson)
        // Typical grain yield range (Kg/ha) matching live API style
        val yieldKg = 1200 + abs(seed % 3301) // 1200 … 4500
        val errorKg = 15 + abs((seed / 7) % 36) // 15 … 50
        return "$yieldKg +- $errorKg"
    }

    fun rawJson(payloadJson: String = ""): String =
        """{"result":"${resultFor(payloadJson)}"}"""

    private fun seedFromPayload(payloadJson: String): Int {
        if (payloadJson.isBlank()) return System.currentTimeMillis().toInt()
        return try {
            val json = JSONObject(payloadJson)
            var h = 17
            val keys = listOf(
                "Long", "Lat", "Sowing_DOY", "Harvest_DOY", "Irr_no",
                "Appl_N_kg_ha", "Appl_P2O5_kg_ha", "Appl_K2O_kg_ha",
                "Grain_Yield_Q_ha", "Grain_Yield_t_ha",
                "Residue_Incor_Q_ha", "Residue_Incor_t_ha",
                "pH", "OC", "Avail_N_Kg_ha"
            )
            for (key in keys) {
                if (json.has(key)) {
                    val v = json.optDouble(key, 0.0)
                    h = 31 * h + (v * 1000).toInt()
                }
            }
            // Also fold full string so any extra fields still change the result
            h = 31 * h + payloadJson.hashCode()
            h
        } catch (_: Exception) {
            payloadJson.hashCode()
        }
    }
}
