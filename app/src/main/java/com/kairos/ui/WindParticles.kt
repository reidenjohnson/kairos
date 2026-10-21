package com.kairos.ui

import android.graphics.Paint
import android.graphics.PointF
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.kairos.data.WindMath
import com.kairos.data.WindSampler
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Windy-style wind particles: a few hundred short fading trails drifting downwind, colored by
 * speed. Drawn on a Compose Canvas over the MapLibre view, with every position kept in Web
 * Mercator (not screen pixels) so the trails stay glued to the ground while you pan, zoom or
 * rotate — each frame maps them to the screen through the map's current projection.
 *
 * Speed: hold ~60 fps on the S23. Per frame that's 3 projection calls (not one per point), plain
 * affine math for ~6k trail points, and ~30 batched drawLines calls (speed bucket x fade level)
 * instead of one draw per segment. Nothing allocates per frame once warm.
 */

/** Wind speed bucket edges (mph). Bucket 0 (under 2 mph) is CALM: no particles at all, so dead
 *  air reads as empty map. The rest split where a stand decision changes: light, steady, strong. */
internal val WIND_BUCKETS_MPH = floatArrayOf(2f, 5f, 10f, 15f)

/**
 * Speed colors per bucket (index 0 = calm, never drawn), picked by the GROUND under the particles:
 *  - imagery (Satellite / VA Aerial): white, yellow, orange, red. Four separate hues, not shades of
 *    one: on the phone, near-neighbor shades (pale blue ramps, then white/pale-yellow/gold) were
 *    too easy to confuse over forest.
 *  - Topo / Smooth (light paper): slate, gold, orange, red (white would vanish on paper).
 * The key on the Wind screen draws from these same arrays.
 */
internal val WIND_COLORS_ON_DARK = intArrayOf(0, 0xFFFFFFFF.toInt(), 0xFFFFE03D.toInt(), 0xFFFF8A1F.toInt(), 0xFFFF3030.toInt())
internal val WIND_COLORS_ON_LIGHT = intArrayOf(0, 0xFF55606B.toInt(), 0xFFC79A00.toInt(), 0xFFE0620B.toInt(), 0xFFC81E1E.toInt())

internal fun windBucket(mph: Float): Int {
    var b = 0
    while (b < WIND_BUCKETS_MPH.size && mph >= WIND_BUCKETS_MPH[b]) b++
    return b
}

/**
 * The map's projection for this frame as an affine map from Mercator (x east, y south, both
 * 0..1) to screen pixels. Exact while the map has no tilt — the Wind screen turns tilt off —
 * and it handles rotation for free.
 */
internal class MercatorAffine {
    var m0x = 0.0; var m0y = 0.0
    // Screen = A * (m - m0)
    var a00 = 0.0; var a01 = 0.0; var a10 = 0.0; var a11 = 0.0
    // Inverse pieces: m = m0 + d1 * (sx / w) + d2 * (sy / h)
    var d1x = 0.0; var d1y = 0.0; var d2x = 0.0; var d2y = 0.0
    var w = 1.0; var h = 1.0
    /** Pixels per Mercator unit (the world is [scale] px wide at this zoom). */
    var scale = 1.0

    private val pt = PointF()

    /** Refresh from [map] for a [w] x [h] px overlay. False if the projection isn't usable. */
    fun update(map: MapLibreMap, w: Float, h: Float): Boolean {
        if (w < 2f || h < 2f) return false
        val p = map.projection
        pt.set(0f, 0f); val c0 = p.fromScreenLocation(pt)
        pt.set(w, 0f); val c1 = p.fromScreenLocation(pt)
        pt.set(0f, h); val c2 = p.fromScreenLocation(pt)
        m0x = WindMath.mercX(c0.longitude); m0y = WindMath.mercY(c0.latitude)
        var x1 = WindMath.mercX(c1.longitude); val y1 = WindMath.mercY(c1.latitude)
        var x2 = WindMath.mercX(c2.longitude); val y2 = WindMath.mercY(c2.latitude)
        // Keep the corners on the same side of the antimeridian as corner 0.
        if (x1 - m0x > 0.5) x1 -= 1.0 else if (m0x - x1 > 0.5) x1 += 1.0
        if (x2 - m0x > 0.5) x2 -= 1.0 else if (m0x - x2 > 0.5) x2 += 1.0
        d1x = x1 - m0x; d1y = y1 - m0y; d2x = x2 - m0x; d2y = y2 - m0y
        val det = d1x * d2y - d2x * d1y
        if (abs(det) < 1e-18) return false
        this.w = w.toDouble(); this.h = h.toDouble()
        a00 = this.w * d2y / det; a01 = -this.w * d2x / det
        a10 = -this.h * d1y / det; a11 = this.h * d1x / det
        scale = sqrt(abs(a00 * a11 - a01 * a10))
        return true
    }

    fun sx(mx: Double, my: Double): Float = (a00 * (mx - m0x) + a01 * (my - m0y)).toFloat()
    fun sy(mx: Double, my: Double): Float = (a10 * (mx - m0x) + a11 * (my - m0y)).toFloat()
    fun mx(sx: Double, sy: Double): Double = m0x + d1x * (sx / w) + d2x * (sy / h)
    fun my(sx: Double, sy: Double): Double = m0y + d1y * (sx / w) + d2y * (sy / h)
}

