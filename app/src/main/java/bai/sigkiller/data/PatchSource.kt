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

package bai.sigkiller.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 一次去签的输入。
 *
 * 两个来源：手机上已安装的应用，或者用户从文件管理器里挑来的 APK。
 * 引擎只认一个本地文件路径，所以两种情况在这里先统一掉。
 */
data class PatchSource(
    val label: String,
    val packageName: String,
    val apkPath: String,
    val versionName: String = "",
    val sizeBytes: Long = 0L,
    /** true 表示来自「从文件选择」，产物默认另存、不覆盖系统里已装的那份 */
    val fromFile: Boolean = false,
    /** 应用图标，详情页头部展示用；从文件挑的 APK 可能为 null */
    val icon: ImageBitmap? = null,
) {

    val displayPackage: String get() = packageName.ifBlank { "未知包名" }

    companion object {
        fun fromApp(app: InstalledApp): PatchSource = PatchSource(
            label = app.label,
            packageName = app.packageName,
            apkPath = app.apkPath,
            versionName = app.versionName,
            sizeBytes = app.apkSize,
            fromFile = false,
            icon = app.icon,
        )
    }
}
