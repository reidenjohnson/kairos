package com.kairos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.data.HrrrApi
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.WindAxis
import com.kairos.data.WindBudget
import com.kairos.data.WindFieldStore
import com.kairos.data.WindGrid
import com.kairos.data.WindNodeCache
import com.kairos.data.WindNodes
import com.kairos.data.WindPack
import com.kairos.data.WindSampler
import com.kairos.data.WindSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import kotlin.math.roundToInt

/**
 * Wind — a Windy-style map for checking the wind at a spot, past and future. Animated particles
 * drift downwind over the map (HRRR, 3 km, refined by WindNinja terrain packs where you have one);
 * the bottom slider scrubs 7 days back to 48 h ahead; tap anywhere for that point's wind chart.
 *
 * Direction convention: the UI always says where wind comes FROM (meteorological); particles and
 * arrows move the way the air goes (downwind). See [com.kairos.data.WindMath].
 */

/** What the tap sheet knows about the pinned point. */
internal sealed interface SpotWind {
    data object Loading : SpotWind
    data object Failed : SpotWind
    /** [live] false = the fetch failed and this is the nearest cached grid node instead. */
    data class Ready(val series: WindSeries, val live: Boolean) : SpotWind
}

private object WindPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("wind_prefs", Context.MODE_PRIVATE)

    // Satellite by default: the particles read best over imagery, and it's the hybrid style.
    fun loadBase(c: Context): BaseMap =
        runCatching { BaseMap.valueOf(prefs(c).getString("base", null) ?: "") }.getOrDefault(BaseMap.AERIAL)

    fun saveBase(c: Context, b: BaseMap) {
        prefs(c).edit().putString("base", b.name).apply()
    }
}

