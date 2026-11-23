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

package bai.sigkiller.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bai.sigkiller.core.ui.theme.BottomBarStyle
import bai.sigkiller.core.ui.theme.ThemeMode
import bai.sigkiller.core.ui.theme.ThemeViewModel
import bai.sigkiller.core.ui.theme.UiStyle
import bai.sigkiller.patch.PatchRunner
import bai.sigkiller.ui.common.HLine
import bai.sigkiller.ui.common.HSpace
import bai.sigkiller.ui.common.MetaChip
import bai.sigkiller.ui.common.PageHeader
import bai.sigkiller.ui.common.Sk
import bai.sigkiller.ui.common.SkCard
import bai.sigkiller.ui.common.clickableNoRipple
import bai.sigkiller.ui.common.contentBottomInset
import bai.sigkiller.ui.dialog.KillerConfigDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
fun SettingsScreen(
    onOpenSource: () -> Unit,
    onOpenWebsite: () -> Unit,
    updateViewModel: bai.sigkiller.ui.update.UpdateViewModel,
    viewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val inject by viewModel.inject.collectAsStateWithLifecycle()
    val saveToPublic by viewModel.saveToPublic.collectAsStateWithLifecycle()
    val customKeystore by viewModel.customKeystore.collectAsStateWithLifecycle()
    val mode by themeViewModel.mode.collectAsStateWithLifecycle()
    val barStyle by themeViewModel.bottomBarStyle.collectAsStateWithLifecycle()
    val uiStyle by themeViewModel.uiStyle.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val bottomInset = contentBottomInset()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showInject by remember { mutableStateOf(false) }
    var showSigning by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showBar by remember { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Android 9 及以下写公共目录要存储权限，其余版本走 MediaStore 不需要
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setSaveToPublic(granted)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Sk.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            title = "设置",
            subtitle = "默认注入参数、签名密钥与外观",
        )

        SectionLabel("注入")
        SkCard(modifier = Modifier.padding(horizontal = 20.dp), corner = 18.dp) {
            SettingRow(
                title = "默认注入配置",
                summary = "${inject.injectedClass} · ${if (inject.useOriginalApk) "原包过签" else "仅替换签名"}",
                onClick = { showInject = true },
            )
            HLine(Modifier.padding(start = 16.dp))
            SettingRow(
                title = "签名密钥",
                summary = if (customKeystore == null) {
                    "内置密钥 sigkiller.p12（口令 ${PatchRunner.BUILTIN_PASSWORD}）"
                } else {
                    "自定义 keystore"
                },
                onClick = { showSigning = true },
            )
            HLine(Modifier.padding(start = 16.dp))
            SwitchRow(
                title = "导出到公共目录",
                summary = if (saveToPublic) {
                    "Download/${PatchRunner.PUBLIC_DIR}，卸载应用也不丢"
                } else {
                    "只留在应用私有目录"
                },
                checked = saveToPublic,
                onCheckedChange = { wanted ->
                    val needPermission = wanted &&
                        android.os.Build.VERSION.SDK_INT < 29 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (needPermission) {
                        storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.setSaveToPublic(wanted)
                    }
                },
            )
        }

        SectionLabel("外观")
        SkCard(modifier = Modifier.padding(horizontal = 20.dp), corner = 18.dp) {
            SettingRow("主题", mode.label(), onClick = { showTheme = true })
            HLine(Modifier.padding(start = 16.dp))
            SettingRow(
                title = "底栏样式",
                summary = if (android.os.Build.VERSION.SDK_INT < 33) {
                    barStyle.label() + " · 系统低于 Android 13 时液态玻璃会回退为悬浮底栏"
                } else {
                    barStyle.label()
                },
                onClick = { showBar = true },
            )
            HLine(Modifier.padding(start = 16.dp))
            SettingRow("界面样式", uiStyle.label(), onClick = { showUi = true })
        }

        SectionLabel("关于")
        SkCard(modifier = Modifier.padding(horizontal = 20.dp), corner = 18.dp) {
            SettingRow(
                title = "检查更新",
                summary = if (updateState.checking) {
                    "正在检查…"
                } else {
                    "当前 v${updateViewModel.currentVersionName} · 检查是否有新版本"
                },
                onClick = { updateViewModel.checkManually() },
            )
            HLine(Modifier.padding(start = 16.dp))
            SettingRow("更新历史", "查看各版本更新内容", onClick = { updateViewModel.loadHistory() })
            HLine(Modifier.padding(start = 16.dp))
            SettingRow("原理与依赖", "注入流程、签名方式、开源引用", onClick = { showAbout = true })
            HLine(Modifier.padding(start = 16.dp))
            SettingRow("打开开源仓库", "github.com/mubai0122/SigKiller", onClick = onOpenSource)
            HLine(Modifier.padding(start = 16.dp))
            SettingRow("访问官网", "sigkiller.baiccb.cn", onClick = onOpenWebsite)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "bai.sigkiller · v${updateViewModel.currentVersionName} (${updateViewModel.currentVersionCode})",
            fontSize = 11.sp,
            color = Sk.Faint,
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomInset + 26.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (showInject) {
        KillerConfigDialog(
            initial = inject,
            title = "默认注入配置",
            confirmText = "保存",
            onDismiss = { showInject = false },
            onConfirm = {
                viewModel.saveInject(it)
                showInject = false
            },
            onOpenSource = onOpenSource,
        )
    }

    if (showSigning) {
        KeystoreDialog(
            usingCustom = customKeystore != null,
            onDismiss = { showSigning = false },
            onPick = { uri, password ->
                viewModel.importKeystore(uri, password)
                showSigning = false
            },
            onUseBuiltin = {
                viewModel.useBuiltinKeystore()
                showSigning = false
            },
        )
    }

    if (showTheme) {
        OptionDialog(
            title = "主题",
            options = ThemeMode.entries.map { it.label() to it },
            selected = mode,
            onDismiss = { showTheme = false },
            onSelect = {
                themeViewModel.set(it)
                showTheme = false
            },
        )
    }

    if (showBar) {
        OptionDialog(
            title = "底栏样式",
            options = BottomBarStyle.entries.map { it.label() to it },
            selected = barStyle,
            onDismiss = { showBar = false },
            onSelect = { style ->
                // 液态玻璃依赖 RuntimeShader（API 33+），低版本直接落回悬浮底栏
                val safe = if (style == BottomBarStyle.LIQUID_GLASS && android.os.Build.VERSION.SDK_INT < 33) {
                    BottomBarStyle.FLOATING
                } else {
                    style
                }
                themeViewModel.setBottomBarStyle(safe)
                showBar = false
            },
        )
    }

    if (showUi) {
        OptionDialog(
            title = "界面样式",
            options = UiStyle.entries.map { it.label() to it },
            selected = uiStyle,
            onDismiss = { showUi = false },
            onSelect = {
                themeViewModel.setUiStyle(it)
                showUi = false
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = Sk.Card,
            title = { Text("原理与依赖", color = Sk.Text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "注入一个 ContentProvider 到目标应用的 AndroidManifest.xml（目标已有 Application 时）或者接管 " +
                            "android:name（目标没有 Application 时），让过签代码在 Application.onCreate 之前跑起来；" +
                            "运行时替换 PackageInfo.CREATOR，把签名换成原包签名；开启「使用原包过签」时再把原始 APK 释放到" +
                            "私有目录，并用 xhook 接管 open/openat，让按文件读取签名的校验也读到原包。",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Sk.Muted,
                    )
                    HLine()
                    Text(
                        "dex 注入用 dexlib2，签名用 apksig（v1 + v2 + v3），清单与 resources.arsc 是二进制层面的手术式改写，不重新编译。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Sk.Muted,
                    )
                    Text(
                        "参考实现：ApkSignatureKillerEx（L-JINBIN）、miuix（yukonga）、AndroidLiquidGlass（Kyant0）。" +
                            "仅用于应用安全性测试。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = Sk.Faint,
                    )
                }
            },
        confirmButton = {
            TextButton(onClick = { showAbout = false }) {
                Text("知道了", color = Sk.Accent)
            }
        },
    )

}
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Sk.Muted,
        modifier = Modifier.padding(start = 26.dp, top = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, color = Sk.Text)
            if (summary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 11.5.sp, lineHeight = 16.sp, color = Sk.Muted)
            }
        }
        HSpace(8.dp)
        Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(15.dp), tint = Sk.Faint)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, color = Sk.Text)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 11.5.sp, lineHeight = 16.sp, color = Sk.Muted)
        }
        HSpace(8.dp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors(),
        )
    }
}

