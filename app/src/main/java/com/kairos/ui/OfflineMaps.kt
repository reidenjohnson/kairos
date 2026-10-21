package com.kairos.ui

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.nio.charset.StandardCharsets

/**
 * Thin wrapper over MapLibre's offline downloader — lets the user save a map area (base
 * tiles for the current topo/satellite style over a chosen region + zoom range) so the
 * map works with no signal in the woods. Overlay boundaries are already cached to disk by
 * [com.kairos.data.GeoCache], so once downloaded they render offline too.
 *
 * MapLibre keeps a per-app tile limit (default 6,000); we lift it so a real hunting area
 * fits. Region metadata is a small JSON blob (name, base map) we read back for the list.
 */
object OfflineMaps {

    private const val TILE_LIMIT = 60_000L
    private const val META_NAME = "name"
    private const val META_BASE = "base"

    private fun manager(context: Context): OfflineManager =
        OfflineManager.getInstance(context).also { it.setOfflineMapboxTileCountLimit(TILE_LIMIT) }

    /** Start downloading [bounds] at zooms [minZoom]..[maxZoom] for [styleUri], named [name]. */
    internal fun download(
        context: Context,
        name: String,
        base: BaseMap,
        styleUri: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val pixelRatio = context.resources.displayMetrics.density
        val definition = OfflineTilePyramidRegionDefinition(styleUri, bounds, minZoom, maxZoom, pixelRatio)
        val metadata = JSONObject().put(META_NAME, name).put(META_BASE, base.name)
            .toString().toByteArray(StandardCharsets.UTF_8)

        manager(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val pct = if (status.requiredResourceCount > 0) {
                                (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt().coerceIn(0, 100)
                            } else 0
                            if (status.isComplete) onComplete() else onProgress(pct)
                        }

                        override fun onError(error: OfflineRegionError) {
                            onError(error.message ?: error.reason ?: "download error")
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            onError("Area too large — over the $limit-tile limit. Zoom in or pick a smaller area.")
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) = onError(error)
            },
        )
    }

    /** List downloaded regions as (region, display name). */
    fun list(context: Context, onResult: (List<Pair<OfflineRegion, String>>) -> Unit) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                onResult(regions.orEmpty().map { it to nameOf(it) })
            }

            override fun onError(error: String) = onResult(emptyList())
        })
    }

    fun delete(region: OfflineRegion, onDone: () -> Unit) {
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = onDone()
            override fun onError(error: String) = onDone()
        })
    }

    private fun nameOf(region: OfflineRegion): String = runCatching {
        JSONObject(String(region.metadata, StandardCharsets.UTF_8)).optString(META_NAME, "Saved area")
    }.getOrDefault("Saved area")
}
