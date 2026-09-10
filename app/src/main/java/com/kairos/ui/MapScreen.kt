package com.kairos.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import kotlinx.coroutines.Dispatchers
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

/**
 * The interactive Maine map — an OnX-style base-map switcher (topo / satellite / street)
 * with (coming next) toggleable official state overlays: public land, expanded-archery
 * zones, WMAs, WMDs. Built on MapLibre Native (open-source, no API key). All base tiles
 * are free and keyless: USGS The National Map (public-domain topo + imagery) and
 * OpenFreeMap (street/vector). The overlays come straight from Maine's own ArcGIS GIS.
 */

/** The base maps a user can switch between, like OnX. */
internal enum class BaseMap(val label: String) {
    TOPO("Topographic"),
    SATELLITE("Satellite"),
    STREET("Street"),
}

/** Center of the state as a sensible default until we have the device's location. */
private val MAINE_CENTER = LatLng(Location.LAT, Location.LON)

@Composable
fun MapScreen() {
    val context = LocalContext.current
    // MapLibre must be initialised before any MapView is created. Safe to call repeatedly.
    remember { MapLibre.getInstance(context) }

    var base by remember { mutableStateOf(BaseMap.TOPO) }
    var showLayers by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(onMapReady = { map = it })

        // Re-apply the style whenever the base map changes (and once the map is ready).
        LaunchedEffect(map, base) {
            val m = map ?: return@LaunchedEffect
            m.setStyle(base.toStyle()) { style -> enableLocation(context, m, style) }
        }

        // Center on the device once, if we can get a fix.
        LaunchedEffect(map) {
            val m = map ?: return@LaunchedEffect
            val place = withContext(Dispatchers.IO) { LocationProvider.current(context) }
            val target = place?.let { LatLng(it.lat, it.lon) } ?: MAINE_CENTER
            m.cameraPosition = CameraPosition.Builder().target(target).zoom(if (place != null) 12.0 else 7.0).build()
        }

        // The OnX-style layer button.
        LayerButton(onClick = { showLayers = true }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        if (showLayers) {
            // Scrim behind the sheet — tap to dismiss.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickableNoRipple { showLayers = false },
            )
            LayerSheet(
                base = base,
                onPickBase = { base = it },
                onDismiss = { showLayers = false },
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
    // One MapView, created once. Adding the observer below replays the lifecycle up to
    // the current state (usually RESUMED), so the map starts rendering without extra calls.
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
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).build(),
        )
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
        lc.renderMode = RenderMode.COMPASS
    }
}

// ---- Base map style JSON -------------------------------------------------------------

/** OpenFreeMap street style (vector, keyless). */
private const val STREET_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

/** USGS The National Map raster tiles — public domain, keyless. ArcGIS tile order is
 *  {z}/{row}/{col}, which maps to MapLibre's {z}/{y}/{x}. */
private const val USGS_TOPO =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"
private const val USGS_IMAGERY =
    "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"

private fun BaseMap.toStyle(): Style.Builder = when (this) {
    BaseMap.STREET -> Style.Builder().fromUri(STREET_STYLE_URI)
    BaseMap.TOPO -> Style.Builder().fromJson(rasterStyleJson(USGS_TOPO, "USGS The National Map (topo)"))
    BaseMap.SATELLITE -> Style.Builder().fromJson(rasterStyleJson(USGS_IMAGERY, "USGS The National Map (imagery)"))
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
private fun LayerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = KairosColors.Surface,
        shadowElevation = 4.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Layers, contentDescription = "Map layers", tint = KairosColors.Pine)
        }
    }
}

@Composable
private fun LayerSheet(
    base: BaseMap,
    onPickBase: (BaseMap) -> Unit,
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
            Text(
                "Overlays — public land, expanded archery, WMAs, WMDs — are coming next.",
                style = MaterialTheme.typography.bodySmall,
                color = KairosColors.Faint,
            )
        }
    }
}

@Composable
private fun BasePill(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (active) KairosColors.SegBottom else KairosColors.Bg,
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, KairosColors.Line),
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