@Composable
fun WindScreen() {
    val context = LocalContext.current
    remember { MapLibre.getInstance(context) } // must init before any MapView; safe to repeat
    val scope = rememberCoroutineScope()

    var base by remember { mutableStateOf(WindPrefs.loadBase(context)) }
    LaunchedEffect(base) { WindPrefs.saveBase(context, base) }
    var showBases by remember { mutableStateOf(false) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Bumped every time the camera settles: drives the (debounced) field fetch + particle reseed.
    var idleKey by remember { mutableIntStateOf(0) }

    // The slider's hours (index NOW_INDEX = this hour). Rolls forward on the hour while open.
    var axisStart by remember { mutableLongStateOf(WindAxis.startSec()) }
    var hour by remember { mutableFloatStateOf(WindAxis.NOW_INDEX.toFloat()) }

    val store = remember { WindFieldStore(context.applicationContext) }
    var grid by remember { mutableStateOf<WindGrid?>(null) }
    var packs by remember { mutableStateOf<List<WindPack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(WindFieldStore.Status(offline = false, refused = false)) }
    val sampler = remember(grid, packs) { WindSampler(grid, packs) }

    var pin by remember { mutableStateOf<LatLng?>(null) }
    var spot by remember { mutableStateOf<SpotWind?>(null) }

    // New hour while the screen is open: shift the axis so Now stays now. The picked moment stays
    // picked (or stays on Now if that's where it was); the grid rebuilds on the new axis.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_600_000L - System.currentTimeMillis() % 3_600_000L + 1_000L)
            val next = WindAxis.startSec()
            val shift = ((next - axisStart) / 3600L).toInt()
            if (shift <= 0) continue
            val atNow = Math.round(hour) == WindAxis.NOW_INDEX
            axisStart = next
            hour = if (atNow) WindAxis.NOW_INDEX.toFloat() else (hour - shift).coerceAtLeast(0f)
            idleKey++
        }
    }

    // Terrain packs + cache housekeeping, once per visit.
    LaunchedEffect(Unit) {
        packs = withContext(Dispatchers.IO) {
            WindNodeCache.prune(context)
            WindPack.loadAll(File(context.filesDir, WindPack.DIR).apply { mkdirs() })
        }
    }

    Box(Modifier.fillMaxSize()) {
        WindMapView(
            onMapReady = { m ->
                map = m
                m.uiSettings.isTiltGesturesEnabled = false // the particle projection assumes a flat map
                m.uiSettings.isCompassEnabled = false      // our own reset-north button instead
                m.setMinZoomPreference(4.0)                // HRRR covers the lower 48; no whole-globe views
                // Keep MapLibre's logo + (i) clear of the bottom panels.
                m.uiSettings.logoGravity = Gravity.TOP or Gravity.START
                m.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
                val dp = context.resources.displayMetrics.density
                m.uiSettings.setLogoMargins((12 * dp).toInt(), (10 * dp).toInt(), 0, 0)
                // The (i) sits just right of the logo (~85 dp wide), not on top of it.
                m.uiSettings.setAttributionMargins((104 * dp).toInt(), (10 * dp).toInt(), 0, 0)
                m.addOnCameraIdleListener { idleKey++ }
                m.addOnMapClickListener { latLng ->
                    pin = latLng
                    // The spot card covers the lower half: slide a low tap up so the pin stays in view.
                    val h = context.resources.displayMetrics.heightPixels.toFloat()
                    val y = m.projection.toScreenLocation(latLng).y
                    if (y > h * 0.4f) {
                        val c = m.projection.toScreenLocation(m.cameraPosition.target ?: latLng)
                        val shifted = m.projection.fromScreenLocation(android.graphics.PointF(c.x, c.y + (y - h * 0.28f)))
                        m.animateCamera(CameraUpdateFactory.newLatLng(shifted), 350)
                    }
                    true
                }
            },
        )

        // Base style; pack outlines + pin are re-added onto each new style below.
        LaunchedEffect(map, base) {
            val m = map ?: return@LaunchedEffect
            disableWindLocation(m) // see MapScreen: the compass dot crashes during a style swap
            // Drop the old style first: touching it while the new one loads throws (crashed on the
            // first phone test when switching Satellite -> Topo).
            style = null
            m.setStyle(Style.Builder().fromUri(baseStyleUri(context, base))) { s ->
                enableWindLocation(context, m, s)
                style = s
            }
        }

        // Start at the spot the app already knows (instant), else a GPS fix, else the default.
        LaunchedEffect(map) {
            val m = map ?: return@LaunchedEffect
            val known = com.kairos.notify.NotifyPrefsBridge.lastPlace(context)
                ?: withContext(Dispatchers.IO) { LocationProvider.current(context) }
            val target = known?.let { LatLng(it.lat, it.lon) } ?: LatLng(Location.LAT, Location.LON)
            m.cameraPosition = CameraPosition.Builder().target(target).zoom(if (known != null) 12.0 else 9.0).build()
            idleKey++
        }

        // Camera settled: show what's cached for the view right away, then fetch what's missing or
        // older than an hour (one request, <= 150 nodes). Debounced so a fling is one fetch, not ten.
        LaunchedEffect(idleKey) {
            val m = map ?: return@LaunchedEffect
            delay(400)
            val b = m.projection.visibleRegion.latLngBounds
            val padLat = (b.latitudeNorth - b.latitudeSouth) * 0.3
            val padLon = (b.longitudeEast - b.longitudeWest) * 0.3
            val plan = WindNodes.plan(
                b.latitudeSouth - padLat, b.longitudeWest - padLon,
                b.latitudeNorth + padLat, b.longitudeEast + padLon,
            )
            grid = withContext(Dispatchers.IO) {
                plan.keys().forEach { (i, j) -> store.get(WindNodes.key(i, j)) }
                store.grid(plan, axisStart)
            }
            loading = true
            val st = withContext(Dispatchers.IO) { store.ensure(plan) }
            grid = withContext(Dispatchers.IO) { store.grid(plan, axisStart) }
            status = st
            loading = false
        }

        // Thin outline of every terrain pack's box, so you can see where the wind is terrain-adjusted.
        LaunchedEffect(style, packs, base) {
            style?.takeIf { it.isFullyLoaded }?.let { applyPackOutlines(it, packs, darkGround = base.darkGround()) }
            map?.triggerRepaint()
        }
        LaunchedEffect(style, pin) {
            style?.takeIf { it.isFullyLoaded }?.let { applyWindPin(it, pin) }
            map?.triggerRepaint()
        }

        // The tapped point's own series (not interpolated from the grid). Offline -> nearest cached node.
        LaunchedEffect(pin) {
            val p = pin ?: run { spot = null; return@LaunchedEffect }
            spot = SpotWind.Loading
            spot = withContext(Dispatchers.IO) {
                val live = if (WindBudget.tryTake(context, 1)) {
                    runCatching { HrrrApi.fetch(listOf(p.latitude to p.longitude)).first() }
                        .onFailure { android.util.Log.w("KairosWind", "spot fetch failed", it) }
                        .getOrNull()
                } else null
                live?.let { SpotWind.Ready(it, live = true) }
                    ?: store.nearest(p.latitude, p.longitude)?.let { SpotWind.Ready(it, live = false) }
                    ?: SpotWind.Failed
            }
        }

        WindParticleOverlay(
            map = map,
            sampler = sampler,
            hour = hour,
            darkGround = base.darkGround(),
            settleKey = idleKey,
        )

        // Speed key for the particle colors, under the MapLibre logo.
        WindSpeedKey(base.darkGround(), Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 40.dp))

        // Status chip: offline / loading / nothing for this hour. Plain words, top center.
        val g = grid
        val hourIdx = Math.round(hour)
        val chip: Pair<String, Boolean>? = when {
            g == null || g.nodesWithData == 0 -> when {
                loading -> "Loading wind…" to true
                status.refused -> "Too many wind requests — try again in a minute" to false
                status.offline -> "Offline — no wind data saved for this area" to false
                else -> null
            }
            status.offline -> "Offline - showing data from ${dayTimeText(g.oldestFetchMillis)}" to false
            !g.hourHasData(hourIdx) && packs.none { it.covers(axisStart + hourIdx * 3600L) } ->
                "No HRRR data for this hour" to false
            loading -> "Updating wind…" to true
            else -> null
        }
        chip?.let { (text, spinning) ->
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = KairosColors.Surface,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (spinning) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = KairosColors.Pine)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.labelMedium, color = KairosColors.Dim)
                }
            }
        }

        // Map chrome, top-right: base map + reset north + my location.
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            Box {
                WindMapButton(Icons.Outlined.Layers, "Base map") { showBases = true }
                DropdownMenu(
                    expanded = showBases,
                    onDismissRequest = { showBases = false },
                    containerColor = KairosColors.Surface,
                ) {
                    BaseMap.entries.forEach { b ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    b.label,
                                    color = if (b == base) KairosColors.Pine else KairosColors.Text,
                                    fontWeight = if (b == base) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { base = b; showBases = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            WindMapButton(Icons.Outlined.Explore, "Reset map to north") {
                val m = map ?: return@WindMapButton
                val cp = m.cameraPosition
                m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(cp.target).zoom(cp.zoom).bearing(0.0).build()))
            }
            Spacer(Modifier.height(10.dp))
            WindMapButton(Icons.Outlined.MyLocation, "Center on my location") {
                val m = map ?: return@WindMapButton
                scope.launch {
                    val place = withContext(Dispatchers.IO) { LocationProvider.current(context) } ?: return@launch
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 12.5))
                }
            }
        }

        // Bottom: the tapped spot (expand in place) above the time slider.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val p = pin
            if (p != null) {
                WindSpotCard(
                    lat = p.latitude,
                    lon = p.longitude,
                    spot = spot,
                    packs = packs,
                    axisStart = axisStart,
                    hour = hour,
                    onScrub = { hour = it },
                    onClose = { pin = null },
                )
            }
            WindTimeSlider(axisStart = axisStart, hour = hour, onHour = { hour = it })
        }
    }
}

