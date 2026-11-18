/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */



package com.gallbladderz.openkick.core.datastore

data class ChatSettings(
    val textScale: Float = 1f,
    val showGlobalBadges: Boolean = true,
    val showChannelBadges: Boolean = true,
    val useOfficialNameColor: Boolean = true,
    val animatedEmotes: Boolean = true,
    val danmakuEnabled: Boolean = false,
    val danmakuSpeed: Int = 10,      // 横穿一屏的秒数（userscript: 4-20）
    val danmakuSize: Int = 18,       // 字号 sp（userscript: 14-38）
    val danmakuOpacity: Float = 0.85f, // 不透明度（userscript: 0.2-1）
    val danmakuArea: Int = 100,      // 显示区域占画面高度百分比（25-100）
    val danmakuDensity: String = "normal" // normal / more / overlap
)
