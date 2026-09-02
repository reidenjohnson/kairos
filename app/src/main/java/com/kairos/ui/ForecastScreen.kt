package com.kairos.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kairos.data.Forecast
import com.kairos.data.ForecastCache
import com.kairos.data.Location
import com.kairos.data.LocationProvider
import com.kairos.data.WeatherRepository
import com.kairos.engine.Conditions
import com.kairos.engine.Rating
import com.kairos.engine.Side
import com.kairos.engine.SpeciesScore
import com.kairos.engine.scoreAll
import kotlin.math.roundToInt

/** UI state for the single forecast screen. */
sealed interface UiState {
    data object Loading : UiState

    /** A shown forecast. [live] false means it came from cache (offline). */
    data class Ready(val forecast: Forecast, val live: Boolean, val savedAtMillis: Long) : UiState

    data class Error(val message: String) : UiState
}

/** App entry composable: owns state, resolves location, loads + caches the forecast. */
@Composable
fun ForecastApp() {
    KairosTheme {
        val context = LocalContext.current
        var state by remember { mutableStateOf<UiState>(UiState.Loading) }
        var refreshing by remember { mutableStateOf(false) }
        var reloadKey by remember { mutableIntStateOf(0) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { reloadKey++ } // reload once the user answers, granted or not

        // Ask for location once, up front.
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
            // Keep showing current data while refreshing; full-screen spinner only on first load.
            if (state is UiState.Ready) refreshing = true else state = UiState.Loading
            val place = LocationProvider.current(context) ?: Location.SEBAGO
            state = try {
                val forecast = withContext(Dispatchers.IO) { WeatherRepository.fetch(place) }
                ForecastCache.save(context, forecast)
                UiState.Ready(forecast, live = true, savedAtMillis = System.currentTimeMillis())
            } catch (e: Exception) {
                val cached = ForecastCache.load(context)
                if (cached != null) {
                    UiState.Ready(cached.forecast, live = false, savedAtMillis = cached.savedAtMillis)
                } else {
                    UiState.Error(e.message ?: "Couldn't reach the weather service.")
                }
            }
            refreshing = false
        }

        ForecastScreen(state = state, refreshing = refreshing, onRefresh = { reloadKey++ })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastScreen(state: UiState, refreshing: Boolean, onRefresh: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kairos") },
                actions = { TextButton(onClick = onRefresh) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Error -> ErrorView(state.message, onRefresh)
                is UiState.Ready -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ForecastList(state)
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ForecastList(ready: UiState.Ready) {
    val forecast = ready.forecast
    val c = forecast.conditions
    val hunt = remember(forecast) { scoreAll(c, Side.HUNT) }
    val fish = remember(forecast) { scoreAll(c, Side.FISH) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        if (!ready.live) item { OfflineBanner(ready.savedAtMillis) }
        item { SummaryCard(forecast, ready.savedAtMillis, ready.live) }
        item { SectionHeader("HUNT") }
        items(hunt) { SpeciesRow(it, c) }
        item { SectionHeader("FISH") }
        items(fish) { SpeciesRow(it, c) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OfflineBanner(savedAtMillis: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            "Offline — showing the last forecast (${ageText(savedAtMillis)}). Pull Refresh when you have signal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SummaryCard(f: Forecast, savedAtMillis: Long, live: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                f.placeLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${f.dateLabel} · ${if (live) "Updated" else "Cached"} ${timeText(savedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Air ${f.airF.roundToInt()}°F · water ~${f.waterF.roundToInt()}°F · " +
                    "wind ${f.windMph.roundToInt()} mph · ${f.cloudPct.roundToInt()}% cloud",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Pressure ${"%.2f".format(f.pressureInHg)} inHg (${f.trendWord}) · " +
                    "front −${f.tempDropNext24hF.roundToInt()}°F/24h · moon ${f.moonName}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SpeciesRow(row: SpeciesScore, c: Conditions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.species.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${row.percent}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ratingColor(row.rating),
                )
                Spacer(Modifier.width(8.dp))
                RatingChip(row.rating)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { row.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = ratingColor(row.rating),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                whyFor(row.species, c),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RatingChip(rating: Rating) {
    val label = when (rating) {
        Rating.PRIME -> "Prime"
        Rating.GOOD -> "Good"
        Rating.FAIR -> "Fair"
        Rating.SLOW -> "Slow"
    }
    Box(
        modifier = Modifier
            .background(ratingColor(rating).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ratingColor(rating))
    }
}

private fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.PRIME -> Color(0xFF2E7D32)
    Rating.GOOD -> Color(0xFF66A020)
    Rating.FAIR -> Color(0xFFC28A00)
    Rating.SLOW -> Color(0xFF9E9E9E)
}

private fun ageText(savedAtMillis: Long): String {
    val mins = ((System.currentTimeMillis() - savedAtMillis) / 60_000L).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> "${mins / (60 * 24)} d ago"
    }
}

private fun timeText(savedAtMillis: Long): String {
    val zoned = java.time.Instant.ofEpochMilli(savedAtMillis)
        .atZone(java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(zoned)
}
