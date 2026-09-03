package com.kairos.data

import com.kairos.engine.Conditions
import com.kairos.engine.SEBAGO_WATER_F
import com.kairos.engine.SPECIES
import com.kairos.engine.Side
import com.kairos.engine.activityMultiplier
import com.kairos.engine.moonInfo
import com.kairos.engine.score
import com.kairos.engine.scoreAll
import com.kairos.engine.timeOfDayActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.roundToInt

/** A place to forecast for. Shared by the phone (:app) and the Wear tile (:wear). */
data class Place(val lat: Double, val lon: Double, val label: String)

/** Sebago Lake, southern Maine — the default location used until device location is available. */
object Location {
    const val LAT = 43.85
    const val LON = -70.56
    const val LABEL = "Sebago Lake, ME"
    val SEBAGO = Place(LAT, LON, LABEL)
}

private const val HPA_TO_INHG = 0.02953

/** Everything a forecast screen needs: the engine input plus a display summary. */
data class Forecast(
    val conditions: Conditions,
    val placeLabel: String,  // e.g. "Sebago Lake, ME" or "Naples, Maine"
    val dateLabel: String,   // e.g. "2026-09-01"
    val airF: Double,
    val waterF: Double,
    val windMph: Double,
    val cloudPct: Double,
    val pressureInHg: Double,
    val pressureTrendInHg: Double,
    val tempDropNext24hF: Double,
    val moonName: String,
    /** Local sunrise/sunset ISO datetime for the day, e.g. "2026-09-02T06:07" (null if unavailable). */
    val sunrise: String? = null,
    val sunset: String? = null,
    /** Today's hour-by-hour "best times" timing (null if not computed, e.g. from cache). */
    val timing: DayTiming? = null,
) {
    val trendWord: String
        get() = when {
            pressureTrendInHg < -0.01 -> "falling"
            pressureTrendInHg > 0.01 -> "rising"
            else -> "steady"
        }

    /** Sunrise as a local time, or null if unavailable. */
    val sunriseTime: java.time.LocalTime? get() = parseIsoTime(sunrise)

    /** Sunset as a local time, or null if unavailable. */
    val sunsetTime: java.time.LocalTime? get() = parseIsoTime(sunset)

    /**
     * Maine legal shooting/hunting hours: a half-hour before sunrise until a
     * half-hour after sunset (per Maine IF&W). Null if sun times are unavailable.
     */
    val legalShootingHours: Pair<java.time.LocalTime, java.time.LocalTime>?
        get() {
            val sr = sunriseTime ?: return null
            val ss = sunsetTime ?: return null
            return sr.minusMinutes(30) to ss.plusMinutes(30)
        }

    private fun parseIsoTime(iso: String?): java.time.LocalTime? =
        iso?.let { runCatching { java.time.LocalTime.parse(it.substring(11, 16)) }.getOrNull() }
}

/** A side's score at one hour of today (0-23), used for the "best times" curve. */
data class HourScore(val hour: Int, val huntScore: Int, val fishScore: Int)

/**
 * Today's timing: the hour-by-hour "best times" curve for each side, the day-level
 * overall score per side (how good today is regardless of hour), and the sun times
 * that shape the curve. See [Forecast.timing].
 */
data class DayTiming(
    val hours: List<HourScore>,
    val sunriseHour: Double,
    val sunsetHour: Double,
    val huntToday: Int,
    val fishToday: Int,
) {
    fun scoreForSide(side: Side): Int = if (side == Side.FISH) fishToday else huntToday

    /** Contiguous peak windows for [side]: runs of hours within 8 pts of the day's max. */
    fun bestWindows(side: Side): List<IntRange> {
        if (hours.isEmpty()) return emptyList()
        val vals = hours.map { it.hour to if (side == Side.FISH) it.fishScore else it.huntScore }
        val max = vals.maxOf { it.second }
        val hot = vals.filter { it.second >= max - 8 }.map { it.first }.toSortedSet()
        val out = ArrayList<IntRange>()
        var start: Int? = null
        var prev: Int? = null
        for (h in hot) {
            if (start == null) { start = h; prev = h } else if (h == prev!! + 1) { prev = h } else {
                out.add(start!!..prev!!); start = h; prev = h
            }
        }
        if (start != null) out.add(start!!..prev!!)
        return out
    }
}

/** One species' best score on one day, and the local hour (0-23) it peaks. */
data class DayScore(val date: LocalDate, val bestPercent: Int, val bestHour: Int)

