/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gallbladderz.openkick.features.following


import com.gallbladderz.openkick.core.auth.AppMode
import com.gallbladderz.openkick.core.auth.SessionManager
import com.gallbladderz.openkick.core.data.local.FollowType
import com.gallbladderz.openkick.core.data.local.FollowedEntity
import com.gallbladderz.openkick.core.datastore.SettingsRepository
import com.gallbladderz.openkick.core.domain.toDomainError
import com.gallbladderz.openkick.core.network.api.KickApiService
import com.gallbladderz.openkick.core.network.dto.categories.CategoryDetailsResponse
import com.gallbladderz.openkick.core.network.dto.profile.ChannelV1Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FollowingRepository(
    private val apiService: KickApiService,
    private val local: LocalFollowingDataSource,
    private val remote: RemoteFollowingDataSource,
    private val remoteCategories: RemoteCategoryFollowingDataSource,
    private val session: SessionManager,
    private val settingsRepository: SettingsRepository
) {
    private val refreshMutex = Mutex()
    private val categoryRefreshMutex = Mutex()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun followedEntities(): Flow<List<FollowedEntity>> = session.mode.flatMapLatest { mode ->
        if (mode == AppMode.AUTHENTICATED) {
            combine(remoteCategories.categories, remote.channels) { categories, channels ->
                categories.map { category ->
                    FollowedEntity(
                        slug = category.slug,
                        type = FollowType.CATEGORY,
                        categoryName = category.name
                    )
                } + channels.map {
                    FollowedEntity(
                        slug = it.slug,
                        type = FollowType.STREAMER,
                        isLive = it.isLive,
                        username = it.username.ifBlank { it.slug },
                        avatarUrl = it.profilePic,
                        streamTitle = it.sessionTitle,
                        viewers = it.viewerCount,
                        categoryName = it.categoryName
                    )
                }
            }
        } else local.entities().map { list ->
            // 登录过再登出：本地记录已被 migrateLocalToRemote 清空，seed 标记又拦着重种
            // → 未登录且本地为空时补上默认推荐账号
            if (list.isEmpty()) defaultFollowEntities() else list
        }
    }

    companion object {
        /** 默认推荐关注的频道（未登录时关注页至少显示这两个） */
        private val defaultFollowEntities = {
            listOf(
                FollowedEntity(
                    slug = "quin3614",
                    type = FollowType.STREAMER,
                    username = "Quin3614",
                    avatarUrl = "https://files.kick.com/images/user/120368189/profile_image/conversion/65cc6371-ff00-4077-9434-a614d585194d-fullsize.webp"
                ),
                FollowedEntity(
                    slug = "czcjiaxu",
                    type = FollowType.STREAMER,
                    username = "Czcjiaxu",
                    avatarUrl = "https://files.kick.com/images/user/21083445/profile_image/conversion/06b1a4ed-16c3-47fb-8707-316edf390708-fullsize.webp"
                )
            )
        }
    }

    fun isStreamerFollowed(slug: String): Flow<Boolean> = followedEntities().map { entities ->
        entities.any { it.type == FollowType.STREAMER && it.slug.equals(slug, ignoreCase = true) }
    }

    fun followedCategorySlugs(): Flow<Set<String>> = followedEntities().map { entities ->
        entities.filter { it.type == FollowType.CATEGORY }.map { it.slug.lowercase() }.toSet()
    }

    suspend fun toggleCategoryFollow(slug: String, currentlyFollowed: Boolean) {
        if (session.mode.value == AppMode.AUTHENTICATED) {
            remoteCategories.setFollowed(slug, !currentlyFollowed)
        } else if (currentlyFollowed) local.delete(slug, FollowType.CATEGORY)
        else local.save(FollowedEntity(slug, FollowType.CATEGORY))
    }

    suspend fun toggleStreamerFollow(slug: String, currentlyFollowed: Boolean) {
        if (session.mode.value == AppMode.AUTHENTICATED) {
            if (currentlyFollowed) remote.unfollow(slug) else remote.follow(slug)
        } else if (currentlyFollowed) local.delete(slug, FollowType.STREAMER)
        else local.save(FollowedEntity(slug, FollowType.STREAMER))
    }

    suspend fun refreshFollowing() = refreshMutex.withLock {
        if (session.mode.value != AppMode.AUTHENTICATED) return@withLock
        remote.refresh()

        try {
            refreshCategories()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {

        }
    }

    suspend fun refreshCategories() = categoryRefreshMutex.withLock {
        if (session.mode.value != AppMode.AUTHENTICATED) return@withLock
        val generation = session.sessionGeneration.value
        remoteCategories.refresh()

        for (category in local.categories().first()) {
            check(session.mode.value == AppMode.AUTHENTICATED && session.sessionGeneration.value == generation) {
                "Session changed while moving followed categories"
            }
            remoteCategories.setFollowed(category.slug, true)
            check(session.mode.value == AppMode.AUTHENTICATED && session.sessionGeneration.value == generation) {
                "Session changed while moving followed categories"
            }
            local.delete(category.slug, FollowType.CATEGORY)
        }
    }

    fun localStreamers(): Flow<List<FollowedEntity>> = local.streamers()
    val isAuthenticated: Boolean get() = session.mode.value == AppMode.AUTHENTICATED

    suspend fun saveLocalSnapshot(entity: FollowedEntity) = local.save(entity)

    /** 首次启动时写入默认关注的频道（只在首次生效，用户取关后不会复活） */
    suspend fun seedDefaultFollows() {
        if (settingsRepository.defaultFollowsSeededFlow.first()) return
        val defaults = listOf(
            Triple(
                "quin3614", "Quin3614",
                "https://files.kick.com/images/user/120368189/profile_image/conversion/65cc6371-ff00-4077-9434-a614d585194d-fullsize.webp"
            ),
            Triple(
                "czcjiaxu", "Czcjiaxu",
                "https://files.kick.com/images/user/21083445/profile_image/conversion/06b1a4ed-16c3-47fb-8707-316edf390708-fullsize.webp"
            )
        )
        val existing = local.streamers().first().map { it.slug }.toSet()
        for ((slug, username, avatarUrl) in defaults) {
            if (slug !in existing) {
                local.save(
                    FollowedEntity(
                        slug = slug,
                        type = FollowType.STREAMER,
                        username = username,
                        avatarUrl = avatarUrl
                    )
                )
            }
        }
        setSeeded(true)
    }

    private suspend fun setSeeded(value: Boolean) = settingsRepository.setDefaultFollowsSeeded(value)

    suspend fun migrateLocalToRemote() {
        check(session.mode.value == AppMode.AUTHENTICATED) { "Sign in before migrating follows" }
        remote.refresh()
        val generation = session.sessionGeneration.value
        val existing = remote.currentChannels().map { it.slug }.toSet()
        for (entity in local.streamers().first()) {
            check(session.mode.value == AppMode.AUTHENTICATED && session.sessionGeneration.value == generation) {
                "Session changed while migrating subscriptions"
            }
            if (entity.slug !in existing) remote.follow(entity.slug)
            check(session.mode.value == AppMode.AUTHENTICATED && session.sessionGeneration.value == generation) {
                "Session changed while migrating subscriptions"
            }
            local.delete(entity.slug, FollowType.STREAMER)
        }
    }

    suspend fun fetchChannelDetails(slug: String): Result<FollowedStreamerUi> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getChannelV1(slug)
                Result.success(response.toFollowedStreamerDomain(slug))
            } catch (e: Exception) {
                Result.failure(e.toDomainError())
            }
        }

    suspend fun fetchCategoryDetails(slug: String): Result<FollowedCategoryUi> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCategoryDetails(slug)
                Result.success(response.toFollowedCategoryDomain(slug))
            } catch (e: Exception) {
                Result.failure(e.toDomainError())
            }
        }
}

