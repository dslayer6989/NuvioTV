package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvClock
import com.nuvio.tv.livetv.LiveTvGuide

/**
 * Channel list shown over the picture while watching Live TV. Opening a channel in it
 * tunes straight to that channel.
 */
@Composable
internal fun LiveTvPlayerOverlay(
    channels: List<LiveTvChannel>,
    guide: LiveTvGuide?,
    currentChannelId: String,
    onSelect: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = liveTvColors()
    val currentIndex = channels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val currentRequester = remember { FocusRequester() }
    val nowMs = remember { LiveTvClock.nowEpochMs() }
    LaunchedEffect(Unit) {
        runCatching { currentRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(380.dp)
                .background(colors.surface.copy(alpha = 0.96f))
                .padding(vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.live_tv_player_guide),
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    val program = guide?.programAt(channel.id, nowMs)
                    LiveTvFocusSurface(
                        onClick = { onSelect(channel) },
                        radius = 12.dp,
                        selected = false,
                        containerColor = if (channel.id == currentChannelId) colors.surfaceRaised else colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == currentIndex) Modifier.focusRequester(currentRequester) else Modifier)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiveTvChannelLogo(channel = channel, size = 40.dp)
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    text = channel.name,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = program?.title ?: stringResource(R.string.live_tv_no_guide_data),
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