/** The particle state + its per-frame step and draw. Owned by [WindParticleOverlay]. */
internal class WindParticles {
    private companion object {
        const val TRAIL = 12          // positions kept per particle (one per frame)
        const val LEVELS = 6          // fade steps along a trail
        const val FADE_OUT = 18       // frames a particle takes to fade before respawning
        const val SPEED_DP = 4.5f     // on-screen drift: dp per second per mph...
        const val MIN_DP = 22f        // ...plus this floor, so a 2 mph night still visibly moves
        const val MAX_DP = 160f
        const val MAX_DT = 0.05f
    }

    private var n = 0
    private var mx = DoubleArray(0)  // n * TRAIL ring of positions (Mercator)
    private var my = DoubleArray(0)
    private var head = 0             // ring slot of the newest position (shared by all)
    private var age = IntArray(0)
    private var life = IntArray(0)
    private var bucket = ByteArray(0)
    private var seededScale = 0.0
    private var px = FloatArray(TRAIL) // one particle's screen points, reused
    private var py = FloatArray(TRAIL)

    private val buckets = WIND_BUCKETS_MPH.size + 1
    private var batch = Array(buckets * LEVELS) { FloatArray(256) }
    private val count = IntArray(buckets * LEVELS)
    private val paints = Array(buckets * LEVELS) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    }
    private var paintKey = 0L
    private val rnd = java.util.Random()
    private val uv = FloatArray(2)
    private var density = 1f

    val size get() = n

    /** Size the system for the overlay (particle count follows screen area) and seed it once. */
    fun ensure(aff: MercatorAffine, density: Float) {
        this.density = density
        val areaDp = (aff.w * aff.h) / (density * density)
        // Sparse on purpose, like Windy: enough to read the flow, never a solid sheet.
        val want = (areaDp / 700.0).toInt().coerceIn(180, 600)
        if (want != n) {
            n = want
            mx = DoubleArray(n * TRAIL); my = DoubleArray(n * TRAIL)
            age = IntArray(n); life = IntArray(n); bucket = ByteArray(n)
            reseed(aff)
        }
    }

    /** Scatter every particle across the screen again (first frame, or after a big zoom). */
    fun reseed(aff: MercatorAffine) {
        for (p in 0 until n) {
            spawn(p, aff)
            age[p] = rnd.nextInt(life[p]) // stagger so they don't all respawn together
        }
        seededScale = aff.scale
    }

    /** After the camera settles: a zoom of more than ~1 level leaves particles clumped or sparse; reseed. */
    fun settle(aff: MercatorAffine) {
        if (seededScale <= 0.0) return
        val r = aff.scale / seededScale
        if (r > 1.8 || r < 0.55) reseed(aff)
    }

    private fun spawn(p: Int, aff: MercatorAffine) {
        val sx = rnd.nextDouble() * aff.w
        val sy = rnd.nextDouble() * aff.h
        val x = aff.mx(sx, sy)
        val y = aff.my(sx, sy)
        val base = p * TRAIL
        for (k in 0 until TRAIL) { mx[base + k] = x; my[base + k] = y }
        age[p] = 0
        life[p] = 70 + rnd.nextInt(150)
    }

    /** Advance every particle [dt] seconds through [sampler]'s field. */
    fun step(aff: MercatorAffine, sampler: WindSampler, dtIn: Float) {
        val dt = dtIn.coerceIn(0f, MAX_DT)
        val next = (head + 1) % TRAIL
        val perDp = density * dt / aff.scale // Mercator units per dp of drift this frame
        val margin = 40.0
        for (p in 0 until n) {
            val base = p * TRAIL
            if (age[p] >= life[p]) { spawn(p, aff); continue }
            val x = mx[base + head]
            val y = my[base + head]
            if (sampler.sample(WindMath.latOf(y), WindMath.lonOf(x), uv) < 0f) {
                // No wind data here (not loaded yet / outside HRRR): try again elsewhere.
                spawn(p, aff); age[p] = life[p] - 1
                mx[base + next] = mx[base + head]; my[base + next] = my[base + head]
                continue
            }
            val u = uv[0]
            val v = uv[1]
            val mph = hypot(u, v)
            bucket[p] = windBucket(mph).toByte()
            // Drift = floor + per-mph, along the wind's direction. Calm holds still (and isn't drawn).
            val k = if (bucket[p].toInt() == 0) 0f else (MIN_DP + SPEED_DP * mph).coerceAtMost(MAX_DP) * perDp.toFloat() / mph
            val nx = x + u * k
            val ny = y - v * k // Mercator y runs south
            mx[base + next] = nx
            my[base + next] = ny
            age[p]++
            // Off screen: start fading out now (it respawns on screen after).
            val sx = aff.sx(nx, ny); val sy = aff.sy(nx, ny)
            if ((sx < -margin || sy < -margin || sx > aff.w + margin || sy > aff.h + margin) && life[p] - age[p] > FADE_OUT) {
                life[p] = age[p] + FADE_OUT / 2
            }
        }
        head = next
    }

    /** Draw all trails with [colors] (one per speed bucket). [strokePx] = line width. */
    fun draw(canvas: android.graphics.Canvas, aff: MercatorAffine, colors: IntArray, strokePx: Float) {
        preparePaints(colors, strokePx)
        java.util.Arrays.fill(count, 0)
        for (p in 0 until n) {
            val base = p * TRAIL
            val left = life[p] - age[p]
            val fadeLevel = if (left < FADE_OUT) ((FADE_OUT - left) * LEVELS) / FADE_OUT else 0
            if (fadeLevel >= LEVELS) continue
            // Newest first, walking back around the ring.
            var k = head
            for (a in 0 until TRAIL) {
                px[a] = aff.sx(mx[base + k], my[base + k])
                py[a] = aff.sy(mx[base + k], my[base + k])
                k = if (k == 0) TRAIL - 1 else k - 1
            }
            val b = bucket[p].toInt()
            if (b == 0) continue // calm: no particle
            for (a in 0 until TRAIL - 1) {
                val level = max((a * LEVELS) / (TRAIL - 1), fadeLevel)
                if (level >= LEVELS) break
                val x0 = px[a]; val y0 = py[a]; val x1 = px[a + 1]; val y1 = py[a + 1]
                val dx = x1 - x0; val dy = y1 - y0
                val d2 = dx * dx + dy * dy
                if (d2 < 0.01f) break           // the rest of the trail hasn't moved yet (fresh spawn)
                if (d2 > 10_000f) break         // a jump (respawn / wrap) — never draw a streak across the map
                val slot = b * LEVELS + level
                var arr = batch[slot]
                val c = count[slot]
                if (c + 4 > arr.size) { arr = arr.copyOf(arr.size * 2); batch[slot] = arr }
                arr[c] = x0; arr[c + 1] = y0; arr[c + 2] = x1; arr[c + 3] = y1
                count[slot] = c + 4
            }
        }
        for (slot in batch.indices) {
            if (count[slot] > 0) canvas.drawLines(batch[slot], 0, count[slot], paints[slot])
        }
    }

    private fun preparePaints(colors: IntArray, strokePx: Float) {
        val key = colors.fold(strokePx.toBits().toLong()) { acc, c -> acc * 31 + c }
        if (key == paintKey) return
        paintKey = key
        for (b in 0 until buckets) for (l in 0 until LEVELS) {
            // Head of the trail nearly opaque, tail fading out; slightly thinner toward the tail.
            val t = l / LEVELS.toFloat()
            val alpha = ((1f - t) * 0.92f + 0.06f).coerceIn(0f, 1f)
            val c = colors[b.coerceAtMost(colors.size - 1)]
            paints[b * LEVELS + l].apply {
                color = (c and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
                strokeWidth = strokePx * (1f - 0.35f * t)
            }
        }
    }
}

