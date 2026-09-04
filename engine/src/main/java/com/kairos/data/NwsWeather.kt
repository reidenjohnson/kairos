package com.kairos.data

import com.kairos.engine.Conditions
import com.kairos.engine.SEBAGO_WATER_F
import com.kairos.engine.moonInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import kotlin.math.abs

/**
 * Backup weather source: the US National Weather Service (api.weather.gov, free,
 * no API key, US-only — fine for Maine). Used when Open-Meteo errors out, which
 * its free tier does intermittently (see [WeatherRepository.httpGetJson]).
 *
 * NWS's model differs from Open-Meteo's, so this is an honest, slightly-reduced
 * fallback rather than a drop-in:
 *  - **temp / cloud / wind / front** come from the gridpoint forecast (a numeric
 *    time series in ISO-8601 duration intervals; converted from °C, %, km/h).
 *  - **pressure (absolute + a real ~6h trend)** comes from the nearest station's
 *    observation history — the gridpoint forecast carries no pressure here.
 *  - **sun times, legal shooting hours, and the hourly "best times" curve are
 *    omitted** (NWS gives no sunrise/sunset); the day-level score is unaffected.
 *
 * Every returned [Forecast] is tagged `source = "NWS"` so the UI can say so.
 * Requires a descriptive User-Agent header per NWS policy.
 */
object NwsWeather {

    const val SOURCE = "NWS"
    private const val USER_AGENT = "Kairos/1.0 (weather app; reidenjohnson@gmail.com)"
    private const val PA_TO_INHG = 1.0 / 3386.389
    private const val KMH_TO_MPH = 0.621371

    /**
     * Fetch + translate NWS weather for [place] into a scoring-ready [Forecast].
     * BLOCKING — does network I/O, call off the main thread. Throws on failure so
     * the caller can fall through to the cache.
     */
    fun fetch(place: Place): Forecast {
        // 1) Resolve the point → its gridpoint URL, station list, and timezone.
        val point = JSONObject(httpGet(pointUrl(place))).getJSONObject("properties")
        val gridUrl = point.getString("forecastGridData")
        val stationsUrl = point.getString("observationStations")
        val zone = point.optString("timeZone").ifBlank { "America/New_York" }

        // 2) Nearest observation station (for pressure).
        val stations = JSONObject(httpGet(stationsUrl)).getJSONArray("features")
        require(stations.length() > 0) { "NWS returned no observation stations" }
        val station = stations.getJSONObject(0).getJSONObject("properties")
            .getString("stationIdentifier")

        // 3) Gridpoint forecast (temp/cloud/wind) + recent observations (pressure).
        val grid = httpGet(gridUrl)
        val obs = httpGet("https://api.weather.gov/stations/$station/observations?limit=90")

        return parse(grid, obs, zone, place.label, Instant.now())
    }

