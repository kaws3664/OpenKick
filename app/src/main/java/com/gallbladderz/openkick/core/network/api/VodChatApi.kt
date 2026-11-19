/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gallbladderz.openkick.core.network.api

import androidx.annotation.Keep
import com.gallbladderz.openkick.core.network.chat.ChatMessageDataDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** VOD 回放聊天：元数据匿名可取，消息历史需要登录态（Bearer/Cookie 由 AuthInterceptor 注入）。 */
@Keep
interface VodChatApi {
    @GET("api/v1/video/{uuid}")
    suspend fun videoMeta(@Path("uuid") uuid: String): VodReplayMetaDto

    @GET("api/v2/channels/{channelId}/messages")
    suspend fun channelMessages(
        @Path("channelId") channelId: Long,
        @Query("start_time") startTime: String
    ): VodMessagesResponseDto
}

@Keep
@Serializable
data class VodReplayMetaDto(
    val id: Long? = null,
    val uuid: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val livestream: VodLiveStreamDto? = null
)

@Keep
@Serializable
data class VodLiveStreamDto(
    val id: Long? = null,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Keep
@Serializable
data class VodMessagesResponseDto(
    val `data`: VodMessagesDataDto? = null,
    val meta: VodMessagesMetaDto? = null
)

@Keep
@Serializable
data class VodMessagesDataDto(
    val messages: List<ChatMessageDataDto> = emptyList(),
    @SerialName("pinned_message") val pinnedMessage: kotlinx.serialization.json.JsonElement? = null,
    val cursor: String? = null
)

@Keep
@Serializable
data class VodMessagesMetaDto(val cursor: String? = null)
