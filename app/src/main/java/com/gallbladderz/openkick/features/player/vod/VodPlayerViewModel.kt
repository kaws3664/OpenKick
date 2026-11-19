/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gallbladderz.openkick.features.player.vod


import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.gallbladderz.openkick.core.domain.chat.ChatEmoteParser
import com.gallbladderz.openkick.core.domain.chat.ChatMessage
import com.gallbladderz.openkick.core.network.api.VodChatApi
import com.gallbladderz.openkick.core.player.PlayerManager
import com.gallbladderz.openkick.features.profile.StreamerProfileRepository
import com.gallbladderz.openkick.features.profile.VideoUiModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.LocalDateTime

sealed class VodPlayerUiState {
    object Loading : VodPlayerUiState()
    data class Success(val metadata: VideoUiModel, val playbackUrl: String) : VodPlayerUiState()
    data class Error(val message: String) : VodPlayerUiState()
}

/** VOD 聊天回放消息：offsetMs = 消息时间 - VOD 开播时间 */
data class VodChatItem(val offsetMs: Long, val message: ChatMessage)

private const val TAG = "VodChat"

@UnstableApi
class VodPlayerViewModel(
    private val videoId: String,
    context: Context,
    dataSourceFactory: DataSource.Factory,
    private val repository: StreamerProfileRepository,
    private val vodChatApi: VodChatApi
) : ViewModel() {

    val playerManager = PlayerManager(context, dataSourceFactory)

    private val _uiState = MutableStateFlow<VodPlayerUiState>(VodPlayerUiState.Loading)
    val uiState: StateFlow<VodPlayerUiState> = _uiState.asStateFlow()

    // VOD 聊天回放：按播放位置增量拉取的完整列表
    private val _chatItems = MutableStateFlow<List<VodChatItem>>(emptyList())
    val chatItems: StateFlow<List<VodChatItem>> = _chatItems.asStateFlow()

    private var chatBaseEpochMs = 0L      // VOD 开播时间
    private var chatChannelId = 0L
    private var loadedUntilMs = -1L       // 已加载到的偏移（消息按 offset 排序的末尾）
    private var chatFetchFailed = false   // 未登录/接口不可用时不再重试

    init {
        playerManager.initializePlayer()
        loadVod()
    }

    fun loadVod() {
        viewModelScope.launch {
            _uiState.update { VodPlayerUiState.Loading }

            val metadata = repository.getCachedVideo(videoId)

            if (metadata == null) {
                _uiState.update { VodPlayerUiState.Error("VOD metadata not found in cache") }
                return@launch
            }

            val urlDeferred = async { repository.fetchVideoPlaybackUrl(videoId) }
            val urlResult = urlDeferred.await()

            if (urlResult.isSuccess) {
                val url = urlResult.getOrThrow()

                _uiState.update { VodPlayerUiState.Success(metadata, url) }

                playerManager.play(
                    videoUrl = url,
                    title = metadata.title,
                    streamerName = metadata.channelUsername,
                    avatarUrl = "",
                    isLive = false
                )
                initChatReplay()
            } else {
                val errorMessage = urlResult.exceptionOrNull()?.message
                    ?: "Unknown error loading VOD"
                _uiState.update { VodPlayerUiState.Error(errorMessage) }
            }
        }
    }

    private fun initChatReplay() {
        viewModelScope.launch {
            try {
                val meta = vodChatApi.videoMeta(videoId)
                val baseIso = meta.createdAt ?: meta.livestream?.createdAt
                val channelId = meta.livestream?.channelId
                if (baseIso == null || channelId == null) {
                    Log.w(TAG, "replay meta missing fields, chat disabled")
                    chatFetchFailed = true
                    return@launch
                }
                chatChannelId = channelId
                chatBaseEpochMs = parseKickTime(baseIso)
                loadedUntilMs = 0L
                Log.d(TAG, "chat replay ready base=$chatBaseEpochMs channel=$chatChannelId")
            } catch (e: Exception) {
                Log.w(TAG, "chat replay meta failed: ${e.message}")
                chatFetchFailed = true
            }
        }
    }

    /** 播放位置推进时调用：保证已加载到 untilMs 之后的消息 */
    fun ensureChatLoaded(untilMs: Long) {
        if (chatFetchFailed || chatChannelId == 0L || loadedUntilMs < 0L) return
        if (untilMs + 60_000L <= loadedUntilMs) return
        viewModelScope.launch {
            var startMs = loadedUntilMs
            try {
                repeat(4) {
                    val startIso = isoOf(chatBaseEpochMs + startMs)
                    val resp = vodChatApi.channelMessages(chatChannelId, startIso)
                    val messages = resp.data?.messages.orEmpty()
                    if (messages.isEmpty()) {
                        loadedUntilMs = untilMs + 120_000L
                        return@launch
                    }
                    val items = messages.mapNotNull { m -> toVodChatItem(m) }
                    _chatItems.update { old -> (old + items).distinctBy { it.message.id } }
                    // 取本批最大的 offset 作为下一次起点
                    val maxOffset = items.maxOfOrNull { it.offsetMs } ?: return@launch
                    if (maxOffset <= startMs) {
                        loadedUntilMs = untilMs + 120_000L
                        return@launch
                    }
                    startMs = maxOffset
                    loadedUntilMs = maxOffset
                }
            } catch (e: Exception) {
                Log.w(TAG, "chat page failed: ${e.message}")
                chatFetchFailed = true
            }
        }
    }

    private fun toVodChatItem(m: com.gallbladderz.openkick.core.network.chat.ChatMessageDataDto): VodChatItem? {
        val content = m.content ?: return null
        val senderName = m.sender?.username ?: m.user?.username ?: return null
        val createdIso = m.created_at ?: return null
        val offset = (parseKickTime(createdIso) - chatBaseEpochMs).coerceAtLeast(0L)
        val sender = m.sender ?: m.user
        val message = ChatMessage(
            id = m.id ?: "vod_${createdIso}_$senderName",
            sender = senderName,
            avatarUrl = sender?.profilePic ?: sender?.profilePicture ?: sender?.avatarUrl ?: "",
            senderColor = sender?.identity?.color ?: sender?.identity?.username_color ?: "#ffffff",
            content = content,
            tokens = ChatEmoteParser.parseEmotes(content)
        )
        return VodChatItem(offset, message)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

/** Kick 返回两种时间格式：ISO（含微秒）与 "yyyy-MM-dd HH:mm:ss"（UTC） */
internal fun parseKickTime(raw: String): Long = try {
    Instant.parse(raw).toEpochMilli()
} catch (e: Exception) {
    try {
        LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (e2: Exception) {
        Instant.parse(raw.replace(" ", "T") + if (raw.endsWith("Z")) "" else "Z").toEpochMilli()
    }
}

internal fun isoOf(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).toString()
