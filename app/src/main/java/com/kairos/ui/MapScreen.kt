package com.kairos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.runtime.snapshotFlow
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
import com.kairos.R
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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
/** The three distinct views a hunter needs — a shaded topo, a labeled satellite (hybrid), and
 *  raw imagery. Kept deliberately minimal; earlier extra styles were redundant. */
internal enum class BaseMap(val label: String, val thumb: Int) {
    SHADED("Shaded Topo", R.drawable.base_shaded),  // USGS topo + Esri hillshade — 3D relief (CalTopo look)
    HYBRID("Hybrid", R.drawable.base_hybrid),       // USGS Imagery Topo — satellite + labels/contours
    AERIAL("Satellite", R.drawable.base_aerial),    // Esri World Imagery — sharp raw imagery
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
    /** ArcGIS attribute filter — narrows a national dataset to Maine's slice. */
    val where: String = "1=1",
    /** Heavy statewide layers only draw once zoomed in past this, to keep panning smooth. */
    val minZoom: Double? = null,
    /** Property that groups a feature with the rest of its unit — tapping one highlights
     *  the whole thing (OnX-style). e.g. PROJECT ties a park's scattered parcels together. */
    val groupField: String? = null,
    /** Fields (first non-null wins) used to label the unit on the map once zoomed in to
     *  property level — empty = no labels. Kept off until ~z12 so the map isn't word-noise. */
    val labelFields: List<String> = emptyList(),
    val attribution: String,
    val disclaimer: String,
    val legalLink: String? = null,
)

private const val GIS_HOST =
    "https://services1.arcgis.com/RbMX0mRVOFNTdLzd/arcgis/rest/services"

/** Server-side filter for the "Hunting land (verified)" layer — Maine's ArcGIS returns only the
 *  huntable parcels, so the download is small and there's nothing to parse/classify on the phone.
 *  Mirrors the confident (GOOD) authority reads in [huntingStatus]: IF&W land, National Forest,
 *  State Public Reserved Land, the record listing hunting as a use, plus hand-verified names
 *  (Knight's Pond) — minus any parcel whose access note says no hunting. Keep in sync with
 *  [HuntingOverrides] when adding curated open parcels. */
private const val HUNTABLE_WHERE =
    "(IFW_ID IS NOT NULL OR UPPER(HOLD1_NAME) LIKE '%INLAND FISHERIES%' " +
    "OR UPPER(HOLD1_NAME) LIKE '%FOREST SERVICE%' OR UPPER(DESIGNATION) LIKE '%NATIONAL FOREST%' " +
    "OR UPPER(PURPOSE1) LIKE '%HUNT%' OR UPPER(PURPOSE2) LIKE '%HUNT%' " +
    "OR (UPPER(HOLD1_NAME) LIKE '%BUREAU OF PARKS%' AND (UPPER(DESIGNATION) LIKE '%PUBLIC%' OR UPPER(DESIGNATION) LIKE '%RESERVED%')) " +
    "OR UPPER(PROJECT)='KNIGHT''S POND PRESERVE') " +
    "AND (PUB_ACCESS IS NULL OR UPPER(PUB_ACCESS) NOT LIKE '%NO HUNTING%')"

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
        minZoom = 7.0, // draw a couple zoom levels sooner so zones show without pinching way in
        groupField = "PROJECT", // tap one parcel → highlight the whole conservation project
        labelFields = listOf("PROJECT", "PARCEL_NAME"), // common name first, tax id only as fallback
        attribution = "Maine Office of GIS — Conserved Lands",
        disclaimer = "Approximate ownership boundaries (1:24,000), not legal survey lines. Public access is not implied — respect posted and private inholdings.",
    ),
    MapOverlay(
        id = "hunting-verified",
        label = "Hunting land (verified)",
        layerUrl = "$GIS_HOST/Maine_Conserved_Lands_All/FeatureServer/0",
        where = HUNTABLE_WHERE, // ArcGIS returns only huntable parcels — no on-device filtering
        color = Color(0xFF00C853), // vivid "go" green — only ground confirmed open to hunting
        filled = true,
        generalizeDeg = null,
        minZoom = 7.0,
        groupField = "PROJECT",
        labelFields = listOf("PROJECT", "PARCEL_NAME"),
        attribution = "Maine Office of GIS — Conserved Lands (huntable subset)",
        disclaimer = "Only parcels open to hunting: by owner authority (IF&W, State Public Reserved Land, National Forest), the state record listing hunting as a use, or our hand-verified list. Confirm posted rules before you hunt.",
    ),
    MapOverlay(
        id = "expanded-archery",
        label = "Expanded archery zones",
        layerUrl = "$GIS_HOST/MaineDIFW_ExpandedArcheryAreas/FeatureServer/0",
        color = Color(0xFFEF6C00),
        filled = true,
        generalizeDeg = null,
        groupField = "NAME",
        labelFields = listOf("NAME"),
        attribution = "Maine DIFW — Expanded Archery Areas",
        disclaimer = "Mapped at 1:3,000 and approximate. Where the map and the written boundary description differ, the WRITTEN description is the legal authority.",
        legalLink = "https://www.maine.gov/ifw/hunting-trapping/hunting/species/deer/expanded-archery/index.html",
    ),
    MapOverlay(
        id = "wma",
        label = "Wildlife Mgmt Areas",
        layerUrl = "$GIS_HOST/MaineDIFW_WildlifeManagementAreas/FeatureServer/0",
        color = Color(0xFF1565C0), // blue — distinct from the greens (public land / national forest)
        filled = true,
        generalizeDeg = null,
        groupField = "PROJECT", // tap a parcel → highlight the whole WMA
        labelFields = listOf("PROJECT", "PARCEL_NAME"),
        attribution = "Maine DIFW — Wildlife Management Areas",
        disclaimer = "Approximate property boundaries (1:24,000), not legal survey lines.",
    ),
    MapOverlay(
        id = "national-forest",
        label = "National Forest",
        layerUrl = "https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_ForestSystemBoundaries_01/MapServer/0",
        color = Color(0xFF00897B), // teal-green — clearly separate from the public-land green
        filled = true,
        generalizeDeg = null,
        where = "FORESTNAME='White Mountain National Forest'", // the only NF touching Maine
        minZoom = 6.0,
        groupField = "forestname",
        labelFields = listOf("forestname", "FORESTNAME"),
        attribution = "USDA Forest Service — Administrative Forest Boundaries",
        disclaimer = "National Forest System land (the White Mountain NF reaches into western Maine). General administrative boundary — check the district map and posted rules before you hunt.",
        legalLink = "https://www.fs.usda.gov/whitemountain",
    ),
    MapOverlay(
        id = "wmd",
        label = "Wildlife Mgmt Districts",
        layerUrl = "$GIS_HOST/WMD/FeatureServer/0",
        color = Color(0xFF5E35B1),
        filled = false,
        generalizeDeg = null, // full resolution
        minZoom = 7.0,
        groupField = "IDENTIFIER", // tap a district line → highlight that whole district
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
            MaineGisRepository.fetchGeoJson(ov.layerUrl, ov.generalizeDeg, ov.where)
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
    val hunting: HuntingStatus? = null,          // derived hunting read, for land parcels
)

