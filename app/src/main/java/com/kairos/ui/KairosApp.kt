package com.kairos.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.data.Forecast
import com.kairos.data.ForecastCache
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.Outlook
import com.kairos.data.ScoreHistory
import com.kairos.data.WeatherRepository
import com.kairos.engine.Side
import com.kairos.engine.scoreAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate

/** The forecast side of the app's state. [live] false means it came from cache. */
sealed interface UiState {
    data object Loading : UiState
    data class Ready(val forecast: Forecast, val live: Boolean, val savedAtMillis: Long) : UiState
    data class Error(val message: String) : UiState
}

/** Longest a refresh spins before falling back to the cached forecast. */
private const val REFRESH_CAP_MS = 20_000L

private enum class Dest { TODAY, SPECIES, MAP, WIND, OFFLINE, WEATHER, SEASONS, DEADLINES, WEEKLY, SETTINGS, DETAIL, PLAN, LEARN, LESSON, BUCKS, BUCK, BUCK_PHOTO, CAM_PIN, BUCK_REVIEW, BUCK_SETTINGS, BUCK_MAP, FORECAST, MORE, ALL_SPECIES }

/**
 * One place in the app. [seasonFocus] applies to SEASONS; [detailSpecies] to DETAIL;
 * the PLAN page uses [planSpecies] (a species plan) or [planSide] (a general side plan);
 * LESSON uses [lessonId].
 */
private data class NavEntry(
    val dest: Dest,
    val listSide: Side? = null,
    val seasonFocus: String? = null,
    val detailSpecies: String? = null,
    val planSpecies: String? = null,
    val planSide: Side? = null,
    val lessonId: String? = null,
    val buckId: String? = null,
    val photoId: String? = null,
    /** BUCKS: open on this tab ("GALLERY", "SORT"...), and for the map, this moment + buck. */
    val bucksTab: String? = null,
    val mapTime: Long? = null,
    /** BUCKS Gallery: open on this filter (a camera's name). */
    val galleryFilter: String? = null,
    /** FORECAST: open at this camera and hour. */
    val huntCam: String? = null,
    val huntHour: Long? = null,
)

/** The five sections on the bottom bar. Each keeps its own history, like any phone app. */
private enum class Section(val label: String, val root: Dest) {
    TODAY("Today", Dest.TODAY), BUCKS("Bucks", Dest.BUCKS), FORECAST("Forecast", Dest.FORECAST), LEARN("Learn", Dest.LEARN), MORE("More", Dest.MORE);
}

/** Details that slide up as a card over the screen you were on (back or a swipe down closes them). */
private val SHEETS = setOf(Dest.BUCK, Dest.BUCK_PHOTO, Dest.WEATHER, Dest.DETAIL, Dest.PLAN, Dest.LESSON, Dest.BUCK_SETTINGS, Dest.BUCK_REVIEW)

/** Edge-to-edge pages with their own controls: no top or bottom bar. */
private val FULL = setOf(Dest.BUCK_MAP, Dest.CAM_PIN)

