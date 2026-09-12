package com.example.nasf.weather

import org.json.JSONObject

/**
 * Builds ML JSON payloads for the weather add-on without modifying existing activities.
 */
object WeatherPayloadBuilder {

    fun automaticPayload(
        longitude: Double,
        latitude: Double,
        sowingDoy: Double,
        harvestDoy: Double,
        grainYield: Double
    ): String = JSONObject()
        .put("Long", longitude)
        .put("Lat", latitude)
        .put("Residue_Incor_t_ha", 0.0)
        .put("Appl_N_kg_ha", 0.0)
        .put("Appl_P2O5_kg_ha", 0.0)
        .put("Appl_K2O_kg_ha", 0.0)
        .put("Zinc_Sul_kg_ha", 0.0)
        .put("FYM_t_ha", 0.0)
        .put("FYM_input_interval_Yr", 0.0)
        .put("Sowing_DOY", sowingDoy)
        .put("Irr_no", 0.0)
        .put("Harvest_DOY", harvestDoy)
        .put("Grain_Yield_t_ha", grainYield)
        .toString()

    fun manualPayload(
        longitude: Double,
        latitude: Double,
        sowingDoy: Double,
        harvestDoy: Double,
        grainYield: Double
    ): String = JSONObject()
        .put("Long", longitude)
        .put("Lat", latitude)
        .put("pH", 7.0)
        .put("EC_dS_m", 0.0)
        .put("OC", 0.0)
        .put("Avail_N_Kg_ha", 0.0)
        .put("Avail_P_kg_ha", 0.0)
        .put("Avail_K_kg_ha", 0.0)
        .put("Soil_S_kg_ha", 0.0)
        .put("Soil_Zn_ppm", 0.0)
        .put("Soil_Fe_ppm", 0.0)
        .put("Soil_Mn_ppm", 0.0)
        .put("Soil_Cu_ppm", 0.0)
        .put("Residue_Incor_Q_ha", 0.0)
        .put("Appl_N_kg_ha", 0.0)
        .put("Appl_P2O5_kg_ha", 0.0)
        .put("Appl_K2O_kg_ha", 0.0)
        .put("Zinc_Sul_kg_ha", 0.0)
        .put("FYM_t_ha", 0.0)
        .put("FYM_input_interval_Yr", 0.0)
        .put("Sowing_DOY", sowingDoy)
        .put("Irr_no", 0.0)
        .put("Harvest_DOY", harvestDoy)
        .put("Grain_Yield_Q_ha", grainYield)
        .toString()

    fun soilPayload(longitude: Double, latitude: Double, grainYield: Double): String =
        JSONObject()
            .put("Long", longitude)
            .put("Lat", latitude)
            .put("Grain_Yield_t_ha", grainYield)
            .toString()
}