/**
 * The particle layer. Steps + draws in the Canvas's draw pass, so the step and the draw use the
 * SAME projection snapshot (no shimmer while panning); a frame-clock loop just invalidates the
 * draw each vsync — no recomposition per frame.
 *
 * Reduced motion (system "Remove animations" / animator scale 0): particles build their trails
 * once and then hold still, still tracking the map as it moves.
 */
@Composable
internal fun WindParticleOverlay(
    map: MapLibreMap?,
    sampler: WindSampler?,
    hour: Float,
    darkGround: Boolean,
    /** Bumped by the screen on camera idle, so a big zoom can reseed. */
    settleKey: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val system = remember { WindParticles() }
    val aff = remember { MercatorAffine() }
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val currentSampler by rememberUpdatedState(sampler)
    val currentHour by rememberUpdatedState(hour)
    var frameNanos by remember { mutableLongStateOf(0L) }
    val clock = remember { longArrayOf(0L, 0L) } // [last frame nanos, frames stepped]

    LaunchedEffect(map) {
        if (map == null) return@LaunchedEffect
        while (true) withFrameNanos { frameNanos = it }
    }
    LaunchedEffect(settleKey) {
        if (system.size > 0) system.settle(aff)
    }

    Canvas(modifier.fillMaxSize()) {
        val now = frameNanos // read in the draw pass: each new frame redraws, nothing recomposes
        val m = map ?: return@Canvas
        val s = currentSampler ?: return@Canvas
        if (!aff.update(m, size.width, size.height)) return@Canvas
        system.ensure(aff, density)
        s.setHour(currentHour)
        val dt = if (clock[0] == 0L) 0f else (now - clock[0]) / 1e9f
        clock[0] = now
        // Reduced motion: step just long enough to draw trails, then freeze.
        if (!reducedMotion || clock[1] < 16) {
            system.step(aff, s, if (reducedMotion) 1f / 60f else dt)
            clock[1]++
        }
        drawIntoCanvas {
            system.draw(it.nativeCanvas, aff, if (darkGround) WIND_COLORS_ON_DARK else WIND_COLORS_ON_LIGHT, 2.0f * density)
        }
    }
}
