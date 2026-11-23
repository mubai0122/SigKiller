# Project:    BaiSigKiller
# Author:     by 沐白/Baix
# License:    AGPL-3.0 (GNU Affero General Public License v3.0)
# Date:       2026-11-22 01:43:09
# Repository: https://github.com/mubai0122/SigKiller
# Website:    https://sigkiller.baiccb.cn/
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.

# SigKiller · 一键去签

用 Jetpack Compose 写的一个「去除签名校验」工具：**选应用 → 配置注入 → 一键出包**。
底栏与主题层与参考工程（`TextureBackend`）**同一套实现、同一组依赖**；过签逻辑来自
`ApkSignatureKillerEx` 的原理，重写成了可注入的载荷 + 一个纯 JVM 的 APK 补丁引擎。

```
SigKiller/
├── app/        Compose 界面：应用列表、任务队列、设置、注入配置弹窗
│   ├── assets/killer.dex        注入到目标应用的字节码（源码在 killer/）
│   ├── assets/sigkiller.p12     内置签名密钥（口令 123456）
│   └── src/main/cpp/            xhook + JNI：运行期接管 open()/openat()
├── core/       共享 UI 层：TxtTheme + TxtNavShell 底栏 + 液态玻璃 / Miuix 组件
├── engine/     纯 JVM 的补丁引擎：AXML、resources.arsc、dex、zip、apksig 签名
├── killer/     注入载荷的 Java 源码（编译产物落在 app/src/main/assets/killer.dex）
├── scripts/    重新生成 killer.dex 的脚本
└── prebuilt/   已构建好的 debug APK，可直接装机试用
```

## 一、界面

三个顶层页，底栏就是参考工程那套 `TxtNavShell`：`Material 3 / 默认 / 悬浮 / 液态玻璃` 四种形态，
设置里可切，液态玻璃带按下放大、拖拽橡皮筋、光斑跟随重力感应那一整套效果。

界面是**纯白底 + miuix 组件**（`Card` / `Switch` / `Button` / `TextField` / `LinearProgressIndicator` / `Icon`），
卡片用 1px 描边而不是阴影分层，强调色沿用参考弹窗里那支青 `#00B4A4`；标题统一做了状态栏留白并向下压了一段距离。

| 页面 | 内容 |
| --- | --- |
| 应用 | 标题 + 搜索 + 「已安装 / 含系统应用」分段控件；**顶部第一条是「从文件选择 APK」**（SAF，不限于已安装应用）；列表显示图标 / 名称 / 包名 / 版本 / 体积 / 签名 MD5 前 6 位；点一行选中，底部浮出操作条，可「一键去签」 |
| 任务 | 每个任务一张卡：进度条 + 当前阶段 + 百分比；**点开是任务详情**——头部展示目标应用图标；每阶段（解析 / 注入 dex / 改写清单 / 嵌入原包 / 注入 so / 打包 / 签名 / 校验）都有 完成·进行中·等待·中断 四态；**「重新打包」阶段额外有一条实时压缩进度条**（已写 / 总字节，按 ZIP 写入的未压缩字节逐段填充）；另附实时日志、耗时、产物路径，可直接安装 / 分享 / 删除 |
| 设置 | 默认注入配置、签名密钥（内置 / 自定义 keystore + 口令）、是否导出到 `Download/BaiSigKiller`、主题、底栏样式、界面样式、关于 |

**注入配置弹窗**按参考图 1:1 复刻：标题 + 右上 GitHub 入口，`注入类名`（青色下划线输入框），
`使用原包过签` 开关（打开后展开 `注入so库名` / `注入原包路径` / `原包解压路径`），免责声明段落，
底部 `自动签名` 复选框 + `取消` / `确定`。另外加了一个默认关闭的「修改包名」高级项（见限制）。

## 二、它到底改了什么

一次「一键去签」= 在原 APK 上做四件事，然后重签：

1. **追加 dex**：把 `assets/killer.dex` 写成 `classesN.dex`，不动原包任何一个 dex。
2. **改写 AndroidManifest.xml**：
   - 目标**没有**自定义 `Application` → 把 `<application android:name>` 设成 `<注入类名>.KillerApp`，
     hook 在 `attachBaseContext()` 就跑起来（最早时机）；
   - 目标**已有** `Application` → 不动它，改为插入一个 `<provider>`。provider 由系统在
     `ActivityThread.installContentProviders()` 里创建，早于 `Application.onCreate()`；
   - 再写入若干 `<meta-data>`：原签名证书 base64、so 名、原包路径/解压路径。
3. **注入 so**：`lib/<abi>/lib<注入so库名>.so`，一律 **STORED**（目标若声明
   `extractNativeLibs=false`，只有不压缩 + 页对齐才能被 `System.loadLibrary` 直接 mmap）。
