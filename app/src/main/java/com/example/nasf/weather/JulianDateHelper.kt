package com.example.nasf.weather

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nasf.R
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

object JulianDateHelper {

    fun toDayOfYear(month: Int, day: Int): Double {
        val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var totalDays = 0
        for (i in 0 until month - 1) totalDays += daysInMonth[i]
        totalDays += day
        return totalDays.toDouble()
    }

    fun formatYmd(year: Int, month: Int, day: Int): String =
        String.format("%04d%02d%02d", year, month, day)

    fun attachDatePicker(activity: AppCompatActivity, editText: TextInputEditText) {
        editText.isFocusable = false
        editText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                activity,
                { _, selectedYear, monthOfYear, dayOfMonth ->
                    val month = monthOfYear + 1
                    val julian = toDayOfYear(month, dayOfMonth)
                    val ymd = formatYmd(selectedYear, month, dayOfMonth)
                    editText.setText(String.format("%.2f", julian))
                    editText.setTag(R.id.date_ymd_tag, ymd)
                    Toast.makeText(activity, "Date saved ($ymd)", Toast.LENGTH_SHORT).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = Calendar.getInstance().apply {
                    set(1981, Calendar.JANUARY, 1)
                }.timeInMillis
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
    }

    fun getDateYmd(editText: TextInputEditText): String? {
        val tag = editText.getTag(R.id.date_ymd_tag)
        if (tag is String && tag.length == 8) return tag
        return inferYmdFromJulianDisplay(editText.text?.toString())
    }

    private fun inferYmdFromJulianDisplay(display: String?): String? {
        val doy = display?.toDoubleOrNull() ?: return null
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, cal.get(Calendar.YEAR))
        cal.set(Calendar.DAY_OF_YEAR, doy.toInt().coerceIn(1, 366))
        return formatYmd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun isValidSeason(sowingYmd: String?, harvestYmd: String?): Boolean {
        if (sowingYmd == null || harvestYmd == null) return false
        if (sowingYmd.length != 8 || harvestYmd.length != 8) return false
        return harvestYmd >= sowingYmd
    }
}
