package com.kairos.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls official Maine GIS layers as GeoJSON, straight from the State of Maine's own
 * ArcGIS services — no third-party middleman, no API key. Same house style as
 * [WeatherRepository]/[UsgsWater]: blocking `HttpURLConnection` + `org.json`, meant to be
 * called off the main thread. The map layer feeds the returned GeoJSON straight into
 * MapLibre, so the lines/areas are exactly the state's published boundaries — nothing we
 * invent or interpolate.
 *
 * ArcGIS Feature Services cap how many features they return per request (`maxRecordCount`),
 * so [fetchGeoJson] pages with `resultOffset` until every feature is in hand, then stitches
 * them into one FeatureCollection.
 *
 * Sources (all on Maine's ArcGIS org `services1.arcgis.com/RbMX0mRVOFNTdLzd`):
 *  - Conserved/public lands, Wildlife Management Areas, Expanded Archery Areas, Wildlife
 *    Management Districts. Cited in reference/SOURCES.md. Maine labels these APPROXIMATE
 *    (not legal survey lines); the app surfaces that disclaimer.
 */
object MaineGisRepository {

    private const val PAGE = 1000
    private const val MAX_PAGES = 80 // safety cap: 80k features

    /**
     * Fetch every feature of an ArcGIS feature layer as one GeoJSON FeatureCollection string.
     * [layerUrl] is the layer endpoint (e.g. ".../FeatureServer/0"). [generalizeDeg], when
     * set, asks the server to simplify geometry by that many degrees (~0.0001° ≈ 11 m) to
     * shrink large statewide layers; leave null to get full-resolution boundaries.
     *
     * BLOCKING — call on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun fetchGeoJson(layerUrl: String, generalizeDeg: Double? = null): String {
        val features = JSONArray()
        var offset = 0
        var pages = 0
        while (pages < MAX_PAGES) {
            val body = httpGet(queryUrl(layerUrl, offset, PAGE, generalizeDeg))
            val json = JSONObject(body)
            // ArcGIS reports query errors as a 200 with an "error" object — surface it.
            json.optJSONObject("error")?.let { err ->
                throw IllegalStateException("ArcGIS error: ${err.optString("message", "unknown")}")
            }
            val batch = json.optJSONArray("features") ?: JSONArray()
            for (i in 0 until batch.length()) features.put(batch.get(i))
            // In ArcGIS GeoJSON, the "more remain" flag lives under top-level "properties".
            val exceeded = json.optJSONObject("properties")?.optBoolean("exceededTransferLimit", false) ?: false
            if (!exceeded || batch.length() == 0) break
            offset += PAGE
            pages++
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }

    private fun queryUrl(layerUrl: String, offset: Int, count: Int, generalizeDeg: Double?): String {
        val sb = StringBuilder(layerUrl.trimEnd('/'))
        sb.append("/query?where=1%3D1&outFields=*&returnGeometry=true&outSR=4326&f=geojson")
        sb.append("&resultOffset=").append(offset)
        sb.append("&resultRecordCount=").append(count)
        sb.append("&geometryPrecision=5")
        if (generalizeDeg != null) sb.append("&maxAllowableOffset=").append(generalizeDeg)
        return sb.toString()
    }

    private fun httpGet(url: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Kairos/1.0 (map; reidenjohnson@gmail.com)")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code from $url")
            return text
        } finally {
            conn?.disconnect()
        }
    }
}