4. **可选嵌入原包**：`使用原包过签` 打开时，把原始 APK 原样塞进 `assets/origin.apk`。
5. **清掉旧签名 + 重签**：删除 `META-INF/*.SF|.RSA|.DSA|.EC|MANIFEST.MF`，用 apksig 重新做
   v1 + v2 + v3，最后用 `ApkVerifier` 自检，不通过就报错。

运行期（注入的那一方）：

- 替换 `PackageInfo.CREATOR`，凡是经过 Parcel 的 `getPackageInfo` 都拿到**原签名**；
  顺带清 `Parcel.mCreators` 与 `PackageManager.sPackageInfoCache`，必要时用 `VMRuntime` 放行 hidden API。
- `使用原包过签` 时，把嵌入的原包释放到 `files/origin.apk`（用 token 文件判断是否需要重写），
  再用 xhook 把 `open / open64 / openat / openat64` 里命中**安装包真实路径**的调用重定向到原包 ——
  这样「直接读 APK 文件算签名」的校验也会读到原始签名。

## 三、产物去哪了

- 私有：`/data/data/bai.sigkiller/files/outputs/<原名>-sigkilled.apk`（任务详情里能看到完整路径）
- 公共：`Download/BaiSigKiller/`（Android 10+ 走 MediaStore，不需要任何权限；Android 9 及以下会在打开开关时申请存储权限）

从文件选择器挑进来的 APK 会先整份复制到 `files/imports/`，因为引擎只认真实文件路径，`content://` 它读不了。

## 四、构建

要求：JDK 17+、Android SDK **Platform 37.2**（`compileSdk = 37` + `compileSdkMinor = 2`）、
Build-Tools 37、NDK 28.2.13676358、CMake 3.22.1。Gradle 用 9.3.1（AGP 9.1.0 的下限，wrapper 已配好）。

```bash
# local.properties 里写 sdk.dir，或设置 ANDROID_HOME
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`prebuilt/SigKiller-debug.apk` 是已经编好的 debug 包（包名 `bai.sigkiller`），可直接安装。

依赖版本与参考工程严格对齐（Compose BOM 2026.09.00、miuix 0.9.4、Kotlin 2.4.21、AGP 9.1.0、
Hilt 2.60.1、KSP 2.3.12、Navigation 2.10.2 …），另加两个引擎依赖：`com.android.tools.build:apksig`
（签名 / 校验）和 `org.smali:dexlib2`（按用户配置重命名注入包名）。两者都只用 API 24 起可用的
`java.util.function/stream`，不碰 `java.nio.file`。

## 五、改动过 killer/ 之后

```bash
ANDROID_HOME=/path/to/sdk ./scripts/build-killer-dex.sh
```

## 六、引擎自测

`engine` 是纯 JVM 模块，测试会拿真实 APK 跑完整流程，并用 **aapt2** 和 **apksig** 交叉验证：

```bash
./gradlew :engine:test \
  -Psigkiller.sampleApk=/path/to/app.apk \
  -Psigkiller.sampleNoAppApk=/path/to/app-without-application.apk \
  -Psigkiller.payloadDex=app/src/main/assets/killer.dex \
  -Psigkiller.keystore=app/src/main/assets/sigkiller.p12 \
  -Psigkiller.aapt2=$ANDROID_HOME/build-tools/37.0.0/aapt2
```

覆盖：完整打补丁 + 签名校验、注入 provider、注入 Application、改包名（manifest + arsc）、
自定义注入类名时 dex 重写（`bai.killer.Native` 因 JNI 符号绑定保持原包名）。

## 七、限制（都写在明处）

- **Application 构造函数 / 静态块里的校验**跑在 provider 之前，且目标已有自定义 `Application` 时我们
  无法接管（那需要运行时生成「继承原类」的桩字节码）。这类加固应用需要另外的手段。
- 只处理**单 base.apk**，split APK / App Bundle 拆分包不支持。
- **修改包名**是「尽力而为」：会同步改 `AndroidManifest.xml` 的 `package`、`resources.arsc` 的包名字段
  与字符串池里的 `旧包名.` 前缀，但第三方 SDK 的硬编码、推送、账号体系仍可能失效，默认关闭。
- 原签名证书从 v1 的 `META-INF/*.RSA` 或 v2/v3 签名块读取；**未签名**的 APK 无法处理。
- 注入的 so 目前只编了 `arm64-v8a` 与 `armeabi-v7a`，`x86` 设备上的目标应用不会被注入 so
  （此时「仅替换签名」模式仍然可用）。
- 清单解析走的是「只加不改」的外科手术式编辑：字符串池尾部追加、属性原位改值。若遇到
  字符串池带 style 的畸形清单会直接报错，不会生成半成品。

## 八、声明

仅供应用安全性自测与学习。请勿用于破解他人应用、绕过付费或传播修改版。
注入实现参考 `L-JINBIN/ApkSignatureKillerEx`（Apache-2.0）与 MT 管理器的「去除签名校验」；
UI 底栏与液态玻璃效果来自 `yukonga/miuix` 与 `Kyant0/AndroidLiquidGlass`（Apache-2.0）。
