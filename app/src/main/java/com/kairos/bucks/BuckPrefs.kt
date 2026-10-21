package com.kairos.bucks

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kairos.engine.bucks.UnitFmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class TempUnit(val label: String) { F("°F"), C("°C") }
enum class WindUnit(val label: String) { MPH("mph"), KMH("km/h"), KT("knots"), MS("m/s") }
enum class PressUnit(val label: String) { INHG("inHg"), HPA("hPa / mb") }
enum class RainUnit(val label: String) { IN("inches"), MM("mm") }
enum class Compass(val label: String) { EIGHT("8-point (NW)"), SIXTEEN("16-point (NNW)") }

/**
 * Bucks settings: units for every number, clock style, compass detail, and how photos group into
 * visits. Compose state over SharedPreferences, so every screen re-renders on a change. Data is
 * always stored in °F / mph / hPa / inches; these only change how it's shown.
 */
object BuckPrefs {
    var temp by mutableStateOf(TempUnit.F); private set
    var wind by mutableStateOf(WindUnit.MPH); private set
    var press by mutableStateOf(PressUnit.INHG); private set
    var rain by mutableStateOf(RainUnit.IN); private set
    var compass by mutableStateOf(Compass.SIXTEEN); private set
    var clock24 by mutableStateOf(false); private set
    /** Photos at one camera this close together count as one visit (minutes). */
    var visitMin by mutableStateOf(10); private set
    /** Units picked once on first open. */
    var unitsAsked by mutableStateOf(true); private set
    /** One-time strip re-read with the voting reader has run. */
    var rereadDone by mutableStateOf(true); private set

    private var loaded = false
    private lateinit var app: Context
    private fun sp() = app.getSharedPreferences("bucks_prefs", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        app = context.applicationContext
        val p = sp()
        fun <E : Enum<E>> get(key: String, def: E, values: Array<E>) = values.firstOrNull { it.name == p.getString(key, null) } ?: def
        temp = get("temp", TempUnit.F, TempUnit.entries.toTypedArray())
        wind = get("wind", WindUnit.MPH, WindUnit.entries.toTypedArray())
        press = get("press", PressUnit.INHG, PressUnit.entries.toTypedArray())
        rain = get("rain", RainUnit.IN, RainUnit.entries.toTypedArray())
        compass = get("compass", Compass.SIXTEEN, Compass.entries.toTypedArray())
        clock24 = p.getBoolean("clock24", false)
        visitMin = p.getInt("visitMin", 10)
        unitsAsked = p.getBoolean("unitsAsked", false)
        rereadDone = p.getBoolean("rereadV2", false)
        loaded = true
    }

    private fun put(k: String, v: String) = sp().edit().putString(k, v).apply()
    fun pickTemp(v: TempUnit) { temp = v; put("temp", v.name) }
    fun pickWind(v: WindUnit) { wind = v; put("wind", v.name) }
    fun pickPress(v: PressUnit) { press = v; put("press", v.name) }
    fun pickRain(v: RainUnit) { rain = v; put("rain", v.name) }
    fun pickCompass(v: Compass) { compass = v; put("compass", v.name) }
    fun pickClock24(v: Boolean) { clock24 = v; sp().edit().putBoolean("clock24", v).apply() }
    fun pickVisitMin(v: Int) { visitMin = v; sp().edit().putInt("visitMin", v).apply() }
    fun markUnitsAsked() { unitsAsked = true; sp().edit().putBoolean("unitsAsked", true).apply() }
    fun markRereadDone() { rereadDone = true; sp().edit().putBoolean("rereadV2", true).apply() }

    val visitGapSec get() = visitMin * 60L
}

/** Every number on the Bucks screens goes through here, in his units. */
object U : UnitFmt {
    override fun temp(f: Double): String = when (BuckPrefs.temp) {
        TempUnit.F -> "${f.roundToInt()}°F"
        TempUnit.C -> "${((f - 32) * 5 / 9).roundToInt()}°C"
    }

    /** A temperature difference (no offset): 9°F = 5°C. */
    override fun tempDelta(f: Double): String = when (BuckPrefs.temp) {
        TempUnit.F -> "${f.roundToInt()}°"
        TempUnit.C -> "${(f * 5 / 9).roundToInt()}°"
    }

    override val tempUnit get() = BuckPrefs.temp.label
    override fun tempNum(f: Double): Int = when (BuckPrefs.temp) { TempUnit.F -> f; TempUnit.C -> (f - 32) * 5 / 9 }.roundToInt()
    override fun tempDeltaNum(f: Double): Int = when (BuckPrefs.temp) { TempUnit.F -> f; TempUnit.C -> f * 5 / 9 }.roundToInt()
    override val speedUnit get() = BuckPrefs.wind.label.let { if (it == "knots") "kt" else it }

    override fun speedNum(mph: Double): Int = when (BuckPrefs.wind) {
        WindUnit.MPH -> mph
        WindUnit.KMH -> mph * 1.609344
        WindUnit.KT -> mph * 0.868976
        WindUnit.MS -> mph * 0.44704
    }.roundToInt()

    override fun speed(mph: Double): String = "${speedNum(mph)} $speedUnit"

    override fun compass(deg: Double): String {
        val d = (deg % 360 + 360) % 360
        return if (BuckPrefs.compass == Compass.EIGHT) {
            arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((d + 22.5) / 45).toInt() % 8]
        } else {
            arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[((d + 11.25) / 22.5).toInt() % 16]
        }
    }

    fun pressure(hpa: Double): String = when (BuckPrefs.press) {
        PressUnit.INHG -> "%.2f inHg".format(hpa * 0.0295300)
        PressUnit.HPA -> "${hpa.roundToInt()} hPa"
    }

    /** A 3-hour pressure change. */
    fun pressureDelta(hpa: Double): String = when (BuckPrefs.press) {
        PressUnit.INHG -> (if (hpa >= 0) "+" else "") + "%.2f".format(hpa * 0.0295300)
        PressUnit.HPA -> (if (hpa >= 0) "+" else "") + "%.1f".format(hpa)
    }

    override fun rain(inches: Double): String = when (BuckPrefs.rain) {
        RainUnit.IN -> "%.2f in".format(inches)
        RainUnit.MM -> "%.1f mm".format(inches * 25.4)
    }

    override fun pressAmt(hpa: Double): String = when (BuckPrefs.press) {
        PressUnit.INHG -> "%.2f inHg".format(hpa * 0.0295300)
        PressUnit.HPA -> if (hpa == hpa.roundToInt().toDouble()) "${hpa.roundToInt()} hPa" else "%.1f hPa".format(hpa)
    }

    private val H12 = DateTimeFormatter.ofPattern("h:mm a")
    private val H24 = DateTimeFormatter.ofPattern("HH:mm")
    override fun clock(t: Long, zone: ZoneId): String = (if (BuckPrefs.clock24) H24 else H12).format(Instant.ofEpochSecond(t).atZone(zone))
    fun clock(t: java.time.temporal.TemporalAccessor): String = (if (BuckPrefs.clock24) H24 else H12).format(t)

    /** "SW 6 mph", or "Calm" under 2 mph (the same line the map's particles stop at). */
    fun wind(dir: Double?, mph: Double?): String = when {
        mph == null -> "Wind ?"
        mph < 2 -> "Calm"
        dir == null -> speed(mph)
        else -> "${compass(dir)} ${speed(mph)}"
    }
}
