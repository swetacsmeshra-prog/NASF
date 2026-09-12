# Merge Methodology: Rice + NASA POWER Weekly Weather

This document describes how `rice_nasapower_weekly_wide.csv` was created from the rice farm dataset and NASA POWER API.

---

## 1. High-Level Pipeline

```mermaid
flowchart LR
    A[RICE_UP_BI_PU_HR_USED_FINAL.xlsx] --> B[Clean & Filter]
    B --> C[For each farm record]
    C --> D[Fetch NASA POWER daily data\nlat/lon + sow→harvest]
    D --> E[Aggregate Mon-Fri weeks]
    E --> F[Pivot to wide format\nweek01..week29 columns]
    F --> G[rice_nasapower_weekly_wide.csv]
```

---

## 2. Input Data Preparation

### 2.1 Rice source file

- **File:** `RICE_UP_BI_PU_HR_USED_FINAL.xlsx`
- **Rows:** 1,066 (including one duplicate header row at index 0)
- **Action:** Skip row 0 (alternate column names), use row 1+ as data
- **Assign:** `record_id` = 1-based row index after skip

### 2.2 Type conversion

| Field | Conversion |
|-------|------------|
| `Crop Sowing Time` | `datetime` |
| `Crop Harvesting Time` | `datetime` |
| `Longitude`, `Latitude` | `float` |
| `Yield Level (t/ha)` | `float` |
| `season_days` | `(harvest - sowing).days` |

### 2.3 Exclusion rules

A record is **excluded** if any of the following is true:

| Rule | Rationale |
|------|-----------|
| Sowing or harvest date is missing | Cannot define growing season |
| Harvest date < sowing date | Invalid date sequence (7 records) |
| `season_days` > 200 | Unrealistic/outlier season length (2 records) |
| Latitude or longitude is missing | Cannot fetch location-specific weather |

**Result:** 1,065 source rows → **1,056 included**, **9 excluded**

---

## 3. NASA POWER Weather Fetch

### 3.1 API endpoint

```
GET https://power.larc.nasa.gov/api/temporal/daily/point
```

### 3.2 Request parameters (per farm)

| Parameter | Value |
|-----------|-------|
| `start` | Sowing date (`YYYYMMDD`) |
| `end` | Harvest date (`YYYYMMDD`) |
| `latitude` | Farm latitude |
| `longitude` | Farm longitude |
| `community` | `AG` (agriculture) |
| `parameters` | `PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN` |
| `format` | `JSON` |
| `units` | `metric` |
| `time-standard` | `utc` |

### 3.3 NASA parameter mapping

| NASA parameter | Description | Used for |
|----------------|-------------|----------|
| `PRECTOTCORR` | Precipitation corrected (mm/day) | Rainfall total and mean |
| `RH2M` | Relative humidity at 2 m (%) | Humidity mean |
| `ALLSKY_SFC_SW_DWN` | All-sky surface shortwave downward irradiance (kWh/m²/day) | Solar radiation mean |

### 3.4 Caching

- Each unique `(latitude, longitude, start, end)` request is cached as JSON in `exports/nasa_cache/`
- Cache key: MD5 hash of coordinates and date range
- **1,047 unique API requests** were made for 1,056 farms (9 cache hits from identical location+season)

---

## 4. Monday–Friday Week Definition

### 4.1 Week window

For each farm, Mon–Fri weeks are generated as follows:

1. Find the **Monday on or before** the sowing date
2. Define the week window: **Monday → Friday** (5 calendar days)
3. Include the week if it overlaps the sowing–harvest range:
   - `week_friday >= sowing_date` AND `week_monday <= harvest_date`
4. Advance Monday by 7 days and repeat until Monday exceeds harvest date

```
Sowing: 2015-07-15 (Wed)          Harvest: 2015-11-18 (Wed)

Week 01: Mon 2015-07-13 → Fri 2015-07-17   ✓ overlaps
Week 02: Mon 2015-07-20 → Fri 2015-07-24   ✓ overlaps
  ...
Week 19: Mon 2015-11-09 → Fri 2015-11-13   ✓ overlaps
Week 20: Mon 2015-11-16 → Fri 2015-11-20   ✓ (Fri extends past harvest, still overlaps)
```

### 4.2 Daily aggregation within each week

