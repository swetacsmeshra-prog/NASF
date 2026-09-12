#!/usr/bin/env python3
"""Generate Word documentation for the June–October Crop Season Analytics feature."""

from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Inches, Pt, RGBColor

OUTPUT_DOCX = Path("docs/52_WEEK_ANALYTICS_BUTTON_FEATURE.docx")
OUTPUT_DOC = Path("docs/52_WEEK_ANALYTICS_BUTTON_FEATURE.doc")


def set_normal_style(doc: Document) -> None:
    style = doc.styles["Normal"]
    style.font.name = "Calibri"
    style.font.size = Pt(11)


def add_title(doc: Document, text: str, level: int = 1) -> None:
    doc.add_heading(text, level=level)


def add_para(doc: Document, text: str, bold: bool = False) -> None:
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.bold = bold


def add_bullets(doc: Document, items: list[str]) -> None:
    for item in items:
        doc.add_paragraph(item, style="List Bullet")


def add_numbered(doc: Document, items: list[str]) -> None:
    for item in items:
        doc.add_paragraph(item, style="List Number")


def add_flowchart_box(doc: Document, lines: list[str]) -> None:
    p = doc.add_paragraph()
    run = p.add_run("\n".join(lines))
    run.font.name = "Consolas"
    run.font.size = Pt(9)
    p.paragraph_format.left_indent = Inches(0.25)
    shading = p.paragraph_format
    shading.space_before = Pt(6)
    shading.space_after = Pt(6)


def add_table(doc: Document, headers: list[str], rows: list[list[str]]) -> None:
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = h
        for p in hdr[i].paragraphs:
            for r in p.runs:
                r.bold = True
    for row in rows:
        cells = table.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = val
    doc.add_paragraph()


