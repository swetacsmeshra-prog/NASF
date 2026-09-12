# Rice NASA POWER Segregated 52-Week Dataset (with Wind Speed)

## Overview

Wide-format dataset with **one row per farm** and weather columns **segregated by metric type** across 52 calendar weeks, including **wind speed**.

| Property | Value |
|----------|-------|
| **Excel file** | `exports/rice_nasapower_weekly_segregated.xlsx` |
| **CSV file** | `exports/rice_nasapower_weekly_segregated.csv` |
| **Farms** | 1,056 |
| **Columns** | 392 (28 farm + 364 weather) |
| **Weeks** | 52 per metric |

---

## Column Layout

```
Farm fields (28 columns)
├── week01_rainfall_total … week52_rainfall_total           (52 cols)
├── week01_rainfall_mean … week52_rainfall_mean               (52 cols)
├── week01_relative_humidity … week52_relative_humidity      (52 cols)
├── week01_solar_radiation … week52_solar_radiation        (52 cols)
├── week01_t_min … week52_t_min                              (52 cols)
├── week01_t_max … week52_t_max                              (52 cols)
└── week01_wind_speed … week52_wind_speed                    (52 cols)
```

---

## Weather Metrics

| Column suffix | Unit | NASA parameter | Aggregation |
|---------------|------|----------------|-------------|
| `rainfall_total` | mm | PRECTOTCORR | Sum per week |
| `rainfall_mean` | mm/day | PRECTOTCORR | Mean per week |
| `relative_humidity` | % | RH2M | Mean per week |
| `solar_radiation` | kWh/m²/day | ALLSKY_SFC_SW_DWN | Mean per week |
| `t_min` | °C | T2M_MIN | Min per week |
| `t_max` | °C | T2M_MAX | Max per week |
| **`wind_speed`** | **m/s** | **WS10M** | **Mean per week (10 m height)** |

### Wind speed note

`nasapower 30 yrs.xlsx` does **not** contain wind data. Wind speed is fetched from **NASA POWER** (`WS10M`) per farm lat/lon, aggregated using week dates from the Excel calendar (`week_start` / `week_end`, `ind_week` 1–52).

---

## Source Pipeline

```bash
source .venv/bin/activate
python3 merge_rice_nasapower_long.py    # long-format 52-week merged CSV
python3 add_wind_speed.py               # adds wind_speed_weekly_mean_ms
python3 segregate_weekly_columns.py     # wide segregated Excel/CSV
```

See **[SUPPORTING_SCRIPTS.md](SUPPORTING_SCRIPTS.md)** for full script documentation, arguments, and file paths.

---

## Supporting Scripts

| Script | Purpose | Output |
|--------|---------|--------|
| `merge_rice_nasapower_long.py` | Build 52-week long merged CSV | `rice_nasapower_weekly_merged.csv` |
| `add_wind_speed.py` | Add WS10M wind speed per week | Updates merged CSV |
| `segregate_weekly_columns.py` | Pivot to wide segregated format | `rice_nasapower_weekly_segregated.xlsx` |
| `build_rework_52week_excel.py` | Optional: build from rework.xlsx | `rework_52week_calendar.xlsx` |

All scripts: `NASA_Project/` root folder.

---

## Documentation Files

| File | Description |
|------|-------------|
| [`Rice_NASAPOWER_Segregated_Dataset_Documentation.doc`](Rice_NASAPOWER_Segregated_Dataset_Documentation.doc) | Word document |
| [`Rice_NASAPOWER_Segregated_Dataset_Documentation.docx`](Rice_NASAPOWER_Segregated_Dataset_Documentation.docx) | Word document (docx) |
| [`column_dictionary.csv`](column_dictionary.csv) | All 392 column definitions |
| [`dataset_summary.csv`](dataset_summary.csv) | Summary statistics |
| [`SUPPORTING_SCRIPTS.md`](SUPPORTING_SCRIPTS.md) | Script documentation and pipeline |
| [`supporting_scripts.csv`](supporting_scripts.csv) | Script reference table |

---

## Example

```python
import pandas as pd

df = pd.read_excel("exports/rice_nasapower_weekly_segregated.xlsx")

wind_cols = [c for c in df.columns if c.endswith("_wind_speed")]
df.loc[0, ["record_id", "week01_wind_speed", "week28_wind_speed", "week52_wind_speed"]]
```
