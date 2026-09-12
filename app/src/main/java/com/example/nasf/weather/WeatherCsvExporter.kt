package com.example.nasf.weather

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.IOException

object WeatherCsvExporter {

    fun toCsv(weatherJson: JSONObject): String {
        val params = weatherJson.optJSONObject("parameter")
            ?: throw IllegalStateException("Weather data has no parameter series.")

        val paramNames = mutableListOf<String>()
        val keys = params.keys()
        while (keys.hasNext()) {
            paramNames.add(keys.next())
        }
        paramNames.sort()

        val allDates = linkedSetOf<String>()
        paramNames.forEach { name ->
            params.optJSONObject(name)?.keys()?.forEach { date -> allDates.add(date) }
        }

        val sb = StringBuilder()
        sb.append("Date")
        paramNames.forEach { sb.append(',').append(it) }
        sb.append('\n')

        allDates.sorted().forEach { date ->
            sb.append(date)
            paramNames.forEach { name ->
                sb.append(',')
                val value = params.optJSONObject(name)?.optDouble(date, Double.NaN) ?: Double.NaN
                if (!value.isNaN() && value != -999.0) {
                    sb.append(value)
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun buildFileName(request: NasaPowerRequest): String {
        val lat = request.latitude.toString().replace('.', '_')
        val lon = request.longitude.toString().replace('.', '_')
        return "nasa_power_${request.start}_${request.end}_${lat}_${lon}.csv"
    }

    /**
     * Saves CSV to Downloads/NASF on API 29+, otherwise to app-accessible Documents/NASF.
     * Returns a user-facing location label.
     */
    fun save(context: Context, csv: String, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NASF")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create CSV file in Downloads.")
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not write CSV file.")
            return "Downloads/NASF/$fileName"
        }

        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
            "NASF"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create export folder.")
        }
        val file = File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)
        return file.absolutePath
    }
}
