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

package bai.sigkiller.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bai.sigkiller.data.InjectSettings
import bai.sigkiller.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore,
) : ViewModel() {

    val inject: StateFlow<InjectSettings> = store.inject
    val saveToPublic: StateFlow<Boolean> = store.saveToPublic
    val customKeystore: StateFlow<String?> = store.customKeystore
    val customKeystorePassword: StateFlow<String> = store.customKeystorePassword

    fun saveInject(settings: InjectSettings) = store.saveInject(settings)

    fun setSaveToPublic(value: Boolean) = store.setSaveToPublic(value)

    fun importKeystore(uri: Uri, password: String) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.filesDir, "custom-keystore.bin")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            if (path != null) store.setCustomKeystore(path, password)
        }
    }

    fun useBuiltinKeystore() = store.setCustomKeystore(null, "")

    fun clearTasksOnDisk() {
        viewModelScope.launch(Dispatchers.IO) {
            File(context.filesDir, "outputs").listFiles()?.forEach { it.delete() }
        }
    }
}
