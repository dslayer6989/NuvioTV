package com.nuvio.tv.livetv.ui

import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvClock
import com.nuvio.tv.livetv.LiveTvFavoritesGroupId
import com.nuvio.tv.livetv.LiveTvGuide
import com.nuvio.tv.livetv.LiveTvOnNowSort
import com.nuvio.tv.livetv.LiveTvProgram
import com.nuvio.tv.livetv.LiveTvRecentGroupId
import com.nuvio.tv.livetv.LiveTvTime
import com.nuvio.tv.livetv.LiveTvUiState
import com.nuvio.tv.livetv.LiveTvViewMode
import com.nuvio.tv.livetv.liveTvGuideWindow
import com.nuvio.tv.livetv.normalizeManifestUrl
import com.nuvio.tv.livetv.onNow
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.collection.NuvioButton
import com.nuvio.tv.ui.screens.collection.NuvioTextField
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlin.math.max
import kotlin.math.roundToInt

private const val PageJumpMs = 2 * LiveTvTime.HOUR_MS
private const val NowLeadFraction = 0.12f

/** The Live TV page: a header, group chips, and either the guide grid or the On Now cards. */
@Composable
fun LiveTvScreen(viewModel: LiveTvViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tuningChannelId by viewModel.tuningChannelId.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(LiveTvClock.nowEpochMs()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = LiveTvClock.nowEpochMs()
            viewModel.refreshIfStale(nowMs)
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val horizontalPadding = 28.dp
    val guide = state.guide

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(top = 20.dp)
    ) {
        LiveTvHeader(
            state = state,
            horizontalPadding = horizontalPadding,
            onViewMode = viewModel::setViewMode,
            onLastChannel = { state.recentIds.firstOrNull()?.let(viewModel::playId) },
            onRefresh = { viewModel.refresh(true) },
            onSettings = { showSettings = true }
        )
        when {
            !state.isConfigured -> LiveTvSetupPanel(
                initialUrl = state.manifestUrl.orEmpty(),
                onSave = viewModel::setManifestUrl
            )
            guide == null && state.errorMessage != null -> LiveTvMessagePanel(
                title = stringResource(R.string.live_tv_error_title),
                message = state.errorMessage,
                primaryLabel = stringResource(R.string.live_tv_retry),
                onPrimary = { viewModel.refresh(true) },
                secondaryLabel = stringResource(R.string.live_tv_settings),
                onSecondary = { showSettings = true }
            )
            guide == null -> LiveTvLoadingPanel()
            else -> {
                Spacer(Modifier.height(10.dp))
                LiveTvChipRow(
                    state = state,
                    onSelect = viewModel::selectGroup,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                when (state.viewMode) {
                    LiveTvViewMode.Guide -> LiveTvGuideContent(
                        viewModel = viewModel,
                        state = state,
                        guide = guide,
                        nowMs = nowMs,
                        tuningChannelId = tuningChannelId,
                        horizontalPadding = horizontalPadding
                    )
                    LiveTvViewMode.OnNow -> LiveTvOnNowContent(
                        viewModel = viewModel,
                        state = state,
                        guide = guide,
                        nowMs = nowMs,
                        tuningChannelId = tuningChannelId,
                        horizontalPadding = horizontalPadding
                    )
                }
            }
        }
    }

    if (showSettings) {
        LiveTvSettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { url ->
                showSettings = false
                if (normalizeManifestUrl(url) != normalizeManifestUrl(state.manifestUrl.orEmpty())) {
                    viewModel.setManifestUrl(url)
                }
            }
        )
    }
}

