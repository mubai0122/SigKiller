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

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

android {
    namespace = "bai.sigkiller"
    compileSdk = 37
    // 平台包 android-37.2（SDK 37 的第 2 个 minor 版本）
    compileSdkMinor = 2
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "bai.sigkiller"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cFlags += "-O2"
            }
        }
        ndk {
            // 过签 so 只会注入到目标 APK 里存在的这些 ABI 上
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
    }

    // release 的 lint  vital 分析需要额外的 lint-check 依赖，离线环境下拿不到；
    // 它与 APK 产物无关，直接关掉，保证 assembleRelease 可复现
    lint {
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            val secret = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            storeFile = file(secret.getProperty("release.keystore.file", "mbdev-sk.jks"))
            storePassword = secret.getProperty("release.keystore.password", "")
            keyAlias = secret.getProperty("release.key.alias", "mbdev-sk")
            keyPassword = secret.getProperty("release.key.password", "")
        }
    }

    buildTypes {
        release {
            // 开启 R8：类名按字典混淆成 a1/a2 短名，保留原包名目录
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // release 用本地 release keystore 签名，口令放在 local.properties（不进 git）
            signingConfig = signingConfigs.getByName("release")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        // 引擎自己带 apksig/dexlib2，不需要它们自带的 META-INF 元数据
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
}