/** The particle color key: one dash per speed range. Calm (under 2 mph) has no particles, so no dash. */
@Composable
private fun WindSpeedKey(darkGround: Boolean, modifier: Modifier = Modifier) {
    val colors = if (darkGround) WIND_COLORS_ON_DARK else WIND_COLORS_ON_LIGHT
    val edges = WIND_BUCKETS_MPH.map { it.roundToInt() }
    val labels = edges.indices.map { k -> if (k < edges.size - 1) "${edges[k]}–${edges[k + 1]}" else "${edges[k]}+ mph" }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        // Dark pill over imagery so the white/yellow dashes show; light pill over paper maps.
        color = if (darkGround) androidx.compose.ui.graphics.Color(0xB3141714) else KairosColors.Surface.copy(alpha = 0.9f),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            val textColor = if (darkGround) androidx.compose.ui.graphics.Color.White else KairosColors.Dim
            labels.forEachIndexed { k, label ->
                androidx.compose.foundation.Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                    drawLine(
                        androidx.compose.ui.graphics.Color(colors[k + 1]),
                        androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                        androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    modifier = Modifier.padding(start = 3.dp, end = 7.dp),
                )
            }
        }
    }
}

/** Satellite-type bases are a dark ground for the particles; Topo/Smooth are light. */
internal fun BaseMap.darkGround(): Boolean = this == BaseMap.AERIAL || this == BaseMap.VAAERIAL

