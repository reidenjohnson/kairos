package com.kairos.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Maine license & permit-lottery deadlines — forward-looking so the app can remind
 * you before an application window closes (see HANDOFF Stride 4).
 *
 * Every date comes from an official Maine IF&W (maine.gov) source, cited per
 * deadline via [LicenseDeadline.sourceLabel]/[sourceUrl]. The lotteries recur every
 * year, but the EXACT dates are set annually by IF&W and shift year to year, so we
 * store the last officially-posted cycle's dates ([cycleYear]) and, once they pass,
 * say the next cycle is not yet posted rather than guessing it. [typicalWindow] is
 * an approximate historical pattern, clearly labeled as guidance, never an official
 * date. Do NOT edit a date without updating its source.
 */

enum class DeadlineKind { APPLICATION_OPEN, APPLICATION_DEADLINE, DRAWING, PAYMENT_DEADLINE }

/** One dated step in a permit process. */
data class DeadlineEvent(
    val label: String,
    val date: LocalDate,
    val kind: DeadlineKind,
    val note: String = "",
)

/** A license or permit-lottery process with its official citation. */
data class LicenseDeadline(
    val name: String,
    val side: Side,
    /** One-line plain-English description of what this is. */
    val summary: String,
    /** The dated steps, in chronological order. */
    val events: List<DeadlineEvent>,
    /** The license year the posted [events] belong to. */
    val cycleYear: Int,
    /** Approximate recurring timing, labeled as guidance (dates shift each year). */
    val typicalWindow: String,
    val sourceLabel: String,
    val sourceUrl: String,
)

enum class DeadlinePhase {
    /** The application window is open now — apply before the deadline. */
    OPEN,
    /** The application window opens later this cycle. */
    UPCOMING,
    /** The posted cycle's deadline has passed; next cycle not yet posted. */
    PASSED,
}

/** Where [today] falls in a deadline's application window. */
data class DeadlineStatus(
    val phase: DeadlinePhase,
    val applicationOpen: DeadlineEvent?,
    val applicationDeadline: DeadlineEvent?,
    /** Days until the deadline, when [phase] is OPEN. */
    val daysUntilDeadline: Int? = null,
    /** Days until applications open, when [phase] is UPCOMING. */
    val daysUntilOpen: Int? = null,
) {
    /** A short plain-English line for the UI. */
    fun headline(cycleYear: Int, typicalWindow: String): String = when (phase) {
        DeadlinePhase.OPEN -> {
            val d = daysUntilDeadline!!
            val by = applicationDeadline!!.date
            val whenWord = when {
                d == 0 -> "today"
                d == 1 -> "tomorrow"
                else -> "in $d days"
            }
            "Apply by ${monthDayYear(by)} — $whenWord"
        }
        DeadlinePhase.UPCOMING -> {
            val d = daysUntilOpen!!
            val whenWord = if (d == 0) "today" else if (d == 1) "tomorrow" else "in $d days"
            "Applications open $whenWord (${monthDay(applicationOpen!!.date)})"
        }
        DeadlinePhase.PASSED ->
            "$cycleYear window closed — ${cycleYear + 1} dates not yet posted ($typicalWindow)"
    }
}

/** Compute [today]'s status against a deadline's application window. */
fun deadlineStatus(d: LicenseDeadline, today: LocalDate): DeadlineStatus {
    val open = d.events.firstOrNull { it.kind == DeadlineKind.APPLICATION_OPEN }
    val deadline = d.events.firstOrNull { it.kind == DeadlineKind.APPLICATION_DEADLINE }
    if (deadline == null) return DeadlineStatus(DeadlinePhase.PASSED, open, deadline)

    return when {
        // Before applications open: upcoming.
        open != null && today.isBefore(open.date) -> DeadlineStatus(
            phase = DeadlinePhase.UPCOMING,
            applicationOpen = open,
            applicationDeadline = deadline,
            daysUntilOpen = ChronoUnit.DAYS.between(today, open.date).toInt(),
        )
        // Within the window (on or before the deadline): open to apply.
        !today.isAfter(deadline.date) -> DeadlineStatus(
            phase = DeadlinePhase.OPEN,
            applicationOpen = open,
            applicationDeadline = deadline,
            daysUntilDeadline = ChronoUnit.DAYS.between(today, deadline.date).toInt(),
        )
        // Past the deadline: this cycle is done.
        else -> DeadlineStatus(DeadlinePhase.PASSED, open, deadline)
    }
}

private fun monthDayYear(d: LocalDate): String = "${monthDay(d)}, ${d.year}"

private fun monthDay(d: LocalDate): String {
    val m = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )[d.monthValue - 1]
    return "$m ${d.dayOfMonth}"
}

private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

private const val MOOSE_SRC = "Maine IF&W — Moose Permit Lottery"
private const val MOOSE_URL =
    "https://www.maine.gov/ifw/hunting-trapping/hunting/species/moose/moose-permit.html"
private const val DEER_SRC = "Maine IF&W — Antlerless Deer Permit Lottery"
private const val DEER_URL =
    "https://www.maine.gov/ifw/hunting-trapping/hunting/species/deer/antlerless-deer-permit.html"

/**
 * The deadline table. Dates are the 2026 lottery cycle, verified against the official
 * IF&W pages above. Both are draw-by-lottery permits you must apply for months ahead.
 */
val MAINE_DEADLINES: List<LicenseDeadline> = listOf(
    LicenseDeadline(
        name = "Moose permit lottery",
        side = Side.HUNT,
        summary = "Maine's once-a-year draw for a moose hunting permit. Free to apply; " +
            "winners buy the permit after the drawing.",
        events = listOf(
            DeadlineEvent("Applications open", d(2026, 4, 1), DeadlineKind.APPLICATION_OPEN),
            DeadlineEvent(
                "Application deadline", d(2026, 5, 18), DeadlineKind.APPLICATION_DEADLINE,
                "11:59 pm ET",
            ),
            DeadlineEvent("Lottery drawing", d(2026, 6, 20), DeadlineKind.DRAWING, "Acton Fairgrounds"),
        ),
        cycleYear = 2026,
        typicalWindow = "typically opens April 1, closes mid-May, draws mid-to-late June",
        sourceLabel = MOOSE_SRC,
        sourceUrl = MOOSE_URL,
    ),
    LicenseDeadline(
        name = "Antlerless deer permit lottery",
        side = Side.HUNT,
        summary = "The draw for a permit to take an antlerless deer (doe) in a chosen " +
            "Wildlife Management District. Free to apply; winners pay for the permit.",
        events = listOf(
            DeadlineEvent("Applications open", d(2026, 6, 25), DeadlineKind.APPLICATION_OPEN),
            DeadlineEvent(
                "Application deadline", d(2026, 8, 3), DeadlineKind.APPLICATION_DEADLINE,
                "11:59 pm",
            ),
            DeadlineEvent("Lottery drawing", d(2026, 8, 13), DeadlineKind.DRAWING),
            DeadlineEvent(
                "Permit payment deadline", d(2026, 9, 10), DeadlineKind.PAYMENT_DEADLINE,
                "Winners must claim and pay for the permit",
            ),
        ),
        cycleYear = 2026,
        typicalWindow = "typically opens late June, closes early August, draws mid-August",
        sourceLabel = DEER_SRC,
        sourceUrl = DEER_URL,
    ),
)