Only **Monday through Friday** daily values are used:

| Output metric | Formula |
|---------------|---------|
| `rainfall_total_week_mm` | `SUM(PRECTOTCORR)` for Mon–Fri days |
| `rainfall_weekly_mean_mm_day` | `MEAN(PRECTOTCORR)` for Mon–Fri days |
| `relative_humidity_weekly_mean_pct` | `MEAN(RH2M)` for Mon–Fri days |
| `solar_radiation_weekly_mean_kwh_m2_day` | `MEAN(ALLSKY_SFC_SW_DWN)` for Mon–Fri days |

**Note:** If a Mon–Fri window has fewer than 5 days of NASA data (e.g., at season boundaries), aggregation uses only the available days within that window.

---

## 5. Wide Format Pivot

### 5.1 Layout

Each farm's weekly rows are pivoted into columns:

```
Farm record (1 row)
├── week01_rainfall_total_week_mm
├── week01_rainfall_weekly_mean_mm_day
├── week01_relative_humidity_weekly_mean_pct
├── week01_solar_radiation_weekly_mean_kwh_m2_day
├── week02_rainfall_total_week_mm
│   ...
└── week29_solar_radiation_weekly_mean_kwh_m2_day
```

### 5.2 Padding

- Maximum weeks across all farms: **29**
- Columns `week01` through `week29` are always present
- Weeks beyond a farm's `total_mon_fri_weeks` are left blank

---

## 6. Output Schema

### Column order

1. **Identifiers & dates:** `record_id`, `Longitude`, `Latitude`, `Crop Sowing Time`, `Crop Harvesting Time`, `Yield Level (t/ha)`
2. **Derived:** `season_days`, `total_mon_fri_weeks`
3. **Farm fields:** soil, nutrients, management (from source)
4. **Weather:** `week01_*` through `week29_*`

### File details

| Property | Value |
|----------|-------|
| File | `exports/rice_nasapower_weekly_wide.csv` |
| Encoding | UTF-8 |
| Separator | Comma |
| Header | Yes (row 1) |
| Rows | 1,056 + 1 header |
| Columns | 144 |

---

## 7. Excluded Records Detail

| record_id | Reason | season_days | Notes |
|-----------|--------|-------------|-------|
| 321, 326, 334, 335, 408, 410, 414 | harvest_before_sowing | -220 to -235 | Harvest in 2014, sowing in 2015 |
| 331 | season_exceeds_200_days | 503 | Sowing 2014-07-20, harvest 2015-12-05 |
| 388 | season_exceeds_200_days | 4,506 | Sowing 2003-07-10, harvest 2015-11-10 |

Full list: [`excluded_records.csv`](excluded_records.csv)

---

## 8. Limitations & Assumptions

| Item | Detail |
|------|--------|
| **Gridded weather** | NASA POWER provides modelled/reanalysis data at ~0.5° grid resolution, not station measurements |
| **UTC time standard** | All NASA data uses UTC; no local timezone adjustment |
| **Mon–Fri at boundaries** | First/last week may have fewer than 5 days if sowing/harvest falls mid-week |
| **No elevation adjustment** | API called without site-specific elevation parameters |
| **Single growing season** | One sowing–harvest cycle per row; no multi-cropping |
| **Wide column sparsity** | Farms with 6 weeks have 92 blank weather columns (week07–week29) |

---

## 9. Reproducibility

```bash
# Full regeneration
source .venv/bin/activate
python3 merge_rice_nasapower.py

# Custom paths
python3 merge_rice_nasapower.py \
  --rice "/path/to/RICE_UP_BI_PU_HR_USED_FINAL.xlsx" \
  --output "exports/rice_nasapower_weekly_wide.csv" \
  --cache-dir "exports/nasa_cache" \
  --sleep 0.3
```

The merge is fully deterministic given the same source file and NASA cache. Deleting `exports/nasa_cache/` forces fresh API fetches.

---

## 10. Related Documentation

- [`README.md`](README.md) — Dataset overview and usage
- [`column_dictionary.csv`](column_dictionary.csv) — All 144 column definitions
- [`dataset_summary.csv`](dataset_summary.csv) — Summary statistics
- [`../NASA_POWER_INTEGRATION.md`](../NASA_POWER_INTEGRATION.md) — NASA POWER API in the NASF mobile app
