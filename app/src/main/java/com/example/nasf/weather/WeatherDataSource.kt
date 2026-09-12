package com.example.nasf.weather

import android.widget.TextView
import com.example.nasf.R
import com.google.android.material.button.MaterialButtonToggleGroup

enum class WeatherDataSource {
    NASA_POWER,
    IMD
}

object WeatherSourceToggle {

    fun selected(group: MaterialButtonToggleGroup): WeatherDataSource =
        if (group.checkedButtonId == R.id.weatherSourceImd) {
            WeatherDataSource.IMD
        } else {
            WeatherDataSource.NASA_POWER
        }

    fun bind(group: MaterialButtonToggleGroup, hint: TextView) {
        if (group.checkedButtonId == -1) {
            group.check(R.id.weatherSourceNasa)
        }
        updateHint(group, hint)
        group.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) updateHint(group, hint)
        }
    }

    private fun updateHint(group: MaterialButtonToggleGroup, hint: TextView) {
        hint.setText(
            when (selected(group)) {
                WeatherDataSource.IMD -> R.string.weather_source_hint_imd
                WeatherDataSource.NASA_POWER -> R.string.weather_source_hint_nasa
            }
        )
    }
}
