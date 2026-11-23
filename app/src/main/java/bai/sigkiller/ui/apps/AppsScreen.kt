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

package bai.sigkiller.ui.apps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bai.sigkiller.R
import bai.sigkiller.data.InstalledApp
import bai.sigkiller.data.PatchSource
import bai.sigkiller.ui.common.Dot
import bai.sigkiller.ui.common.EmptyState
import bai.sigkiller.ui.common.HSpace
import bai.sigkiller.ui.common.IconBadge
import bai.sigkiller.ui.common.LinkButton
import bai.sigkiller.ui.common.MetaChip
import bai.sigkiller.ui.common.PageHeader
import bai.sigkiller.ui.common.PrimaryButton
import bai.sigkiller.ui.common.Segmented
import bai.sigkiller.ui.common.Sk
import bai.sigkiller.ui.common.SkCard
import bai.sigkiller.ui.common.clickableNoRipple
import bai.sigkiller.ui.common.contentBottomInset
import bai.sigkiller.ui.dialog.KillerConfigDialog
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Search

@Composable
fun AppsScreen(
    onOpenSource: () -> Unit,
    onOpenTasks: () -> Unit,
    sharedApk: android.net.Uri? = null,
    onSharedApkHandled: () -> Unit = {},
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystem.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val dialogVisible by viewModel.dialogVisible.collectAsStateWithLifecycle()
    val inject by viewModel.inject.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val bottomInset = contentBottomInset()

    // 别的应用「分享」过来的 APK：当成一次文件选择，处理完就通知上层清掉
    LaunchedEffect(sharedApk) {
        sharedApk?.let { uri ->
            viewModel.importFile(uri)
            onSharedApkHandled()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // 有些文件管理器报 octet-stream，mime 过滤会漏，统一放开再自己判断
        uri?.let(viewModel::importFile)
    }

    Box(Modifier.fillMaxSize().background(Sk.Bg)) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = "去除签名校验",
                subtitle = "选应用或挑一个 APK，注入过签组件后重新签名",
            ) {
                IconAction(onClick = onOpenSource) {
                    Image(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = "开源仓库",
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                // miuix 的装饰盒用 Arrangement.Start，图标与文字之间没有间距：
                // 横向内边距加大，并在两个 icon 上补 padding，文字才不会贴着图标。
                insideMargin = androidx.compose.ui.unit.DpSize(16.dp, 12.dp),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Sk.Soft,
                    labelColor = Sk.Muted,
                    borderColor = Color.Transparent,
                ),
                cornerRadius = 14.dp,
                label = "搜索应用名或包名",
                leadingIcon = {
                    Icon(
                        MiuixIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 10.dp).size(17.dp),
                        tint = Sk.Faint,
                    )
                },
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        Icon(
                            MiuixIcons.Clear,
                            contentDescription = "清空",
                            modifier = Modifier.padding(start = 8.dp).size(17.dp).clickableNoRipple { viewModel.setQuery("") },
                            tint = Sk.Faint,
                        )
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            Segmented(
                options = listOf("已安装", "含系统应用"),
                selectedIndex = if (includeSystem) 1 else 0,
                onSelect = { viewModel.setIncludeSystem(it == 1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            message?.let { text ->
                NoticeBar(
                    text = text,
                    onDismiss = viewModel::dismissMessage,
                    actionLabel = "查看进度",
                    onAction = onOpenTasks,
                )
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 2.dp,
                    bottom = bottomInset + if (selected != null) 78.dp else 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "pick-file") {
                    FilePickCard(onClick = { picker.launch(arrayOf("*/*")) })
                }

                when {
                    loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                            InfiniteProgressIndicator(
                                color = Sk.Accent,
                                size = 28.dp,
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }

                    apps.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            icon = MiuixIcons.File,
                            title = if (query.isBlank()) "没有可处理的应用" else "没有匹配「$query」的应用",
                            description = "可以直接从文件里挑一个 APK，不限于已安装的应用。",
                            action = { LinkButton("从文件选择", onClick = { picker.launch(arrayOf("*/*")) }) },
                        )
                    }

                    else -> items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            selected = selected?.apkPath == app.apkPath,
                            onClick = { viewModel.select(app) },
                            onRun = { viewModel.startWith(app) },
                        )
                    }
                }
            }
        }

        if (selected != null) {
            SelectionBar(
                source = selected!!,
                onRun = viewModel::openDialog,
                onClear = viewModel::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 10.dp),
            )
        }

        if (importing) {
            Box(
                Modifier.fillMaxSize().background(Color(0x1A000000)),
                contentAlignment = Alignment.Center,
            ) {
                SkCard(modifier = Modifier.widthIn(max = 250.dp), corner = 18.dp) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfiniteProgressIndicator(
                            color = Sk.Accent,
                            size = 22.dp,
                            strokeWidth = 2.5.dp,
                        )
                        HSpace(12.dp)
                        Text("正在读取 APK…", fontSize = 13.sp, color = Sk.Text)
                    }
                }
            }
        }
    }

    if (dialogVisible) {
        KillerConfigDialog(
            initial = inject,
            title = "去除签名校验",
            confirmText = "确定",
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::confirm,
            onOpenSource = onOpenSource,
        )
    }
}

