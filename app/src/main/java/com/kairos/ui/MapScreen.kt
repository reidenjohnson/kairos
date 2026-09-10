package com.kairos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.advice.buildSidePlan
import com.kairos.data.GeoCache
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.MaineGisRepository
import com.kairos.data.Place
import com.kairos.data.WeatherRepository
import com.kairos.engine.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.floor
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The interactive Maine map — OnX-style. A base-map switcher (topo / satellite / street)
 * with toggleable official state overlays: public/conserved land, expanded-archery zones,
 * Wildlife Management Areas, and Wildlife Management Districts. Built on MapLibre Native
 * (open-source, no API key). Base tiles are free/keyless (USGS The National Map + the
 * OpenFreeMap street style); overlays come straight from Maine's own ArcGIS GIS, so the
 * lines/areas are exactly the state's published boundaries — nothing we invent.
 *
 * Honesty: Maine labels these boundaries APPROXIMATE (mapped at 1:24k / 1:3k), "not legal
 * survey lines," and for expanded archery the written description is the legal authority.
 * The feature info sheet shows that disclaimer.
 */

/** The base maps a user can switch between, like OnX. */
internal enum class BaseMap(val label: String) {
    TOPO("Topographic"),
    SATELLITE("Satellite"),
    STREET("Street"),
}

/** One official Maine GIS overlay. Colors are map semantics, not brand primaries. */
internal data class MapOverlay(
    val id: String,
    val label: String,
    val layerUrl: String,
    val color: Color,
    /** Polygon overlays draw a translucent fill + outline; false = outline only (districts). */
    val filled: Boolean,
    /** Server-side geometry generalization in degrees for big statewide layers; null = full res. */
    val generalizeDeg: Double?,
    /** Heavy statewide layers only draw once zoomed in past this, to keep panning smooth. */
    val minZoom: Double? = null,
    /** Property that groups a feature with the rest of its unit — tapping one highlights
     *  the whole thing (OnX-style). e.g. PROJECT ties a park's scattered parcels together. */
    val groupField: String? = null,
    val attribution: String,
    val disclaimer: String,
    val legalLink: String? = null,
)

private const val GIS_HOST =
    "https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services"

/** The v1 overlays — all confirmed live on Maine's official ArcGIS org. Data-driven so
 *  fishing stocking pins / public boat launches drop in later as new entries. */
internal val OVERLAYS: List<MapOverlay> = listOf(
    MapOverlay(
        id = "public-land",
        label = "Public / conserved land",
        layerUrl = "$GIS_HOST/Maine_Conserved_Lands_All/FeatureServer/0",
        color = Color(0xFF2E7D32),
        filled = true,
        generalizeDeg = null, // full resolution — exact state boundaries, no simplification
        minZoom = 9.0, // only DRAW once zoomed in (fidelity unchanged) so a statewide pan stays smooth
        groupField = "PROJECT", // tap one parcel → highlight the whole conservation project
        attribution = "Maine Office of GIS — Conserved Lands",
        disclaimer = "Approximate ownership boundaries (1:24,000), not legal survey lines. Public access is not implied — respect posted and private inholdings.",
    ),
    MapOverlay(
        id = "expanded-archery",
        label = "Expanded archery zones",
        layerUrl = "$GIS_HOST/MaineDIFW_ExpandedArcheryAreas/FeatureServer/0",
        color = Color(0xFFEF6C00),
        filled = true,
        generalizeDeg = null,
        groupField = "NAME",
        attribution = "Maine DIFW — Expanded Archery Areas",
        disclaimer = "Mapped at 1:3,000 and approximate. Where the map and the written boundary description differ, the WRITTEN description is the legal authority.",
        legalLink = "https://www.maine.gov/ifw/hunting-trapping/hunting/species/deer/expanded-archery/index.html",
    ),
    MapOverlay(
        id = "wma",
        label = "Wildlife Mgmt Areas",
        layerUrl = "$GIS_HOST/MaineDIFW_WildlifeManagementAreas/FeatureServer/0",
        color = Color(0xFF00838F),
        filled = true,
        generalizeDeg = null,
        groupField = "PROJECT", // tap a parcel → highlight the whole WMA
        attribution = "Maine DIFW — Wildlife Management Areas",
        disclaimer = "Approximate property boundaries (1:24,000), not legal survey lines.",
    ),
    MapOverlay(
        id = "wmd",
        label = "Wildlife Mgmt Districts",
        layerUrl = "$GIS_HOST/WMD/FeatureServer/0",
        color = Color(0xFF5E35B1),
        filled = false,
        generalizeDeg = null, // full resolution
        minZoom = 7.0,
        attribution = "Maine DIFW — Wildlife Management Districts",
        disclaimer = "The 29 statewide management districts that season dates and permits key off of.",
    ),
)

