package com.kairos.engine.bucks

import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.sqrt

/** What the burned-in info strip says (SPYPOINT: "09/24/2026 17:46 65°F 18°C"). */
data class StripRead(val time: LocalDateTime?, val tempF: Int?, val hasSeconds: Boolean)

object StripParser {
    private val DATE = Regex("""(\d{1,2})\s*[/.\-]\s*(\d{1,2})\s*[/.\-]\s*(\d{4})""")
    private val DATE_SLIP = Regex("""(?<!\d)([01]\d)[/71|](\d{2})[/71|](20\d{2})(?!\d)""")
    private val TIME = Regex("""(\d{1,2})\s*[:.]\s*(\d{2})(?:\s*[:.]\s*(\d{2}))?\s*([AaPp][Mm])?""")
    // A temperature never follows a clock or date separator: "03:41 °F" (the temp's digits lost)
    // must not read as 41°F.
    private val TEMP_F = Regex("""(?<![\dA-Za-z:./])(-?\d{1,3})\s*[°ºo*]?\s*F\b""")
    private val TEMP_C = Regex("""(?<![\dA-Za-z:./])(-?\d{1,2})\s*[°ºo*]?\s*C\b""")

    /**
     * OCR on a small strip mixes up look-alike characters inside numbers (O/0, l/1, S/5, B/8...).
     * Only characters sitting between digits or date/time separators are swapped, so words stay words.
     */
    fun normalize(text: String): String {
        val map = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'l' to '1', 'I' to '1', '|' to '1', 'i' to '1',
            'S' to '5', 's' to '5', 'B' to '8', 'Z' to '2', 'z' to '2', 'g' to '9', 'q' to '9')
        val c = text.toCharArray()
        fun numeric(k: Int) = k in c.indices && (c[k].isDigit() || c[k] == '/' || c[k] == ':')
        for (k in c.indices) {
            val m = map[c[k]] ?: continue
            // "57oF": that o is a degree sign, not a zero.
            if ((c[k] == 'o' || c[k] == 'O') && k + 1 in c.indices && (c[k + 1] == 'F' || c[k + 1] == 'C')) continue
            if (numeric(k - 1) || numeric(k + 1)) c[k] = m
        }
        return String(c)
    }

    /**
     * Reads date, time and °F from OCR text. Strict: the date must be a real MM/DD/YYYY and the
     * time a real clock time, or that part comes back null and the photo goes to "check time".
     * °F is cross-checked against the strip's °C; when they disagree (an OCR slip like "G0F" for
     * "60°F") or °F is missing, the °C reading converted to °F is used.
     */
    fun parse(text: String): StripRead {
        // The camera's name/model ("SPYPOINT FLEX G-36C") follows the readings; its "36C" isn't a temperature.
        val noModel = Regex("""(?i)\bSP[A-Z]{3,9}\b|\bFLEX\s*\S*|\bG-?\d{1,2}\w*""").replace(text.replace('\n', ' '), " ")
        val flat = normalize(noModel)
        // A slash misread as 7 or 1 ("10704/2026" for 10/04/2026): strict MM?DD?YYYY fallback.
        val d = DATE.find(flat) ?: DATE_SLIP.find(flat)
        var time: LocalDateTime? = null
        var secs = false
        if (d != null) {
            val after = flat.substring(d.range.last + 1)
            // OCR sometimes returns the strip's pieces out of order: fall back to a time anywhere
            // outside the date.
            val t = TIME.find(after) ?: TIME.find(flat.removeRange(d.range))
            if (t != null) {
                var h = t.groupValues[1].toInt()
                val m = t.groupValues[2].toInt()
                val s = t.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                val ampm = t.groupValues[4].uppercase()
                if (ampm == "PM" && h < 12) h += 12
                if (ampm == "AM" && h == 12) h = 0
                time = runCatching {
                    LocalDateTime.of(d.groupValues[3].toInt(), d.groupValues[1].toInt(), d.groupValues[2].toInt(), h, m, s ?: 0)
                }.getOrNull()
                secs = s != null
            }
        }
        // Temperatures are read with the date and the time blanked out, so their digits can't be taken for one.
        var rest = flat
        if (d != null) rest = rest.replaceRange(d.range, " ".repeat(d.range.last - d.range.first + 1))
        TIME.findAll(rest).firstOrNull { it.groupValues[2].length == 2 && (it.value.contains(':') || it.value.contains('.')) }?.let { t ->
            rest = rest.replaceRange(t.range, " ".repeat(t.range.last - t.range.first + 1))
        }
        val tail = if (d != null) rest.substring(d.range.first) else rest
        val f = TEMP_F.findAll(tail).mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it in -40..120 }
        val cAsF = TEMP_C.findAll(tail).mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it in -40..50 }
            ?.let { Math.round(it * 9 / 5.0 + 32).toInt() }
        val temp = when {
            // Disagree = one of them lost a digit, which always makes it smaller ("17" read as "7",
            // "71" as "1" on his strips): take the higher.
            f != null && cAsF != null -> if (kotlin.math.abs(f - cAsF) <= 2) f else maxOf(f, cAsF)
            else -> f ?: cAsF
        }
        return StripRead(time, temp, secs)
    }
}