/**
 * App entry: owns location + forecast + weekly outlook + navigation. Five sections on a bottom bar
 * (Today, Bucks, Forecast, Learn, More), each with its own history; details stack as cards over
 * the screen you're on; photos open full screen. Every screen can link to any other through
 * [LocalAppNav]. Every successful live forecast is logged to [ScoreHistory] for the Trends chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosApp(onToggleTheme: (Boolean) -> Unit = {}) {
    KairosTheme {
        val context = LocalContext.current
        // Keep the system-bar icons legible: dark icons on the light theme, light on dark.
        val view = LocalView.current
        val darkTheme = KairosColors.dark
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }

        // Show the last cached forecast immediately on open, then refresh — so the
        // app isn't a blank spinner while it fetches.
        var state by remember {
            mutableStateOf<UiState>(
                ForecastCache.load(context)
                    ?.let { UiState.Ready(it.forecast, live = false, it.savedAtMillis) }
                    ?: UiState.Loading,
            )
        }
        var outlook by remember { mutableStateOf<Outlook?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var reloadKey by remember { mutableIntStateOf(0) }

        // ---- Bucks, app-wide: new photos in his linked folders come in whenever the app comes
        // to the front, and the analysis re-runs whenever anything it reads changes (debounced) or
        // its forecast is over an hour old. So every screen shows the same, current numbers.
        val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { com.kairos.bucks.BuckStore.ensureLoaded(context) }
            com.kairos.bucks.BuckPrefs.load(context)
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.kairos.bucks.PhotoSync.checkFolders(context)
                val a = com.kairos.bucks.BuckAnalysis.state
                if (a != null && System.currentTimeMillis() - a.at > 3_600_000L) com.kairos.bucks.BuckAnalysis.run(com.kairos.bucks.BuckStore.db)
            }
        }
        val bdb = com.kairos.bucks.BuckStore.db
        val bp = com.kairos.bucks.BuckPrefs
        val bucksKey = bdb.photos.joinToString { "${it.id}${it.label}${it.buckIds}${it.cameraId}${it.capture}${it.heading}" } +
            bdb.cameras.joinToString { "${it.lat}${it.lon}${it.facingDeg}${it.windSectors.sorted()}" } + bdb.rut + bp.visitMin +
            // Plan text and bin labels are written in his units.
            bp.temp + bp.wind + bp.press + bp.rain + bp.clock24
        LaunchedEffect(bucksKey) { kotlinx.coroutines.delay(700); com.kairos.bucks.BuckAnalysis.run(com.kairos.bucks.BuckStore.db) }

        // ---- Navigation: a history per section; back pops it, then returns to Today.
        var section by remember { mutableStateOf(Section.TODAY) }
        val stacks = remember { Section.entries.associateWith { s -> mutableStateListOf(NavEntry(s.root)) } }
        val stack = stacks.getValue(section)
        val current = stack.last()
        // Each screen keeps its own state (tab, filter, scroll) while you're deeper, so back
        // lands exactly where you were. Keyed by section + depth + entry; dropped when popped.
        val saveHolder = rememberSaveableStateHolder()
        fun stateKey(s: Section, depth: Int, e: NavEntry) = "${s.name}:$depth:${e.hashCode()}"
        fun goTo(entry: NavEntry) { if (entry != stack.last()) stack.add(entry) }
        fun replaceTop(entry: NavEntry) {
            saveHolder.removeState(stateKey(section, stack.lastIndex, stack.last()))
            stack[stack.lastIndex] = entry
        }
        fun popToRoot(s: Section) {
            val st = stacks.getValue(s)
            while (st.size > 1) { saveHolder.removeState(stateKey(s, st.lastIndex, st.last())); st.removeAt(st.lastIndex) }
        }
        fun pop() {
            if (stack.size > 1) {
                saveHolder.removeState(stateKey(section, stack.lastIndex, stack.last()))
                stack.removeAt(stack.lastIndex)
            } else if (section != Section.TODAY) section = Section.TODAY
        }
        // Tap the section you're in: back to its top (iOS / Android convention).
        fun select(s: Section) { if (s == section) popToRoot(s) else section = s }
        BackHandler(enabled = stack.size > 1 || section != Section.TODAY) { pop() }

        var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
        val nav = remember {
            AppNav(
                openPhotos = { ids, i -> viewer = ids to i },
                openPhotoDetails = { id -> stacks.getValue(section).let { st -> if (st.last() != NavEntry(Dest.BUCK_PHOTO, photoId = id)) st.add(NavEntry(Dest.BUCK_PHOTO, photoId = id)) } },
                openBuck = { id -> stacks.getValue(section).let { st -> if (st.last() != NavEntry(Dest.BUCK, buckId = id)) st.add(NavEntry(Dest.BUCK, buckId = id)) } },
                openForecast = { cam, t ->
                    // The Forecast section opens fresh at that camera + hour.
                    popToRoot(Section.FORECAST)
                    val st = stacks.getValue(Section.FORECAST)
                    saveHolder.removeState(stateKey(Section.FORECAST, 0, st[0]))
                    st[0] = NavEntry(Dest.FORECAST, huntCam = cam, huntHour = t)
                    section = Section.FORECAST
                },
                openMap = { t, b -> stacks.getValue(section).add(NavEntry(Dest.BUCK_MAP, mapTime = t, buckId = b)) },
                openSort = {
                    popToRoot(Section.BUCKS)
                    val st = stacks.getValue(Section.BUCKS)
                    saveHolder.removeState(stateKey(Section.BUCKS, 0, st[0]))
                    st[0] = NavEntry(Dest.BUCKS, bucksTab = "SORT")
                    section = Section.BUCKS
                },
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { reloadKey++ }

        LaunchedEffect(Unit) {
            if (!LocationProvider.hasPermission(context)) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }

        // Pre-fetch the official Maine map overlays to disk in the background on launch, so
        // the Map opens instantly (and works offline) instead of downloading on first view.
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) { preloadOverlays(context) }
        }

        LaunchedEffect(reloadKey) {
            if (state is UiState.Ready) refreshing = true else state = UiState.Loading
            val place = LocationProvider.current(context) ?: Location.SEBAGO
            // Remember where we looked so the daily reminder can forecast it without a slow GPS fix.
            com.kairos.notify.NotifyPrefsBridge.saveLastPlace(context, place)
            try {
                // The fetch is a chain of blocking HTTP calls with retries and a backup
                // service; on a bad connection it could spin for minutes. Cap the wait: run it
                // detached and stop waiting after REFRESH_CAP_MS, then show the cached forecast.
                // (A blocking call can't be interrupted, so the late request just finishes and
                // is dropped.)
                val fetch = CoroutineScope(Dispatchers.IO).async { WeatherRepository.fetch(place) }
                val forecast = withTimeoutOrNull(REFRESH_CAP_MS) { fetch.await() }
                    ?: throw java.io.IOException("The weather service is slow right now.")
                ForecastCache.save(context, forecast)
                ScoreHistory.record(context, LocalDate.now(), scoreAll(forecast.conditions))
                state = UiState.Ready(forecast, live = true, savedAtMillis = System.currentTimeMillis())
                refreshing = false
                // Weekly outlook is a second, heavier fetch — load it after the
                // forecast is already on screen so it never delays the first paint.
                outlook = runCatching {
                    withContext(Dispatchers.IO) { WeatherRepository.fetchOutlook(place) }
                }.getOrNull()
            } catch (e: CancellationException) {
                throw e // a newer refresh replaced this one; let it own the spinner
            } catch (e: Exception) {
                android.util.Log.w("KairosFetch", "forecast refresh failed", e)
                val cached = ForecastCache.load(context)
                state = if (cached != null) {
                    UiState.Ready(cached.forecast, live = false, savedAtMillis = cached.savedAtMillis)
                } else {
                    UiState.Error(e.message ?: "Couldn't reach the weather service.")
                }
                refreshing = false
            }
        }

        // The timing hero (per-side averages) is computed at fetch time over the enabled
        // species, so a filter change needs a recompute. The visible lists update live;
        // re-fetch once per change to bring the hero in line. (Skips the first pass.)
        var filterInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(SpeciesPrefs.enabled) {
            if (filterInitialized) reloadKey++ else filterInitialized = true
        }

        fun titleOf(e: NavEntry) = when (e.dest) {
            Dest.TODAY -> "Today"
            Dest.SPECIES -> if (e.listSide == Side.FISH) "Fish" else "Hunt"
            Dest.MAP -> "Map"
            Dest.WIND -> "Wind"
            Dest.OFFLINE -> "Offline maps"
            Dest.WEATHER -> "Weather"
            Dest.SEASONS -> "Seasons"
            Dest.DEADLINES -> "Licenses & lotteries"
            Dest.WEEKLY -> "Weekly outlook"
            Dest.SETTINGS -> "Settings"
            Dest.DETAIL -> e.detailSpecies ?: "Details"
            Dest.PLAN -> "Game plan"
            Dest.LEARN, Dest.LESSON -> "Learn"
            Dest.BUCKS -> "Bucks"
            Dest.BUCK -> com.kairos.bucks.BuckStore.db.buck(e.buckId)?.name ?: "Buck"
            Dest.BUCK_PHOTO -> "Sighting"
            Dest.CAM_PIN -> "Place camera"
            Dest.BUCK_REVIEW -> "Re-sort"
            Dest.BUCK_SETTINGS -> "Bucks settings"
            Dest.BUCK_MAP -> "Map"
            Dest.FORECAST -> "Forecast"
            Dest.ALL_SPECIES -> "All species today"
            Dest.MORE -> "More"
        }

        @Composable
        fun Screen(e: NavEntry) {
            when (e.dest) {
                // With a camera pinned, Today is the hero page for your stand; before that, the weather here.
                Dest.TODAY -> if (com.kairos.bucks.BuckStore.db.cameras.any { it.lat != null && it.lon != null }) com.kairos.ui.bucks.TodayHome() else TodayScreen(
                    state = state,
                    refreshing = refreshing,
                    onRefresh = { reloadKey++ },
                    onOpenSide = { s -> goTo(NavEntry(Dest.SPECIES, listSide = s)) },
                    onOpenDetail = { species -> goTo(NavEntry(Dest.DETAIL, detailSpecies = species)) },
                    onOpenWeather = { goTo(NavEntry(Dest.WEATHER)) },
                )
                Dest.ALL_SPECIES -> TodayScreen(
                    state = state,
                    refreshing = refreshing,
                    onRefresh = { reloadKey++ },
                    onOpenSide = { s -> goTo(NavEntry(Dest.SPECIES, listSide = s)) },
                    onOpenDetail = { species -> goTo(NavEntry(Dest.DETAIL, detailSpecies = species)) },
                    onOpenWeather = { goTo(NavEntry(Dest.WEATHER)) },
                    allSpecies = true,
                )
                Dest.WEATHER -> WeatherScreen(state = state)
                Dest.MAP -> MapScreen()
                Dest.WIND -> WindScreen()
                Dest.OFFLINE -> OfflineMapsScreen()
                Dest.SPECIES -> SideSpeciesScreen(
                    state = state,
                    side = e.listSide ?: Side.HUNT,
                    onOpenDetail = { species -> goTo(NavEntry(Dest.DETAIL, detailSpecies = species)) },
                    onOpenSidePlan = { s -> goTo(NavEntry(Dest.PLAN, planSide = s)) },
                )
                Dest.SEASONS -> SeasonsScreen(focusSpecies = e.seasonFocus)
                Dest.DEADLINES -> DeadlinesScreen()
                Dest.WEEKLY -> TrendsScreen(state = state, outlook = outlook)
                Dest.SETTINGS -> SettingsScreen()
                Dest.DETAIL -> DetailScreen(
                    state = state,
                    speciesName = e.detailSpecies.orEmpty(),
                    onOpenSeason = { species -> goTo(NavEntry(Dest.SEASONS, seasonFocus = species)) },
                    onOpenPlan = { species -> goTo(NavEntry(Dest.PLAN, planSpecies = species)) },
                )
                Dest.PLAN -> GamePlanScreen(state = state, speciesName = e.planSpecies, side = e.planSide)
                Dest.LEARN -> LearnScreen(onOpenLesson = { id -> goTo(NavEntry(Dest.LESSON, lessonId = id)) })
                Dest.BUCKS -> com.kairos.ui.bucks.BucksScreen(
                    onOpenBuck = { id -> goTo(NavEntry(Dest.BUCK, buckId = id)) },
                    onPinCamera = { id -> goTo(NavEntry(Dest.CAM_PIN, buckId = id)) },
                    onOpenPhoto = { id -> goTo(NavEntry(Dest.BUCK_PHOTO, photoId = id)) },
                    onReview = { id -> goTo(NavEntry(Dest.BUCK_REVIEW, buckId = id)) },
                    onSettings = { goTo(NavEntry(Dest.BUCK_SETTINGS)) },
                    onOpenGallery = { f -> goTo(NavEntry(Dest.BUCKS, bucksTab = "GALLERY", galleryFilter = f)) },
                    onOpenMap = { goTo(NavEntry(Dest.BUCK_MAP)) },
                    onOpenMapAt = { t, b -> goTo(NavEntry(Dest.BUCK_MAP, mapTime = t, buckId = b)) },
                    startTab = e.bucksTab,
                    startFilter = e.galleryFilter,
                    mapTime = e.mapTime,
                    mapBuck = e.buckId,
                )
                Dest.FORECAST -> com.kairos.ui.bucks.ForecastScreen(state = state, startCam = e.huntCam, startHour = e.huntHour)
                Dest.MORE -> MoreScreen(
                    onOpen = { d, side -> goTo(NavEntry(d, listSide = side)) },
                    onToggleTheme = onToggleTheme,
                )
                Dest.BUCK_MAP -> com.kairos.ui.bucks.BuckMapPane(
                    startTime = e.mapTime,
                    startBuck = e.buckId,
                    onOpenPhoto = { id -> goTo(NavEntry(Dest.BUCK_PHOTO, photoId = id)) },
                    onPinCamera = { id -> goTo(NavEntry(Dest.CAM_PIN, buckId = id)) },
                    onGallery = { f -> section = Section.BUCKS; stacks.getValue(Section.BUCKS).add(NavEntry(Dest.BUCKS, bucksTab = "GALLERY", galleryFilter = f)) },
                    onCamerasTab = { pop() },
                    onBack = { pop() },
                )
                Dest.BUCK_SETTINGS -> com.kairos.ui.bucks.BuckSettingsScreen(onStartedOver = { pop() })
                Dest.BUCK_REVIEW -> com.kairos.ui.bucks.ReviewScreen(buckId = e.buckId.orEmpty(), onDone = { pop() })
                Dest.CAM_PIN -> com.kairos.ui.bucks.CameraPinScreen(cameraId = e.buckId.orEmpty(), onDone = { pop() })
                Dest.BUCK -> com.kairos.ui.bucks.BuckScreen(
                    buckId = e.buckId.orEmpty(),
                    onOpenPhoto = { id -> goTo(NavEntry(Dest.BUCK_PHOTO, photoId = id)) },
                    onDeleted = { pop() },
                    onReview = { id -> goTo(NavEntry(Dest.BUCK_REVIEW, buckId = id)) },
                    onMap = { t -> goTo(NavEntry(Dest.BUCK_MAP, mapTime = t, buckId = e.buckId)) },
                )
                Dest.BUCK_PHOTO -> com.kairos.ui.bucks.PhotoMomentScreen(
                    photoId = e.photoId.orEmpty(),
                    onOpenBuck = { id -> goTo(NavEntry(Dest.BUCK, buckId = id)) },
                    onGone = { pop() },
                    onMap = { t, buck -> goTo(NavEntry(Dest.BUCK_MAP, mapTime = t, buckId = buck)) },
                )
                // "Next" replaces the lesson rather than stacking, so back returns to Learn.
                Dest.LESSON -> LessonScreen(lessonId = e.lessonId.orEmpty(), onOpenLesson = { id -> replaceTop(NavEntry(Dest.LESSON, lessonId = id)) })
            }
        }

        // The page underneath = the newest non-card entry; the card (if any) slides over it.
        val baseIdx = stack.indexOfLast { it.dest !in SHEETS }.coerceAtLeast(0)
        val base = stack[baseIdx]
        val sheet = current.takeIf { it.dest in SHEETS && stack.lastIndex > baseIdx }
        val full = base.dest in FULL

        CompositionLocalProvider(LocalAppNav provides nav) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = KairosColors.Bg,
                    topBar = {
                        if (!full) TopAppBar(
                            title = { Text(titleOf(base), fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                if (baseIdx > 0) IconButton(onClick = { pop() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                if (base.dest == Dest.TODAY || base.dest == Dest.WEEKLY) {
                                    IconButton(onClick = { reloadKey++ }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        )
                    },
                    bottomBar = { if (!full) BottomBar(section) { select(it) } },
                ) { padding ->
                    Box(Modifier.fillMaxSize().background(screenBackground()).padding(padding)) {
                        CompositionLocalProvider(LocalCovered provides (sheet != null)) {
                            saveHolder.SaveableStateProvider(stateKey(section, baseIdx, base)) { Screen(base) }
                        }
                    }
                }
                sheet?.let { s ->
                    androidx.compose.runtime.key(stateKey(section, stack.lastIndex, s)) {
                        SheetFrame(titleOf(s), onClose = { pop() }) {
                            saveHolder.SaveableStateProvider(stateKey(section, stack.lastIndex, s)) { Screen(s) }
                        }
                    }
                }
                viewer?.let { (ids, i) -> com.kairos.ui.bucks.PhotoViewer(ids, i) { viewer = null } }
            }
        }
    }
}

@Composable
private fun BottomBar(section: Section, onSelect: (Section) -> Unit) {
    NavigationBar(containerColor = KairosColors.Surface, tonalElevation = 0.dp) {
        Section.entries.forEach { s ->
            val icon = when (s) {
                Section.TODAY -> Icons.Filled.Star
                Section.BUCKS -> Icons.Outlined.CameraAlt
                Section.FORECAST -> Icons.Outlined.Air
                Section.LEARN -> Icons.Outlined.School
                Section.MORE -> Icons.Outlined.Menu
            }
            NavigationBarItem(
                selected = s == section,
                onClick = { onSelect(s) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(s.label, fontWeight = if (s == section) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = KairosColors.OnSeg,
                    selectedTextColor = KairosColors.Text,
                    indicatorColor = KairosColors.SegBottom,
                    unselectedIconColor = KairosColors.Dim,
                    unselectedTextColor = KairosColors.Dim,
                ),
            )
        }
    }
}

/**
 * A card that slides up over the screen you were on (Apple Maps style): grab the top and pull it
 * down, tap the dimmed screen, or press back to close it.
 */
