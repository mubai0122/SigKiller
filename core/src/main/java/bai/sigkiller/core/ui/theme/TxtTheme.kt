package bai.sigkiller.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalTxtUiStyle = staticCompositionLocalOf { UiStyle.MATERIAL3 }

@Composable
fun TxtTheme(
    mode: ColorSchemeMode = ColorSchemeMode.System,
    uiStyle: UiStyle = UiStyle.MATERIAL3,
    content: @Composable () -> Unit,
) {
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }
    CompositionLocalProvider(LocalTxtUiStyle provides uiStyle) {
        MiuixTheme(controller = controller) {
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
                if (uiStyle == UiStyle.MATERIAL3) {
                    androidx.compose.material3.MaterialTheme(
                        colorScheme = miuixToMaterial3(MiuixTheme.colorScheme),
                    ) { content() }
                } else {
                    content()
                }
            }
        }
    }
}//root surface: the nav shell sits outside any Scaffold, so it needs this backdrop

private fun miuixToMaterial3(c: top.yukonga.miuix.kmp.theme.Colors): androidx.compose.material3.ColorScheme {
    val dark = c.surface.luminance() < 0.5f
    val base = if (dark) {
        androidx.compose.material3.darkColorScheme()
    } else {
        androidx.compose.material3.lightColorScheme()
    }
    return base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        secondary = c.secondary,
        onSecondary = c.onSecondary,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.onTertiaryContainer,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.onSurfaceVariantSummary,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerHighest = c.surfaceContainerHighest,
        outline = c.outline,
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onErrorContainer,
    )
}//dark vs light is read off the miuix surface, so the two skins never disagree

enum class ThemeMode(val scheme: ColorSchemeMode) {
    SYSTEM(ColorSchemeMode.System),
    LIGHT(ColorSchemeMode.Light),
    DARK(ColorSchemeMode.Dark),
    MONET_SYSTEM(ColorSchemeMode.MonetSystem),
    MONET_LIGHT(ColorSchemeMode.MonetLight),
    MONET_DARK(ColorSchemeMode.MonetDark),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}//user facing theme choices, one to one with Miuix ColorSchemeMode