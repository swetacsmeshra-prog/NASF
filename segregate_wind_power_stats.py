#!/usr/bin/env python3
"""Export weeks 23–42 STDV / R² columns in segregated metric blocks."""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

WEEK_START = 23
WEEK_END = 42

FARM_COLS = [
    "record_id",
    "Longitude",
    "Latitude",
    "Crop Sowing Time",
    "Crop Harvesting Time",
    "Yield Level (t/ha)",
    "season_days",
    "calendar_year",
]

METRIC_BLOCKS = [
    "t_min_stdv",
    "t_max_stdv",
    "tmin_tmax_r2",
]


def block_columns(metric: str) -> list[str]:
    return [f"week{w:02d}_{metric}" for w in range(WEEK_START, WEEK_END + 1)]


def segregate_stats(df: pd.DataFrame) -> pd.DataFrame:
    farm_cols = [c for c in FARM_COLS if c in df.columns]
    stat_cols = [col for metric in METRIC_BLOCKS for col in block_columns(metric)]
    missing = [c for c in stat_cols if c not in df.columns]
    if missing:
        raise KeyError(f"Missing expected stat columns: {missing[:5]} ... ({len(missing)} total)")
    return df[farm_cols + stat_cols]


def main() -> None:
    parser = argparse.ArgumentParser(description="Segregate wind-power week 23–42 stat columns.")
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.xlsx",
    )
    parser.add_argument(
        "--output-csv",
        default="exports/rice_nasapower_weekly_wind_power_stats_segregated.csv",
    )
    parser.add_argument(
        "--output-excel",
        default="exports/rice_nasapower_weekly_wind_power_stats_segregated.xlsx",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    if input_path.suffix.lower() in {".xlsx", ".xls"}:
        try:
            df = pd.read_excel(input_path, sheet_name="Segregated_52Weeks")
        except ValueError:
            df = pd.read_excel(input_path)
    else:
        df = pd.read_csv(input_path)

    out = segregate_stats(df)
    csv_path = Path(args.output_csv)
    xlsx_path = Path(args.output_excel)
    csv_path.parent.mkdir(parents=True, exist_ok=True)

    out.to_csv(csv_path, index=False)
    out.to_excel(xlsx_path, sheet_name="Stats_23_42", index=False)

    print(f"Rows: {len(out)}")
    print(f"Columns: {len(out.columns)} ({len([c for c in FARM_COLS if c in out.columns])} farm + {len(out.columns) - len([c for c in FARM_COLS if c in out.columns])} stats)")
    print(f"CSV:   {csv_path.resolve()}")
    print(f"Excel: {xlsx_path.resolve()}")
    print("\nColumn blocks:")
    for metric in METRIC_BLOCKS:
        cols = block_columns(metric)
        print(f"  {cols[0]} ... {cols[-1]}")


if __name__ == "__main__":
    main()
