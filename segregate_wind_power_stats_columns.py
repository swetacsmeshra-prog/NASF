#!/usr/bin/env python3
"""Export weeks 23–42 STDV/R² columns in segregated metric blocks.

Reads rice_nasapower_weekly_wind_power_segregated123 and writes:
  1) stats-only CSV — farm metadata + 3 metric blocks
  2) full segregated CSV — original weather columns + 3 metric blocks after wind_speed
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

import pandas as pd

from segregate_weekly_columns import FARM_ID_COLS

WEEK_START = 23
WEEK_END = 42

STAT_METRICS = ("t_min_stdv", "t_max_stdv", "tmin_tmax_r2")


def stat_block(metric: str) -> list[str]:
    return [f"week{w:02d}_{metric}" for w in range(WEEK_START, WEEK_END + 1)]


def segregated_stat_columns() -> list[str]:
    cols: list[str] = []
    for metric in STAT_METRICS:
        cols.extend(stat_block(metric))
    return cols


def is_stat_column(name: str) -> bool:
    return bool(re.match(r"week\d{2}_(t_min_stdv|t_max_stdv|tmin_tmax_r2)$", str(name)))


def load_source(path: Path) -> pd.DataFrame:
    if path.suffix.lower() in {".xlsx", ".xls"}:
        try:
            return pd.read_excel(path, sheet_name="Segregated_52Weeks")
        except ValueError:
            return pd.read_excel(path)
    return pd.read_csv(path)


def build_stats_only(df: pd.DataFrame) -> pd.DataFrame:
    farm_cols = [c for c in FARM_ID_COLS if c in df.columns]
    stat_cols = [c for c in segregated_stat_columns() if c in df.columns]
    missing = set(segregated_stat_columns()) - set(stat_cols)
    if missing:
        raise KeyError(f"Missing stat columns in source: {sorted(missing)[:5]} ...")
    return df[farm_cols + stat_cols]


def build_full_segregated(df: pd.DataFrame) -> pd.DataFrame:
    stat_cols = set(segregated_stat_columns())
    base_cols = [c for c in df.columns if c not in stat_cols and not is_stat_column(c)]
    return df[base_cols + segregated_stat_columns()]


def main() -> None:
    parser = argparse.ArgumentParser(description="Segregate wind-power STDV/R² columns by metric.")
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.xlsx",
    )
    parser.add_argument(
        "--output-stats-csv",
        default="exports/rice_nasapower_weekly_wind_power_stats_segregated.csv",
    )
    parser.add_argument(
        "--output-full-csv",
        default="exports/rice_nasapower_weekly_wind_power_segregated123_segregated.csv",
    )
    parser.add_argument(
        "--output-full-excel",
        default="exports/rice_nasapower_weekly_wind_power_segregated123_segregated.xlsx",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    df = load_source(input_path)
    print(f"Loaded {len(df)} rows, {len(df.columns)} columns from {input_path}")

    stats_only = build_stats_only(df)
    full_seg = build_full_segregated(df)

    stats_path = Path(args.output_stats_csv)
    full_csv_path = Path(args.output_full_csv)
    full_xlsx_path = Path(args.output_full_excel)
    stats_path.parent.mkdir(parents=True, exist_ok=True)

    stats_only.to_csv(stats_path, index=False)
    full_seg.to_csv(full_csv_path, index=False)
    full_seg.to_excel(full_xlsx_path, sheet_name="Segregated_52Weeks", index=False)

    print(f"\nStats-only CSV ({len(stats_only.columns)} cols): {stats_path}")
    print("  Blocks:")
    for metric in STAT_METRICS:
        block = stat_block(metric)
        print(f"    week{WEEK_START:02d}_{metric} ... week{WEEK_END:02d}_{metric}")

    print(f"\nFull segregated CSV ({len(full_seg.columns)} cols): {full_csv_path}")
    print(f"Full segregated Excel: {full_xlsx_path}")
    print("\nColumn order tail:")
    print("  " + " → ".join(list(full_seg.columns)[-8:]))


if __name__ == "__main__":
    main()
