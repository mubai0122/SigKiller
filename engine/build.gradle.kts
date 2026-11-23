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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// 纯 JVM 模块：不依赖 Android 框架，便于在电脑上直接单测 AXML / arsc / dex / 签名逻辑
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("17"))
    }
}

dependencies {
    api(libs.apksig)
    api(libs.dexlib2)

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // 测试用的真实 APK / 资产路径通过系统属性传入，缺省时不跑集成用例
    systemProperty("sigkiller.sampleApk", providers.gradleProperty("sigkiller.sampleApk").getOrElse(""))
    systemProperty("sigkiller.payloadDex", providers.gradleProperty("sigkiller.payloadDex").getOrElse(""))
    systemProperty("sigkiller.keystore", providers.gradleProperty("sigkiller.keystore").getOrElse(""))
    systemProperty("sigkiller.aapt2", providers.gradleProperty("sigkiller.aapt2").getOrElse(""))
    systemProperty(
        "sigkiller.sampleNoAppApk",
        providers.gradleProperty("sigkiller.sampleNoAppApk").getOrElse(""),
    )
}
