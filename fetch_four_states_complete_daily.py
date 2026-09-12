#!/usr/bin/env python3
"""Fetch NASA POWER daily weather for the 40 merged-CSV districts, 1995–2025."""

from __future__ import annotations

import csv
import json
import time
from collections import OrderedDict
from datetime import datetime
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent
MERGED = ROOT / "exports" / "Extract_Four_States_Tmax_Tmin_pr_1995-2025_merged.csv"
CACHE = ROOT / "exports" / "nasa_daily_cache"
OUT = ROOT / "exports" / "Extract_Four_States_1995-2025_complete_daily.csv"

NASA_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"
PARAMS = "T2M_MAX,T2M_MIN,T2M,PRECTOTCORR,RH2M,WS10M,ALLSKY_SFC_SW_DWN"
START = "19950101"
END = "20251231"
FILL = -999.0


def fmt(value) -> str:
    if value is None:
        return ""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return ""
    if number <= FILL + 0.1:
        return ""
    if number == int(number) and abs(number) < 1e12:
        return str(int(number))
    return f"{number:.4f}".rstrip("0").rstrip(".")


def load_districts() -> list[dict]:
    seen: OrderedDict[str, dict] = OrderedDict()
    with MERGED.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            code = row["Dist_Code"]
            if code not in seen:
                seen[code] = row
    return list(seen.values())


def fetch_point(session: requests.Session, lat: str, lon: str, cache_file: Path) -> dict:
    if cache_file.exists():
        return json.loads(cache_file.read_text(encoding="utf-8"))
    response = session.get(
        NASA_URL,
        params={
            "start": START,
            "end": END,
            "latitude": lat,
            "longitude": lon,
            "community": "AG",
            "parameters": PARAMS,
            "format": "JSON",
            "units": "metric",
            "header": "true",
            "time-standard": "utc",
        },
        timeout=180,
    )
    response.raise_for_status()
    payload = response.json()
    cache_file.write_text(json.dumps(payload), encoding="utf-8")
    return payload


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    districts = load_districts()
    session = requests.Session()
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
        "humidity_pct",
        "solar_kwh_m2_day",
        "wind_speed_ms",
    ]

    rows_written = 0
    with OUT.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        for i, district in enumerate(districts, start=1):
            cache_file = CACHE / f"{district['Dist_Code']}_{district['lat']}_{district['lon']}.json"
            cached = cache_file.exists()
            print(f"[{i}/{len(districts)}] {district['STATE']} {district['District']}", flush=True)
            payload = fetch_point(session, district["lat"], district["lon"], cache_file)
            if not cached:
                time.sleep(0.6)
            parameter = payload["properties"]["parameter"]
            dates = sorted(parameter["T2M_MAX"].keys())
            for key in dates:
                dt = datetime.strptime(key, "%Y%m%d")
                writer.writerow(
                    [
                        district["STATE"],
                        district["District"],
                        district["Dist_Code"],
                        district["TEHSIL"],
                        district["lat"],
                        district["lon"],
                        dt.strftime("%Y-%m-%d"),
                        dt.year,
                        dt.month,
                        dt.day,
                        int(dt.strftime("%j")),
                        fmt(parameter["T2M_MAX"][key]),
                        fmt(parameter["T2M_MIN"][key]),
                        fmt(parameter["T2M"][key]),
                        fmt(parameter["PRECTOTCORR"][key]),
                        fmt(parameter["RH2M"][key]),
                        fmt(parameter["ALLSKY_SFC_SW_DWN"][key]),
                        fmt(parameter["WS10M"][key]),
                    ]
                )
                rows_written += 1

    print(f"wrote {rows_written} rows -> {OUT}", flush=True)


if __name__ == "__main__":
    main()
