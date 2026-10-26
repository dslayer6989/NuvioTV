package com.nuvio.tv.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvClock
import com.nuvio.tv.livetv.LiveTvOnNowEntry
import com.nuvio.tv.livetv.LiveTvProgram
import com.nuvio.tv.livetv.LiveTvTime

/** "6:00 PM – 7:00 PM" for a program. */
internal fun liveTvTimeRange(program: LiveTvProgram): String =
    LiveTvClock.formatTimeOfDay(program.startMs) + " – " + LiveTvClock.formatTimeOfDay(program.endMs)

@Composable
private fun remainingLabel(remainingMs: Long): String {
    val totalMinutes = (remainingMs.coerceAtLeast(0L) / LiveTvTime.MINUTE_MS).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.live_tv_hours_minutes_short, hours, minutes)
    } else {
        stringResource(R.string.live_tv_minutes_short, minutes)
    }
}

@Composable
internal fun LiveTvOnNowGrid(
    entries: List<LiveTvOnNowEntry>,
    nowMs: Long,
    favoriteIds: Set<String>,
    tuningChannelId: String?,
    columns: Int,
    contentPadding: PaddingValues,
    onPlay: (LiveTvChannel) -> Unit,
    onFavoriteToggle: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(entries, key = { it.channel.id }) { entry ->
            LiveTvOnNowCard(
                entry = entry,
                nowMs = nowMs,
                isFavorite = entry.channel.id in favoriteIds,
                isTuning = entry.channel.id == tuningChannelId,
                onPlay = { onPlay(entry.channel) },
                onFavoriteToggle = { onFavoriteToggle(entry.channel) }
            )
        }
    }
}

@Composable
private fun LiveTvOnNowCard(
    entry: LiveTvOnNowEntry,
    nowMs: Long,
    isFavorite: Boolean,
    isTuning: Boolean,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val colors = liveTvColors()
    val current = entry.current
    LiveTvFocusSurface(
        onClick = onPlay,
        onLongClick = onFavoriteToggle,
        radius = 16.dp,
        containerColor = colors.surface,
        focusedScale = 1.03f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveTvChannelLogo(channel = entry.channel, size = 48.dp)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = entry.channel.name,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (current != null) {
                        Spacer(Modifier.height(4.dp))
                        LiveTvLiveBadge(label = stringResource(R.string.live_tv_live_badge), compact = true)
                    }
                }
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = stringResource(R.string.live_tv_remove_favorite),
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (current != null) {
                Text(
                    text = current.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                LiveTvProgressBar(
                    progress = current.progressAt(nowMs),
                    modifier = Modifier.fillMaxWidth(),
                    height = 4.dp
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = liveTvTimeRange(current),
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            R.string.live_tv_ends_in,
                            remainingLabel(current.endMs - nowMs)
                        ),
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                val next = entry.next
                if (next != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.live_tv_up_next) + " · " + next.title,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.live_tv_no_guide_data),
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
            if (isTuning) {
                Spacer(Modifier.height(6.dp))
                Text(text = "…", color = colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
