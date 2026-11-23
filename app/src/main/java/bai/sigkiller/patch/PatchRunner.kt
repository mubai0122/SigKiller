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

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import bai.sigkiller.data.InjectSettings
import bai.sigkiller.data.PatchSource
import bai.sigkiller.data.SettingsStore
import bai.sigkiller.data.TaskStore
import bai.sigkiller.engine.ApkPatcher
import bai.sigkiller.engine.PatchConfig
import bai.sigkiller.engine.PatchException
import bai.sigkiller.engine.PatchProgress
import bai.sigkiller.engine.PatchResult
import bai.sigkiller.engine.sign.SigningKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 把引擎接到界面上：出一份任务、边走边报进度、产物再按设置导出到 Download。
 * 任务不放 ViewModel 里，切页面不会打断它。
 */
@Singleton
class PatchRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val payloadLoader: PayloadLoader,
    private val settingsStore: SettingsStore,
    private val taskStore: TaskStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun enqueue(source: PatchSource, settings: InjectSettings) {
        val taskId = taskStore.start(source)
        scope.launch {
            val log = mutableListOf<String>()
            fun record(line: String) {
                log += "[${clock.format(Date())}] $line"
                while (log.size > MAX_LOG_LINES) log.removeAt(0)
            }

            try {
                record("读取 ${source.apkPath}")
                val config = settings.toPatchConfig()
                if (config.useOriginalApk && payloadLoader.payload.natives.isEmpty()) {
                    throw PatchException("没有找到 libSignatureKiller.so，请用带 NDK 的环境重新构建应用")
                }
                val outputDir = File(context.filesDir, "outputs").apply { mkdirs() }
                val patcher = ApkPatcher(payloadLoader.payload)
                val result = patcher.patch(
                    source = File(source.apkPath),
                    outputDir = outputDir,
                    config = config,
                    signingKey = signingKey(config),
                    onProgress = { progress: PatchProgress ->
                        record(progress.message)
                        taskStore.progress(
                            id = taskId,
                            stage = progress.stage,
                            progress = progress.fraction,
                            message = progress.message,
                            log = log.toList(),
                            packedBytes = progress.packedBytes,
                            totalBytes = progress.totalBytes,
                        )
                    },
                )
                val exported = if (settingsStore.saveToPublic.value) export(result) else null
                record(
                    buildString {
                        append("产物 ${result.output.name} · ${result.sizeBytes / 1024} KB")
                        if (result.signed) append(" · 已签名") else append(" · 未签名")
                        if (exported != null) append(" · 已导出 $exported") else if (settingsStore.saveToPublic.value) append(" · 导出失败")
                    },
                )
                taskStore.finish(
                    id = taskId,
                    outputPath = result.output.absolutePath,
                    signed = result.signed,
                    sizeBytes = result.sizeBytes,
                    exportedPath = exported,
                    log = log.toList(),
                )
            } catch (error: Throwable) {
                record("失败：${error.message ?: error::class.java.simpleName}")
                taskStore.fail(taskId, error.message ?: error::class.java.simpleName, log.toList())
            }
        }
    }

    private fun signingKey(config: PatchConfig): SigningKey? {
        if (!config.autoSign) return null
        val custom = settingsStore.customKeystore.value
        if (custom != null) {
            val file = File(custom)
            if (file.isFile) {
                val password = settingsStore.customKeystorePassword.value.ifBlank { BUILTIN_PASSWORD }
                return SigningKey(file.readBytes(), password, password)
            }
        }
        val bytes = context.assets.open("sigkiller.p12").use { it.readBytes() }
        return SigningKey(bytes, BUILTIN_PASSWORD, BUILTIN_PASSWORD)
    }

    /** 导出到 Download/BaiSigKiller。失败不影响主流程，产物仍在应用私有目录。 */
    private fun export(result: PatchResult): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, result.output.name)
                put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_DIR")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                result.output.inputStream().use { input -> input.copyTo(output) }
            }
            "Download/$PUBLIC_DIR/${result.output.name}"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PUBLIC_DIR)
            dir.mkdirs()
            val target = File(dir, result.output.name)
            result.output.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }.getOrNull()

    companion object {
        const val BUILTIN_PASSWORD = "123456"
        const val APK_MIME = "application/vnd.android.package-archive"

        /** 公共目录名，跟应用包名对齐（bai = Bai）。 */
        const val PUBLIC_DIR = "BaiSigKiller"

        private const val MAX_LOG_LINES = 400
    }
}
