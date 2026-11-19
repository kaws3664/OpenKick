/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gallbladderz.openkick.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import com.gallbladderz.openkick.core.datastore.ChatSettings
import com.gallbladderz.openkick.core.datastore.SettingsRepository
import com.gallbladderz.openkick.core.player.PlayerExpandedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.foundation.gestures.AnchoredDraggableState

@Composable
fun PlayerRoute(
    streamerName: String,
    dragState: AnchoredDraggableState<PlayerExpandedState>,
    onBackClick: () -> Unit,
    onAvatarClick: (String) -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    viewModel: PlayerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatSendState by viewModel.chatSendState.collectAsStateWithLifecycle()
    val chatEmoteSets by viewModel.chatEmoteSets.collectAsStateWithLifecycle()
    val chatMode by viewModel.chatMode.collectAsStateWithLifecycle()
    val channelLinks by viewModel.channelLinks.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isStreamerFollowed(streamerName)
        .collectAsStateWithLifecycle(initialValue = false)

    val playWhenReady by viewModel.playerManager.playWhenReady.collectAsStateWithLifecycle()
    val playbackState by viewModel.playerManager.playbackState.collectAsStateWithLifecycle()
    val availableQualities by viewModel.availableQualities.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()

    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val settingsRepository: SettingsRepository = koinInject()
    val chatSettings by settingsRepository.chatSettingsFlow.collectAsStateWithLifecycle(
        initialValue = ChatSettings(),
        lifecycle = LocalLifecycleOwner.current.lifecycle
    )
    val settingsScope = rememberCoroutineScope()
    val settingsWriteMutex = remember { Mutex() }
    var displayedChatSettings by remember { mutableStateOf(chatSettings) }

    LaunchedEffect(chatSettings) { displayedChatSettings = chatSettings }

    val closePlayer = {
        viewModel.playerManager.release()
        viewModel.disconnectChat()
        onBackClick()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.playerManager.release()
            viewModel.disconnectChat()
        }
    }

    PlayerScreen(
        streamerName = streamerName,
        uiState = uiState,
        dragState = dragState,
        chatMessages = chatMessages,
        chatSendState = chatSendState,
        chatEmoteSets = chatEmoteSets,
        chatMode = chatMode,
        isAuthenticated = isAuthenticated,
        chatSettings = displayedChatSettings,
        channelLinks = channelLinks,
        isFollowed = isFollowed,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        availableQualities = availableQualities,
        selectedQuality = selectedQuality,
        player = viewModel.playerManager.player,
        onLoadStreamInfo = { viewModel.loadStreamInfo(it) },
        onLoadChannelLinks = { viewModel.loadChannelLinks(it) },
        onPlayerManagerInitialize = { viewModel.playerManager.initializePlayer() },
        onPlayerManagerPause = { viewModel.playerManager.pause() },
        onPlayerManagerResume = { viewModel.playerManager.resume() },
        onReloadStream = { viewModel.playerManager.reload() },
        onPlayerManagerRelease = {
            viewModel.playerManager.release()
            viewModel.disconnectChat()
        },
        onToggleFollow = { streamer, followed -> viewModel.toggleFollow(streamer, followed) },
        onSetVideoQuality = { viewModel.setVideoQuality(it) },
        onBackClick = closePlayer,
        onAvatarClick = onAvatarClick,
        onCategoryClick = { slug -> onCategoryClick(slug) },
        onSeekToLiveEdge = { viewModel.playerManager.seekToLiveEdge() },
        onSendChatMessage = { content, replyTo -> viewModel.sendChatMessage(content, replyTo) },
        onChatSettingsChange = { updated ->
            displayedChatSettings = updated
            settingsScope.launch {
                settingsWriteMutex.withLock {
                    settingsRepository.setChatSettings(updated)
                }
            }
        }
    )
}

