# ChargingWidget

An Android widget that shows real-time charging power (watts), battery percent, current, and voltage.

I built this as a quick **2-day project** to solve a practical problem I was having: I wanted a fast way to sanity-check whether a charger/cable/brick was actually delivering the power I expected, without having to open an app and dig through menus.

## What It Does

- Home screen + lock screen widget (Android 14 / API 34+).
- Displays:
  - Charging status + type (AC / USB / Wireless)
  - Wattage (computed from voltage * current)
  - Battery percent + progress bar
  - Current (mA), voltage (mV), temperature (C) in the companion UI
- Updates automatically while charging.

## How It Works (High Level)

- `ChargingDataProvider` reads current/voltage from common sysfs paths when available for better accuracy, with `BatteryManager` as a fallback.
- `ChargingWidgetProvider` renders the widget using `RemoteViews`.
- `WidgetUpdateService` runs as a foreground service while charging to refresh the widget frequently (every ~2 seconds).
- Charging state is detected via:
  - power connect/disconnect broadcast receiver
  - `JobScheduler` jobs that trigger on charging/not-charging
  - a periodic `WorkManager` fallback worker

## Requirements

- Android Studio + JDK 17
- Android 14+ device/emulator (this project currently targets **minSdk 34**)

## Build

```bash
./gradlew assembleDebug
```

## Install (ADB)

```bash
./gradlew installDebug
```

or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Add The Widget

1. Long-press on home screen (or go to lock screen widget picker, depending on device).
2. Find **Charging Widget**.
3. Add it to the home screen / lock screen.

## Notes / Caveats

- Some devices report current as negative values when charging; the app uses `abs()` to normalize readings.
- Sysfs paths vary by manufacturer; the code checks several common locations.
- The frequent update loop uses a foreground service; Android may show a persistent notification while it’s active.

