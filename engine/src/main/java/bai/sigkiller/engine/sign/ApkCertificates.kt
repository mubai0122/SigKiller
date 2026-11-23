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

package bai.sigkiller.engine.sign

import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * 取原包的签名证书。
 *
 * 两条路：老包走 META-INF 里的 PKCS#7（v1），新包（只有 v2/v3）走 apksig 的验证器。
 * 拿到的证书会以 base64 写进注入的 meta-data，运行时就是用它冒充原签名 ——
 * 所以「假装成原签名」这件事本身不需要把整个原包塞进去，只有需要骗过
 * 按文件读签名的检查时才要 使用原包过签。
 */
object ApkCertificates {

    fun primary(apk: File): X509Certificate {
        fromMetaInf(apk)?.let { return it }
        return fromSigningBlock(apk)
    }

    private fun fromMetaInf(apk: File): X509Certificate? {
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.startsWith("META-INF/", ignoreCase = true)) continue
                if (!entry.name.endsWith(".RSA", true) && !entry.name.endsWith(".DSA", true) &&
                    !entry.name.endsWith(".EC", true)
                ) {
                    continue
                }
                runCatching {
                    val factory = CertificateFactory.getInstance("X.509")
                    zip.getInputStream(entry).use { input ->
                        factory.generateCertificate(ByteArrayInputStream(input.readBytes())) as X509Certificate
                    }
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun fromSigningBlock(apk: File): X509Certificate {
        val result = runCatching {
            ApkVerifier.Builder(apk).build().verify()
        }.getOrElse { throw IllegalStateException("读取原签名失败：${it.message}", it) }
        return result.signerCertificates.firstOrNull()
            ?: throw IllegalStateException("这个 APK 读不到签名证书，可能没有签名或是分卷包")
    }
}
