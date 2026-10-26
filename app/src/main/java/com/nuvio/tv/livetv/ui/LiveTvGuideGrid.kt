package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvClock
import com.nuvio.tv.livetv.LiveTvGuide
import com.nuvio.tv.livetv.LiveTvGuideWindow
import com.nuvio.tv.livetv.LiveTvProgram
import com.nuvio.tv.livetv.LiveTvTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Sizes for the TV guide. */
@Immutable
internal data class LiveTvGridMetrics(
    val channelColumnWidth: Dp = 300.dp,
    val rowHeight: Dp = 96.dp,
    val halfHourWidth: Dp = 260.dp,
    val headerHeight: Dp = 56.dp
)

/** Which channel row and moment in time the remote cursor is on. */
@Immutable
internal data class LiveTvGuideCursor(val row: Int, val timeMs: Long)

@Composable
internal fun LiveTvGuideGrid(
    guide: LiveTvGuide,
    channels: List<LiveTvChannel>,
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    listState: LazyListState,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float,
    cursor: LiveTvGuideCursor?,
    selectedProgramId: String?,
    favoriteIds: Set<String>,
    tuningChannelId: String?,
    contentPadding: PaddingValues,
    onChannelClick: (LiveTvChannel) -> Unit,
    onProgramClick: (rowIndex: Int, channel: LiveTvChannel, program: LiveTvProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LiveTvTimeHeader(
            window = window,
            nowMs = nowMs,
            metrics = metrics,
            scrollXPx = scrollXPx,
            visibleStartMs = visibleStartMs,
            visibleEndMs = visibleEndMs,
            pxPerMs = pxPerMs
        )
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                LiveTvGuideRow(
                    guide = guide,
                    channel = channel,
                    rowIndex = index,
                    window = window,
                    nowMs = nowMs,
                    metrics = metrics,
                    scrollXPx = scrollXPx,
                    visibleStartMs = visibleStartMs,
                    visibleEndMs = visibleEndMs,
                    pxPerMs = pxPerMs,
                    cursorTimeMs = cursor?.takeIf { it.row == index }?.timeMs,
                    rowHighlighted = cursor?.row == index,
                    selectedProgramId = selectedProgramId,
                    isFavorite = channel.id in favoriteIds,
                    isTuning = channel.id == tuningChannelId,
                    onChannelClick = { onChannelClick(channel) },
                    onProgramClick = { program -> onProgramClick(index, channel, program) }
                )
            }
        }
    }
}

@Composable
private fun LiveTvTimeHeader(
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float
) {
    val colors = liveTvColors()
    val density = LocalDensity.current
    val slotWidth = metrics.halfHourWidth
    val slots = remember(window, visibleStartMs, visibleEndMs) {
        window.slots.filter { it + LiveTvTime.HALF_HOUR_MS > visibleStartMs && it < visibleEndMs }
    }
    val currentSlot = LiveTvTime.floorToHalfHour(nowMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.headerHeight)
            .background(colors.panel)
            .drawBehind {
                drawLine(colors.gridLineStrong, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
    ) {
        Box(
            modifier = Modifier.width(metrics.channelColumnWidth).fillMaxHeight().padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = LiveTvClock.formatDay(nowMs),
                color = colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            slots.forEach { slotStart ->
                val isCurrent = slotStart == currentSlot
                val isDayStart = LiveTvClock.localDayStart(slotStart) == slotStart
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(((slotStart - window.startMs) * pxPerMs - scrollXPx()).roundToInt(), 0)
                        }
                        .width(slotWidth)
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (isDayStart) {
                            LiveTvClock.formatDay(slotStart) + "  " + LiveTvClock.formatTimeOfDay(slotStart)
                        } else {
                            LiveTvClock.formatTimeOfDay(slotStart)
                        },
                        color = if (isCurrent || isDayStart) colors.accent else colors.textMuted,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent || isDayStart) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            LiveTvNowMarker(
                xPx = { (nowMs - window.startMs) * pxPerMs - scrollXPx() },
                dotInHeader = true
            )
        }
    }
}

