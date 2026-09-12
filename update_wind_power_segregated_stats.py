#!/usr/bin/env python3
"""Add per-week STDV / R² columns after each wind_speed column (weeks 23–42).

Within the wind_speed block, inserts after each week23..week42 wind column:
  - week{NN}_t_min_stdv   — population STDV of t_min from week 23 through NN
  - week{NN}_t_max_stdv   — population STDV of t_max from week 23 through NN
  - week{NN}_tmin_tmax_r2 — R² of linear fit t_max ~ t_min over weeks 23..NN
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LinearRegression
from sklearn.metrics import r2_score

WEEK_START = 23
WEEK_END = 42

AGGREGATE_COLUMNS = [
    "week23_42_t_min_stdv",
    "week23_42_t_max_stdv",
    "week23_42_tmin_tmax_r2",
]

PER_WEEK_SUFFIXES = ("_t_min_stdv", "_t_max_stdv", "_tmin_tmax_r2")


def per_week_stat_columns() -> list[str]:
    cols: list[str] = []
    for w in range(WEEK_START, WEEK_END + 1):
        prefix = f"week{w:02d}"
        cols.extend([f"{prefix}{s}" for s in PER_WEEK_SUFFIXES])
    return cols


def strip_existing_stats(df: pd.DataFrame) -> pd.DataFrame:
    drop = [c for c in df.columns if c in AGGREGATE_COLUMNS]
    drop += [
        c for c in df.columns
        if re.match(r"week\d{2}_(t_min_stdv|t_max_stdv|tmin_tmax_r2)$", str(c))
    ]
    if drop:
        df = df.drop(columns=drop)
    return df


def population_std_row(values: np.ndarray) -> float:
    valid = values[~np.isnan(values)]
    if valid.size < 2:
        return float("nan")
    return float(np.std(valid, ddof=0))


def expanding_std_matrix(week_data: np.ndarray) -> np.ndarray:
    """week_data shape (n_rows, n_weeks) — expanding std from week index 0 onward."""
    n_rows, n_weeks = week_data.shape
    out = np.full((n_rows, n_weeks), np.nan)
    for end in range(1, n_weeks):
        window = week_data[:, : end + 1]
        for i in range(n_rows):
            out[i, end] = population_std_row(window[i])
    return out


def expanding_r2_matrix(tmin: np.ndarray, tmax: np.ndarray) -> np.ndarray:
    n_rows, n_weeks = tmin.shape
    out = np.full((n_rows, n_weeks), np.nan)
    for end in range(1, n_weeks):
        for i in range(n_rows):
            x = tmin[i, : end + 1]
            y = tmax[i, : end + 1]
            mask = ~(np.isnan(x) | np.isnan(y))
            xv, yv = x[mask], y[mask]
            if xv.size < 2:
                continue
            model = LinearRegression()
            model.fit(xv.reshape(-1, 1), yv)
            out[i, end] = float(r2_score(yv, model.predict(xv.reshape(-1, 1))))
    return out


def compute_per_week_stats(df: pd.DataFrame) -> pd.DataFrame:
    out = strip_existing_stats(df.copy())
    weeks = list(range(WEEK_START, WEEK_END + 1))

    tmin_cols = [f"week{w:02d}_t_min" for w in weeks]
    tmax_cols = [f"week{w:02d}_t_max" for w in weeks]
    tmin = out[tmin_cols].to_numpy(dtype=float)
    tmax = out[tmax_cols].to_numpy(dtype=float)

    tmin_std = expanding_std_matrix(tmin)
    tmax_std = expanding_std_matrix(tmax)
    r2 = expanding_r2_matrix(tmin, tmax)

    for j, w in enumerate(weeks):
        prefix = f"week{w:02d}"
        out[f"{prefix}_t_min_stdv"] = tmin_std[:, j]
        out[f"{prefix}_t_max_stdv"] = tmax_std[:, j]
        out[f"{prefix}_tmin_tmax_r2"] = r2[:, j]

    return reorder_columns(out)


def reorder_columns(df: pd.DataFrame) -> pd.DataFrame:
    cols = list(df.columns)
    wind_cols = [c for c in cols if re.match(r"week\d{2}_wind_speed$", str(c))]
    if not wind_cols:
        raise KeyError("No week##_wind_speed columns found")

    wind_cols_sorted = sorted(wind_cols, key=lambda c: int(re.search(r"week(\d+)", c).group(1)))
    stat_set = set(per_week_stat_columns())

    first_wind_idx = cols.index(wind_cols_sorted[0])
    prefix_cols = [c for c in cols[:first_wind_idx] if c not in stat_set]

    ordered_wind_block: list[str] = []
    for wind_col in wind_cols_sorted:
        ordered_wind_block.append(wind_col)
        week_num = int(re.search(r"week(\d+)", wind_col).group(1))
        if WEEK_START <= week_num <= WEEK_END:
            p = f"week{week_num:02d}"
            ordered_wind_block.extend([
                f"{p}_t_min_stdv",
                f"{p}_t_max_stdv",
                f"{p}_tmin_tmax_r2",
            ])

    return df[prefix_cols + ordered_wind_block]


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Insert per-week t_min/t_max STDV and R² after wind columns (weeks 23–42)."
    )
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.xlsx",
    )
    parser.add_argument(
        "--output-csv",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.csv",
    )
    parser.add_argument(
        "--output-excel",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.xlsx",
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

    print(f"Loaded {len(df)} rows, {len(df.columns)} columns from {input_path}")
    updated = compute_per_week_stats(df)
    added = len(per_week_stat_columns())
    print(f"Updated columns: {len(updated.columns)} (+{added} interleaved in wind block)")

    csv_path = Path(args.output_csv)
    xlsx_path = Path(args.output_excel)
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    updated.to_csv(csv_path, index=False)
    updated.to_excel(xlsx_path, sheet_name="Segregated_52Weeks", index=False)

    print(f"CSV:   {csv_path}")
    print(f"Excel: {xlsx_path}")

    sample_cols = [
        "week23_wind_speed", "week23_t_min_stdv", "week23_t_max_stdv", "week23_tmin_tmax_r2",
        "week24_wind_speed", "week24_t_min_stdv", "week24_t_max_stdv", "week24_tmin_tmax_r2",
        "week42_wind_speed", "week42_t_min_stdv", "week42_t_max_stdv", "week42_tmin_tmax_r2",
    ]
    print("\nSample columns (row 1):")
    print(updated.loc[0, sample_cols].to_string())
    print("\nweek42 summary:")
    w42 = ["week42_t_min_stdv", "week42_t_max_stdv", "week42_tmin_tmax_r2"]
    print(updated[w42].describe().round(4).to_string())


if __name__ == "__main__":
    main()
