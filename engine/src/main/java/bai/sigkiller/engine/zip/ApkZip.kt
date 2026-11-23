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

package bai.sigkiller.engine.zip

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 对打包过程的一处修改：整条替换、用流写入、或者删掉。 */
internal sealed class ZipEdit {
    abstract val name: String

    class Bytes(override val name: String, val data: ByteArray, val store: Boolean = false) : ZipEdit()

    class Stream(
        override val name: String,
        /** 会写进压缩流的未压缩字节数，用于计算打包进度 */
        val sizeBytes: Long,
        val deflated: Boolean = true,
        val writer: (OutputStream) -> Unit,
    ) : ZipEdit()
}

/**
 * 写入压缩流时统计未压缩字节，用来驱动详情页的实时打包进度。
 *
 * 它只是把写操作原样转发给 ZipOutputStream，额外数一下经过的字节数；
 * putNextEntry/closeEntry 仍由调用方直接在 ZipOutputStream 上做。
 */
private class CountingOutputStream(
    private val delegate: OutputStream,
    private val onBytes: (Long) -> Unit,
) : OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
        onBytes(1L)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        onBytes(len.toLong())
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}

internal object ApkZip {

    /**
     * 以 [source] 为底重新打包到 [target]：保持条目顺序与压缩方式，
     * 只替换/追加 [edits] 里点名的条目，[drop] 返回 true 的条目直接丢掉
     * （旧签名文件必须丢，否则 v1 校验会把它们当成本次签名的一部分）。
     *
     * [onProgress] 回调（已写未压缩字节, 预计总未压缩字节），每写满一个缓冲区触发一次，
     * 打包阶段可以据此把进度条连续填满，而不是等整包写完才跳一格。
     */
    fun rebuild(
        source: File,
        target: File,
        edits: List<ZipEdit>,
        drop: (String) -> Boolean,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val editByName = edits.associateBy { it.name }
        val writtenNames = HashSet<String>()

        // 先估出要写出的未压缩总字节：源包里保留的条目 + 源包里没有的新增条目
        var total = 0L
        val sourceNames = HashSet<String>()
        ZipFile(source).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || drop(entry.name)) continue
                sourceNames += entry.name
                val edit = editByName[entry.name]
                total += when (edit) {
                    is ZipEdit.Bytes -> edit.data.size.toLong()
                    is ZipEdit.Stream -> edit.sizeBytes
                    null -> entry.size.coerceAtLeast(0)
                }
            }
        }
        edits.forEach { edit ->
            if (edit.name !in sourceNames) {
                total += when (edit) {
                    is ZipEdit.Bytes -> edit.data.size.toLong()
                    is ZipEdit.Stream -> edit.sizeBytes
                }
            }
        }
        if (total <= 0) total = 1L // 避免除零，空包场景下直接当 100%

        var done = 0L
        ZipOutputStream(FileOutputStream(target).buffered(1 shl 16), Charsets.UTF_8).use { out ->
            // 所有写条目的数据流都经过它：字节数累加后回报给调用方
            val sink = CountingOutputStream(out) { n ->
                done += n
                onProgress(done.coerceAtMost(total), total)
            }

            ZipFile(source).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    if (drop(entry.name)) continue
                    val edit = editByName[entry.name]
                    when (edit) {
                        is ZipEdit.Bytes -> writeBytes(out, edit.name, edit.data, edit.store, entry.time, sink)
                        is ZipEdit.Stream -> writeStream(out, edit.name, edit.deflated, entry.time, edit.writer, sink)
                        null -> copyEntry(zip, entry, out, sink)
                    }
                    writtenNames += entry.name
                }
            }
            edits.forEach { edit ->
                if (edit.name in writtenNames) return@forEach
                when (edit) {
                    is ZipEdit.Bytes -> writeBytes(out, edit.name, edit.data, edit.store, System.currentTimeMillis(), sink)
                    is ZipEdit.Stream -> writeStream(out, edit.name, edit.deflated, System.currentTimeMillis(), edit.writer, sink)
                }
            }
        }
        onProgress(total, total)
    }

    private fun copyEntry(zip: ZipFile, entry: ZipEntry, out: ZipOutputStream, sink: OutputStream) {
        val stored = entry.method == ZipEntry.STORED
        if (!stored) {
            val copy = ZipEntry(entry.name).apply {
                method = ZipEntry.DEFLATED
                time = entry.time
            }
            out.putNextEntry(copy)
            zip.getInputStream(entry).use { it.copyTo(sink, 1 shl 16) }
            out.closeEntry()
            return
        }
        // STORED 条目要预先写 CRC 和长度：先扫一遍算 CRC，再写第二遍（只有第二遍计入进度）
        val crc = CRC32()
        var size = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
                size += read
            }
        }
        val copy = ZipEntry(entry.name).apply {
            method = ZipEntry.STORED
            this.size = size
            compressedSize = size
            this.crc = crc.value
            time = entry.time
        }
        out.putNextEntry(copy)
        zip.getInputStream(entry).use { it.copyTo(sink, 1 shl 16) }
        out.closeEntry()
    }

    private fun writeBytes(
        out: ZipOutputStream,
        name: String,
        data: ByteArray,
        store: Boolean,
        time: Long,
        sink: OutputStream,
    ) {
        val entry = ZipEntry(name).apply {
            this.time = time
            if (store) {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                crc = CRC32().apply { update(data) }.value
            } else {
                method = ZipEntry.DEFLATED
            }
        }
        out.putNextEntry(entry)
        sink.write(data)
        out.closeEntry()
    }

    private fun writeStream(
        out: ZipOutputStream,
        name: String,
        deflated: Boolean,
        time: Long,
        writer: (OutputStream) -> Unit,
        sink: OutputStream,
    ) {
        // 流式写入的条目一律 deflate：只有 so 需要 STORED，而 so 本来就在内存里
        val entry = ZipEntry(name).apply {
            this.time = time
            method = if (deflated) ZipEntry.DEFLATED else ZipEntry.STORED
        }
        if (!deflated) error("流式写入不支持 STORED：$name")
        out.putNextEntry(entry)
        writer(sink)
        out.closeEntry()
    }
}
