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

import bai.sigkiller.engine.axml.BinaryXmlAccess
import bai.sigkiller.engine.dex.DexPayload
import bai.sigkiller.engine.sign.ApkSigning
import bai.sigkiller.engine.sign.SigningKey
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * 端到端用例：拿一个真实的 APK 跑完整流程，然后用 apksig 校验签名，
 * 再用 aapt2 dump 检查清单结构（有 aapt2 时）。
 *
 * 用例依赖外部文件，通过 -P 参数传入；缺少文件时自动跳过，
 * 这样在没有构建产物的机器上 gradle test 依然是绿的。
 */
class ApkPatcherTest {

    private val sampleApk = File(System.getProperty("sigkiller.sampleApk").orEmpty())
    private val payloadDex = File(System.getProperty("sigkiller.payloadDex").orEmpty())
    private val keystore = File(System.getProperty("sigkiller.keystore").orEmpty())
    private val aapt2 = File(System.getProperty("sigkiller.aapt2").orEmpty())
    private val sampleNoApp = File(System.getProperty("sigkiller.sampleNoAppApk").orEmpty())

    private fun payload(): Payload = Payload(
        dex = payloadDex.readBytes(),
        natives = mapOf(
            "arm64-v8a" to ByteArray(2048) { 0x7f },
            "armeabi-v7a" to ByteArray(1024) { 0x7f },
        ),
    )

    @Test
    fun `patches a real apk end to end`() {
        assumeTrue(sampleApk.isFile, "缺少样例 APK")
        assumeTrue(payloadDex.isFile, "缺少 killer.dex")
        assumeTrue(keystore.isFile, "缺少 keystore")

        val outputDir = File(System.getProperty("java.io.tmpdir"), "sigkiller-test").apply { deleteRecursively(); mkdirs() }
        val config = PatchConfig(
            injectedClass = "bai.killer",
            useOriginalApk = true,
            autoSign = true,
        )
        val result = ApkPatcher(payload()).patch(
            source = sampleApk,
            outputDir = outputDir,
            config = config,
            signingKey = SigningKey(keystore.readBytes(), "123456", "123456"),
        )

        assertTrue(result.output.isFile, "产物不存在")
        assertEquals("com.example.sigtest", result.packageName)
        assertEquals("1.2.3", result.versionName)
        assertEquals(7L, result.versionCode)

        val report = ApkSigning.verify(result.output)
        assertTrue(report.verified, "签名校验失败：${report.errors}")
        assertTrue(report.v2, "应当有 v2 签名")

        ZipFile(result.output).use { zip ->
            assertNotNull(zip.getEntry("classes2.dex"), "应当追加 classes2.dex")
            assertNotNull(zip.getEntry("lib/arm64-v8a/libSignatureKiller.so"), "应当注入 so")
            assertNotNull(zip.getEntry("assets/origin.apk"), "应当嵌入原包")
            // 旧签名必须清掉；签名之后 apksig 只会写进它自己那一份 .SF
            val sfCount = zip.entries().toList().count {
                it.name.uppercase().startsWith("META-INF/") && it.name.uppercase().endsWith(".SF")
            }
            assertEquals(1, sfCount, "META-INF 下应当只剩本次签名生成的 .SF")
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            assertEquals("com.example.sigtest", BinaryXmlAccess.packageName(manifest))
            val providers = BinaryXmlAccess.elements(manifest, "provider")
            val injected = providers.firstOrNull { it["name"] == "bai.killer.KillerProvider" }
            assertNotNull(injected, "provider 未注入，现有：$providers")
            assertEquals("false", injected["exported"])
            val meta = BinaryXmlAccess.elements(manifest, "meta-data").mapNotNull { it["name"] }
            assertTrue("killer.signature" in meta, "缺少签名 meta-data：$meta")
            assertTrue("killer.origin.asset" in meta, "缺少原包 meta-data：$meta")

            val dex = zip.getInputStream(zip.getEntry("classes2.dex")).use { it.readBytes() }
            assertTrue(dex.size > 1000)
        }

        if (aapt2.isFile) {
            val process = ProcessBuilder(aapt2.absolutePath, "dump", "xmltree", result.output.absolutePath, "--file", "AndroidManifest.xml")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            assertTrue(text.contains("KillerProvider"), "aapt2 解析不出注入的 provider：\n$text")
            assertTrue(text.contains("killer.signature"), "aapt2 解析不出 meta-data")
            println(text)
        }
    }

