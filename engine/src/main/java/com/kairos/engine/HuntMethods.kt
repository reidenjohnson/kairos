package com.kairos.engine

/**
 * Hunting METHODS — the seasons a hunter actually signs up for (archery, firearms,
 * muzzleloader, …). Maine's season windows are already labeled by method (see
 * [MAINE_SEASONS]); this groups those labels into a small, stable set the user can
 * subscribe to in Settings, each shown in its own coordinated color.
 *
 * Subscribing FILTERS: when a filter is set, only windows whose method is enabled are
 * shown, and a species with no enabled window drops out. Default (no filter) = all on.
 */
enum class HuntMethod(val label: String) {
    ARCHERY("Archery"),
    EXPANDED_ARCHERY("Expanded archery"),
    FIREARMS("Firearms"),
    MUZZLELOADER("Muzzleloader"),
    // Everything that isn't one of the deer methods above: general seasons (grouse,
    // waterfowl, bear, hare, turkey) and the deer special days (youth / residents-only).
    // Not a "method you sign up for", so it's NOT subscribable and always shows.
    GENERAL("General season"),
}

/** The methods a user actually subscribes to in Settings (the deer choices). */
val SUBSCRIBABLE_METHODS: List<HuntMethod> =
    listOf(HuntMethod.ARCHERY, HuntMethod.EXPANDED_ARCHERY, HuntMethod.FIREARMS, HuntMethod.MUZZLELOADER)

/** Classify a season window by its label into a [HuntMethod]. */
fun methodOf(windowLabel: String): HuntMethod {
    val l = windowLabel.lowercase()
    return when {
        "expanded archery" in l -> HuntMethod.EXPANDED_ARCHERY
        "archery" in l -> HuntMethod.ARCHERY
        "firearms" in l -> HuntMethod.FIREARMS
        "muzzleloader" in l -> HuntMethod.MUZZLELOADER
        else -> HuntMethod.GENERAL
    }
}

/**
 * The user's method subscription, bridged from the app's prefs into the engine the
 * same way [SpeciesFilter] is (a plain @Volatile holder the UI writes through), so any
 * screen or background job can respect it. null = no filter set (every method on).
 */
object MethodFilter {
    @Volatile
    var enabledMethods: Set<HuntMethod>? = null

    /** True when [m] should be shown. GENERAL always shows (it isn't subscribable). */
    fun isEnabled(m: HuntMethod): Boolean =
        m == HuntMethod.GENERAL || (enabledMethods?.contains(m) ?: true)

    /** The windows of [s] narrowed to enabled methods; GENERAL windows always kept. */
    fun windows(s: SpeciesSeasons): List<SeasonWindow> =
        enabledMethods?.let { set -> s.windows.filter { isEnabled(methodOf(it.label)) } } ?: s.windows

    /** True when [s] has at least one window still showing (or no filter). */
    fun coversAny(s: SpeciesSeasons): Boolean =
        s.noOpenSeason || windows(s).isNotEmpty() || enabledMethods == null
}
