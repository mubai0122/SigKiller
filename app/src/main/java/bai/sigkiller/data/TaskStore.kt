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

import androidx.compose.ui.graphics.ImageBitmap
import bai.sigkiller.engine.PatchStage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TaskStatus { RUNNING, DONE, FAILED }

data class PatchTask(
    val id: Long,
    val label: String,
    val packageName: String,
    val status: TaskStatus,
    val stage: PatchStage,
    val progress: Float,
    val message: String,
    val sourcePath: String = "",
    val fromFile: Boolean = false,
    val versionName: String = "",
    /** 目标应用图标，详情页头部展示 */
    val icon: ImageBitmap? = null,
    val outputPath: String? = null,
    val signed: Boolean = false,
    val sizeBytes: Long = 0L,
    /** 打包阶段已写 / 总字节；-1 表示当前不在打包阶段或对字节进度无意义 */
    val packedBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val exportedPath: String? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {

    /** 详情页的那条步骤条：哪些走完了、哪一步正在跑。 */
    fun stageSteps(): List<StageStep> {
        val current = stage
        // DONE 是终态，不占步骤名额：进度条走到最后一步就是结束
        return PatchStage.entries.filter { it != PatchStage.DONE }.map { item ->
            val state = when {
                status == TaskStatus.DONE -> StepState.DONE
                status == TaskStatus.FAILED && item == current -> StepState.FAILED
                item.ordinal < current.ordinal -> StepState.DONE
                item == current && status == TaskStatus.RUNNING -> StepState.ACTIVE
                else -> StepState.PENDING
            }
            StageStep(item, state)
        }
    }

    val elapsedMillis: Long get() = (finishedAt ?: System.currentTimeMillis()) - startedAt
}

enum class StepState { DONE, ACTIVE, PENDING, FAILED }

data class StageStep(val stage: PatchStage, val state: StepState)

/** 任务队列只活在内存里：进程被杀就重来，比落库再对状态一致性问题省事得多。 */
@Singleton
class TaskStore @Inject constructor() {

    private val _tasks = MutableStateFlow<List<PatchTask>>(emptyList())
    val tasks: StateFlow<List<PatchTask>> = _tasks.asStateFlow()

    private var nextId = 1L

    fun start(source: PatchSource): Long {
        val id = nextId++
        _tasks.update { current ->
            listOf(
                PatchTask(
                    id = id,
                    label = source.label,
                    packageName = source.packageName,
                    status = TaskStatus.RUNNING,
                    stage = PatchStage.PREPARE,
                    progress = 0f,
                    message = "排队中",
                    sourcePath = source.apkPath,
                    fromFile = source.fromFile,
                    versionName = source.versionName,
                    icon = source.icon,
                ),
            ) + current
        }
        return id
    }

    fun progress(
        id: Long,
        stage: PatchStage,
        progress: Float,
        message: String,
        log: List<String>,
        packedBytes: Long = -1L,
        totalBytes: Long = -1L,
    ) {
        _tasks.update { current ->
            current.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        stage = stage,
                        progress = progress.coerceIn(0f, 1f),
                        message = message,
                        log = log,
                        packedBytes = packedBytes,
                        totalBytes = totalBytes,
                    )
                }
            }
        }
    }

    fun finish(
        id: Long,
        outputPath: String,
        signed: Boolean,
        sizeBytes: Long,
        exportedPath: String?,
        log: List<String>,
    ) {
        _tasks.update { current ->
            current.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        status = TaskStatus.DONE,
                        stage = PatchStage.DONE,
                        progress = 1f,
                        message = "完成",
                        outputPath = outputPath,
                        signed = signed,
                        sizeBytes = sizeBytes,
                        exportedPath = exportedPath,
                        log = log,
                        finishedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun fail(id: Long, message: String, log: List<String>) {
        _tasks.update { current ->
            current.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        status = TaskStatus.FAILED,
                        message = "失败",
                        error = message,
                        log = log,
                        finishedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun find(id: Long): PatchTask? = _tasks.value.firstOrNull { it.id == id }

    fun remove(id: Long) {
        _tasks.update { current -> current.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { current -> current.filter { it.status == TaskStatus.RUNNING } }
    }
}
