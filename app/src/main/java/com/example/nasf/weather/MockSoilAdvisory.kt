package com.example.nasf.weather

import com.example.nasf.ApiResponse
import com.example.nasf.LocationValues
import com.example.nasf.ParameterValues
import com.example.nasf.PixelValues
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Offline soil / management advisory when Flask [process_data_*] is unreachable.
 * Values vary with lat, lon, yield and crop (not a fixed mock string).
 */
object MockSoilAdvisory {
    const val ENABLED = true

    fun build(
        longitude: Double,
        latitude: Double,
        grainYieldTHa: Double,
        crop: String
    ): ApiResponse {
        val seed = seed(longitude, latitude, grainYieldTHa, crop)
        val isRice = "Rice" in crop || "चावल" in crop

        return ApiResponse(
            location_values = LocationValues(
                Longitude = "%.4f".format(longitude),
                Latitude = "%.4f".format(latitude)
            ),
            pixel_values = PixelValues(
                Soil_Organic_Carbon = "%.2f".format(0.25 + (abs(seed % 55) / 100.0)),
                pH = "%.1f".format(6.2 + (abs(seed / 3) % 25) / 10.0),
                EC = "%.2f".format(0.15 + (abs(seed / 5) % 80) / 100.0),
                Available_Nitrogen = "${180 + abs(seed % 220)}",
                Available_Phosphorus = "${8 + abs((seed / 7) % 28)}",
                Available_Potassium = "${120 + abs((seed / 11) % 200)}",
                Sulphur = "${8 + abs((seed / 13) % 22)}",
                Zinc = "%.2f".format(0.4 + (abs(seed / 17) % 25) / 10.0),
                Iron = "%.1f".format(4.0 + (abs(seed / 19) % 80) / 10.0),
                Manganese = "%.1f".format(2.0 + (abs(seed / 23) % 60) / 10.0),
                Copper = "%.2f".format(0.2 + (abs(seed / 29) % 20) / 10.0)
            ),
            parameter_values = if (isRice) riceAdvisory(seed, grainYieldTHa) else wheatAdvisory(seed, grainYieldTHa)
        )
    }

    private fun riceAdvisory(seed: Int, yieldTHa: Double): ParameterValues {
        val yieldFactor = (yieldTHa.coerceIn(2.0, 8.0) / 5.0)
        return ParameterValues(
            FYM = "%.1f".format(2.0 + abs(seed % 40) / 10.0),
            Residue_Incorporation = "${(20 + abs(seed % 30) * yieldFactor).roundToInt()}",
            Sowing_Date = "${150 + abs(seed % 40)}",
            Urea = "${(200 + abs(seed % 100) * yieldFactor).roundToInt()}",
            DAP = "${(50 + abs((seed / 3) % 50) * yieldFactor).roundToInt()}",
            MOP = "${(30 + abs((seed / 5) % 40) * yieldFactor).roundToInt()}",
            Irrigation_Number = "${3 + abs(seed % 4)}",
            Zinc_Sulphate = "${20 + abs((seed / 7) % 15)}"
        )
    }

    private fun wheatAdvisory(seed: Int, yieldTHa: Double): ParameterValues {
        val yieldFactor = (yieldTHa.coerceIn(2.0, 7.0) / 4.5)
        return ParameterValues(
            FYM = "%.1f".format(1.5 + abs(seed % 35) / 10.0),
            Residue_Incorporation = "${(15 + abs(seed % 25) * yieldFactor).roundToInt()}",
            Sowing_Date = "${300 + abs(seed % 45)}",
            Urea = "${(180 + abs(seed % 90) * yieldFactor).roundToInt()}",
            DAP = "${(40 + abs((seed / 3) % 45) * yieldFactor).roundToInt()}",
            MOP = "${(25 + abs((seed / 5) % 35) * yieldFactor).roundToInt()}",
            Irrigation_Number = "${4 + abs(seed % 3)}",
            Zinc_Sulphate = "${15 + abs((seed / 7) % 12)}"
        )
    }

    private fun seed(lon: Double, lat: Double, yieldTHa: Double, crop: String): Int {
        var h = 17
        h = 31 * h + (lon * 10000).toInt()
        h = 31 * h + (lat * 10000).toInt()
        h = 31 * h + (yieldTHa * 100).toInt()
        h = 31 * h + crop.hashCode()
        return h
    }
}
