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

package bai.sigkiller.engine.arsc

import bai.sigkiller.engine.axml.RES_STRING_POOL_TYPE
import bai.sigkiller.engine.axml.StringPool
import bai.sigkiller.engine.axml.readInt
import bai.sigkiller.engine.axml.readUInt16
import bai.sigkiller.engine.axml.writeInt32
import java.io.ByteArrayOutputStream

/**
 * resources.arsc 的包名改写。
 *
 * 只动两个地方：ResTable_package 里定长的 128 个 UTF-16 包名字段，以及全局字符串池里
 * 等于旧包名 / 以「旧包名.」「旧包名/」开头的字符串（authorities、组件名常见这种写法）。
 * 资源 ID 不变，所以清单里的 @0x7f… 引用全部继续有效。
 */
internal object ResourceTable {

    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val PACKAGE_NAME_CHARS = 128

    fun renamePackage(data: ByteArray, from: String, to: String): ByteArray {
        if (readUInt16(data, 0) != RES_TABLE_TYPE) return data

        val headerSize = readUInt16(data, 2)
        val end = minOf(readInt(data, 4), data.size)
        val header = data.copyOfRange(0, headerSize)

        var pool: StringPool? = null
        val rest = mutableListOf<ByteArray>()
        var offset = headerSize
        while (offset + 8 <= end) {
            val type = readUInt16(data, offset)
            val size = readInt(data, offset + 4)
            if (size < 8 || offset + size > data.size) break
            if (type == RES_STRING_POOL_TYPE) {
                pool = StringPool.parse(data, offset, size)
            } else {
                val chunk = data.copyOfRange(offset, offset + size)
                if (type == RES_TABLE_PACKAGE_TYPE) patchPackageNameField(chunk, to)
                rest += chunk
            }
            offset += size
        }

        val poolChunk = pool ?: return data
        poolChunk.replaceAll { value -> renameValue(value, from, to) }
        val poolBytes = poolChunk.encode()

        val out = ByteArrayOutputStream(data.size)
        out.write(header)
        out.write(poolBytes)
        rest.forEach { out.write(it) }
        val bytes = out.toByteArray()
        writeInt32(bytes, 4, bytes.size)
        return bytes
    }

    private fun renameValue(value: String, from: String, to: String): String = when {
        value == from -> to
        value.startsWith("$from.") -> to + value.substring(from.length)
        value.startsWith("$from/") -> to + value.substring(from.length)
        else -> value
    }

    /** ResTable_package：header 之后是 id(u32) + 128 个 UTF-16 字符的包名。 */
    private fun patchPackageNameField(chunk: ByteArray, name: String) {
        val start = 12
        val byteCount = PACKAGE_NAME_CHARS * 2
        if (start + byteCount > chunk.size) return
        java.util.Arrays.fill(chunk, start, start + byteCount, 0)
        for (i in 0 until minOf(name.length, PACKAGE_NAME_CHARS)) {
            val ch = name[i].code
            chunk[start + i * 2] = (ch and 0xFF).toByte()
            chunk[start + i * 2 + 1] = (ch shr 8 and 0xFF).toByte()
        }
    }
}