@Composable
private fun LiveTvHeader(
    state: LiveTvUiState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onViewMode: (LiveTvViewMode) -> Unit,
    onLastChannel: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    val colors = liveTvColors()
    val guide = state.guide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.live_tv_title).uppercase(),
                    style = TextStyle(
                        brush = colors.accentBrush,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                )
                LiveTvLiveBadge(label = stringResource(R.string.live_tv_live_badge))
                if (state.isRefreshing) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                }
            }
            if (guide != null) {
                Text(
                    text = guide.addonName + " · " + stringResource(R.string.live_tv_channel_count, guide.lineupIds.size),
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guide != null) {
                LiveTvViewToggle(mode = state.viewMode, onSelect = onViewMode)
                Spacer(Modifier.width(8.dp))
                if (state.recentIds.isNotEmpty()) {
                    LiveTvIconButton(
                        icon = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.live_tv_last_channel),
                        onClick = onLastChannel
                    )
                }
                LiveTvIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.live_tv_refresh),
                    onClick = onRefresh
                )
            }
            LiveTvIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.live_tv_settings),
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun LiveTvViewToggle(mode: LiveTvViewMode, onSelect: (LiveTvViewMode) -> Unit) {
    val colors = liveTvColors()
    val shape = liveTvShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.gridLine, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LiveTvPillButton(
            label = stringResource(R.string.live_tv_view_guide),
            onClick = { onSelect(LiveTvViewMode.Guide) },
            selected = mode == LiveTvViewMode.Guide,
            icon = Icons.Rounded.Tv
        )
        LiveTvPillButton(
            label = stringResource(R.string.live_tv_view_on_now),
            onClick = { onSelect(LiveTvViewMode.OnNow) },
            selected = mode == LiveTvViewMode.OnNow,
            icon = Icons.Rounded.GridView
        )
    }
}

@Composable
private fun LiveTvTimeNav(onEarlier: () -> Unit, onNow: () -> Unit, onLater: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LiveTvIconButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.live_tv_earlier),
            onClick = onEarlier
        )
        LiveTvPillButton(label = stringResource(R.string.live_tv_now), onClick = onNow)
        LiveTvIconButton(
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.live_tv_later),
            onClick = onLater
        )
    }
}

