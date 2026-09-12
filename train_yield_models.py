#!/usr/bin/env python3
"""Train Random Forest and cubic yield models; export JSON for Android assets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import PolynomialFeatures
from sklearn.linear_model import LinearRegression
from sklearn.pipeline import Pipeline

MAX_WEEKS = 52
CROP_START, CROP_END = 23, 44

SOIL_COLS = {
    "pH (1:2)": "ph",
    "OC %": "oc_pct",
    "Major-nutrient Avail N. (Kg/ha)": "n_kg_ha",
    "Major-nutrient P(kg/ha)": "p_kg_ha",
    "Major-nutrient Avail. K (kg/ha)": "k_kg_ha",
    "EC (dS/m)": "ec_ds_m",
}

REDUCED_FEATURES = [
    "season_rainfall_mm",
    "season_t_max_mean",
    "season_humidity_mean",
    "season_solar_mean",
    "season_wind_mean",
    "ph",
    "oc_pct",
    "n_kg_ha",
    "p_kg_ha",
    "k_kg_ha",
    "ec_ds_m",
]


def load_data(path: Path) -> pd.DataFrame:
    if path.suffix.lower() in {".xlsx", ".xls"}:
        try:
            df = pd.read_excel(path, sheet_name="Cleaned_Data")
        except ValueError:
            df = pd.read_excel(path)
    else:
        df = pd.read_csv(path)
    df["Yield Level (t/ha)"] = pd.to_numeric(df["Yield Level (t/ha)"], errors="coerce")
    return df.dropna(subset=["Yield Level (t/ha)"])


def season_rainfall(row: pd.Series) -> float:
    total = 0.0
    for w in range(CROP_START, CROP_END + 1):
        col = f"week{w:02d}_rainfall_total"
        if col in row.index and pd.notna(row[col]):
            total += float(row[col])
    return total


def season_mean(row: pd.Series, metric: str) -> float:
    vals = []
    for w in range(CROP_START, CROP_END + 1):
        col = f"week{w:02d}_{metric}"
        if col in row.index and pd.notna(row[col]):
            vals.append(float(row[col]))
    return float(np.mean(vals)) if vals else np.nan


def build_reduced_frame(df: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for _, row in df.iterrows():
        item = {
            "season_rainfall_mm": season_rainfall(row),
            "season_t_max_mean": season_mean(row, "t_max"),
            "season_humidity_mean": season_mean(row, "relative_humidity"),
            "season_solar_mean": season_mean(row, "solar_radiation"),
            "season_wind_mean": season_mean(row, "wind_speed"),
            "yield_t_ha": float(row["Yield Level (t/ha)"]),
        }
        for src, dst in SOIL_COLS.items():
            item[dst] = float(row[src]) if src in row.index and pd.notna(row[src]) else np.nan
        rows.append(item)
    out = pd.DataFrame(rows)
    for col in REDUCED_FEATURES:
        if col in out.columns:
            out[col] = out[col].fillna(out[col].median())
    return out


def export_rf_trees(rf: RandomForestRegressor, feature_names: list[str]) -> list[dict]:
    """Export a simplified tree structure for on-device inference."""
    trees = []
    for est in rf.estimators_:
        tree = est.tree_
        nodes = []
        for i in range(tree.node_count):
            if tree.feature[i] >= 0:
                nodes.append({
                    "feature_index": int(tree.feature[i]),
                    "threshold": float(tree.threshold[i]),
                    "left": int(tree.children_left[i]),
                    "right": int(tree.children_right[i]),
                    "value": None,
                })
            else:
                nodes.append({
                    "feature_index": -1,
                    "threshold": 0.0,
                    "left": -1,
                    "right": -1,
                    "value": float(tree.value[i][0][0]),
                })
        trees.append({"nodes": nodes})
    return trees


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_segregated_outlier_fixed.xlsx",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/ml/yield_models.json",
    )
    args = parser.parse_args()

    df = load_data(Path(args.input))
    frame = build_reduced_frame(df)
    X = frame[REDUCED_FEATURES].values
    y = frame["yield_t_ha"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    rf = RandomForestRegressor(n_estimators=25, max_depth=10, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)
    rf_pred = rf.predict(X_test)
    rf_mae = mean_absolute_error(y_test, rf_pred)
    rf_r2 = r2_score(y_test, rf_pred)

    cubic = Pipeline([
        ("poly", PolynomialFeatures(degree=3, include_bias=True)),
        ("lin", LinearRegression()),
    ])
    cubic_x = frame[["season_rainfall_mm"]].values
    cubic.fit(cubic_x, y)
    coef = cubic.named_steps["lin"].coef_.tolist()
    intercept = float(cubic.named_steps["lin"].intercept_)

    importances = dict(zip(REDUCED_FEATURES, rf.feature_importances_.tolist()))

    payload = {
        "version": 1,
        "trained_on_farms": len(df),
        "crop_season_weeks": {"start": CROP_START, "end": CROP_END},
        "feature_names": REDUCED_FEATURES,
        "random_forest": {
            "n_estimators": rf.n_estimators,
            "mae_t_ha": round(rf_mae, 3),
            "r2": round(rf_r2, 3),
            "feature_importance": {k: round(v, 4) for k, v in sorted(importances.items(), key=lambda x: -x[1])},
            "trees": export_rf_trees(rf, REDUCED_FEATURES),
        },
        "cubic_model": {
            "x_feature": "season_rainfall_mm",
            "degree": 3,
            "coefficients": [round(c, 8) for c in coef],
            "intercept": round(intercept, 8),
            "x_min": round(float(frame["season_rainfall_mm"].min()), 2),
            "x_max": round(float(frame["season_rainfall_mm"].max()), 2),
        },
        "defaults": {
            "mean_yield_t_ha": round(float(y.mean()), 2),
            "median_season_rainfall_mm": round(float(frame["season_rainfall_mm"].median()), 2),
        },
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        json.dump(payload, f, indent=2)

    print(f"Trained on {len(df)} farms")
    print(f"RF MAE: {rf_mae:.3f} t/ha, R²: {rf_r2:.3f}")
    print(f"Saved: {out}")


if __name__ == "__main__":
    main()
