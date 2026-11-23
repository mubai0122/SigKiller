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

package bai.sigkiller

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bai.sigkiller.core.ui.theme.ThemeViewModel
import bai.sigkiller.core.ui.theme.TxtTheme
import bai.sigkiller.nav.AppNav
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 从别的应用「分享」过来的 APK，交界面拿去当输入。 */
    private var sharedApk by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 三个页面都是纯白底，系统栏图标固定用深色，否则深色模式下会白字白底
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        sharedApk = readSharedApk(intent)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val mode by themeViewModel.mode.collectAsStateWithLifecycle()
            val uiStyle by themeViewModel.uiStyle.collectAsStateWithLifecycle()
            TxtTheme(mode = mode.scheme, uiStyle = uiStyle) {
                AppNav(
                    themeViewModel = themeViewModel,
                    sharedApk = sharedApk,
                    onSharedApkHandled = { sharedApk = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedApk(intent)?.let { sharedApk = it }
    }

    private fun readSharedApk(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return uri.takeIf { intent.type == "application/vnd.android.package-archive" || it.path?.endsWith(".apk") == true }
    }
}
