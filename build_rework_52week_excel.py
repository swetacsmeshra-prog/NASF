#!/usr/bin/env python3
"""
Build a clean 52-week calendar Excel from rework.xlsx farm records.

Week calendar (ind_week, week_start, week_end) from nasapower 30 yrs.xlsx.
Weather per farm lat/lon from NASA POWER for the sowing calendar year.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from pathlib import Path

import pandas as pd
import requests

NASA_DAILY_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
NASA_PARAMETERS = "PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN,T2M_MIN,T2M_MAX"
MAX_WEEKS = 52

FARM_COLUMNS = [
    "record_id",
    "Longitude",
    "Latitude",
    "Crop Sowing Time",
    "Crop Harvesting Time",
    "Yield Level (t/ha)",
    "season_days",
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
    "Crop Sowing Time_1",
    "Crop Harvesting Time_1",
]

WEATHER_METRICS = [
    ("rainfall_total_week_mm", "PRECTOTCORR", "sum"),
    ("rainfall_weekly_mean_mm_day", "PRECTOTCORR", "mean"),
    ("relative_humidity_weekly_mean_pct", "RH2M", "mean"),
    ("solar_radiation_weekly_mean_kwh_m2_day", "ALLSKY_SFC_SW_DWN", "mean"),
    ("T_Min", "T2M_MIN", "min"),
    ("T_Max", "T2M_MAX", "max"),
]


def load_rework_farms(path: Path) -> pd.DataFrame:
    df = pd.read_excel(path)
    df = df[df["record_id"].notna()].copy()
    df["record_id"] = df["record_id"].astype(int)
    df["Crop Sowing Time"] = pd.to_datetime(df["Crop Sowing Time"], errors="coerce")
    df["Crop Harvesting Time"] = pd.to_datetime(df["Crop Harvesting Time"], errors="coerce")
    df["sow_year"] = df["Crop Sowing Time"].dt.year.astype(int)
    return df.sort_values("record_id").reset_index(drop=True)


def load_week_calendar(nasa_path: Path) -> pd.DataFrame:
    df = pd.read_excel(nasa_path)
    weekly = df[df["granularity"] == "weekly_ind"].copy()
    weekly["week_start"] = pd.to_datetime(weekly["week_start"])
    weekly["week_end"] = pd.to_datetime(weekly["week_end"])
    weekly["ind_week"] = weekly["ind_week"].astype(int)
    weekly["year"] = weekly["year"].astype(int)
    calendar = weekly[weekly["ind_week"] <= MAX_WEEKS].copy()
    return calendar.rename(columns={"year": "calendar_year"}).sort_values(
        ["calendar_year", "ind_week"]
    )


def cache_key(lat: float, lon: float, year: int) -> str:
    return hashlib.md5(f"{lat:.6f}_{lon:.6f}_{year}_full_year".encode()).hexdigest()


def fetch_nasa_daily_year(
    lat: float,
    lon: float,
    year: int,
    cache_dir: Path,
    session: requests.Session,
    sleep_s: float,
) -> pd.DataFrame:
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_file = cache_dir / f"{cache_key(lat, lon, year)}.json"
    start = pd.Timestamp(year=year, month=1, day=1)
    end = pd.Timestamp(year=year, month=12, day=31)

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
        response = session.get(NASA_DAILY_URL, params=params, timeout=120)
        response.raise_for_status()
        payload = response.json()
        with cache_file.open("w") as f:
            json.dump(payload, f)
        if sleep_s > 0:
            time.sleep(sleep_s)

    parameter_data = payload["properties"]["parameter"]
    dates = sorted(parameter_data["PRECTOTCORR"].keys())
    return pd.DataFrame(
        {
            "date": pd.to_datetime(dates, format="%Y%m%d"),
            "PRECTOTCORR": [parameter_data["PRECTOTCORR"][d] for d in dates],
            "RH2M": [parameter_data["RH2M"][d] for d in dates],
            "ALLSKY_SFC_SW_DWN": [parameter_data["ALLSKY_SFC_SW_DWN"][d] for d in dates],
            "T2M_MIN": [parameter_data["T2M_MIN"][d] for d in dates],
            "T2M_MAX": [parameter_data["T2M_MAX"][d] for d in dates],
        }
    )


def aggregate_week(daily: pd.DataFrame, week_start: pd.Timestamp, week_end: pd.Timestamp) -> dict[str, float]:
    mask = (daily["date"] >= week_start) & (daily["date"] <= week_end)
    week = daily.loc[mask]
    if week.empty:
        return {metric: float("nan") for metric, _, _ in WEATHER_METRICS}
    return {
        "rainfall_total_week_mm": float(week["PRECTOTCORR"].sum()),
        "rainfall_weekly_mean_mm_day": float(week["PRECTOTCORR"].mean()),
        "relative_humidity_weekly_mean_pct": float(week["RH2M"].mean()),
        "solar_radiation_weekly_mean_kwh_m2_day": float(week["ALLSKY_SFC_SW_DWN"].mean()),
        "T_Min": float(week["T2M_MIN"].min()),
        "T_Max": float(week["T2M_MAX"].max()),
    }


def build_wide_excel(
    farms: pd.DataFrame,
    calendar: pd.DataFrame,
    cache_dir: Path,
    sleep_s: float,
) -> pd.DataFrame:
    session = requests.Session()
    year_cache: dict[tuple, pd.DataFrame] = {}
    rows: list[dict] = []

    total_fetches = farms.groupby(["Latitude", "Longitude", "sow_year"]).ngroups
    fetched = 0

    for _, farm in farms.iterrows():
        lat = float(farm["Latitude"])
        lon = float(farm["Longitude"])
        year = int(farm["sow_year"])
        key = (round(lat, 6), round(lon, 6), year)

        if key not in year_cache:
            year_cache[key] = fetch_nasa_daily_year(lat, lon, year, cache_dir, session, sleep_s)
            fetched += 1
            if fetched % 50 == 0:
                print(f"Fetched {fetched}/{total_fetches} location-year datasets...")

        daily = year_cache[key]
        year_weeks = calendar[calendar["calendar_year"] == year]

        row = {col: farm[col] for col in FARM_COLUMNS if col in farm.index}
        row["calendar_year"] = year

        for _, week in year_weeks.iterrows():
            wn = int(week["ind_week"])
            weather = aggregate_week(daily, week["week_start"], week["week_end"])
            row[f"week{wn:02d}_ind_week"] = wn
            row[f"week{wn:02d}_week_start"] = week["week_start"].strftime("%Y-%m-%d")
            row[f"week{wn:02d}_week_end"] = week["week_end"].strftime("%Y-%m-%d")
            for metric, _, _ in WEATHER_METRICS:
                row[f"week{wn:02d}_{metric}"] = weather[metric]

        rows.append(row)

    wide = pd.DataFrame(rows)

    # Column order: farm fields, then week01..week52 blocks (dates + weather adjacent)
    farm_cols = [c for c in FARM_COLUMNS if c in wide.columns] + ["calendar_year"]
    week_cols: list[str] = []
    for w in range(1, MAX_WEEKS + 1):
        prefix = f"week{w:02d}_"
        block = [c for c in wide.columns if c.startswith(prefix)]
        # Sort: ind_week, week_start, week_end, then metrics in defined order
        ordered_block = []
        for suffix in ["ind_week", "week_start", "week_end"]:
            col = f"{prefix}{suffix}"
            if col in block:
                ordered_block.append(col)
        for metric, _, _ in WEATHER_METRICS:
            col = f"{prefix}{metric}"
            if col in block:
                ordered_block.append(col)
        week_cols.extend(ordered_block)

    return wide[farm_cols + week_cols]


def main() -> None:
    parser = argparse.ArgumentParser(description="Build 52-week Excel from rework.xlsx.")
    parser.add_argument("--rework", default="/Users/nagarro/Documents/Project/rework.xlsx")
    parser.add_argument(
        "--nasa-calendar",
        default="/Users/nagarro/Documents/Project/csv files/nasapower 30 yrs.xlsx",
    )
    parser.add_argument(
        "--output",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rework_52week_calendar.xlsx",
    )
    parser.add_argument(
        "--cache-dir",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/nasa_cache/yearly",
    )
    parser.add_argument("--sleep", type=float, default=0.1)
    args = parser.parse_args()

    farms = load_rework_farms(Path(args.rework))
    calendar = load_week_calendar(Path(args.nasa_calendar))

    print(f"Farms from rework.xlsx: {len(farms)}")
    print(f"Sowing years: {sorted(farms['sow_year'].unique())}")

    wide = build_wide_excel(farms, calendar, Path(args.cache_dir), args.sleep)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)

    # Week reference sheet from nasapower template
    nasa_full = pd.read_excel(args.nasa_calendar)
    nasa_weekly = nasa_full[nasa_full["granularity"] == "weekly_ind"].copy()
    nasa_weekly = nasa_weekly[nasa_weekly["ind_week"] <= MAX_WEEKS]
    nasa_weekly["week_start"] = pd.to_datetime(nasa_weekly["week_start"]).dt.strftime("%Y-%m-%d")
    nasa_weekly["week_end"] = pd.to_datetime(nasa_weekly["week_end"]).dt.strftime("%Y-%m-%d")
    ref_sheet_cols = [
        "year",
        "ind_week",
        "week_start",
        "week_end",
        "rainfall_total_week_mm",
        "rainfall_weekly_mean_mm_day",
        "relative_humidity_weekly_mean_pct",
        "solar_radiation_weekly_mean_kwh_m2_day",
    ]

    with pd.ExcelWriter(out, engine="openpyxl") as writer:
        wide.to_excel(writer, sheet_name="Farm_52Week_Data", index=False)
        nasa_weekly[ref_sheet_cols].to_excel(writer, sheet_name="Week_Calendar_Reference", index=False)

    print(f"\nSaved: {out}")
    print(f"Sheet 1: {len(wide)} farms x {len(wide.columns)} columns")
    print(f"Sheet 2: Week calendar reference ({len(nasa_weekly)} rows from nasapower 30 yrs.xlsx)")


if __name__ == "__main__":
    main()
