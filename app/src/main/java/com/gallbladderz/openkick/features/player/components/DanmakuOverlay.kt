package com.gallbladderz.openkick.features.player.components

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
 * - 轨道满时进入等待队列，腾出空位再发射，不互相压叠。
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

    val active = remember { mutableStateListOf<DanmakuItem>() }
    // 每条轨道上最后一条弹幕（用于碰撞检测）
    val laneLast = remember { arrayOfNulls<DanmakuItem>(16) }
    // 弹幕时间线：只在播放时推进
    var timelineMs by remember { mutableLongStateOf(0L) }
    var timelineTick by remember { mutableLongStateOf(0L) } // 触发重组用

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val travelMs = settings.danmakuSpeed * 1000L
    val fontSizeSp = settings.danmakuSize * settings.textScale
    val textStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.SemiBold,
        shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
    )

    // 时间线推进：暂停时不累积
    LaunchedEffect(isPlaying) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (isPlaying) {
                    if (lastNanos > 0) {
                        timelineMs += (now - lastNanos) / 1_000_000
                        timelineTick = timelineMs
                    }
                }
                lastNanos = now
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()

        fun laneHeightPx() = with(density) { (fontSizeSp * 1.5f).sp.toPx() }
        val laneCount = remember(settings.danmakuArea, settings.danmakuSize) {
            ((settings.danmakuArea / 100f) * 720f / (settings.danmakuSize * 1.5f)).toInt().coerceIn(2, 16)
        }

        // 修剪：飞出屏幕的移除并释放轨道
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

        // 碰撞检测：轨道上上一条的右缘已完全进入屏幕左界内才可上轨
        fun tryPlace(text: String, id: String, now: Long): Boolean {
            for (lane in 0 until laneCount) {
                val last = laneLast[lane]
                if (last == null) {
                    val w = with(density) {
                        textMeasurer.measure(text, textStyle, maxLines = 1).size.width.toFloat()
                    }
                    val item = DanmakuItem(id, text, lane, w, now)
                    laneLast[lane] = item
                    active.add(item)
                    return true
                }
                val lastX = screenW - (now - last.spawnedAt).toFloat() / travelMs * (screenW + last.widthPx)
                if (lastX + last.widthPx <= screenW) {
                    // 上一条已完全进入屏幕；再确认不与本条初始位置重叠
                    val w = with(density) {
                        textMeasurer.measure(text, textStyle, maxLines = 1).size.width.toFloat()
                    }
                    val item = DanmakuItem(id, text, lane, w, now)
                    laneLast[lane] = item
                    active.add(item)
                    return true
                }
            }
            return false
        }

        // 新消息 → 去重后入等待队列
        LaunchedEffect(chatMessages) {
            for (m in chatMessages.asReversed()) {
                if (m.id in seenSet || m.isPending) continue
                seenSet.add(m.id)
                seenIds.addLast(m.id)
                while (seenIds.size > MAX_SEEN) seenSet.remove(seenIds.removeFirst())
                val text = danmakuText(m.content)
                if (text.isNotEmpty() && pendingQueue.size < MAX_PENDING) {
                    pendingQueue.addLast(Pending(m.id, text))
                }
            }
        }

        // 每帧：修剪 + 从队列发射
        LaunchedEffect(timelineTick, chatMessages.size) {
            val now = timelineMs
            prune(now)
            while (pendingQueue.isNotEmpty() && tryPlace(pendingQueue.first().text, pendingQueue.first().id, now)) {
                pendingQueue.removeFirst()
            }
            while (active.size > MAX_ACTIVE) {
                val removed = active.removeAt(0)
                if (laneLast[removed.lane] === removed) laneLast[removed.lane] = null
            }
        }

        if (settings.danmakuDensity == "overlap") {
            // 重叠模式：不等待空位，直接在任意最旧轨道发射（用户明确要求时才牺牲可读性）
            LaunchedEffect(chatMessages.size) {
                val now = timelineMs
                prune(now)
                while (pendingQueue.isNotEmpty()) {
                    val p = pendingQueue.removeFirst()
                    val w = with(density) {
                        textMeasurer.measure(p.text, textStyle, maxLines = 1).size.width.toFloat()
                    }
                    var lane = 0
                    var oldest = Long.MAX_VALUE
                    for (i in 0 until laneCount) {
                        val last = laneLast[i]
                        val t = last?.spawnedAt ?: Long.MIN_VALUE
                        if (t < oldest) { oldest = t; lane = i }
                    }
                    val item = DanmakuItem(p.id, p.text, lane, w, now)
                    laneLast[lane] = item
                    active.add(item)
                }
            }
        }

        active.forEach { d ->
            val laneHeight = with(density) { (fontSizeSp * 1.5f).sp.toPx() }
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
