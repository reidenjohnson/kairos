# Kairos

An evidence-based "go now" forecaster for hunting and fishing. Open it and Kairos reads your
location and live weather, then scores each species from 0–100 on how good conditions are right
now — with a plain-English explanation of why. It runs on Android and has a Wear OS tile for
at-a-glance scores on the wrist.

The name comes from the Greek *kairos* — the right, opportune moment to act.

## What makes it different

The scores are grounded in published research, not folklore. Every factor and weight is
documented and cited in [`reference/SOURCES.md`](reference/SOURCES.md). Notably, moon/solunar
influence is weighted near zero everywhere the evidence doesn't support it — it earns weight only
where studies do (e.g. snowshoe hare in winter, walleye near the new and full moon).

## How the score works

Each species has a weighted blend of 0–1 sub-scores derived from live conditions:

- **Temperature** — species-specific comfort/activity band (air for game, a water-temp proxy for fish)
- **Pressure trend** — barometric change over ~6h (falling favors feeding)
- **Pressure range** — the absolute-pressure sweet spot
- **Cold front** — a temperature drop incoming in the next 24h
- **Wind** — each species' preferred band
- **Cloud cover**
- **Moon** — near-zero except where research supports it

Weights sum to 1.0 per species and come straight from the sources table.

Weather comes from [Open-Meteo](https://open-meteo.com) (free, no API key). Sun and moon are
computed on-device, so the scoring engine works offline; the last forecast is cached for use with
no signal.

## Project layout

- **`engine/`** — a pure Kotlin scoring library (no UI, no framework beyond `java.time`), shared by
  the phone and watch so both score identically. Ported from the Python reference in `reference/`
  and covered by parity unit tests.
- **`app/`** — the phone app (Jetpack Compose, Material 3): live location, weather fetch, and the
  HUNT/FISH score lists with per-species explanations, sorting, and an offline cache.
- **`wear/`** — a Wear OS tile showing the best hunt and fish score at a glance.
- **`reference/`** — the Python reference implementation (`forecast.py`) and the research/weights
  documentation (`SOURCES.md`) the Kotlin engine is verified against.

## Build

Requires JDK 17+ and the Android SDK.

```
./gradlew :engine:testDebugUnitTest   # run the engine parity tests
./gradlew :app:assembleDebug          # build the phone APK
./gradlew :wear:assembleDebug         # build the Wear OS tile
```

## Status

Working MVP: the engine, phone app (verified on a Galaxy S23 Ultra), and Wear OS tile (verified on
a Galaxy Watch 7) are functional. In progress: a redesigned UI, Maine season calendars, a weekly
outlook, and a catch/harvest journal.
