package com.nuvio.tv.livetv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.livetv.LiveTvChannel
import com.nuvio.tv.livetv.LiveTvLauncher
import com.nuvio.tv.livetv.LiveTvOnNowSort
import com.nuvio.tv.livetv.LiveTvRepository
import com.nuvio.tv.livetv.LiveTvScreenEvents
import com.nuvio.tv.livetv.LiveTvUiState
import com.nuvio.tv.livetv.LiveTvViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin bridge between the Live TV screens and the singleton repository/launcher. */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val launcher: LiveTvLauncher,
    events: LiveTvScreenEvents
) : ViewModel() {

    val uiState: StateFlow<LiveTvUiState> = repository.uiState
    val tuningChannelId: StateFlow<String?> = launcher.tuningChannelId
    val jumpToNow: SharedFlow<Unit> = events.jumpToNow

    init {
        viewModelScope.launch { repository.ensureLoaded() }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch { repository.refresh(forceRefresh = force) }
    }

    fun refreshIfStale(nowMs: Long) {
        viewModelScope.launch { repository.refreshIfStale(nowMs) }
    }

    fun setManifestUrl(url: String) {
        viewModelScope.launch { repository.setManifestUrl(url) }
    }

    fun selectGroup(groupId: String?) {
        viewModelScope.launch { repository.selectGroup(groupId) }
    }

    fun setViewMode(mode: LiveTvViewMode) {
        viewModelScope.launch { repository.setViewMode(mode) }
    }

    fun setOnNowSort(sort: LiveTvOnNowSort) {
        viewModelScope.launch { repository.setOnNowSort(sort) }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { repository.toggleFavorite(channelId) }
    }

    fun play(channel: LiveTvChannel) {
        launcher.play(channel.id, uiState.value.selectedGroupId)
    }

    fun playId(channelId: String) {
        launcher.play(channelId, uiState.value.selectedGroupId)
    }
}