/** Tone of the derived hunting read — drives the chip color. */
private enum class HuntTone { GOOD, CAUTION, NO, UNKNOWN }

/** A best-effort, HONESTLY-LABELED read of whether you can hunt a land parcel, derived from
 *  the state's own record (access note, listed purposes, owner type, designation). Never an
 *  authority — always paired with "confirm" + a link. Maine's rule is look-it-up per area. */
private data class HuntingStatus(val label: String, val tone: HuntTone, val note: String)

/** The tapped feature's unit, to highlight all of it (OnX-style). */
private data class Highlight(val overlay: MapOverlay, val field: String, val value: String)

/** One overlapping layer under a tap: its info + the highlight for it. When a tap hits more
 *  than one (e.g. public land under a national forest), the sheet lists them so you can pick. */
private data class SpotChoice(val info: FeatureInfo, val highlight: Highlight?)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) } // must init before any MapView; safe to repeat

    var base by remember { mutableStateOf(MapPrefs.loadBase(context)) }
    var enabled by remember { mutableStateOf(MapPrefs.loadEnabled(context)) }
    // Persist the map choices so they survive leaving the screen + app restarts.
    LaunchedEffect(base) { MapPrefs.saveBase(context, base) }
    LaunchedEffect(enabled) { MapPrefs.saveEnabled(context, enabled) }
    var showLayers by remember { mutableStateOf(false) }
    // Two-finger-hold distance measure: the two finger points (screen px) + yardage between them.
    var measureA by remember { mutableStateOf<Offset?>(null) }
    var measureB by remember { mutableStateOf<Offset?>(null) }
    var measureYd by remember { mutableStateOf<Int?>(null) }
    var measureDone by remember { mutableStateOf(false) } // fingers lifted → linger, then clear
    // Keep the measured line up for a few seconds after release, then clear it.
    LaunchedEffect(measureDone, measureA, measureB) {
        if (measureDone && measureA != null) {
            delay(6000)
            measureA = null; measureB = null; measureYd = null; measureDone = false
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selected by remember { mutableStateOf<FeatureInfo?>(null) }
    var highlight by remember { mutableStateOf<Highlight?>(null) }
    // When a tap overlaps several layers, all of them (for the in-sheet chooser); which one
    // is showing is [choiceIdx]. A single hit leaves [choices] size 1 (chooser stays hidden).
    var choices by remember { mutableStateOf<List<SpotChoice>>(emptyList()) }
    var choiceIdx by remember { mutableStateOf(0) }
    var spotFx by remember { mutableStateOf<SpotForecast?>(null) }
    val data = remember { mutableStateMapOf<String, String>() } // overlayId -> GeoJSON
    val loading = remember { mutableStateMapOf<String, Boolean>() }
    var downloadPct by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(toast) { if (toast != null) { delay(3500); toast = null } }

    // Save the current view for offline: base tiles for the visible area (+3 zoom levels,
    // capped) and every overlay cached to disk. All base maps are raster now, so any saves.
    val startDownload = start@{
        val m = map ?: return@start
        val bounds = m.projection.visibleRegion.latLngBounds
        val z = m.cameraPosition.zoom
        val dlBase = base
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

    // A standard (non-modal) bottom sheet so it can COLLAPSE to a small peek instead of
    // dismissing: swipe down minimizes it (the selection + map outline stay), swipe it all
    // the way off — or tap the X — to clear. Driven by [selected].
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
        ),
    )
    LaunchedEffect(selected != null) {
        // Open showing the full info; the user can swipe down to the small peek (which keeps
        // the selection + outline on the map) or all the way off to close.
        if (selected != null) scaffoldState.bottomSheetState.expand()
        else scaffoldState.bottomSheetState.hide()
    }
    // Swiping the sheet fully away is a "close" — clear the selection + its highlight/pin.
    LaunchedEffect(Unit) {
        snapshotFlow { scaffoldState.bottomSheetState.currentValue }
            .collect { if (it == SheetValue.Hidden && selected != null) { selected = null; highlight = null; choices = emptyList() } }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selected != null) 172.dp else 0.dp,
        sheetContainerColor = KairosColors.Surface,
        sheetContent = {
            selected?.let { info ->
                FeatureSheetContent(
                    info = info,
                    forecast = spotFx,
                    choices = choices,
                    activeIndex = choiceIdx,
                    onPick = { i -> choiceIdx = i; selected = choices[i].info; highlight = choices[i].highlight },
                    onClose = { selected = null; highlight = null; choices = emptyList() },
                )
            } ?: Spacer(Modifier.height(1.dp))
        },
    ) { _ ->
    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            onMeasure = { a, b, yd, done -> measureA = a; measureB = b; measureYd = yd; measureDone = done },
            onMapReady = { m ->
                map = m
                // Tap a property → identify it, highlight its whole unit, forecast the spot.
                m.addOnMapClickListener { latLng ->
                    val hits = featuresAt(m, latLng, enabled)
                    if (hits.isNotEmpty()) {
                        // Build a choice per overlapping layer (its info + its highlight); the
                        // sheet lists them when there's more than one so you can pick by color.
                        choices = hits.map { (ov, feat) ->
                            val hl = (ov.groupField?.let { gf -> feat.str(gf)?.let { Highlight(ov, gf, it) } })
                                ?: feat.str("OBJECTID")?.let { Highlight(ov, "OBJECTID", it) }
                            SpotChoice(describeFeature(ov, feat, latLng.latitude, latLng.longitude), hl)
                        }
                        choiceIdx = 0
                        selected = choices[0].info
                        highlight = choices[0].highlight
                    } else {
                        // Tapped open ground → clear the current property selection.
                        choices = emptyList(); selected = null; highlight = null
                    }
                    true
                }
                // Long-press is ONLY for open ground (a bare spot forecast); on a property a
                // normal tap already selects it, so ignore a long-press that lands on a zone.
                m.addOnMapLongClickListener { latLng ->
                    if (featuresAt(m, latLng, enabled).isNotEmpty()) return@addOnMapLongClickListener false
                    choices = emptyList()
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
                    withContext(Dispatchers.IO) { MaineGisRepository.fetchGeoJson(ov.layerUrl, ov.generalizeDeg, ov.where) }
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

        // Highlight the tapped unit (OnX-style) on the current style. triggerRepaint forces the
        // map to draw the new layer now, instead of waiting for the next camera move.
        LaunchedEffect(style, highlight) {
            style?.let { applyHighlight(it, highlight) }
            map?.triggerRepaint()
        }

        // Drop a pin exactly where the forecast was taken (tap or long-press), so you can
        // see the spot the numbers belong to. Cleared when the sheet closes.
        LaunchedEffect(style, selected?.lat, selected?.lon) {
            style?.let { applySpotPin(it, selected?.lat, selected?.lon) }
            map?.triggerRepaint()
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

        // Passive draw layer for the two-finger measure line (no touch handling of its own, so
        // it never blocks the map — the gesture is detected on the MapView itself).
        MeasureLineOverlay(measureA, measureB, measureYd)

        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            MapIconButton(Icons.Outlined.Layers, "Map layers") { showLayers = true }
            Spacer(Modifier.height(10.dp))
            MapIconButton(Icons.Outlined.Download, "Save this area for offline", onClick = startDownload)
        }

        // Reset-to-north (compass) + recenter-on-me, stacked bottom-right like Google Maps.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapIconButton(Icons.Outlined.Explore, "Reset map to north") {
                val m = map ?: return@MapIconButton
                val cp = m.cameraPosition
                m.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(cp.target).zoom(cp.zoom).bearing(0.0).tilt(0.0).build(),
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            MapIconButton(Icons.Outlined.MyLocation, "Center on my location") {
                val m = map ?: return@MapIconButton
                scope.launch {
                    val place = withContext(Dispatchers.IO) { LocationProvider.current(context) } ?: return@launch
                    m.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 13.0),
                    )
                }
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
            ModalBottomSheet(
                onDismissRequest = { showLayers = false }, // swipe down or tap the scrim to close
                containerColor = KairosColors.Surface,
            ) {
                LayerSheet(
                    base = base,
                    enabled = enabled,
                    onPickBase = { base = it },
                    onToggleOverlay = { id -> enabled = if (id in enabled) enabled - id else enabled + id },
                    onDismiss = { showLayers = false },
                )
            }
        }

    }
    } // BottomSheetScaffold
}

