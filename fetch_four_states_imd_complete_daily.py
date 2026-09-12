#!/usr/bin/env python3
"""Build 40-district IMD daily CSV (1995–2025) from IMD Pune gridded archives.

Source: https://imdpune.gov.in/cmpg/Griddata/
  rain 0.25°  |  tmax/tmin 1.0°
"""

from __future__ import annotations

import calendar
import csv
from collections import OrderedDict
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import requests

import imdlib as imd

ROOT = Path(__file__).resolve().parent
NASA_DAILY = ROOT / "exports" / "Extract_Four_States_1995-2025_complete_daily.csv"
CACHE = ROOT / "exports" / "imd_gridded_cache"
OUT = ROOT / "exports" / "Extract_Four_States_IMD_1995-2025_complete_daily.csv"
OUT_365 = ROOT / "exports" / "Extract_Four_States_IMD_365day_climatology.csv"

START_YEAR = 1995
END_YEAR = 2025

DOWNLOAD = {
    "rain": ("https://imdpune.gov.in/cmpg/Griddata/rainfall.php", "rain", ".grd"),
    "tmax": ("https://imdpune.gov.in/cmpg/Griddata/maxtemp.php", "maxtemp", ".GRD"),
    "tmin": ("https://imdpune.gov.in/cmpg/Griddata/mintemp.php", "mintemp", ".GRD"),
}


def expected_bytes(var_type: str, year: int) -> int:
    days = 366 if calendar.isleap(year) else 365
    if var_type == "rain":
        return days * 129 * 135 * 4
    return days * 31 * 31 * 4


def fmt(value, kind: str = "temp") -> str:
    if value is None:
        return ""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return ""
    if not np.isfinite(number):
        return ""
    if number <= -100:
        return ""
    if kind == "temp" and number >= 90:
        return ""
    if kind == "rain" and number >= 900:
        return ""
    if number == int(number):
        return str(int(number))
    return f"{number:.4f}".rstrip("0").rstrip(".")


def load_districts() -> list[dict]:
    seen: OrderedDict[str, dict] = OrderedDict()
    with NASA_DAILY.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            code = row["Dist_Code"]
            if code not in seen:
                seen[code] = {
                    "STATE": row["STATE"],
                    "District": row["District"],
                    "Dist_Code": row["Dist_Code"],
                    "TEHSIL": row["TEHSIL"],
                    "lat": row["lat"],
                    "lon": row["lon"],
                }
    return list(seen.values())


def download_year(session: requests.Session, var_type: str, year: int) -> Path:
    url, form_key, suffix = DOWNLOAD[var_type]
    folder = CACHE / var_type
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{year}{suffix}"
    want = expected_bytes(var_type, year)
    if path.exists() and path.stat().st_size == want:
        return path
    print(f"  download {var_type} {year}", flush=True)
    response = session.post(url, data={form_key: year}, timeout=300)
    response.raise_for_status()
    if len(response.content) != want:
        raise RuntimeError(
            f"{var_type} {year}: got {len(response.content)} bytes, expected {want}"
        )
    path.write_bytes(response.content)
    return path


def nearest_index(values: np.ndarray, target: float) -> int:
    return int(np.abs(values - target).argmin())


def series_at(imd_obj, lat: float, lon: float) -> np.ndarray:
    lat_i = nearest_index(imd_obj.lat_array, lat)
    lon_i = nearest_index(imd_obj.lon_array, lon)
    # IMD object stores (time, lon, lat)
    return np.asarray(imd_obj.data[:, lon_i, lat_i], dtype=float)


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    districts = load_districts()
    print(f"districts {len(districts)}", flush=True)
    session = requests.Session()
    years = list(range(START_YEAR, END_YEAR + 1))

    for var in ("rain", "tmax", "tmin"):
        print(f"Fetching {var} {START_YEAR}-{END_YEAR}", flush=True)
        for year in years:
            download_year(session, var, year)

    header = [
        "STATE",
        "District",
        "Dist_Code",
        "TEHSIL",
        "lat",
        "lon",
        "date",
        "year",
        "month",
        "day",
        "day_of_year",
        "Tmax",
        "Tmin",
        "Tmean",
        "rainfall_mm",
    ]
    rows = 0
    with OUT.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        for year in years:
            print(f"Extract {year}", flush=True)
            rain = imd.open_data("rain", year, year, "yearwise", str(CACHE))
            tmax = imd.open_data("tmax", year, year, "yearwise", str(CACHE))
            tmin = imd.open_data("tmin", year, year, "yearwise", str(CACHE))
            start = date(year, 1, 1)
            n_days = rain.no_days
            for district in districts:
                lat = float(district["lat"])
                lon = float(district["lon"])
                rain_s = series_at(rain, lat, lon)
                tmax_s = series_at(tmax, lat, lon)
                tmin_s = series_at(tmin, lat, lon)
                for i in range(n_days):
                    d = start + timedelta(days=i)
                    tmx = fmt(tmax_s[i], "temp")
                    tmn = fmt(tmin_s[i], "temp")
                    if tmx and tmn:
                        tmean = fmt((float(tmx) + float(tmn)) / 2.0, "temp")
                    else:
                        tmean = ""
                    writer.writerow(
                        [
                            district["STATE"],
                            district["District"],
                            district["Dist_Code"],
                            district["TEHSIL"],
                            district["lat"],
                            district["lon"],
                            d.isoformat(),
                            d.year,
                            d.month,
                            d.day,
                            int(d.strftime("%j")),
                            tmx,
                            tmn,
                            tmean,
                            fmt(rain_s[i], "rain"),
                        ]
                    )
                    rows += 1

    print(f"wrote {rows} rows -> {OUT}", flush=True)
    add_climatology(OUT)


