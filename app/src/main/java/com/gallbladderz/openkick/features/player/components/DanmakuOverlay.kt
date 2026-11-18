package com.gallbladderz.openkick.features.player.components

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallbladderz.openkick.core.datastore.ChatSettings
import com.gallbladderz.openkick.core.domain.chat.ChatMessage
import kotlin.math.roundToInt

// 参考 PiliPlus/PiliNara 的 canvas_danmaku 引擎算法（Compose 移植）
private const val TAG = "Danmaku"
private const val MAX_SEEN = 5000        // userscript MAX_SEEN_MESSAGES
private const val MAX_PENDING = 120      // 轨道满时的等待队列上限
private const val MAX_ACTIVE = 180

private val EMOTE_TOKEN = Regex("\\[emote:\\d+:([^\\]]+)\\]")

/** Kick 的 emote 在 content 里是 [emote:ID:名字]，弹幕里显示成表情名字 */
private fun danmakuText(content: String): String =
    content.replace(EMOTE_TOKEN, "$1").trim()

private data class DanmakuItem(
    val id: String,
    val text: String,
    val lane: Int,
    val widthPx: Float,
    val spawnedAt: Long,   // 弹幕自身的时间线（毫秒，可暂停）
)

private data class Pending(val id: String, val text: String)

/**
 * 弹幕覆盖层（参考 PiliPlus/PiliNara 的 canvas_danmaku 引擎）：
 * - 轨道分配用真实文本宽度做碰撞检测：上一条弹幕的右缘完全进入屏幕后才允许新弹幕上轨；
 * - 播放器暂停时弹幕冻结（时间线只在实际播放时推进）；
 * - 轨道满时进入等待队列，腾出空位再发射，不互相压叠；
 * - 进入直播间时的历史消息只标记已见、不发射（对齐脚本"只处理新渲染行"的行为）。
 */
@Composable
fun DanmakuOverlay(
    chatMessages: List<ChatMessage>,
    settings: ChatSettings,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // 按消息 ID 去重（userscript MAX_SEEN_MESSAGES 方案）
    val seenIds = remember { ArrayDeque<String>() }
    val seenSet = remember { HashSet<String>() }
    val pendingQueue = remember { ArrayDeque<Pending>() }
    // 首次收到非空列表 = 进入直播间灌入的历史，只标记不发射
    var backlogConsumed by remember { mutableStateOf(false) }

    val active = remember { mutableStateListOf<DanmakuItem>() }
    val laneLast = remember { arrayOfNulls<DanmakuItem>(16) }
    // 弹幕时间线：只在播放时推进
    var timelineMs by remember { mutableLongStateOf(0L) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val isPlayingState = rememberUpdatedState(isPlaying)
    val travelMs = settings.danmakuSpeed * 1000L
    val fontSizeSp = settings.danmakuSize * settings.textScale
    val overlapMode = settings.danmakuDensity == "overlap"
    val laneCount = remember(settings.danmakuArea, settings.danmakuSize) {
        ((settings.danmakuArea / 100f) * 720f / (settings.danmakuSize * 1.5f)).toInt().coerceIn(2, 16)
    }
    val textStyle = remember(fontSizeSp) {
        TextStyle(
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val laneHeight = with(density) { (fontSizeSp * 1.5f).sp.toPx() }

        fun measure(text: String): Float =
            textMeasurer.measure(text, textStyle, maxLines = 1).size.width.toFloat()

        fun addToLane(lane: Int, p: Pending, w: Float, now: Long) {
            val item = DanmakuItem(p.id, p.text, lane, w, now)
            laneLast[lane] = item
            active.add(item)
            Log.d(TAG, "spawn lane=$lane active=${active.size} queue=${pendingQueue.size} text=${p.text.take(12)}")
        }

        /** 碰撞检测：轨道上上一条的右缘完全进入屏幕内才可上轨 */
        fun tryPlace(p: Pending, now: Long): Boolean {
            val w = measure(p.text)
            for (i in 0 until laneCount) {
                val last = laneLast[i] ?: return true.also { addToLane(i, p, w, now) }
                val lastX = screenW - (now - last.spawnedAt).toFloat() / travelMs * (screenW + last.widthPx)
                if (lastX + last.widthPx <= screenW) {
                    addToLane(i, p, w, now)
                    return true
                }
            }
            return false
        }

        /** 重叠模式：选最旧的轨道直接发射（用户明确选择时才牺牲可读性） */
        fun placeOverlap(p: Pending, now: Long) {
            val w = measure(p.text)
            var lane = 0
            var oldest = Long.MAX_VALUE
            for (i in 0 until laneCount) {
                val t = laneLast[i]?.spawnedAt ?: Long.MIN_VALUE
                if (t < oldest) { oldest = t; lane = i }
            }
            addToLane(lane, p, w, now)
        }

        fun prune(now: Long) {
            val it = active.iterator()
            while (it.hasNext()) {
                val d = it.next()
                val x = screenW - (now - d.spawnedAt).toFloat() / travelMs * (screenW + d.widthPx)
                if (x <= -d.widthPx) {
                    it.remove()
                    if (laneLast[d.lane] === d) laneLast[d.lane] = null
                }
            }
        }

        fun drain(now: Long) {
            if (overlapMode) {
                while (pendingQueue.isNotEmpty()) {
                    placeOverlap(pendingQueue.removeFirst(), now)
                }
            } else {
                while (pendingQueue.isNotEmpty() && tryPlace(pendingQueue.first(), now)) {
                    pendingQueue.removeFirst()
                }
            }
            while (active.size > MAX_ACTIVE) {
                val removed = active.removeAt(0)
                if (laneLast[removed.lane] === removed) laneLast[removed.lane] = null
            }
        }

        // 单一帧循环（对齐 canvas_danmaku 的每帧 tick）：时间线推进 + 修剪 + 队列发射
        LaunchedEffect(Unit) {
            var lastNanos = -1L
            while (true) {
                withFrameNanos { now ->
                    if (isPlayingState.value && lastNanos > 0) {
                        timelineMs += (now - lastNanos) / 1_000_000
                    }
                    lastNanos = now
                }
                prune(timelineMs)
                drain(timelineMs)
            }
        }

        // 新消息 → 去重后入等待队列；首次非空列表视为历史，只标记不发射
        LaunchedEffect(chatMessages) {
            if (chatMessages.isEmpty()) return@LaunchedEffect
            val isBacklog = !backlogConsumed
            backlogConsumed = true
            for (m in chatMessages.asReversed()) {
                if (m.id in seenSet || m.isPending) continue
                seenSet.add(m.id)
                seenIds.addLast(m.id)
                while (seenIds.size > MAX_SEEN) seenSet.remove(seenIds.removeFirst())
                val text = danmakuText(m.content)
                if (text.isEmpty()) continue
                if (isBacklog) continue // 历史：只标记
                if (pendingQueue.size < MAX_PENDING) pendingQueue.addLast(Pending(m.id, text))
            }
            Log.d(TAG, "chat update size=${chatMessages.size} backlog=$isBacklog queue=${pendingQueue.size} seen=${seenSet.size}")
        }

        active.forEach { d ->
            val x = (screenW - (timelineMs - d.spawnedAt).toFloat() / travelMs * (screenW + d.widthPx)).roundToInt()
            Text(
                text = d.text,
                color = Color.White.copy(alpha = settings.danmakuOpacity),
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset {
                    IntOffset(x, (laneHeight * d.lane + with(density) { 8.dp.toPx() }).roundToInt())
                }
            )
        }
    }
}
