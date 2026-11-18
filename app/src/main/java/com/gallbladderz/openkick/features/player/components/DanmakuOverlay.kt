package com.gallbladderz.openkick.features.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallbladderz.openkick.core.datastore.ChatSettings
import com.gallbladderz.openkick.core.domain.chat.ChatMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// 与 kick显示弹幕 userscript（greasyfork 600728）对齐的参数
private const val DENSITY_NORMAL_GAP_FACTOR = 0.3f  // 轨道复用：上一条飞出屏幕的比例
private const val DENSITY_MORE_GAP_FACTOR = 0.1f
private const val DENSITY_OVERLAP_GAP_FACTOR = 0f
private const val MAX_TRACKED = 200

private val EMOTE_TOKEN = Regex("\\[emote:\\d+:([^\\]]+)\\]")

/** Kick 的 emote 在 content 里是 [emote:ID:名字]，弹幕里显示成表情名字 */
private fun danmakuText(content: String): String =
    content.replace(EMOTE_TOKEN, "$1").trim()

/**
 * 弹幕覆盖层：把新到的聊天消息以右→左滚动字幕画在播放器画面上方。
 * 设置（速度/字号/不透明度/显示区域/密度）与 userscript 的 DEFAULTS 对齐。
 */
@Composable
fun DanmakuOverlay(
    chatMessages: List<ChatMessage>,
    settings: ChatSettings,
    modifier: Modifier = Modifier
) {
    var lastSeenCount by remember { mutableIntStateOf(0) }
    data class Flying(
        val key: String,
        val text: String,
        val lane: Int,
        val startedAt: Long
    )
    val flying = remember { mutableStateListOf<Flying>() }
    val laneLastStart = remember { IntArray(16) { Int.MIN_VALUE } }
    val density = LocalDensity.current

    val travelMs = (settings.danmakuSpeed * 1000L)
    val gapFactor = when (settings.danmakuDensity) {
        "more" -> DENSITY_MORE_GAP_FACTOR
        "overlap" -> DENSITY_OVERLAP_GAP_FACTOR
        else -> DENSITY_NORMAL_GAP_FACTOR
    }
    val laneCount = remember(settings.danmakuArea, settings.danmakuSize) {
        // 行高 = 字号 × 1.5，轨道数由显示区域决定
        ((settings.danmakuArea / 100f) * 720f / (settings.danmakuSize * 1.5f)).toInt().coerceIn(2, 16)
    }

    LaunchedEffect(chatMessages) {
        if (chatMessages.size < lastSeenCount) {
            lastSeenCount = chatMessages.size
            flying.clear()
        }
        if (chatMessages.size > lastSeenCount) {
            val now = System.currentTimeMillis()
            for (m in chatMessages.subList(lastSeenCount, chatMessages.size)) {
                val text = danmakuText(m.content)
                if (text.isEmpty() || m.isPending) continue
                var lane = 0
                var oldest = Long.MAX_VALUE
                for (i in 0 until laneCount) {
                    val last = laneLastStart[i].toLong()
                    if (now - last >= (travelMs * gapFactor).toInt()) { lane = i; break }
                    if (last < oldest) { oldest = last; lane = i }
                }
                laneLastStart[lane] = now.toInt()
                flying.add(Flying(m.id, text, lane, now))
            }
            lastSeenCount = chatMessages.size
            while (flying.size > MAX_TRACKED) flying.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            flying.removeAll { now - it.startedAt > travelMs + 500 }
        }
    }

    if (flying.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val fontSizeSp = settings.danmakuSize * settings.textScale
        val laneHeightPx = with(density) { (fontSizeSp * 1.5f).sp.toPx() }

        flying.forEach { item ->
            val progress = remember(item.key + travelMs) { Animatable(0f) }
            LaunchedEffect(item.key + travelMs) {
                progress.animateTo(1f, tween(durationMillis = travelMs.toInt(), easing = LinearEasing))
            }
            val x = (widthPx - progress.value * (widthPx * 2f)).roundToInt()
            Text(
                text = item.text,
                color = Color.White.copy(alpha = settings.danmakuOpacity),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
                ),
                modifier = Modifier
                    .offset { IntOffset(x, (laneHeightPx * item.lane).roundToInt()) }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

private fun <T> mutableStateOf(initial: T) = androidx.compose.runtime.mutableStateOf(initial)