/** The strip as several OCR passes agree on it. [agree] = passes that gave this exact time. */
data class StripConsensus(val read: StripRead, val agree: Int, val reads: Int, val alternates: List<LocalDateTime>)

/**
 * One OCR pass can slip a digit (3 read as 8, a lost "1"), so the strip is read several ways
 * (different crops, high contrast) and the passes vote: the time most passes agree on wins, and
 * the temperature most of those passes read wins. Ties go to the earlier pass (the cleaner crop).
 * The losing times are kept so a time that fails the light check can fall back to one of them.
 */
object StripVote {
    fun pick(reads: List<StripRead>): StripConsensus? {
        val timed = reads.filter { it.time != null }
        if (timed.isEmpty()) return null
        val byTime = timed.groupBy { it.time!! }
        val best = byTime.entries.maxWith(compareBy<Map.Entry<LocalDateTime, List<StripRead>>> { it.value.size }.thenBy { e -> -timed.indexOfFirst { it.time == e.key } })
        val temps = best.value.mapNotNull { it.tempF }.ifEmpty { reads.mapNotNull { it.tempF } }
        val temp = temps.groupBy { it }.maxByOrNull { it.value.size }?.key
        val alts = byTime.entries.filter { it.key != best.key }.sortedByDescending { it.value.size }.map { it.key }
        return StripConsensus(StripRead(best.key, temp, best.value.any { it.hasSeconds }), best.value.size, reads.size, alts)
    }

    /** Stop reading more passes once two agree on time and temperature. */
    fun settled(reads: List<StripRead>): Boolean {
        val c = pick(reads) ?: return false
        return c.agree >= 2 && reads.count { it.time == c.read.time && it.tempF == c.read.tempF } >= 2
    }
}

/**
 * Does a capture time fit what the photo itself shows? An IR (no color) shot means the camera
 * thought it was dark; a color shot means daylight. Twilight is allowed either way (an hour each
 * side of sunrise and sunset, which covers every IR switch time measured on his cameras so far).
 * A clear miss means the clock or the read is wrong (AM/PM, a slipped digit).
 */
object LightCheck {
    fun fits(night: Boolean, t: Long, lat: Double, lon: Double, zone: java.time.ZoneId): Boolean {
        val day = Sun.day(java.time.Instant.ofEpochSecond(t).atZone(zone).toLocalDate(), lat, lon)
        val sr = day.sunrise ?: return true
        val ss = day.sunset ?: return true
        val hour = 3600L
        val brightDay = t > sr + hour && t < ss - hour
        val deepNight = t < sr - hour || t > ss + hour
        return if (night) !brightDay else !deepNight
    }
}

