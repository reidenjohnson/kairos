package com.kairos.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide links, so any screen can point at any other without threading callbacks through every
 * layer: open photos full screen, a photo's details, a buck, the forecast at a camera and hour,
 * the buck map at a moment. KairosApp provides the real ones.
 */
class AppNav(
    /** Full-screen viewer over [ids], starting at [index]. */
    val openPhotos: (ids: List<String>, index: Int) -> Unit = { _, _ -> },
    /** A photo's details card (time, weather, the write-up). */
    val openPhotoDetails: (id: String) -> Unit = {},
    val openBuck: (id: String) -> Unit = {},
    /** The Forecast tab at a camera (null = keep the current one) and hour (null = keep). */
    val openForecast: (camId: String?, t: Long?) -> Unit = { _, _ -> },
    /** The buck map at a moment, optionally showing one buck. */
    val openMap: (t: Long?, buckId: String?) -> Unit = { _, _ -> },
    /** Bucks, on the Sort tab. */
    val openSort: () -> Unit = {},
)

val LocalAppNav = staticCompositionLocalOf { AppNav() }

/**
 * true while a card (photo details, a buck...) is open over this page. A pop-up is its own window,
 * so it would sit on top of that card; pop-ups hide while it's true and come back on back.
 */
val LocalCovered = androidx.compose.runtime.compositionLocalOf { false }
