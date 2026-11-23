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

import java.io.File

/** 一次补丁任务的全部可调参数，对应界面上的「去除签名校验」弹窗。 */
data class PatchConfig(
    /** 注入类名前缀，例如 bai.killer → 真正注入的组件是 bai.killer.KillerProvider */
    val injectedClass: String = DEFAULT_INJECTED_CLASS,
    /** 是否使用原包过签：把原始 APK 塞进 assets，运行时解包并劫持 open() */
    val useOriginalApk: Boolean = true,
    /** 注入的 so 文件名（不含 lib 前缀与 .so 后缀） */
    val soName: String = DEFAULT_SO_NAME,
    /** 原包在 APK 内的路径 */
    val originAssetPath: String = DEFAULT_ORIGIN_ASSET,
    /** 原包在设备上的解压路径（相对应用数据目录） */
    val originExtractPath: String = DEFAULT_ORIGIN_EXTRACT,
    /** 完成后自动签名 */
    val autoSign: Boolean = true,
    /** 自定义包名，null 表示不改包名 */
    val newPackageName: String? = null,
) {

    val providerClass: String get() = "$injectedClass.KillerProvider"

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!CLASS_NAME.matches(injectedClass)) {
            errors.add("注入类名不合法：需要是 dot 分隔的合法 Java 包名，例如 bai.killer")
        }
        if (!SIMPLE_NAME.matches(soName)) {
            errors.add("so 库名不合法：只能包含字母、数字和下划线")
        }
        if (!originAssetPath.startsWith("assets/")) {
            errors.add("注入原包路径必须以 assets/ 开头")
        }
        if (originAssetPath.contains("..") || originExtractPath.contains("..")) {
            errors.add("路径里不能出现 ..")
        }
        if (originExtractPath.startsWith("/") || originExtractPath.isBlank()) {
            errors.add("原包解压路径必须是相对应用数据目录的路径，例如 files/origin.apk")
        }
        val pkg = newPackageName
        if (pkg != null && !PACKAGE_NAME.matches(pkg)) {
            errors.add("自定义包名不合法：至少需要两段，例如 com.demo.app")
        }
        return errors
    }

    companion object {
        const val DEFAULT_INJECTED_CLASS = "bai.killer"
        const val DEFAULT_SO_NAME = "SignatureKiller"
        const val DEFAULT_ORIGIN_ASSET = "assets/origin.apk"
        const val DEFAULT_ORIGIN_EXTRACT = "files/origin.apk"

        private val CLASS_NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*""")
        private val SIMPLE_NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")
    }
}

enum class PatchStage(val title: String) {
    PREPARE("准备"),
    ANALYZE("解析 APK"),
    DEX("注入 dex"),
    MANIFEST("改写清单"),
    ORIGIN("嵌入原包"),
    NATIVE("注入 so"),
    REBUILD("重新打包"),
    SIGN("签名"),
    VERIFY("校验"),
    DONE("完成"),
}

data class PatchProgress(
    val stage: PatchStage,
    val fraction: Float,
    val message: String,
    /** 打包阶段已写入的未压缩字节数；-1 表示该进度与打包字节无关 */
    val packedBytes: Long = -1L,
    /** 打包阶段预计写入的未压缩总字节数 */
    val totalBytes: Long = -1L,
) {
    /** 打包阶段的完成比例，供详情页单独画一条实时压缩进度条 */
    val packRatio: Float get() = if (totalBytes > 0) (packedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
}

data class PatchResult(
    val output: File,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signed: Boolean,
    val sizeBytes: Long,
    val log: List<String>,
)

class PatchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 注入用的载荷：编译好的 killer.dex 与各 ABI 的 so。 */
class Payload(
    val dex: ByteArray,
    val natives: Map<String, ByteArray>,
    /** killer.dex 里那些类的包名，用于按用户设置改名 */
    val dexPackage: String = "bai.killer",
    /** 与 JNI 导出符号绑定、必须保持原包名的类 */
    val pinnedClasses: Set<String> = setOf("bai.killer.Native"),
)
