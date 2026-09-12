package com.example.nasf
import com.google.gson.annotations.SerializedName

data class LocationValues(
    @SerializedName("longitude") val Longitude: String,
    @SerializedName("latitude") val Latitude: String
)
data class ParameterValues(
    @SerializedName("FYM_t_ha") val FYM: String,
    @SerializedName("Residue_Incor_q_ha") val Residue_Incorporation: String,
    @SerializedName("Sowing_date") val Sowing_Date: String,
    @SerializedName("Urea_appl_kg_ha") val Urea: String,
    @SerializedName("DAP_appl_kg_ha") val DAP: String,
    @SerializedName("MOP_appl_kg_ha") val MOP: String,
    @SerializedName("Irr_no") val Irrigation_Number: String,
    @SerializedName("Zinc_Sul_kg_ha") val Zinc_Sulphate: String
)
data class PixelValues(
    @SerializedName("Soil_Organic_Carbon.tif") val Soil_Organic_Carbon: String,
    @SerializedName("PH.tif") val pH: String,
    @SerializedName("EC.tif") val EC: String,
    @SerializedName("Available_Nitrogen.tif") val Available_Nitrogen: String,
    @SerializedName("Available_Phosphorus.tif") val Available_Phosphorus: String,
    @SerializedName("Available_Potassium.tif") val Available_Potassium: String,
    @SerializedName("Sulphur.tif") val Sulphur: String,
    @SerializedName("Zinc.tif") val Zinc: String,
    @SerializedName("Iron.tif") val Iron: String,
    @SerializedName("Manganese.tif") val Manganese: String,
    @SerializedName("Copper.tif") val Copper: String
)

data class ApiResponse(
    val location_values: LocationValues,
    val parameter_values: ParameterValues,
    val pixel_values: PixelValues
)

