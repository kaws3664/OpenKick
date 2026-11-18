package com.gallbladderz.openkick.features.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallbladderz.openkick.core.datastore.ChatSettings
import com.gallbladderz.openkick.core.domain.chat.ChatMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val LANE_COUNT = 6
private const val TRAVEL_MS = 9000
private const val MAX_TRACKED_MESSAGES = 200

/**
 * 弹幕覆盖层：把新到的聊天消息以右→左滚动的字幕画在播放器画面上方。
 * 参考 kick显示弹幕 userscript（greasyfork 600728）的交互形式。
 */
@Composable
fun DanmakuOverlay(
    chatMessages: List<ChatMessage>,
    chatSettings: ChatSettings,
    modifier: Modifier = Modifier
) {
    // 只对"新增"的尾部消息发射弹幕，避免列表刷新导致重放
    var lastSeenCount by remember { mutableStateOf(0) }
    data class Flying(
        val key: String,
        val text: String,
        val lane: Int,
        val startedAt: Long
    )
    val flying = remember { mutableStateListOf<Flying>() }
    val laneLastStart = remember { LongArray(LANE_COUNT) }

    LaunchedEffect(chatMessages) {
        if (chatMessages.size < lastSeenCount) {
            // 列表被清理（切换频道等），重置
            lastSeenCount = chatMessages.size
            flying.clear()
        }
        if (chatMessages.size > lastSeenCount) {
            val now = System.currentTimeMillis()
            for (m in chatMessages.subList(lastSeenCount, chatMessages.size)) {
                val text = m.content.trim()
                if (text.isEmpty() || m.isPending) continue
                // 选一条最空闲的轨道：轨道上最后一条已飞满 3 秒即视为可用
                var lane = 0
                var oldest = Long.MAX_VALUE
                for (i in 0 until LANE_COUNT) {
                    if (now - laneLastStart[i] >= TRAVEL_MS / 3) { lane = i; break }
                    if (laneLastStart[i] < oldest) { oldest = laneLastStart[i]; lane = i }
                }
                laneLastStart[lane] = now
                flying.add(Flying(m.id, text, lane, now))
            }
            lastSeenCount = chatMessages.size
            while (flying.size > MAX_TRACKED_MESSAGES) flying.removeAt(0)
        }
    }

    // 清理飞完的
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            flying.removeAll { now - it.startedAt > TRAVEL_MS + 500 }
        }
    }

    if (flying.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val fontSize = 14.sp * chatSettings.textScale
        val laneHeight = (fontSize.value * 1.6f).dp

        flying.forEach { item ->
            val progress = remember(item.key) { Animatable(0f) }
            LaunchedEffect(item.key) {
                progress.animateTo(
                    1f,
                    tween(durationMillis = TRAVEL_MS, easing = LinearEasing)
                )
            }
            val x = (widthPx - (progress.value * (widthPx * 2f))).roundToInt()
            Text(
                text = item.text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
                ),
                modifier = Modifier
                    .offset { IntOffset(x, 0) }
                    .padding(top = laneHeight * item.lane + 8.dp)
            )
        }
    }
}
