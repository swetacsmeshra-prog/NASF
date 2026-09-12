#!/usr/bin/env python3
"""Add weekly wind speed (WS10M) to rice_nasapower_weekly_merged.csv."""

from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path

import pandas as pd
import requests

NASA_DAILY_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
WIND_PARAMETER = "WS10M"  # Wind speed at 10 m, m/s (metric)


def cache_key(lat: float, lon: float, year: int) -> str:
    return hashlib.md5(f"{lat:.6f}_{lon:.6f}_{year}_ws10m".encode()).hexdigest()


def fetch_wind_daily(
    lat: float,
    lon: float,
    year: int,
    cache_dir: Path,
    session: requests.Session,
    sleep_s: float,
) -> pd.DataFrame:
    cache_dir.mkdir(parents=True, exist_ok=True)
    cache_file = cache_dir / f"{cache_key(lat, lon, year)}.json"

    if cache_file.exists():
        with cache_file.open() as f:
            payload = json.load(f)
    else:
        params = {
            "start": f"{year}0101",
            "end": f"{year}1231",
            "latitude": lat,
            "longitude": lon,
            "community": "AG",
            "parameters": WIND_PARAMETER,
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

    parameter_data = payload["properties"]["parameter"][WIND_PARAMETER]
    dates = sorted(parameter_data.keys())
    return pd.DataFrame(
        {
            "date": pd.to_datetime(dates, format="%Y%m%d"),
            "WS10M": [parameter_data[d] for d in dates],
        }
    )


def weekly_mean_wind(daily: pd.DataFrame, week_start: pd.Timestamp, week_end: pd.Timestamp) -> float:
    mask = (daily["date"] >= week_start) & (daily["date"] <= week_end)
    week = daily.loc[mask, "WS10M"]
    return float(week.mean()) if not week.empty else float("nan")


def add_wind_speed(df: pd.DataFrame, cache_dir: Path, sleep_s: float) -> pd.DataFrame:
    df = df.copy()
    df["week_start"] = pd.to_datetime(df["week_start"])
    df["week_end"] = pd.to_datetime(df["week_end"])

    session = requests.Session()
    wind_cache: dict[tuple, pd.DataFrame] = {}
    wind_values: list[float] = []

    keys = df.groupby(["Latitude", "Longitude", "calendar_year"]).ngroups
    fetched = 0

    for _, row in df.iterrows():
        lat = float(row["Latitude"])
        lon = float(row["Longitude"])
        year = int(row["calendar_year"])
        key = (round(lat, 6), round(lon, 6), year)

        if key not in wind_cache:
            wind_cache[key] = fetch_wind_daily(lat, lon, year, cache_dir, session, sleep_s)
            fetched += 1
            if fetched % 50 == 0:
                print(f"Fetched wind data {fetched}/{keys} location-years...")

        wind_values.append(weekly_mean_wind(wind_cache[key], row["week_start"], row["week_end"]))

    df["wind_speed_weekly_mean_ms"] = wind_values

    # Place wind_speed after T_Max in weather block
    cols = list(df.columns)
    if "T_Max" in cols and "wind_speed_weekly_mean_ms" in cols:
        cols.remove("wind_speed_weekly_mean_ms")
        tmax_idx = cols.index("T_Max") + 1
        cols.insert(tmax_idx, "wind_speed_weekly_mean_ms")
    return df[cols]


def main() -> None:
    parser = argparse.ArgumentParser(description="Add wind speed to weekly merged CSV.")
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
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/nasa_cache/wind",
    )
    parser.add_argument("--sleep", type=float, default=0.2)
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    print(f"Loaded {len(df)} rows")

    updated = add_wind_speed(df, Path(args.cache_dir), args.sleep)
    updated.to_csv(args.output, index=False)

    print(f"\nSaved {len(updated)} rows to {args.output}")
    print(f"wind_speed range: {updated['wind_speed_weekly_mean_ms'].min():.2f} – {updated['wind_speed_weekly_mean_ms'].max():.2f} m/s")
    print(f"Missing: {updated['wind_speed_weekly_mean_ms'].isna().sum()}")


if __name__ == "__main__":
    main()
