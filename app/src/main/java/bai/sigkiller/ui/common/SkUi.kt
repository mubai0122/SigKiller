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

package bai.sigkiller.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors

/**
 * 全局视觉token。
 *
 * 底色是纯白（参考图的观感），所以卡片不能靠明度差分层，改用
 * 1px 描边 + 20dp 圆角来做区隔；强调色沿用参考弹窗里的青。
 */
object Sk {
    val Accent = Color(0xFF00B4A4)
    val AccentDark = Color(0xFF009184)
    val AccentSoft = Color(0xFFE6F7F5)
    val Bg = Color(0xFFFFFFFF)
    val Card = Color(0xFFFFFFFF)
    val Soft = Color(0xFFF5F6F8)
    val SoftDeep = Color(0xFFEEF0F3)
    val Line = Color(0xFFEBECEF)
    val Text = Color(0xFF16181D)
    val Muted = Color(0xFF8B909A)
    val Faint = Color(0xFFB3B8C0)
    val Danger = Color(0xFFE5484D)

    val CardShape = RoundedCornerShape(20.dp)
    val CardShapeSmall = RoundedCornerShape(14.dp)
}

/** 白色页面 + 状态栏留白 + 下拉过的标题。三个页面共用同一套头。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 10.dp, top = 26.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                color = Sk.Text,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, fontSize = 12.5.sp, color = Sk.Muted)
            }
        }
        trailing()
    }
}

/** 白底卡片：miuix Card 的圆角/按压反馈，加一圈描边把它从白底上摘出来。 */
@Composable
fun SkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 20.dp,
    borderColor: Color = Sk.Line,
    containerColor: Color = Sk.Card,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .border(1.dp, borderColor, RoundedCornerShape(corner))
    val colors = CardDefaults.defaultColors(color = containerColor, contentColor = Sk.Text)
    if (onClick == null) {
        Card(
            modifier = base,
            cornerRadius = corner,
            insideMargin = contentPadding,
            colors = colors,
            content = content,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = base,
            cornerRadius = corner,
            insideMargin = contentPadding,
            colors = colors,
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        color = Sk.Muted,
        modifier = modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** 圆角图标底座，列表、空状态、文件入口都用它统一观感。 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = Sk.Accent,
    background: Color = Sk.AccentSoft,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.52f), tint = tint)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 14.dp,
        minHeight = 42.dp,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            color = Sk.Accent,
            disabledColor = Sk.SoftDeep,
            contentColor = Color.White,
            disabledContentColor = Sk.Faint,
        ),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Sk.Accent,
    enabled: Boolean = true,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 12.dp,
        minWidth = 0.dp,
        minHeight = 36.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            textColor = color,
            disabledTextColor = Sk.Faint,
        ),
    )
}

/** 小标签：版本号、大小、签名前缀这类信息用。 */
@Composable
fun MetaChip(
    text: String,
    color: Color = Sk.Muted,
    background: Color = Sk.Soft,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
    ) {
        Text(text = text, fontSize = 10.5.sp, color = color, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon = icon, size = 56.dp, corner = 18.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Sk.Text)
        Spacer(Modifier.height(6.dp))
        Text(description, fontSize = 12.5.sp, lineHeight = 19.sp, color = Sk.Muted)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun HLine(modifier: Modifier = Modifier, color: Color = Sk.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun RowScope.Filler() = Spacer(Modifier.weight(1f))

@Composable
fun RowOfSpaced(
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** 分段控件：两个选项之间的切换，灰底白药丸，比 TabRow 更贴这套白底。 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sk.Soft)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .clickableNoRipple { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) Sk.Text else Sk.Muted,
                )
            }
        }
    }
}

/** 列表行要的是整块高亮，不要 Material 的水波纹压在自己的背景上。 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/**
 * 页面底部的安全留白。
 *
 * 底栏是浮在内容之上的：液态玻璃与 M3 形态会把自己的高度报给
 * [bai.sigkiller.core.ui.component.LocalTxtNavBarInset]，而默认/悬浮形态
 * 报 0（内容要从底下穿过去）。这里统一兜一层，保证列表末尾和悬浮操作条
 * 不会被底栏压住。
 */
@Composable
fun contentBottomInset(): Dp {
    val reported = bai.sigkiller.core.ui.component.LocalTxtNavBarInset.current
    if (reported > 0.dp) return reported + 12.dp
    val nav = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return nav + 86.dp
}
