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

package bai.sigkiller.engine.axml

import bai.sigkiller.engine.PatchConfig
import bai.sigkiller.engine.PatchException

/**
 * 面向清单业务的封装：注入组件 + meta-data、改包名。
 *
 * 注入点分两种，取决于目标应用自己有没有 Application：
 *
 *  · 没有 `android:name`（不少工具类应用是这样）→ 直接把它设为 `<注入类名>.KillerApp`，
 *    这样 hook 在 Application.attachBaseContext() 里就跑起来了，比 provider 更早。
 *  · 已经有自己的 Application → 不能覆盖（覆盖就得写一个继承原类的桩，那要求运行时生成
 *    字节码），于是加一个 ContentProvider。provider 由 ActivityThread.installContentProviders()
 *    创建，早于 Application.onCreate()，能挡住绝大多数校验。
 *
 * 两种方式都不改目标应用已有的任何代码。
 */
internal object ManifestEditor {

    /** android.R.attr 里要用到的几个固定资源 ID（API 1 起就没变过）。 */
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_ENABLED = 0x0101000e
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_AUTHORITIES = 0x01010018
    private const val ATTR_VALUE = 0x01010024

    class Result(
        val bytes: ByteArray,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        /** 实际注入的类：KillerApp 或 KillerProvider */
        val componentClass: String,
        val viaApplication: Boolean,
        val authority: String?,
        val metaData: Map<String, String>,
    )

    fun inject(
        manifest: ByteArray,
        config: PatchConfig,
        signatureBase64: String,
        authoritySuffix: String,
        log: (String) -> Unit,
    ): Result {
        val xml = BinaryXml.parse(manifest)
        val appIndex = xml.indexOfElement("application")
        if (appIndex < 0) throw PatchException("清单里找不到 <application> 节点，可能不是完整的 APK")
        val appEndIndex = xml.indexOfEndElement("application")
        if (appEndIndex < 0) throw PatchException("清单里 </application> 不完整")

        // 1. 属性名先登记资源 ID，框架靠 ID 而不是字符串名来取 android:name 等属性
        xml.registerAttributeId("name", ATTR_NAME)
        xml.registerAttributeId("value", ATTR_VALUE)
        xml.registerAttributeId("authorities", ATTR_AUTHORITIES)
        xml.registerAttributeId("exported", ATTR_EXPORTED)
        xml.registerAttributeId("enabled", ATTR_ENABLED)

        val metaData = linkedMapOf(
            "killer.signature" to signatureBase64,
            "killer.so" to config.soName,
        )
        if (config.useOriginalApk) {
            metaData["killer.origin.asset"] = config.originAssetPath
            metaData["killer.origin.file"] = config.originExtractPath
        }

        // 2. 包名（可选）
        val originalPackage = readAttribute(xml, "manifest", "package")
            ?: throw PatchException("清单里没有 package 属性")
        val targetPackage = config.newPackageName?.takeIf { it != originalPackage }
        if (targetPackage != null) {
            if (!xml.setStringAttribute("manifest", "package", targetPackage)) {
                throw PatchException("改写 package 属性失败")
            }
            log("包名：$originalPackage → $targetPackage")
        }

        val existingApplication = readAttribute(xml, "application", "name")
        val appClass = "${config.injectedClass}.KillerApp"
        val providerClass = "${config.injectedClass}.KillerProvider"
        val newChunks = mutableListOf<BinaryXml.Chunk>()
        var authority: String? = null

        val useApplication = existingApplication.isNullOrBlank()
        if (useApplication) {
            // 原本没有 Application：直接接管，hook 在 attachBaseContext 就跑
            val added = xml.addStringAttribute("application", "name", appClass) ||
                xml.setStringAttribute("application", "name", appClass)
            if (!added) throw PatchException("写入 application 的 android:name 失败")
            log("注入 Application：$appClass（原应用没有自定义 Application）")
        } else {
            // 已经有 Application：不覆盖，改用 provider
            authority = "${config.injectedClass}.sk.$authoritySuffix"
            val providerAttrs = mutableListOf<BinaryXml.NewAttribute>()
            providerAttrs += xml.stringAttribute("name", providerClass)
            providerAttrs += xml.stringAttribute("authorities", authority)
            providerAttrs += xml.booleanAttribute("exported", false)
            providerAttrs += xml.booleanAttribute("enabled", true)
            newChunks += xml.startElement("provider", providerAttrs)
            newChunks += xml.endElement("provider")
            log("注入 provider：$providerClass（保留原 Application $existingApplication）")
        }

        metaData.forEach { (key, value) ->
            newChunks += xml.startElement(
                "meta-data",
                listOf(xml.stringAttribute("name", key), xml.stringAttribute("value", value)),
            )
            newChunks += xml.endElement("meta-data")
        }
        xml.insertChunks(appEndIndex, newChunks)

        return Result(
            bytes = xml.toByteArray(),
            packageName = targetPackage ?: originalPackage,
            versionName = readAttribute(xml, "manifest", "versionName") ?: "1.0",
            versionCode = readAttribute(xml, "manifest", "versionCode")?.toLongOrNull() ?: 0L,
            componentClass = if (useApplication) appClass else providerClass,
            viaApplication = useApplication,
            authority = authority,
            metaData = metaData,
        )
    }

    /** 读一个字符串属性，命名空间不参与匹配（package 与 android:name 都走这里）。 */
    private fun readAttribute(xml: BinaryXml, element: String, name: String): String? {
        val elementIndex = xml.indexOfElement(element)
        if (elementIndex < 0) return null
        val nameIndex = xml.pool.indexOf(name)
        if (nameIndex < 0) return null
        val bytes = xml.chunkBytes(elementIndex)
        val attrStart = readUInt16(bytes, 24)
        val attrSize = readUInt16(bytes, 26)
        val attrCount = readUInt16(bytes, 28)
        for (i in 0 until attrCount) {
            val attr = 16 + attrStart + i * attrSize
            if (attr + 20 > bytes.size) break
            if (readInt(bytes, attr + 4) != nameIndex) continue
            val dataType = bytes[attr + 15].toInt() and 0xFF
            val data = readInt(bytes, attr + 16)
            return when (dataType) {
                BinaryXml.TYPE_STRING -> xml.pool[data]
                0x10, 0x11 -> data.toString()
                else -> null
            }
        }
        return null
    }
}
