package com.example.nasf.weather

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.example.nasf.R
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

object ClimatologyAnomalyFormatter {

    fun bindLatLonHint(
        activity: Activity,
        latField: TextInputEditText,
        lonField: TextInputEditText,
        hintView: TextView
    ) {
        val updater = {
            val lat = latField.text?.toString()?.trim()?.toDoubleOrNull()
            val lon = lonField.text?.toString()?.trim()?.toDoubleOrNull()
            if (lat == null || lon == null) {
                hintView.visibility = View.GONE
            } else {
                val lookup = runCatching {
                    ClimatologyStdvRepository.nearest(activity, lat, lon)
                }.getOrNull()
                if (lookup == null) {
                    hintView.visibility = View.GONE
                } else {
                    val farm = lookup.farm
                    hintView.visibility = View.VISIBLE
                    hintView.text = activity.getString(
                        R.string.climatology_hint,
                        fmt(farm.rainSeasonTotalMm.mean, 0),
                        fmt(farm.rainSeasonTotalMm.sd, 0),
                        fmt(farm.tminSeason.mean, 1),
                        fmt(farm.tminSeason.sd, 1),
                        fmt(lookup.distanceKm, 1),
                        activity.getString(R.string.weather_source_nasa)
                    )
                }
            }
        }
        latField.addTextChangedListener(simpleWatcher { updater() })
        lonField.addTextChangedListener(simpleWatcher { updater() })
        updater()
    }

    fun helperForField(stat: ClimatologyStdvRepository.Stat, unit: String): String {
        val suffix = if (unit.isBlank()) "" else " $unit"
        return "30-yr ${fmt(stat.mean, 1)}$suffix ± ${fmt(stat.sd, 1)}"
    }

    fun bandLabel(band: ClimatologyStdvRepository.Band): String = when (band) {
        ClimatologyStdvRepository.Band.TYPICAL -> "typical"
        ClimatologyStdvRepository.Band.UNUSUAL -> "unusual"
        ClimatologyStdvRepository.Band.OUTLIER -> "an outlier"
    }

    fun formatZ(z: Double): String = String.format(Locale.US, "%+.2f", z)

    fun compactStrip(scores: List<ClimatologyStdvRepository.ZScore>): String =
        scores.joinToString(" · ") { "${it.label} ${formatZ(it.z)}" }

    fun appendSeasonAnomaly(
        activity: Activity,
        base: String,
        lookup: ClimatologyStdvRepository.Lookup?,
        climate: ManualClimateAnnPredictor.ClimateInputs?
    ): String {
        if (lookup == null || climate == null) return base
        val scores = ClimatologyStdvRepository.seasonZScores(lookup, climate)
        val header = activity.getString(
            R.string.climatology_season_header,
            String.format(Locale.US, "%.1f", lookup.distanceKm)
        )
        return "$base\n\n$header\n${compactStrip(scores)}"
    }

    private fun fmt(value: Double, decimals: Int): String {
        if (value.isNaN()) return "—"
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    private fun simpleWatcher(onChange: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange()
    }
}