@Composable
private fun switchColors() = SwitchDefaults.switchColors(
    checkedThumbColor = Color.White,
    uncheckedThumbColor = Color.White,
    disabledCheckedThumbColor = Color(0xFFF2F2F2),
    disabledUncheckedThumbColor = Color(0xFFF2F2F2),
    checkedTrackColor = Sk.Accent,
    uncheckedTrackColor = Color(0xFFD6DAE0),
    disabledCheckedTrackColor = Sk.Accent.copy(alpha = 0.4f),
    disabledUncheckedTrackColor = Color(0xFFE6E8EB),
)

@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sk.Card,
        title = { Text(title, color = Sk.Text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                options.forEach { (label, value) ->
                    val active = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Sk.AccentSoft else Color.Transparent)
                            .clickableNoRipple { onSelect(value) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = if (active) Sk.AccentDark else Sk.Text,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) MetaChip("当前", color = Sk.AccentDark, background = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Sk.Accent) }
        },
    )
}

@Composable
private fun KeystoreDialog(
    usingCustom: Boolean,
    onDismiss: () -> Unit,
    onPick: (android.net.Uri, String) -> Unit,
    onUseBuiltin: () -> Unit,
) {
    var password by remember { mutableStateOf("123456") }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Sk.Card,
        title = { Text("签名密钥", color = Sk.Text, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    text = if (usingCustom) {
                        "当前使用自定义 keystore，产物用它签名。"
                    } else {
                        "当前使用内置密钥（sigkiller.p12，口令 123456）。换成自己的可以从根上区分签名。"
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = Sk.Muted,
                )
                Spacer(Modifier.height(12.dp))
                Text("keystore 口令", fontSize = 12.5.sp, color = Sk.Muted)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Sk.Soft)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = Sk.Text),
                        cursorBrush = SolidColor(Sk.Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = pendingUri?.lastPathSegment ?: "未选择文件（支持 .jks / .p12 / .bks）",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (pendingUri == null) Sk.Faint else Sk.AccentDark,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pendingUri?.let { onPick(it, password) } ?: onUseBuiltin() },
            ) {
                Text(if (pendingUri == null) "用内置密钥" else "导入并启用", color = Sk.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text("选择文件", color = Sk.Accent)
            }
        },
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.MONET_SYSTEM -> "动态取色（跟随系统）"
    ThemeMode.MONET_LIGHT -> "动态取色（浅色）"
    ThemeMode.MONET_DARK -> "动态取色（深色）"
}

private fun BottomBarStyle.label(): String = when (this) {
    BottomBarStyle.DEFAULT -> "默认"
    BottomBarStyle.FLOATING -> "悬浮底栏"
    BottomBarStyle.LIQUID_GLASS -> "液态玻璃底栏"
}

private fun UiStyle.label(): String = when (this) {
    UiStyle.MATERIAL3 -> "Material 3"
    UiStyle.MIUIX -> "Miuix"
}
