#!/usr/bin/env python3
"""
Build rice_nasapower_weekly_merged.csv with 52 calendar weeks per farm.

Week structure (ind_week, week_start, week_end) is taken from nasapower 30 yrs.xlsx.
Weather is fetched per farm lat/lon from NASA POWER for the sowing calendar year.
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
NASA_PARAMETERS = "PRECTOTCORR,RH2M,ALLSKY_SFC_SW_DWN,T2M_MIN,T2M_MAX,WS10M"
MAX_SEASON_DAYS = 200
MAX_WEEKS = 52

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
    out = df.loc[valid].copy()
    out["sow_year"] = out["Crop Sowing Time"].dt.year
    return out


def load_week_calendar(nasa_path: Path) -> pd.DataFrame:
    df = pd.read_excel(nasa_path)
    weekly = df[df["granularity"] == "weekly_ind"].copy()
    weekly["week_start"] = pd.to_datetime(weekly["week_start"])
    weekly["week_end"] = pd.to_datetime(weekly["week_end"])
    weekly["ind_week"] = weekly["ind_week"].astype(int)
    weekly["year"] = weekly["year"].astype(int)
    calendar = weekly[weekly["ind_week"] <= MAX_WEEKS].copy()
    calendar = calendar.rename(columns={"year": "calendar_year"})
    return calendar[["calendar_year", "ind_week", "week_start", "week_end"]].sort_values(
        ["calendar_year", "ind_week"]
    )


def cache_key(lat: float, lon: float, year: int) -> str:
    raw = f"{lat:.6f}_{lon:.6f}_{year}_full_year"
    return hashlib.md5(raw.encode()).hexdigest()


def fetch_nasa_daily_year(
    lat: float,
    lon: float,
    year: int,
    cache_dir: Path,
    session: requests.Session,
    sleep_s: float,
) -> pd.DataFrame:
    cache_dir.mkdir(parents=True, exist_ok=True)
    key = cache_key(lat, lon, year)
    cache_file = cache_dir / f"{key}.json"
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
            "WS10M": [parameter_data["WS10M"][d] for d in dates],
        }
    )


def aggregate_week(daily: pd.DataFrame, week_start: pd.Timestamp, week_end: pd.Timestamp) -> dict:
    mask = (daily["date"] >= week_start) & (daily["date"] <= week_end)
    week = daily.loc[mask]
    if week.empty:
        return {
            "rainfall_total_week_mm": float("nan"),
            "rainfall_weekly_mean_mm_day": float("nan"),
            "relative_humidity_weekly_mean_pct": float("nan"),
            "solar_radiation_weekly_mean_kwh_m2_day": float("nan"),
            "T_Min": float("nan"),
            "T_Max": float("nan"),
            "wind_speed_weekly_mean_ms": float("nan"),
        }
    return {
        "rainfall_total_week_mm": float(week["PRECTOTCORR"].sum()),
        "rainfall_weekly_mean_mm_day": float(week["PRECTOTCORR"].mean()),
        "relative_humidity_weekly_mean_pct": float(week["RH2M"].mean()),
        "solar_radiation_weekly_mean_kwh_m2_day": float(week["ALLSKY_SFC_SW_DWN"].mean()),
        "T_Min": float(week["T2M_MIN"].min()),
        "T_Max": float(week["T2M_MAX"].max()),
        "wind_speed_weekly_mean_ms": float(week["WS10M"].mean()),
    }


def build_merged_long(
    rice: pd.DataFrame,
    calendar: pd.DataFrame,
    cache_dir: Path,
    sleep_s: float,
) -> pd.DataFrame:
    session = requests.Session()
    year_cache: dict[tuple, pd.DataFrame] = {}
    rows: list[dict] = []

    years_in_calendar = set(calendar["calendar_year"].unique())
    missing_years = set(rice["sow_year"].unique()) - years_in_calendar
    if missing_years:
        raise ValueError(f"Week calendar missing years: {sorted(missing_years)}")

    total_fetches = rice.groupby(["Latitude", "Longitude", "sow_year"]).ngroups
    fetched = 0

    for _, farm in rice.iterrows():
        lat = float(farm["Latitude"])
        lon = float(farm["Longitude"])
        year = int(farm["sow_year"])
        cache_tuple = (round(lat, 6), round(lon, 6), year)

        if cache_tuple not in year_cache:
            year_cache[cache_tuple] = fetch_nasa_daily_year(
                lat, lon, year, cache_dir, session, sleep_s
            )
            fetched += 1
            if fetched % 25 == 0:
                print(f"Fetched {fetched}/{total_fetches} location-year datasets...")

        daily = year_cache[cache_tuple]
        year_weeks = calendar[calendar["calendar_year"] == year]

        farm_fields = {col: farm[col] for col in RICE_COLUMNS_KEEP if col in farm.index}
        farm_fields["record_id"] = farm["record_id"]
        farm_fields["season_days"] = farm["season_days"]
        farm_fields["calendar_year"] = year

        for _, week in year_weeks.iterrows():
            weather = aggregate_week(daily, week["week_start"], week["week_end"])
            row = {
                **farm_fields,
                "ind_week": int(week["ind_week"]),
                "week_start": week["week_start"].strftime("%Y-%m-%d"),
                "week_end": week["week_end"].strftime("%Y-%m-%d"),
                **weather,
            }
            rows.append(row)

    merged = pd.DataFrame(rows)

    front = [
        "record_id",
        "Longitude",
        "Latitude",
        "Crop Sowing Time",
        "Crop Harvesting Time",
        "Yield Level (t/ha)",
        "season_days",
        "calendar_year",
        "ind_week",
        "week_start",
        "week_end",
    ]
    weather = [
        "rainfall_total_week_mm",
        "rainfall_weekly_mean_mm_day",
        "relative_humidity_weekly_mean_pct",
        "solar_radiation_weekly_mean_kwh_m2_day",
        "T_Min",
        "T_Max",
        "wind_speed_weekly_mean_ms",
    ]
    other_farm = [c for c in RICE_COLUMNS_KEEP if c not in front and c in merged.columns]
    ordered = front + other_farm + weather
    remaining = [c for c in merged.columns if c not in ordered]
    return merged[ordered + remaining]


def main() -> None:
    parser = argparse.ArgumentParser(description="Build 52-week merged long CSV.")
    parser.add_argument(
        "--rice",
        default="/Users/nagarro/Documents/Project/csv files/RICE_UP_BI_PU_HR_USED_FINAL.xlsx",
    )
    parser.add_argument(
        "--nasa-calendar",
        default="/Users/nagarro/Documents/Project/csv files/nasapower 30 yrs.xlsx",
    )
    parser.add_argument(
        "--output",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_merged.csv",
    )
    parser.add_argument(
        "--cache-dir",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/nasa_cache/yearly",
    )
    parser.add_argument("--sleep", type=float, default=0.3)
    args = parser.parse_args()

    rice = load_rice_data(Path(args.rice))
    calendar = load_week_calendar(Path(args.nasa_calendar))
    print(f"Rice farms: {len(rice)}")
    print(f"Calendar weeks per year: {MAX_WEEKS}")
    print(f"Expected output rows: {len(rice) * MAX_WEEKS}")

    merged = build_merged_long(rice, calendar, Path(args.cache_dir), args.sleep)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    merged.to_csv(out, index=False)

    print(f"\nSaved {len(merged)} rows, {len(merged.columns)} columns")
    print(f"ind_week range: {merged['ind_week'].min()} – {merged['ind_week'].max()}")
    print(f"Rows per farm: {sorted(merged.groupby('record_id').size().unique())}")
    print(f"Output: {out}")


if __name__ == "__main__":
    main()
