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

package bai.sigkiller.patch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import bai.sigkiller.data.PatchSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户从 SAF 挑来的 APK 落到本地。
 *
 * 引擎用的是 java.io.File + ZipFile，content:// 那套它啃不动，
 * 所以先整份拷进 cache 再交给它。挑来的文件不保证扩展名是 .apk，
 * 包名从 PackageManager 读，读不到就用文件名兜底。
 */
@Singleton
class FileImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun import(uri: Uri): Result<PatchSource> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "picked.apk"
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            val dir = File(context.filesDir, "imports").apply { mkdirs() }
            // 每次换一个前缀，避免同名文件互相覆盖
            val target = File(dir, "${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            } ?: error("这个文件打不开，可能是授权已失效")

            if (target.length() == 0L) {
                target.delete()
                error("文件是空的")
            }

            val info = runCatching {
                context.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
            }.getOrNull()
            info?.applicationInfo?.let { app ->
                // 不设 sourceDir，getApplicationLabel / loadIcon 拿不到资源，只能回退到包名
                app.sourceDir = target.absolutePath
                app.publicSourceDir = target.absolutePath
            }
            val label = info?.applicationInfo?.let {
                runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull()
            }?.takeIf { it.isNotBlank() && it != info?.packageName }
                ?: displayName.substringBeforeLast('.')

            val icon = info?.applicationInfo?.let { app ->
                runCatching {
                    val drawable = app.loadIcon(context.packageManager)
                    val bounded = DrawableCompat.wrap(drawable).mutate()
                    bounded.toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888).asImageBitmap()
                }.getOrNull()
            }

            PatchSource(
                label = label,
                packageName = info?.packageName.orEmpty(),
                apkPath = target.absolutePath,
                versionName = info?.versionName.orEmpty(),
                sizeBytes = target.length(),
                fromFile = true,
                icon = icon,
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    /** 判断挑来的东西到底是不是个 APK，避免用户选了个 zip 之后在引擎里炸掉。 */
    fun looksLikeApk(uri: Uri): Boolean = runCatching {
        val type = context.contentResolver.getType(uri).orEmpty()
        val name = queryDisplayName(uri).orEmpty()
        type == APK_MIME || name.endsWith(".apk", ignoreCase = true)
    }.getOrDefault(true)

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
