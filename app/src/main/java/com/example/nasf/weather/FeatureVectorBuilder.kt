package com.example.nasf.weather

import org.json.JSONObject

/**
 * Builds model feature vectors aligned with segregated CSV column names.
 * Random Forest and cubic inputs use crop-season weeks 23–44 (June–October) only.
 */
object FeatureVectorBuilder {

    data class ModelFeatures(
        val wideColumns: Map<String, Double>,
        val seasonRainfallMm: Double,
        val seasonTMinMean: Double,
        val seasonTMaxMean: Double,
        val seasonHumidityMean: Double,
        val seasonSolarMean: Double,
        val seasonWindMean: Double,
        val soilFeatures: Map<String, Double>,
        val cropSeasonWeekCount: Int
    )

    fun fromCalendarWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        range: ClimateWeekRange,
        sowingYmd: String,
        harvestYmd: String,
        farmPayloadJson: String = ""
    ): ModelFeatures {
        val soil = extractSoilFeatures(farmPayloadJson)
        val season = range.filterWeeks(weeks, sowingYmd, harvestYmd)
        return buildFromSeasonWeeks(season, weeks.associateBy { it.weekNumber }, soil)
    }

    fun climate3FeatureMap(features: ModelFeatures): Map<String, Double> = linkedMapOf(
        "season_rainfall_mm" to features.seasonRainfallMm,
        "season_t_min_mean" to features.seasonTMinMean,
        "season_t_max_mean" to features.seasonTMaxMean
    )

    fun fromWindPowerWeeks(
        weeks: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        farmPayloadJson: String
    ): ModelFeatures {
        val soil = extractSoilFeatures(farmPayloadJson)
        val season = WindPowerWeekMapper.filterWeeks(weeks)
        val wide = linkedMapOf<String, Double>()
        season.forEach { week ->
            val prefix = "week%02d".format(week.weekNumber)
            wide["${prefix}_rainfall_total"] = week.rainfallTotalMm
            wide["${prefix}_rainfall_mean"] = week.rainfallMeanMmDay
            wide["${prefix}_relative_humidity"] = week.humidityMeanPct
            wide["${prefix}_solar_radiation"] = week.solarMeanKwhM2Day
            wide["${prefix}_t_min"] = week.tMin
            wide["${prefix}_t_max"] = week.tMax
            wide["${prefix}_wind_speed"] = week.windSpeedMeanMs
        }
        return ModelFeatures(
            wideColumns = wide,
            seasonRainfallMm = season.mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }.sum(),
            seasonTMinMean = meanOf(season.map { it.tMin }),
            seasonTMaxMean = meanOf(season.map { it.tMax }),
            seasonHumidityMean = meanOf(season.map { it.humidityMeanPct }),
            seasonSolarMean = meanOf(season.map { it.solarMeanKwhM2Day }),
            seasonWindMean = meanOf(season.map { it.windSpeedMeanMs }),
            soilFeatures = soil,
            cropSeasonWeekCount = season.size
        )
    }

    private fun buildFromSeasonWeeks(
        season: List<CalendarWeeklyAnalyzer.CalendarWeekStats>,
        allWeeksByNumber: Map<Int, CalendarWeeklyAnalyzer.CalendarWeekStats>,
        soil: Map<String, Double>
    ): ModelFeatures {
        val wide = linkedMapOf<String, Double>()
        allWeeksByNumber.values.sortedBy { it.weekNumber }.forEach { week ->
            val prefix = "week%02d".format(week.weekNumber)
            wide["${prefix}_rainfall_total"] = week.rainfallTotalMm
            wide["${prefix}_rainfall_mean"] = week.rainfallMeanMmDay
            wide["${prefix}_relative_humidity"] = week.humidityMeanPct
            wide["${prefix}_solar_radiation"] = week.solarMeanKwhM2Day
            wide["${prefix}_t_min"] = week.tMin
            wide["${prefix}_t_max"] = week.tMax
            wide["${prefix}_wind_speed"] = week.windSpeedMeanMs
        }
        return ModelFeatures(
            wideColumns = wide,
            seasonRainfallMm = season.mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }.sum(),
            seasonTMinMean = meanOf(season.map { it.tMin }),
            seasonTMaxMean = meanOf(season.map { it.tMax }),
            seasonHumidityMean = meanOf(season.map { it.humidityMeanPct }),
            seasonSolarMean = meanOf(season.map { it.solarMeanKwhM2Day }),
            seasonWindMean = meanOf(season.map { it.windSpeedMeanMs }),
            soilFeatures = soil,
            cropSeasonWeekCount = season.size
        )
    }

    fun fromWideColumns(
        wideColumns: Map<String, Double>,
        soilFeatures: Map<String, Double>
    ): ModelFeatures {
        val cropWeeks = (SeasonWeekMapper.CROP_SEASON_START_WEEK..SeasonWeekMapper.CROP_SEASON_END_WEEK)
            .map { week ->
                val prefix = "week%02d".format(week)
                CalendarWeeklyAnalyzer.CalendarWeekStats(
                    weekNumber = week,
                    weekStartYmd = "",
                    weekEndYmd = "",
                    rainfallTotalMm = wideColumns["${prefix}_rainfall_total"] ?: Double.NaN,
                    rainfallMeanMmDay = wideColumns["${prefix}_rainfall_mean"] ?: Double.NaN,
                    humidityMeanPct = wideColumns["${prefix}_relative_humidity"] ?: Double.NaN,
                    solarMeanKwhM2Day = wideColumns["${prefix}_solar_radiation"] ?: Double.NaN,
                    tMin = wideColumns["${prefix}_t_min"] ?: Double.NaN,
                    tMax = wideColumns["${prefix}_t_max"] ?: Double.NaN,
                    windSpeedMeanMs = wideColumns["${prefix}_wind_speed"] ?: Double.NaN
                )
            }

        return ModelFeatures(
            wideColumns = wideColumns,
            seasonRainfallMm = cropWeeks.mapNotNull { it.rainfallTotalMm.takeIf { v -> !v.isNaN() } }.sum(),
            seasonTMinMean = meanOf(cropWeeks.map { it.tMin }),
            seasonTMaxMean = meanOf(cropWeeks.map { it.tMax }),
            seasonHumidityMean = meanOf(cropWeeks.map { it.humidityMeanPct }),
            seasonSolarMean = meanOf(cropWeeks.map { it.solarMeanKwhM2Day }),
            seasonWindMean = meanOf(cropWeeks.map { it.windSpeedMeanMs }),
            soilFeatures = soilFeatures,
            cropSeasonWeekCount = cropWeeks.size
        )
    }

    fun fromClimateInputs(
        climate: ManualClimateAnnPredictor.ClimateInputs,
        farmPayloadJson: String = ""
    ): ModelFeatures = ModelFeatures(
        wideColumns = emptyMap(),
        seasonRainfallMm = climate.seasonRainfallMm,
        seasonTMinMean = climate.seasonTMinMean,
        seasonTMaxMean = climate.seasonTMaxMean,
        seasonHumidityMean = climate.seasonHumidityMean,
        seasonSolarMean = climate.seasonSolarMean,
        seasonWindMean = climate.seasonWindMean,
        soilFeatures = extractSoilFeatures(farmPayloadJson),
        cropSeasonWeekCount = SeasonWeekMapper.cropSeasonWeekCount
    )

    fun fromYearSeason(
        yearStats: MonthlyCropSeasonAnalyzer.YearSeasonStats,
        farmPayloadJson: String
    ): ModelFeatures {
        val soil = extractSoilFeatures(farmPayloadJson)
        return ModelFeatures(
            wideColumns = emptyMap(),
            seasonRainfallMm = yearStats.seasonRainfallMm,
            seasonTMinMean = Double.NaN,
            seasonTMaxMean = yearStats.seasonTMaxMean,
            seasonHumidityMean = yearStats.seasonHumidityMean,
            seasonSolarMean = yearStats.seasonSolarMean,
            seasonWindMean = yearStats.seasonWindMean,
            soilFeatures = soil,
            cropSeasonWeekCount = yearStats.months.size
        )
    }

    fun fromCalendarAnalysis(
        calendarResult: CalendarWeeklyAnalyzer.CalendarAnalysisResult,
        farmPayloadJson: String
    ): ModelFeatures {
        val soil = extractSoilFeatures(farmPayloadJson)
        val cropWeeks = calendarResult.weeks.filter { SeasonWeekMapper.isInCropSeason(it.weekNumber) }

        return ModelFeatures(
            wideColumns = calendarResult.wideColumns,
            seasonRainfallMm = CalendarWeeklyAnalyzer.seasonRainfallTotal(calendarResult.weeks),
            seasonTMinMean = meanOf(cropWeeks.map { it.tMin }),
            seasonTMaxMean = meanOf(cropWeeks.map { it.tMax }),
            seasonHumidityMean = meanOf(cropWeeks.map { it.humidityMeanPct }),
            seasonSolarMean = meanOf(cropWeeks.map { it.solarMeanKwhM2Day }),
            seasonWindMean = meanOf(cropWeeks.map { it.windSpeedMeanMs }),
            soilFeatures = soil,
            cropSeasonWeekCount = cropWeeks.size
        )
    }

    /**
     * Rebuild features from analyzed JSON passed through the Calculate Yield path
     * (calendar 52-week or mon–fri weekly formats).
     */
    fun fromAnalyzedJson(
        analyzedJson: JSONObject,
        farmPayloadJson: String = ""
    ): ModelFeatures {
        val soil = extractSoilFeatures(farmPayloadJson)
        val wideJson = analyzedJson.optJSONObject("wide")
        if (wideJson != null && wideJson.length() > 0) {
            val wide = linkedMapOf<String, Double>()
            wideJson.keys().forEach { key ->
                val v = wideJson.optDouble(key, Double.NaN)
                if (!v.isNaN()) wide[key] = v
            }
            // Calendar format uses weekNN_rainfall_total; weekly analyzer uses longer names.
            val normalized = normalizeWideColumns(wide)
            if (normalized.isNotEmpty()) {
                return fromWideColumns(normalized, soil)
            }
        }

        val weeksArray = analyzedJson.optJSONArray("weeks")
        if (weeksArray != null && weeksArray.length() > 0) {
            val weeks = mutableListOf<CalendarWeeklyAnalyzer.CalendarWeekStats>()
            for (i in 0 until weeksArray.length()) {
                val w = weeksArray.getJSONObject(i)
                weeks.add(
                    CalendarWeeklyAnalyzer.CalendarWeekStats(
                        weekNumber = w.optInt("week_number", i + 1),
                        weekStartYmd = w.optString("week_start_ymd", ""),
                        weekEndYmd = w.optString("week_end_ymd", ""),
                        rainfallTotalMm = firstDouble(w, "rainfall_total", "rainfall_total_week_mm"),
                        rainfallMeanMmDay = firstDouble(w, "rainfall_mean", "rainfall_weekly_mean_mm_day"),
                        humidityMeanPct = firstDouble(w, "relative_humidity", "relative_humidity_weekly_mean_pct"),
                        solarMeanKwhM2Day = firstDouble(w, "solar_radiation", "solar_radiation_weekly_mean_kwh_m2_day"),
                        tMin = w.optDouble("t_min", Double.NaN),
                        tMax = w.optDouble("t_max", Double.NaN),
                        windSpeedMeanMs = w.optDouble("wind_speed", Double.NaN)
                    )
                )
            }
            val byNumber = weeks.associateBy { it.weekNumber }
            val season = weeks.filter { SeasonWeekMapper.isInCropSeason(it.weekNumber) }
                .ifEmpty { weeks }
            return buildFromSeasonWeeks(season, byNumber, soil)
        }

        // Last resort: summary block from weekly analyzer
        val summary = analyzedJson.optJSONObject("summary")
        val rain = summary?.optJSONObject("rainfall_total_week_mm")?.optDouble("mean", Double.NaN)
            ?: Double.NaN
        return ModelFeatures(
            wideColumns = emptyMap(),
            seasonRainfallMm = if (rain.isNaN()) 0.0 else rain * 22.0,
            seasonTMinMean = Double.NaN,
            seasonTMaxMean = Double.NaN,
            seasonHumidityMean = Double.NaN,
            seasonSolarMean = Double.NaN,
            seasonWindMean = Double.NaN,
            soilFeatures = soil,
            cropSeasonWeekCount = 0
        )
    }

    private fun normalizeWideColumns(wide: Map<String, Double>): Map<String, Double> {
        if (wide.keys.any { it.matches(Regex("week\\d+_rainfall_total$")) }) {
            return wide
        }
        val out = linkedMapOf<String, Double>()
        wide.forEach { (k, v) ->
            when {
                k.contains("rainfall_total") -> {
                    val week = Regex("week_?(\\d+)").find(k)?.groupValues?.get(1)
                        ?: Regex("(\\d+)").find(k)?.groupValues?.get(1)
                    if (week != null) {
                        out["week%02d_rainfall_total".format(week.toInt())] = v
                    }
                }
                k.contains("rainfall") && k.contains("mean") -> {
                    val week = Regex("week_?(\\d+)").find(k)?.groupValues?.get(1)
                    if (week != null) out["week%02d_rainfall_mean".format(week.toInt())] = v
                }
                k.contains("humidity") -> {
                    val week = Regex("week_?(\\d+)").find(k)?.groupValues?.get(1)
                    if (week != null) out["week%02d_relative_humidity".format(week.toInt())] = v
                }
                k.contains("solar") -> {
                    val week = Regex("week_?(\\d+)").find(k)?.groupValues?.get(1)
                    if (week != null) out["week%02d_solar_radiation".format(week.toInt())] = v
                }
                else -> out[k] = v
            }
        }
        return out
    }

    private fun firstDouble(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (json.has(key)) {
                val v = json.optDouble(key, Double.NaN)
                if (!v.isNaN()) return v
            }
        }
        return Double.NaN
    }

    fun reducedFeatureMap(features: ModelFeatures): Map<String, Double> {
        val map = linkedMapOf<String, Double>()
        map["season_rainfall_mm"] = features.seasonRainfallMm
        map["season_t_max_mean"] = features.seasonTMaxMean
        map["season_humidity_mean"] = features.seasonHumidityMean
        map["season_solar_mean"] = features.seasonSolarMean
        map["season_wind_mean"] = features.seasonWindMean
        features.soilFeatures.forEach { (k, v) -> map[k] = v }
        return map
    }

    private fun extractSoilFeatures(payloadJson: String): Map<String, Double> {
        val out = linkedMapOf(
            "ph" to 7.2,
            "oc_pct" to 0.45,
            "n_kg_ha" to 250.0,
            "p_kg_ha" to 18.0,
            "k_kg_ha" to 180.0,
            "ec_ds_m" to 0.4
        )
        if (payloadJson.isBlank()) return out
        return try {
            val json = JSONObject(payloadJson)
            val aliases = listOf(
                listOf("pH (1:2)", "pH", "ph") to "ph",
                listOf("OC %", "OC", "oc_pct") to "oc_pct",
                listOf("Major-nutrient Avail N. (Kg/ha)", "Avail_N_Kg_ha", "n_kg_ha") to "n_kg_ha",
                listOf("Major-nutrient P(kg/ha)", "Avail_P_kg_ha", "Appl_P2O5_kg_ha", "p_kg_ha") to "p_kg_ha",
                listOf("Major-nutrient Avail. K (kg/ha)", "Avail_K_kg_ha", "Appl_K2O_kg_ha", "k_kg_ha") to "k_kg_ha",
                listOf("EC (dS/m)", "EC_dS_m", "ec_ds_m") to "ec_ds_m"
            )
            aliases.forEach { (keys, featureKey) ->
                for (key in keys) {
                    if (json.has(key)) {
                        val v = json.optDouble(key, Double.NaN)
                        if (!v.isNaN()) {
                            out[featureKey] = v
                            break
                        }
                    }
                }
            }
            out
        } catch (_: Exception) {
            out
        }
    }

    private fun meanOf(values: List<Double>): Double {
        val valid = values.filter { !it.isNaN() }
        if (valid.isEmpty()) return Double.NaN
        return (valid.sum() / valid.size * 100).toInt() / 100.0
    }
}
