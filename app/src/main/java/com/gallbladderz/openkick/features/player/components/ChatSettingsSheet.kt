/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */



package com.gallbladderz.openkick.features.player.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.gallbladderz.openkick.R
import com.gallbladderz.openkick.core.datastore.ChatSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsSheet(
    settings: ChatSettings,
    onSettingsChange: (ChatSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = true,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.chat_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.back))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.chat_readability),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_sample_letter),
                    style = MaterialTheme.typography.labelSmall
                )
                Slider(
                    value = settings.textScale,
                    onValueChange = { onSettingsChange(settings.copy(textScale = it)) },
                    valueRange = 0.85f..1.3f,
                    steps = 8,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                Text(
                    stringResource(R.string.chat_sample_letter),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = stringResource(
                    R.string.chat_text_size_value,
                    (settings.textScale * 100).toInt()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ChatPreview(
                settings = settings,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = stringResource(R.string.chat_badges_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )

            ChatSettingSwitch(
                title = stringResource(R.string.chat_global_badges),
                description = stringResource(R.string.chat_global_badges_desc),
                checked = settings.showGlobalBadges,
                onCheckedChange = { onSettingsChange(settings.copy(showGlobalBadges = it)) }
            )
            ChatSettingSwitch(
                title = stringResource(R.string.chat_channel_badges),
                description = stringResource(R.string.chat_channel_badges_desc),
                checked = settings.showChannelBadges,
                onCheckedChange = { onSettingsChange(settings.copy(showChannelBadges = it)) }
            )
            ChatSettingSwitch(
                title = stringResource(R.string.chat_use_message_name_colors),
                description = stringResource(R.string.chat_use_message_name_colors_desc),
                checked = settings.useOfficialNameColor,
                onCheckedChange = { onSettingsChange(settings.copy(useOfficialNameColor = it)) }
            )
            ChatSettingSwitch(
                title = stringResource(R.string.chat_animated_emotes),
                description = stringResource(R.string.chat_animated_emotes_desc),
                checked = settings.animatedEmotes,
                onCheckedChange = { onSettingsChange(settings.copy(animatedEmotes = it)) }
            )
            ChatSettingSwitch(
                title = stringResource(R.string.chat_danmaku),
                description = stringResource(R.string.chat_danmaku_desc),
                checked = settings.danmakuEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(danmakuEnabled = it)) }
            )

            if (settings.danmakuEnabled) {
                Text(
                    text = stringResource(R.string.chat_danmaku_speed, settings.danmakuSpeed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.danmakuSpeed.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(danmakuSpeed = it.roundToInt())) },
                    valueRange = 4f..20f,
                    steps = 15
                )
                Text(
                    text = stringResource(R.string.chat_danmaku_size, settings.danmakuSize),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.danmakuSize.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(danmakuSize = it.roundToInt())) },
                    valueRange = 14f..38f,
                    steps = 23
                )
                Text(
                    text = stringResource(
                        R.string.chat_danmaku_opacity,
                        (settings.danmakuOpacity * 100).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.danmakuOpacity,
                    onValueChange = { onSettingsChange(settings.copy(danmakuOpacity = it)) },
                    valueRange = 0.2f..1f,
                    steps = 15
                )
                Text(
                    text = stringResource(R.string.chat_danmaku_area, settings.danmakuArea),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.danmakuArea.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(danmakuArea = it.roundToInt())) },
                    valueRange = 25f..100f,
                    steps = 2
                )
                Text(
                    text = stringResource(R.string.chat_danmaku_density),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "normal" to stringResource(R.string.chat_danmaku_density_normal),
                        "more" to stringResource(R.string.chat_danmaku_density_more),
                        "overlap" to stringResource(R.string.chat_danmaku_density_overlap)
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = settings.danmakuDensity == value,
                            onClick = { onSettingsChange(settings.copy(danmakuDensity = value)) },
                            label = { Text(label) }
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun ChatPreview(settings: ChatSettings, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_preview_author),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14f * settings.textScale).sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.chat_preview_message),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14f * settings.textScale).sp)
        )
    }
}

@Composable
private fun ChatSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
