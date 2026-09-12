#!/usr/bin/env python3
"""Pivot long weekly merged data to wide format with metrics segregated by type."""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

FARM_ID_COLS = [
    "record_id",
    "Longitude",
    "Latitude",
    "Crop Sowing Time",
    "Crop Harvesting Time",
    "Yield Level (t/ha)",
    "season_days",
    "calendar_year",
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

METRICS = [
    ("rainfall_total_week_mm", "rainfall_total"),
    ("rainfall_weekly_mean_mm_day", "rainfall_mean"),
    ("relative_humidity_weekly_mean_pct", "relative_humidity"),
    ("solar_radiation_weekly_mean_kwh_m2_day", "solar_radiation"),
    ("T_Min", "t_min"),
    ("T_Max", "t_max"),
    ("wind_speed_weekly_mean_ms", "wind_speed"),
]

MAX_WEEKS = 52


def pivot_segregated(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["ind_week"] = df["ind_week"].astype(int)

    farm_cols = [c for c in FARM_ID_COLS if c in df.columns]
    farms = df.groupby("record_id", as_index=False).first()[farm_cols]

    wide = farms.set_index("record_id")
    for source_col, short_name in METRICS:
        pivot = df.pivot(index="record_id", columns="ind_week", values=source_col)
        pivot.columns = [f"week{int(w):02d}_{short_name}" for w in pivot.columns]
        wide = wide.join(pivot)

    # Enforce column order: farm cols, then each metric block week01..week52
    ordered_metric_cols: list[str] = []
    for _, short_name in METRICS:
        for w in range(1, MAX_WEEKS + 1):
            col = f"week{w:02d}_{short_name}"
            if col in wide.columns:
                ordered_metric_cols.append(col)

    wide = wide.reset_index()
    return wide[farm_cols + ordered_metric_cols]


def main() -> None:
    parser = argparse.ArgumentParser(description="Segregate weekly weather columns by metric type.")
    parser.add_argument(
        "--input",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_merged.csv",
    )
    parser.add_argument(
        "--output-csv",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_segregated.csv",
    )
    parser.add_argument(
        "--output-excel",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_segregated.xlsx",
    )
    args = parser.parse_args()

    df = pd.read_csv(args.input)
    wide = pivot_segregated(df)

    csv_path = Path(args.output_csv)
    xlsx_path = Path(args.output_excel)
    csv_path.parent.mkdir(parents=True, exist_ok=True)

    wide.to_csv(csv_path, index=False)
    wide.to_excel(xlsx_path, sheet_name="Segregated_52Weeks", index=False)

    print(f"Farms: {len(wide)}")
    print(f"Columns: {len(wide.columns)} ({len(FARM_ID_COLS)} farm + {len(wide.columns) - len([c for c in FARM_ID_COLS if c in wide.columns])} weather)")
    print(f"CSV:   {csv_path}")
    print(f"Excel: {xlsx_path}")
    print("\nColumn layout (blocks of 52 weeks each):")
    for _, short in METRICS:
        print(f"  week01_{short} ... week52_{short}")


if __name__ == "__main__":
    main()
