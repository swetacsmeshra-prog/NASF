#!/usr/bin/env python3
"""
Merge rice farm records with NASA POWER weekly weather (per lat/lon).

- Fetches NASA POWER daily data per farm location and growing season
- Aggregates Mon-Fri calendar weeks (Option B)
- Outputs wide format: one row per farm, week1_*, week2_*, ...
- Filters outlier records (invalid dates or season > 200 days)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path

import pandas as pd
import requests

NASA_DAILY_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
NASA_PARAMETERS = "PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN"
MAX_SEASON_DAYS = 200
MAX_WEEKS = 29

RICE_COLUMNS_KEEP = [
    "Longitude",
    "Latitude",
    "pH (1:2)",
    "EC (dS/m)",
    "OC %",
    "Major-nutrient Avail N. (Kg/ha)",
    "Major-nutrient P(kg/ha)",
    "Major-nutrient Avail. K (kg/ha)",
    "Major-nutrient Sulphur (kg/ha)",
    "Micro-nutrientZn (ppm)",
    "Micro-nutrient Fe (ppm)",
    "Micro-nutrient Mn (ppm)",
    "Micro-nutrient Cu (ppm)",
    "Actual Residue Incorporation",
    "N (kg/ha)",
    "P2o5 from DAP",
    "K2o5 from MOP",
    "Zinc Sul",
    "FYM (in t/ha)",
    "FYM input interval (Yr)",
    "Crop Sowing Time",
    "Crop Sowing Time_1",
    "Crop Harvesting Time",
    "Crop Harvesting Time_1",
    "Yield Level (t/ha)",
]

WEATHER_METRICS = [
    "rainfall_total_week_mm",
    "rainfall_weekly_mean_mm_day",
    "relative_humidity_weekly_mean_pct",
    "solar_radiation_weekly_mean_kwh_m2_day",
]


def load_rice_data(path: Path) -> pd.DataFrame:
    df = pd.read_excel(path)
    df = df.iloc[1:].copy().reset_index(drop=True)
    df["record_id"] = df.index + 1

    df["Crop Sowing Time"] = pd.to_datetime(df["Crop Sowing Time"], errors="coerce")
    df["Crop Harvesting Time"] = pd.to_datetime(df["Crop Harvesting Time"], errors="coerce")
    df["Longitude"] = pd.to_numeric(df["Longitude"], errors="coerce")
    df["Latitude"] = pd.to_numeric(df["Latitude"], errors="coerce")
    df["Yield Level (t/ha)"] = pd.to_numeric(df["Yield Level (t/ha)"], errors="coerce")
    df["season_days"] = (df["Crop Harvesting Time"] - df["Crop Sowing Time"]).dt.days

    valid = (
        df["Crop Sowing Time"].notna()
        & df["Crop Harvesting Time"].notna()
        & (df["Crop Harvesting Time"] >= df["Crop Sowing Time"])
        & (df["season_days"] <= MAX_SEASON_DAYS)
        & df["Latitude"].notna()
        & df["Longitude"].notna()
    )
    return df.loc[valid].copy()


def monday_of(date: pd.Timestamp) -> pd.Timestamp:
    return date.normalize() - pd.Timedelta(days=date.dayofweek)


def mon_fri_weeks(sow: pd.Timestamp, harv: pd.Timestamp) -> list[tuple[pd.Timestamp, pd.Timestamp]]:
    monday = monday_of(sow)
    weeks: list[tuple[pd.Timestamp, pd.Timestamp]] = []
    while monday <= harv:
        friday = monday + pd.Timedelta(days=4)
        if friday >= sow and monday <= harv:
            weeks.append((monday, friday))
        monday += pd.Timedelta(days=7)
    return weeks


def cache_key(lat: float, lon: float, start: pd.Timestamp, end: pd.Timestamp) -> str:
    raw = f"{lat:.6f}_{lon:.6f}_{start:%Y%m%d}_{end:%Y%m%d}"
    return hashlib.md5(raw.encode()).hexdigest()


def fetch_nasa_daily(
    lat: float,
    lon: float,
    start: pd.Timestamp,
    end: pd.Timestamp,
    cache_dir: Path,
    session: requests.Session,
    sleep_s: float,
) -> pd.DataFrame:
    cache_dir.mkdir(parents=True, exist_ok=True)
    key = cache_key(lat, lon, start, end)
    cache_file = cache_dir / f"{key}.json"

    if cache_file.exists():
        with cache_file.open() as f:
            payload = json.load(f)
    else:
        params = {
            "start": start.strftime("%Y%m%d"),
            "end": end.strftime("%Y%m%d"),
            "latitude": lat,
            "longitude": lon,
            "community": "AG",
            "parameters": NASA_PARAMETERS,
            "format": "JSON",
            "units": "metric",
            "header": "true",
            "time-standard": "utc",
        }
        response = session.get(NASA_DAILY_URL, params=params, timeout=90)
        response.raise_for_status()
        payload = response.json()
        with cache_file.open("w") as f:
            json.dump(payload, f)
        if sleep_s > 0:
            time.sleep(sleep_s)

    parameter_data = payload["properties"]["parameter"]
    dates = sorted(parameter_data["PRECTOTCORR"].keys())
    rows = []
    for date_str in dates:
        rows.append(
            {
                "date": pd.to_datetime(date_str, format="%Y%m%d"),
                "PRECTOTCORR": parameter_data["PRECTOTCORR"][date_str],
                "RH2M": parameter_data["RH2M"][date_str],
                "ALLSKY_SFC_SW_DWN": parameter_data["ALLSKY_SFC_SW_DWN"][date_str],
            }
        )
    return pd.DataFrame(rows)


def aggregate_mon_fri_weeks(daily: pd.DataFrame, sow: pd.Timestamp, harv: pd.Timestamp) -> pd.DataFrame:
    weekly_rows = []
    for week_num, (monday, friday) in enumerate(mon_fri_weeks(sow, harv), start=1):
        mask = (daily["date"] >= monday) & (daily["date"] <= friday)
        week = daily.loc[mask]
        if week.empty:
            continue
        weekly_rows.append(
            {
                "week_number": week_num,
                "week_monday": monday,
                "week_friday": friday,
                "rainfall_total_week_mm": week["PRECTOTCORR"].sum(),
                "rainfall_weekly_mean_mm_day": week["PRECTOTCORR"].mean(),
                "relative_humidity_weekly_mean_pct": week["RH2M"].mean(),
                "solar_radiation_weekly_mean_kwh_m2_day": week["ALLSKY_SFC_SW_DWN"].mean(),
            }
        )
    return pd.DataFrame(weekly_rows)


def weekly_to_wide(weekly: pd.DataFrame) -> dict[str, float]:
    wide: dict[str, float] = {}
    for _, row in weekly.iterrows():
        week_num = int(row["week_number"])
        for metric in WEATHER_METRICS:
            wide[f"week{week_num:02d}_{metric}"] = row[metric]
    return wide


def build_wide_dataset(
    rice: pd.DataFrame,
    cache_dir: Path,
    sleep_s: float,
) -> pd.DataFrame:
    session = requests.Session()
    fetch_cache: dict[tuple, pd.DataFrame] = {}
    output_rows: list[dict] = []

    total = len(rice)
    for idx, (_, row) in enumerate(rice.iterrows(), start=1):
        lat = float(row["Latitude"])
        lon = float(row["Longitude"])
        sow = row["Crop Sowing Time"]
        harv = row["Crop Harvesting Time"]
        req_key = (round(lat, 6), round(lon, 6), sow, harv)

        if req_key not in fetch_cache:
            daily = fetch_nasa_daily(lat, lon, sow, harv, cache_dir, session, sleep_s)
            fetch_cache[req_key] = daily
            print(f"[{idx}/{total}] Fetched NASA data for ({lat:.4f}, {lon:.4f}) {sow:%Y-%m-%d} to {harv:%Y-%m-%d}")
        else:
            print(f"[{idx}/{total}] Cache hit for ({lat:.4f}, {lon:.4f}) {sow:%Y-%m-%d} to {harv:%Y-%m-%d}")

        weekly = aggregate_mon_fri_weeks(fetch_cache[req_key], sow, harv)
        wide_weather = weekly_to_wide(weekly)

        farm_data = {col: row[col] for col in RICE_COLUMNS_KEEP if col in row.index}
        farm_data["record_id"] = row["record_id"]
        farm_data["season_days"] = row["season_days"]
        farm_data["total_mon_fri_weeks"] = len(weekly)
        farm_data.update(wide_weather)
        output_rows.append(farm_data)

    merged = pd.DataFrame(output_rows)

    front_cols = [
        "record_id",
        "Longitude",
        "Latitude",
        "Crop Sowing Time",
        "Crop Harvesting Time",
        "Yield Level (t/ha)",
        "season_days",
        "total_mon_fri_weeks",
    ]
    other_farm = [c for c in RICE_COLUMNS_KEEP if c not in front_cols and c in merged.columns]
    week_cols = []
    for week_num in range(1, MAX_WEEKS + 1):
        for metric in WEATHER_METRICS:
            col = f"week{week_num:02d}_{metric}"
            if col in merged.columns:
                week_cols.append(col)
    ordered = front_cols + other_farm + week_cols
    ordered = [c for c in ordered if c in merged.columns]
    remaining = [c for c in merged.columns if c not in ordered]
    return merged[ordered + remaining]


def main() -> None:
    parser = argparse.ArgumentParser(description="Merge rice and NASA POWER weekly weather (wide format).")
    parser.add_argument(
        "--rice",
        default="/Users/nagarro/Documents/Project/csv files/RICE_UP_BI_PU_HR_USED_FINAL.xlsx",
    )
    parser.add_argument(
        "--output",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_wide.csv",
    )
    parser.add_argument(
        "--cache-dir",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/nasa_cache",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=0.3,
        help="Seconds to sleep between NASA API calls (cache hits are not delayed).",
    )
    args = parser.parse_args()

    rice = load_rice_data(Path(args.rice))
    merged = build_wide_dataset(rice, Path(args.cache_dir), args.sleep)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    merged.to_csv(out, index=False)

    print(f"\nRice records (valid, outliers removed): {len(rice)}")
    print(f"Output rows (one per farm): {len(merged)}")
    print(f"Output columns: {len(merged.columns)}")
    print(f"Output: {out}")


if __name__ == "__main__":
    main()
