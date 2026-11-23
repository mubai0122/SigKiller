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

import java.io.ByteArrayOutputStream

/**
 * 二进制 AndroidManifest.xml 的「外科手术式」编辑器。
 *
 * 不把整棵树反序列化再重写 —— 那样容易丢掉我们不认识的属性、typed value 和行号信息。
 * 这里只做三件必须做的事：
 *   1. 往字符串池尾部追加新字符串（已有索引全部保持不变）；
 *   2. 改某个属性的字符串值（改包名用）；
 *   3. 在 `</application>` 之前插入新的 `provider` / `meta-data` 节点。
 * 其余字节原封不动。
 */
internal class BinaryXml private constructor(
    val pool: StringPool,
    private var resourceIds: IntArray,
    private val chunks: MutableList<Chunk>,
    private val headerBytes: ByteArray,
) {

    class Chunk(val type: Int, var bytes: ByteArray)

    fun toByteArray(): ByteArray {
        val poolBytes = pool.encode()
        val resMapBytes = encodeResourceMap()
        var total = 8 + poolBytes.size + resMapBytes.size
        for (chunk in chunks) total += chunk.bytes.size

        val header = headerBytes.copyOf()
        writeInt32(header, 4, total)
        val out = ByteArrayOutputStream(total)
        out.write(header)
        out.write(poolBytes)
        out.write(resMapBytes)
        for (chunk in chunks) out.write(chunk.bytes)
        return out.toByteArray()
    }

    private fun encodeResourceMap(): ByteArray {
        val idCount = resourceIds.size
        val size = 8 + idCount * 4
        val out = ByteArrayOutputStream(size)
        out.writeInt16(RES_XML_RESOURCE_MAP_TYPE)
        out.writeInt16(8)
        out.writeInt32(size)
        for (id in resourceIds) out.writeInt32(id)
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- 查找

    fun indexOfElement(name: String): Int {
        val nameIndex = pool.indexOf(name)
        if (nameIndex < 0) return -1
        return chunks.indexOfFirst { it.type == RES_XML_START_ELEMENT_TYPE && elementNameIndex(it.bytes) == nameIndex }
    }

    fun indexOfEndElement(name: String): Int {
        val nameIndex = pool.indexOf(name)
        if (nameIndex < 0) return -1
        return chunks.indexOfFirst { it.type == RES_XML_END_ELEMENT_TYPE && elementNameIndex(it.bytes) == nameIndex }
    }

    private fun elementNameIndex(bytes: ByteArray): Int = readInt(bytes, 20)

    fun chunkBytes(index: Int): ByteArray = chunks[index].bytes

    val chunkCount: Int get() = chunks.size

    fun chunkTypeAt(index: Int): Int = chunks[index].type

    fun chunkNameIndexAt(index: Int): Int = elementNameIndex(chunks[index].bytes)

    class AttributeRef(val namespace: Int, val name: Int, val type: Int, val data: Int)

    fun attributeCountAt(index: Int): Int {
        val bytes = chunks[index].bytes
        if (bytes.size < 36) return 0
        return readUInt16(bytes, 28)
    }

    fun attributeAt(index: Int, attributeIndex: Int): AttributeRef? {
        val bytes = chunks[index].bytes
        if (bytes.size < 36) return null
        val attrStart = readUInt16(bytes, 24)
        val attrSize = readUInt16(bytes, 26)
        val offset = 16 + attrStart + attributeIndex * attrSize
        if (offset + 20 > bytes.size) return null
        return AttributeRef(
            namespace = readInt(bytes, offset),
            name = readInt(bytes, offset + 4),
            type = bytes[offset + 15].toInt() and 0xFF,
            data = readInt(bytes, offset + 16),
        )
    }

    // ---------------------------------------------------------------- 编辑

    /** 在指定 chunk 之前插入若干节点。 */
    fun insertChunks(beforeIndex: Int, newChunks: List<Chunk>) {
        chunks.addAll(beforeIndex, newChunks)
    }

    /**
     * 把某个元素上名为 [attributeName] 的属性改成新的字符串值。
     * 属性不存在或元素不存在时返回 false。
     */
    fun setStringAttribute(elementName: String, attributeName: String, value: String, namespace: String? = null): Boolean {
        val elementIndex = indexOfElement(elementName)
        if (elementIndex < 0) return false
        val bytes = chunks[elementIndex].bytes
        val attrNameIndex = pool.indexOf(attributeName)
        if (attrNameIndex < 0) return false
        val nsIndex = if (namespace == null) -1 else pool.indexOf(namespace)

        val attrStart = readUInt16(bytes, 24).toInt()
        val attrSize = readUInt16(bytes, 26).toInt()
        val attrCount = readUInt16(bytes, 28).toInt()
        val base = 16 + attrStart
        for (i in 0 until attrCount) {
            val attr = base + i * attrSize
            if (attr + 20 > bytes.size) break
            val attrNs = readInt(bytes, attr)
            val nameIdx = readInt(bytes, attr + 4)
            if (nameIdx != attrNameIndex) continue
            if (namespace != null && attrNs != nsIndex) continue
            if (namespace == null && attrNs != -1) continue
            val valueIndex = pool.obtain(value)
            writeInt32(bytes, attr + 8, valueIndex) // rawValue
            bytes[attr + 15] = TYPE_STRING.toByte() // dataType
            writeInt32(bytes, attr + 16, valueIndex) // data
            return true
        }
        return false
    }

    /**
     * 往已有元素上加一个属性。属性区紧跟在 attrExt 之后，所以只要把原属性原样搬到
     * 新 chunk 尾部再加一条，同时改掉 attributeCount 与 size 即可。
     */
    fun addStringAttribute(elementName: String, attributeName: String, value: String, namespace: String = ANDROID_NS): Boolean {
        val elementIndex = indexOfElement(elementName)
        if (elementIndex < 0) return false
        val old = chunks[elementIndex].bytes
        if (old.size < 36) return false
        val attrStart = readUInt16(old, 24)
        val attrSize = readUInt16(old, 26)
        val attrCount = readUInt16(old, 28)
        if (attrSize != 20) return false
        val attrBytes = attrCount * attrSize
        val attrsEnd = 16 + attrStart + attrBytes
        if (attrsEnd > old.size) return false

        val nameIndex = pool.obtain(attributeName)
        val valueIndex = pool.obtain(value)
        val nsIndex = pool.obtain(namespace)

        val newSize = old.size + attrSize
        val bytes = ByteArray(newSize)
        old.copyInto(bytes, 0, 0, attrsEnd)
        writeInt32(bytes, 4, newSize)
        writeInt32(bytes, 28, attrCount + 1)
        writeInt32(bytes, 32, 0)
        writeInt32(bytes, attrsEnd, nsIndex)
        writeInt32(bytes, attrsEnd + 4, nameIndex)
        writeInt32(bytes, attrsEnd + 8, valueIndex)
        bytes[attrsEnd + 12] = 8
        bytes[attrsEnd + 13] = 0
        bytes[attrsEnd + 15] = TYPE_STRING.toByte()
        writeInt32(bytes, attrsEnd + 16, valueIndex)

        chunks[elementIndex] = Chunk(RES_XML_START_ELEMENT_TYPE, bytes)
        return true
    }

    // ---------------------------------------------------------------- 构造节点

    fun androidNamespaceIndex(): Int =
        pool.obtain(ANDROID_NS)

    fun stringIndex(value: String): Int = pool.obtain(value)

    fun startElement(
        name: String,
        attributes: List<NewAttribute>,
        lineNumber: Int = 1,
    ): Chunk {
        val nameIndex = pool.obtain(name)
        val attrSize = 20
        val bodySize = 20 + attributes.size * attrSize
        val size = 16 + bodySize
        val bytes = ByteArray(size)
        writeInt32(bytes, 0, (RES_XML_START_ELEMENT_TYPE or (16 shl 16)))
        writeInt32(bytes, 4, size)
        writeInt32(bytes, 8, lineNumber)
        writeInt32(bytes, 12, -1) // comment
        writeInt32(bytes, 16, -1) // ns
        writeInt32(bytes, 20, nameIndex)
        // ResXMLTree_attrExt：attributeStart(u16) attributeSize(u16) attributeCount(u16)
        //                    idIndex(u16) classIndex(u16) styleIndex(u16)
        writeInt32(bytes, 24, 20 or (20 shl 16))
        writeInt32(bytes, 28, attributes.size)
        // id/class/style 三个索引保持 0：清单解析不走这三条捷径
        writeInt32(bytes, 32, 0)
        attributes.forEachIndexed { i, attribute ->
            val base = 36 + i * attrSize
            writeInt32(bytes, base, attribute.namespaceIndex)
            writeInt32(bytes, base + 4, attribute.nameIndex)
            writeInt32(bytes, base + 8, attribute.rawValueIndex)
            bytes[base + 12] = 8 // typedValue.size
            bytes[base + 13] = 0 // res0
            bytes[base + 15] = attribute.type.toByte()
            writeInt32(bytes, base + 16, attribute.data)
        }
        return Chunk(RES_XML_START_ELEMENT_TYPE, bytes)
    }

    fun endElement(name: String, lineNumber: Int = 1): Chunk {
        val nameIndex = pool.obtain(name)
        val bytes = ByteArray(24)
        writeInt32(bytes, 0, (RES_XML_END_ELEMENT_TYPE or (16 shl 16)))
        writeInt32(bytes, 4, 24)
        writeInt32(bytes, 8, lineNumber)
        writeInt32(bytes, 12, -1)
        writeInt32(bytes, 16, -1)
        writeInt32(bytes, 20, nameIndex)
        return Chunk(RES_XML_END_ELEMENT_TYPE, bytes)
    }

    /** 登记一个属性名的资源 ID：框架按资源 ID 找 android:name / android:value 这类属性。 */
    fun registerAttributeId(name: String, resourceId: Int) {
        val index = pool.obtain(name)
        if (index < resourceIds.size && resourceIds[index] == resourceId) return
        if (index < resourceIds.size && resourceIds[index] != 0) return
        if (index >= resourceIds.size) {
            resourceIds = resourceIds.copyOf(index + 1)
        }
        resourceIds[index] = resourceId
    }

    fun stringAttribute(name: String, value: String): NewAttribute {
        val nameIndex = pool.obtain(name)
        val valueIndex = pool.obtain(value)
        return NewAttribute(androidNamespaceIndex(), nameIndex, valueIndex, TYPE_STRING, valueIndex)
    }

    fun booleanAttribute(name: String, value: Boolean): NewAttribute {
        val nameIndex = pool.obtain(name)
        return NewAttribute(androidNamespaceIndex(), nameIndex, -1, TYPE_INT_BOOLEAN, if (value) -1 else 0)
    }

    class NewAttribute(
        val namespaceIndex: Int,
        val nameIndex: Int,
        val rawValueIndex: Int,
        val type: Int,
        val data: Int,
    )

    companion object {

        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_END_ELEMENT_TYPE = 0x0103
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

        const val TYPE_STRING = 0x03
        const val TYPE_INT_BOOLEAN = 0x12

        fun parse(data: ByteArray): BinaryXml {
            require(data.size >= 8) { "AndroidManifest.xml 太小" }
            val type = readUInt16(data, 0)
            require(type == RES_XML_TYPE) { "不是二进制 XML（type=0x${type.toString(16)}）" }
            val headerSize = readUInt16(data, 2)
            val totalSize = readInt(data, 4)
            val header = data.copyOfRange(0, headerSize)

            var pool: StringPool? = null
            var resourceIds = IntArray(0)
            val chunks = mutableListOf<Chunk>()

            var offset = headerSize
            val end = minOf(totalSize, data.size)
            while (offset + 8 <= end) {
                val chunkType = readUInt16(data, offset)
                val chunkHeaderSize = readUInt16(data, offset + 2)
                val chunkSize = readInt(data, offset + 4)
                if (chunkSize < 8 || offset + chunkSize > data.size) break
                when (chunkType) {
                    RES_STRING_POOL_TYPE -> pool = StringPool.parse(data, offset, chunkSize)
                    RES_XML_RESOURCE_MAP_TYPE -> {
                        val count = (chunkSize - chunkHeaderSize) / 4
                        resourceIds = IntArray(count) { readInt(data, offset + chunkHeaderSize + it * 4) }
                    }
                    else -> chunks.add(Chunk(chunkType, data.copyOfRange(offset, offset + chunkSize)))
                }
                offset += chunkSize
            }
            requireNotNull(pool) { "清单里没有字符串池" }
            return BinaryXml(pool, resourceIds, chunks, header)
        }
    }
}
