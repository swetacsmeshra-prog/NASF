# IMD Mausam scrape analysis

- Scraped at: `2026-08-05T15:55:39`
- Priority stations with data: **51**
- Daily forecast rows (7-day long format): **357**
- Stations with rain in past 24h: **40**
- Stations with rain/thunder signal in Day-1 forecast: **46**

## Observed extremes (today)
- Hottest max: **Jaisalmer** 37.3°C (dep 0.7)
- Coolest min: **Shillong** 17.4°C (dep None)
- Most variable 7-day forecast temps: **New Delhi-safdarjung** (max σ=1.278, min σ=1.294)

## Seasonal forecast (SW monsoon 2026)
- Quantitatively, the southwest monsoon seasonal rainfall over the country as a whole is likely to be 90% of the Long Period Average (LPA) with a model error of ±4%, indicating that below normal rainfall is most likely over the country as a whole during the monsoon season (June to September), 2026.

## Output CSVs
- `imd_stations_catalog.csv`
- `imd_daily_weather_forecast.csv`
- `imd_weather_forecast_station_summary.csv`
- `imd_standard_deviation_by_station.csv`
- `imd_standard_deviation_by_forecast_day.csv`
- `imd_climate_standard_deviation_reference.csv`
- `imd_seasonal_forecast.csv`
- `seasonal_forecast_assets.csv`

## Notes
- Official `api.imd.gov.in` returns **401 Unauthorized** without JWT/IP whitelist.
- City forecasts scraped from public `city.imd.gov.in` static API.
- Seasonal numbers come from IMD press-release PDFs (LRF / monthly outlooks).
- All-India monsoon rainfall climatology: LPA ≈ 89 cm, SD ≈ 9 cm (~10%).
