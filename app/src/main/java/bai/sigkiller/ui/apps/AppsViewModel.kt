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

package bai.sigkiller.ui.apps

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bai.sigkiller.data.InstalledApp
import bai.sigkiller.data.InstalledAppRepository
import bai.sigkiller.data.InjectSettings
import bai.sigkiller.data.PatchSource
import bai.sigkiller.data.SettingsStore
import bai.sigkiller.patch.FileImporter
import bai.sigkiller.patch.PatchRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repository: InstalledAppRepository,
    private val settingsStore: SettingsStore,
    private val runner: PatchRunner,
    private val fileImporter: FileImporter,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _includeSystem = MutableStateFlow(false)
    private val _loading = MutableStateFlow(true)
    private val _selected = MutableStateFlow<PatchSource?>(null)
    private val _dialogVisible = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _importing = MutableStateFlow(false)

    val query: StateFlow<String> = _query.asStateFlow()
    val includeSystem: StateFlow<Boolean> = _includeSystem.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    val selected: StateFlow<PatchSource?> = _selected.asStateFlow()
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()
    val importing: StateFlow<Boolean> = _importing.asStateFlow()
    val inject: StateFlow<InjectSettings> = settingsStore.inject

    val apps: StateFlow<List<InstalledApp>> = combine(_apps, _query) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            val keyword = query.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(keyword) || it.packageName.lowercase().contains(keyword)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = repository.load(_includeSystem.value)
            // 列表刷新后把选中的那条换成新对象，图标/签名信息才不会过期
            val current = _selected.value
            if (current != null && !current.fromFile) {
                _selected.value = _apps.value
                    .firstOrNull { it.packageName == current.packageName }
                    ?.let(PatchSource::fromApp)
            }
            _loading.value = false
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setIncludeSystem(value: Boolean) {
        if (_includeSystem.value == value) return
        _includeSystem.value = value
        refresh()
    }

    fun select(app: InstalledApp) {
        val source = PatchSource.fromApp(app)
        _selected.value = if (_selected.value?.apkPath == source.apkPath) null else source
    }

    /** 列表右侧「去签」：直接选中并弹配置，不再走一次 toggle。 */
    fun startWith(app: InstalledApp) {
        _selected.value = PatchSource.fromApp(app)
        _dialogVisible.value = true
    }

    fun clearSelection() {
        _selected.value = null
    }

    /** 从文件选择器挑一个 APK：拷进私有目录 → 读出包名 → 直接弹配置窗。 */
    fun importFile(uri: Uri) {
        if (!fileImporter.looksLikeApk(uri)) {
            _message.value = "这不是 APK 文件，换一个试试"
            return
        }
        viewModelScope.launch {
            _importing.value = true
            val result = fileImporter.import(uri)
            _importing.value = false
            result
                .onSuccess { source ->
                    _selected.value = source
                    if (source.packageName.isBlank()) {
                        _message.value = "读不出这个 APK 的包名，仍可尝试处理"
                    }
                    _dialogVisible.value = true
                }
                .onFailure { error ->
                    _message.value = error.message ?: "文件读取失败"
                }
        }
    }

    fun openDialog() {
        val target = _selected.value
        if (target == null) {
            _message.value = "先选择一个应用，或者从文件挑一个 APK"
            return
        }
        _dialogVisible.value = true
    }

    fun dismissDialog() {
        _dialogVisible.value = false
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun confirm(config: InjectSettings) {
        val target = _selected.value ?: return
        _dialogVisible.value = false
        settingsStore.saveInject(config)
        runner.enqueue(target, config)
        _message.value = "已开始处理「${target.label}」，进度见任务页"
    }
}
