package com.kairos.ui

/**
 * The research behind each species' score — the "show your work" view. Every entry
 * is copied verbatim from reference/SOURCES.md (title + exact URL); nothing here is
 * guessed. [citationsFor] returns the handful most relevant to a species' heaviest
 * factors so the detail screen can cite where the numbers come from.
 */
data class Citation(val label: String, val url: String)

private val TEMP_GAME = Citation(
    "MeatEater — temperature drives deer movement",
    "https://www.themeateater.com/wired-to-hunt/whitetail-hunting/does-barometric-pressure-affect-deer-movement",
)
private val MOOSE_TEMP = Citation(
    "J. Mammalogy — moose heat thresholds",
    "https://academic.oup.com/jmammal/article/100/1/169/5299335",
)
private val ELK_TEMP = Citation(
    "Outdoor Canada — elk & weather",
    "https://www.outdoorcanada.ca/elkweather/",
)
private val BEAR_TEMP = Citation(
    "HuntWise — black bear timing",
    "https://huntwise.com/field-guide/bear/best-times-to-hunt-black-bear",
)
private val TREND_FISH = Citation(
    "In-Fisherman — falling pressure & bass",
    "https://www.in-fisherman.com/editorial/barometric-pressure-and-bass/153689",
)
private val TREND_GAME = Citation(
    "Deer & Deer Hunting — pressure drops & activity",
    "https://www.deeranddeerhunting.com/content/articles/1-how-weather-affects-deer-behavior-alsheimers-greatest-insights",
)
private val RANGE_GAME = Citation(
    "MidWest Outdoors — pressure band & whitetail",
    "https://midwestoutdoors.com/hunting/hunting-december-issue-barometric-pressure-and-whitetail-movement/",
)
private val RANGE_FISH = Citation(
    "Tempest — pressure & fishing",
    "https://tempest.earth/resources/barometric-pressure-and-fishing/",
)
private val FRONT_DUCK = Citation(
    "Ducks Unlimited — cold fronts & ducks",
    "https://www.ducks.org/hunting/waterfowl-hunting-tips/forecast-your-duck-hunting-success-weather-matters",
)
private val FRONT_GAME = Citation(
    "ScentLok — cold fronts & whitetail",
    "https://www.scentlok.com/utilizing-barometric-pressure-and-cold-fronts-for-whitetail-buck-harvesting/",
)
private val WIND_DUCK = Citation(
    "Realtree — how much wind ducks need",
    "https://realtree.com/the-duck-blog/how-much-wind-do-duck-hunters-need",
)
private val WIND_UPLAND = Citation(
    "Minnesota DNR — grouse & woodcock",
    "https://www.dnr.state.mn.us/gohunting/ruffed-grouse-and-woodcock-hunting.html",
)
private val WIND_WALLEYE = Citation(
    "Northern Ontario — the \"walleye chop\"",
    "https://northernontario.travel/fishing/wind-cloud-and-walleye-why-its-important-understand-weather-when-fishing",
)
private val CLOUD_WALLEYE = Citation(
    "Mack's Lure — light intensity & walleye",
    "https://mackslure.com/blogs/mack-attack/harrington-how-light-intensity-impacts-walleye-fishing",
)
private val MOON_DEBUNK = Citation(
    "MeatEater — the moon & deer movement (debunked)",
    "https://www.themeateater.com/wired-to-hunt/whitetail-hunting/new-research-confirms-the-moon-doesnt-affect-deer-movement",
)
private val MOON_HARE = Citation(
    "Griffin et al. — moonlight & hare predation",
    "https://www.umt.edu/mills-lab/files/2015/01/griffin05moonlight.pdf",
)
private val TEMP_LM = Citation(
    "In-Fisherman — largemouth & temperature",
    "https://www.in-fisherman.com/editorial/largemouth-bass-temperature-thermoclines/494247",
)
private val TEMP_SM = Citation(
    "Bassmaster — smallmouth & temperature",
    "https://www.bassmaster.com/how-to/news/smallmouth-and-temperature/",
)
private val TEMP_COLD = Citation(
    "Maine IF&W — coldwater fish temperatures",
    "https://www.maine.gov/ifw/fishing-boating/fishing/maine-fishing-guide/catch-specific-fish.html",
)

/** The most relevant sources per species, ordered by how much each factor drives its score. */
fun citationsFor(speciesName: String): List<Citation> = when (speciesName) {
    "Whitetail deer" -> listOf(TEMP_GAME, FRONT_GAME, RANGE_GAME, TREND_GAME, MOON_DEBUNK)
    "Moose" -> listOf(MOOSE_TEMP, TEMP_GAME, FRONT_GAME)
    "Elk" -> listOf(ELK_TEMP, TEMP_GAME, FRONT_GAME)
    "Black bear" -> listOf(BEAR_TEMP, FRONT_GAME, TREND_GAME)
    "Snowshoe hare" -> listOf(MOON_HARE, WIND_UPLAND, TEMP_GAME)
    "Upland birds" -> listOf(WIND_UPLAND, TEMP_GAME)
    "Waterfowl" -> listOf(FRONT_DUCK, WIND_DUCK)
    "Largemouth bass" -> listOf(TREND_FISH, TEMP_LM, RANGE_FISH)
    "Smallmouth bass" -> listOf(TREND_FISH, TEMP_SM, RANGE_FISH)
    "Salmon / togue / brookie" -> listOf(TEMP_COLD, TREND_FISH, RANGE_FISH)
    "Walleye" -> listOf(WIND_WALLEYE, CLOUD_WALLEYE, TREND_FISH)
    else -> emptyList()
}
