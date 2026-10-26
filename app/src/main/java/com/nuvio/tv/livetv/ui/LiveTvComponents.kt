package com.nuvio.tv.livetv.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvFavoritesGroupId
import com.nuvio.tv.livetv.LiveTvRecentGroupId
import com.nuvio.tv.livetv.LiveTvUiState

/** Generic D-pad focusable surface used for every button-like Live TV element. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    radius: Dp = 999.dp,
    containerColor: Color? = null,
    focusedScale: Float = 1.04f,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = liveTvColors()
    val base = containerColor ?: if (selected) colors.accent else colors.surfaceRaised
    val shape = RoundedCornerShape(radius)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = base,
            focusedContainerColor = if (selected) colors.accent else colors.focusBackground,
            pressedContainerColor = base
        ),
        border = ClickableSurfaceDefaults.border(
            border = liveTvIdleBorder(radius),
            focusedBorder = liveTvFocusBorder(radius)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        content = content
    )
}

/** Pulsing red "LIVE" pill. */
@Composable
internal fun LiveTvLiveBadge(
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val live = LiveTvLiveRed
    val transition = rememberInfiniteTransition(label = "live_badge")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "live_badge_pulse"
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(live.copy(alpha = 0.16f))
            .border(1.dp, live.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 1.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 6.dp else 7.dp)
                .drawBehind {
                    drawCircle(live.copy(alpha = 0.35f * pulse), radius = size.minDimension * 1.3f)
                    drawCircle(live.copy(alpha = 0.55f + 0.45f * pulse), radius = size.minDimension / 2f)
                }
        )
        Text(
            text = label,
            color = liveTvColors().textPrimary,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
internal fun LiveTvChannelLogo(
    channel: LiveTvChannel,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val colors = liveTvColors()
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) colors.accent else colors.gridLineStrong,
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = channel.monogram(),
            color = colors.textSecondary,
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight.Black
        )
        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
internal fun LiveTvProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    val colors = liveTvColors()
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = 0.12f))
            .drawBehind {
                drawRect(
                    brush = colors.accentBrush,
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                )
            }
    )
}

internal fun LiveTvChannel.monogram(): String =
    name.split(' ', '-', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "TV" }

/** Round icon button (header actions, time navigation). */
@Composable
internal fun LiveTvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val colors = liveTvColors()
    LiveTvFocusSurface(onClick = onClick, modifier = modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textPrimary,
            modifier = Modifier.align(Alignment.Center).size(22.dp)
        )
    }
}

/** Primary "Watch" button. */
@Composable
internal fun LiveTvWatchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = liveTvColors()
    LiveTvFocusSurface(onClick = onClick, modifier = modifier, selected = true) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = colors.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun LiveTvPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    fontSize: Float = 13f
) {
    val colors = liveTvColors()
    val fg = if (selected) colors.onAccent else colors.textPrimary
    LiveTvFocusSurface(onClick = onClick, modifier = modifier, selected = selected) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                color = fg,
                fontSize = fontSize.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal data class LiveTvChip(val id: String?, val label: String, val icon: ImageVector? = null)

@Composable
internal fun LiveTvChipRow(
    state: LiveTvUiState,
    onSelect: (String?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val all = stringResource(R.string.live_tv_chip_all)
    val favorites = stringResource(R.string.live_tv_chip_favorites)
    val recent = stringResource(R.string.live_tv_chip_recent)
    val chips = buildList {
        add(LiveTvChip(null, all))
        if (state.favoriteIds.isNotEmpty()) add(LiveTvChip(LiveTvFavoritesGroupId, favorites, Icons.Rounded.Star))
        if (state.recentIds.isNotEmpty()) add(LiveTvChip(LiveTvRecentGroupId, recent, Icons.Rounded.History))
        state.addonChips().forEach { add(LiveTvChip(it.id, it.name)) }
    }
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(chips, key = { it.id ?: "__all__" }) { chip ->
            LiveTvPillButton(
                label = chip.label,
                icon = chip.icon,
                selected = chip.id == state.selectedGroupId,
                onClick = { onSelect(chip.id) }
            )
        }
    }
}
