/*
 * Project:    BaiSigKiller
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/SigKiller
 * Website:    https://sigkiller.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package bai.sigkiller.ui.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bai.sigkiller.data.update.Release
import bai.sigkiller.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val checking: Boolean = false,
    val update: Release? = null,
    val force: Boolean = false,
    /** 无需更新 / 网络异常时的一次性提示，展示后由界面清掉 */
    val message: String? = null,
    val history: List<Release> = emptyList(),
    val historyOpen: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val currentVersionCode: Int by lazy {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(1)
    }

    val currentVersionName: String by lazy {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    /** 手动点「检查更新」。无论有无更新都给一次反馈。 */
    fun checkManually() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true, message = null)
        viewModelScope.launch {
            runCatching { repo.check(currentVersionCode) }
                .onSuccess { res ->
                    if (res.ok && res.hasUpdate && res.latest.versionCode > currentVersionCode) {
                        _state.value = _state.value.copy(
                            checking = false,
                            update = res.latest.copy(force = res.force),
                            force = res.force,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            checking = false,
                            message = "已是最新版本（v$currentVersionName）",
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        checking = false,
                        message = "检查失败，请稍后重试",
                    )
                }
        }
    }

    /** 启动时静默检查：只在确实有更新时弹窗，网络异常一律不打扰。 */
    fun checkSilently() {
        if (_state.value.checking) return
        viewModelScope.launch {
            runCatching { repo.check(currentVersionCode) }
                .onSuccess { res ->
                    if (res.ok && res.hasUpdate && res.latest.versionCode > currentVersionCode) {
                        _state.value = _state.value.copy(
                            update = res.latest.copy(force = res.force),
                            force = res.force,
                        )
                    }
                }
                .onFailure { /* 静默检查失败不打扰用户 */ }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { repo.history(currentVersionCode) }
                .onSuccess { res ->
                    if (res.ok) {
                        _state.value = _state.value.copy(
                            history = res.history,
                            historyOpen = true,
                        )
                    } else {
                        _state.value = _state.value.copy(message = "获取历史失败")
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(message = "获取历史失败，请检查网络")
                }
        }
    }

    fun dismissUpdate() {
        if (_state.value.force) return // 强制更新不允许关闭
        _state.value = _state.value.copy(update = null, force = false)
    }

    fun closeHistory() {
        _state.value = _state.value.copy(historyOpen = false)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
