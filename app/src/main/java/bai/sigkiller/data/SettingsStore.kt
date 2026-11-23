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

package bai.sigkiller.data

import android.content.Context
import androidx.core.content.edit
import bai.sigkiller.engine.PatchConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 弹窗里那一组注入参数，外加签名与输出相关的两个开关。 */
data class InjectSettings(
    val injectedClass: String = PatchConfig.DEFAULT_INJECTED_CLASS,
    val useOriginalApk: Boolean = true,
    val soName: String = PatchConfig.DEFAULT_SO_NAME,
    val originAssetPath: String = PatchConfig.DEFAULT_ORIGIN_ASSET,
    val originExtractPath: String = PatchConfig.DEFAULT_ORIGIN_EXTRACT,
    val autoSign: Boolean = true,
    val renamePackage: Boolean = false,
    val newPackage: String = "",
) {

    fun toPatchConfig(): PatchConfig = PatchConfig(
        injectedClass = injectedClass.trim(),
        useOriginalApk = useOriginalApk,
        soName = soName.trim(),
        originAssetPath = originAssetPath.trim(),
        originExtractPath = originExtractPath.trim(),
        autoSign = autoSign,
        newPackageName = newPackage.trim().takeIf { renamePackage && it.isNotEmpty() },
    )

    fun errors(): List<String> = toPatchConfig().validate()
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences("sigkiller", Context.MODE_PRIVATE)

    private val _inject = MutableStateFlow(readInject())
    val inject: StateFlow<InjectSettings> = _inject.asStateFlow()

    private val _saveToPublic = MutableStateFlow(prefs.getBoolean(KEY_PUBLIC_OUTPUT, true))
    val saveToPublic: StateFlow<Boolean> = _saveToPublic.asStateFlow()

    private val _customKeystore = MutableStateFlow(prefs.getString(KEY_KEYSTORE, null))
    val customKeystore: StateFlow<String?> = _customKeystore.asStateFlow()

    private val _customKeystorePassword = MutableStateFlow(prefs.getString(KEY_KEYSTORE_PASS, null).orEmpty())
    val customKeystorePassword: StateFlow<String> = _customKeystorePassword.asStateFlow()

    fun saveInject(settings: InjectSettings) {
        prefs.edit {
            putString(KEY_INJECT_CLASS, settings.injectedClass)
            putBoolean(KEY_USE_ORIGIN, settings.useOriginalApk)
            putString(KEY_SO_NAME, settings.soName)
            putString(KEY_ORIGIN_ASSET, settings.originAssetPath)
            putString(KEY_ORIGIN_FILE, settings.originExtractPath)
            putBoolean(KEY_AUTO_SIGN, settings.autoSign)
            putBoolean(KEY_RENAME, settings.renamePackage)
            putString(KEY_NEW_PACKAGE, settings.newPackage)
        }
        _inject.value = settings
    }

    fun setSaveToPublic(value: Boolean) {
        prefs.edit { putBoolean(KEY_PUBLIC_OUTPUT, value) }
        _saveToPublic.value = value
    }

    fun setCustomKeystore(path: String?, password: String) {
        prefs.edit {
            putString(KEY_KEYSTORE, path)
            putString(KEY_KEYSTORE_PASS, password)
        }
        _customKeystore.value = path
        _customKeystorePassword.value = password
    }

    private fun readInject(): InjectSettings = InjectSettings(
        injectedClass = prefs.getString(KEY_INJECT_CLASS, null) ?: PatchConfig.DEFAULT_INJECTED_CLASS,
        useOriginalApk = prefs.getBoolean(KEY_USE_ORIGIN, true),
        soName = prefs.getString(KEY_SO_NAME, null) ?: PatchConfig.DEFAULT_SO_NAME,
        originAssetPath = prefs.getString(KEY_ORIGIN_ASSET, null) ?: PatchConfig.DEFAULT_ORIGIN_ASSET,
        originExtractPath = prefs.getString(KEY_ORIGIN_FILE, null) ?: PatchConfig.DEFAULT_ORIGIN_EXTRACT,
        autoSign = prefs.getBoolean(KEY_AUTO_SIGN, true),
        renamePackage = prefs.getBoolean(KEY_RENAME, false),
        newPackage = prefs.getString(KEY_NEW_PACKAGE, null).orEmpty(),
    )

    private companion object {
        const val KEY_INJECT_CLASS = "inject_class"
        const val KEY_USE_ORIGIN = "use_origin"
        const val KEY_SO_NAME = "so_name"
        const val KEY_ORIGIN_ASSET = "origin_asset"
        const val KEY_ORIGIN_FILE = "origin_file"
        const val KEY_AUTO_SIGN = "auto_sign"
        const val KEY_RENAME = "rename_package"
        const val KEY_NEW_PACKAGE = "new_package"
        const val KEY_PUBLIC_OUTPUT = "public_output"
        const val KEY_KEYSTORE = "custom_keystore"
        const val KEY_KEYSTORE_PASS = "custom_keystore_pass"
    }
}
