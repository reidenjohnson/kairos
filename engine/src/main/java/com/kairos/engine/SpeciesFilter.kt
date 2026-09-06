package com.kairos.engine

/**
 * The user's species filter — which species Kairos shows and scores. It lives in
 * the engine (not the UI) so background scoring, like the Today timing hero in
 * [com.kairos.data.WeatherRepository.computeDayTiming], respects it too.
 *
 * A plain holder, not Compose state: the UI keeps its own observable mirror and
 * writes through to [enabledNames] so both stay in sync (see the app's SpeciesPrefs).
 * [enabledNames] == null means "no filter set — everything is enabled" (the default),
 * which is different from an empty set (the user turned everything off).
 */
object SpeciesFilter {
    @Volatile
    var enabledNames: Set<String>? = null

    /** True when [name] should be shown/scored under the current filter. */
    fun isEnabled(name: String): Boolean = enabledNames?.contains(name) ?: true

    /** [species] narrowed to the enabled set (all of them when no filter is set). */
    fun enabled(species: List<Species>): List<Species> =
        enabledNames?.let { set -> species.filter { it.name in set } } ?: species
}
