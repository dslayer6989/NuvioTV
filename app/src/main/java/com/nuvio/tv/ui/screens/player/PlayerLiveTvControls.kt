package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * What the player needs from Live TV when it is playing a channel. Null for every other playback.
 */
@Stable
class PlayerLiveTvControls(
    /** Moves one channel up (+1) or down (-1) in the list the user was browsing. */
    val onChannelStep: (delta: Int) -> Unit,
    /** The current source failed. Returns true when a backup source is taking over. */
    val onSourceFailed: () -> Boolean,
    /** The stream has started playing, so it can be remembered as the working source. */
    val onPlaybackStarted: () -> Unit,
    /** The channel list shown over the picture (Guide key or Menu key). */
    val overlay: @Composable (onDismiss: () -> Unit) -> Unit
)