/** A species' day-by-day forecasted outlook (the "expected" line on the chart). */
data class SpeciesOutlook(val speciesName: String, val side: Side, val days: List<DayScore>)

/** The forecasted outlook for every species over the coming days. */
data class Outlook(val placeLabel: String, val perSpecies: List<SpeciesOutlook>)

/**
 * Pulls live weather from Open-Meteo (free, no API key) and turns it into a
 * [Conditions] for the engine. This is the app-layer port of forecast.py's
 * fetch_weather / pressure_trend_inhg / temp_drop_next_24h; the scoring math
 * itself lives in the pure [com.kairos.engine] package.
 */
object WeatherRepository {

    private fun buildUrl(place: Place): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.lat}&longitude=${place.lon}" +
            "&hourly=temperature_2m,surface_pressure,wind_speed_10m,cloud_cover" +
            "&current=temperature_2m,surface_pressure,wind_speed_10m,cloud_cover" +
            "&daily=sunrise,sunset" +
            "&timezone=auto&past_days=1&forecast_days=2" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph"

    /** Round to the nearest [step] — keeps the score steady when weather is roughly flat. */
    private fun roundTo(x: Double, step: Double): Double = Math.round(x / step) * step

    /**
     * Fetch + parse + score-ready for [place] (defaults to Sebago). BLOCKING —
     * does network I/O, so call it off the main thread (the phone wraps it in
     * Dispatchers.IO; the Wear tile runs it on a background executor). Throws on
     * network/parse failure. `timezone=auto` makes Open-Meteo return times local
     * to the given coordinates, so date/month come out right anywhere.
     */
    fun fetch(place: Place = Location.SEBAGO): Forecast {
        return parse(JSONObject(httpGetJson(buildUrl(place))), place.label)
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("Open-Meteo returned HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * GET expecting a JSON object, with retries. Open-Meteo's free tier
     * intermittently returns HTTP 200 with a non-JSON body ("Unexpected error
     * while streaming data: timeoutReached") or a JSON error object
     * ({"error":true,"reason":"The service is overloaded"}). Both are transient,
     * so treat them as failures and retry with backoff before giving up (the
     * caller then falls back to the cached forecast).
     */
    private fun httpGetJson(url: String, attempts: Int = 3): String {
        var last: Exception? = null
        for (i in 0 until attempts) {
            try {
                val body = httpGet(url).trimStart()
                if (!body.startsWith("{")) {
                    error("Weather service busy — please try again")
                }
                if (Regex("\"error\"\\s*:\\s*true").containsMatchIn(body)) {
                    val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
                    error(reason ?: "Weather service unavailable")
                }
                return body
            } catch (e: Exception) {
                last = e
                if (i < attempts - 1) Thread.sleep(600L * (i + 1))
            }
        }
        throw last ?: java.io.IOException("Weather request failed")
    }

    /** Visible for testing: turn an Open-Meteo response into a [Forecast]. */
    internal fun parse(root: JSONObject, placeLabel: String): Forecast {
        val current = root.getJSONObject("current")
        val currentTime = current.getString("time") // local to the place's timezone

        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val pressures = hourly.getJSONArray("surface_pressure")
        val temps = hourly.getJSONArray("temperature_2m")
        val winds = hourly.getJSONArray("wind_speed_10m")
        val clouds = hourly.getJSONArray("cloud_cover")

        // Index of the current hour within the hourly arrays (tz-consistent).
        val nowHour = currentTime.take(13) + ":00"
        var i = 0
        while (i < times.length() && times.getString(i) != nowHour) i++
        if (i >= times.length()) i = times.length() / 2 // fallback, mirrors reference

        // Score off the current HOUR's forecast values (stable across the hour)
        // rather than the `current` block (which updates every few minutes with
        // gusts/pressure ticks and makes the score jitter). Inputs are rounded so
        // that roughly-flat weather yields a roughly-flat score all day.
        val airF = roundTo(temps.getDouble(i), 1.0)
        val pressureInHg = roundTo(pressures.getDouble(i) * HPA_TO_INHG, 0.01)
        val windMph = roundTo(winds.getDouble(i), 1.0)
        val cloudPct = roundTo(clouds.getDouble(i), 5.0)

        // Pressure trend over ~6h (inHg, negative = falling).
        val i6 = maxOf(0, i - 6)
        val pressureTrendInHg =
            roundTo((pressures.getDouble(i) - pressures.getDouble(i6)) * HPA_TO_INHG, 0.01)

        // Coldest drop coming over the next 24h (°F, positive = front incoming).
        val end = minOf(i + 24, temps.length())
        var coldest = temps.getDouble(i)
        for (k in i until end) coldest = minOf(coldest, temps.getDouble(k))
        val tempDropNext24hF = roundTo(temps.getDouble(i) - coldest, 1.0)

        val date = LocalDate.parse(currentTime.take(10))
        val waterF = SEBAGO_WATER_F.getValue(date.monthValue).toDouble()
        val moon = moonInfo(date)

        // Sun times for the day (for legal shooting hours), local to the place.
        var sunrise: String? = null
        var sunset: String? = null
        root.optJSONObject("daily")?.let { daily ->
            val days = daily.optJSONArray("time")
            val rises = daily.optJSONArray("sunrise")
            val sets = daily.optJSONArray("sunset")
            if (days != null && rises != null && sets != null) {
                for (k in 0 until days.length()) {
                    if (days.getString(k).take(10) == date.toString()) {
                        sunrise = rises.optString(k, null)
                        sunset = sets.optString(k, null)
                        break
                    }
                }
            }
        }

        val conditions = Conditions(
            airF = airF,
            waterF = waterF,
            windMph = windMph,
            cloudPct = cloudPct,
            pressureInHg = pressureInHg,
            pressureTrendInHg = pressureTrendInHg,
            tempDropNext24hF = tempDropNext24hF,
            moonIllum = moon.illum,
        )

        return Forecast(
            conditions = conditions,
            placeLabel = placeLabel,
            dateLabel = date.toString(),
            airF = airF,
            waterF = waterF,
            windMph = windMph,
            cloudPct = cloudPct,
            pressureInHg = pressureInHg,
            pressureTrendInHg = pressureTrendInHg,
            tempDropNext24hF = tempDropNext24hF,
            moonName = moon.phaseName,
            sunrise = sunrise,
            sunset = sunset,
            // Timing is a bonus layer — never let a glitch in it break the forecast.
            timing = runCatching {
                computeDayTiming(times, temps, pressures, winds, clouds, date, sunrise, sunset, conditions)
            }.getOrNull(),
        )
    }

    /** Parse "…T06:07" into a fractional hour (6.117), or null. */
    private fun isoHour(iso: String?): Double? {
        if (iso == null || iso.length < 16) return null
        return runCatching {
            iso.substring(11, 13).toInt() + iso.substring(14, 16).toInt() / 60.0
        }.getOrNull()
    }

    /**
     * Build today's hour-by-hour timing: for each hour of [date], score every
     * species from that hour's weather times its time-of-day activity, and average
     * per side. The day-level per-side score comes from [current] conditions (no
     * time modifier) — "how good is today," separate from "when today."
     */
    private fun computeDayTiming(
        times: org.json.JSONArray,
        temps: org.json.JSONArray,
        pressures: org.json.JSONArray,
        winds: org.json.JSONArray,
        clouds: org.json.JSONArray,
        date: LocalDate,
        sunriseIso: String?,
        sunsetIso: String?,
        current: Conditions,
    ): DayTiming? {
        val srH = isoHour(sunriseIso) ?: return null
        val ssH = isoHour(sunsetIso) ?: return null
        val waterF = SEBAGO_WATER_F.getValue(date.monthValue).toDouble()
        val moonIllum = moonInfo(date).illum
        val hours = ArrayList<HourScore>()
        for (idx in 0 until times.length()) {
            val t = times.getString(idx)
            if (t.take(10) != date.toString()) continue
            val h = t.substring(11, 13).toIntOrNull() ?: continue
            val i6 = maxOf(0, idx - 6)
            val end = minOf(idx + 24, temps.length())
            var coldest = temps.getDouble(idx)
            for (k in idx until end) coldest = minOf(coldest, temps.getDouble(k))
            val c = Conditions(
                airF = roundTo(temps.getDouble(idx), 1.0),
                waterF = waterF,
                windMph = roundTo(winds.getDouble(idx), 1.0),
                cloudPct = roundTo(clouds.getDouble(idx), 5.0),
                pressureInHg = roundTo(pressures.getDouble(idx) * HPA_TO_INHG, 0.01),
                pressureTrendInHg =
                    roundTo((pressures.getDouble(idx) - pressures.getDouble(i6)) * HPA_TO_INHG, 0.01),
                tempDropNext24hF = roundTo(temps.getDouble(idx) - coldest, 1.0),
                moonIllum = moonIllum,
            )
            var huntSum = 0.0; var huntN = 0; var fishSum = 0.0; var fishN = 0
            for (sp in SPECIES) {
                val act = activityMultiplier(timeOfDayActivity(h.toDouble(), srH, ssH, sp.chronotype))
                val s = score(sp, c) * act
                if (sp.side == Side.HUNT) { huntSum += s; huntN++ } else { fishSum += s; fishN++ }
            }
            hours.add(
                HourScore(
                    h,
                    if (huntN > 0) (huntSum / huntN * 100).roundToInt() else 0,
                    if (fishN > 0) (fishSum / fishN * 100).roundToInt() else 0,
                ),
            )
        }
        if (hours.isEmpty()) return null
        val huntToday = (SPECIES.filter { it.side == Side.HUNT }.map { score(it, current) }.average() * 100).roundToInt()
        val fishToday = (SPECIES.filter { it.side == Side.FISH }.map { score(it, current) }.average() * 100).roundToInt()
        return DayTiming(hours.sortedBy { it.hour }, srH, ssH, huntToday, fishToday)
    }

    private fun outlookUrl(place: Place, days: Int): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.lat}&longitude=${place.lon}" +
            "&hourly=temperature_2m,surface_pressure,wind_speed_10m,cloud_cover" +
            "&timezone=auto&past_days=1&forecast_days=$days" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph"

    /**
     * Fetch a multi-day outlook: for each of the next [days] days, the best score
     * each species reaches at any hour (the "expected" line the Trends chart draws
     * against the recorded "actual"). BLOCKING — call off the main thread. Uses the
     * same rounded, hourly-block inputs as [parse], so it is consistent with the
     * live score.
     */
    fun fetchOutlook(place: Place = Location.SEBAGO, days: Int = 7): Outlook =
        parseOutlook(JSONObject(httpGetJson(outlookUrl(place, days))), place.label)

    /** Visible for testing: turn an hourly Open-Meteo response into an [Outlook]. */
    internal fun parseOutlook(root: JSONObject, placeLabel: String): Outlook {
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val pressures = hourly.getJSONArray("surface_pressure")
        val winds = hourly.getJSONArray("wind_speed_10m")
        val clouds = hourly.getJSONArray("cloud_cover")
        val n = times.length()

        val today = LocalDate.now()
        // speciesName -> (date -> best DayScore so far)
        val best = LinkedHashMap<String, LinkedHashMap<LocalDate, DayScore>>()
        val sideOf = HashMap<String, Side>()

        for (i in 0 until n) {
            val date = LocalDate.parse(times.getString(i).take(10))
            if (date.isBefore(today)) continue // outlook is forward-looking
            val hour = times.getString(i).substring(11, 13).toIntOrNull() ?: 12

            val i6 = maxOf(0, i - 6)
            val end = minOf(i + 24, temps.length())
            var coldest = temps.getDouble(i)
            for (k in i until end) coldest = minOf(coldest, temps.getDouble(k))

            val c = Conditions(
                airF = roundTo(temps.getDouble(i), 1.0),
                waterF = SEBAGO_WATER_F.getValue(date.monthValue).toDouble(),
                windMph = roundTo(winds.getDouble(i), 1.0),
                cloudPct = roundTo(clouds.getDouble(i), 5.0),
                pressureInHg = roundTo(pressures.getDouble(i) * HPA_TO_INHG, 0.01),
                pressureTrendInHg =
                    roundTo((pressures.getDouble(i) - pressures.getDouble(i6)) * HPA_TO_INHG, 0.01),
                tempDropNext24hF = roundTo(temps.getDouble(i) - coldest, 1.0),
                moonIllum = moonInfo(date).illum,
            )

            for (s in scoreAll(c)) {
                val name = s.species.name
                sideOf[name] = s.species.side
                val byDate = best.getOrPut(name) { LinkedHashMap() }
                val prev = byDate[date]
                if (prev == null || s.percent > prev.bestPercent) {
                    byDate[date] = DayScore(date, s.percent, hour)
                }
            }
        }

        val perSpecies = best.map { (name, byDate) ->
            SpeciesOutlook(name, sideOf.getValue(name), byDate.values.sortedBy { it.date })
        }
        return Outlook(placeLabel, perSpecies)
    }
}
