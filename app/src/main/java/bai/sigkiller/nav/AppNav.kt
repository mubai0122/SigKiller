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

package bai.sigkiller.nav

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import bai.sigkiller.ui.common.Sk
import bai.sigkiller.core.ui.component.TxtNavShell
import bai.sigkiller.core.ui.component.TxtNavTab
import bai.sigkiller.core.ui.theme.ThemeViewModel
import bai.sigkiller.ui.apps.AppsScreen
import bai.sigkiller.ui.settings.SettingsScreen
import bai.sigkiller.ui.tasks.TasksScreen
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Settings

object Routes {
    const val APPS = "apps"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
}

private const val REPO_URL = "https://github.com/mubai0122/SigKiller"
private const val WEBSITE_URL = "https://sigkiller.baiccb.cn/"

@Composable
fun AppNav(
    themeViewModel: ThemeViewModel,
    sharedApk: android.net.Uri? = null,
    onSharedApkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    // 启动时静默检查一次更新：有新版才弹窗，网络不通不打扰
    val updateViewModel: bai.sigkiller.ui.update.UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) { updateViewModel.checkSilently() }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val barStyle by themeViewModel.bottomBarStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val openSource: () -> Unit = { openUrl(context, REPO_URL) }
    val openWebsite: () -> Unit = { openUrl(context, WEBSITE_URL) }
    val openUrl: (String) -> Unit = { url -> openUrl(context, url) }

    val tabs = listOf(
        TxtNavTab(Routes.APPS, MiuixIcons.Home, "应用"),
        TxtNavTab(Routes.TASKS, MiuixIcons.Promotions, "任务"),
        TxtNavTab(Routes.SETTINGS, MiuixIcons.Settings, "设置"),
    )

    TxtNavShell(
        style = barStyle,
        // 三个页面都是顶层页，底栏一直显示；弹窗自己会向上报告，底栏自动让位
        showBar = true,
        tabs = tabs,
        selectedRoute = route,
        onTabSelected = { navController.switchTab(it) },
    ) {
        NavHost(navController = navController, startDestination = Routes.APPS) {
            composable(Routes.APPS) {
                AppsScreen(
                    onOpenSource = openSource,
                    onOpenTasks = { navController.switchTab(Routes.TASKS) },
                    sharedApk = sharedApk,
                    onSharedApkHandled = onSharedApkHandled,
                )
            }
            composable(Routes.TASKS) { TasksScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenSource = openSource,
                    onOpenWebsite = openWebsite,
                    updateViewModel = updateViewModel,
                )
            }
        }
    }

    // 更新弹窗放在导航外层：无论当前在哪个页面，检查到新版本都能弹出来
    val pendingUpdate = updateState.update
    if (pendingUpdate != null) {
        bai.sigkiller.ui.update.UpdateDialog(
            release = pendingUpdate,
            force = updateState.force,
            currentVersionName = updateViewModel.currentVersionName,
            onDownload = { openUrl(pendingUpdate.downloadUrl) },
            onDismiss = { updateViewModel.dismissUpdate() },
            onHistory = { updateViewModel.loadHistory() },
        )
    }
    if (updateState.historyOpen) {
        bai.sigkiller.ui.update.UpdateHistoryDialog(
            history = updateState.history,
            onDismiss = { updateViewModel.closeHistory() },
            onDownload = { openUrl(it.downloadUrl) },
        )
    }
    updateState.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateViewModel.consumeMessage() },
            containerColor = Sk.Card,
            title = { Text("检查更新", color = Sk.Text, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
            text = { Text(msg, fontSize = 13.5.sp, color = Sk.Muted) },
            confirmButton = {
                TextButton(onClick = { updateViewModel.consumeMessage() }) {
                    Text("知道了", color = Sk.Accent)
                }
            },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.APPS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
