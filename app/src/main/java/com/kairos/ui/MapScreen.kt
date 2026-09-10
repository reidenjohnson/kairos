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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.data.GeoCache
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.MaineGisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

/** What a tapped feature shows in the info sheet. */
private data class FeatureInfo(val overlay: MapOverlay, val name: String?, val details: String?)

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
    val data = remember { mutableStateMapOf<String, String>() } // overlayId -> GeoJSON
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    var downloadPct by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

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
                m.addOnMapClickListener { latLng ->
                    selected = queryFeature(m, latLng, enabled)
                    selected != null
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
            Box(Modifier.fillMaxSize().clickableNoRipple { selected = null })
            FeatureSheet(info = info, onDismiss = { selected = null }, modifier = Modifier.align(Alignment.BottomCenter))
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

private val NAME_KEYS = listOf("name", "owner", "unit", "wmd", "district", "area", "label")
private val DETAIL_KEYS = listOf("description", "descriptio", "legal", "towns", "town", "acres", "type")

/** First property whose key matches any of [keys] and has a non-blank value. Reads the
 *  JSON element directly and skips JSON-null / non-string values — MapLibre's
 *  getStringProperty() throws on a JsonNull, which was crashing the tap handler. */
private fun org.maplibre.geojson.Feature.pick(keys: List<String>): String? {
    val props = properties() ?: return null
    for (key in props.keySet()) {
        if (keys.none { key.contains(it, ignoreCase = true) }) continue
        val el = props.get(key) ?: continue
        if (el.isJsonNull || !el.isJsonPrimitive) continue
        val value = el.asString
        if (value.isNotBlank()) return value
    }
    return null
}

/** Which overlay (if any) sits under the tap, plus a best-effort feature name + details. */
private fun queryFeature(map: MapLibreMap, latLng: LatLng, enabled: Set<String>): FeatureInfo? {
    val point: PointF = map.projection.toScreenLocation(latLng)
    for (ov in OVERLAYS) {
        if (ov.id !in enabled) continue
        val layerId = "ov-${ov.id}-" + if (ov.filled) "fill" else "line"
        val feats = runCatching { map.queryRenderedFeatures(point, layerId) }.getOrNull().orEmpty()
        val f = feats.firstOrNull() ?: continue
        return FeatureInfo(ov, name = f.pick(NAME_KEYS), details = f.pick(DETAIL_KEYS))
    }
    return null
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
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(48.dp),
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
private fun FeatureSheet(info: FeatureInfo, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KairosColors.Surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(info.overlay.color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Text(info.overlay.label, style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = KairosColors.Dim)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                info.name ?: info.overlay.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = KairosColors.Text,
            )
            // Long legal text (e.g. the expanded-archery written boundary) scrolls.
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                info.details?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = KairosColors.Text, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(info.overlay.disclaimer, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(info.overlay.attribution, style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint)
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
