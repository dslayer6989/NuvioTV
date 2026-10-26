package com.nuvio.tv.livetv

import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** A request to start watching a channel, from any screen (guide, On Now, Home row). */
data class LiveTvPlayRequest(
    val channelId: String,
    /** The chip the user was browsing, so channel up/down stays inside it. */
    val groupId: String? = null,
)

/**
 * Screens ask for playback here; the app shell (which owns navigation and the player)
 * listens and launches.
 */
@Singleton
class LiveTvLauncher @Inject constructor() {
    private val _requests = MutableSharedFlow<LiveTvPlayRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<LiveTvPlayRequest> = _requests.asSharedFlow()

    private val _tuningChannelId = MutableStateFlow<String?>(null)

    /** The channel whose streams are being resolved right now, for "Tuning..." indicators. */
    val tuningChannelId: StateFlow<String?> = _tuningChannelId.asStateFlow()

    fun play(channelId: String, groupId: String? = null) {
        _requests.tryEmit(LiveTvPlayRequest(channelId = channelId, groupId = groupId))
    }

    fun setTuning(channelId: String?) {
        _tuningChannelId.value = channelId
    }
}

/** What the player needs to tune a channel and fail over between its sources. */
data class LiveTvTuneResult(
    val channel: LiveTvChannel,
    val program: LiveTvProgram?,
    val streams: List<Stream>,
    val groupId: String?,
)

sealed interface LiveTvTuneOutcome {
    data class Ready(val result: LiveTvTuneResult) : LiveTvTuneOutcome
    data class Failed(val message: String) : LiveTvTuneOutcome
}

@Singleton
class LiveTvTuner @Inject constructor(
    private val repository: LiveTvRepository,
) {
    /** Resolves a channel's streams (Primary first). Never throws. */
    suspend fun tune(request: LiveTvPlayRequest): LiveTvTuneOutcome {
        repository.ensureLoaded()
        val state = repository.uiState.value
        val guide = state.guide
            ?: return LiveTvTuneOutcome.Failed("Live TV is still loading. Try again in a moment.")
        val channel = guide.channel(request.channelId)
            ?: return LiveTvTuneOutcome.Failed("That channel is no longer in the lineup.")
        val streams = try {
            repository.loadStreams(channel)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            return LiveTvTuneOutcome.Failed("Couldn't reach ${channel.name}: ${error.message ?: "network error"}")
        }
        if (streams.isEmpty()) {
            return LiveTvTuneOutcome.Failed("${channel.name} has no working streams right now.")
        }
        // Start with the source that worked last time; the rest stay in addon order as backups.
        val ordered = orderStreamsByPreference(streams, repository.workingStreamFor(channel.id))
        return LiveTvTuneOutcome.Ready(
            LiveTvTuneResult(
                channel = channel,
                program = guide.programAt(channel.id, LiveTvClock.nowEpochMs()),
                streams = ordered,
                groupId = request.groupId,
            ),
        )
    }
}

/** State of the channel currently playing in the player, shared with the in-player overlay. */
data class LiveTvSessionState(
    val channel: LiveTvChannel,
    val groupId: String?,
    val streams: List<Stream>,
    val streamIndex: Int = 0,
    /** Bumped on every channel or source change so the startup watchdog restarts. */
    val generation: Long = 0L,
) {
    val currentStream: Stream?
        get() = streams.getOrNull(streamIndex)

    val hasBackup: Boolean
        get() = streamIndex + 1 < streams.size
}

@Singleton
class LiveTvPlayerSession @Inject constructor(
    private val repository: LiveTvRepository,
) {
    private val _state = MutableStateFlow<LiveTvSessionState?>(null)
    val state: StateFlow<LiveTvSessionState?> = _state.asStateFlow()
    private var generation = 0L

    fun start(result: LiveTvTuneResult) {
        generation += 1
        _state.value = LiveTvSessionState(
            channel = result.channel,
            groupId = result.groupId,
            streams = result.streams,
            streamIndex = 0,
            generation = generation,
        )
        repository.recordWatched(result.channel.id)
    }

    /** Moves to [index] within the current channel's sources. */
    fun selectStream(index: Int): Stream? {
        val current = _state.value ?: return null
        val stream = current.streams.getOrNull(index) ?: return null
        generation += 1
        _state.value = current.copy(streamIndex = index, generation = generation)
        return stream
    }

    /** Next backup after the current source, or null when every source has been tried. */
    fun advanceToBackup(): Stream? {
        val current = _state.value ?: return null
        if (!current.hasBackup) return null
        return selectStream(current.streamIndex + 1)
    }

    /** Remembers the source that is playing now so it is tried first next time. */
    fun rememberCurrentAsWorking() {
        val current = _state.value ?: return
        val stream = current.currentStream ?: return
        repository.rememberWorkingStream(current.channel.id, stream)
    }

    fun clear() {
        _state.value = null
    }
}

/** Short source label for notifications, e.g. "Backup 1". */
internal fun Stream.liveSourceLabel(index: Int): String =
    name?.takeIf { it.isNotBlank() } ?: if (index == 0) "Primary" else "Backup $index"

/** One-shot UI events for the Live TV screen. */
@Singleton
class LiveTvScreenEvents @Inject constructor() {
    private val _jumpToNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val jumpToNow: SharedFlow<Unit> = _jumpToNow.asSharedFlow()

    fun requestJumpToNow() {
        _jumpToNow.tryEmit(Unit)
    }
}

internal const val LiveTvMaxRememberedStreams = 200

/** The last source that played for a channel, matched by link first and then by its label. */
@Serializable
data class LiveTvWorkingStream(
    val url: String? = null,
    val label: String? = null,
    val savedAtMs: Long = 0L,
) {
    fun sameSourceAs(other: LiveTvWorkingStream): Boolean = url == other.url && label == other.label

    fun matches(stream: Stream): Boolean {
        val streamUrl = stream.getStreamUrl()
        if (url != null && streamUrl == url) return true
        return label != null && stream.sourceLabelKey() == label
    }

    companion object {
        fun of(stream: Stream, nowMs: Long = LiveTvClock.nowEpochMs()): LiveTvWorkingStream? {
            val url = stream.getStreamUrl()
            val label = stream.sourceLabelKey()
            if (url == null && label == null) return null
            return LiveTvWorkingStream(url = url, label = label, savedAtMs = nowMs)
        }
    }
}

private fun Stream.sourceLabelKey(): String? =
    listOfNotNull(name, title, description).filter { it.isNotBlank() }.joinToString("|").ifBlank { null }

/** Moves the remembered source to the front; everything else keeps its order for failover. */
internal fun orderStreamsByPreference(streams: List<Stream>, preferred: LiveTvWorkingStream?): List<Stream> {
    if (preferred == null || streams.size < 2) return streams
    val index = streams.indexOfFirst { preferred.url != null && it.getStreamUrl() == preferred.url }
        .takeIf { it >= 0 }
        ?: streams.indexOfFirst(preferred::matches)
    if (index <= 0) return streams
    return listOf(streams[index]) + streams.filterIndexed { i, _ -> i != index }
}
