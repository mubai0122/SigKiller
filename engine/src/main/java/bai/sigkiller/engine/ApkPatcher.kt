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

package bai.sigkiller.engine

import bai.sigkiller.engine.axml.ManifestEditor
import bai.sigkiller.engine.arsc.ResourceTable
import bai.sigkiller.engine.dex.DexPayload
import bai.sigkiller.engine.sign.ApkCertificates
import bai.sigkiller.engine.sign.ApkSigning
import bai.sigkiller.engine.sign.SigningKey
import bai.sigkiller.engine.zip.ApkZip
import bai.sigkiller.engine.zip.ZipEdit
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.zip.ZipFile

/**
 * 去签引擎。
 *
 * 产物 = 原 APK + 一个 killer.dex + 一个 hook 用 so + 改写过的 AndroidManifest.xml，
 * 可选再塞一份原始 APK 进 assets。整个过程不碰原包的 dex（只追加 classesN.dex），
 * 所以目标应用自己的代码一行都不会变。
 */
class ApkPatcher(private val payload: Payload) {

    fun patch(
        source: File,
        outputDir: File,
        config: PatchConfig,
        signingKey: SigningKey? = null,
        onProgress: (PatchProgress) -> Unit = {},
    ): PatchResult {
        val log = mutableListOf<String>()
        fun step(stage: PatchStage, fraction: Float, message: String) {
            log += message
            onProgress(PatchProgress(stage, fraction, message))
        }

        config.validate().takeIf { it.isNotEmpty() }?.let {
            throw PatchException(it.joinToString("；"))
        }
        if (!source.isFile) throw PatchException("找不到要处理的 APK：$source")
        if (config.autoSign && signingKey == null) throw PatchException("开启了自动签名但没有可用的签名密钥")

        outputDir.mkdirs()
        step(PatchStage.PREPARE, 0.02f, "检查输入：${source.name}（${source.length() / 1024} KB）")

        // ---- 1. 读原包 ------------------------------------------------------
        var manifestBytes: ByteArray
        var existingDexCount: Int
        ZipFile(source).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw PatchException("不是有效的 APK：缺少 AndroidManifest.xml")
            manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
            existingDexCount = zip.detectDexCount()
        }
        step(PatchStage.ANALYZE, 0.08f, "清单与 ${existingDexCount} 个 dex 读取完成")

        // ---- 2. 原包签名证书 ------------------------------------------------
        val certificate = ApkCertificates.primary(source)
        val signatureBase64 = Base64.getEncoder().encodeToString(certificate.encoded)
        step(
            PatchStage.ANALYZE,
            0.14f,
            "原签名：${certificate.subjectX500Principal.name.replace("\n", ", ")}",
        )

        // ---- 3. 载荷 dex ----------------------------------------------------
        val renamedDex = DexPayload.renamePackage(
            dex = payload.dex,
            fromPackage = payload.dexPackage,
            toPackage = config.injectedClass,
            pinnedClasses = payload.pinnedClasses,
        )
        // 只追加，绝不覆盖目标应用自己的 dex
        val dexName = if (existingDexCount == 0) "classes.dex" else "classes${existingDexCount + 1}.dex"
        step(PatchStage.DEX, 0.24f, "注入 $dexName（${renamedDex.size / 1024} KB，包名 ${config.injectedClass}）")

        // ---- 4. 清单 --------------------------------------------------------
        val authoritySuffix = randomSuffix()
        var manifest = ManifestEditor.inject(
            manifest = manifestBytes,
            config = config,
            signatureBase64 = signatureBase64,
            authoritySuffix = authoritySuffix,
            log = { step(PatchStage.MANIFEST, 0.32f, it) },
        )
        step(
            PatchStage.MANIFEST,
            0.36f,
            if (manifest.viaApplication) {
                "注入 Application：${manifest.componentClass}"
            } else {
                "注入 provider：${manifest.componentClass}（authority=${manifest.authority}）"
            },
        )
        if (config.useOriginalApk) {
            step(PatchStage.ORIGIN, 0.40f, "原包将在 assets/${config.originAssetPath.removePrefix("assets/")} 内嵌入")
        }

        // ---- 5. resources.arsc（只有改包名时才需要） -------------------------
        var arscEdit: ZipEdit.Bytes? = null
        if (config.newPackageName != null && config.newPackageName != readOriginalPackage(manifestBytes)) {
            ZipFile(source).use { zip ->
                val arsc = zip.getEntry("resources.arsc")
                if (arsc != null) {
                    val patched = ResourceTable.renamePackage(
                        zip.getInputStream(arsc).use { it.readBytes() },
                        from = readOriginalPackage(manifestBytes),
                        to = config.newPackageName,
                    )
                    arscEdit = ZipEdit.Bytes("resources.arsc", patched)
                    step(PatchStage.MANIFEST, 0.44f, "resources.arsc 包名已改写")
                } else {
                    step(PatchStage.MANIFEST, 0.44f, "这个 APK 没有 resources.arsc，跳过资源包名改写")
                }
            }
        }

