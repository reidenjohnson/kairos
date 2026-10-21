package com.kairos.engine.bucks

import kotlin.math.exp
import kotlin.math.ln

/**
 * One condition a sighting can be sorted by (wind direction, 24-h temperature swing, ...).
 * [of] returns the bin index, or null when the weather for that moment is unknown (those
 * moments are left out of both the buck's count and the baseline, never guessed).
 *
 * [stratify] true = the baseline is weighted to the buck's own mix of cameras and light periods,
 * so a night-moving buck's "calm wind" isn't just "nights are calmer". The time-of-day factor
 * itself is compared to plain camera hours.
 */
class Factor(
    val key: String,
    val title: String,
    /** Bin labels in the given units. Thresholds stay in °F / mph / hPa; only the labels convert. */
    private val labels: (UnitFmt) -> List<String>,
    val stratify: Boolean = true,
    val of: (HourFeatures) -> Int?,
) {
    constructor(key: String, title: String, bins: List<String>, stratify: Boolean = true, of: (HourFeatures) -> Int?) :
        this(key, title, { _: UnitFmt -> bins }, stratify, of)

    /** Bin labels in the user's units ([Factors.units]). Match bins by index, never by label. */
    val bins: List<String> get() = labels(Factors.units)
}

object Factors {
    /** The user's units, for bin labels and plan text. Set by the app before each analysis. */
    @Volatile var units: UnitFmt = Imperial

    val light = Factor("light", "Time of day", Light.entries.map { it.label }, stratify = false) { it.light.ordinal }

    private fun calm(u: UnitFmt) = "Calm (<${u.speedNum(3.0)} ${u.speedUnit})"

