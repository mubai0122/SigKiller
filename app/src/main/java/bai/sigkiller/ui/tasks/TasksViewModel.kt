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

package bai.sigkiller.ui.tasks

import androidx.lifecycle.ViewModel
import bai.sigkiller.data.TaskStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val store: TaskStore,
) : ViewModel() {

    val tasks: StateFlow<List<bai.sigkiller.data.PatchTask>> = store.tasks

    fun remove(id: Long) = store.remove(id)

    fun clearFinished() = store.clearFinished()
}
