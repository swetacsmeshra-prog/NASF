#!/usr/bin/env python3
"""Train 3-input climate ANN (Rain, Tmin, Tmax) for on-device Android inference."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler

CLIMATE3_FEATURES = [
    "season_rainfall_mm",
    "season_t_min_mean",
    "season_t_max_mean",
]


def load_data(path: Path) -> pd.DataFrame:
    if path.suffix.lower() in {".xlsx", ".xls"}:
        try:
            df = pd.read_excel(path, sheet_name="Segregated_52Weeks")
        except ValueError:
            df = pd.read_excel(path)
    else:
        df = pd.read_csv(path)
    df["Yield Level (t/ha)"] = pd.to_numeric(df["Yield Level (t/ha)"], errors="coerce")
    return df.dropna(subset=["Yield Level (t/ha)"])


def season_rainfall(row: pd.Series, start: int, end: int) -> float:
    total = 0.0
    for w in range(start, end + 1):
        col = f"week{w:02d}_rainfall_total"
        if col in row.index and pd.notna(row[col]):
            total += float(row[col])
    return total


def season_mean(row: pd.Series, metric: str, start: int, end: int) -> float:
    vals = []
    for w in range(start, end + 1):
        col = f"week{w:02d}_{metric}"
        if col in row.index and pd.notna(row[col]):
            vals.append(float(row[col]))
    return float(np.mean(vals)) if vals else np.nan


def build_climate3_frame(df: pd.DataFrame, start: int, end: int) -> pd.DataFrame:
    rows = []
    for _, row in df.iterrows():
        rows.append({
            "season_rainfall_mm": season_rainfall(row, start, end),
            "season_t_min_mean": season_mean(row, "t_min", start, end),
            "season_t_max_mean": season_mean(row, "t_max", start, end),
            "yield_t_ha": float(row["Yield Level (t/ha)"]),
        })
    out = pd.DataFrame(rows)
    for col in CLIMATE3_FEATURES:
        out[col] = out[col].fillna(out[col].median())
    return out


def validation_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict:
    residuals = y_true - y_pred
    rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
    return {
        "rmse_t_ha": round(rmse, 4),
        "rms_t_ha": round(rmse, 4),
        "stdv_residual_t_ha": round(float(np.std(residuals)), 4),
        "mae_t_ha": round(float(mean_absolute_error(y_true, y_pred)), 4),
        "r2": round(float(r2_score(y_true, y_pred)), 4),
    }


def export_ann(mlp: MLPRegressor) -> dict:
    return {
        "hidden_layer_sizes": list(mlp.hidden_layer_sizes),
        "activation": mlp.activation,
        "coefs": [[round(float(v), 8) for v in row.ravel()] for row in mlp.coefs_],
        "intercepts": [[round(float(v), 8) for v in row.ravel()] for row in mlp.intercepts_],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Train 3-input climate ANN (Rain/Tmin/Tmax).")
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.csv",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/ml/yield_models_climate3_ann.json",
    )
    parser.add_argument("--start", type=int, default=23)
    parser.add_argument("--end", type=int, default=44)
    args = parser.parse_args()

    df = load_data(Path(args.input))
    frame = build_climate3_frame(df, args.start, args.end)
    X = frame[CLIMATE3_FEATURES].values
    y = frame["yield_t_ha"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    ann = MLPRegressor(
        hidden_layer_sizes=(5, 5),
        activation="relu",
        max_iter=1000,
        random_state=42,
    )
    ann.fit(X_train_s, y_train)
    ann_pred = ann.predict(X_test_s)
    ann_metrics = validation_metrics(y_test, ann_pred)

    payload = {
        "version": 1,
        "trained_on_farms": len(df),
        "crop_season_weeks": {"start": args.start, "end": args.end},
        "feature_names": CLIMATE3_FEATURES,
        "predictor_labels": {
            "season_rainfall_mm": "Rain (mm)",
            "season_t_min_mean": "T min (°C)",
            "season_t_max_mean": "T max (°C)",
        },
        "defaults": {
            "mean_yield_t_ha": round(float(y.mean()), 2),
            "median_season_rainfall_mm": round(float(frame["season_rainfall_mm"].median()), 2),
            "median_season_t_min_mean": round(float(frame["season_t_min_mean"].median()), 2),
            "median_season_t_max_mean": round(float(frame["season_t_max_mean"].median()), 2),
        },
        "ann": {
            "metrics": ann_metrics,
            "model": export_ann(ann),
            "scaler_mean": [round(float(v), 8) for v in scaler.mean_],
            "scaler_scale": [round(float(v), 8) for v in scaler.scale_],
        },
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        json.dump(payload, f, indent=2)

    print(f"Trained on {len(df)} farms, weeks {args.start}-{args.end}")
    print(f"ANN RMSE: {ann_metrics['rmse_t_ha']} t/ha, R²: {ann_metrics['r2']}")
    print(f"Saved: {out}")


if __name__ == "__main__":
    main()
