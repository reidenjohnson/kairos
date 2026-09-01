package com.kairos.engine

/** Which side of the app a species belongs to. */
enum class Side { HUNT, FISH }

/**
 * Per-species factor weights. Must sum to 1.0 (enforced by SpeciesTest).
 * Values come straight from the SOURCES.md weight table.
 */
data class Weights(
    val temp: Double,
    val trend: Double,
    val range: Double,
    val front: Double,
    val wind: Double,
    val cloud: Double,
    val moon: Double,
)

/** Species-specific wind band: full score in [lo, hi], zero at [hard]. */
data class WindBand(val lo: Double, val hi: Double, val hard: Double)

/** A scored species: its name, side, weights, and factor tunings. */
data class Species(
    val name: String,
    val side: Side,
    val weights: Weights,
    val tempSpec: TempSpec,
    val wind: WindBand,
    val moonMode: MoonMode,
)

/**
 * The 11 species Kairos covers. Mirrors the SPECIES dict in forecast.py
 * one-to-one. Do not retune weights without updating SOURCES.md.
 */
val SPECIES: List<Species> = listOf(
    Species(
        "Whitetail deer", Side.HUNT,
        Weights(temp = .32, trend = .10, range = .15, front = .26, wind = .12, cloud = .05, moon = 0.0),
        TempSpec.Cold(lo = 40.0, hi = 78.0), WindBand(3.0, 12.0, 25.0), MoonMode.NONE,
    ),
    Species(
        "Moose", Side.HUNT,
        Weights(temp = .47, trend = .10, range = .05, front = .20, wind = .12, cloud = .06, moon = 0.0),
        TempSpec.Cold(lo = 40.0, hi = 72.0), WindBand(2.0, 10.0, 22.0), MoonMode.NONE,
    ),
    Species(
        "Elk", Side.HUNT,
        Weights(temp = .40, trend = .10, range = .10, front = .21, wind = .12, cloud = .07, moon = 0.0),
        TempSpec.Cold(lo = 40.0, hi = 78.0), WindBand(3.0, 12.0, 25.0), MoonMode.NONE,
    ),
    Species(
        "Black bear", Side.HUNT,
        Weights(temp = .35, trend = .15, range = .05, front = .28, wind = .07, cloud = .10, moon = 0.0),
        TempSpec.Cold(lo = 40.0, hi = 75.0), WindBand(2.0, 10.0, 22.0), MoonMode.NONE,
    ),
    Species(
        "Snowshoe hare", Side.HUNT,
        Weights(temp = .30, trend = .10, range = .05, front = .10, wind = .20, cloud = .05, moon = .20),
        TempSpec.Cold(lo = 20.0, hi = 55.0), WindBand(0.0, 8.0, 18.0), MoonMode.INVERSE,
    ),
    Species(
        "Upland birds", Side.HUNT,
        Weights(temp = .35, trend = .10, range = .05, front = .10, wind = .25, cloud = .15, moon = 0.0),
        TempSpec.Cold(lo = 25.0, hi = 68.0), WindBand(0.0, 7.0, 18.0), MoonMode.NONE,
    ),
    Species(
        "Waterfowl", Side.HUNT,
        Weights(temp = .13, trend = .18, range = .02, front = .32, wind = .30, cloud = .05, moon = 0.0),
        TempSpec.Cold(lo = 25.0, hi = 70.0), WindBand(8.0, 16.0, 30.0), MoonMode.NONE,
    ),
    Species(
        "Largemouth bass", Side.FISH,
        Weights(temp = .23, trend = .40, range = .20, front = .08, wind = .05, cloud = .04, moon = 0.0),
        TempSpec.Band(ideal = 80.0, spread = 22.0), WindBand(2.0, 12.0, 25.0), MoonMode.NONE,
    ),
    Species(
        "Smallmouth bass", Side.FISH,
        Weights(temp = .23, trend = .40, range = .20, front = .08, wind = .05, cloud = .04, moon = 0.0),
        TempSpec.Band(ideal = 72.0, spread = 16.0), WindBand(2.0, 12.0, 25.0), MoonMode.NONE,
    ),
    Species(
        "Salmon / togue / brookie", Side.FISH,
        Weights(temp = .42, trend = .28, range = .10, front = .10, wind = .05, cloud = .05, moon = 0.0),
        TempSpec.Coldwater, WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
    ),
    Species(
        "Walleye", Side.FISH,
        Weights(temp = .18, trend = .18, range = .07, front = .08, wind = .22, cloud = .22, moon = .05),
        TempSpec.Band(ideal = 68.0, spread = 18.0), WindBand(6.0, 16.0, 30.0), MoonMode.NEWFULL,
    ),
)

/**
 * Approximate Sebago Lake surface water temp by month (°F) — a deep coldwater
 * lake. Tier-3 proxy: fish respond to WATER temp but the free feed gives AIR
 * temp. See SOURCES.md "Known limitation". Future fix: real lake-temp input.
 * Keyed 1 (Jan) .. 12 (Dec).
 */
val SEBAGO_WATER_F: Map<Int, Int> = mapOf(
    1 to 34, 2 to 33, 3 to 36, 4 to 45, 5 to 55, 6 to 66,
    7 to 73, 8 to 74, 9 to 68, 10 to 57, 11 to 47, 12 to 39,
)