        // ---- 6. 打包 --------------------------------------------------------
        val baseName = source.nameWithoutExtension
        val unsigned = File(outputDir, "$baseName-unsigned.apk")
        val edits = mutableListOf<ZipEdit>()
        edits += ZipEdit.Bytes("AndroidManifest.xml", manifest.bytes)
        edits += ZipEdit.Bytes(dexName, renamedDex)
        payload.natives.forEach { (abi, bytes) ->
            // so 一律不压缩：目标 APK 可能声明 extractNativeLibs=false，那样只有
            // STORED + 页对齐才能被 System.loadLibrary 直接 mmap
            edits += ZipEdit.Bytes("lib/$abi/lib${config.soName}.so", bytes, store = true)
        }
        arscEdit?.let { edits += it }
        if (config.useOriginalApk) {
            edits += ZipEdit.Stream(config.originAssetPath, source.length(), deflated = true) { out ->
                FileInputStream(source).use { it.copyTo(out, 1 shl 16) }
            }
        }
        step(PatchStage.NATIVE, 0.52f, "注入 so：${payload.natives.keys.joinToString("/")}")
        step(PatchStage.REBUILD, 0.58f, "重新打包 ${edits.size} 处改动")

        // 打包阶段：按已写字节 / 总字节把进度连续推进 0.58 → 0.70。
        // 字节级回调很密（每 64KB 一次），按整数百分比节流，避免日志和界面刷新过猛。
        var lastPackPercent = -1
        ApkZip.rebuild(
            source = source,
            target = unsigned,
            edits = edits,
            drop = { name -> isSignatureFile(name) },
            onProgress = { written, total ->
                val ratio = if (total > 0) written.toFloat() / total else 1f
                val percent = (ratio * 100).toInt()
                if (percent != lastPackPercent || written >= total) {
                    lastPackPercent = percent
                    log += "打包 ${percent}% · ${written / 1024}/${total / 1024} KB"
                    onProgress(
                        PatchProgress(
                            stage = PatchStage.REBUILD,
                            fraction = 0.58f + 0.12f * ratio.coerceIn(0f, 1f),
                            message = "正在压缩打包 ${percent}%",
                            packedBytes = written,
                            totalBytes = total,
                        ),
                    )
                }
            },
        )
        step(PatchStage.REBUILD, 0.70f, "已生成未签名包（${unsigned.length() / 1024} KB）")

        // ---- 7. 签名 + 校验 --------------------------------------------------
        var output = unsigned
        var signed = false
        if (config.autoSign && signingKey != null) {
            output = File(outputDir, "$baseName-sigkilled.apk")
            ApkSigning.sign(unsigned, output, signingKey)
            signed = true
            step(PatchStage.SIGN, 0.86f, "已签名（v1+v2+v3，密钥 ${signingKey.displayAlias}）")

            val report = ApkSigning.verify(output)
            if (!report.verified) {
                throw PatchException("签名校验失败：${report.errors.joinToString("; ")}")
            }
            step(
                PatchStage.VERIFY,
                0.95f,
                "校验通过（v1=${report.v1} v2=${report.v2} v3=${report.v3}）",
            )
            unsigned.delete()
        } else {
            step(PatchStage.SIGN, 0.86f, "已跳过签名，产物未签名，安装前需要自行签名")
        }

        step(PatchStage.DONE, 1f, "完成：${output.name}（${output.length() / 1024} KB）")
        return PatchResult(
            output = output,
            packageName = manifest.packageName,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            signed = signed,
            sizeBytes = output.length(),
            log = log.toList(),
        )
    }

    private fun readOriginalPackage(manifest: ByteArray): String {
        val xml = bai.sigkiller.engine.axml.BinaryXmlAccess.packageName(manifest)
        return xml ?: throw PatchException("清单里没有 package 属性")
    }

    private fun randomSuffix(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10).map { alphabet.random() }.joinToString("")
    }

    private fun ZipFile.detectDexCount(): Int {
        var max = 0
        val entries = entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            val match = Regex("""^classes(\d*)\.dex$""").find(name) ?: continue
            val index = match.groupValues[1].toIntOrNull() ?: 1
            if (index > max) max = index
        }
        return max
    }

    private fun isSignatureFile(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
            upper.endsWith(".SF") || upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") || upper.endsWith(".EC")
    }
}
