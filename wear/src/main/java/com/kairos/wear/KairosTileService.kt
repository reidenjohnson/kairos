package com.kairos.wear

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kairos.data.Location
import com.kairos.data.WeatherRepository
import com.kairos.engine.Side
import com.kairos.engine.SpeciesScore
import com.kairos.engine.scoreAll
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * A Wear OS tile showing the best HUNT and best FISH score right now, scored by
 * the shared :engine. A Refresh button reloads it on demand (tiles can't scroll
 * and whole-tile taps are unreliable, so the button is the affordance); the tile
 * also self-refreshes about every 30 minutes. The full scrollable Hunt/Fish app
 * is a separate Wear activity (watch-expansion work).
 *
 * v1 forecasts for Sebago (Location.SEBAGO) — watch-side GPS is a follow-up.
 */
class KairosTileService : TileService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        executor.execute {
            try {
                val root = buildLayout(applicationContext)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(REFRESH_MILLIS)
                    .setTileTimeline(
                        TimelineBuilders.Timeline.Builder()
                            .addTimelineEntry(
                                TimelineBuilders.TimelineEntry.Builder()
                                    .setLayout(
                                        LayoutElementBuilders.Layout.Builder()
                                            .setRoot(root)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build()
                completer.set(tile)
            } catch (e: Exception) {
                completer.setException(e)
            }
        }
        "KairosTileService.onTileRequest"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
            )
            "KairosTileService.onTileResourcesRequest"
        }

    private fun buildLayout(context: Context): LayoutElement {
        val forecast = WeatherRepository.fetch(Location.SEBAGO)
        val c = forecast.conditions
        val bestHunt = scoreAll(c, Side.HUNT).first()
        val bestFish = scoreAll(c, Side.FISH).first()
        val updated = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))

        val column = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(context, "KAIROS")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(ACCENT))
                    .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(16f)).build())
            .addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(miniScore(context, "🦌", bestHunt))
                    .addContent(Spacer.Builder().setWidth(dp(26f)).build())
                    .addContent(miniScore(context, "🎣", bestFish))
                    .build(),
            )
            .addContent(Spacer.Builder().setHeight(dp(16f)).build())
            .addContent(refreshButton(context))
            .addContent(Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder(context, "✓")
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(CHECK))
                            .build(),
                    )
                    .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                    .addContent(
                        Text.Builder(context, "Updated $updated")
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(STAMP))
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    /** A score with its species: "<icon> Name" label over the big number. */
    private fun miniScore(context: Context, icon: String, s: SpeciesScore): LayoutElement =
        Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(context, "$icon ${s.species.name}")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(DIM))
                    .setMaxLines(1)
                    .build(),
            )
            .addContent(
                Text.Builder(context, "${s.percent}")
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setColor(argb(WHITE))
                    .build(),
            )
            .build()

    /** A real tappable Refresh control — reloads the tile => re-fetches weather. */
    private fun refreshButton(context: Context): LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("kairos-refresh")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        return Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(BUTTON))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18f)).build())
                            .build(),
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(13f))
                            .setEnd(dp(13f))
                            .setTop(dp(5f))
                            .setBottom(dp(5f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Text.Builder(context, "↻")
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(WHITE))
                    .build(),
            )
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val REFRESH_MILLIS = 30L * 60L * 1000L
        const val ACCENT = 0xFF8FC7B3.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DIM = 0xFFB0B0B0.toInt()
        const val FAINT = 0xFF80908A.toInt()
        const val BUTTON = 0xFF25574A.toInt()
        const val CHECK = 0xFF4FBE73.toInt()
        const val STAMP = 0xFF5E736B.toInt()
    }
}
