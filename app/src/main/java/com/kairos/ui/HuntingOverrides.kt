package com.kairos.ui

/**
 * Hand-verified hunting-access overrides for parcels the state's own record can't classify —
 * town and land-trust preserves whose hunting rules live only on the managing org's website
 * (e.g. Knight's Pond Preserve, which the state data tags a "Municipal Park" but which is in
 * fact open to hunting). Each entry is confirmed against a real published source and dated.
 *
 * This is the honest bridge over Maine's missing statewide hunting-access database: there is no
 * feed for these, so we verify them by hand and cite each one. Keep it sourced and dated, and
 * re-verify periodically — local rules change. Keyed by the conserved-lands PROJECT name
 * (case-insensitive, trimmed).
 */
internal object HuntingOverrides {
    /** open = confirmed huntable; !open = confirmed closed. [note] shows in the tap sheet. */
    data class Entry(
        val open: Boolean,
        val note: String,
        val source: String,
        val verified: String, // yyyy-MM, when we last confirmed it
    )

    private val BY_PROJECT: Map<String, Entry> = mapOf(
        "knight's pond preserve" to Entry(
            open = true,
            note = "Open to hunting, fishing and trapping during daylight hours; wear blaze orange " +
                "in season and respect posted abutting private land. Managed by the Town of " +
                "Cumberland and the Chebeague & Cumberland Land Trust.",
            source = "ccltmaine.org / Town of Cumberland",
            verified = "2026-09",
        ),
    )

    fun forProject(project: String?): Entry? =
        project?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { BY_PROJECT[it] }
}
