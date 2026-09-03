package com.kairos.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The forecast side of the app's state. [live] false means it came from cache. */
sealed interface UiState {
    data object Loading : UiState
    data class Ready(val forecast: Forecast, val live: Boolean, val savedAtMillis: Long) : UiState
    data class Error(val message: String) : UiState
}

private enum class Dest { TODAY, SEASONS, WEEKLY }

/** One place in the app. [side] applies to TODAY; [seasonFocus] to SEASONS. */
private data class NavEntry(val dest: Dest, val side: Side? = null, val seasonFocus: String? = null)

/**
 * App entry: owns location + forecast + weekly outlook + navigation, behind a
 * left navigation drawer (the approved redesign — see design/Menu.dc.html). The
 * Today screen filters Hunt/Fish/Best; Seasons and Weekly are their own screens.
 * Every successful live forecast is logged to [ScoreHistory] for the Trends chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairosApp() {
    KairosTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)

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

        // Manual nav with a back stack so the system back button returns to the
        // previous screen (e.g. Seasons → back → Today) instead of exiting.
        var current by remember { mutableStateOf(NavEntry(Dest.TODAY)) }
        val backStack = remember { mutableStateListOf<NavEntry>() }
        val goTo = { entry: NavEntry ->
            if (entry != current) {
                backStack.add(current)
                current = entry
            }
        }
        val dest = current.dest
        val side = current.side

        // System back: close the drawer first, else pop the nav stack.
        BackHandler(enabled = drawerState.isOpen || backStack.isNotEmpty()) {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (backStack.isNotEmpty()) {
                current = backStack.removeAt(backStack.lastIndex)
            }
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

        LaunchedEffect(reloadKey) {
            if (state is UiState.Ready) refreshing = true else state = UiState.Loading
            val place = LocationProvider.current(context) ?: Location.SEBAGO
            try {
                val forecast = withContext(Dispatchers.IO) { WeatherRepository.fetch(place) }
                ForecastCache.save(context, forecast)
                ScoreHistory.record(context, LocalDate.now(), scoreAll(forecast.conditions))
                state = UiState.Ready(forecast, live = true, savedAtMillis = System.currentTimeMillis())
                refreshing = false
                // Weekly outlook is a second, heavier fetch — load it after the
                // forecast is already on screen so it never delays the first paint.
                outlook = runCatching {
                    withContext(Dispatchers.IO) { WeatherRepository.fetchOutlook(place) }
                }.getOrNull()
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

        val placeLabel = (state as? UiState.Ready)?.forecast?.placeLabel ?: "Locating…"

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    placeLabel = placeLabel,
                    current = dest,
                    currentSide = side,
                    onSelect = { d, s ->
                        goTo(NavEntry(d, if (d == Dest.TODAY) s else null))
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            val title = when (dest) {
                Dest.TODAY -> when (side) {
                    Side.HUNT -> "Hunt"
                    Side.FISH -> "Fish"
                    null -> "Today's Best"
                }
                Dest.SEASONS -> "Seasons"
                Dest.WEEKLY -> "Weekly outlook"
            }
            Scaffold(
                containerColor = KairosColors.Bg,
                topBar = {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (dest != Dest.SEASONS) {
                                IconButton(onClick = { reloadKey++ }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(screenBackground())
                        .padding(padding),
                ) {
                    when (dest) {
                        Dest.TODAY -> TodayScreen(
                            state = state,
                            sideFilter = side,
                            refreshing = refreshing,
                            onRefresh = { reloadKey++ },
                            onSelectSide = { current = current.copy(side = it) },
                            onOpenSeason = { species ->
                                goTo(NavEntry(Dest.SEASONS, seasonFocus = species))
                            },
                        )
                        Dest.SEASONS -> SeasonsScreen(focusSpecies = current.seasonFocus)
                        Dest.WEEKLY -> TrendsScreen(state = state, outlook = outlook)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    placeLabel: String,
    current: Dest,
    currentSide: Side?,
    onSelect: (Dest, Side?) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = KairosColors.Surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF2E5E4E), Color(0xFF3B9E6E)),
                        ),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                // Same spacing as the launcher icon: mark ~43% of the tile, nudged right.
                KairosMark(size = 20.dp, modifier = Modifier.offset(x = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Kairos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(placeLabel, style = MaterialTheme.typography.bodySmall, color = KairosColors.Faint)
        }
        Spacer(Modifier.height(18.dp))

        DrawerItem("Today's Best", Icons.Filled.Star, current == Dest.TODAY && currentSide == null) {
            onSelect(Dest.TODAY, null)
        }
        DrawerItem("Hunt", Icons.Outlined.Forest, current == Dest.TODAY && currentSide == Side.HUNT) {
            onSelect(Dest.TODAY, Side.HUNT)
        }
        DrawerItem("Fish", Icons.Outlined.WaterDrop, current == Dest.TODAY && currentSide == Side.FISH) {
            onSelect(Dest.TODAY, Side.FISH)
        }
        DrawerItem("Seasons", Icons.Filled.CalendarMonth, current == Dest.SEASONS) {
            onSelect(Dest.SEASONS, null)
        }
        DrawerItem("Weekly outlook", Icons.Outlined.ShowChart, current == Dest.WEEKLY) {
            onSelect(Dest.WEEKLY, null)
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = KairosColors.SegBottom,
            selectedTextColor = KairosColors.OnSeg,
            selectedIconColor = KairosColors.Pine,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = KairosColors.Dim,
            unselectedIconColor = KairosColors.Dim,
        ),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