/** A click with no ripple, for full-screen scrims. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
)

/** The MapLibre [MapView] hosted in Compose, with its Android lifecycle wired up. [onMeasure]
 *  reports the two-finger-hold distance line (null args = cleared). */
@Composable
private fun MapLibreView(
    onMapReady: (MapLibreMap) -> Unit,
    onMeasure: (Offset?, Offset?, Int?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gesture = remember { MeasureGesture() }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                onMapReady(m)
                // Detect a two-finger HOLD at the touch layer: a still two-finger press measures
                // (consumes the touch); a normal pinch/pan is left to the map to zoom/scroll.
                @SuppressLint("ClickableViewAccessibility")
                setOnTouchListener { _, ev -> gesture.onTouch(ev, m, onMeasure) }
            }
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
        val labelId = "$srcId-label"
        val geo = data[ov.id]
        val want = ov.id in enabled && geo != null
        val hasSrc = style.getSource(srcId) != null
        if (want && !hasSrc) {
            style.addSource(GeoJsonSource(srcId, geo))
            if (ov.filled) {
                // Fill only — NO per-parcel outline. Big units (e.g. Scarborough Marsh WMA) are
                // hundreds of separate acquisition parcels; outlining each drew a mess of interior
                // lines. The uniform fill makes abutting parcels read as one shape (exact
                // boundaries kept, nothing generalized); a crisp outline still appears on tap.
                val fill = FillLayer(fillId, srcId).withProperties(
                    PropertyFactory.fillColor(ov.color.toArgb()),
                    PropertyFactory.fillOpacity(0.30f),
                )
                ov.minZoom?.let { fill.setMinZoom(it.toFloat()) }
                style.addLayer(fill)
            } else {
                // Unfilled layers (management districts) are the boundary line itself.
                val line = LineLayer(lineId, srcId).withProperties(
                    PropertyFactory.lineColor(ov.color.toArgb()),
                    PropertyFactory.lineWidth(2.4f),
                )
                ov.minZoom?.let { line.setMinZoom(it.toFloat()) }
                style.addLayer(line)
            }
            // Name label — only once zoomed to property level, so the map isn't word-noise.
            if (ov.labelFields.isNotEmpty()) {
                val nameExpr =
                    if (ov.labelFields.size == 1) Expression.get(ov.labelFields[0])
                    else Expression.coalesce(*ov.labelFields.map { Expression.get(it) }.toTypedArray())
                val label = SymbolLayer(labelId, srcId).withProperties(
                    PropertyFactory.textField(nameExpr),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor(ov.color.toArgb()),
                    PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                    PropertyFactory.textHaloWidth(1.4f),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textMaxWidth(7f),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textOptional(true),
                )
                label.setMinZoom(12f)
                style.addLayer(label)
            }
        } else if (want && hasSrc) {
            (style.getSourceAs<GeoJsonSource>(srcId))?.setGeoJson(geo)
        } else if (!want && hasSrc) {
            style.getLayer(labelId)?.let { style.removeLayer(it) }
            style.getLayer(fillId)?.let { style.removeLayer(it) }
            style.getLayer(lineId)?.let { style.removeLayer(it) }
            style.removeSource(srcId)
        }
    }
}