@Composable
private fun LiveTvGuideRow(
    guide: LiveTvGuide,
    channel: LiveTvChannel,
    rowIndex: Int,
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float,
    cursorTimeMs: Long?,
    rowHighlighted: Boolean,
    selectedProgramId: String?,
    isFavorite: Boolean,
    isTuning: Boolean,
    onChannelClick: () -> Unit,
    onProgramClick: (LiveTvProgram) -> Unit
) {
    val colors = liveTvColors()
    val programs = remember(guide, channel.id, visibleStartMs, visibleEndMs) {
        guide.programs(channel.id).filter { it.endMs > visibleStartMs && it.startMs < visibleEndMs }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .background(if (rowHighlighted) colors.accentSoft else Color.Transparent)
            .drawBehind {
                drawLine(colors.gridLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
    ) {
        LiveTvChannelCell(
            channel = channel,
            width = metrics.channelColumnWidth,
            highlighted = rowHighlighted,
            isFavorite = isFavorite,
            isTuning = isTuning,
            onClick = onChannelClick
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            if (programs.isEmpty()) {
                LiveTvNoGuideCell()
            } else {
                programs.forEach { program ->
                    val start = max(program.startMs, window.startMs)
                    val end = min(program.endMs, window.endMs)
                    if (end > start) {
                        LiveTvProgramCell(
                            program = program,
                            nowMs = nowMs,
                            xPx = (start - window.startMs) * pxPerMs,
                            widthPx = (end - start) * pxPerMs,
                            scrollXPx = scrollXPx,
                            focused = cursorTimeMs != null &&
                                cursorTimeMs >= program.startMs && cursorTimeMs < program.endMs,
                            selected = program.id == selectedProgramId,
                            onClick = { onProgramClick(program) }
                        )
                    }
                }
            }
            LiveTvNowMarker(
                xPx = { (nowMs - window.startMs) * pxPerMs - scrollXPx() },
                dotInHeader = false
            )
        }
    }
}

@Composable
private fun LiveTvChannelCell(
    channel: LiveTvChannel,
    width: Dp,
    highlighted: Boolean,
    isFavorite: Boolean,
    isTuning: Boolean,
    onClick: () -> Unit
) {
    val colors = liveTvColors()
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(colors.panel, colors.surface)))
            .pointerInput(channel.id) { detectTapGestures(onTap = { onClick() }) }
            .drawBehind {
                drawLine(
                    colors.gridLineStrong,
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    1.dp.toPx()
                )
                if (highlighted) {
                    drawRect(colors.accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                }
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.number > 0) {
            Text(
                text = channel.number.toString(),
                color = colors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            LiveTvChannelLogo(channel = channel, size = 46.dp, selected = highlighted)
            if (isTuning) Text(text = "…", color = colors.accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = channel.name,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = stringResource(R.string.live_tv_remove_favorite),
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LiveTvProgramCell(
    program: LiveTvProgram,
    nowMs: Long,
    xPx: Float,
    widthPx: Float,
    scrollXPx: () -> Float,
    focused: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = liveTvColors()
    val density = LocalDensity.current
    val airing = program.isAiringAt(nowMs)
    val past = program.endMs <= nowMs
    val widthDp = with(density) { widthPx.toDp() }
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        airing -> colors.airingCell
        past -> colors.pastCell
        else -> colors.futureCell
    }
    val borderModifier = when {
        focused || selected -> Modifier.border(3.dp, colors.focusRing, shape)
        airing -> Modifier.border(1.dp, colors.accent.copy(alpha = 0.35f), shape)
        else -> Modifier.border(1.dp, colors.gridLine, shape)
    }
    val maxShiftPx = with(density) { (widthDp - 90.dp).coerceAtLeast(0.dp).toPx() }
    val minVisiblePx = with(density) { 64.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - scrollXPx()).roundToInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .clip(shape)
            .background(if (focused || selected) colors.focusBackground else background)
            .then(borderModifier)
            .pointerInput(program.id) { detectTapGestures(onTap = { onClick() }) }
    ) {
        if (airing) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(colors.accent)
            )
        }
        Column(
            modifier = Modifier
                .offset {
                    val hidden = (scrollXPx() - xPx).coerceAtLeast(0f)
                    IntOffset(min(hidden, maxShiftPx).roundToInt(), 0)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = program.title,
                color = if (past) colors.textMuted else colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (widthPx > minVisiblePx * 2) {
                Text(
                    text = liveTvTimeRange(program),
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LiveTvNoGuideCell() {
    val colors = liveTvColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.pastCell)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(R.string.live_tv_no_guide_data),
            color = colors.textMuted,
            fontSize = 14.sp
        )
    }
}

/** Thin vertical "now" line, with an optional dot for the header. */
@Composable
private fun LiveTvNowMarker(xPx: () -> Float, dotInHeader: Boolean) {
    val colors = liveTvColors()
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx().roundToInt() - 6.dp.roundToPx(), 0) }
            .width(12.dp)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2f
                drawLine(
                    brush = Brush.verticalGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.4f))),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                if (dotInHeader) drawCircle(colors.accent, radius = 5.dp.toPx(), center = Offset(x, size.height - 6.dp.toPx()))
            }
    )
}