def add_climatology(path: Path) -> None:
    """Attach 30-year calendar-day climatology mean and std to each daily row."""
    import pandas as pd

    df = pd.read_csv(path)
    metrics = ["Tmax", "Tmin", "Tmean", "rainfall_mm"]
    grouped = (
        df.groupby(["Dist_Code", "month", "day"], sort=False)[metrics]
        .agg(["mean", "std"])
    )
    grouped.columns = [f"{metric}_{stat}" for metric, stat in grouped.columns]
    grouped = grouped.rename(
        columns={
            "Tmax_mean": "Tmax_clim",
            "Tmax_std": "Tmax_std",
            "Tmin_mean": "Tmin_clim",
            "Tmin_std": "Tmin_std",
            "Tmean_mean": "Tmean_clim",
            "Tmean_std": "Tmean_std",
            "rainfall_mm_mean": "rainfall_mm_clim",
            "rainfall_mm_std": "rainfall_mm_std",
        }
    )
    out = df.merge(grouped, on=["Dist_Code", "month", "day"], how="left")
    ordered = [
        "STATE",
        "District",
        "Dist_Code",
        "TEHSIL",
        "lat",
        "lon",
        "date",
        "year",
        "month",
        "day",
        "day_of_year",
        "Tmax",
        "Tmax_clim",
        "Tmax_std",
        "Tmin",
        "Tmin_clim",
        "Tmin_std",
        "Tmean",
        "Tmean_clim",
        "Tmean_std",
        "rainfall_mm",
        "rainfall_mm_clim",
        "rainfall_mm_std",
    ]
    out = out[ordered]
    stat_cols = [
        "Tmax_clim",
        "Tmax_std",
        "Tmin_clim",
        "Tmin_std",
        "Tmean_clim",
        "Tmean_std",
        "rainfall_mm_clim",
        "rainfall_mm_std",
    ]
    out[stat_cols] = out[stat_cols].round(4)
    out.to_csv(path, index=False)
    print(f"added climatology columns -> {path}", flush=True)

    from datetime import date as date_cls

    meta = out[
        ["STATE", "District", "Dist_Code", "TEHSIL", "lat", "lon"]
    ].drop_duplicates("Dist_Code")
    day = (
        out.groupby(["Dist_Code", "month", "day"], sort=False)
        .agg(
            Years_Count=("Tmax", "size"),
            Tmax=("Tmax_clim", "first"),
            Tmax_std=("Tmax_std", "first"),
            Tmin=("Tmin_clim", "first"),
            Tmin_std=("Tmin_std", "first"),
            Tmean=("Tmean_clim", "first"),
            Tmean_std=("Tmean_std", "first"),
            rainfall_mm=("rainfall_mm_clim", "first"),
            rainfall_mm_std=("rainfall_mm_std", "first"),
        )
        .reset_index()
        .merge(meta, on="Dist_Code", how="left")
    )
    day["date"] = day.apply(
        lambda r: f"{int(r.month):02d}-{int(r.day):02d}", axis=1
    )
    day["day_of_year"] = day.apply(
        lambda r: int(date_cls(2024, int(r.month), int(r.day)).strftime("%j")),
        axis=1,
    )
    day = day.sort_values(["STATE", "District", "day_of_year"], kind="mergesort")
    day = day[
        [
            "STATE",
            "District",
            "Dist_Code",
            "TEHSIL",
            "lat",
            "lon",
            "date",
            "month",
            "day",
            "day_of_year",
            "Years_Count",
            "Tmax",
            "Tmax_std",
            "Tmin",
            "Tmin_std",
            "Tmean",
            "Tmean_std",
            "rainfall_mm",
            "rainfall_mm_std",
        ]
    ]
    day.to_csv(OUT_365, index=False)
    print(f"wrote 365-day climatology -> {OUT_365}", flush=True)


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "--climatology-only":
        add_climatology(OUT)
    else:
        main()
