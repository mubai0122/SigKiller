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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkSize: Long,
    val isSystem: Boolean,
    val signatureMd5: String,
    val icon: ImageBitmap?,
)

@Singleton
class InstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val packageManager: PackageManager get() = context.packageManager

    suspend fun load(includeSystem: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        val infos = runCatching { packageManager.getInstalledApplications(flags) }.getOrDefault(emptyList())
        infos.asSequence()
            .filter { includeSystem || !it.isSystemApp() }
            .filter { !it.packageName.contains("bai.sigkiller") }
            .mapNotNull { info -> runCatching { info.toApp(includeIcon = true) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    suspend fun refreshSignature(app: InstalledApp): InstalledApp = withContext(Dispatchers.IO) {
        runCatching {
            val info = packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
            info.toApp(includeIcon = false).copy(icon = app.icon)
        }.getOrDefault(app)
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    private fun ApplicationInfo.toApp(includeIcon: Boolean): InstalledApp {
        val path = sourceDir ?: ""
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull()
        return InstalledApp(
            label = packageManager.getApplicationLabel(this).toString(),
            packageName = packageName,
            versionName = packageInfo?.versionName ?: "1.0",
            versionCode = packageInfo?.let { info ->
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            } ?: 0L,
            apkPath = path,
            apkSize = if (path.isNotEmpty()) java.io.File(path).length() else 0L,
            isSystem = isSystemApp(),
            signatureMd5 = packageInfo?.let { signatureMd5(it) }.orEmpty(),
            icon = if (includeIcon) loadIcon(packageName) else null,
        )
    }

    private fun loadIcon(packageName: String): ImageBitmap? = runCatching {
        val drawable: Drawable = packageManager.getApplicationIcon(packageName)
        val bitmap: Bitmap = drawable.toBitmap(width = 144, height = 144)
        bitmap.asImageBitmap()
    }.getOrNull()

    private fun signatureMd5(info: android.content.pm.PackageInfo): String {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val bytes = signatures?.firstOrNull()?.toByteArray() ?: return ""
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
