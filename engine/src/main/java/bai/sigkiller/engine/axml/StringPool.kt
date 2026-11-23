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
 * AXML / resources.arsc 共用的字符串池（ResStringPool）。
 *
 * 只做两件事：解码成 List<String>、按原编码写回；并支持「在尾部追加」。
 * 追加而不是重排，是为了让文件里已有的所有字符串索引继续有效 —— 这样清单里
 * 成千上万个我们并不关心的属性可以一个字节都不用动。
 */
internal class StringPool(
    val utf8: Boolean,
    strings: List<String>,
    private val styles: ByteArray? = null,
    private val styleOffsets: IntArray = IntArray(0),
) {

    private val items = strings.toMutableList()

    var modified: Boolean = false
        private set

    val size: Int get() = items.size

    operator fun get(index: Int): String? = items.getOrNull(index)

    fun indexOf(value: String): Int = items.indexOf(value)

    /** 已有就直接复用，没有才追加。返回字符串索引。 */
    fun obtain(value: String): Int {
        val existing = items.indexOf(value)
        if (existing >= 0) return existing
        items.add(value)
        modified = true
        return items.size - 1
    }

    /** 整池替换（arsc 改包名用）。字符串数量与顺序保持不变，所以索引全部继续有效。 */
    fun replaceAll(transform: (String) -> String) {
        for (i in items.indices) {
            val old = items[i]
            val new = transform(old)
            if (new != old) {
                items[i] = new
                modified = true
            }
        }
    }

    fun isAttributeName(index: Int): Boolean = index in items.indices

    companion object {

        fun parse(data: ByteArray, offset: Int, chunkSize: Int): StringPool {
            val stringCount = readInt(data, offset + 8)
            val styleCount = readInt(data, offset + 12)
            val flags = readInt(data, offset + 16)
            val stringsStart = readInt(data, offset + 20)
            val stylesStart = readInt(data, offset + 24)
            val utf8 = (flags and FLAG_UTF8) != 0

            val strings = ArrayList<String>(stringCount)
            for (i in 0 until stringCount) {
                val stringOffset = readInt(data, offset + 28 + i * 4)
                strings.add(
                    if (utf8) {
                        readUtf8(data, offset + stringsStart + stringOffset)
                    } else {
                        readUtf16(data, offset + stringsStart + stringOffset)
                    },
                )
            }

            val styles = if (styleCount > 0 && stylesStart != 0) {
                data.copyOfRange(offset + stylesStart, offset + chunkSize)
            } else {
                null
            }
            val styleOffsets = if (styleCount > 0) {
                IntArray(styleCount) { readInt(data, offset + 28 + stringCount * 4 + it * 4) }
            } else {
                IntArray(0)
            }
            return StringPool(utf8, strings, styles, styleOffsets)
        }

        /** 从一段原始的 ResStringPool 结构里把字符串读出来（用于 arsc 里的多个池）。 */
        fun parsePool(data: ByteArray, offset: Int): List<String> =
            parse(data, offset, readInt(data, offset + 4)).let { pool -> List(pool.size) { pool[it] ?: "" } }

        private const val FLAG_SORTED = 0x1
        private const val FLAG_UTF8 = 0x100
    }

    fun encode(): ByteArray {
        val stringData = ByteArrayOutputStream(items.size * 16 + 256)
        val offsets = IntArray(items.size)
        for (i in items.indices) {
            offsets[i] = stringData.size()
            val value = items[i]
            if (utf8) {
                val bytes = value.toByteArray(Charsets.UTF_8)
                writeLength8(stringData, value.length)
                writeLength8(stringData, bytes.size)
                stringData.write(bytes)
                stringData.write(0)
            } else {
                writeLength16(stringData, value.length)
                for (ch in value) {
                    stringData.write(ch.code and 0xFF)
                    stringData.write(ch.code shr 8 and 0xFF)
                }
                stringData.write(0)
                stringData.write(0)
            }
        }
        while (stringData.size() % 4 != 0) stringData.write(0)

        val stringsBytes = stringData.toByteArray()
        val stylesBytes = styles
        val styleOffsetsCount = styleOffsets.size

        val headerSize = 28
        val stringsStart = headerSize + items.size * 4 + styleOffsetsCount * 4
        val stylesStart = if (stylesBytes != null && stylesBytes.isNotEmpty()) stringsStart + stringsBytes.size else 0
        val totalSize = stringsStart + stringsBytes.size + (if (stylesStart != 0) stylesBytes!!.size else 0)

        val out = ByteArrayOutputStream(totalSize)
        out.writeInt16(RES_STRING_POOL_TYPE)
        out.writeInt16(headerSize)
        out.writeInt32(totalSize)
        out.writeInt32(items.size)
        out.writeInt32(styleOffsetsCount)
        // 追加过字符串之后池子已经不是「已排序」状态，必须把 SORTED 位清掉，
        // 否则框架按二分查找取字符串会取错
        out.writeInt32(if (utf8) FLAG_UTF8 else 0)
        out.writeInt32(stringsStart)
        out.writeInt32(stylesStart)
        for (offset in offsets) out.writeInt32(offset)
        for (offset in styleOffsets) out.writeInt32(offset)
        out.write(stringsBytes)
        if (stylesStart != 0) out.write(stylesBytes!!)
        return out.toByteArray()
    }

    private fun writeLength8(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            out.write(length)
        } else {
            out.write(length shr 8 or 0x80)
            out.write(length and 0xFF)
        }
    }

    private fun writeLength16(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x8000) {
            out.writeInt16(length)
        } else {
            out.writeInt16(length shr 16 or 0x8000)
            out.writeInt16(length and 0xFFFF)
        }
    }

}

private fun readUtf8(data: ByteArray, offset: Int): String {
    var cursor = offset
        cursor += if ((data[cursor].toInt() and 0x80) != 0) 2 else 1
        var byteLength = data[cursor].toInt() and 0xFF
        if ((byteLength and 0x80) != 0) {
            byteLength = ((byteLength and 0x7F) shl 8) or (data[cursor + 1].toInt() and 0xFF)
            cursor += 2
        } else {
            cursor += 1
        }
        return String(data, cursor, byteLength, Charsets.UTF_8)
    }

private fun readUtf16(data: ByteArray, offset: Int): String {
    var cursor = offset
        var charLength = readUInt16(data, cursor)
        if ((charLength and 0x8000) != 0) {
            charLength = ((charLength and 0x7FFF) shl 16) or readUInt16(data, cursor + 2)
            cursor += 4
        } else {
            cursor += 2
        }
        val chars = CharArray(charLength) {
            val lo = data[cursor + it * 2].toInt() and 0xFF
            val hi = data[cursor + it * 2 + 1].toInt() and 0xFF
            (hi shl 8 or lo).toChar()
        }
    return String(chars)
}

internal const val RES_STRING_POOL_TYPE = 0x0001
internal const val RES_XML_TYPE = 0x0003

internal fun readInt(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)

internal fun readUInt16(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

internal fun writeInt32(data: ByteArray, offset: Int, value: Int) {
    data[offset] = (value and 0xFF).toByte()
    data[offset + 1] = (value shr 8 and 0xFF).toByte()
    data[offset + 2] = (value shr 16 and 0xFF).toByte()
    data[offset + 3] = (value shr 24 and 0xFF).toByte()
}

internal fun ByteArrayOutputStream.writeInt16(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
}

internal fun ByteArrayOutputStream.writeInt32(value: Int) {
    write(value and 0xFF)
    write(value shr 8 and 0xFF)
    write(value shr 16 and 0xFF)
    write(value shr 24 and 0xFF)
}
