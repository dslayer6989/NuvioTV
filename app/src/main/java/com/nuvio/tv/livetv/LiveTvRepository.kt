package com.nuvio.tv.livetv

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveTvViewMode { Guide, OnNow }

data class LiveTvUiState(
    val manifestUrl: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val guide: LiveTvGuide? = null,
    /** null = the full lineup. May also be [LiveTvFavoritesGroupId] or [LiveTvRecentGroupId]. */
    val selectedGroupId: String? = null,
    val viewMode: LiveTvViewMode = LiveTvViewMode.Guide,
    val onNowSort: LiveTvOnNowSort = LiveTvOnNowSort.Channel,
    val favoriteIds: List<String> = emptyList(),
    val recentIds: List<String> = emptyList(),
) {
    val isConfigured: Boolean get() = manifestUrl.isNotBlank()

    fun visibleChannels(): List<LiveTvChannel> {
        val current = guide ?: return emptyList()
        return when (selectedGroupId) {
            LiveTvFavoritesGroupId -> favoriteIds.mapNotNull { current.channel(it) }
            LiveTvRecentGroupId -> recentIds.mapNotNull { current.channel(it) }
            else -> current.channelsFor(selectedGroupId)
        }
    }

    /** Addon groups shown as chips (the addon's own "favorites" group is replaced by ours). */
    fun addonChips(): List<LiveTvGroup> =
        guide?.groups.orEmpty().filterNot { it.isAddonFavoritesGroup() }
}

internal const val LiveTvFavoritesGroupId = "local:favorites"
internal const val LiveTvRecentGroupId = "local:recent"
internal const val LiveTvMaxRecents = 12
internal const val LiveTvStaleAfterMs = 30 * LiveTvTime.MINUTE_MS

private const val LiveTvPrefsFeature = "livetv_prefs"
private val KeyManifestUrl = stringPreferencesKey("manifest_url")
private val KeyFavorites = stringPreferencesKey("favorites")
private val KeyRecents = stringPreferencesKey("recents")
private val KeySelectedGroup = stringPreferencesKey("selected_group")
private val KeyViewMode = stringPreferencesKey("view_mode")
private val KeyOnNowSort = stringPreferencesKey("on_now_sort")
private val KeyWorkingStreams = stringPreferencesKey("working_streams")

internal fun LiveTvGroup.isAddonFavoritesGroup(): Boolean =
    id.contains("favorite", ignoreCase = true) || name.contains("favorite", ignoreCase = true)

/** Prefers the profile's own value, then a legacy value; empty means "not set". */
internal fun resolveProfileValue(scoped: String?, legacy: String?): String? =
    (scoped ?: legacy)?.ifEmpty { null }

internal fun decodeProfileIds(scoped: String?, legacy: String?): List<String> {
    val payload = resolveProfileValue(scoped, legacy) ?: return emptyList()
    return runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(String.serializer()), payload)
    }.getOrNull().orEmpty()
}

internal fun LiveTvUiState.withoutFavorites(): LiveTvUiState = copy(
    favoriteIds = emptyList(),
    selectedGroupId = selectedGroupId.takeUnless { it == LiveTvFavoritesGroupId },
)

internal fun LiveTvUiState.withoutRecents(): LiveTvUiState = copy(
    recentIds = emptyList(),
    selectedGroupId = selectedGroupId.takeUnless { it == LiveTvRecentGroupId },
)

/** Turns whatever the user typed (or pasted) into a manifest.json URL. */
internal fun normalizeManifestUrl(raw: String): String {
    var url = raw.trim()
    if (url.startsWith("stremio://", ignoreCase = true)) {
        url = "https://" + url.substring("stremio://".length)
    } else if (!url.contains("://")) {
        url = "https://$url"
    }
    val query = url.substringAfter("?", "")
    val base = url.substringBefore("?").trimEnd('/')
    val withManifest = if (base.endsWith("/manifest.json", ignoreCase = true)) base else "$base/manifest.json"
    return if (query.isNotEmpty()) "$withManifest?$query" else withManifest
}