/**
 * Fetch every overlay to the on-disk cache if it isn't there yet — called in the
 * background on app launch so the map is ready (and offline-capable) the first time it's
 * opened, instead of making the user wait. Best-effort: a failure just means it loads on
 * demand later. BLOCKING — call on [Dispatchers.IO].
 */
internal fun preloadOverlays(context: Context) {
    for (ov in OVERLAYS) {
        if (GeoCache.load(context, ov.id) != null) continue
        runCatching {
            MaineGisRepository.fetchGeoJson(ov.layerUrl, ov.generalizeDeg)
                .also { GeoCache.save(context, ov.id, it) }
        }
    }
}

/** A cleaned-up description of a tapped feature for the info sheet. A null [overlay]
 *  means a bare spot (long-press on open ground) — just the forecast, no property. */
private data class FeatureInfo(
    val overlay: MapOverlay?,
    val title: String,
    val facts: List<Pair<String, String>>,      // compact, always shown
    val moreFacts: List<Pair<String, String>>,  // behind "read more"
    val description: String?,                    // long legal text (read more)
    val links: List<Pair<String, String>>,       // label -> url
    val lat: Double,
    val lon: Double,
)

/** The tapped feature's unit, to highlight all of it (OnX-style). */
private data class Highlight(val overlay: MapOverlay, val field: String, val value: String)

/** The per-spot engine forecast shown in the sheet. */
private sealed interface SpotForecast {
    data object Loading : SpotForecast
    data object Failed : SpotForecast
    data class Ready(
        val huntScore: Int,
        val fishScore: Int,
        val huntWindow: String?,
        val fishWindow: String?,
        val planHeadline: String,
        val planTactic: String,
        val weatherLine: String,
    ) : SpotForecast
}

/** Center of the state as a sensible default until we have the device's location. */
private val MAINE_CENTER = LatLng(Location.LAT, Location.LON)

