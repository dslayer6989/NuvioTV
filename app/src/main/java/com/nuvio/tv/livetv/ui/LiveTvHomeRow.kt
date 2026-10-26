package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvClock
import com.nuvio.tv.livetv.LiveTvProgram
import com.nuvio.tv.livetv.LiveTvTime
import kotlinx.coroutines.delay

private const val HomeRowMaxChannels = 12

/** What the "Open guide" card does; provided by the navigation host so Home doesn't need to know. */
val LocalLiveTvOpenGuide = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A "Live Now" row for the Home screen: favorite channels first, each showing what is on right now.
 * Draws nothing until a guide has loaded, so Home looks unchanged for people who don't use Live TV.
 */
@Composable
fun LiveTvHomeRow(
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = LocalLiveTvOpenGuide.current,
    horizontalPadding: Dp = 48.dp,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val guide = state.guide ?: return
    var nowMs by remember { mutableLongStateOf(LiveTvClock.nowEpochMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LiveTvTime.MINUTE_MS)
            nowMs = LiveTvClock.nowEpochMs()
        }
    }

    val channels = remember(guide, state.favoriteIds, nowMs / LiveTvTime.MINUTE_MS) {
        val favorites = state.favoriteIds.mapNotNull { guide.channel(it) }
        val rest = guide.channelsFor(null).filter { it !in favorites }
        (favorites + rest)
            .filter { guide.programAt(it.id, nowMs) != null }
            .take(HomeRowMaxChannels)
    }
    if (channels.isEmpty()) return

    val colors = liveTvColors()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.live_tv_home_row_title),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            LiveTvLiveBadge(label = stringResource(R.string.live_tv_live_badge), compact = true)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = horizontalPadding, vertical = 6.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                LiveTvHomeCard(
                    channel = channel,
                    program = guide.programAt(channel.id, nowMs),
                    nowMs = nowMs,
                    onClick = { viewModel.play(channel) }
                )
            }
            item(key = "open_guide") {
                LiveTvFocusSurface(
                    onClick = onOpenGuide,
                    radius = 14.dp,
                    containerColor = colors.surface,
                    modifier = Modifier.width(150.dp).height(112.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_tv_home_row_open),
                        color = colors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center).padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvHomeCard(
    channel: LiveTvChannel,
    program: LiveTvProgram?,
    nowMs: Long,
    onClick: () -> Unit
) {
    val colors = liveTvColors()
    LiveTvFocusSurface(
        onClick = onClick,
        radius = 14.dp,
        containerColor = colors.surface,
        modifier = Modifier.width(240.dp).height(112.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveTvChannelLogo(channel = channel, size = 36.dp)
                Text(
                    text = channel.name,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = program?.title ?: stringResource(R.string.live_tv_no_guide_data),
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            if (program != null) {
                LiveTvProgressBar(progress = program.progressAt(nowMs), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
