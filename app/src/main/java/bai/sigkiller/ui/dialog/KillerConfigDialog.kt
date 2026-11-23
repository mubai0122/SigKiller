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

package bai.sigkiller.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bai.sigkiller.R
import bai.sigkiller.data.InjectSettings
import bai.sigkiller.ui.common.Sk
import androidx.compose.foundation.Image

/** 截图里那个青色，整块弹窗的强调色都跟着它走。 */
val KillerAccent = Color(0xFF00B4A4)

/**
 * 「去除签名校验」配置弹窗。
 *
 * 版式照着参考图来：标题 + 右上角开源入口，注入类名带下划线输入框，
 * 原包过签开关打开后展开三个路径项，下面是免责声明和「自动签名 / 取消 / 确定」。
 */
@Composable
fun KillerConfigDialog(
    initial: InjectSettings,
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (InjectSettings) -> Unit,
    onOpenSource: () -> Unit,
) {
    var injectedClass by remember { mutableStateOf(initial.injectedClass) }
    var useOriginal by remember { mutableStateOf(initial.useOriginalApk) }
    var soName by remember { mutableStateOf(initial.soName) }
    var originAsset by remember { mutableStateOf(initial.originAssetPath) }
    var originFile by remember { mutableStateOf(initial.originExtractPath) }
    var autoSign by remember { mutableStateOf(initial.autoSign) }
    var renamePackage by remember { mutableStateOf(initial.renamePackage) }
    var newPackage by remember { mutableStateOf(initial.newPackage) }

    val draft = InjectSettings(
        injectedClass = injectedClass,
        useOriginalApk = useOriginal,
        soName = soName,
        originAssetPath = originAsset,
        originExtractPath = originFile,
        autoSign = autoSign,
        renamePackage = renamePackage,
        newPackage = newPackage,
    )
    val errors = draft.errors()
    val surface = Sk.Card
    val onSurface = Sk.Text
    val muted = Sk.Muted

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .imePadding(),
            shape = RoundedCornerShape(26.dp),
            color = surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenSource) {
                        Image(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "开源链接",
                            modifier = Modifier.width(26.dp).height(26.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                UnderlineField(
                    label = "注入类名",
                    value = injectedClass,
                    onValueChange = { injectedClass = it },
                    placeholder = "bai.killer",
                )

                ToggleRow(
                    label = "使用原包过签",
                    checked = useOriginal,
                    onCheckedChange = { useOriginal = it },
                    accent = KillerAccent,
                    emphasized = true,
                )

                if (useOriginal) {
                    UnderlineField(label = "注入so库名", value = soName, onValueChange = { soName = it })
                    UnderlineField(label = "注入原包路径", value = originAsset, onValueChange = { originAsset = it })
                    UnderlineField(label = "原包解压路径", value = originFile, onValueChange = { originFile = it })
                }

                ToggleRow(
                    label = "修改包名",
                    checked = renamePackage,
                    onCheckedChange = { renamePackage = it },
                    accent = KillerAccent,
                )
                if (renamePackage) {
                    UnderlineField(
                        label = "新包名",
                        value = newPackage,
                        onValueChange = { newPackage = it },
                        placeholder = "com.demo.app",
                    )
                    Text(
                        text = "改包名会让应用变成另一个应用：它自己的数据、推送、第三方 SDK 回调都可能失效，只在需要绕开包名检测时使用。",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = muted,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                Text(
                    text = "免责声明：该功能仅用于APP安全性测试，请勿用于违法用途，我们不承担由此产生的任何法律责任，" +
                        "使用该功能即表示同意该声明。如果您的产品无法通过该测试，可点击右上角的开源链接获取技术原理及其对抗方式。",
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp,
                    color = muted,
                )

                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    errors.forEach { error ->
                        Text(
                            text = "· $error",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoSign,
                        onCheckedChange = { autoSign = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = KillerAccent,
                            uncheckedColor = muted,
                            checkmarkColor = Color.White,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("自动签名", fontSize = 18.sp, color = KillerAccent)

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text("取消", fontSize = 18.sp, color = KillerAccent)
                    }
                    TextButton(
                        onClick = { onConfirm(draft) },
                        enabled = errors.isEmpty(),
                    ) {
                        Text(
                            text = confirmText,
                            fontSize = 18.sp,
                            color = if (errors.isEmpty()) KillerAccent else muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = if (emphasized) 22.sp else 18.sp,
            color = if (emphasized) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFB8BEC4),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = when {
        !enabled -> muted.copy(alpha = 0.3f)
        focused -> KillerAccent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(text = label, fontSize = 15.sp, color = muted)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 21.sp, color = muted.copy(alpha = 0.45f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                interactionSource = interaction,
                textStyle = TextStyle(
                    fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(KillerAccent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 2.dp else 1.5.dp)
                .background(lineColor),
        )
        Spacer(Modifier.height(10.dp))
    }
}
