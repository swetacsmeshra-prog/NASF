#!/usr/bin/env python3
"""
Detect outliers in segregated Excel, apply fixes, and export cleaned workbook.

Output sheets:
  - Cleaned_Data: farms after removing invalid outliers
  - Outliers_Removed: excluded rows with reasons
  - Outliers_Flagged: kept rows that are unusual but plausible
  - Fix_Summary: counts and rules applied
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

# Exclusion rules (removed from cleaned file)
RULES_REMOVE = [
    ("season_too_short", lambda df: df["season_days"] < 60, "Growing season under 60 days"),
    ("season_too_long", lambda df: df["season_days"] > 195, "Growing season over 195 days"),
    ("yield_too_low", lambda df: df["Yield Level (t/ha)"] < 2.5, "Yield below 2.5 t/ha"),
    ("yield_too_high", lambda df: df["Yield Level (t/ha)"] > 9.0, "Yield above 9.0 t/ha"),
    ("lat_out_of_range", lambda df: (df["Latitude"] < 8) | (df["Latitude"] > 32), "Latitude outside India range"),
    ("lon_out_of_range", lambda df: (df["Longitude"] < 74) | (df["Longitude"] > 88), "Longitude outside India range"),
]

# Flag rules (kept but marked for review)
RULES_FLAG = [
    ("season_short", lambda df: (df["season_days"] >= 60) & (df["season_days"] < 80), "Short season 60–79 days"),
    ("yield_high_iqr", lambda df: df["Yield Level (t/ha)"] > df["Yield Level (t/ha)"].quantile(0.75) + 1.5 * (df["Yield Level (t/ha)"].quantile(0.75) - df["Yield Level (t/ha)"].quantile(0.25)), "Yield above IQR upper bound"),
]


def check_weather_outliers(df: pd.DataFrame) -> pd.DataFrame:
    """Per-row weather issue flags."""
    issues: list[dict] = []
    for idx, row in df.iterrows():
        rid = row["record_id"]
        problems: list[str] = []

        for w in range(1, 53):
            mn, mx = f"week{w:02d}_t_min", f"week{w:02d}_t_max"
            if mn in df.columns and mx in df.columns:
                if pd.notna(row[mn]) and pd.notna(row[mx]) and row[mn] > row[mx]:
                    problems.append(f"week{w:02d}_t_min>t_max")

            rh = f"week{w:02d}_relative_humidity"
            if rh in df.columns and pd.notna(row[rh]) and (row[rh] < 0 or row[rh] > 100):
                problems.append(f"week{w:02d}_RH_out_of_range")

            rain = f"week{w:02d}_rainfall_total"
            if rain in df.columns and pd.notna(row[rain]) and row[rain] < 0:
                problems.append(f"week{w:02d}_negative_rain")

        if problems:
            issues.append({"record_id": rid, "weather_issues": "; ".join(problems[:5]) + ("..." if len(problems) > 5 else "")})

    return pd.DataFrame(issues)


def fix_weather_values(df: pd.DataFrame) -> pd.DataFrame:
    """Replace NASA fill values and fix impossible t_min/t_max pairs."""
    out = df.copy()
    weather_cols = [c for c in out.columns if c.startswith("week")]

    # NASA missing fill
    out[weather_cols] = out[weather_cols].replace(-999, pd.NA)

    for w in range(1, 53):
        mn, mx = f"week{w:02d}_t_min", f"week{w:02d}_t_max"
        if mn not in out.columns or mx not in out.columns:
            continue
        mask = out[mn].notna() & out[mx].notna() & (out[mn] > out[mx])
        # Swap when min > max (rare data glitch)
        out.loc[mask, [mn, mx]] = out.loc[mask, [mx, mn]].values

    return out


def build_outlier_workbook(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    df = fix_weather_values(df)

    remove_mask = pd.Series(False, index=df.index)
    removed_rows: list[pd.DataFrame] = []

    for rule_id, rule_fn, description in RULES_REMOVE:
        mask = rule_fn(df) & ~remove_mask
        if mask.any():
            chunk = df.loc[mask].copy()
            chunk["exclusion_reason"] = description
            chunk["rule_id"] = rule_id
            removed_rows.append(chunk)
            remove_mask |= mask

    removed = pd.concat(removed_rows, ignore_index=True) if removed_rows else pd.DataFrame()
    cleaned = df.loc[~remove_mask].copy()

    # Flag remaining unusual rows
    flag_mask = pd.Series(False, index=cleaned.index)
    flagged_parts: list[pd.DataFrame] = []

    for rule_id, rule_fn, description in RULES_FLAG:
        # Recompute IQR on cleaned for yield rule
        if rule_id == "yield_high_iqr":
            q1 = cleaned["Yield Level (t/ha)"].quantile(0.25)
            q3 = cleaned["Yield Level (t/ha)"].quantile(0.75)
            high = q3 + 1.5 * (q3 - q1)
            mask = (cleaned["Yield Level (t/ha)"] > high) & ~flag_mask
        else:
            mask = rule_fn(cleaned) & ~flag_mask

        if mask.any():
            chunk = cleaned.loc[mask, ["record_id", "Longitude", "Latitude", "Crop Sowing Time", "Crop Harvesting Time", "Yield Level (t/ha)", "season_days", "calendar_year"]].copy()
            chunk["flag_reason"] = description
            chunk["rule_id"] = rule_id
            flagged_parts.append(chunk)
            flag_mask |= mask

    weather_issues = check_weather_outliers(cleaned)
    if not weather_issues.empty:
        wflag = cleaned.merge(weather_issues, on="record_id", how="inner")
        wflag = wflag[["record_id", "Longitude", "Latitude", "Yield Level (t/ha)", "season_days", "weather_issues"]].copy()
        wflag["flag_reason"] = "Weather validation issues"
        wflag["rule_id"] = "weather_issue"
        flagged_parts.append(wflag)

    flagged = pd.concat(flagged_parts, ignore_index=True) if flagged_parts else pd.DataFrame()

    summary = pd.DataFrame([
        {"metric": "source_farms", "value": len(df)},
        {"metric": "removed_farms", "value": len(removed)},
        {"metric": "cleaned_farms", "value": len(cleaned)},
        {"metric": "flagged_for_review", "value": flagged["record_id"].nunique() if not flagged.empty else 0},
        {"metric": "rule_season_under_60_days", "value": (removed["rule_id"] == "season_too_short").sum() if not removed.empty else 0},
        {"metric": "rule_season_over_195_days", "value": (removed["rule_id"] == "season_too_long").sum() if not removed.empty else 0},
        {"metric": "rule_yield_under_2.5", "value": (removed["rule_id"] == "yield_too_low").sum() if not removed.empty else 0},
    ])

    return cleaned, removed, flagged, summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Create outlier-fixed segregated Excel.")
    parser.add_argument(
        "--input",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_segregated.xlsx",
    )
    parser.add_argument(
        "--output",
        default="/Users/nagarro/Documents/Project/NASF/NASA_Project/exports/rice_nasapower_weekly_segregated_outlier_fixed.xlsx",
    )
    args = parser.parse_args()

    df = pd.read_excel(args.input)
    cleaned, removed, flagged, summary = build_outlier_workbook(df)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)

    with pd.ExcelWriter(out, engine="openpyxl") as writer:
        cleaned.to_excel(writer, sheet_name="Cleaned_Data", index=False)
        if not removed.empty:
            removed.to_excel(writer, sheet_name="Outliers_Removed", index=False)
        if not flagged.empty:
            flagged.to_excel(writer, sheet_name="Outliers_Flagged", index=False)
        summary.to_excel(writer, sheet_name="Fix_Summary", index=False)

        rules_doc = pd.DataFrame([
            {"rule_id": r[0], "action": "REMOVE", "description": r[2]} for r in RULES_REMOVE
        ] + [
            {"rule_id": r[0], "action": "FLAG", "description": r[2]} for r in RULES_FLAG
        ] + [
            {"rule_id": "weather_fix", "action": "FIX", "description": "Replace NASA -999 with blank; swap t_min/t_max if min > max"},
        ])
        rules_doc.to_excel(writer, sheet_name="Rules_Applied", index=False)

    print(f"Source farms: {len(df)}")
    print(f"Removed: {len(removed)}")
    print(f"Cleaned: {len(cleaned)}")
    print(f"Flagged for review: {flagged['record_id'].nunique() if not flagged.empty else 0}")
    print(f"Output: {out}")


if __name__ == "__main__":
    main()
