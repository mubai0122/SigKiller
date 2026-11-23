/*
 * Project:    BaiSigKiller
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/SigKiller
 * Website:    https://sigkiller.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package bai.sigkiller.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bai.sigkiller.data.update.Release
import bai.sigkiller.ui.common.HLine
import bai.sigkiller.ui.common.HSpace
import bai.sigkiller.ui.common.MetaChip
import bai.sigkiller.ui.common.PrimaryButton
import bai.sigkiller.ui.common.Sk
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Update

/** 发现新版本时的弹窗。force=true 时只留「去更新」，点外部/返回都不关闭。 */
@Composable
fun UpdateDialog(
    release: Release,
    force: Boolean,
    currentVersionName: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onHistory: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!force) onDismiss() },
        containerColor = Sk.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    MiuixIcons.Update,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Sk.Accent,
                )
                HSpace(8.dp)
                Text("发现新版本", color = Sk.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(
                        text = release.displayVersion,
                        color = Sk.AccentDark,
                        background = Sk.AccentSoft,
                    )
                    HSpace(6.dp)
                    MetaChip("当前 v$currentVersionName")
                    if (force) {
                        HSpace(6.dp)
                        MetaChip("强制更新", color = Sk.Danger, background = Color(0xFFFDECEC))
                    }
                }
                if (release.publishedAt.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        release.publishedAt.replace('T', ' '),
                        fontSize = 11.sp,
                        color = Sk.Faint,
                    )
                }

                Spacer(Modifier.height(14.dp))
                HLine()
                Spacer(Modifier.height(12.dp))
                Text("更新内容", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Sk.Muted)

                val lines = release.changelog.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("（本次更新未填写说明）", fontSize = 12.5.sp, color = Sk.Faint)
                } else {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("•", fontSize = 12.5.sp, color = Sk.Accent, modifier = Modifier.padding(end = 6.dp))
                                Text(
                                    line.removePrefix("•").trim(),
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = Sk.Text,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(text = "去下载", onClick = onDownload)
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onHistory) {
                    Text("历史版本", color = Sk.Accent)
                }
                if (!force) {
                    TextButton(onClick = onDismiss) {
                        Text("稍后", color = Sk.Muted)
                    }
                }
            }
        },
    )
}

/** 历史版本列表。当前版本高亮，比当前新的标注「新版」。 */
@Composable
fun UpdateHistoryDialog(
    history: List<Release>,
    onDismiss: () -> Unit,
    onDownload: (Release) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sk.Card,
        title = {
            Text("更新历史", color = Sk.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        text = {
            if (history.isEmpty()) {
                Text("暂无历史记录", fontSize = 13.sp, color = Sk.Muted)
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    history.forEach { item ->
                        HistoryItemCard(
                            release = item,
                            onDownload = { onDownload(item) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Sk.Accent)
            }
        },
    )
}

@Composable
private fun HistoryItemCard(release: Release, onDownload: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (release.isCurrent) Sk.AccentSoft else Sk.Soft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                release.displayVersion,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (release.isCurrent) Sk.AccentDark else Sk.Text,
            )
            HSpace(6.dp)
            if (release.isCurrent) {
                MetaChip("当前版本", color = Sk.AccentDark, background = Color.White)
            } else if (release.isNewer) {
                MetaChip("新版", color = Sk.AccentDark, background = Color.White)
            }
        }
        if (release.publishedAt.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                release.publishedAt.replace('T', ' '),
                fontSize = 10.5.sp,
                color = Sk.Faint,
            )
        }
        val lines = release.changelog.lines().map { it.trim() }
            .map { it.removePrefix("•").trim() }
            .filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text("• $line", fontSize = 11.5.sp, lineHeight = 16.sp, color = Sk.Muted)
            }
        }
        if (release.downloadUrl.isNotBlank() && !release.isCurrent) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDownload) {
                Text("下载此版本", color = Sk.Accent, fontSize = 12.sp)
            }
        }
    }
}
