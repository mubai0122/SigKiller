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
import bai.sigkiller.engine.Payload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从自己的 APK 里取出过签载荷：
 *   assets/killer.dex      —— 注入到目标应用的 dex
 *   lib/<abi>/libSignatureKiller.so —— open() hook
 *
 * 直接从 APK 的 lib 目录读，而不是 nativeLibraryDir：目标 APK 可能只支持
 * 某一个 ABI，我们需要知道自己手上有哪几份 so 可以挑。
 */
@Singleton
class PayloadLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val cached: Payload by lazy { load() }

    val payload: Payload get() = cached

    private fun load(): Payload {
        val dex = context.assets.open("killer.dex").use { it.readBytes() }
        return Payload(dex = dex, natives = readNatives())
    }

    private fun readNatives(): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        val apk = File(context.applicationInfo.sourceDir)
        runCatching {
            ZipFile(apk).use { zip ->
                zip.entries().toList()
                    .filter { it.name.startsWith("lib/") && it.name.endsWith("/libSignatureKiller.so") }
                    .forEach { entry ->
                        val abi = entry.name.split('/')[1]
                        result[abi] = zip.getInputStream(entry).use { it.readBytes() }
                    }
            }
        }
        if (result.isEmpty()) {
            // 兜底：安装时被解压出来的那份
            val dir = File(context.applicationInfo.nativeLibraryDir.orEmpty())
            val lib = File(dir, "libSignatureKiller.so")
            if (lib.isFile) {
                result[android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"] = lib.readBytes()
            }
        }
        return result
    }
}