@Composable
private fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val rise = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f)) }
    var drag by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val closeAt = with(density) { 140.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * (1f - rise.value)))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onClose() },
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)
                .graphicsLayer { translationY = rise.value * size.height + drag }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(KairosColors.Bg)
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { },
        ) {
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > closeAt) onClose() else drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { _, dy -> drag = (drag + dy).coerceAtLeast(0f) }
                },
            ) {
                Box(Modifier.padding(top = 8.dp).align(androidx.compose.ui.Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(KairosColors.Line))
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = KairosColors.Text, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close", tint = KairosColors.Dim) }
                }
            }
            // Cards sit outside the Scaffold, so untinted text would fall back to black on the dark theme.
            Box(Modifier.weight(1f).navigationBarsPadding()) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides KairosColors.Text) { content() }
            }
        }
    }
}

/** Everything that isn't one of the four main sections, grouped, one tap each. */
@Composable
private fun MoreScreen(onOpen: (Dest, Side?) -> Unit, onToggleTheme: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        MoreGroup("Maps & seasons") {
            MoreRow("Seasons", "Maine seasons by species, on a chart", Icons.Filled.CalendarMonth) { onOpen(Dest.SEASONS, null) }
            MoreRow("Map", "Public land, WMAs, districts, archery zones", Icons.Outlined.Map) { onOpen(Dest.MAP, null) }
            MoreRow("Wind", "Wind anywhere: 7 days back to 48 h ahead", Icons.Outlined.Air) { onOpen(Dest.WIND, null) }
            MoreRow("Offline maps", "Areas saved for no signal", Icons.Outlined.CloudDownload) { onOpen(Dest.OFFLINE, null) }
        }
        // The general all-species features, out of the deer flow but kept.
        MoreGroup("Other species") {
            MoreRow("All species today", "Hunt and fish scores, top picks, weather sayings", Icons.Outlined.Forest) { onOpen(Dest.ALL_SPECIES, null) }
            MoreRow("Hunt", "Every hunting species, best first", Icons.Outlined.Forest) { onOpen(Dest.SPECIES, Side.HUNT) }
            MoreRow("Fish", "Every fish species, best first", Icons.Outlined.WaterDrop) { onOpen(Dest.SPECIES, Side.FISH) }
            MoreRow("Weekly outlook", "Each day's forecast score vs what happened", Icons.AutoMirrored.Outlined.ShowChart) { onOpen(Dest.WEEKLY, null) }
            MoreRow("Licenses & lotteries", "Moose and antlerless deer lottery windows", Icons.Outlined.EditCalendar) { onOpen(Dest.DEADLINES, null) }
        }
        MoreGroup("App") {
            MoreRow("Settings", "Species you follow, water temperature", Icons.Outlined.Settings) { onOpen(Dest.SETTINGS, null) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = KairosColors.Water)
                Spacer(Modifier.width(16.dp))
                Text("Dark mode", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = KairosColors.Text)
                Switch(checked = KairosColors.dark, onCheckedChange = onToggleTheme)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MoreGroup(title: String, rows: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), color = KairosColors.Dim, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KairosColors.Surface).padding(vertical = 4.dp)) { rows() }
    }
}

@Composable
private fun MoreRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = KairosColors.Water)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = KairosColors.Text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = KairosColors.Dim)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = KairosColors.Faint)
    }
}
