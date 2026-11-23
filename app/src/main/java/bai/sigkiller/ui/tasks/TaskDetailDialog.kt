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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bai.sigkiller.data.PatchTask
import bai.sigkiller.data.StageStep
import bai.sigkiller.data.StepState
import bai.sigkiller.engine.PatchStage
import bai.sigkiller.data.TaskStatus
import bai.sigkiller.ui.common.Dot
import bai.sigkiller.ui.common.HSpace
import bai.sigkiller.ui.common.HLine
import bai.sigkiller.ui.common.IconBadge
import bai.sigkiller.ui.common.LinkButton
import bai.sigkiller.ui.common.PrimaryButton
import bai.sigkiller.ui.common.Sk
import bai.sigkiller.ui.common.SkCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Tasks
import kotlin.math.roundToInt

/**
 * 任务详情：步骤条 + 实时日志 + 产物操作。
 *
 * 进度不是一根空转的进度条——每一阶段（解析 / 注入 dex / 改清单 / 嵌原包 /
 * 注 so / 打包 / 签名 / 校验）都有自己的状态，跑完的打勾、正在跑的转圈。
 */
@Composable
fun TaskDetailDialog(
    task: PatchTask,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val steps = remember(task.stage, task.status) { task.stageSteps() }
    val percent = (task.progress * 100).roundToInt()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SkCard(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 26.dp)
                .widthIn(max = 560.dp),
            corner = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // 头部：有应用图标就展示图标，否则回退到任务图标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.icon != null) {
                        Image(
                            bitmap = task.icon,
                            contentDescription = task.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)),
                        )
                    } else {
                        IconBadge(
                            icon = MiuixIcons.Tasks,
                            size = 40.dp,
                            corner = 13.dp,
                            tint = if (task.status == TaskStatus.FAILED) Sk.Danger else Sk.Accent,
                            background = if (task.status == TaskStatus.FAILED) Color(0xFFFDECEC) else Sk.AccentSoft,
                        )
                    }
                    HSpace(11.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = task.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Sk.Text,
                            maxLines = 1,
                        )
                        Text(
                            text = task.packageName.ifBlank { task.sourcePath.substringAfterLast('/') },
                            fontSize = 11.5.sp,
                            color = Sk.Muted,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Sk.Soft)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MiuixIcons.Close, contentDescription = "关闭", modifier = Modifier.size(14.dp), tint = Sk.Muted)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 总体进度
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = percent.coerceIn(0, 100).toString(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.status == TaskStatus.FAILED) Sk.Danger else Sk.Text,
                    )
                    Text("%", fontSize = 15.sp, color = Sk.Muted, modifier = Modifier.padding(bottom = 3.dp, start = 1.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = statusText(task),
                            fontSize = 12.sp,
                            color = if (task.status == TaskStatus.FAILED) Sk.Danger else Sk.AccentDark,
                        )
                        Text(
                            text = "耗时 ${formatDuration(task.elapsedMillis)}",
                            fontSize = 11.sp,
                            color = Sk.Faint,
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = if (task.status == TaskStatus.FAILED) task.progress else task.progress.coerceIn(0f, 1f),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = if (task.status == TaskStatus.FAILED) Sk.Danger else Sk.Accent,
                        disabledForegroundColor = Sk.Faint,
                        backgroundColor = Sk.SoftDeep,
                    ),
                    height = 6.dp,
                )

                if (task.status == TaskStatus.RUNNING) {
                    Spacer(Modifier.height(7.dp))
                    Text(task.message, fontSize = 11.5.sp, color = Sk.Muted)
                }

                // 打包阶段单独一条实时压缩进度：已写字节 / 总字节
                if (task.stage == PatchStage.REBUILD && task.totalBytes > 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sk.Soft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("压缩进度", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Sk.Muted)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${formatSize(task.packedBytes)} / ${formatSize(task.totalBytes)}",
                                    fontSize = 11.sp,
                                    color = Sk.AccentDark,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = (task.packedBytes.toFloat() / task.totalBytes).coerceIn(0f, 1f),
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Sk.Accent,
                                    disabledForegroundColor = Sk.Faint,
                                    backgroundColor = Sk.SoftDeep,
                                ),
                                height = 5.dp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HLine()
                Spacer(Modifier.height(14.dp))

                // 阶段清单
                Text("处理步骤", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Sk.Muted)
                Spacer(Modifier.height(9.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    steps.forEach { step -> StepRow(step) }
                }

                if (task.log.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("日志", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Sk.Muted)
                    Spacer(Modifier.height(7.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 168.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sk.Soft)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        task.log.forEach { line ->
                            Text(
                                text = line,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.contains("失败")) Sk.Danger else Sk.Muted,
                            )
                        }
                    }
                }

                if (task.status == TaskStatus.FAILED && !task.error.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFDECEC))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(task.error, fontSize = 12.sp, lineHeight = 18.sp, color = Sk.Danger)
                    }
                }

                if (task.status == TaskStatus.DONE) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sk.Soft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text("产物", fontSize = 11.sp, color = Sk.Muted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = task.outputPath.orEmpty(),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Sk.Text,
                        )
                        task.exportedPath?.let { exported ->
                            Spacer(Modifier.height(6.dp))
                            Text("公共目录", fontSize = 11.sp, color = Sk.Muted)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = exported,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Sk.AccentDark,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        TaskStatus.DONE -> {
                            PrimaryButton(
                                text = "安装",
                                onClick = onInstall,
                                modifier = Modifier.widthIn(min = 92.dp),
                            )
                            HSpace(6.dp)
                            ActionIcon(MiuixIcons.Share, "分享", onShare)
                        }

                        else -> {
                            LinkButton("删除任务", onDelete, color = Sk.Muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (task.status != TaskStatus.DONE) {
                        PrimaryButton(text = "知道了", onClick = onDismiss)
                    } else {
                        ActionIcon(MiuixIcons.Delete, "删除", onDelete, tint = Sk.Muted)
                        HSpace(4.dp)
                        LinkButton("关闭", onDismiss, color = Sk.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: StageStep) {
    val (tint, titleColor) = when (step.state) {
        StepState.DONE -> Sk.Accent to Sk.Text
        StepState.ACTIVE -> Sk.Accent to Sk.Text
        StepState.FAILED -> Sk.Danger to Sk.Danger
        StepState.PENDING -> Sk.Faint to Sk.Faint
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(17.dp).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = if (step.state == StepState.PENDING) 0.12f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            when (step.state) {
                StepState.DONE -> Icon(MiuixIcons.Ok, null, Modifier.size(10.dp), tint = Sk.Accent)
                StepState.ACTIVE -> Dot(Sk.Accent, 6.dp)
                StepState.FAILED -> Text("!", fontSize = 10.sp, color = Sk.Danger)
                StepState.PENDING -> Dot(Sk.Faint, 4.dp)
            }
        }
        HSpace(9.dp)
        Text(
            text = step.stage.title,
            fontSize = 12.5.sp,
            fontWeight = if (step.state == StepState.ACTIVE) FontWeight.Medium else FontWeight.Normal,
            color = titleColor,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = when (step.state) {
                StepState.DONE -> "完成"
                StepState.ACTIVE -> "进行中"
                StepState.FAILED -> "中断"
                StepState.PENDING -> "等待"
            },
            fontSize = 10.5.sp,
            color = if (step.state == StepState.PENDING) Sk.Faint else tint,
        )
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Sk.Muted,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Sk.Soft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(15.dp), tint = tint)
    }
}

private fun statusText(task: PatchTask): String = when (task.status) {
    TaskStatus.RUNNING -> "第 ${task.stage.ordinal + 1}/${PatchStage.entries.count { it != PatchStage.DONE }} 步 · ${task.stage.title}"
    TaskStatus.DONE -> if (task.signed) "已完成并签名" else "已完成（未签名）"
    TaskStatus.FAILED -> "处理失败"
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return if (totalSeconds < 60) {
        "${totalSeconds}s"
    } else {
        "${totalSeconds / 60}m ${totalSeconds % 60}s"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    val mb = bytes / (1024 * 1024).toDouble()
    return if (mb >= 100) "${mb.toInt()} MB" else String.format("%.1f MB", mb)
}
