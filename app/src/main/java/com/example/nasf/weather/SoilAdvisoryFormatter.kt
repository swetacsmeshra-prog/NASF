package com.example.nasf.weather

import com.example.nasf.ApiResponse
import com.example.nasf.ParameterValues
import com.example.nasf.PixelValues
import com.google.gson.Gson
import kotlin.reflect.full.memberProperties

object SoilAdvisoryFormatter {

    fun formatFromResponseBody(responseBody: String): String =
        format(Gson().fromJson(responseBody, ApiResponse::class.java))

    fun format(apiResponse: ApiResponse): String {
        val sb = StringBuilder()
        sb.append("<big><b>Soil Properties</b></big><br><br>")
        var counter = 1
        apiResponse.pixel_values.let { pixelValues ->
            PixelValues::class.memberProperties.forEach { property ->
                val name = property.name.replace("_", " ").replace("tif", "").trim()
                sb.append("\t${counter++}. $name: <span style=\"color:red;\">${property.get(pixelValues)}</span><br>")
            }
        }
        sb.append("<br><big><b>Crop Management Advisory</b></big><br><br>")
        counter = 1
        apiResponse.parameter_values.let { parameterValues ->
            ParameterValues::class.memberProperties.forEach { property ->
                val name = property.name.replace("_", " ")
                sb.append("\t${counter++}. $name: <span style=\"color:red;\">${property.get(parameterValues)}</span><br>")
            }
        }
        return sb.toString()
    }
}
