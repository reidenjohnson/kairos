package com.kairos.engine

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 30-year NORMAL high and low for a spot and date: what "typical" means HERE on
 * this date, so the plan can say "12° below normal" honestly.
 *
 * How it's built (tested, see ENGINE_AUDIT.md "Normals work"): the Open-Meteo 30-yr
 * modeled history at the spot, corrected by how wrong that model is at the nearby
 * NOAA 1991–2020 stations (inverse-distance weighted, k nearest). The model alone runs
 * ~4°F warm on lows (valley cold-air pooling); the correction removes that. Beyond
 * [MAX_STATION_KM] from any station there's nothing to correct with, so the result is
 * labeled [NormalsSource.MODELED].
 */
enum class NormalsSource { STATION_CORRECTED, MODELED }

data class DayNormal(val lowF: Double, val highF: Double, val source: NormalsSource)

/** One NOAA station's daily model error (NOAA − model), °F, 365 days (no Feb 29). */
class StationBias(val lat: Double, val lon: Double, val lowBias: DoubleArray, val highBias: DoubleArray)

/**
 * Neighbours used for the correction, and how far one may be to count. Leave-one-out
 * test (82 Maine stations, Oct–Dec lows): the correction beat the raw model at every
 * tested distance, out to ~90 km from the nearest station (median 4.2 → 1.6°F at
 * 30–45 km). 100 km stays just past what was tested; farther is left MODELED.
 */
const val NORMALS_K = 5
const val MAX_STATION_KM = 100.0

/** Day index 0..364 for [date] on a no-leap calendar; Feb 29 counts as Feb 28. */
fun normalsDayIndex(date: LocalDate): Int {
    val d = if (date.monthValue == 2 && date.dayOfMonth == 29) date.minusDays(1) else date
    return LocalDate.of(2021, d.monthValue, d.dayOfMonth).dayOfYear - 1
}

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lon2 - lon1)
    return 6371.0 * acos(min(1.0, sin(p1) * sin(p2) + cos(p1) * cos(p2) * cos(dl)))
}

/**
 * Smooth 30 years of daily model lows/highs into a 365-day normal: mean per calendar
 * day, then a ±15-day circular moving average. Must match notes/normals_test/loo.py,
 * which is how the station biases were measured.
 */
fun modelNormals(dates: List<LocalDate>, lows: List<Double?>, highs: List<Double?>): Pair<DoubleArray, DoubleArray>? {
    val sumLo = DoubleArray(365); val sumHi = DoubleArray(365); val n = IntArray(365)
    for (k in dates.indices) {
        val d = dates[k]
        if (d.monthValue == 2 && d.dayOfMonth == 29) continue
        val lo = lows[k] ?: continue
        val hi = highs[k] ?: continue
        val i = normalsDayIndex(d)
        sumLo[i] += lo; sumHi[i] += hi; n[i]++
    }
    if (n.any { it == 0 }) return null
    val lo = DoubleArray(365) { sumLo[it] / n[it] }
    val hi = DoubleArray(365) { sumHi[it] / n[it] }
    fun smooth(a: DoubleArray) = DoubleArray(365) { i -> (-15..15).sumOf { a[(i + it + 365) % 365] } / 31.0 }
    return smooth(lo) to smooth(hi)
}

/** Correct the spot's modeled normal for [dayIndex] by its nearby stations' errors. */
fun localNormal(
    lat: Double,
    lon: Double,
    dayIndex: Int,
    modelLowF: Double,
    modelHighF: Double,
    stations: List<StationBias>,
): DayNormal {
    val near = stations
        .map { it to distanceKm(lat, lon, it.lat, it.lon) }
        .filter { it.second <= MAX_STATION_KM }
        .sortedBy { it.second }
        .take(NORMALS_K)
    if (near.isEmpty()) return DayNormal(modelLowF, modelHighF, NormalsSource.MODELED)
    var w = 0.0; var lo = 0.0; var hi = 0.0
    for ((s, d) in near) {
        val x = 1.0 / (max(d, 1.0) * max(d, 1.0))
        w += x; lo += x * s.lowBias[dayIndex]; hi += x * s.highBias[dayIndex]
    }
    return DayNormal(modelLowF + lo / w, modelHighF + hi / w, NormalsSource.STATION_CORRECTED)
}

/**
 * How far today runs from normal, °F (positive = warmer): the average of the high's and
 * the low's departures, the way weather reports figure a day's departure from normal.
 * Uses whole-day forecast highs/lows, never one hour against a daily figure.
 */
fun departureFromNormalF(todayLowF: Double, todayHighF: Double, normal: DayNormal): Double =
    ((todayLowF - normal.lowF) + (todayHighF - normal.highF)) / 2.0

/** Loads the bundled station table (built by tools/build_normals_bias.py). */
object StationBiasTable {
    private const val RESOURCE = "/com/kairos/engine/station_normal_bias.tsv"

    val all: List<StationBias> by lazy { load() }

    private fun load(): List<StationBias> {
        val text = StationBiasTable::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()?.use { it.readText() } ?: return emptyList()
        return text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val f = line.split('\t')
            // id, name, lat, lon, 365 low biases, 365 high biases
            StationBias(
                lat = f[2].toDouble(),
                lon = f[3].toDouble(),
                lowBias = DoubleArray(365) { f[4 + it].toDouble() },
                highBias = DoubleArray(365) { f[4 + 365 + it].toDouble() },
            )
        }.toList()
    }
}
