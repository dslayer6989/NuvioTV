package com.nuvio.tv.livetv.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvLauncher
import com.nuvio.tv.livetv.LiveTvPlayRequest
import com.nuvio.tv.livetv.LiveTvPlayerSession
import com.nuvio.tv.livetv.LiveTvRepository
import com.nuvio.tv.livetv.LiveTvSessionState
import com.nuvio.tv.livetv.LiveTvTuneOutcome
import com.nuvio.tv.livetv.LiveTvTuner
import com.nuvio.tv.livetv.LiveTvUiState
import com.nuvio.tv.ui.screens.player.PlayerLiveTvControls
import kotlinx.coroutines.flow.collectLatest
import com.nuvio.tv.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Gives the app shell (navigation) and the player access to the Live TV singletons:
 * screens ask to play a channel, the shell tunes it and opens the player, and the player
 * asks for channel changes and backup sources.
 */
@HiltViewModel
class LiveTvShellViewModel @Inject constructor(
    private val launcher: LiveTvLauncher,
    private val tuner: LiveTvTuner,
    private val session: LiveTvPlayerSession,
    private val repository: LiveTvRepository
) : ViewModel() {
    val requests: SharedFlow<LiveTvPlayRequest> = launcher.requests
    val sessionState: StateFlow<LiveTvSessionState?> = session.state
    val uiState: StateFlow<LiveTvUiState> = repository.uiState

    /** Resolves the channel's streams and, when ready, starts the player session. */
    suspend fun tune(request: LiveTvPlayRequest): LiveTvTuneOutcome {
        launcher.setTuning(request.channelId)
        try {
            val outcome = tuner.tune(request)
            if (outcome is LiveTvTuneOutcome.Ready) session.start(outcome.result)
            return outcome
        } finally {
            launcher.setTuning(null)
        }
    }

    fun requestChannel(channelId: String, groupId: String?) = launcher.play(channelId, groupId)

    /** The channels channel up/down moves through: the chip the user was browsing. */
    fun channelsInSession(state: LiveTvSessionState): List<LiveTvChannel> =
        repository.uiState.value.copy(selectedGroupId = state.groupId).visibleChannels()

    fun advanceToBackup(): Stream? = session.advanceToBackup()
    fun selectStream(index: Int): Stream? = session.selectStream(index)
    fun rememberCurrentAsWorking() = session.rememberCurrentAsWorking()
    fun clearSessionIfGeneration(generation: Long) = session.clearIfGeneration(generation)
}

/** The player route for one source of a tuned channel. */
internal fun liveTvPlayerRoute(state: LiveTvSessionState, stream: Stream): String? {
    val url = stream.getStreamUrl() ?: return null
    val channel = state.channel
    return Screen.Player.createRoute(
        streamUrl = url,
        title = channel.name,
        streamName = stream.name,
        headers = stream.behaviorHints?.proxyHeaders?.request?.takeIf { it.isNotEmpty() },
        contentId = channel.id,
        // The player treats the Stremio type "channel" as live; "tv" would count as a series.
        contentType = "channel",
        contentName = channel.name,
        poster = channel.logo,
        logo = channel.logo,
        addonName = stream.addonName,
        addonLogo = stream.addonLogo,
        streamDescription = stream.description,
        audioDelayMs = stream.behaviorHints?.audioDelayMs
    )
}

/**
 * Listens for "play this channel" requests from any screen, tunes the channel, and opens the
 * player. [openPlayer] receives the route; it should replace the current player if one is open.
 */
@Composable
fun LiveTvPlayRequestHandler(
    shell: LiveTvShellViewModel,
    openPlayer: (route: String) -> Unit
) {
    val context = LocalContext.current
    val currentOpenPlayer by androidx.compose.runtime.rememberUpdatedState(openPlayer)
    LaunchedEffect(shell) {
        shell.requests.collectLatest { request ->
            when (val outcome = shell.tune(request)) {
                is LiveTvTuneOutcome.Ready -> {
                    val state = shell.sessionState.value
                    val route = state?.currentStream?.let { liveTvPlayerRoute(state, it) }
                    if (route != null) {
                        currentOpenPlayer(route)
                    } else {
                        Toast.makeText(context, outcome.result.channel.name, Toast.LENGTH_LONG).show()
                    }
                }
                is LiveTvTuneOutcome.Failed ->
                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Remote-control actions for a player that is showing a Live TV channel, or null when the
 * player is showing something else.
 */
@Composable
fun rememberPlayerLiveTvControls(
    shell: LiveTvShellViewModel,
    contentType: String?,
    contentId: String?,
    openPlayer: (route: String) -> Unit
): PlayerLiveTvControls? {
    val context = LocalContext.current
    val session by shell.sessionState.collectAsState()
    val ownGeneration = remember { shell.sessionState.value?.generation }
    val state = session
    val isLiveTvPlayback = contentType.equals("channel", ignoreCase = true) &&
        state != null && state.channel.id == contentId
    DisposableEffect(isLiveTvPlayback) {
        onDispose {
            if (isLiveTvPlayback && ownGeneration != null) shell.clearSessionIfGeneration(ownGeneration)
        }
    }
    if (!isLiveTvPlayback || state == null) return null

    val currentOpenPlayer by androidx.compose.runtime.rememberUpdatedState(openPlayer)
    return remember(shell, state.channel.id, state.generation) {
        PlayerLiveTvControls(
            onChannelStep = { delta ->
                val channels = shell.channelsInSession(state)
                if (channels.size > 1) {
                    val index = channels.indexOfFirst { it.id == state.channel.id }
                    val next = channels[Math.floorMod(index + delta, channels.size)]
                    shell.requestChannel(next.id, state.groupId)
                }
            },
            onSourceFailed = {
                var stream = shell.advanceToBackup()
                var route: String? = null
                var guard = state.streams.size
                while (stream != null && guard > 0) {
                    val current = shell.sessionState.value
                    route = current?.let { liveTvPlayerRoute(it, stream!!) }
                    if (route != null) break
                    stream = shell.advanceToBackup()
                    guard -= 1
                }
                if (route != null) {
                    Toast.makeText(
                        context,
                        context.getString(com.nuvio.tv.R.string.live_tv_player_switching_backup, state.channel.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    currentOpenPlayer(route)
                    true
                } else {
                    Toast.makeText(
                        context,
                        context.getString(com.nuvio.tv.R.string.live_tv_player_all_sources_failed, state.channel.name),
                        Toast.LENGTH_LONG
                    ).show()
                    false
                }
            },
            onPlaybackStarted = { shell.rememberCurrentAsWorking() },
            overlay = { dismiss ->
                LiveTvPlayerOverlay(
                    channels = shell.channelsInSession(state),
                    guide = shell.uiState.value.guide,
                    currentChannelId = state.channel.id,
                    onSelect = { channel ->
                        dismiss()
                        if (channel.id != state.channel.id) shell.requestChannel(channel.id, state.groupId)
                    }
                )
            }
        )
    }
}