@Composable
fun MapScreen() {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) } // must init before any MapView; safe to repeat

    var base by remember { mutableStateOf(BaseMap.TOPO) }
    var enabled by remember { mutableStateOf(setOf("public-land", "expanded-archery")) }
    var showLayers by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selected by remember { mutableStateOf<FeatureInfo?>(null) }
    var highlight by remember { mutableStateOf<Highlight?>(null) }
    var spotFx by remember { mutableStateOf<SpotForecast?>(null) }
    val data = remember { mutableStateMapOf<String, String>() } // overlayId -> GeoJSON
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    var downloadPct by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(toast) { if (toast != null) { delay(3500); toast = null } }

    // Save the current view for offline: base tiles for the visible area (+3 zoom levels,
    // capped) and every overlay cached to disk. Street falls back to topo for the tiles.
    val startDownload = start@{
        val m = map ?: return@start
        val bounds = m.projection.visibleRegion.latLngBounds
        val z = m.cameraPosition.zoom
        val dlBase = if (base == BaseMap.STREET) BaseMap.TOPO else base
        enabled = OVERLAYS.map { it.id }.toSet() // load + cache every overlay for offline
        downloadPct = 0
        OfflineMaps.download(
            context = context,
            name = "Saved area",
            base = dlBase,
            styleUri = baseStyleUri(context, dlBase),
            bounds = bounds,
            minZoom = floor(z),
            maxZoom = minOf(z + 3.0, 15.0),
            onProgress = { downloadPct = it },
            onComplete = { downloadPct = null; toast = "Saved for offline use" },
            onError = { downloadPct = null; toast = it },
        )
    }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            onMapReady = { m ->
                map = m
                // Tap a property → identify it, highlight its whole unit, forecast the spot.
                m.addOnMapClickListener { latLng ->
                    val hit = featureAt(m, latLng, enabled)
                    if (hit != null) {
                        val (ov, feat) = hit
                        selected = describeFeature(ov, feat, latLng.latitude, latLng.longitude)
                        highlight = ov.groupField?.let { gf -> feat.str(gf)?.let { Highlight(ov, gf, it) } }
                    }
                    hit != null
                }
                // Long-press anywhere → a quick weather + hunt/fish read for that spot.
                m.addOnMapLongClickListener { latLng ->
                    selected = FeatureInfo(null, "Selected spot", emptyList(), emptyList(), null, emptyList(), latLng.latitude, latLng.longitude)
                    highlight = null
                    true
                }
            },
        )

        // Apply the base style; the overlay sync below re-adds sources onto the new style.
        LaunchedEffect(map, base) {
            val m = map ?: return@LaunchedEffect
            m.setStyle(Style.Builder().fromUri(baseStyleUri(context, base))) { s ->
                enableLocation(context, m, s)
                style = s
            }
        }

        // Center on the device once, if we can get a fix.
        LaunchedEffect(map) {
            val m = map ?: return@LaunchedEffect
            val place = withContext(Dispatchers.IO) { LocationProvider.current(context) }
            val target = place?.let { LatLng(it.lat, it.lon) } ?: MAINE_CENTER
            m.cameraPosition = CameraPosition.Builder().target(target).zoom(if (place != null) 12.0 else 7.0).build()
        }

        // Fetch GeoJSON for any enabled overlay we don't have yet (cache-first).
        LaunchedEffect(enabled) {
            for (ov in OVERLAYS) {
                if (ov.id !in enabled || data.containsKey(ov.id) || loading[ov.id] == true) continue
                loading[ov.id] = true
                val cached = GeoCache.load(context, ov.id)
                val geo = cached ?: runCatching {
                    withContext(Dispatchers.IO) { MaineGisRepository.fetchGeoJson(ov.layerUrl, ov.generalizeDeg) }
                        .also { GeoCache.save(context, ov.id, it) }
                }.getOrNull()
                if (geo != null) data[ov.id] = geo
                loading[ov.id] = false
            }
        }

        // Keep the map's sources/layers in step with the toggles + the data we've loaded.
        LaunchedEffect(style, enabled, data.keys.toList()) {
            style?.let { syncOverlays(it, enabled, data) }
        }

        // Highlight the tapped unit (OnX-style) on the current style.
        LaunchedEffect(style, highlight) {
            style?.let { applyHighlight(it, highlight) }
        }

        // Run the engine for the tapped/long-pressed spot: weather + hunt/fish + a plan.
        LaunchedEffect(selected?.lat, selected?.lon) {
            val sel = selected
            if (sel == null) { spotFx = null; return@LaunchedEffect }
            spotFx = SpotForecast.Loading
            spotFx = loadSpotForecast(sel.lat, sel.lon, sel.title)
        }

        if (loading.values.any { it }) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = KairosColors.Surface,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = KairosColors.Pine)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading map data…", style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
                }
            }
        }

        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            MapIconButton(Icons.Outlined.Layers, "Map layers") { showLayers = true }
            Spacer(Modifier.height(10.dp))
            MapIconButton(Icons.Outlined.Download, "Save this area for offline", onClick = startDownload)
        }

        // Recenter on the device's GPS location, like Google Maps.
        MapIconButton(
            icon = Icons.Outlined.MyLocation,
            desc = "Center on my location",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
        ) {
            val m = map ?: return@MapIconButton
            scope.launch {
                val place = withContext(Dispatchers.IO) { LocationProvider.current(context) } ?: return@launch
                m.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 13.0),
                )
            }
        }

        (downloadPct?.let { "Downloading map… $it%" } ?: toast)?.let { msg ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                shape = RoundedCornerShape(999.dp),
                color = KairosColors.Surface,
                shadowElevation = 3.dp,
            ) {
                Text(msg, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
            }
        }

        if (showLayers) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickableNoRipple { showLayers = false })
            LayerSheet(
                base = base,
                enabled = enabled,
                onPickBase = { base = it },
                onToggleOverlay = { id -> enabled = if (id in enabled) enabled - id else enabled + id },
                onDismiss = { showLayers = false },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        selected?.let { info ->
            Box(Modifier.fillMaxSize().clickableNoRipple { selected = null; highlight = null })
            FeatureSheet(
                info = info,
                forecast = spotFx,
                onDismiss = { selected = null; highlight = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** A click with no ripple, for full-screen scrims. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
)

/** The MapLibre [MapView] hosted in Compose, with its Android lifecycle wired up. */
@Composable
private fun MapLibreView(onMapReady: (MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { onMapReady(it) }
        }
    }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
}

/** Turn on the blue "you are here" dot, if the user has granted location. */
@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
    if (!LocationProvider.hasPermission(context)) return
    runCatching {
        val lc = map.locationComponent
        lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
        lc.renderMode = RenderMode.COMPASS
    }
}

// ---- Overlay rendering ---------------------------------------------------------------

/** Add/update/remove overlay sources + layers on [style] to match [enabled] + loaded [data]. */
private fun syncOverlays(style: Style, enabled: Set<String>, data: Map<String, String>) {
    for (ov in OVERLAYS) {
        val srcId = "ov-${ov.id}"
        val fillId = "$srcId-fill"
        val lineId = "$srcId-line"
        val geo = data[ov.id]
        val want = ov.id in enabled && geo != null
        val hasSrc = style.getSource(srcId) != null
        if (want && !hasSrc) {
            style.addSource(GeoJsonSource(srcId, geo))
            if (ov.filled) {
                val fill = FillLayer(fillId, srcId).withProperties(
                    PropertyFactory.fillColor(ov.color.toArgb()),
                    PropertyFactory.fillOpacity(0.28f),
                )
                ov.minZoom?.let { fill.setMinZoom(it.toFloat()) }
                style.addLayer(fill)
            }
            val line = LineLayer(lineId, srcId).withProperties(
                PropertyFactory.lineColor(ov.color.toArgb()),
                PropertyFactory.lineWidth(if (ov.filled) 1.6f else 2.4f),
            )
            ov.minZoom?.let { line.setMinZoom(it.toFloat()) }
            style.addLayer(line)
        } else if (want && hasSrc) {
            (style.getSourceAs<GeoJsonSource>(srcId))?.setGeoJson(geo)
        } else if (!want && hasSrc) {
            style.getLayer(fillId)?.let { style.removeLayer(it) }
            style.getLayer(lineId)?.let { style.removeLayer(it) }
            style.removeSource(srcId)
        }
    }
}

/** Highlight the whole tapped unit (all features sharing its group value) with a bold
 *  outline over its own source, OnX-style. Removes any prior highlight first. */
private fun applyHighlight(style: Style, highlight: Highlight?) {
    style.getLayer("hl-line")?.let { style.removeLayer(it) }
    style.getLayer("hl-fill")?.let { style.removeLayer(it) }
    if (highlight == null) return
    val srcId = "ov-${highlight.overlay.id}"
    if (style.getSource(srcId) == null) return
    val filter = org.maplibre.android.style.expressions.Expression.eq(
        org.maplibre.android.style.expressions.Expression.get(highlight.field),
        org.maplibre.android.style.expressions.Expression.literal(highlight.value),
    )
    val fill = FillLayer("hl-fill", srcId).withProperties(
        PropertyFactory.fillColor(highlight.overlay.color.toArgb()),
        PropertyFactory.fillOpacity(0.35f),
    ).withFilter(filter)
    val line = LineLayer("hl-line", srcId).withProperties(
        PropertyFactory.lineColor(android.graphics.Color.WHITE),
        PropertyFactory.lineWidth(3.0f),
    ).withFilter(filter)
    style.addLayer(fill)
    style.addLayer(line)
}

/** Run the engine at a tapped spot: fetch its weather, score hunt + fish, build a plan. */
private suspend fun loadSpotForecast(lat: Double, lon: Double, label: String): SpotForecast {
    val fx = runCatching {
        withContext(Dispatchers.IO) { WeatherRepository.fetch(Place(lat, lon, label)) }
    }.getOrNull() ?: return SpotForecast.Failed
    val timing = fx.timing
    val hunt = timing?.scoreForSide(Side.HUNT) ?: 0
    val fish = timing?.scoreForSide(Side.FISH) ?: 0
    val bestSide = if (fish >= hunt) Side.FISH else Side.HUNT
    val plan = buildSidePlan(bestSide, fx.conditions, LocalDate.now(), timing, fx.precipMmHr)
    val c = fx.conditions
    val trend = when {
        c.pressureTrendInHg < -0.03 -> "falling"
        c.pressureTrendInHg > 0.03 -> "rising"
        else -> "steady"
    }
    val weather = "${c.airF.toInt()}° · wind ${c.windMph.toInt()} mph · $trend"
    return SpotForecast.Ready(
        huntScore = hunt,
        fishScore = fish,
        huntWindow = spotWindow(timing, Side.HUNT),
        fishWindow = spotWindow(timing, Side.FISH),
        planHeadline = plan.headline,
        planTactic = plan.tacticLine,
        weatherLine = weather,
    )
}

private fun spotWindow(timing: com.kairos.data.DayTiming?, side: Side): String? {
    val w = timing?.bestWindows(side)?.firstOrNull() ?: return null
    return "${spotHour(w.first)}–${spotHour(w.last + 1)}"
}

private fun spotHour(h: Int): String = when {
    h == 0 || h == 24 -> "12 AM"
    h == 12 -> "12 PM"
    h < 12 -> "$h AM"
    else -> "${h - 12} PM"
}

/** A property's value as a clean string, or null. Skips JSON-null / "unknown" / blanks —
 *  and never throws (MapLibre's getStringProperty throws on a JsonNull). */
private fun org.maplibre.geojson.Feature.str(key: String): String? {
    val el = properties()?.get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    val v = el.asString.trim()
    return v.takeIf { it.isNotBlank() && !it.equals("unknown", true) && it != "0" }
}

/** Like [str], but also rejects a value that's just a number (e.g. a tax-map parcel id),
 *  so a feature's title falls back to a real name. */
private fun org.maplibre.geojson.Feature.name(key: String): String? =
    str(key)?.takeUnless { v -> v.all { it.isDigit() || it == '-' || it == ' ' } }

/** Acreage from the reported or calculated field, formatted. */
private fun org.maplibre.geojson.Feature.acres(): String? {
    val a = str("RPT_AC")?.toDoubleOrNull() ?: str("CALC_AC")?.toDoubleOrNull() ?: return null
    if (a < 0.5) return null
    return "%,d acres".format(a.toInt())
}

private fun titleCase(s: String): String =
    s.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/** Which overlay feature sits under the tap (topmost enabled overlay), if any. */
private fun featureAt(map: MapLibreMap, latLng: LatLng, enabled: Set<String>): Pair<MapOverlay, org.maplibre.geojson.Feature>? {
    val point: PointF = map.projection.toScreenLocation(latLng)
    for (ov in OVERLAYS) {
        if (ov.id !in enabled) continue
        val layerId = "ov-${ov.id}-" + if (ov.filled) "fill" else "line"
        val f = runCatching { map.queryRenderedFeatures(point, layerId) }.getOrNull().orEmpty().firstOrNull()
        if (f != null) return ov to f
    }
    return null
}

/** Build the clean, labeled description for a tapped feature — per overlay, using the
 *  real fields, with the bulky text tucked into "read more". */
private fun describeFeature(ov: MapOverlay, f: org.maplibre.geojson.Feature, lat: Double, lon: Double): FeatureInfo = when (ov.id) {
    "public-land" -> FeatureInfo(
        overlay = ov,
        title = f.name("PARCEL_NAME") ?: f.name("PROJECT") ?: f.str("HOLD1_NAME") ?: "Conserved land",
        facts = listOfNotNull(
            f.str("HOLD1_NAME")?.let { "Owner" to it },
            f.str("PUB_ACCESS")?.let { "Access" to it },
            f.str("CONS1_TYPE")?.let { "Interest" to it },
            f.acres()?.let { "Size" to it },
        ),
        moreFacts = listOfNotNull(
            f.str("PROJECT")?.let { "Project" to it },
            f.str("DESIGNATION")?.let { "Designation" to it },
            f.str("HOLD1_TYPE")?.let { "Owner type" to it },
            f.str("GAP_STATUS")?.let { "Protection" to it },
            f.str("PURPOSE1")?.let { "Purpose" to it },
            f.str("ACQ_YEAR")?.let { "Acquired" to it },
        ),
        description = null,
        links = emptyList(),
        lat = lat, lon = lon,
    )
    "wma" -> FeatureInfo(
        overlay = ov,
        title = f.name("PROJECT") ?: f.name("PARCEL_NAME") ?: "Wildlife Management Area",
        facts = listOfNotNull(
            f.str("HOLD1_NAME")?.let { "Managed by" to it },
            f.str("PUB_ACCESS")?.let { "Access" to it },
            f.acres()?.let { "Size" to it },
        ),
        moreFacts = listOfNotNull(
            f.str("PARCEL_NAME")?.let { "Parcel" to it },
            f.str("DESIGNATION")?.let { "Designation" to it },
            f.str("PURPOSE1")?.let { "Purpose" to it },
            f.str("ACQ_YEAR")?.let { "Acquired" to it },
        ),
        description = null,
        links = emptyList(),
        lat = lat, lon = lon,
    )
    "expanded-archery" -> FeatureInfo(
        overlay = ov,
        title = f.str("NAME")?.let { titleCase(it) } ?: "Expanded archery zone",
        facts = listOfNotNull(f.str("TownsIncluded")?.let { "Towns" to it }),
        moreFacts = emptyList(),
        description = f.str("Description"), // the long legal boundary — read more
        links = listOfNotNull(ov.legalLink?.let { "Official expanded-archery info (Maine IF&W)" to it }),
        lat = lat, lon = lon,
    )
    "wmd" -> FeatureInfo(
        overlay = ov,
        title = "Wildlife Management District ${f.str("IDENTIFIER") ?: "?"}",
        facts = emptyList(),
        moreFacts = listOfNotNull(
            f.str("AREASQMI")?.toDoubleOrNull()?.let { "Area" to "%,d sq mi".format(it.toInt()) },
        ),
        description = null,
        links = listOfNotNull(
            f.str("MapPDF")?.let { "District map (PDF)" to it },
            f.str("DescriptionPDF")?.let { "Written boundary description (PDF)" to it },
        ),
        lat = lat, lon = lon,
    )
    else -> FeatureInfo(ov, ov.label, emptyList(), emptyList(), null, emptyList(), lat, lon)
}

// ---- Base map style JSON -------------------------------------------------------------

private const val STREET_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

/** USGS The National Map raster tiles — public domain, keyless. ArcGIS tile order is
 *  {z}/{row}/{col}, which maps to MapLibre's {z}/{y}/{x}. */
private const val USGS_TOPO =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"
private const val USGS_IMAGERY =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"

/**
 * The style URI for a base map. The raster (USGS) styles are written to a local file so
 * the SAME uri drives both on-screen display and MapLibre's offline downloader (which
 * needs a resolvable style URL, not inline JSON). Street uses OpenFreeMap's hosted style.
 */
internal fun baseStyleUri(context: Context, base: BaseMap): String = when (base) {
    BaseMap.STREET -> STREET_STYLE_URI
    BaseMap.TOPO -> writeStyleFile(context, "style_topo.json", USGS_TOPO, "USGS The National Map (topo)")
    BaseMap.SATELLITE -> writeStyleFile(context, "style_sat.json", USGS_IMAGERY, "USGS The National Map (imagery)")
}

private fun writeStyleFile(context: Context, name: String, tileUrl: String, attribution: String): String {
    val f = java.io.File(context.filesDir, name)
    f.writeText(rasterStyleJson(tileUrl, attribution)) // static content; cheap to rewrite
    return "file://${f.absolutePath}"
}

/** A minimal MapLibre style with a single full-screen raster layer from an XYZ endpoint. */
private fun rasterStyleJson(tileUrl: String, attribution: String): String = """
{
  "version": 8,
  "sources": {
    "base": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": 256,
      "maxzoom": 16,
      "attribution": "$attribution"
    }
  },
  "layers": [
    { "id": "base", "type": "raster", "source": "base" }
  ]
}
""".trimIndent()

// ---- The layer control (OnX-style) ---------------------------------------------------

@Composable
private fun MapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = KairosColors.Surface,
        shadowElevation = 4.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = KairosColors.Pine)
        }
    }
}

