package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.accentBrush

/** Colors used by every Live TV screen, derived from the active app theme. */
@Immutable
internal data class LiveTvColors(
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val accentBrush: Brush,
    val surface: Color,
    val surfaceRaised: Color,
    val panel: Color,
    val gridLine: Color,
    val gridLineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pastCell: Color,
    val futureCell: Color,
    val airingCell: Color,
    val live: Color,
    val focusRing: Color,
    val focusBackground: Color
)

internal val LiveTvLiveRed = Color(0xFFFF3B6B)

@Composable
internal fun liveTvColors(): LiveTvColors {
    val c = NuvioTheme.colors
    val brush = NuvioTheme.palette.accentBrush()
    return remember(c, brush) {
        LiveTvColors(
            accent = c.Secondary,
            accentSoft = c.Secondary.copy(alpha = 0.16f),
            onAccent = c.OnSecondary,
            accentBrush = brush,
            surface = c.Surface,
            surfaceRaised = c.SurfaceVariant,
            panel = c.Panel,
            gridLine = c.BorderMuted,
            gridLineStrong = c.Border,
            textPrimary = c.TextPrimary,
            textSecondary = c.TextSecondary,
            textMuted = c.TextTertiary,
            pastCell = lerp(c.Background, c.BackgroundCard, 0.45f),
            futureCell = c.BackgroundCard,
            airingCell = lerp(c.BackgroundCard, c.Secondary, 0.16f),
            live = LiveTvLiveRed,
            focusRing = c.FocusRing,
            focusBackground = c.FocusBackground
        )
    }
}

internal fun liveTvShape(radius: Dp = 12.dp) = RoundedCornerShape(radius)

/** Themed focus ring border used on all focusable Live TV surfaces. */
@Composable
internal fun liveTvFocusBorder(radius: Dp = 12.dp): Border =
    Border(
        border = BorderStroke(3.dp, NuvioTheme.colors.FocusRing),
        shape = RoundedCornerShape(radius)
    )

@Composable
internal fun liveTvIdleBorder(radius: Dp = 12.dp, color: Color = NuvioTheme.colors.Border): Border =
    Border(
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(radius)
    )