@Singleton
class LiveTvRepository @Inject constructor(
    private val storeFactory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    private val http: LiveTvHttp,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val initMutex = Mutex()

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var loadedProfileId: Int? = null
    private var loadJob: Job? = null
    private var profileWatchJob: Job? = null
    private var workingStreams: Map<String, LiveTvWorkingStream> = emptyMap()

    private fun currentProfileId(): Int = loadedProfileId ?: profileManager.activeProfileId.value

    private fun store(profileId: Int) = storeFactory.get(profileId, LiveTvPrefsFeature)

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val profileId = currentProfileId()
        scope.launch {
            runCatching { store(profileId).edit { block(it) } }
        }
    }

    /** Loads saved settings for the active profile (once per profile) and fetches the guide. */
    suspend fun ensureLoaded() {
        initMutex.withLock {
            val profileId = profileManager.activeProfileId.value
            if (!initialized || loadedProfileId != profileId) {
                loadProfile(profileId)
                initialized = true
                if (profileWatchJob == null) watchProfileChanges()
            }
        }
        val state = _uiState.value
        if (state.guide == null && !state.isLoading && state.errorMessage == null && state.isConfigured) {
            refresh()
        }
    }

    private suspend fun loadProfile(profileId: Int) {
        loadJob?.cancel()
        val prefs = runCatching { store(profileId).data.first() }.getOrNull()
        val manifest = prefs?.get(KeyManifestUrl)?.ifEmpty { null } ?: LiveTvDefaultManifestUrl
        val viewMode = prefs?.get(KeyViewMode)
            ?.let { name -> LiveTvViewMode.entries.firstOrNull { it.name == name } }
            ?: LiveTvViewMode.Guide
        val onNowSort = prefs?.get(KeyOnNowSort)
            ?.let { name -> LiveTvOnNowSort.entries.firstOrNull { it.name == name } }
            ?: LiveTvOnNowSort.Channel
        loadedProfileId = profileId
        workingStreams = decodeWorkingStreams(prefs?.get(KeyWorkingStreams))
        _uiState.value = LiveTvUiState(
            manifestUrl = manifest,
            selectedGroupId = prefs?.get(KeySelectedGroup)?.ifEmpty { null },
            viewMode = viewMode,
            onNowSort = onNowSort,
            favoriteIds = decodeProfileIds(prefs?.get(KeyFavorites), null),
            recentIds = decodeProfileIds(prefs?.get(KeyRecents), null),
        )
    }

    private fun watchProfileChanges() {
        profileWatchJob = scope.launch {
            profileManager.activeProfileId.collect { id ->
                if (initialized && id != loadedProfileId) {
                    runCatching { ensureLoaded() }
                }
            }
        }
    }

    fun refresh(forceRefresh: Boolean = false) {
        val url = _uiState.value.manifestUrl
        if (url.isBlank()) return
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = _uiState.value.guide == null,
            isRefreshing = _uiState.value.guide != null,
            errorMessage = null,
        )
        loadJob = scope.launch {
            try {
                val guide = LiveTvGuideLoader.load(
                    manifestUrl = normalizeManifestUrl(url),
                    nowMs = LiveTvClock.nowEpochMs(),
                    forceRefresh = forceRefresh,
                    fetch = http::fetch,
                )
                seedFavoritesIfNeeded(guide)
                val current = _uiState.value
                val selected = current.selectedGroupId
                val keep = selected == null ||
                    selected == LiveTvFavoritesGroupId ||
                    selected == LiveTvRecentGroupId ||
                    guide.groups.any { it.id == selected }
                _uiState.value = current.copy(
                    guide = guide,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    selectedGroupId = if (keep) selected else null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val current = _uiState.value
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = if (current.guide == null) {
                        error.message ?: "Couldn't load Live TV."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** Reloads in the background when the guide has gone stale. */
    fun refreshIfStale(nowMs: Long = LiveTvClock.nowEpochMs()) {
        val state = _uiState.value
        val guide = state.guide ?: return
        if (state.isLoading || state.isRefreshing) return
        if (nowMs - guide.loadedAtMs >= LiveTvStaleAfterMs) refresh()
    }

    fun setManifestUrl(url: String) {
        val trimmed = url.trim()
        edit { it[KeyManifestUrl] = trimmed; it[KeySelectedGroup] = "" }
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            manifestUrl = trimmed,
            guide = null,
            selectedGroupId = null,
            isLoading = false,
            isRefreshing = false,
            errorMessage = null,
        )
        refresh(forceRefresh = true)
    }

    fun selectGroup(groupId: String?) {
        // An explicit "all channels" is stored as "" so it is distinguishable from "never set".
        edit { it[KeySelectedGroup] = groupId ?: "" }
        _uiState.value = _uiState.value.copy(selectedGroupId = groupId)
    }

    fun setViewMode(mode: LiveTvViewMode) {
        edit { it[KeyViewMode] = mode.name }
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setOnNowSort(sort: LiveTvOnNowSort) {
        edit { it[KeyOnNowSort] = sort.name }
        _uiState.value = _uiState.value.copy(onNowSort = sort)
    }

    fun isFavorite(channelId: String): Boolean = channelId in _uiState.value.favoriteIds

    fun toggleFavorite(channelId: String) {
        val current = _uiState.value
        val updated = if (channelId in current.favoriteIds) {
            current.favoriteIds - channelId
        } else {
            current.favoriteIds + channelId
        }
        saveIds(KeyFavorites, updated)
        _uiState.value = current.copy(favoriteIds = updated)
    }

    fun recordWatched(channelId: String) {
        val current = _uiState.value
        val updated = (listOf(channelId) + current.recentIds.filter { it != channelId }).take(LiveTvMaxRecents)
        saveIds(KeyRecents, updated)
        _uiState.value = current.copy(recentIds = updated)
    }

    fun clearFavorites() {
        saveIds(KeyFavorites, emptyList())
        val updated = _uiState.value.withoutFavorites()
        if (updated.selectedGroupId == null) edit { it[KeySelectedGroup] = "" }
        _uiState.value = updated
    }

    fun clearRecents() {
        saveIds(KeyRecents, emptyList())
        val updated = _uiState.value.withoutRecents()
        if (updated.selectedGroupId == null) edit { it[KeySelectedGroup] = "" }
        _uiState.value = updated
    }

    /** The channel watched before [currentId], for the "previous channel" remote button. */
    fun previousChannelId(currentId: String): String? =
        _uiState.value.recentIds.firstOrNull { it != currentId }

    fun rememberWorkingStream(channelId: String, stream: Stream) {
        val entry = LiveTvWorkingStream.of(stream) ?: return
        val existing = workingStreams[channelId]
        if (existing != null && existing.sameSourceAs(entry)) return
        val updated = (workingStreams + (channelId to entry))
            .entries
            .sortedByDescending { it.value.savedAtMs }
            .take(LiveTvMaxRememberedStreams)
            .associate { it.key to it.value }
        workingStreams = updated
        val encoded = json.encodeToString(
            MapSerializer(String.serializer(), LiveTvWorkingStream.serializer()),
            updated,
        )
        edit { it[KeyWorkingStreams] = encoded }
    }

    fun workingStreamFor(channelId: String): LiveTvWorkingStream? = workingStreams[channelId]

    suspend fun loadStreams(channel: LiveTvChannel): List<Stream> {
        val state = _uiState.value
        val guide = state.guide ?: throw IllegalStateException("Live TV guide isn't loaded yet.")
        return LiveTvGuideLoader.loadStreams(
            manifestUrl = normalizeManifestUrl(state.manifestUrl),
            guide = guide,
            channel = channel,
            fetch = http::fetch,
        )
    }

    private fun decodeWorkingStreams(payload: String?): Map<String, LiveTvWorkingStream> {
        if (payload.isNullOrEmpty()) return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), LiveTvWorkingStream.serializer()),
                payload,
            )
        }.getOrNull().orEmpty()
    }

    private fun saveIds(key: Preferences.Key<String>, ids: List<String>) {
        val encoded = json.encodeToString(ListSerializer(String.serializer()), ids)
        edit { it[key] = encoded }
    }

    /** First time this profile sees a guide: start its favorites from the addon's own favorites group. */
    private suspend fun seedFavoritesIfNeeded(guide: LiveTvGuide) {
        val profileId = currentProfileId()
        val prefs = runCatching { store(profileId).data.first() }.getOrNull() ?: return
        if (prefs.contains(KeyFavorites)) return
        val seed = guide.groups.firstOrNull { it.isAddonFavoritesGroup() }?.channelIds.orEmpty()
        saveIds(KeyFavorites, seed)
        _uiState.value = _uiState.value.copy(favoriteIds = seed)
    }

    /** Forgets everything held in memory (used when signing out). */
    fun clearLocalState() {
        loadJob?.cancel()
        profileWatchJob?.cancel()
        profileWatchJob = null
        initialized = false
        loadedProfileId = null
        workingStreams = emptyMap()
        _uiState.value = LiveTvUiState()
    }
}
