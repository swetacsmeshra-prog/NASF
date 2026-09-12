#!/usr/bin/env python3
"""Train 6-input climate ANN from the merged district climatology CSV.

Each rice farm is assigned the nearest of 40 district points. Features are that
district's full-year (366-day) stats from the merged Tmax/Tmin/rainfall file:
rainfall sum, Tmin mean, Tmax mean, and the three daily 30-yr std columns averaged.
Target is farm yield (t/ha).
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler

FEATURES = [
    "annual_rainfall_mm",
    "annual_tmin_mean",
    "annual_tmax_mean",
    "annual_pr_std",
    "annual_tmin_std",
    "annual_tmax_std",
]


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def district_annual_stats(merged: pd.DataFrame) -> pd.DataFrame:
    g = merged.groupby(["STATE", "District", "Dist_Code", "TEHSIL", "lat", "lon"], dropna=False)
    out = g.agg(
        annual_rainfall_mm=("IMD_pr_Mean", "sum"),
        annual_tmin_mean=("Tmin_Mean", "mean"),
        annual_tmax_mean=("Tmax_mean", "mean"),
        annual_pr_std=("IMD_pr_std", "mean"),
        annual_tmin_std=("Tmin_Std", "mean"),
        annual_tmax_std=("Tmax_Std", "mean"),
        n_days=("date", "count"),
    ).reset_index()
    return out


def nearest_district(lat: float, lon: float, districts: pd.DataFrame) -> pd.Series:
    dists = [
        haversine_km(lat, lon, float(r.lat), float(r.lon))
        for r in districts.itertuples(index=False)
    ]
    return districts.iloc[int(np.argmin(dists))]


def load_farms(path: Path) -> pd.DataFrame:
    if path.suffix.lower() in {".xlsx", ".xls"}:
        try:
            df = pd.read_excel(path, sheet_name="Segregated_52Weeks")
        except ValueError:
            df = pd.read_excel(path)
    else:
        df = pd.read_csv(path)
    df["Yield Level (t/ha)"] = pd.to_numeric(df["Yield Level (t/ha)"], errors="coerce")
    df["Latitude"] = pd.to_numeric(df["Latitude"], errors="coerce")
    df["Longitude"] = pd.to_numeric(df["Longitude"], errors="coerce")
    return df.dropna(subset=["Yield Level (t/ha)", "Latitude", "Longitude"])


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
    parser = argparse.ArgumentParser(description="Train IMD merged-CSV 6-feature climate ANN.")
    parser.add_argument(
        "--merged",
        default="exports/Extract_Four_States_Tmax_Tmin_pr_1995-2025_merged.csv",
    )
    parser.add_argument(
        "--farms",
        default="exports/rice_nasapower_weekly_wind_power_segregated123.csv",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/ml/yield_models_imd_merged_ann.json",
    )
    args = parser.parse_args()

    merged = pd.read_csv(args.merged)
    districts = district_annual_stats(merged)
    farms = load_farms(Path(args.farms))

    rows = []
    for _, farm in farms.iterrows():
        d = nearest_district(float(farm["Latitude"]), float(farm["Longitude"]), districts)
        rows.append({
            **{f: float(d[f]) for f in FEATURES},
            "yield_t_ha": float(farm["Yield Level (t/ha)"]),
            "dist_code": d["Dist_Code"],
        })
    frame = pd.DataFrame(rows)
    print("farms", len(frame), "unique districts used", frame["dist_code"].nunique())
    print(frame[FEATURES].describe().round(2).to_string())

    X = frame[FEATURES].values
    y = frame["yield_t_ha"].values
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    ann = MLPRegressor(
        hidden_layer_sizes=(8, 5),
        activation="relu",
        max_iter=2000,
        random_state=42,
    )
    ann.fit(X_train_s, y_train)
    metrics = validation_metrics(y_test, ann.predict(X_test_s))

    payload = {
        "version": 1,
        "trained_on_farms": int(len(frame)),
        "season": "full_year_366_days",
        "feature_names": FEATURES,
        "predictor_labels": {
            "annual_rainfall_mm": "Rain (mm, annual sum)",
            "annual_tmin_mean": "T min (°C, annual mean)",
            "annual_tmax_mean": "T max (°C, annual mean)",
            "annual_pr_std": "Rain std (mm/day)",
            "annual_tmin_std": "T min std (°C)",
            "annual_tmax_std": "T max std (°C)",
        },
        "defaults": {
            "mean_yield_t_ha": round(float(y.mean()), 2),
            **{f"median_{name}": round(float(frame[name].median()), 4) for name in FEATURES},
        },
        "ann": {
            "metrics": metrics,
            "model": export_ann(ann),
            "scaler_mean": [round(float(v), 8) for v in scaler.mean_],
            "scaler_scale": [round(float(v), 8) for v in scaler.scale_],
        },
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        json.dump(payload, f, indent=2)

    print(f"ANN RMSE: {metrics['rmse_t_ha']} t/ha, MAE: {metrics['mae_t_ha']}, R²: {metrics['r2']}")
    print(f"Saved: {out}")


if __name__ == "__main__":
    main()
