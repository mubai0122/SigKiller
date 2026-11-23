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

package bai.sigkiller.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 一个版本条目，update.php 的 latest 与 history.php 的 history 共用同一套字段。 */
@Serializable
data class Release(
    val versionCode: Int = 0,
    val versionName: String = "",
    val publishedAt: String = "",
    val changelog: String = "",
    @SerialName("downloadUrl") val downloadUrl: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val force: Boolean = false,
    val minVersionCode: Int = 0,
    val isCurrent: Boolean = false,
    val isNewer: Boolean = false,
) {
    val displayVersion: String get() = if (versionName.isNotBlank()) "v$versionName" else "v$versionCode"
}

/** GET /api/update.php 的响应。 */
@Serializable
data class UpdateResponse(
    val ok: Boolean = false,
    val hasUpdate: Boolean = false,
    val force: Boolean = false,
    val clientVersionCode: Int = 0,
    val latest: Release = Release(),
    val serverTime: String = "",
    val error: String = "",
)

/** GET /api/history.php 的响应。 */
@Serializable
data class HistoryResponse(
    val ok: Boolean = false,
    @SerialName("package") val packageName: String = "",
    val clientVersionCode: Int = 0,
    val count: Int = 0,
    val history: List<Release> = emptyList(),
    val serverTime: String = "",
    val error: String = "",
)
