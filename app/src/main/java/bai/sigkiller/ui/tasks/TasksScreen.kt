/*
 * Project:    BaiSigKiller
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-22 01:43:09
 * Repository: https://github.com/mubai0122/SigKiller
 * Website:    https://sigkiller.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package bai.sigkiller.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bai.sigkiller.data.PatchTask
import bai.sigkiller.data.TaskStatus
import bai.sigkiller.ui.common.ApkActions
import bai.sigkiller.ui.common.Dot
import bai.sigkiller.ui.common.EmptyState
import bai.sigkiller.ui.common.HSpace
import bai.sigkiller.ui.common.IconBadge
import bai.sigkiller.ui.common.LinkButton
import bai.sigkiller.ui.common.MetaChip
import bai.sigkiller.ui.common.PageHeader
import bai.sigkiller.ui.common.Sk
import bai.sigkiller.ui.common.SkCard
import bai.sigkiller.ui.common.clickableNoRipple
import bai.sigkiller.ui.common.contentBottomInset
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Tasks
import kotlin.math.roundToInt

@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val bottomInset = contentBottomInset()
    val context = LocalContext.current
    var detailId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().background(Sk.Bg)) {
        PageHeader(
            title = "任务",
            subtitle = "点开任意一条看每一步的详细进度",
        ) {
            if (tasks.any { it.status != TaskStatus.RUNNING }) {
                LinkButton(text = "清空已完成", onClick = viewModel::clearFinished, color = Sk.Muted)
            }
        }

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = MiuixIcons.Tasks,
                    title = "还没有处理过应用",
                    description = "去「应用」页选一个应用，或者从文件里挑一个 APK，处理记录会出现在这里。",
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 2.dp,
                bottom = bottomInset + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(task = task, onClick = { detailId = task.id })
            }
        }
    }

    val detailTask = tasks.firstOrNull { it.id == detailId }
    if (detailTask != null) {
        val task = detailTask
        TaskDetailDialog(
            task = task,
            onDismiss = { detailId = null },
            onInstall = { task.outputPath?.let { ApkActions.install(context, it) } },
            onShare = { task.outputPath?.let { ApkActions.share(context, it) } },
            onDelete = {
                viewModel.remove(task.id)
                detailId = null
            },
        )
    }
}

@Composable
private fun TaskCard(task: PatchTask, onClick: () -> Unit) {
    SkCard(onClick = onClick, corner = 18.dp) {
        Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 13.dp, bottom = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = MiuixIcons.Tasks,
                    size = 36.dp,
                    corner = 12.dp,
                    tint = if (task.status == TaskStatus.FAILED) Sk.Danger else Sk.Accent,
                    background = if (task.status == TaskStatus.FAILED) Color(0xFFFDECEC) else Sk.AccentSoft,
                )
                HSpace(11.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.label,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Sk.Text,
                        maxLines = 1,
                    )
                    Text(
                        text = task.packageName.ifBlank { task.sourcePath.substringAfterLast('/') },
                        fontSize = 11.sp,
                        color = Sk.Muted,
                        maxLines = 1,
                    )
                }
                StatusChip(task)
                Icon(
                    MiuixIcons.ChevronForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Sk.Faint,
                )
            }

            Spacer(Modifier.height(11.dp))

            when (task.status) {
                TaskStatus.RUNNING -> {
                    ProgressBar(task.progress)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.stage.title,
                            fontSize = 11.5.sp,
                            color = Sk.Accent,
                            fontWeight = FontWeight.Medium,
                        )
                        HSpace(6.dp)
                        Text(
                            text = task.message,
                            fontSize = 11.5.sp,
                            color = Sk.Muted,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(task.progress * 100).roundToInt()}%", fontSize = 11.5.sp, color = Sk.Muted)
                    }
                }

                TaskStatus.DONE -> {
                    ProgressBar(1f)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetaChip(if (task.signed) "已签名" else "未签名", color = Sk.AccentDark, background = Sk.AccentSoft)
                        MetaChip("${task.sizeBytes / 1024} KB")
                        MetaChip(if (task.exportedPath != null) "已导出" else "仅私有目录")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = task.exportedPath ?: task.outputPath.orEmpty(),
                        fontSize = 10.5.sp,
                        color = Sk.Faint,
                        maxLines = 1,
                    )
                }

                TaskStatus.FAILED -> {
                    Text(
                        text = task.error.orEmpty(),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Sk.Danger,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "点击查看日志",
                        fontSize = 11.sp,
                        color = Sk.Faint,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        progress = progress.coerceIn(0f, 1f),
        colors = ProgressIndicatorDefaults.progressIndicatorColors(
            foregroundColor = Sk.Accent,
            disabledForegroundColor = Sk.Faint,
            backgroundColor = Sk.SoftDeep,
        ),
        height = 5.dp,
    )
}

@Composable
private fun StatusChip(task: PatchTask) {
    val (text, color, bg) = when (task.status) {
        TaskStatus.RUNNING -> Triple("处理中", Sk.AccentDark, Sk.AccentSoft)
        TaskStatus.DONE -> Triple("完成", Sk.AccentDark, Sk.AccentSoft)
        TaskStatus.FAILED -> Triple("失败", Sk.Danger, Color(0xFFFDECEC))
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (task.status == TaskStatus.RUNNING) {
                Dot(Sk.Accent, 5.dp)
                HSpace(4.dp)
            }
            Text(text, fontSize = 10.5.sp, color = color)
        }
    }
}
