# NASA POWER Weather Integration

## WeatherActivity — explicit query form

Users enter (or edit) all NASA POWER daily point API parameters on [`WeatherActivity`](../app/src/main/java/com/example/nasf/WeatherActivity.kt), matching the official curl contract:

```
GET https://power.larc.nasa.gov/api/temporal/daily/point
  ?start=YYYYMMDD&end=YYYYMMDD
  &latitude={lat}&longitude={lon}
  &community={sb|ag|re}
  &parameters={comma-separated}
  &format=json&units=metric
  &user={optional}&header=true
  &time-standard=utc
  &site-elevation={m}&wind-elevation={m}
  &wind-surface={SeaIce|...}
```

Reference: [NASA POWER Daily Point API](https://power.larc.nasa.gov/api/pages/#/Data%20Requests/daily_single_point_data_request_api_temporal_daily_point_get)

## Flow (Automatic yield)

1. `AutomaticDetails` → Calculate → `WeatherActivity` (form pre-filled with lat, long, start, end)
2. User edits NASA fields → **Fetch NASA Data**
3. Preview shows header, location, per-parameter min/max/mean, messages
4. **Continue to Calculate** → ML API receives farm JSON + `weather` object

## Pre-fill defaults (editable)

| Field | Default when from Automatic |
|-------|----------------------------|
| start / end | Sowing / harvest `yyyyMMdd` from Intent |
| latitude / longitude | From Intent |
| community | SB |
| parameters | T2M |
| format | JSON |
| units | metric |
| user | usr123 |
| header | true |
| time-standard | UTC |
| site-elevation | 3 |
| wind-elevation | 10 |
| wind-surface | SeaIce |

## ML payload `weather` object

```json
{
  "source": "NASA_POWER",
  "request": { "start": "20200101", "community": "sb", "parameters": "T2M", ... },
  "header": { "start": "20200101", "end": "20201231", "time_standard": "UTC", ... },
  "geometry": { "type": "Point", "coordinates": [180, 90, 0] },
  "parameter_metadata": { "T2M": { "units": "C", "longname": "..." } },
  "parameter": { "T2M": { "20200101": -28.79, ... } },
  "summary": { "T2M": { "mean": -12.4, "min": -37.03, "max": 0.68, "count": 366, "units": "C" } },
  "messages": ["..."]
}
```

## Key source files

| File | Role |
|------|------|
| `weather/NasaPowerRequest.kt` | Request model + validation |
| `weather/NasaPowerClient.kt` | URL build + response parse |
| `weather/WeatherConfig.kt` | Dropdown options + defaults |
| `WeatherActivity.kt` | Form UI, fetch, ML continue |
| `weather/MlPredictionClient.kt` | Merge `weather` into ML POST |

## Architecture

- **MVP:** Android calls NASA POWER directly (HTTPS).
- **Production:** Optional backend proxy at `172.17.30.99` for caching and rate limits.