def build() -> Path:
    doc = Document()
    set_normal_style(doc)

    # Cover
    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    t_run = title.add_run("NASF Android Application\n")
    t_run.bold = True
    t_run.font.size = Pt(22)
    t_run.font.color.rgb = RGBColor(0x2A, 0x2D, 0x93)
    sub = title.add_run("June–October Crop Season Analytics\nFeature Documentation")
    sub.bold = True
    sub.font.size = Pt(16)

    doc.add_paragraph()
    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta.add_run("Plain-language guide for users, testers, and developers\nJune 2026")

    doc.add_page_break()

    # 1. Introduction
    add_title(doc, "1. Introduction", 1)
    add_para(
        doc,
        "This document explains the View Crop Season Analytics Report button in the "
        "NASF Android app. The report shows June–October weather charts, Random Forest "
        "and Cubic model predictions (using crop-season data only), and a downloadable "
        "full 52-week segregated CSV file for research compatibility.",
    )
    add_para(doc, "Who is this for?", bold=True)
    add_bullets(
        doc,
        [
            "Farmers and field advisors who want a visual weekly weather report",
            "Testers checking that the automatic rice workflow works end-to-end",
            "Developers maintaining the NASA POWER and ML integration",
        ],
    )

    # 2. What is new
    add_title(doc, "2. What Is New?", 1)
    add_table(
        doc,
        ["Feature", "Description"],
        [
            ["New button", "View Crop Season Analytics Report on the Analyzed Weather screen"],
            ["Charts & models", "June–October only (weeks 23–44)"],
            ["Segregated CSV", "Full 52-week download unchanged"],
            ["New screen", "WeeklyAnalyticsReportActivity — charts, models, CSV export"],
            ["Full-year weather", "Automatic mode fetches Jan 1 – Dec 31 from NASA POWER"],
            ["Segregated CSV", "Download file matching rice_nasapower_weekly_segregated.csv layout"],
            ["Random Forest", "On-device yield prediction trained on 1,056 farms"],
            ["Cubic model", "Rainfall-vs-yield curve for easy interpretation"],
            ["Rice season highlight", "Weeks 23–44 (June sowing – October harvest) marked on charts"],
        ],
    )

    # 3. User flow
    add_title(doc, "3. How to Reach the Button (User Flow)", 1)
    add_para(
        doc,
        "Follow these steps in order. The 52-week button only appears after NASA weather "
        "has been fetched successfully.",
    )
    add_flowchart_box(
        doc,
        [
            "  ┌─────────────────┐",
            "  │   Main Screen   │",
            "  └────────┬────────┘",
            "           │",
            "           ▼",
            "  ┌─────────────────┐",
            "  │ Automatic Mode  │  ← Enter farm details (lat, lon, June sow, Oct harvest)",
            "  └────────┬────────┘",
            "           │",
            "           ▼",
            "  ┌─────────────────┐",
            "  │ Weather Screen  │  ← Tap Fetch (full year weather downloaded)",
            "  └────────┬────────┘",
            "           │",
            "           ▼",
            "  ┌─────────────────────────────┐",
            "  │ NASA POWER Analyzed Screen  │  ← Short weekly summary shown here",
            "  └────────┬────────────────────┘",
            "           │",
            "     ┌─────┴─────┐",
            "     ▼           ▼",
            "  [View 52-Week   [Continue to",
            "   Analytics      Calculate]",
            "   Report]  ◄── NEW BUTTON",
            "     │",
            "     ▼",
            "  ┌─────────────────────────────┐",
            "  │ Crop Season Analytics Report    │  ← Charts + RF + Cubic + Segregated CSV",
            "  └────────┬────────────────────┘",
            "           │",
            "           ▼",
            "  ┌─────────────────┐",
            "  │ ML Yield Result │  ← Existing server API (unchanged)",
            "  └─────────────────┘",
        ],
    )

    add_title(doc, "3.1 Step-by-step instructions", 2)
    add_table(
        doc,
        ["Step", "Screen", "Action"],
        [
            ["1", "Automatic Mode", "Select state, rice crop, enter latitude & longitude, sowing (~June) and harvest (~October) dates"],
            ["2", "Weather (NASA POWER)", "Tap Fetch. App downloads weather for the entire calendar year (1 January – 31 December)"],
            ["3", "Analyzed Weather", "Review summary. Tap View Crop Season Analytics Report (button must be active/purple)"],
            ["4", "Crop Season Report", "Scroll charts, read RF and cubic predictions, preview segregated data, download CSV if needed"],
            ["5", "Continue to Calculate", "Tap to get final yield from existing ML server (same as before)"],
        ],
    )

    # 4. Button behaviour
    add_title(doc, "4. View Crop Season Analytics Report Button — Detailed Behaviour", 1)
    add_para(doc, "Where is the button?", bold=True)
    add_bullets(
        doc,
        [
            "Screen name: NASA POWER Data — Analyzed (NasaPowerAnalyzedActivity)",
            "Position: Below Download Analyzed CSV, above Continue to Calculate",
            "Label: View Crop Season Analytics Report",
        ],
    )

    add_para(doc, "When is the button enabled?", bold=True)
    add_bullets(
        doc,
        [
            "NASA weather data was fetched successfully",
            "A valid sowing date (YYYYMMDD) is available",
            "The app can build or has built 52-week calendar analysis",
        ],
    )

    add_para(doc, "When is the button disabled (greyed out)?", bold=True)
    add_bullets(
        doc,
        [
            "Weather fetch failed or weather JSON is missing",
            "Sowing date is missing or invalid",
            "Analysis is still running (wait for progress bar to finish)",
        ],
    )

    add_para(doc, "What happens when you tap the button?", bold=True)
    add_numbered(
        doc,
        [
            "App opens WeeklyAnalyticsReportActivity",
            "June–October weather charts and crop-season ML models are shown; "
            "full-year NASA data is used internally; segregated CSV export keeps all 52 weeks.",
            "Charts are drawn for rainfall, temperature, humidity, solar radiation, and wind",
            "Random Forest and cubic models predict yield (tonnes per hectare)",
            "Segregated CSV preview and download become available",
            "Continue to Calculate on the report screen calls the existing ML API",
        ],
    )

    # 5. Report screen
    add_title(doc, "5. What You See on the Crop Season Report Screen", 1)
    add_flowchart_box(
        doc,
        [
            "  ┌──────────────────────────────────────────┐",
            "  │  Farm summary (location, year, season)   │",
            "  ├──────────────────────────────────────────┤",
            "  │  Random Forest yield + importance chart  │",
            "  ├──────────────────────────────────────────┤",
            "  │  Cubic model curve (rainfall vs yield)    │",
            "  ├──────────────────────────────────────────┤",
            "  │  Segregated CSV preview (week01_… columns) │",
            "  │  [Download Segregated CSV]                 │",
            "  ├──────────────────────────────────────────┤",
            "  │  Weekly Rainfall chart (52 weeks)         │",
            "  │  Temperature Min/Max chart                 │",
            "  │  Humidity chart                            │",
            "  │  Solar radiation chart                     │",
            "  │  Wind speed chart                          │",
            "  ├──────────────────────────────────────────┤",
            "  │  [Continue to Calculate]                   │",
            "  └──────────────────────────────────────────┘",
        ],
    )

    add_table(
        doc,
        ["Section", "Plain English explanation"],
        [
            ["Farm summary", "Shows your GPS coordinates, calendar year, and total rain during June–October"],
            ["Random Forest", "Predicted rice yield using a model trained on 1,056 real farms"],
            ["Top drivers chart", "Shows which weather/soil factors most influenced the prediction"],
            ["Cubic model", "A smooth curve relating seasonal rainfall to yield; your farm appears as a dot"],
            ["Segregated preview", "Sample of column names like week01_rainfall_total, week01_t_min, etc."],
            ["Download Segregated CSV", "Saves a file matching the research dataset format (one row, 52 weeks × 7 metrics)"],
            ["Weather charts", "Green highlighting = rice crop weeks 23–44 (June–October)"],
        ],
    )

    # 6. Rice calendar
    add_title(doc, "6. Rice Season on the 52-Week Calendar", 1)
    add_para(
        doc,
        "Rice in this project: sowing month = June, harvest month = October. "
        "On the 52-week calendar used by the app:",
    )
    add_table(
        doc,
        ["Period", "Week numbers", "Meaning"],
        [
            ["January – May", "1 – 22", "Pre-season (before sowing)"],
            ["June", "23 – 27", "Sowing period (highlighted on charts)"],
            ["July – September", "28 – 39", "Growing season"],
            ["October", "40 – 44", "Harvest period (highlighted on charts)"],
            ["November – December", "45 – 52", "Post-harvest"],
        ],
    )
    add_flowchart_box(
        doc,
        [
            "  Year timeline (52 weeks):",
            "  |←── Jan-May ──→|←──── June-Oct (RICE) ────→|← Nov-Dec →|",
            "  Week:  1 … 22      23 …………………………… 44          45 … 52",
            "                      ^ sowing          ^ harvest",
        ],
    )

    # 7. Segregated CSV
    add_title(doc, "7. Segregated CSV Format (Simple Explanation)", 1)
    add_para(
        doc,
        "The segregated CSV is a spreadsheet layout where each farm occupies ONE row and "
        "weather is stored in many columns — grouped by metric type:",
    )
    add_bullets(
        doc,
        [
            "week01_rainfall_total, week02_rainfall_total, … week52_rainfall_total",
            "week01_rainfall_mean, week02_rainfall_mean, … week52_rainfall_mean",
            "week01_relative_humidity … through week52_wind_speed",
            "7 metrics × 52 weeks = 364 weather columns per farm",
        ],
    )
    add_para(
        doc,
        "The app builds this same structure from live NASA weather for your farm and lets "
        "you download it from the report screen.",
    )
    add_flowchart_box(
        doc,
        [
            "  NASA daily weather (365 days)",
            "           │",
            "           ▼",
            "  Split into 52 calendar weeks",
            "           │",
            "           ▼",
            "  Calculate 7 metrics per week",
            "  (rain, humidity, solar, t_min, t_max, wind)",
            "           │",
            "           ▼",
            "  Segregated CSV (one row per farm)",
        ],
    )

    # 8. Data flow
    add_title(doc, "8. Technical Data Flow (Behind the Button)", 1)
    add_table(
        doc,
        ["Stage", "Component", "What it does"],
        [
            ["Input", "AutomaticDetails form", "Collects lat, lon, sowing/harvest dates, soil nutrients"],
            ["Fetch", "WeatherActivity", "Calls NASA POWER for full calendar year with 6 weather parameters"],
            ["Short analysis", "WeeklyWeatherAnalyzer", "Mon–Fri weeks during crop season (existing summary)"],
            ["52-week analysis", "CalendarWeeklyAnalyzer", "Splits year into 52 weeks × 7 metrics"],
            ["ML features", "FeatureVectorBuilder", "Prepares season totals for Random Forest and cubic models"],
            ["Prediction", "YieldModelPredictor", "Runs bundled yield_models.json on the phone"],
            ["Export", "SegregatedCsvExporter", "Creates downloadable segregated CSV file"],
            ["Charts", "WeeklyChartHelper + MPAndroidChart", "Draws bar and line charts on screen"],
            ["Navigation", "WeeklyAnalyticsNavigation", "Opens report when button is tapped"],
        ],
    )

    # 9. ML models
    add_title(doc, "9. Machine Learning Models (Simple Terms)", 1)
    add_title(doc, "9.1 Random Forest", 2)
    add_bullets(
        doc,
        [
            "Trained on exports/rice_nasapower_weekly_segregated.csv (1,056 farms)",
            "Training script: train_yield_models.py",
            "Stored in app: app/src/main/assets/ml/yield_models.json",
            "Typical accuracy: about 0.48 t/ha mean error (MAE), R² ≈ 0.74",
            "Works offline on the phone — no internet needed for RF/cubic display",
        ],
    )
    add_title(doc, "9.2 Cubic model", 2)
    add_bullets(
        doc,
        [
            "Uses mainly total rainfall during crop season (June–October)",
            "Shows a smooth curve on screen — easy to explain to farmers",
            "Your farm appears as a highlighted point on the curve",
        ],
    )

    # 10. Two weekly methods
    add_title(doc, "10. Two Types of Weekly Analysis", 1)
    add_para(
        doc,
        "The app uses two different weekly calculations. This is important to understand:",
    )
    add_table(
        doc,
        ["", "Mon–Fri season weeks (old)", "52 calendar weeks (new — powers the button)"],
        [
            ["Date range", "Sowing date → harvest date only", "Full year January – December"],
            ["Week style", "Monday to Friday blocks", "Calendar weeks 1 – 52"],
            ["Max weeks", "About 29", "52"],
            ["Used for", "Short text summary, Download Analyzed CSV", "Crop Season Report button, segregated CSV, RF/cubic"],
            ["Matches segregated CSV", "Partially", "Yes"],
        ],
    )

    # 11. Troubleshooting
    add_title(doc, "11. Troubleshooting — Button Not Working", 1)
    add_table(
        doc,
        ["Problem", "Likely cause", "What to do"],
        [
            ["Button is grey/disabled", "Weather not fetched or sowing date missing", "Go back, use Automatic Mode, tap Fetch on weather screen"],
            ["Only 3 days in season summary", "Short date range was fetched", "Rebuild app; automatic mode now forces full year fetch"],
            ["Season exceeds 200 days error", "Full-year dates were treated as crop season", "Fixed — use latest app version"],
            ["Empty or flat charts", "Weather data missing for most weeks", "Check internet; confirm lat/lon are valid (India region)"],
            ["Report opens but yield looks wrong", "Test coordinates (e.g. 90°, 90°) or bad dates", "Use real farm location and June/October dates"],
        ],
    )

    # 12. Files changed
    add_title(doc, "12. Key Files in the Codebase", 1)
    add_table(
        doc,
        ["File", "Purpose"],
        [
            ["NasaPowerAnalyzedActivity.kt", "Hosts report buttons; runs both analyses"],
            ["WeeklyAnalyticsReportActivity.kt", "Crop season (weekly) report screen"],
            ["ThirtyYearCropSeasonActivity.kt", "30-year June–October monthly report"],
            ["MonthlyCropSeasonAnalyzer.kt", "30-year monthly Jun–Oct aggregation"],
            ["MonthlyCropSeasonCsvExporter.kt", "30-year summary CSV export"],
            ["ThirtyYearNavigation.kt", "Navigation to 30-year screen"],
            ["WeeklyAnalyticsNavigation.kt", "Navigation helper to open weekly report"],
            ["CalendarWeeklyAnalyzer.kt", "52-week weather aggregation"],
            ["SegregatedCsvExporter.kt", "CSV preview and download (52-week, unchanged)"],
            ["YieldModelPredictor.kt", "Random Forest and cubic on device"],
            ["WeatherActivity.kt", "Full-year NASA fetch; 30-year report entry"],
            ["train_yield_models.py", "Retrain models from segregated CSV"],
            ["activity_nasa_power_analyzed.xml", "Button layout on analyzed screen"],
            ["activity_thirty_year_crop_season.xml", "30-year report layout"],
            ["activity_weekly_analytics_report.xml", "Weekly report screen layout"],
        ],
    )

    # 12b. 30-year screen
    add_title(doc, "12b. 30-Year June–October Screen", 1)
    add_para(
        doc,
        "A second report screen shows about 30 years of NASA POWER monthly climate for June through "
        "October. Open it from the Weather screen (View 30-Year June–October Report) or the Analyzed "
        "screen (View 30-Year Climate Report). Charts use years on the x-axis. Pick a year from the "
        "spinner to see monthly detail and Random Forest / Cubic yield for that season. A separate CSV "
        "export is available; the full 52-week segregated file remains on the Crop Season report only.",
    )
    add_table(
        doc,
        ["Feature", "Weekly crop season report", "30-year Jun–Oct report"],
        [
            ["Years shown", "1 calendar year", "~30 years"],
            ["Granularity", "Weekly (weeks 23–44)", "Monthly Jun–Oct"],
            ["CSV", "52-week segregated wide file", "30yr × 5 months summary"],
            ["ML", "RF + Cubic for current season", "RF + Cubic per selected year + trend"],
        ],
    )

    # 12c. 2026 Wind & Power screen
    add_title(doc, "12c. 2026 Wind & Power Model Comparison", 1)
    add_para(
        doc,
        "A third report screen on the NASA Analyzed page compares five yield models "
        "(Random Forest, Cubic, XGBoost, ANN, SVM) for weeks 23–43. "
        "Charts show Tmin/Tmax, wind speed, solar radiation, and rainfall. "
        "Each model card shows predicted yield and validation RMSE/STDV from training on the segregated CSV.",
    )
    add_table(
        doc,
        ["File", "Purpose"],
        [
            ["WindPowerModelReportActivity.kt", "2026 wind & power model comparison UI"],
            ["ExtendedYieldModelPredictor.kt", "On-device RF/Cubic/XGB/ANN/SVM inference"],
            ["train_yield_models_extended.py", "Train and export extended models JSON"],
            ["WindPowerWeekMapper.kt", "Weeks 23–43 filter"],
            ["SeasonStatisticsCalculator.kt", "Tmin/Tmax/wind mean and STDV"],
        ],
    )

    # 13. Glossary
    add_title(doc, "13. Glossary", 1)
    add_table(
        doc,
        ["Term", "Meaning"],
        [
            ["NASA POWER", "Free global weather database used by the app"],
            ["Segregated CSV", "Wide spreadsheet: one row per farm, weekly weather in many columns"],
            ["t/ha", "Tonnes per hectare — unit for rice yield"],
            ["Random Forest", "Machine learning method using many decision trees"],
            ["Cubic model", "Polynomial curve (degree 3) for rainfall vs yield"],
            ["Crop season weeks", "Weeks 23–44 on the calendar (June–October for rice)"],
        ],
    )

    # Footer
    doc.add_paragraph()
    footer = doc.add_paragraph()
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    f_run = footer.add_run(
        "NASF Project — June–October Crop Season Analytics Documentation\n"
        "Related: docs/52_WEEK_ANALYTICS_GUIDE.md"
    )
    f_run.italic = True
    f_run.font.size = Pt(9)

    doc.save(OUTPUT_DOCX)

    # Copy to .doc extension for users who expect .doc (Word opens .docx format)
    import shutil
    shutil.copy(OUTPUT_DOCX, OUTPUT_DOC)
    return OUTPUT_DOC


if __name__ == "__main__":
    path = build()
    print(f"Created: {path}")
    print(f"Also:   {OUTPUT_DOCX}")