@Composable
private fun LiveTvDetailsHeader(
    channel: LiveTvChannel,
    program: LiveTvProgram?,
    nowMs: Long,
    isFavorite: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onWatch: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val colors = liveTvColors()
    val panel = NuvioTheme.colors.Panel
    val shape = RoundedCornerShape(18.dp)
    val airing = program?.isAiringAt(nowMs) == true
    Row(
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .height(136.dp)
            .clip(shape)
            .background(panel)
            .border(1.dp, colors.gridLine, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.34f)
                .widthIn(max = 260.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (program?.thumbnail != null) {
                AsyncImage(
                    model = program.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LiveTvChannelLogo(channel = channel, size = 72.dp)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, panel)))
            )
        }
        Column(
            modifier = Modifier
                .weight(0.66f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = (if (channel.number > 0) "${channel.number}  " else "") + channel.name,
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (airing) {
                    LiveTvLiveBadge(label = stringResource(R.string.live_tv_live_badge), compact = true)
                }
            }
            if (program != null) {
                Text(
                    text = program.title,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val timeText = if (airing || program.endMs <= nowMs) {
                    liveTvTimeRange(program)
                } else {
                    stringResource(R.string.live_tv_starts_at, LiveTvClock.formatTimeOfDay(program.startMs))
                }
                val meta = buildList {
                    add(timeText)
                    addAll(program.genres.filter { it != "Series" }.take(2))
                    program.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("  ·  ")
                Text(text = meta, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (airing) {
                    LiveTvProgressBar(
                        progress = program.progressAt(nowMs),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
                program.description?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = colors.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(
                    text = stringResource(R.string.live_tv_no_guide_data),
                    color = colors.textMuted,
                    fontSize = 14.sp
                )
            }
        }
        Row(
            modifier = Modifier.padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LiveTvWatchButton(
                label = stringResource(if (airing) R.string.live_tv_watch_now else R.string.live_tv_watch),
                onClick = onWatch
            )
            LiveTvIconButton(
                icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.live_tv_remove_favorite else R.string.live_tv_add_favorite
                ),
                onClick = onFavoriteToggle
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LiveTvGuideContent(
    viewModel: LiveTvViewModel,
    state: LiveTvUiState,
    guide: LiveTvGuide,
    nowMs: Long,
    tuningChannelId: String?,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val density = LocalDensity.current
    val metrics = remember { LiveTvGridMetrics() }
    val channels = remember(state) { state.visibleChannels() }
    val favoriteSet = remember(state.favoriteIds) { state.favoriteIds.toSet() }
    val halfHourBucket = nowMs / LiveTvTime.HALF_HOUR_MS
    val window = remember(guide, halfHourBucket) { liveTvGuideWindow(guide, nowMs) }
    val slotPx = with(density) { metrics.halfHourWidth.toPx() }
    val channelColumnPx = with(density) { metrics.channelColumnWidth.toPx() }
    val pxPerMs = slotPx / LiveTvTime.HALF_HOUR_MS
    val totalPx = (window.endMs - window.startMs) * pxPerMs

    var scrollX by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var userScrolled by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<LiveTvGuideCursor?>(null) }
    var selected by remember { mutableStateOf<Pair<LiveTvChannel, LiveTvProgram>?>(null) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val maxScroll = max(0f, totalPx - viewportPx)
    val horizontalState = rememberScrollableState { delta ->
        val next = (scrollX - delta).coerceIn(0f, maxScroll)
        val consumed = scrollX - next
        scrollX = next
        userScrolled = true
        consumed
    }

    suspend fun scrollToTime(timeMs: Long, animated: Boolean) {
        val target = ((timeMs - window.startMs) * pxPerMs - viewportPx * NowLeadFraction)
            .coerceIn(0f, max(0f, totalPx - viewportPx))
        if (animated) {
            animate(scrollX, target, animationSpec = tween(320)) { value, _ -> scrollX = value }
        } else {
            scrollX = target
        }
    }

    LaunchedEffect(window.startMs, viewportPx) {
        if (!userScrolled && viewportPx > 0f) scrollToTime(nowMs, animated = false)
    }
    LaunchedEffect(Unit) {
        viewModel.jumpToNow.collect {
            userScrolled = false
            cursor = null
            selected = null
            scrollToTime(LiveTvClock.nowEpochMs(), animated = true)
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val scrollBucket by remember(slotPx) { derivedStateOf { (scrollX / slotPx).toInt() } }
    val viewportSlots = (viewportPx / slotPx).toInt() + 2
    val visibleStartMs = window.startMs + (scrollBucket - 1) * LiveTvTime.HALF_HOUR_MS
    val visibleEndMs = window.startMs + (scrollBucket + viewportSlots + 1) * LiveTvTime.HALF_HOUR_MS

    val cursorChannel = cursor?.let { channels.getOrNull(it.row) }
    val detailChannel = selected?.first ?: cursorChannel ?: channels.firstOrNull()
    val detailProgram = selected?.second
        ?: cursor?.let { c -> cursorChannel?.let { guide.programAt(it.id, c.timeMs) } }
        ?: detailChannel?.let { guide.programAt(it.id, nowMs) }

    fun moveCursor(row: Int, timeMs: Long) {
        if (channels.isEmpty()) return
        val r = row.coerceIn(0, channels.lastIndex)
        val t = timeMs.coerceIn(window.startMs, window.endMs - 1)
        cursor = LiveTvGuideCursor(r, t)
        selected = null
        val xPx = (t - window.startMs) * pxPerMs
        val leftEdge = scrollX + 5 * LiveTvTime.MINUTE_MS * pxPerMs
        val rightEdge = scrollX + viewportPx - 20 * LiveTvTime.MINUTE_MS * pxPerMs
        if (xPx < leftEdge) {
            scrollX = (xPx - 5 * LiveTvTime.MINUTE_MS * pxPerMs).coerceIn(0f, maxScroll)
            userScrolled = true
        } else if (xPx > rightEdge) {
            scrollX = (xPx - viewportPx + 20 * LiveTvTime.MINUTE_MS * pxPerMs).coerceIn(0f, maxScroll)
            userScrolled = true
        }
    }

    // Keep the cursor row comfortably in view when it moves up or down.
    LaunchedEffect(cursor?.row) {
        val row = cursor?.row ?: return@LaunchedEffect
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (row <= first || row >= last) {
            listState.animateScrollToItem((row - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused && cursor == null && channels.isNotEmpty()) cursor = LiveTvGuideCursor(0, nowMs) }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || channels.isEmpty()) return@onPreviewKeyEvent false
                val current = cursor ?: LiveTvGuideCursor(0, nowMs)
                val channel = channels.getOrNull(current.row) ?: return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (current.row < channels.lastIndex) moveCursor(current.row + 1, current.timeMs)
                        true
                    }
                    Key.DirectionUp -> {
                        if (current.row == 0) {
                            cursor = null
                            false
                        } else {
                            moveCursor(current.row - 1, current.timeMs)
                            true
                        }
                    }
                    Key.DirectionRight -> {
                        val next = guide.nextProgram(channel.id, current.timeMs)
                        moveCursor(current.row, next?.startMs ?: (current.timeMs + LiveTvTime.HALF_HOUR_MS))
                        true
                    }
                    Key.DirectionLeft -> {
                        val here = guide.programAt(channel.id, current.timeMs)
                        val prev = here?.let { h -> guide.programs(channel.id).lastOrNull { it.endMs <= h.startMs } }
                        moveCursor(current.row, prev?.startMs ?: (current.timeMs - LiveTvTime.HALF_HOUR_MS))
                        true
                    }
                    Key.PageDown -> { moveCursor(current.row, current.timeMs + PageJumpMs); true }
                    Key.PageUp -> { moveCursor(current.row, current.timeMs - PageJumpMs); true }
                    Key.MoveHome -> {
                        userScrolled = false
                        moveCursor(current.row, nowMs)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { viewModel.play(channel); true }
                    Key.F, Key.Bookmark, Key.Menu -> { viewModel.toggleFavorite(channel.id); true }
                    else -> false
                }
            }
    ) {
        if (detailChannel != null) {
            LiveTvDetailsHeader(
                channel = detailChannel,
                program = detailProgram,
                nowMs = nowMs,
                isFavorite = detailChannel.id in favoriteSet,
                horizontalPadding = horizontalPadding,
                onWatch = { viewModel.play(detailChannel) },
                onFavoriteToggle = { viewModel.toggleFavorite(detailChannel.id) }
            )
        }
        Spacer(Modifier.height(10.dp))
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LiveTvTimeNav(
            horizontalPadding = horizontalPadding,
            onEarlier = {
                userScrolled = true
                scope.launchScroll { scrollToTime(visibleTimeAtLeft(window.startMs, scrollX, pxPerMs, viewportPx) - PageJumpMs, true) }
            },
            onNow = {
                userScrolled = false
                scope.launchScroll { scrollToTime(nowMs, true) }
            },
            onLater = {
                userScrolled = true
                scope.launchScroll { scrollToTime(visibleTimeAtLeft(window.startMs, scrollX, pxPerMs, viewportPx) + PageJumpMs, true) }
            }
        )
        if (channels.isEmpty()) {
            LiveTvEmptyGroup(isFavorites = state.selectedGroupId == LiveTvFavoritesGroupId)
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .onSizeChanged { viewportPx = (it.width - channelColumnPx).coerceAtLeast(0f) }
            ) {
                val shape = RoundedCornerShape(14.dp)
                LiveTvGuideGrid(
                    guide = guide,
                    channels = channels,
                    window = window,
                    nowMs = nowMs,
                    metrics = metrics,
                    listState = listState,
                    scrollXPx = { scrollX },
                    visibleStartMs = visibleStartMs,
                    visibleEndMs = visibleEndMs,
                    pxPerMs = pxPerMs,
                    cursor = cursor,
                    selectedProgramId = selected?.second?.id,
                    favoriteIds = favoriteSet,
                    tuningChannelId = tuningChannelId,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    onChannelClick = { channel -> viewModel.play(channel) },
                    onProgramClick = { row, channel, program ->
                        runCatching { focusRequester.requestFocus() }
                        cursor = LiveTvGuideCursor(row, program.startMs.coerceAtLeast(window.startMs))
                        if (program.isAiringAt(nowMs)) {
                            selected = null
                            viewModel.play(channel)
                        } else {
                            selected = channel to program
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .border(1.dp, liveTvColors().gridLine, shape)
                        .scrollable(horizontalState, Orientation.Horizontal)
                )
            }
        }
    }
}

private fun visibleTimeAtLeft(windowStartMs: Long, scrollX: Float, pxPerMs: Float, viewportPx: Float): Long =
    windowStartMs + ((scrollX + viewportPx * NowLeadFraction) / pxPerMs).toLong()

private fun kotlinx.coroutines.CoroutineScope.launchScroll(block: suspend () -> Unit) {
    launch { block() }
}

@Composable
private fun LiveTvOnNowContent(
    viewModel: LiveTvViewModel,
    state: LiveTvUiState,
    guide: LiveTvGuide,
    nowMs: Long,
    tuningChannelId: String?,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    val channels = remember(state) { state.visibleChannels() }
    val entries = remember(channels, state.onNowSort, nowMs / LiveTvTime.MINUTE_MS) {
        guide.onNow(channels, nowMs, state.onNowSort)
    }
    val favoriteSet = remember(state.favoriteIds) { state.favoriteIds.toSet() }
    val recents = remember(state.recentIds, guide) { state.recentIds.mapNotNull { guide.channel(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (recents.isNotEmpty() && state.selectedGroupId != LiveTvRecentGroupId) {
            LiveTvRecentStrip(recents = recents, horizontalPadding = horizontalPadding, onPlay = viewModel::play)
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiveTvPillButton(
                label = stringResource(R.string.live_tv_sort_channel),
                onClick = { viewModel.setOnNowSort(LiveTvOnNowSort.Channel) },
                selected = state.onNowSort == LiveTvOnNowSort.Channel
            )
            LiveTvPillButton(
                label = stringResource(R.string.live_tv_sort_ending_soon),
                onClick = { viewModel.setOnNowSort(LiveTvOnNowSort.EndingSoon) },
                selected = state.onNowSort == LiveTvOnNowSort.EndingSoon
            )
            LiveTvPillButton(
                label = stringResource(R.string.live_tv_sort_just_started),
                onClick = { viewModel.setOnNowSort(LiveTvOnNowSort.JustStarted) },
                selected = state.onNowSort == LiveTvOnNowSort.JustStarted
            )
        }
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            LiveTvEmptyGroup(isFavorites = state.selectedGroupId == LiveTvFavoritesGroupId)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = ((maxWidth - horizontalPadding * 2) / 300.dp).toInt().coerceIn(1, 6)
                LiveTvOnNowGrid(
                    entries = entries,
                    nowMs = nowMs,
                    favoriteIds = favoriteSet,
                    tuningChannelId = tuningChannelId,
                    columns = columns,
                    contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, bottom = 24.dp),
                    onPlay = viewModel::play,
                    onFavoriteToggle = { viewModel.toggleFavorite(it.id) }
                )
            }
        }
    }
}

@Composable
private fun LiveTvRecentStrip(
    recents: List<LiveTvChannel>,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onPlay: (LiveTvChannel) -> Unit
) {
    val colors = liveTvColors()
    Column(modifier = Modifier.padding(horizontal = horizontalPadding)) {
        Text(
            text = stringResource(R.string.live_tv_recently_watched).uppercase(),
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            recents.take(10).forEach { channel ->
                LiveTvFocusSurface(
                    onClick = { onPlay(channel) },
                    radius = 12.dp
                ) {
                    Box(modifier = Modifier.padding(4.dp)) {
                        LiveTvChannelLogo(channel = channel, size = 52.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvEmptyGroup(isFavorites: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (isFavorites) R.string.live_tv_empty_favorites else R.string.live_tv_empty_group),
            color = liveTvColors().textMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LiveTvLoadingPanel() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(14.dp))
        Text(text = stringResource(R.string.live_tv_loading), color = liveTvColors().textMuted, fontSize = 14.sp)
    }
}

@Composable
private fun LiveTvMessagePanel(
    title: String,
    message: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit
) {
    val colors = liveTvColors()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
            Text(text = title, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (!message.isNullOrBlank()) {
                Text(text = message, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTvWatchButton(label = primaryLabel, onClick = onPrimary)
                LiveTvPillButton(label = secondaryLabel, onClick = onSecondary)
            }
        }
    }
}

@Composable
private fun LiveTvSetupPanel(initialUrl: String, onSave: (String) -> Unit) {
    val colors = liveTvColors()
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val shape = RoundedCornerShape(22.dp)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(shape)
                .background(NuvioTheme.colors.Panel)
                .border(1.dp, colors.accent.copy(alpha = 0.5f), shape)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = colors.accent, modifier = Modifier.size(44.dp))
            Text(
                text = stringResource(R.string.live_tv_setup_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(R.string.live_tv_setup_message),
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            NuvioTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.live_tv_manifest_placeholder)
            )
            NuvioButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.live_tv_save))
            }
        }
    }
}

@Composable
private fun LiveTvSettingsDialog(
    state: LiveTvUiState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = liveTvColors()
    var url by remember { mutableStateOf(state.manifestUrl.orEmpty()) }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.live_tv_settings),
        subtitle = stringResource(R.string.live_tv_setup_message)
    ) {
        NuvioTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.live_tv_manifest_placeholder)
        )
        val guide = state.guide
        if (guide != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.live_tv_settings_guide_status, guide.addonName, guide.lineupIds.size),
                color = colors.textMuted,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioButton(onClick = onDismiss) { Text(stringResource(R.string.live_tv_cancel)) }
            NuvioButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.live_tv_save))
            }
        }
    }
}