fun ChannelV1Response.toFollowedStreamerDomain(slugFallback: String): FollowedStreamerUi {
    val username = this.user?.username ?: this.slug ?: slugFallback
    val avatarUrl = this.user?.profilePic ?: ""

    return if (this.livestream != null) {
        val title = this.livestream.sessionTitle ?: "Untitled"
        val viewers = this.livestream.viewerCount
        val categoryName = this.livestream.category?.name ?: ""
        val streamThumbnailUrl = this.livestream.thumbnail?.finalUrl ?: ""

        FollowedStreamerUi(
            slugFallback,
            username,
            avatarUrl,
            true,
            title,
            viewers,
            categoryName,
            streamThumbnailUrl
        )
    } else {
        FollowedStreamerUi(slugFallback, username, avatarUrl, false)
    }
}

fun CategoryDetailsResponse.toFollowedCategoryDomain(slugFallback: String): FollowedCategoryUi {
    var bannerUrl = this.banner?.finalUrl ?: ""
    bannerUrl = bannerUrl.replace("\\/", "/")
    if (bannerUrl.contains(" ")) {
        bannerUrl = bannerUrl.split(",").firstOrNull()?.trim()?.substringBefore(" ") ?: bannerUrl
    }

    return FollowedCategoryUi(slugFallback, this.name, bannerUrl, this.viewers)
}
