package bai.sigkiller.core.ui.theme

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences("txt_ui", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_MODE, null)))

    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _bottomBarStyle = MutableStateFlow(
        BottomBarStyle.fromKey(prefs.getString(KEY_BAR, null)),
    )

    val bottomBarStyle: StateFlow<BottomBarStyle> = _bottomBarStyle.asStateFlow()

    private val _uiStyle = MutableStateFlow(UiStyle.fromKey(prefs.getString(KEY_UI_STYLE, null)))

    val uiStyle: StateFlow<UiStyle> = _uiStyle.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
    }

    fun setBottomBarStyle(style: BottomBarStyle) {
        prefs.edit { putString(KEY_BAR, style.name) }
        _bottomBarStyle.value = style
    }

    fun setUiStyle(style: UiStyle) {
        prefs.edit { putString(KEY_UI_STYLE, style.name) }
        _uiStyle.value = style
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_BAR = "bottom_bar_style"
        const val KEY_UI_STYLE = "ui_style"
    }
}//plain prefs on purpose, nothing secret is stored here

enum class BottomBarStyle {
    DEFAULT,
    FLOATING,
    LIQUID_GLASS,
    ;

    companion object {
        fun fromKey(key: String?): BottomBarStyle =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}//liquid glass needs API 33, the shell falls back to floating below that

enum class UiStyle {
    MATERIAL3,
    MIUIX,
    ;

    companion object {
        fun fromKey(key: String?): UiStyle =
            entries.firstOrNull { it.name == key } ?: MATERIAL3
    }
}//absent key means the user never chose, and the default is material3

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferences: ThemePreferences,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> = preferences.mode

    val bottomBarStyle: StateFlow<BottomBarStyle> = preferences.bottomBarStyle

    val uiStyle: StateFlow<UiStyle> = preferences.uiStyle

    val colorSchemeMode: ColorSchemeMode get() = preferences.mode.value.scheme

    fun set(mode: ThemeMode) = preferences.set(mode)

    fun setBottomBarStyle(style: BottomBarStyle) = preferences.setBottomBarStyle(style)

    fun setUiStyle(style: UiStyle) = preferences.setUiStyle(style)
}//the activity reads colorSchemeMode before the first composition