@Composable
private fun WindMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    // Same round, shadowed button as the Map screen's chrome.
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = KairosColors.Surface,
        shadowElevation = 3.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = KairosColors.Dim, modifier = Modifier.size(22.dp))
        }
    }
}

/** A plain MapLibre [MapView] hosted in Compose with its lifecycle wired up (the Map screen's
 *  version also carries the two-finger measure gesture, which Wind doesn't need). */
@Composable
private fun WindMapView(onMapReady: (MapLibreMap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readyMap = remember { arrayOfNulls<MapLibreMap>(1) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                readyMap[0] = m
                onMapReady(m)
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
            readyMap[0]?.let { disableWindLocation(it) }
            mapView.onDestroy()
        }
    }
}

/** The blue "you are here" dot (same setup as the Map screen). */
@SuppressLint("MissingPermission")
private fun enableWindLocation(context: Context, map: MapLibreMap, style: Style) {
    if (!LocationProvider.hasPermission(context)) return
    runCatching {
        val lc = map.locationComponent
        lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
        lc.renderMode = RenderMode.COMPASS
    }
}

private fun disableWindLocation(map: MapLibreMap) {
    runCatching {
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated) lc.isLocationComponentEnabled = false
    }
}

/** Dashed outline of each terrain pack's box: white over imagery, Carbon over light maps. */
private fun applyPackOutlines(style: Style, packs: List<WindPack>, darkGround: Boolean) {
    style.getLayer("windpack-line")?.let { style.removeLayer(it) }
    style.getSource("windpacks")?.let { style.removeSource(it) }
    if (packs.isEmpty()) return
    val fc = """{"type":"FeatureCollection","features":[${packs.joinToString(",") { it.outlineGeoJson() }}]}"""
    style.addSource(GeoJsonSource("windpacks", fc))
    style.addLayer(
        LineLayer("windpack-line", "windpacks").withProperties(
            PropertyFactory.lineColor(if (darkGround) android.graphics.Color.WHITE else 0xFF202321.toInt()),
            PropertyFactory.lineOpacity(0.75f),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
        ),
    )
}

/** The tapped point: the app's amber pin (white ring + soft halo), same as the Map screen's. */
private fun applyWindPin(style: Style, pin: LatLng?) {
    listOf("wind-pin-core", "wind-pin-halo").forEach { id -> style.getLayer(id)?.let { style.removeLayer(it) } }
    style.getSource("wind-pin")?.let { style.removeSource(it) }
    if (pin == null) return
    val geo = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pin.longitude},${pin.latitude}]},"properties":{}}"""
    style.addSource(GeoJsonSource("wind-pin", geo))
    style.addLayer(
        CircleLayer("wind-pin-halo", "wind-pin").withProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor(android.graphics.Color.argb(64, 222, 133, 33)), // Amber wash
        ),
    )
    style.addLayer(
        CircleLayer("wind-pin-core", "wind-pin").withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(0xFFDE8521.toInt()), // brand Amber
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
}