    @Test
    fun `renames injected package when custom class name is used`() {
        assumeTrue(payloadDex.isFile, "缺少 killer.dex")
        val renamed = DexPayload.renamePackage(
            payloadDex.readBytes(),
            fromPackage = "bai.killer",
            toPackage = "com.demo.guard",
            pinnedClasses = setOf("bai.killer.Native"),
        )
        val classes = DexPayload.classDescriptors(renamed).toSet()
        assertTrue("Lcom/demo/guard/KillerProvider;" in classes, "注入类名没改：$classes")
        assertTrue("Lbai/killer/Native;" in classes, "Native 必须保持原包名，否则 JNI 符号对不上")
        assertTrue("Lcom/demo/guard/Killer;" in classes)
        assertTrue("Lcom/demo/guard/KillerApp;" in classes)
    }

    @Test
    fun `renames package in manifest and resources when asked`() {
        assumeTrue(sampleApk.isFile, "缺少样例 APK")
        assumeTrue(payloadDex.isFile, "缺少 killer.dex")
        val outputDir = File(System.getProperty("java.io.tmpdir"), "sigkiller-test-rename").apply { deleteRecursively(); mkdirs() }
        val result = ApkPatcher(payload()).patch(
            source = sampleApk,
            outputDir = outputDir,
            config = PatchConfig(useOriginalApk = false, autoSign = false, newPackageName = "com.demo.renamed"),
        )
        assertEquals("com.demo.renamed", result.packageName)
        ZipFile(result.output).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            assertEquals("com.demo.renamed", BinaryXmlAccess.packageName(manifest))
            val arsc = zip.getInputStream(zip.getEntry("resources.arsc")).use { it.readBytes() }
            val text = String(arsc, Charsets.ISO_8859_1)
            assertTrue(!text.contains("com.example.sigtest\u0000"), "arsc 里的包名字段没改")
        }
        if (aapt2.isFile) {
            val process = ProcessBuilder(aapt2.absolutePath, "dump", "badging", result.output.absolutePath)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            assertTrue(text.contains("package: name='com.demo.renamed'"), "aapt2 读到的包名不对：\n" + text.take(300))
        }
    }

    @Test
    fun `injects application class when target has none`() {
        assumeTrue(sampleNoApp.isFile, "缺少没有 Application 的样例 APK")
        assumeTrue(payloadDex.isFile, "缺少 killer.dex")
        val outputDir = File(System.getProperty("java.io.tmpdir"), "sigkiller-test-application").apply { deleteRecursively(); mkdirs() }
        val result = ApkPatcher(payload()).patch(
            source = sampleNoApp,
            outputDir = outputDir,
            config = PatchConfig(useOriginalApk = false, autoSign = false),
        )
        ZipFile(result.output).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
            val application = BinaryXmlAccess.elements(manifest, "application").first()
            assertEquals("bai.killer.KillerApp", application["name"], "应当接管 android:name")
            val providers = BinaryXmlAccess.elements(manifest, "provider").mapNotNull { it["name"] }
            assertTrue("bai.killer.KillerProvider" !in providers, "接管 Application 时不该再加 provider")
            assertTrue(BinaryXmlAccess.elements(manifest, "meta-data").mapNotNull { it["name"] }.contains("killer.signature"))
        }
        if (aapt2.isFile) {
            val process = ProcessBuilder(aapt2.absolutePath, "dump", "xmltree", result.output.absolutePath, "--file", "AndroidManifest.xml")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            assertTrue(text.contains("KillerApp"), "aapt2 读不到注入的 Application：\n" + text.take(600))
        }
    }

    @Test
    fun `keeps original package when custom name is not set`() {
        assumeTrue(sampleApk.isFile, "缺少样例 APK")
        assumeTrue(payloadDex.isFile, "缺少 killer.dex")
        val outputDir = File(System.getProperty("java.io.tmpdir"), "sigkiller-test-nosign").apply { deleteRecursively(); mkdirs() }
        val result = ApkPatcher(payload()).patch(
            source = sampleApk,
            outputDir = outputDir,
            config = PatchConfig(useOriginalApk = false, autoSign = false),
        )
        assertTrue(!result.signed)
        assertTrue(result.output.name.endsWith("-unsigned.apk"))
        ZipFile(result.output).use { zip ->
            assertTrue(zip.getEntry("assets/origin.apk") == null, "关掉原包模式不该嵌入 APK")
        }
    }
}
