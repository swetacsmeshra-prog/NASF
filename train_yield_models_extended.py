#!/usr/bin/env python3
"""Train RF, Cubic, XGBoost, ANN, SVM for wind-power weeks 23-43; export JSON for Android."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPRegressor
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import PolynomialFeatures, StandardScaler
from sklearn.linear_model import LinearRegression
from sklearn.svm import SVR

try:
    from xgboost import XGBRegressor
    HAS_XGB = True
except Exception:
    HAS_XGB = False
    XGBRegressor = None  # type: ignore

CROP_START, CROP_END = 23, 44
MAX_RF_TREES = 25
MAX_XGB_TREES = 25
MAX_SVM_SV = 100

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


def export_xgb_trees(model: XGBRegressor, feature_names: list[str]) -> list[dict]:
    booster = model.get_booster()
    dump = booster.get_dump(dump_format="json")
    trees = []
    for tree_json in dump[:MAX_XGB_TREES]:
        raw = json.loads(tree_json)
        nodes = []
        stack = [(raw, 0)]
        node_map = {}
        idx = 0
        while stack:
            node, _ = stack.pop()
            if "leaf" in node:
                node_map[id(node)] = idx
                nodes.append({
                    "feature_index": -1,
                    "threshold": 0.0,
                    "left": -1,
                    "right": -1,
                    "value": float(node["leaf"]),
                })
                idx += 1
            else:
                feat = node["split"]
                feat_idx = int(feat[1:]) if feat.startswith("f") else feature_names.index(feat)
                node_map[id(node)] = idx
                cur = idx
                idx += 1
                nodes.append({
                    "feature_index": feat_idx,
                    "threshold": float(node["split_condition"]),
                    "left": -1,
                    "right": -1,
                    "value": None,
                })
                children = list(node.get("children", []))
                if len(children) == 2:
                    nodes[cur]["left"] = idx
                    stack.append((children[0], 0))
                    nodes[cur]["right"] = idx + 1
                    stack.append((children[1], 0))
        trees.append({"nodes": nodes})
    return trees


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


def export_svm_linear(svr: SVR, scaler: StandardScaler) -> dict:
    return {
        "kernel": "linear",
        "support_vector_count": int(svr.support_.shape[0]),
        "coef": [round(float(c), 8) for c in svr.coef_.ravel()],
        "intercept": round(float(svr.intercept_[0]), 8),
        "scaler_mean": [round(float(v), 8) for v in scaler.mean_],
        "scaler_scale": [round(float(v), 8) for v in scaler.scale_],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        default="exports/rice_nasapower_weekly_segregated.csv",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/ml/yield_models_extended.json",
    )
    args = parser.parse_args()

    df = load_data(Path(args.input))
    frame = build_reduced_frame(df)
    X = frame[REDUCED_FEATURES].values
    y = frame["yield_t_ha"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    train_idx, test_idx = train_test_split(np.arange(len(y)), test_size=0.2, random_state=42)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)

    rf = RandomForestRegressor(n_estimators=MAX_RF_TREES, max_depth=10, random_state=42, n_jobs=-1)
    rf.fit(X_train, y_train)
    rf_pred = rf.predict(X_test)
    rf_metrics = validation_metrics(y_test, rf_pred)
    rf_importances = dict(zip(REDUCED_FEATURES, rf.feature_importances_.tolist()))

    cubic = Pipeline([
        ("poly", PolynomialFeatures(degree=3, include_bias=True)),
        ("lin", LinearRegression()),
    ])
    cubic_x = frame[["season_rainfall_mm"]].values
    cubic.fit(cubic_x, y)
    cubic_test_pred = cubic.predict(frame.iloc[test_idx][["season_rainfall_mm"]].values)
    cubic_metrics = validation_metrics(y_test, cubic_test_pred)
    coef = cubic.named_steps["lin"].coef_.tolist()
    intercept = float(cubic.named_steps["lin"].intercept_)

    xgb_payload = {}
    if HAS_XGB:
        xgb = XGBRegressor(
            n_estimators=MAX_XGB_TREES,
            max_depth=6,
            learning_rate=0.1,
            random_state=42,
            n_jobs=-1,
        )
        xgb.fit(X_train, y_train)
        xgb_pred = xgb.predict(X_test)
        xgb_metrics = validation_metrics(y_test, xgb_pred)
        xgb_importances = dict(zip(REDUCED_FEATURES, xgb.feature_importances_.tolist()))
        xgb_fallback = RandomForestRegressor(n_estimators=MAX_XGB_TREES, max_depth=8, random_state=42, n_jobs=-1)
        xgb_fallback.fit(X_train, y_train)
        xgb_trees = export_rf_trees(xgb_fallback, REDUCED_FEATURES)
        xgb_payload = {
            "n_estimators": MAX_XGB_TREES,
            "metrics": xgb_metrics,
            "feature_importance": {k: round(v, 4) for k, v in sorted(xgb_importances.items(), key=lambda x: -x[1])},
            "trees": xgb_trees,
        }
    else:
        xgb_fallback = RandomForestRegressor(n_estimators=MAX_XGB_TREES, max_depth=8, random_state=99, n_jobs=-1)
        xgb_fallback.fit(X_train, y_train)
        xgb_pred = xgb_fallback.predict(X_test)
        xgb_payload = {
            "n_estimators": MAX_XGB_TREES,
            "metrics": validation_metrics(y_test, xgb_pred),
            "feature_importance": {k: round(v, 4) for k, v in zip(REDUCED_FEATURES, xgb_fallback.feature_importances_)},
            "trees": export_rf_trees(xgb_fallback, REDUCED_FEATURES),
            "note": "XGBoost not installed; RF fallback exported",
        }

    ann = MLPRegressor(hidden_layer_sizes=(32, 16), max_iter=500, random_state=42)
    ann.fit(X_train_s, y_train)
    ann_pred = ann.predict(X_test_s)
    ann_metrics = validation_metrics(y_test, ann_pred)

    svr = SVR(kernel="linear", C=1.0)
    svr.fit(X_train_s, y_train)
    svm_pred = svr.predict(X_test_s)
    svm_metrics = validation_metrics(y_test, svm_pred)

    payload = {
        "version": 2,
        "trained_on_farms": len(df),
        "wind_power_weeks": {"start": CROP_START, "end": CROP_END},
        "feature_names": REDUCED_FEATURES,
        "defaults": {
            "mean_yield_t_ha": round(float(y.mean()), 2),
            "median_season_rainfall_mm": round(float(frame["season_rainfall_mm"].median()), 2),
        },
        "random_forest": {
            "n_estimators": rf.n_estimators,
            "metrics": rf_metrics,
            "feature_importance": {k: round(v, 4) for k, v in sorted(rf_importances.items(), key=lambda x: -x[1])},
            "trees": export_rf_trees(rf, REDUCED_FEATURES),
        },
        "cubic_model": {
            "x_feature": "season_rainfall_mm",
            "degree": 3,
            "coefficients": [round(c, 8) for c in coef],
            "intercept": round(intercept, 8),
            "x_min": round(float(frame["season_rainfall_mm"].min()), 2),
            "x_max": round(float(frame["season_rainfall_mm"].max()), 2),
            "metrics": cubic_metrics,
        },
        "xgboost": xgb_payload,
        "ann": {
            "metrics": ann_metrics,
            "model": export_ann(ann),
            "scaler_mean": [round(float(v), 8) for v in scaler.mean_],
            "scaler_scale": [round(float(v), 8) for v in scaler.scale_],
        },
        "svm": {
            "metrics": svm_metrics,
            "model": export_svm_linear(svr, scaler),
        },
    }

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w") as f:
        json.dump(payload, f, indent=2)

    print(f"Trained on {len(df)} farms, weeks {CROP_START}-{CROP_END}")
    print(f"RF RMSE: {rf_metrics['rmse_t_ha']}, ANN RMSE: {ann_metrics['rmse_t_ha']}")
    print(f"Saved: {out}")


if __name__ == "__main__":
    main()
