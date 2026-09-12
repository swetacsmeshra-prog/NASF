# Supporting Scripts — Segregated 52-Week Dataset

All scripts are located in the **NASA_Project** root folder.

## Pipeline Overview

```
RICE_UP_BI_PU_HR_USED_FINAL.xlsx  +  nasapower 30 yrs.xlsx
              │
              ▼
    merge_rice_nasapower_long.py  ──►  rice_nasapower_weekly_merged.csv
              │
              ▼
         add_wind_speed.py         ──►  (adds wind_speed_weekly_mean_ms)
              │
              ▼
    segregate_weekly_columns.py   ──►  rice_nasapower_weekly_segregated.xlsx / .csv
```

---

## 1. merge_rice_nasapower_long.py

**Purpose:** Build the long-format merged CSV with 52 calendar weeks per farm.

| Item | Detail |
|------|--------|
| **Input** | `RICE_UP_BI_PU_HR_USED_FINAL.xlsx`, `nasapower 30 yrs.xlsx` |
| **Output** | `exports/rice_nasapower_weekly_merged.csv` |
| **Rows** | 1,056 farms × 52 weeks = 54,912 |
| **NASA parameters** | PRECTOTCORR, RH2M, ALLSKY_SFC_SW_DWN, T2M_MIN, T2M_MAX, WS10M |
| **Cache** | `exports/nasa_cache/yearly/` |

**Run:**
```bash
cd NASA_Project
source .venv/bin/activate
python3 merge_rice_nasapower_long.py
```

**Arguments:**

| Argument | Default | Description |
|----------|---------|-------------|
| `--rice` | `RICE_UP_BI_PU_HR_USED_FINAL.xlsx` | Rice farm source Excel |
| `--nasa-calendar` | `nasapower 30 yrs.xlsx` | 52-week calendar template |
| `--output` | `exports/rice_nasapower_weekly_merged.csv` | Output CSV path |
| `--cache-dir` | `exports/nasa_cache/yearly` | NASA API response cache |
| `--sleep` | `0.3` | Delay (seconds) between API calls |

---

## 2. add_wind_speed.py

**Purpose:** Add weekly wind speed column to the long-format merged CSV.

| Item | Detail |
|------|--------|
| **Input** | `exports/rice_nasapower_weekly_merged.csv` |
| **Output** | Same file (adds `wind_speed_weekly_mean_ms`) |
| **NASA parameter** | WS10M (wind speed at 10 m, m/s) |
| **Aggregation** | Mean of daily WS10M per week (week_start–week_end) |
| **Cache** | `exports/nasa_cache/wind/` |

**Run:**
```bash
python3 add_wind_speed.py
```

**Arguments:**

| Argument | Default | Description |
|----------|---------|-------------|
| `--input` | `exports/rice_nasapower_weekly_merged.csv` | Input CSV |
| `--output` | `exports/rice_nasapower_weekly_merged.csv` | Output CSV |
| `--cache-dir` | `exports/nasa_cache/wind` | Wind API cache |
| `--sleep` | `0.2` | Delay between API calls |

---

## 3. segregate_weekly_columns.py

**Purpose:** Pivot long-format data to wide segregated Excel/CSV (one row per farm).

| Item | Detail |
|------|--------|
| **Input** | `exports/rice_nasapower_weekly_merged.csv` |
| **Output** | `exports/rice_nasapower_weekly_segregated.xlsx`, `.csv` |
| **Rows** | 1,056 (one per farm) |
| **Columns** | 392 (28 farm + 364 weather) |

**Metric blocks (52 columns each):**
- `week01_rainfall_total` … `week52_rainfall_total`
- `week01_rainfall_mean` … `week52_rainfall_mean`
- `week01_relative_humidity` … `week52_relative_humidity`
- `week01_solar_radiation` … `week52_solar_radiation`
- `week01_t_min` … `week52_t_min`
- `week01_t_max` … `week52_t_max`
- `week01_wind_speed` … `week52_wind_speed`

**Run:**
```bash
python3 segregate_weekly_columns.py
```

**Arguments:**

| Argument | Default | Description |
|----------|---------|-------------|
| `--input` | `exports/rice_nasapower_weekly_merged.csv` | Long-format source CSV |
| `--output-csv` | `exports/rice_nasapower_weekly_segregated.csv` | Output CSV |
| `--output-excel` | `exports/rice_nasapower_weekly_segregated.xlsx` | Output Excel |

---

## 4. build_rework_52week_excel.py (optional)

**Purpose:** Build 52-week wide Excel from `rework.xlsx` farm template.

| Item | Detail |
|------|--------|
| **Input** | `rework.xlsx`, `nasapower 30 yrs.xlsx` |
| **Output** | `exports/rework_52week_calendar.xlsx` |

**Run:**
```bash
python3 build_rework_52week_excel.py
```

---

## Prerequisites

```bash
cd NASA_Project
python3 -m venv .venv
source .venv/bin/activate
pip install pandas openpyxl requests python-docx
```

## Full Regeneration (all steps)

```bash
source .venv/bin/activate
python3 merge_rice_nasapower_long.py
python3 add_wind_speed.py
python3 segregate_weekly_columns.py
```

## Script File Locations

| Script | Path |
|--------|------|
| merge_rice_nasapower_long.py | `NASA_Project/merge_rice_nasapower_long.py` |
| add_wind_speed.py | `NASA_Project/add_wind_speed.py` |
| segregate_weekly_columns.py | `NASA_Project/segregate_weekly_columns.py` |
| build_rework_52week_excel.py | `NASA_Project/build_rework_52week_excel.py` |
