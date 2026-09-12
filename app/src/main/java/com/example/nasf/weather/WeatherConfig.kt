package com.example.nasf.weather

object WeatherConfig {
    const val BASE_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
    const val MONTHLY_BASE_URL = "https://power.larc.nasa.gov/api/temporal/monthly/point"

    const val MONTHLY_MIN_YEAR = 1981
    const val MAX_MONTHLY_YEAR_SPAN = 30

    const val DEFAULT_MONTHLY_COMMUNITY = "AG"
    const val DEFAULT_MONTHLY_PARAMETERS = "T2M,PRECTOTCORR"
    const val FULL_YEAR_MONTHLY_PARAMETERS =
        "PRECTOTCORR,T2M,T2M_MIN,T2M_MAX,RH2M,ALLSKY_SFC_SW_DWN,WS10M"
    const val DEFAULT_ANALYZED_DAILY_PARAMETERS = "PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN"
    const val FULL_YEAR_ANALYZED_PARAMETERS =
        "PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN,T2M_MIN,T2M_MAX,WS10M"

    val COMMUNITIES = listOf("SB", "AG", "RE")
    val FORMATS = listOf("JSON", "CSV", "ASCII")
    val UNITS = listOf("metric", "imperial")
    val TIME_STANDARDS = listOf("UTC", "LST")
    val HEADER_OPTIONS = listOf("true", "false")
    val WIND_SURFACES = listOf(
        "SeaIce",
        "Water",
        "Airport",
        "AirportIce",
        "Grass",
        "Forest"
    )

    const val DEFAULT_COMMUNITY = "SB"
    const val DEFAULT_PARAMETERS = "T2M"
    const val DEFAULT_FORMAT = "JSON"
    const val DEFAULT_UNITS = "metric"
    const val DEFAULT_USER = "usr123"
    const val DEFAULT_HEADER = "true"
    const val DEFAULT_TIME_STANDARD = "UTC"
    const val DEFAULT_SITE_ELEVATION = "3"
    const val DEFAULT_WIND_ELEVATION = "10"
    const val DEFAULT_WIND_SURFACE = "SeaIce"

    const val MAX_DAILY_PARAMETERS = 20
}
