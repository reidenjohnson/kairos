package com.kairos.advice

import com.kairos.engine.Conditions
import java.time.LocalDate

/**
 * "Old-timer's read" — traditional weather wisdom, surfaced as a LABELED secondary
 * voice alongside Kairos's cited model. Reiden's rule (2026-09-06): most of the old
 * guys know what they're talking about, so don't gut the folklore — but never fake it,
 * never let it drive the score, and always label what's backed by the science vs. what's
 * just tradition.
 *
 * So every saying carries a [FolkloreStanding]:
 *  - [BACKED]     — tradition that modern weather science actually supports (usually
 *                   because the old-timers were reading the barometer without a gauge).
 *                   Kairos scores the same signal; the folklore just says it plainly.
 *  - [TRADITION]  — widespread belief with weak or no evidence. Shown as color, clearly
 *                   marked unproven, and NEVER fed into the score.
 *
 * Nothing here moves the number. The moon saying is included on purpose to state, out
 * loud, the one tradition Kairos deliberately does not score.
 */
enum class FolkloreStanding { BACKED, TRADITION }

data class FolkloreSaying(
    /** The saying itself, in the old voice. */
    val saying: String,
    /** Plain read: what it's telling you to do today. */
    val read: String,
    val standing: FolkloreStanding,
    /** The honest footnote: why it holds, or that it's just tradition. */
    val note: String,
)

/**
 * Pick the single most relevant old-timer's read for today. The real weather signals
 * (an incoming front, the bluebird lull, a seasonal shift) win over the compass-based
 * sayings, because that's the honest order of what actually moves fish and game.
 * [windDirDeg] is the wind FROM direction (null when unavailable → wind sayings skip).
 */
fun pickFolklore(c: Conditions, windDirDeg: Double?, date: LocalDate, moonName: String): FolkloreSaying {
    val w = WeatherRead(c)
    val month = date.monthValue
    val eastWind = windDirDeg?.let { it in 45.0..135.0 } == true
    val westWind = windDirDeg?.let { it in 225.0..315.0 } == true
    val fullOrNew = moonName.contains("Full", true) || moonName.contains("New", true)

    return when {
        // 1) A front on the way / falling barometer — the real, reliable cue.
        w.frontIncoming || w.falling -> FolkloreSaying(
            saying = "Fish and game feed hard just before the weather breaks.",
            read = "A front's moving in. Expect a strong feed now, then a lull once it passes — be out ahead of it.",
            standing = FolkloreStanding.BACKED,
            note = "The old-timers read the dropping barometer without a gauge. It's the most reliable weather cue there is, and Kairos scores it.",
        )
        // 2) The bluebird high behind a front.
        w.bluebird -> FolkloreSaying(
            saying = "After the storm blows through, give it a day.",
            read = "High, bright and clear behind a front — the bite backs off. Fish slow and deep, hunt the thick cover.",
            standing = FolkloreStanding.BACKED,
            note = "Real: the rising high pressure and bright sun after a front pin fish down and push game into cover.",
        )
        // 3) Fall's first cold shots — a genuine seasonal feed-up.
        month in 9..11 && (w.frontIncoming || w.bigFront || c.airF < 45) -> FolkloreSaying(
            saying = "When the nights turn cold, the bass go on the feed.",
            read = "Fall's first cold snaps trigger a feed-up before winter. Cover water with faster baits.",
            standing = FolkloreStanding.BACKED,
            note = "Real: cooling water in autumn pushes fish shallow to fatten on baitfish — a well-documented seasonal pattern.",
        )
        // 4) Dog-days heat.
        month in 7..8 && (c.waterF >= 75 || c.airF >= 85) -> FolkloreSaying(
            saying = "In the dog days, fish deep and fish early.",
            read = "Midsummer heat pushes fish deep and off the midday bite. Hit first light and the drop-offs.",
            standing = FolkloreStanding.BACKED,
            note = "Real: warm surface water holds less oxygen, so fish slide to cooler depths and feed in the low light.",
        )
        // 5) The classic wind rhymes — iconic, but mostly folklore.
        eastWind -> FolkloreSaying(
            saying = "Wind from the east, fish bite the least.",
            read = "An east wind has a bad name. Don't quit — slow down, downsize, and keep at it.",
            standing = FolkloreStanding.TRADITION,
            note = "Mostly folklore. It's not the compass that matters — an east wind often rides ahead of a front, so read the barometer, not the vane.",
        )
        westWind -> FolkloreSaying(
            saying = "Wind from the west, the fish bite best.",
            read = "A west or southwest wind is the one anglers hope for. A good day to be on the water.",
            standing = FolkloreStanding.TRADITION,
            note = "Tradition, not proven. A west wind usually follows fair, settled weather — pleasant, but it's the pressure and light doing the real work.",
        )
        // 6) Slick, bright, calm — a real tough-bite tell.
        w.calm && w.clear && w.highPressure -> FolkloreSaying(
            saying = "Slick and bright, the fish sit tight.",
            read = "Flat calm and bluebird — a tough bite. Go early and late, downsize, and fish structure.",
            standing = FolkloreStanding.BACKED,
            note = "Real enough: calm, clear water lets fish see line and lures, so low light and lighter tackle earn their bites.",
        )
        // 7) The one tradition Kairos refuses to score — said out loud.
        fullOrNew -> FolkloreSaying(
            saying = "They feed by the moon.",
            read = "Solunar tables promise a peak feed on the $moonName. Fun to check — don't plan the day around it.",
            standing = FolkloreStanding.TRADITION,
            note = "This is the one Kairos deliberately does NOT score. Studies put moon-based feeding near chance for deer, and it failed to predict trout — so the moon stays near-zero here and we trust weather and light.",
        )
        // 8) Nothing pushing — steady weather.
        else -> FolkloreSaying(
            saying = "Fair and steady, fish when you're ready.",
            read = "Settled weather, no front pushing them. Lean on first and last light — that's your window.",
            standing = FolkloreStanding.BACKED,
            note = "Fair enough: with no weather trigger, the dawn and dusk light change becomes the main thing moving fish and game — which is what Kairos's timing curve tracks.",
        )
    }
}
