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

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 更新服务地址。Upload 时把 api/ 目录放到 sk.baiccb.cn 对应路径即可。 */
private const val BASE_URL = "https://sk.baiccb.cn/api/"

private interface UpdateService {
    @GET("update.php")
    suspend fun check(@Query("versionCode") versionCode: Int): UpdateResponse

    @GET("history.php")
    suspend fun history(
        @Query("versionCode") versionCode: Int,
        @Query("limit") limit: Int = 50,
    ): HistoryResponse
}

/**
 * 远程更新仓库。
 *
 * 用 Retrofit + kotlinx-serialization，OkHttp 设了短超时——更新检查是启动时的
 * 旁路操作，网络不通也不能拖慢主流程。所有异常在 ViewModel 层兜底成「已是最新」。
 */
@Singleton
class UpdateRepository @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val service: UpdateService by lazy {
        val okhttp = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okhttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UpdateService::class.java)
    }

    suspend fun check(currentVersionCode: Int): UpdateResponse = service.check(currentVersionCode)

    suspend fun history(currentVersionCode: Int): HistoryResponse = service.history(currentVersionCode)
}
