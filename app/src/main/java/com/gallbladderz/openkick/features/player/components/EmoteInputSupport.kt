/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gallbladderz.openkick.features.player.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gallbladderz.openkick.R
import com.gallbladderz.openkick.core.domain.chat.ChatEmote
import com.gallbladderz.openkick.core.domain.chat.ChatEmoteParser
import com.gallbladderz.openkick.core.domain.chat.ChatEmoteSet
import com.gallbladderz.openkick.core.domain.chat.ChatToken

internal const val MAX_RECENT_EMOTES = 24
internal const val EMOTE_GRID_COLUMNS = 8

@Composable
internal fun ChatEmotePickerPanel(
    emoteSets: List<ChatEmoteSet>,
    recentEmotes: List<ChatEmote>,
    height: Dp,
    animateEmotes: Boolean,
    onEmoteSelected: (ChatEmote) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val panelHeight = height.coerceIn(240.dp, 420.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(colors.surface)
    ) {
        HorizontalDivider(color = colors.onSurface.copy(alpha = 0.08f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (recentEmotes.isNotEmpty()) {
                EmoteSection(
                    title = stringResource(R.string.chat_emoji_recent),
                    emotes = recentEmotes,
                    animateEmotes = animateEmotes,
                    onEmoteSelected = onEmoteSelected
                )
            }

            if (emoteSets.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_emoji_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            } else {
                emoteSets.forEach { set ->
                    EmoteSection(
                        title = set.name,
                        emotes = set.emotes,
                        animateEmotes = animateEmotes,
                        onEmoteSelected = onEmoteSelected
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmoteSection(
    title: String,
    emotes: List<ChatEmote>,
    animateEmotes: Boolean,
    onEmoteSelected: (ChatEmote) -> Unit
) {
    val context = LocalContext.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
    )
    emotes.chunked(EMOTE_GRID_COLUMNS).forEach { rowEmotes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            repeat(EMOTE_GRID_COLUMNS) { index ->
                val emote = rowEmotes.getOrNull(index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable(enabled = emote != null) {
                            emote?.let(onEmoteSelected)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    emote?.let {
                        AsyncImage(
                            model = kickEmoteImageRequest(
                                context = context,
                                emoteId = it.id,
                                animated = animateEmotes
                            ),
                            contentDescription = it.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

internal data class EmoteInputVisual(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

internal fun buildEmoteInputVisual(
    context: Context,
    source: String,
    animateEmotes: Boolean
): EmoteInputVisual {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        ChatEmoteParser.parseEmotes(source).forEachIndexed { index, token ->
            when (token) {
                is com.gallbladderz.openkick.core.domain.chat.ChatToken.Text -> append(token.text)
                is com.gallbladderz.openkick.core.domain.chat.ChatToken.Emote -> {
                    val inlineId = "input_emote_${index}_${token.emoteId}"
                    appendInlineContent(inlineId, token.emoteName)
                    inlineContent[inlineId] = InlineTextContent(
                        Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(
                            model = kickEmoteImageRequest(
                                context = context,
                                emoteId = token.emoteId,
                                animated = animateEmotes
                            ),
                            contentDescription = token.emoteName,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
    return EmoteInputVisual(annotated, inlineContent)
}

internal object EmoteTokenVisualTransformation : VisualTransformation {
    private val tokenRegex = Regex("\\[emote:(\\d+):([^\\]]+)\\]")

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginal = mutableListOf(0)
        val transformed = buildAnnotatedString {
            var originalIndex = 0
            var transformedIndex = 0
            tokenRegex.findAll(source).forEach { match ->
                while (originalIndex < match.range.first) {
                    originalToTransformed[originalIndex] = transformedIndex
                    append(source[originalIndex])
                    originalIndex++
                    transformedIndex++
                    transformedToOriginal += originalIndex
                }
                for (index in match.range) originalToTransformed[index] = transformedIndex
                append("🙂")
                transformedIndex++
                transformedToOriginal += match.range.last + 1
                originalIndex = match.range.last + 1
                originalToTransformed[originalIndex] = transformedIndex
            }
            while (originalIndex < source.length) {
                originalToTransformed[originalIndex] = transformedIndex
                append(source[originalIndex])
                originalIndex++
                transformedIndex++
                transformedToOriginal += originalIndex
            }
            originalToTransformed[source.length] = transformedIndex
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, source.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
        }
        return TransformedText(transformed, mapping)
    }
}

internal fun appendEmote(current: String, emote: ChatEmote): String {
    val token = "[emote:${emote.id}:${emote.name}]"
    return if (current.isBlank()) token else "$current $token"
}

internal val completeEmoteToken = Regex("\\[emote:\\d+:[^\\]]+\\]")
internal val incompleteEmoteToken = Regex("\\[emote:\\d+:[^\\]]*$")

internal fun normalizeEmoteEdit(previous: TextFieldValue, updated: TextFieldValue): TextFieldValue {
    if (updated.text.length >= previous.text.length || !completeEmoteToken.containsMatchIn(previous.text)) {
        return updated
    }

    val incomplete = incompleteEmoteToken.find(updated.text) ?: return updated
    val cleanedText = updated.text.removeRange(incomplete.range.first, updated.text.length)
    return updated.copy(
        text = cleanedText,
        selection = TextRange(cleanedText.length)
    )
}

