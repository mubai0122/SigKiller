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

/** 给引擎其他部分用的一点点清单读取能力，避免把 BinaryXml 的内部结构暴露出去。 */
object BinaryXmlAccess {

    fun packageName(manifest: ByteArray): String? = attribute(manifest, "manifest", "package")

    fun attribute(manifest: ByteArray, element: String, name: String): String? {
        val xml = BinaryXml.parse(manifest)
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
                BinaryXml.TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
                else -> null
            }
        }
        return null
    }

    /** 把某个元素的所有实例读成「属性名 → 值」，用于自检和界面上的信息展示。 */
    fun elements(manifest: ByteArray, element: String): List<Map<String, String?>> {
        val xml = BinaryXml.parse(manifest)
        val nameIndex = xml.pool.indexOf(element)
        if (nameIndex < 0) return emptyList()
        val result = mutableListOf<Map<String, String?>>()
        for (index in 0 until xml.chunkCount) {
            if (xml.chunkTypeAt(index) != BinaryXml.RES_XML_START_ELEMENT_TYPE) continue
            if (xml.chunkNameIndexAt(index) != nameIndex) continue
            val attributes = linkedMapOf<String, String?>()
            for (i in 0 until xml.attributeCountAt(index)) {
                val attribute = xml.attributeAt(index, i) ?: continue
                val key = xml.pool[attribute.name] ?: continue
                attributes[key] = when (attribute.type) {
                    BinaryXml.TYPE_STRING -> xml.pool[attribute.data]
                    BinaryXml.TYPE_INT_BOOLEAN -> if (attribute.data != 0) "true" else "false"
                    0x10, 0x11 -> attribute.data.toString()
                    else -> null
                }
            }
            result += attributes
        }
        return result
    }

    fun elementNames(manifest: ByteArray): List<String> {
        val xml = BinaryXml.parse(manifest)
        return (0 until xml.chunkCount)
            .filter { xml.chunkTypeAt(it) == BinaryXml.RES_XML_START_ELEMENT_TYPE }
            .mapNotNull { xml.pool[xml.chunkNameIndexAt(it)] }
    }
}

private const val TYPE_INT = 0x10
private const val TYPE_INT_HEX = 0x11