@Composable
private fun LayerSheet(
    base: BaseMap,
    enabled: Set<String>,
    onPickBase: (BaseMap) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KairosColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Map layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = KairosColors.Dim)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("BASE MAP", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BaseMap.entries.forEach { b ->
                    BasePill(label = b.label, active = b == base, onClick = { onPickBase(b) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("OVERLAYS", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            OVERLAYS.forEach { ov ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(ov.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(ov.label, style = MaterialTheme.typography.bodyLarge, color = KairosColors.Text, modifier = Modifier.weight(1f))
                    Switch(checked = ov.id in enabled, onCheckedChange = { onToggleOverlay(ov.id) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Official Maine GIS (Maine Office of GIS · MDIFW). Boundaries are approximate — not legal survey lines.",
                style = MaterialTheme.typography.labelSmall,
                color = KairosColors.Faint,
            )
        }
    }
}

@Composable
private fun FeatureSheet(info: FeatureInfo, forecast: SpotForecast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember(info) { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KairosColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                info.overlay?.let {
                    Box(Modifier.size(12.dp).background(it.color, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    info.overlay?.label ?: "Spot forecast",
                    style = MaterialTheme.typography.labelMedium,
                    color = KairosColors.Dim,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = KairosColors.Dim)
                }
            }
            Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KairosColors.Text)

            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (info.facts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    info.facts.forEach { (k, v) -> FactRow(k, v) }
                }

                // The engine forecast for this exact spot.
                Spacer(Modifier.height(14.dp))
                ForecastBlock(forecast)

                // Read more: the detail fields, the long legal text, and official links.
                val hasMore = info.moreFacts.isNotEmpty() || info.description != null || info.links.isNotEmpty()
                if (hasMore) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = KairosColors.Water,
                        modifier = Modifier.clickableNoRipple { expanded = !expanded }.padding(vertical = 6.dp),
                    )
                    if (expanded) {
                        info.moreFacts.forEach { (k, v) -> FactRow(k, v) }
                        info.description?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Text, lineHeight = 18.sp)
                        }
                        info.links.forEach { (label, url) ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = KairosColors.Water,
                                modifier = Modifier.clickableNoRipple { uriHandler.openUri(url) },
                            )
                        }
                    }
                }

                info.overlay?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it.disclaimer, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, lineHeight = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(it.attribution, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Faint, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

/** Hunt + fish scores, best windows, weather, and a game plan for the tapped spot. */
@Composable
private fun ForecastBlock(forecast: SpotForecast?) {
    Surface(shape = RoundedCornerShape(14.dp), color = KairosColors.Bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            when (forecast) {
                null, SpotForecast.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = KairosColors.Pine)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading conditions for this spot…", style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
                }
                SpotForecast.Failed -> Text(
                    "Couldn't load the forecast for here — check your connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KairosColors.Dim,
                )
                is SpotForecast.Ready -> {
                    Row(Modifier.fillMaxWidth()) {
                        ScorePill("Hunt", forecast.huntScore, forecast.huntWindow, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        ScorePill("Fish", forecast.fishScore, forecast.fishWindow, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(forecast.weatherLine, style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
                    Spacer(Modifier.height(10.dp))
                    Text(forecast.planHeadline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = KairosColors.Text, lineHeight = 19.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(forecast.planTactic, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, score: Int, window: String?, modifier: Modifier = Modifier) {
    val color = ratingColor(com.kairos.engine.rating(score))
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = color)
            window?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = KairosColors.Dim, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun BasePill(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (active) KairosColors.SegBottom else KairosColors.Bg,
        border = if (active) null else BorderStroke(1.dp, KairosColors.Line),
        onClick = onClick,
    ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) KairosColors.OnSeg else KairosColors.Dim,
            )
        }
    }
}