/** Image fingerprints, computed from small grayscale grids (0..255) so they're testable off-device. */
object PhotoMath {
    /** 64-bit difference hash from a 9x8 gray grid (row-major). Near-identical images = small Hamming distance. */
    fun dHash(gray9x8: IntArray): Long {
        var h = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) h = h or (1L shl bit)
            bit++
        }
        return h
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * A camera's view as an edge map: gradient magnitude of a w x h gray grid. Edges (trunks,
     * horizon, fence lines) hold between day and IR night shots far better than raw brightness.
     */
    fun edges(gray: IntArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val gx = gray[y * w + minOf(x + 1, w - 1)] - gray[y * w + maxOf(x - 1, 0)]
            val gy = gray[minOf(y + 1, h - 1) * w + x] - gray[maxOf(y - 1, 0) * w + x]
            out[y * w + x] = minOf(255, (abs(gx) + abs(gy))).toByte()
        }
        return out
    }

    const val GRID_W = 96
    const val GRID_H = 54

    /** The frame itself, for duplicate checks: 96x54 gray as z-scores x40 (signed bytes). */
    fun pixelGrid(gray: IntArray): ByteArray {
        val n = gray.size
        val m = gray.average()
        val sd = sqrt(gray.sumOf { (it - m) * (it - m) } / n) + 1e-6
        return ByteArray(n) { ((gray[it] - m) / sd * 40).toInt().coerceIn(-127, 127).toByte() }
    }

    /**
     * Same frame? Measured on his phone 2026-10-07: an original and its app save (8064 vs 720 px)
     * differ in 0.00% of grid cells; a frame 1 s later, or the same minute's other burst shot,
     * in 0.12-0.71%; two saves of different frames in the same minute 0.25%+. Same = under 0.05%
     * of cells off by more than half a standard deviation, and the 99th-percentile gap small.
     */
    fun sameFrame(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size || a.isEmpty()) return false
        val d = IntArray(a.size) { kotlin.math.abs(a[it] - b[it]) }
        val off = d.count { it > 20 }.toDouble() / d.size
        val p99 = d.sorted()[(d.size * 0.99).toInt().coerceAtMost(d.size - 1)]
        return off < 0.0005 && p99 <= 8
    }

    /** Fraction of grid cells that differ (for picking the closest candidate). */
    fun frameDiff(a: ByteArray, b: ByteArray): Double {
        if (a.size != b.size || a.isEmpty()) return 1.0
        return (a.indices).count { kotlin.math.abs(a[it] - b[it]) > 20 }.toDouble() / a.size
    }

    const val SCENE_W = 64
    const val SCENE_H = 36

    /**
     * A camera's view as a local-contrast-normalized 64x36 gray map (each pixel minus its 5x5 mean,
     * over its 5x5 spread), stored as signed bytes. Picked on his real photos 2026-10-07 (3 cameras,
     * 32 photos, day + IR night): different views never scored above 0.12, and every photo's best
     * match was its own camera (32/32). The older 32x18 edge map scored a same-view day/night pair
     * at -0.18 and missed one.
     */
    fun sceneSignature(gray: IntArray, w: Int = SCENE_W, h: Int = SCENE_H): ByteArray {
        val r = 2
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0; var ss = 0.0; var n = 0
            for (dy in -r..r) for (dx in -r..r) {
                val v = gray[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)].toDouble()
                s += v; ss += v * v; n++
            }
            val m = s / n
            val sd = sqrt(maxOf(ss / n - m * m, 0.001) + 25.0)
            out[y * w + x] = ((gray[y * w + x] - m) / sd * 40).toInt().coerceIn(-127, 127).toByte()
        }
        return out
    }

    /** Normalized correlation of two scene signatures (signed), -1..1 (1 = same view). */
    fun sceneSimilarity(a: ByteArray, b: ByteArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        val n = a.size
        var ma = 0.0; var mb = 0.0
        for (i in 0 until n) { ma += a[i]; mb += b[i] }
        ma /= n; mb /= n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until n) {
            val x = a[i] - ma; val y = b[i] - mb
            sab += x * y; saa += x * x; sbb += y * y
        }
        return if (saa == 0.0 || sbb == 0.0) 0.0 else sab / sqrt(saa * sbb)
    }

    /** Normalized correlation of two edge maps, -1..1 (1 = same view). */
    fun similarity(a: ByteArray, b: ByteArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        val n = a.size
        var ma = 0.0; var mb = 0.0
        for (i in 0 until n) { ma += (a[i].toInt() and 0xFF); mb += (b[i].toInt() and 0xFF) }
        ma /= n; mb /= n
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until n) {
            val x = (a[i].toInt() and 0xFF) - ma
            val y = (b[i].toInt() and 0xFF) - mb
            sab += x * y; saa += x * x; sbb += y * y
        }
        return if (saa == 0.0 || sbb == 0.0) 0.0 else sab / sqrt(saa * sbb)
    }
}