/** Highlight the whole tapped unit (all features sharing its group value, or the single parcel
 *  by OBJECTID). A translucent cyan FILL, not an outline — a big unit is hundreds of parcels, so
 *  outlining each drew a mess of interior lines; a fill lights up the whole unit as one clean
 *  shape over any base map. Removes any prior highlight first. */
private fun applyHighlight(style: Style, highlight: Highlight?) {
    listOf("hl-line", "hl-glow", "hl-casing", "hl-fill").forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    if (highlight == null) return
    val srcId = "ov-${highlight.overlay.id}"
    if (style.getSource(srcId) == null) return
    // OBJECTID is a number in the data — compare as a number; group fields are strings.
    val filter = if (highlight.field == "OBJECTID") {
        Expression.eq(
            Expression.toNumber(Expression.get(highlight.field)),
            Expression.literal(highlight.value.toDoubleOrNull() ?: -1.0),
        )
    } else {
        Expression.eq(Expression.get(highlight.field), Expression.literal(highlight.value))
    }
    val fill = FillLayer("hl-fill", srcId).withProperties(
        PropertyFactory.fillColor(android.graphics.Color.argb(255, 0, 224, 255)), // cyan selection
        PropertyFactory.fillOpacity(0.35f),
    ).withFilter(filter)
    style.addLayer(fill)
}

/** Drop (or move, or clear) the "you tapped here" marker — a bright amber dot with a white
 *  ring and a soft halo, distinct from the blue GPS dot. Null coords remove it. */
