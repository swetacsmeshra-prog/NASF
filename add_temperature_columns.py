#!/usr/bin/env python3
"""Add T_Min and T_Max columns to rice_nasapower_weekly_merged.csv from NASA POWER."""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path

import pandas as pd
import requests

NASA_DAILY_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
NASA_TEMP_PARAMETERS = "T2M_MIN,T2M_MAX"


def cache_key(lat: float, lon: float, start: pd.Timestamp, end: pd.Timestamp) -> str:
    raw = f"{lat:.6f}_{lon:.6f}_{start:%Y%m%d}_{end:%Y%m%d}_temp"
    return hashlib.md5(raw.encode()).hexdigest()


def fetch_daily_temperature(
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
            "parameters": NASA_TEMP_PARAMETERS,
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
    dates = sorted(parameter_data["T2M_MIN"].keys())
    return pd.DataFrame(
        {
            "date": pd.to_datetime(dates, format="%Y%m%d"),
            "T2M_MIN": [parameter_data["T2M_MIN"][d] for d in dates],
            "T2M_MAX": [parameter_data["T2M_MAX"][d] for d in dates],
        }
    )


def weekly_t_min_max(daily: pd.DataFrame, week_monday: pd.Timestamp, week_friday: pd.Timestamp) -> tuple[float, float]:
    mask = (daily["date"] >= pd.to_datetime(week_monday)) & (daily["date"] <= pd.to_datetime(week_friday))
    week = daily.loc[mask]
    if week.empty:
        return float("nan"), float("nan")
    return float(week["T2M_MIN"].min()), float(week["T2M_MAX"].max())


def add_temperature_columns(df: pd.DataFrame, cache_dir: Path, sleep_s: float) -> pd.DataFrame:
    df = df.copy()
    df["Crop Sowing Time"] = pd.to_datetime(df["Crop Sowing Time"])
    df["Crop Harvesting Time"] = pd.to_datetime(df["Crop Harvesting Time"])
    df["week_monday"] = pd.to_datetime(df["week_monday"])
    df["week_friday"] = pd.to_datetime(df["week_friday"])

    session = requests.Session()
    season_cache: dict[tuple, pd.DataFrame] = {}
    t_mins: list[float] = []
    t_maxs: list[float] = []

    season_keys = df.groupby(["Latitude", "Longitude", "Crop Sowing Time", "Crop Harvesting Time"]).ngroups
    fetched = 0

    for i, row in df.iterrows():
        lat = float(row["Latitude"])
        lon = float(row["Longitude"])
        sow = row["Crop Sowing Time"]
        harv = row["Crop Harvesting Time"]
        key = (round(lat, 6), round(lon, 6), sow, harv)

        if key not in season_cache:
            season_cache[key] = fetch_daily_temperature(lat, lon, sow, harv, cache_dir, session, sleep_s)
            fetched += 1
            if fetched % 50 == 0:
                print(f"Fetched {fetched}/{season_keys} unique growing seasons...")

        t_min, t_max = weekly_t_min_max(season_cache[key], row["week_monday"], row["week_friday"])
        t_mins.append(t_min)
        t_maxs.append(t_max)

    df["T_Min"] = t_mins
    df["T_Max"] = t_maxs

    # Place T_Min, T_Max adjacent to other weather columns
    weather_block = [
        "rainfall_total_week_mm",
        "rainfall_weekly_mean_mm_day",
        "relative_humidity_weekly_mean_pct",
        "solar_radiation_weekly_mean_kwh_m2_day",
        "T_Min",
        "T_Max",
    ]
    meta_after = ["year", "ind_week", "week_start", "week_end"]
    front = [c for c in df.columns if c not in weather_block + meta_after]
    ordered = front + weather_block + meta_after
    return df[ordered]


def main() -> None:
    parser = argparse.ArgumentParser(description="Add T_Min and T_Max to weekly merged CSV.")
    parser.add_argument(
        "--input",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_merged.csv",
    )
    parser.add_argument(
        "--output",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_merged.csv",
    )
    parser.add_argument(
        "--cache-dir",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/nasa_cache/temp",
    )
    parser.add_argument("--sleep", type=float, default=0.3)
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    print(f"Loaded {len(df)} rows from {args.input}")

    updated = add_temperature_columns(df, Path(args.cache_dir), args.sleep)
    updated.to_csv(args.output, index=False)

    print(f"\nSaved {len(updated)} rows, {len(updated.columns)} columns to {args.output}")
    print(f"T_Min range: {updated['T_Min'].min():.2f} – {updated['T_Min'].max():.2f} °C")
    print(f"T_Max range: {updated['T_Max'].min():.2f} – {updated['T_Max'].max():.2f} °C")
    print(f"Missing T_Min: {updated['T_Min'].isna().sum()}, Missing T_Max: {updated['T_Max'].isna().sum()}")


if __name__ == "__main__":
    main()
