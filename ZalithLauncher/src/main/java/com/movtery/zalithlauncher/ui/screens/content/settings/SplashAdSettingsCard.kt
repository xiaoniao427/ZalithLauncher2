/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.TitleAndSummary
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.CardPosition
import com.movtery.zalithlauncher.ui.screens.content.settings.layouts.SettingsCard

/** 挽留对话框阶段 */
private sealed interface SplashAdDissuadePhase {
    /** 无对话框 */
    data object None : SplashAdDissuadePhase
    /** 第一次挽留：询问是否关闭展示广告 */
    data object FirstAsk : SplashAdDissuadePhase
    /** 第二次挽留：确认是否不准备关闭展示广告 */
    data object SecondAsk : SplashAdDissuadePhase
}

/** 灰色禁用样式的颜色 */
private val GrayTrack = Color(0xFF555555)
private val GrayThumb = Color(0xFF999999)

@Composable
fun SplashAdSettingsCard(
    modifier: Modifier = Modifier,
    position: CardPosition
) {
    val unit = AllSettings.showSplashAd
    val checked = unit.state

    // 三连击计数器
    var tapCount by remember { mutableIntStateOf(0) }

    // 挽留对话框阶段
    var dissuadePhase by remember { mutableStateOf<SplashAdDissuadePhase>(SplashAdDissuadePhase.None) }

    // 处理挽留对话框
    when (dissuadePhase) {
        is SplashAdDissuadePhase.FirstAsk -> {
            SimpleAlertDialog(
                title = stringResource(R.string.settings_splash_ad_dissuade_title1),
                text = stringResource(R.string.settings_splash_ad_dissuade_text1),
                confirmText = stringResource(R.string.settings_splash_ad_dissuade_confirm1),
                dismissText = stringResource(R.string.settings_splash_ad_dissuade_cancel1),
                onConfirm = {
                    // 用户仍然想关闭 → 进入第二次挽留
                    dissuadePhase = SplashAdDissuadePhase.SecondAsk
                },
                onDismiss = {
                    // 用户被挽留成功 → 保持开启，退出
                    dissuadePhase = SplashAdDissuadePhase.None
                    tapCount = 0
                }
            )
        }
        is SplashAdDissuadePhase.SecondAsk -> {
            SimpleAlertDialog(
                title = stringResource(R.string.settings_splash_ad_dissuade_title2),
                text = stringResource(R.string.settings_splash_ad_dissuade_text2),
                confirmText = stringResource(R.string.settings_splash_ad_dissuade_confirm2),
                dismissText = stringResource(R.string.settings_splash_ad_dissuade_cancel2),
                onConfirm = {
                    // 用户还是坚持要关闭 → 关闭开关
                    unit.save(false)
                    dissuadePhase = SplashAdDissuadePhase.None
                    tapCount = 0
                },
                onDismiss = {
                    // 用户被挽留成功 → 保持开启
                    dissuadePhase = SplashAdDissuadePhase.None
                    tapCount = 0
                }
            )
        }
        is SplashAdDissuadePhase.None -> { /* 无事发生 */ }
    }

    val isOn = checked

    SettingsCard(
        modifier = modifier,
        position = position,
        onClick = {
            if (isOn) {
                // 开关处于开启状态：看似灰色禁用，但连续点击三次触发挽留
                tapCount++
                if (tapCount >= 3) {
                    tapCount = 0
                    dissuadePhase = SplashAdDissuadePhase.FirstAsk
                }
            } else {
                // 开关处于关闭状态：正常操作，直接打开
                unit.save(true)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleAndSummary(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.settings_splash_ad_title),
                    summary = stringResource(if (isOn) R.string.settings_splash_ad_summary_on else R.string.settings_splash_ad_summary_off),
                    titleStyle = MaterialTheme.typography.titleSmall,
                    summaryStyle = MaterialTheme.typography.labelSmall
                )

                Switch(
                    checked = checked,
                    onCheckedChange = null, // 不直接响应，由卡片 onClick 控制
                    colors = if (isOn) {
                        SwitchDefaults.colors(
                            checkedThumbColor = GrayThumb,
                            checkedTrackColor = GrayTrack,
                            uncheckedThumbColor = GrayThumb,
                            uncheckedTrackColor = GrayTrack,
                        )
                    } else {
                        SwitchDefaults.colors()
                    },
                    enabled = false // 始终禁用原生开关交互
                )
            }
            // 关闭状态下显示提示文字
            if (!isOn) {
                Text(
                    text = stringResource(R.string.settings_splash_ad_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}