private fun applySpotPin(style: Style, lat: Double?, lon: Double?) {
    listOf("pin-core", "pin-halo").forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    style.getSource("spot-pin")?.let { style.removeSource(it) }
    if (lat == null || lon == null) return
    val geo = """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}"""
    style.addSource(GeoJsonSource("spot-pin", geo))
    val halo = CircleLayer("pin-halo", "spot-pin").withProperties(
        PropertyFactory.circleRadius(12f),
        PropertyFactory.circleColor(android.graphics.Color.argb(64, 222, 133, 33)), // Amber wash
    )
    val core = CircleLayer("pin-core", "spot-pin").withProperties(
        PropertyFactory.circleRadius(7f),
        PropertyFactory.circleColor(0xFFDE8521.toInt()), // brand Amber
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        PropertyFactory.circleStrokeWidth(2.5f),
    )
    style.addLayer(halo)
    style.addLayer(core)
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

/** Plain-language version of the conservation interest (CONS1_TYPE). "Fee" is jargon for
 *  full ownership; "easement" means someone holds development rights over land they don't own. */
private fun humanInterest(v: String?): String? {
    val s = v?.trim()?.lowercase() ?: return null
    return when {
        s.isBlank() || s == "none" -> null
        s.contains("fee") -> "Owned outright (fee)"
        s.contains("easement") -> "Conservation easement"
        s.contains("lease") -> "Leased"
        s.contains("deed") -> "Deed restriction"
        else -> titleCase(v)
    }
}

/** A best-effort, honestly-labeled read of hunting access from the state's own conserved-lands
 *  record. Maine's own guidance is "look it up per area," so this NEVER asserts — it summarizes
 *  the record and the sheet always shows a "confirm" note + a link. Signals used: the public
 *  access note (which sometimes literally says "no hunting"), the listed purposes (which
 *  sometimes literally list "hunting"), owner type, and designation. */
private fun huntingStatus(f: org.maplibre.geojson.Feature): HuntingStatus {
    // A hand-verified override wins over the state record (this is how Knight's Pond, tagged a
    // "Municipal Park" the state can't classify, correctly reads as open — with its source).
    HuntingOverrides.forProject(f.getStringProperty("PROJECT"))?.let { o ->
        val cite = "${o.note} (Verified ${o.verified} — ${o.source}.)"
        return if (o.open) HuntingStatus("Open to hunting (verified)", HuntTone.GOOD, cite)
        else HuntingStatus("No hunting (verified)", HuntTone.NO, cite)
    }
    val access = f.str("PUB_ACCESS")?.lowercase().orEmpty()
    val desig = f.str("DESIGNATION")?.lowercase().orEmpty()
    val ownerName = f.str("HOLD1_NAME")?.lowercase().orEmpty()
    val purpose = ((f.str("PURPOSE1") ?: "") + " " + (f.str("PURPOSE2") ?: "")).lowercase()
    // Reliable authority flags — who owns/manages it is the trustworthy signal for hunting.
    val isIfw = f.str("IFW_ID") != null || ownerName.contains("inland fisheries") || ownerName.contains("mdifw")
    val isBplReserved = ownerName.contains("bureau of parks") && (desig.contains("public land") || desig.contains("reserved"))
    val isNationalForest = ownerName.contains("forest service") || desig.contains("national forest")
    val huntingListed = purpose.contains("hunting")
    val noHunt = access.contains("no hunting")
    return when {
        // An explicit no-hunting note overrides everything.
        noHunt -> HuntingStatus("No hunting", HuntTone.NO,
            "The state's access note for this parcel specifically says no hunting.")
        // Authority-based, confident reads (the bulk of real public hunting ground):
        isIfw -> HuntingStatus("Open to hunting", HuntTone.GOOD,
            "Maine IF&W wildlife land, managed for hunting and open in season. Check any posted area rules.")
        isBplReserved -> HuntingStatus("Open to hunting", HuntTone.GOOD,
            "State Public Reserved Land (Bureau of Parks & Lands), open to hunting in season.")
        isNationalForest -> HuntingStatus("Open to hunting", HuntTone.GOOD,
            "National Forest land, open to hunting under state law and forest rules. Check the district map.")
        ownerName.contains("baxter") -> HuntingStatus("Mostly no hunting", HuntTone.NO,
            "Baxter State Park, most of the park is closed to hunting; only specific areas allow it. Verify the zone.")
        desig.contains("national wildlife refuge") -> HuntingStatus("Refuge, check rules", HuntTone.CAUTION,
            "National Wildlife Refuges allow hunting only in designated areas and seasons. Check the refuge's own rules.")
        listOf("state park", "municipal park", "sports field", "cemetery", "historic site", "ball field").any { desig.contains(it) } ->
            HuntingStatus("Often no hunting", HuntTone.CAUTION,
                "Parks and developed lands often don't allow hunting, though some town preserves do. Confirm this one.")
        listOf("sanctuary", "nature preserve", "research natural", "wilderness").any { desig.contains(it) } ->
            HuntingStatus("Usually closed to hunting", HuntTone.NO,
                "Sanctuaries and natural-area reserves are often closed to hunting. Verify for this specific parcel.")
        // Data-hint reads (less certain, always paired with a lookup link):
        huntingListed -> HuntingStatus("Hunting is a listed use", HuntTone.GOOD,
            "The state record lists hunting among this area's purposes. Confirm current rules with the owner.")
        access.startsWith("no public access") || access.startsWith("not allowed") || access == "private" || access.contains("permission required") ->
            HuntingStatus("Permission required", HuntTone.CAUTION,
                "Not open to the general public. Hunting only with the owner's permission.")
        access.startsWith("allowed") -> HuntingStatus("Confirm hunting here", HuntTone.CAUTION,
            "Open to general use, but hunting rules vary by parcel. Confirm with the owner before hunting.")
        access.isBlank() || access.contains("unknown") -> HuntingStatus("Rules not listed", HuntTone.UNKNOWN,
            "The state's record doesn't spell out hunting access here. Verify before you hunt.")
        else -> HuntingStatus("Confirm locally", HuntTone.UNKNOWN,
            "Hunting access isn't clear from the record. Confirm with the landowner or manager.")
    }
}

/** Pragmatic "look it up yourself" links for a parcel we can't hardcode a rules page for.
 *  Scoped to the area's real name + owner so the first result IS the managing org's own page
 *  (land trust / town), plus a Maine Trail Finder search (great for preserves' rules + maps).
 *  This is the honest path: there's no statewide hunting-access database, so we route the user
 *  straight to who actually sets the rules for that specific ground. */
private fun lookupLinks(name: String, owner: String?): List<Pair<String, String>> {
    fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    val who = owner?.let { " $it" } ?: ""
    return listOf(
        "Look up this area's rules (web)" to "https://www.google.com/search?q=${enc("$name$who hunting rules Maine")}",
        "Find it on Maine Trail Finder" to "https://www.google.com/search?q=${enc("$name Maine Trail Finder")}",
    )
}

/** Every overlay with a feature under the tap (one per layer, topmost feature), in overlay
 *  order. Empty = open ground. More than one = the tap landed on overlapping layers. */
private fun featuresAt(map: MapLibreMap, latLng: LatLng, enabled: Set<String>): List<Pair<MapOverlay, org.maplibre.geojson.Feature>> {
    val point: PointF = map.projection.toScreenLocation(latLng)
    val out = mutableListOf<Pair<MapOverlay, org.maplibre.geojson.Feature>>()
    for (ov in OVERLAYS) {
        if (ov.id !in enabled) continue
        val layerId = "ov-${ov.id}-" + if (ov.filled) "fill" else "line"
        val f = runCatching { map.queryRenderedFeatures(point, layerId) }.getOrNull().orEmpty().firstOrNull()
        if (f != null) out += ov to f
    }
    return out
}

/** Build the clean, labeled description for a tapped feature — per overlay, using the
 *  real fields, with the bulky text tucked into "read more". */
private fun describeFeature(ov: MapOverlay, f: org.maplibre.geojson.Feature, lat: Double, lon: Double): FeatureInfo = when (ov.id) {
    "public-land", "hunting-verified" -> FeatureInfo(
        overlay = ov,
        // Prefer the common project name ("Knight's Pond Preserve") over a tax-parcel number.
        title = f.name("PROJECT") ?: f.name("PARCEL_NAME") ?: f.str("HOLD1_NAME") ?: "Conserved land",
        facts = listOfNotNull(
            f.str("HOLD1_NAME")?.let { "Owner" to it },
            f.str("DESIGNATION")?.let { "Type" to it },         // what the state calls it
            f.acres()?.let { "Size" to it },
        ),
        moreFacts = listOfNotNull(
            humanInterest(f.str("CONS1_TYPE"))?.let { "Ownership" to it },
            f.str("PUB_ACCESS")?.let { "Access note" to it },
            f.str("PROJECT")?.let { "Project" to it },
            f.str("PURPOSE1")?.let { "Purpose" to it },
            f.str("GAP_STATUS")?.let { "Protection" to it },
            f.str("ACQ_YEAR")?.let { "Acquired" to it },
        ),
        description = null,
        links = lookupLinks(f.name("PROJECT") ?: f.name("PARCEL_NAME") ?: "conserved land", f.str("HOLD1_NAME")),
        lat = lat, lon = lon,
        // Only surface a hunting line when we can confidently call it open (verified / authority-
        // based) — no guessing "maybe" for parcels the state record can't classify.
        hunting = huntingStatus(f).takeIf { it.tone == HuntTone.GOOD },
    )
    "wma" -> FeatureInfo(
        overlay = ov,
        title = f.name("PROJECT") ?: f.name("PARCEL_NAME") ?: "Wildlife Management Area",
        facts = listOfNotNull(
            f.str("HOLD1_NAME")?.let { "Managed by" to it },
            f.acres()?.let { "Size" to it },
        ),
        moreFacts = listOfNotNull(
            f.str("PUB_ACCESS")?.let { "Access note" to it },
            f.str("PARCEL_NAME")?.let { "Parcel" to it },
            f.str("DESIGNATION")?.let { "Designation" to it },
            f.str("PURPOSE1")?.let { "Purpose" to it },
            f.str("ACQ_YEAR")?.let { "Acquired" to it },
        ),
        description = null,
        links = listOf("Maine WMAs — hunting & rules (IF&W)" to "https://www.maine.gov/ifw/programs-resources/wildlife-management-areas/index.html") +
            lookupLinks(f.name("PROJECT") ?: f.name("PARCEL_NAME") ?: "wildlife management area", f.str("HOLD1_NAME")),
        lat = lat, lon = lon,
        hunting = huntingStatus(f),
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
    "national-forest" -> FeatureInfo(
        overlay = ov,
        title = f.str("forestname") ?: f.str("FORESTNAME") ?: "National Forest",
        facts = listOfNotNull(
            (f.str("gis_acres") ?: f.str("GIS_ACRES"))?.toDoubleOrNull()?.let { "Size" to "%,d acres".format(it.toInt()) },
        ),
        moreFacts = emptyList(),
        description = null,
        links = listOfNotNull(ov.legalLink?.let { "White Mountain National Forest (USDA FS)" to it }),
        lat = lat, lon = lon,
        hunting = HuntingStatus("Open to hunting (check district)", HuntTone.GOOD,
            "National Forest land is open to hunting under Maine law and forest rules. Check the district map for any closed areas."),
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

/** USGS The National Map raster tiles — public domain, keyless. ArcGIS tile order is
 *  {z}/{row}/{col}, which maps to MapLibre's {z}/{y}/{x}. */
private const val USGS_TOPO =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"
/** USGS Imagery Topo — satellite with roads, place names + contours baked in (the "Hybrid" look). */
private const val USGS_IMAGERY_TOPO =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}"

/** Esri World Imagery — free, keyless high-res satellite (ArcGIS tile order {z}/{y}/{x}). */
private const val ESRI_IMAGERY =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
/** Esri World Hillshade — free, keyless grayscale terrain relief, blended under the topo for a
 *  3D shaded-relief look (the CalTopo/Gaia style hunters read the land with). */
private const val ESRI_HILLSHADE =
    "https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}"

/**
 * The style URI for a base map. The raster (USGS) styles are written to a local file so
 * the SAME uri drives both on-screen display and MapLibre's offline downloader (which
 * needs a resolvable style URL, not inline JSON). Street uses OpenFreeMap's hosted style.
 */
internal fun baseStyleUri(context: Context, base: BaseMap): String = when (base) {
    BaseMap.SHADED -> writeShadedStyle(context)
    BaseMap.HYBRID -> writeStyleFile(context, "style_hybrid.json", USGS_IMAGERY_TOPO, "USGS The National Map (imagery topo)")
    BaseMap.AERIAL -> writeStyleFile(context, "style_aerial.json", ESRI_IMAGERY, "Esri, Maxar, Earthstar Geographics")
}

private fun writeStyleFile(context: Context, name: String, tileUrl: String, attribution: String): String {
    val f = java.io.File(context.filesDir, name)
    f.writeText(rasterStyleJson(tileUrl, attribution)) // static content; cheap to rewrite
    return "file://${f.absolutePath}"
}

/** Shaded-relief topo: USGS topo with Esri hillshade blended on top at low opacity for 3D terrain. */
private fun writeShadedStyle(context: Context): String {
    val f = java.io.File(context.filesDir, "style_shaded.json")
    f.writeText(shadedTopoStyleJson())
    return "file://${f.absolutePath}"
}

private fun shadedTopoStyleJson(): String = """
{
  "version": 8,
  "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf",
  "sources": {
    "topo": { "type": "raster", "tiles": ["$USGS_TOPO"], "tileSize": 256, "maxzoom": 16, "attribution": "USGS The National Map (topo), Esri World Hillshade" },
    "hillshade": { "type": "raster", "tiles": ["$ESRI_HILLSHADE"], "tileSize": 256, "maxzoom": 16 }
  },
  "layers": [
    { "id": "topo", "type": "raster", "source": "topo" },
    { "id": "hillshade", "type": "raster", "source": "hillshade", "paint": { "raster-opacity": 0.35 } }
  ]
}
""".trimIndent()

/** A minimal MapLibre style with a single full-screen raster layer from an XYZ endpoint. */
private fun rasterStyleJson(tileUrl: String, attribution: String): String = """
{
  "version": 8,
  "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf",
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
    tint: Color = KairosColors.Pine,
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
            Icon(icon, contentDescription = desc, tint = tint)
        }
    }
}

/** Draws the two-finger measure line + yardage from state. Purely visual (no pointerInput), so
 *  it never intercepts touches — the gesture is detected on the MapView by [MeasureGesture]. */
@Composable
private fun MeasureLineOverlay(a: Offset?, b: Offset?, yards: Int?) {
    if (a == null || b == null) return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(Color(0f, 0f, 0f, 0.5f), a, b, strokeWidth = 10f, cap = StrokeCap.Round) // soft shadow
            drawLine(Color.White, a, b, strokeWidth = 4f, cap = StrokeCap.Round)
            listOf(a, b).forEach {
                drawCircle(Color.White, radius = 12f, center = it)
                drawCircle(Color(0f, 0.88f, 1f), radius = 7f, center = it) // cyan endpoints
            }
        }
        val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = KairosColors.Surface,
            shadowElevation = 3.dp,
            modifier = Modifier.offset { IntOffset((mid.x - 46.dp.toPx()).toInt(), (mid.y - 18.dp.toPx()).toInt()) },
        ) {
            Text(
                "%,d yd".format(yards ?: 0),
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = KairosColors.Text,
            )
        }
    }
}

/** Detects a two-finger HOLD on the map (as opposed to a pinch or pan) and turns it into a live
 *  distance line. Used as the MapView's OnTouchListener: returns true to consume (measuring, so
 *  the map stays put), false to let the map zoom/scroll normally. A quick pinch (fingers change
 *  separation past a tolerance before the hold delay) is handed straight to the map; a still
 *  two-finger press past the delay locks into measuring until a finger lifts. */
private class MeasureGesture {
    private var t0 = 0L
    private var span0 = 0f
    private var active = false
    private var rejected = false
    private var lastA: Offset? = null
    private var lastB: Offset? = null
    private var lastYd: Int? = null

    private fun span(ev: android.view.MotionEvent): Float {
        if (ev.pointerCount < 2) return 0f
        return kotlin.math.hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1))
    }

    fun onTouch(ev: android.view.MotionEvent, map: MapLibreMap, onMeasure: (Offset?, Offset?, Int?, Boolean) -> Unit): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 2) {
                t0 = System.currentTimeMillis(); span0 = span(ev); active = false; rejected = false
            }
            android.view.MotionEvent.ACTION_MOVE -> if (ev.pointerCount == 2 && !rejected) {
                if (!active) {
                    if (kotlin.math.abs(span(ev) - span0) > 60f) { rejected = true; return false } // a pinch → let the map zoom
                    if (System.currentTimeMillis() - t0 < 160L) return false                        // still deciding (a held press moves nothing)
                    active = true
                }
                val a = Offset(ev.getX(0), ev.getY(0)); val b = Offset(ev.getX(1), ev.getY(1))
                val la = map.projection.fromScreenLocation(PointF(a.x, a.y))
                val lb = map.projection.fromScreenLocation(PointF(b.x, b.y))
                lastA = a; lastB = b; lastYd = haversineYards(la, lb)
                onMeasure(a, b, lastYd, false)
                return true // consume so the map holds still while measuring
            }
            android.view.MotionEvent.ACTION_POINTER_UP,
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                val wasActive = active
                active = false; rejected = false
                if (wasActive) { onMeasure(lastA, lastB, lastYd, true); return true } // keep the line, let it linger
            }
        }
        return active
    }
}

/** Great-circle distance between two map points, in yards. */
private fun haversineYards(a: LatLng, b: LatLng): Int {
    val r = 6371000.0 // earth radius, meters
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val la1 = Math.toRadians(a.latitude); val la2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
    val meters = 2 * r * asin(sqrt(h))
    return (meters * 1.0936133).roundToInt()
}

@Composable
private fun LayerSheet(
    base: BaseMap,
    enabled: Set<String>,
    onPickBase: (BaseMap) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Map layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = KairosColors.Dim)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("BASE MAP", style = MaterialTheme.typography.labelSmall, color = KairosColors.Faint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BaseMap.entries.forEach { b ->
                    BaseTile(base = b, active = b == base, onClick = { onPickBase(b) })
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

@Composable
private fun FeatureSheetContent(
    info: FeatureInfo,
    forecast: SpotForecast?,
    choices: List<SpotChoice>,
    activeIndex: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember(info.title, info.lat, info.lon) { mutableStateOf(false) }
    val isLand = info.overlay?.id == "public-land" || info.overlay?.id == "wma"
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
    ) {
        // Overlap chooser: when the tap hit several layers, list them by color so you can pick
        // which one to view ("orange = archery, green = public land").
        if (choices.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEachIndexed { i, c ->
                    val ovc = c.info.overlay?.color ?: KairosColors.Dim
                    val active = i == activeIndex
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) ovc.copy(alpha = 0.16f) else KairosColors.Bg,
                        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) ovc else KairosColors.Line),
                        onClick = { onPick(i) },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(ovc, RoundedCornerShape(999.dp)))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                c.info.overlay?.label ?: "Spot",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) ovc else KairosColors.Dim,
                            )
                        }
                    }
                }
            }
        }

        // Header: the property NAME big and first (so it stands out even in the collapsed peek),
        // with the layer kind as a small kicker underneath, and a close (X).
        Row(verticalAlignment = Alignment.Top) {
            Text(
                info.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KairosColors.Text,
                lineHeight = 30.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = KairosColors.Faint,
                modifier = Modifier.size(22.dp).clickableNoRipple(onClose),
            )
        }
        info.overlay?.let {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(it.color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(6.dp))
                Text(
                    it.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = KairosColors.Dim,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // The honest hunting read for land parcels, right up top.
        info.hunting?.let { h ->
            Spacer(Modifier.height(10.dp))
            HuntChip(h)
            Spacer(Modifier.height(6.dp))
            Text(h.note, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim, lineHeight = 17.sp)
            if (isLand) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recreational target shooting is generally not allowed on conserved land, only lawful hunting in season.",
                    style = MaterialTheme.typography.labelSmall,
                    color = KairosColors.Faint,
                    lineHeight = 15.sp,
                )
            }
        }

        if (info.facts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            info.facts.forEach { (k, v) -> FactRow(k, v) }
        }

        // The engine forecast + today's tactic for this exact spot.
        Spacer(Modifier.height(12.dp))
        ForecastBlock(forecast)

        // More info: the detail fields, the long legal text, and the reference links.
        val hasMore = info.moreFacts.isNotEmpty() || info.description != null || info.links.isNotEmpty()
        if (hasMore) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "Less info" else "More info")
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
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

/** The derived hunting-access chip: a tinted pill so the read is scannable at a glance. */
@Composable
private fun HuntChip(h: HuntingStatus) {
    val color = when (h.tone) {
        HuntTone.GOOD -> KairosColors.Good
        HuntTone.CAUTION -> KairosColors.Fair
        HuntTone.NO -> KairosColors.Error
        HuntTone.UNKNOWN -> KairosColors.Dim
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.14f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(999.dp)))
            Spacer(Modifier.width(8.dp))
            Text(h.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KairosColors.Faint, modifier = Modifier.width(88.dp))
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

/** OnX-style base-map picker tile: a thumbnail "taste" of the style with its label under,
 *  the selected one ringed in the brand green with a bold label. */
@Composable
private fun BaseTile(base: BaseMap, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active) KairosColors.Pine else KairosColors.Line,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Image(
                painter = painterResource(base.thumb),
                contentDescription = base.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            base.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) KairosColors.Text else KairosColors.Dim,
        )
    }
}