/**
 * A strip date that looks misread. [options] = the dates one digit away whose model temperature
 * matches the strip, best first (a burst twin's date first when there is one); [modelAt] = NOAA's
 * temperature at the read date and at each option, for showing him why.
 */
data class DateFix(val read: LocalDateTime, val options: List<LocalDateTime>, val stripF: Int, val modelAt: Map<LocalDateTime, Double>, val twin: LocalDateTime?)

/**
 * Catches a misread DATE on the strip (the time and temperature read right on all 56 of his photos
 * checked 2026-10-13; the two misses were date digits: 10/07 read as 10/01, 10/11 as 10/1). The
 * strip prints the camera's own temperature; NOAA HRRR has the temperature at the camera for every
 * hour. On his 56 correct photos the two differ by 1.9°F (median), 6.1°F at worst; the misread
 * date was 13.7°F off. So a read date more than [OFF_F] off is flagged, with the dates one digit
 * away (a digit swapped, or one added to a one-digit day/month) that are within [ON_F] as options.
 * Temperature alone can match more than one night (10/04 and 10/07 were both ~44°F), so a burst
 * twin (another photo at the same camera with the same clock time and temperature) whose date is
 * one of the options goes first. Never applied without him picking.
 */
object StripDateCheck {
    const val OFF_F = 8.0
    const val ON_F = 4.0

    fun check(read: LocalDateTime, stripF: Int, zone: java.time.ZoneId, modelF: (Long) -> Double?, notAfter: LocalDateTime?, twins: List<LocalDateTime> = emptyList()): DateFix? {
        fun at(t: LocalDateTime) = modelF(t.atZone(zone).toEpochSecond())
        val m0 = at(read) ?: return null
        if (Math.abs(stripF - m0) <= OFF_F) return null
        val limit = notAfter ?: LocalDateTime.now(zone)
        val near = variants(read).filter { !it.isAfter(limit) }.mapNotNull { v -> at(v)?.let { v to it } }
            .filter { Math.abs(stripF - it.second) <= ON_F }
        if (near.isEmpty()) return null
        val twin = twins.firstOrNull { tw -> near.any { it.first == tw } }
        val sorted = near.sortedWith(compareBy<Pair<LocalDateTime, Double>> { if (it.first == twin) 0 else 1 }.thenBy { Math.abs(stripF - it.second) })
        return DateFix(read, sorted.map { it.first }, stripF, (near + (read to m0)).toMap(), twin)
    }
    /** Same time, month or day one digit away: each digit replaced, or a digit added to a one-digit field. */
    fun variants(t: LocalDateTime): List<LocalDateTime> {
        val out = LinkedHashSet<LocalDateTime>()
        fun field(v: Int): Set<Int> {
            val s = v.toString().padStart(2, '0')
            val r = HashSet<Int>()
            for (k in s.indices) for (dgt in '0'..'9') r += (s.substring(0, k) + dgt + s.substring(k + 1)).toInt()
            if (v < 10) for (dgt in 0..9) { r += v * 10 + dgt; r += dgt * 10 + v }
            return r - v
        }
        for (d in field(t.dayOfMonth)) runCatching { out += t.withDayOfMonth(d) }
        for (m in field(t.monthValue)) runCatching { out += t.withMonth(m) }
        return out.toList()
    }
}
