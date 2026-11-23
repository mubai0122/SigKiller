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

package bai.sigkiller.engine.dex

import java.io.File
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.rewriter.DexRewriter
import org.jf.dexlib2.rewriter.Rewriter
import org.jf.dexlib2.rewriter.RewriterModule
import org.jf.dexlib2.rewriter.Rewriters

/**
 * 把预编译的 killer.dex 整体挪到用户指定的包名下。
 *
 * dex 不是 zip，没法靠改个字符串就换包名：type_ids 必须按字典序排列，
 * 改完要重建整个池子和所有引用。这里交给 dexlib2 的 DexRewriter，
 * 它会把 descriptor、方法签名、注解、debug info 里的类型一并重写。
 *
 * 唯一的例外是和 JNI 导出符号绑定的类（bai.killer.Native）——符号名是
 * Java_bai_killer_Native_xxx，类名一改就链接不上，所以钉住不动。
 */
internal object DexPayload {

    fun renamePackage(
        dex: ByteArray,
        fromPackage: String,
        toPackage: String,
        pinnedClasses: Set<String>,
    ): ByteArray {
        if (fromPackage == toPackage) return dex

        val fromPrefix = "L" + fromPackage.replace('.', '/') + "/"
        val toPrefix = "L" + toPackage.replace('.', '/') + "/"
        val pinnedDescriptors = pinnedClasses.map { "L" + it.replace('.', '/') + ";" }.toSet()

        val source = DexBackedDexFile(Opcodes.getDefault(), dex)
        val module = object : RewriterModule() {
            override fun getTypeRewriter(rewriters: Rewriters): Rewriter<String> =
                Rewriter { type ->
                    when {
                        type in pinnedDescriptors -> type
                        type.startsWith(fromPrefix) -> toPrefix + type.substring(fromPrefix.length)
                        else -> type
                    }
                }
        }
        val rewritten = DexRewriter(module).dexFileRewriter.rewrite(source)

        val temp = File.createTempFile("sigkiller-", ".dex")
        return try {
            DexFileFactory.writeDexFile(temp.absolutePath, rewritten)
            temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    /** 读一个 dex 里的全部类描述符，用于自检与日志。 */
    fun classDescriptors(dex: ByteArray): List<String> =
        DexBackedDexFile(Opcodes.getDefault(), dex).classes.map { it.type }
}