@Composable
private fun IconAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun NoticeBar(
    text: String,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Sk.AccentSoft)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(Sk.Accent, 6.dp)
        HSpace(8.dp)
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Sk.AccentDark,
            modifier = Modifier.weight(1f).clickableNoRipple(onDismiss),
        )
        if (actionLabel != null && onAction != null) {
            LinkButton(text = actionLabel, onClick = onAction, color = Sk.AccentDark)
        }
    }
}

/** 列表最上面的入口：从文件管理器挑 APK，绕开「必须已安装」这一条。 */
@Composable
private fun FilePickCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Sk.AccentSoft.copy(alpha = 0.7f))
            .border(1.dp, Sk.Accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = MiuixIcons.Folder, size = 40.dp, corner = 13.dp, background = Color.White)
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            Text("从文件选择 APK", fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Sk.Text)
            Text(
                text = "不限于已安装应用，挑本地安装包直接处理",
                fontSize = 11.5.sp,
                color = Sk.Muted,
                maxLines = 1,
            )
        }
        Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = Sk.Accent)
    }
}

@Composable
private fun SelectionBar(
    source: PatchSource,
    onRun: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkCard(
        modifier = modifier,
        corner = 20.dp,
        borderColor = Sk.Accent.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (source.fromFile) MiuixIcons.File else MiuixIcons.Blocklist,
                size = 38.dp,
                corner = 12.dp,
            )
            HSpace(11.dp)
            Column(Modifier.weight(1f)) {
                Text(source.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Sk.Text, maxLines = 1)
                Text(
                    text = source.displayPackage,
                    fontSize = 11.sp,
                    color = Sk.Muted,
                    maxLines = 1,
                )
            }
            HSpace(8.dp)
            PrimaryButton(text = "一键去签", onClick = onRun)
            HSpace(4.dp)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickableNoRipple(onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MiuixIcons.Close, contentDescription = "取消选择", modifier = Modifier.size(13.dp), tint = Sk.Faint)
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    selected: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
) {
    SkCard(
        onClick = onClick,
        corner = 18.dp,
        borderColor = if (selected) Sk.Accent.copy(alpha = 0.55f) else Sk.Line,
        containerColor = if (selected) Sk.AccentSoft.copy(alpha = 0.45f) else Sk.Card,
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, 42.dp)
            HSpace(11.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Sk.Text,
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = Sk.Muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MetaChip("v${app.versionName}")
                    MetaChip("${app.apkSize / 1024 / 1024} MB")
                    if (app.signatureMd5.isNotBlank()) {
                        MetaChip("签名 ${app.signatureMd5.take(6)}")
                    }
                }
            }
            LinkButton("去签", onRun)
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp, size: Dp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(Sk.Soft),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.label.take(1), fontSize = 15.sp, color = Sk.Muted)
        }
    }
}
