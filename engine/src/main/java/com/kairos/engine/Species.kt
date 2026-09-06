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
    /** When it's most active through the day; drives the "best times today" curve. */
    val chronotype: Chronotype = Chronotype.CREPUSCULAR,
)

/**
 * The 21 species Kairos covers. Mirrors the SPECIES dict in forecast.py
 * one-to-one. Do not retune weights without updating SOURCES.md.
 */
val SPECIES: List<Species> = listOf(
    // ---------------------------- HUNT ----------------------------
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
    // Wild turkey: sharp-eyed and ear-driven, so wind hurts most (can't hear or be
    // heard, and everything looks like a threat in moving cover); mild days beat
    // temperature extremes, and heavy overcast/rain keeps birds on the roost. Not a
    // cold-front species like deer. Peaks at fly-down and again late afternoon.
    Species(
        "Wild turkey", Side.HUNT,
        Weights(temp = .20, trend = .12, range = .10, front = .13, wind = .30, cloud = .15, moon = 0.0),
        TempSpec.Band(ideal = 52.0, spread = 45.0), WindBand(0.0, 8.0, 20.0), MoonMode.NONE,
    ),
    // Coyote: a cold-loving predator that moves more in cold and behind fronts; calling
    // works best in light wind (they hunt by ear too). Most active at first/last light
    // and after dark. Hunted year-round in Maine.
    Species(
        "Coyote", Side.HUNT,
        Weights(temp = .28, trend = .12, range = .12, front = .18, wind = .18, cloud = .12, moon = 0.0),
        TempSpec.Cold(lo = 5.0, hi = 70.0), WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // ---------------------------- FISH ----------------------------
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
    // The old "Salmon / togue / brookie" entry, split into three real fisheries.
    // All are coldwater (temp-driven, cliff in warm water), most active in low light.
    // Brook trout are the most temp-sensitive of the three at the surface.
    Species(
        "Brook trout", Side.FISH,
        Weights(temp = .45, trend = .25, range = .10, front = .10, wind = .05, cloud = .05, moon = 0.0),
        TempSpec.Coldwater, WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // Landlocked salmon chase smelt near the surface in cold water and tolerate a bit
    // of chop (classic trolling breeze), so wind counts a touch more than for brookies.
    Species(
        "Landlocked salmon", Side.FISH,
        Weights(temp = .40, trend = .28, range = .10, front = .10, wind = .06, cloud = .06, moon = 0.0),
        TempSpec.Coldwater, WindBand(0.0, 12.0, 24.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // Lake trout (togue) hold the coldest, deepest water — the most temperature-driven
    // of the coldwater fish.
    Species(
        "Lake trout (togue)", Side.FISH,
        Weights(temp = .48, trend = .22, range = .10, front = .08, wind = .06, cloud = .06, moon = 0.0),
        TempSpec.Coldwater, WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // Northern pike: a cool-water ambush predator that feeds hard on falling pressure
    // ahead of a front and likes some wind/chop on the weed edges. Peak mid-60s °F.
    Species(
        "Northern pike", Side.FISH,
        Weights(temp = .22, trend = .34, range = .16, front = .12, wind = .10, cloud = .06, moon = 0.0),
        TempSpec.Band(ideal = 66.0, spread = 22.0), WindBand(2.0, 14.0, 28.0), MoonMode.NONE,
    ),
    // Chain pickerel: like a small pike but more cold-tolerant (a strong ice-fishing
    // biter), so its temperature window is wider.
    Species(
        "Chain pickerel", Side.FISH,
        Weights(temp = .20, trend = .34, range = .18, front = .10, wind = .10, cloud = .08, moon = 0.0),
        TempSpec.Band(ideal = 62.0, spread = 26.0), WindBand(2.0, 12.0, 25.0), MoonMode.NONE,
    ),
    // Yellow perch: a daytime, sight-feeding schooling fish — quiet at night, so it
    // stays CREPUSCULAR. Notably pressure/front sensitive.
    Species(
        "Yellow perch", Side.FISH,
        Weights(temp = .25, trend = .28, range = .20, front = .07, wind = .08, cloud = .12, moon = 0.0),
        TempSpec.Band(ideal = 66.0, spread = 18.0), WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
    ),
    // White perch: a low-light schooling feeder (best at dusk and into dark), common in
    // southern Maine lakes.
    Species(
        "White perch", Side.FISH,
        Weights(temp = .24, trend = .28, range = .18, front = .08, wind = .10, cloud = .12, moon = 0.0),
        TempSpec.Band(ideal = 68.0, spread = 18.0), WindBand(2.0, 12.0, 25.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // Black crappie: the classic dawn/dusk, low-light suspended feeder — notoriously
    // shut down by a bluebird post-front bright sky.
    Species(
        "Black crappie", Side.FISH,
        Weights(temp = .24, trend = .30, range = .16, front = .10, wind = .08, cloud = .12, moon = 0.0),
        TempSpec.Band(ideal = 68.0, spread = 18.0), WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
        chronotype = Chronotype.LOW_LIGHT,
    ),
    // Panfish (sunfish / bluegill): warm-loving, shallow, daytime feeders — the least
    // weather-sensitive of the group, most driven by warm water.
    Species(
        "Panfish (sunfish)", Side.FISH,
        Weights(temp = .30, trend = .26, range = .18, front = .06, wind = .08, cloud = .12, moon = 0.0),
        TempSpec.Band(ideal = 78.0, spread = 20.0), WindBand(0.0, 10.0, 22.0), MoonMode.NONE,
    ),
    Species(
        "Walleye", Side.FISH,
        Weights(temp = .18, trend = .18, range = .07, front = .08, wind = .22, cloud = .22, moon = .05),
        TempSpec.Band(ideal = 68.0, spread = 18.0), WindBand(6.0, 16.0, 30.0), MoonMode.NEWFULL,
        chronotype = Chronotype.LOW_LIGHT,
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
