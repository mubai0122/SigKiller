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

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SigKiller"

// :core  共享 UI 层（主题 + 底栏 + 液态玻璃），与参考工程保持同一套依赖与实现
// :engine 纯 JVM 的 APK 补丁引擎（AXML / resources.arsc / dex / 签名），可在 JVM 上单测
// :app    Compose 界面层：选应用、一键去签、注入配置
include(":core")
include(":engine")
include(":app")