    private fun pointUrl(place: Place): String =
        "https://api.weather.gov/points/${place.lat},${place.lon}"

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/geo+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error("NWS returned HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun roundTo(x: Double, step: Double): Double = Math.round(x / step) * step

    /**
     * Visible for testing: translate a gridpoint-forecast JSON and an
     * observations JSON into a [Forecast], as of [now]. Kept pure (no network)
     * so it can be asserted against captured NWS fixtures.
     */
    internal fun parse(
        gridJson: String,
        obsJson: String,
        zoneId: String,
        placeLabel: String,
        now: Instant,
    ): Forecast {
        val props = JSONObject(gridJson).getJSONObject("properties")
        // Hour -> value maps in UTC, unit-converted to the engine's units.
        val temps = expandSeries(props.getJSONObject("temperature")) { cToF(it) }
        val clouds = expandSeries(props.getJSONObject("skyCover")) { it }
        val winds = expandSeries(props.getJSONObject("windSpeed")) { it * KMH_TO_MPH }

        val nowHour = now.truncatedTo(ChronoUnit.HOURS)
        val airF = roundTo(valueAt(temps, nowHour), 1.0)
        val windMph = roundTo(valueAt(winds, nowHour), 1.0)
        val cloudPct = roundTo(valueAt(clouds, nowHour), 5.0)

        // Coldest air over the next 24h → cold-front trigger (positive = drop coming).
        var coldest = airF
        var h = nowHour
        repeat(25) {
            temps.get(h)?.let { coldest = minOf(coldest, it) }
            h = h.plus(1, ChronoUnit.HOURS)
        }
        val tempDropNext24hF = roundTo(airF - coldest, 1.0)

        // Pressure now + ~6h ago from station observations (Pa → inHg).
        val pressure = pressureNowAnd6hAgo(obsJson, now)
            ?: error("NWS: no barometric pressure available")
        val pressureInHg = roundTo(pressure.first * PA_TO_INHG, 0.01)
        // Unknown 6h-ago reading → treat the trend as steady (0), never fabricated.
        val trendInHg = pressure.second?.let {
            roundTo((pressure.first - it) * PA_TO_INHG, 0.01)
        } ?: 0.0

        val date = now.atZone(ZoneId.of(zoneId)).toLocalDate()
        val waterF = SEBAGO_WATER_F.getValue(date.monthValue).toDouble()
        val moon = moonInfo(date)

        val conditions = Conditions(
            airF = airF,
            waterF = waterF,
            windMph = windMph,
            cloudPct = cloudPct,
            pressureInHg = pressureInHg,
            pressureTrendInHg = trendInHg,
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
            pressureTrendInHg = trendInHg,
            tempDropNext24hF = tempDropNext24hF,
            moonName = moon.phaseName,
            source = SOURCE,
        )
    }

    private fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

    /**
     * Expand an NWS time series into a per-UTC-hour map, applying [convert]. Each
     * entry's `validTime` is an ISO start instant plus an ISO-8601 duration
     * ("2026-09-03T17:00:00+00:00/PT2H") meaning "this value holds for that span";
     * we fill every whole hour it covers.
     */
    private fun expandSeries(
        field: JSONObject,
        convert: (Double) -> Double,
    ): TreeMap<Instant, Double> {
        val out = TreeMap<Instant, Double>()
        val values = field.getJSONArray("values")
        for (i in 0 until values.length()) {
            val v = values.getJSONObject(i)
            if (v.isNull("value")) continue
            val validTime = v.getString("validTime")
            val slash = validTime.indexOf('/')
            val start = OffsetDateTime.parse(validTime.substring(0, slash)).toInstant()
                .truncatedTo(ChronoUnit.HOURS)
            val hours = durationHours(validTime.substring(slash + 1))
            val value = convert(v.getDouble("value"))
            var t = start
            repeat(hours) {
                out[t] = value
                t = t.plus(1, ChronoUnit.HOURS)
            }
        }
        return out
    }

    /** Parse an ISO-8601 duration like "PT2H", "PT30M", or "P1DT6H" into whole hours (min 1). */
    private fun durationHours(iso: String): Int {
        val m = Regex("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?)?").matchEntire(iso) ?: return 1
        val days = m.groupValues[1].toIntOrNull() ?: 0
        val hrs = m.groupValues[2].toIntOrNull() ?: 0
        val mins = m.groupValues[3].toIntOrNull() ?: 0
        val total = days * 24 + hrs + if (mins > 0) 1 else 0
        return maxOf(1, total)
    }

    /** The value at [hour], or the nearest known hour if that exact one is missing. */
    private fun valueAt(series: TreeMap<Instant, Double>, hour: Instant): Double {
        series[hour]?.let { return it }
        val floor = series.floorEntry(hour)
        val ceil = series.ceilingEntry(hour)
        return when {
            floor != null && ceil != null ->
                if (abs(Instant.from(hour).epochSecond - floor.key.epochSecond) <=
                    abs(ceil.key.epochSecond - hour.epochSecond)
                ) floor.value else ceil.value
            floor != null -> floor.value
            ceil != null -> ceil.value
            else -> error("NWS: no data near ${hour}")
        }
    }

    /**
     * From the observations feed (newest first), the latest barometric pressure
     * (Pa) and the reading closest to ~6h before it — or null if there is no
     * usable current pressure. Second value is null when history is too shallow.
     */
    private fun pressureNowAnd6hAgo(obsJson: String, now: Instant): Pair<Double, Double?>? {
        val features = JSONObject(obsJson).getJSONArray("features")
        data class Ob(val time: Instant, val pa: Double)
        val obs = ArrayList<Ob>()
        for (i in 0 until features.length()) {
            val p = features.getJSONObject(i).getJSONObject("properties")
            val bp = p.optJSONObject("barometricPressure") ?: continue
            if (bp.isNull("value")) continue
            val time = runCatching { OffsetDateTime.parse(p.getString("timestamp")).toInstant() }
                .getOrNull() ?: continue
            obs.add(Ob(time, bp.getDouble("value")))
        }
        if (obs.isEmpty()) return null
        obs.sortByDescending { it.time }
        val latest = obs.first()
        val target = latest.time.minus(6, ChronoUnit.HOURS)
        // Closest reading to the 6h-ago target, only if within a 90-min window.
        val ago = obs.minByOrNull { abs(it.time.epochSecond - target.epochSecond) }
            ?.takeIf { abs(it.time.epochSecond - target.epochSecond) <= 90 * 60 }
        return latest.pa to ago?.pa
    }
}
