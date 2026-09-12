# Wiring NASA Weather Into Existing Screens (Optional)

The weather module is ready. To connect **existing** `ManualDetails`, `AutomaticDetails`, and `SoilManagement` **without us changing those files in this pass**, apply these edits when you are ready.

## 1. AndroidManifest.xml

```xml
<activity android:name=".WeatherActivity" android:exported="false" />
<activity android:name=".WeatherStandaloneEntryActivity" android:exported="false" />
```

## 2. ManualDetails — replace direct `sendRequest` with weather navigation

Add imports:

```kotlin
import com.example.nasf.weather.CropSessionExtras
import com.example.nasf.weather.JulianDateHelper
import com.example.nasf.weather.MlUrlResolver
import com.example.nasf.weather.WeatherNavigation
```

Replace date picker blocks with:

```kotlin
JulianDateHelper.attachDatePicker(this, swoingdateValue)
JulianDateHelper.attachDatePicker(this, harvestdateValue)
```

On Calculate (after building adjusted N/P/K values):

```kotlin
val sowingYmd = JulianDateHelper.getDateYmd(swoingdateValue)
val harvestYmd = JulianDateHelper.getDateYmd(harvestdateValue)
val mlUrl = MlUrlResolver.manualYieldUrl(autoCompleteTextViewState, autoCompleteTextViewCrop)
val payload = """{ ... existing JSON fields ... }"""

WeatherNavigation.launch(
    activity = this,
    flowType = CropSessionExtras.FLOW_MANUAL,
    latitude = Lat.toDouble(),
    longitude = Long.toDouble(),
    sowingDateYmd = sowingYmd,
    harvestDateYmd = harvestYmd,
    mlUrl = mlUrl,
    mlPayloadJson = payload,
    crop = autoCompleteTextViewCrop,
    state = autoCompleteTextViewState
)
```

## 3. AutomaticDetails — same pattern

Use `MlUrlResolver.automaticYieldUrl` and `CropSessionExtras.FLOW_AUTOMATIC`.

## 4. SoilManagement — add sowing/harvest pickers + navigation

Copy date fields from `activity_automatic_details.xml` into `activity_soil_management.xml`, then:

```kotlin
JulianDateHelper.attachDatePicker(this, sowingDateValue)
JulianDateHelper.attachDatePicker(this, harvestDateValue)
```

On Get Advisory:

```kotlin
WeatherNavigation.launch(
    activity = this,
    flowType = CropSessionExtras.FLOW_SOIL,
    latitude = latStr.toDouble(),
    longitude = longStr.toDouble(),
    sowingDateYmd = sowingYmd,
    harvestDateYmd = harvestYmd,
    mlUrl = MlUrlResolver.soilAdvisoryUrl(crop),
    mlPayloadJson = """{"Long": $long, "Lat": $lat, "Grain_Yield_t_ha": $yield}""",
    crop = crop,
    useHtmlResult = true
)
```

## 5. MainActivity (optional 4th button)

```kotlin
startActivity(Intent(this, WeatherStandaloneEntryActivity::class.java))
```

## Date storage note

`JulianDateHelper` keeps **Julian DOY in the text field** (for your ML API) and **`yyyyMMdd` in the view tag** (for NASA `start`/`end`). No extra NASA-specific text fields are required.
