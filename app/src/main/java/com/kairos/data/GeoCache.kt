package com.kairos.data

import android.content.Context
import java.io.File

/**
 * On-disk cache for the map's GeoJSON overlays. Unlike [ForecastCache] (a tiny row in
 * SharedPreferences), overlay GeoJSON can be several MB, so it lives as files under
 * `filesDir/gis/`. Fetched once from Maine's ArcGIS, reused on later opens, and — the
 * point of caching here — available offline in the woods once downloaded.
 */
object GeoCache {

    private fun dir(context: Context): File = File(context.filesDir, "gis").apply { mkdirs() }
    private fun file(context: Context, id: String): File = File(dir(context), "$id.geojson")

    /** The cached GeoJSON for [id], or null if we've never fetched it. */
    fun load(context: Context, id: String): String? =
        file(context, id).takeIf { it.exists() && it.length() > 0 }?.readText()

    /** Age of the cached copy in milliseconds, or null if absent. */
    fun ageMillis(context: Context, id: String): Long? =
        file(context, id).takeIf { it.exists() }?.let { System.currentTimeMillis() - it.lastModified() }

    fun save(context: Context, id: String, geoJson: String) {
        file(context, id).writeText(geoJson)
    }
}
