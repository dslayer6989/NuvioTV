@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.livetv.LiveTvRepository
import com.nuvio.tv.livetv.LiveTvUiState
import com.nuvio.tv.livetv.normalizeManifestUrl
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.collection.NuvioTextField
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Bridge between the Live TV settings page and the shared Live TV repository. */
@HiltViewModel
class LiveTvSettingsViewModel @Inject constructor(
    private val repository: LiveTvRepository
) : ViewModel() {
    val uiState: StateFlow<LiveTvUiState> = repository.uiState

    fun setManifestUrl(url: String) {
        if (normalizeManifestUrl(url) != normalizeManifestUrl(repository.uiState.value.manifestUrl)) {
            repository.setManifestUrl(url)
        }
    }

    fun removeAddon() = repository.setManifestUrl("")
    fun refresh() = repository.refresh(forceRefresh = true)
    fun clearFavorites() = repository.clearFavorites()
    fun clearRecents() = repository.clearRecents()
}

private enum class LiveTvSettingsDialogKind { Addon, RemoveAddon, ClearFavorites, ClearRecents }

@Composable
internal fun LiveTvSettingsContent(
    initialFocusRequester: FocusRequester?,
    viewModel: LiveTvSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LiveTvSettingsDialogKind?>(null) }
    val guide = state.guide

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.live_tv_settings_page_title),
            subtitle = stringResource(R.string.live_tv_settings_entry_description)
        )
        SettingsSectionLabel(
            text = stringResource(R.string.live_tv_settings_section_addon),
            description = stringResource(R.string.live_tv_settings_manifest_description)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.live_tv_addon_label),
                subtitle = if (guide != null) {
                    stringResource(R.string.live_tv_settings_guide_status, guide.addonName, guide.lineupIds.size)
                } else {
                    stringResource(R.string.live_tv_setup_title)
                },
                value = state.manifestUrl.takeIf { it.isNotBlank() },
                onClick = { dialog = LiveTvSettingsDialogKind.Addon },
                leadingIcon = Icons.Default.Link,
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
            SettingsActionRow(
                title = stringResource(R.string.live_tv_settings_refresh),
                subtitle = null,
                onClick = viewModel::refresh,
                enabled = state.isConfigured && !state.isLoading && !state.isRefreshing,
                leadingIcon = Icons.Default.Refresh
            )
            SettingsActionRow(
                title = stringResource(R.string.live_tv_settings_remove),
                subtitle = null,
                onClick = { dialog = LiveTvSettingsDialogKind.RemoveAddon },
                enabled = state.isConfigured,
                leadingIcon = Icons.Default.Delete
            )
        }
        SettingsSectionLabel(text = stringResource(R.string.live_tv_settings_section_lists))
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.live_tv_settings_clear_favorites),
                subtitle = null,
                value = stringResource(R.string.live_tv_settings_count, state.favoriteIds.size),
                onClick = { dialog = LiveTvSettingsDialogKind.ClearFavorites },
                enabled = state.favoriteIds.isNotEmpty(),
                leadingIcon = Icons.Default.Star
            )
            SettingsActionRow(
                title = stringResource(R.string.live_tv_settings_clear_recents),
                subtitle = null,
                value = stringResource(R.string.live_tv_settings_count, state.recentIds.size),
                onClick = { dialog = LiveTvSettingsDialogKind.ClearRecents },
                enabled = state.recentIds.isNotEmpty(),
                leadingIcon = Icons.Default.History
            )
        }
        SettingsNote(text = stringResource(R.string.live_tv_settings_lists_note))
    }

    when (dialog) {
        LiveTvSettingsDialogKind.Addon -> AddonUrlDialog(
            initialUrl = state.manifestUrl,
            onDismiss = { dialog = null },
            onSave = { url ->
                dialog = null
                viewModel.setManifestUrl(url)
            }
        )
        LiveTvSettingsDialogKind.RemoveAddon -> ConfirmDialog(
            title = stringResource(R.string.live_tv_settings_remove),
            message = stringResource(R.string.live_tv_settings_remove_message),
            confirmLabel = stringResource(R.string.live_tv_settings_remove),
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                viewModel.removeAddon()
            }
        )
        LiveTvSettingsDialogKind.ClearFavorites -> ConfirmDialog(
            title = stringResource(R.string.live_tv_settings_clear_favorites),
            message = stringResource(R.string.live_tv_settings_lists_note),
            confirmLabel = stringResource(R.string.live_tv_settings_clear),
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                viewModel.clearFavorites()
            }
        )
        LiveTvSettingsDialogKind.ClearRecents -> ConfirmDialog(
            title = stringResource(R.string.live_tv_settings_clear_recents),
            message = stringResource(R.string.live_tv_settings_lists_note),
            confirmLabel = stringResource(R.string.live_tv_settings_clear),
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                viewModel.clearRecents()
            }
        )
        null -> Unit
    }
}

@Composable
private fun AddonUrlDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.live_tv_settings_section_addon),
        subtitle = stringResource(R.string.live_tv_setup_message)
    ) {
        NuvioTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.live_tv_manifest_placeholder)
        )
        SettingsDialogActionRow {
            SettingsDialogActionButton(
                text = stringResource(R.string.live_tv_cancel),
                onClick = onDismiss
            )
            SettingsDialogActionButton(
                text = stringResource(R.string.live_tv_save),
                onClick = { onSave(url) },
                primary = true,
                enabled = url.isNotBlank()
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = message
    ) {
        SettingsDialogActionRow {
            SettingsDialogActionButton(
                text = stringResource(R.string.live_tv_cancel),
                onClick = onDismiss
            )
            SettingsDialogActionButton(
                text = confirmLabel,
                onClick = onConfirm,
                primary = true
            )
        }
    }
}