    val windDir = Factor(
        "wind_dir", "Wind direction",
        { u: UnitFmt -> listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", calm(u)) },
    ) { f ->
        val s = f.windMph ?: return@Factor null
        if (s < 3) 8 else f.windDir?.let { (((it % 360 + 360) % 360 + 22.5) / 45).toInt() % 8 }
    }

    val windSpeed = Factor(
        "wind_speed", "Wind speed",
        { u: UnitFmt ->
            val a = u.speedNum(3.0); val b = u.speedNum(7.0); val c = u.speedNum(12.0); val s = u.speedUnit
            listOf(calm(u), "Light ($a-$b $s)", "Moderate ($b-$c $s)", "Strong ($c+ $s)")
        },
    ) { f ->
        val s = f.windMph ?: return@Factor null
        when { s < 3 -> 0; s < 7 -> 1; s < 12 -> 2; else -> 3 }
    }

    /** "10+ °F colder", "4-10 °F colder", "±4 °F", ... for a 4/10 °F split. */
    private fun swing(u: UnitFmt, same: String, coldest: String): List<String> {
        val a = u.tempDeltaNum(4.0); val b = u.tempDeltaNum(10.0); val s = u.tempUnit
        return listOf("$b+ $s $coldest", "$a-$b $s colder", "$same (±$a $s)", "$a-$b $s warmer", "$b+ $s warmer")
    }

    val tempNormal = Factor(
        "temp_normal", "Temperature vs normal (that day)",
        { u: UnitFmt -> swing(u, "Near normal", "colder than normal") },
    ) { f ->
        val d = f.departureF ?: return@Factor null
        when { d <= -10 -> 0; d <= -4 -> 1; d < 4 -> 2; d < 10 -> 3; else -> 4 }
    }

    val tempBand = Factor(
        "temp_band", "Temperature at the time",
        { u: UnitFmt ->
            val (a, b, c, d) = listOf(32.0, 45.0, 55.0, 65.0).map { u.tempNum(it) }
            val s = u.tempUnit
            listOf("Under $a $s", "$a-$b $s", "$b-$c $s", "$c-$d $s", "$d $s+")
        },
    ) { f ->
        val t = f.tempF ?: return@Factor null
        when { t < 32 -> 0; t < 45 -> 1; t < 55 -> 2; t < 65 -> 3; else -> 4 }
    }

    val tempSwing = Factor(
        "temp_swing", "Last 24 h vs the day before",
        { u: UnitFmt -> swing(u, "About the same", "colder") },
    ) { f ->
        val d = f.tempDay24 ?: return@Factor null
        when { d <= -10 -> 0; d <= -4 -> 1; d < 4 -> 2; d < 10 -> 3; else -> 4 }
    }

    val pressTrend = Factor(
        "pressure_trend", "Pressure trend (last 3 h)",
        { u: UnitFmt -> listOf("Falling fast (${u.pressAmt(2.0)}+)", "Falling", "Steady (±${u.pressAmt(0.7)})", "Rising", "Rising fast (${u.pressAmt(2.0)}+)") },
    ) { f ->
        val p = f.pressTrend3h ?: return@Factor null
        when { p <= -2 -> 0; p <= -0.7 -> 1; p < 0.7 -> 2; p < 2 -> 3; else -> 4 }
    }

    val front = Factor("front", "Fronts", FrontState.entries.map { it.label }) { it.front?.ordinal }

    val windSwing = Factor(
        "wind_swing", "Wind, last 24 h vs the day before",
        { u: UnitFmt ->
            val a = u.speedNum(3.0); val b = u.speedNum(7.0); val s = u.speedUnit
            listOf("Dropped $b+ $s", "Dropped $a-$b $s", "About the same (±$a)", "Rose $a+ $s")
        },
    ) { f ->
        val d = f.windDay24 ?: return@Factor null
        when { d <= -7 -> 0; d <= -3 -> 1; d < 3 -> 2; else -> 3 }
    }

    val cloud = Factor(
        "cloud", "Cloud cover",
        listOf("Clear (<25%)", "Partly cloudy", "Overcast (75%+)"),
    ) { f ->
        val c = f.cloudPct ?: return@Factor null
        when { c < 25 -> 0; c < 75 -> 1; else -> 2 }
    }

    val rain = Factor("rain", "Rain", RainState.entries.map { it.label }) { it.rain?.ordinal }

    val moon = Factor(
        "moon", "Moon",
        listOf("Dark moon (<25% lit)", "Half moon (25-75%)", "Bright moon (75%+)"),
    ) { f ->
        when { f.moonIllum < 0.25 -> 0; f.moonIllum < 0.75 -> 1; else -> 2 }
    }

    var rut: RutCalendar = RutCalendar()

    val phase get() = Factor("phase", "Season phase", SeasonPhase.entries.map { rut.label(it) }) { it.phase.ordinal }

    /** Every weather/calendar factor, in the order the buck page lists them. */
    val weather get() = listOf(windDir, windSpeed, tempSwing, tempNormal, tempBand, front, pressTrend, windSwing, rain, cloud, moon, phase)
}

/** Exact binomial tails, for "could this many visits under this condition be luck?". */
object Binomial {
    private fun logChoose(n: Int, k: Int): Double {
        var s = 0.0
        for (i in 1..k) s += ln((n - k + i).toDouble()) - ln(i.toDouble())
        return s
    }

    private fun pmf(n: Int, k: Int, p: Double): Double = when {
        p <= 0.0 -> if (k == 0) 1.0 else 0.0
        p >= 1.0 -> if (k == n) 1.0 else 0.0
        else -> exp(logChoose(n, k) + k * ln(p) + (n - k) * ln(1 - p))
    }

    /** P(X >= k). */
    fun upper(n: Int, k: Int, p: Double): Double = (k..n).sumOf { pmf(n, it, p) }.coerceIn(0.0, 1.0)

    /** P(X <= k). */
    fun lower(n: Int, k: Int, p: Double): Double = (0..k).sumOf { pmf(n, it, p) }.coerceIn(0.0, 1.0)
}
