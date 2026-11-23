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

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** 签名用的密钥：keystore 字节 + 口令。PKCS12 在 Android 与桌面 JVM 上都能读。 */
class SigningKey(
    val keystoreBytes: ByteArray,
    val storePassword: String,
    val keyPassword: String = storePassword,
    val alias: String? = null,
) {

    private class Loaded(val alias: String, val key: PrivateKey, val certificate: X509Certificate)

    private val loaded: Loaded by lazy { load() }

    val displayAlias: String get() = loaded.alias

    private fun load(): Loaded {
        val store = openStore() ?: throw IllegalArgumentException("无法识别这个 keystore（支持 PKCS12 / JKS / BKS）")
        val chosen = alias?.takeIf { store.containsAlias(it) }
            ?: store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
            ?: throw IllegalArgumentException("keystore 里没有可用的私钥条目")
        val key = store.getKey(chosen, keyPassword.toCharArray()) as? PrivateKey
            ?: throw IllegalArgumentException("条目 $chosen 不是私钥")
        val certificate = store.getCertificate(chosen) as? X509Certificate
            ?: throw IllegalArgumentException("条目 $chosen 没有证书")
        return Loaded(chosen, key, certificate)
    }

    private fun openStore(): KeyStore? {
        for (type in listOf("PKCS12", "JKS", "BKS")) {
            val store = runCatching { KeyStore.getInstance(type) }.getOrNull() ?: continue
            val ok = runCatching {
                store.load(ByteArrayInputStream(keystoreBytes), storePassword.toCharArray())
            }.isSuccess
            if (ok) return store
        }
        return null
    }

    fun signerConfig(name: String = "sigkiller"): ApkSigner.SignerConfig =
        ApkSigner.SignerConfig.Builder(name, loaded.key, listOf(loaded.certificate)).build()
}

data class VerifyReport(
    val verified: Boolean,
    val v1: Boolean,
    val v2: Boolean,
    val v3: Boolean,
    val errors: List<String>,
)

object ApkSigning {

    /**
     * 重新签名。v1 一定开：目标 APK 的 minSdk 可能低于 24，只有 v2 的包在老机器上装不上。
     * apksig 同时负责把未压缩条目重新对齐（so 按 4096 页对齐），所以这里不用先 zipalign。
     */
    fun sign(input: File, output: File, key: SigningKey, minSdk: Int = 21) {
        val signer = ApkSigner.Builder(listOf(key.signerConfig()))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setCreatedBy("SigKiller")
            .build()
        signer.sign()
    }

    fun verify(apk: File): VerifyReport {
        val result = ApkVerifier.Builder(apk).build().verify()
        return VerifyReport(
            verified = result.isVerified,
            v1 = result.isVerifiedUsingV1Scheme,
            v2 = result.isVerifiedUsingV2Scheme,
            v3 = result.isVerifiedUsingV3Scheme,
            errors = result.errors.map { it.toString() },
        )
    }
}
