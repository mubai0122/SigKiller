# SigKiller · 一键去签

用 Jetpack Compose 写的一个「去除签名校验」工具：**选应用 → 配置注入 → 一键出包**。
过签逻辑来自
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

底栏：`Material 3 / 默认 / 悬浮 / 液态玻璃` 四种形态，

界面：**纯白底 + miuix 组件**

## 二、它到底改了什么

在原 APK 上修改，然后重签：

1. **追加 dex**：把 `./killer.dex` 写成 `classesN.dex`，。
2. **改写 AndroidManifest.xml**：
   - 目标**没有**自定义 `Application` → 把 `<application android:name>` 设成 `<注入类名>.KillerApp`，
     hook 在 `attachBaseContext()` 跑；
   - 目标**已有** `Application` → 不动它，改为插入一个 `<provider>`。provider 由系统在     `ActivityThread.installContentProviders()` 里创建，早于 `Application.onCreate()`；
   - 再写入若干 `<meta-data>`：原签名证书 base64、so 名、原包路径/解压路径。
3. **注入 so**：`lib/<abi>/lib<注入so库名>.so`，一律 **STORED**（目标若声明
   `extractNativeLibs=false`，只有不压缩 + 页对齐才能被 `System.loadLibrary` 直接 mmap）。
4. **可选嵌入原包**：`使用原包过签` 打开时，把原始 APK 原样塞进 `assets/origin.apk`。
5. **清掉旧签名 + 重签**：删除 `META-INF/*.SF|.RSA|.DSA|.EC|MANIFEST.MF`，用 apksig 重新做
   v1 + v2 + v3，最后用 `ApkVerifier` 自检。

运行期（注入的那一方）：

- 替换 `PackageInfo.CREATOR`，凡是经过 Parcel 的 `getPackageInfo` 都拿到**原签名**；
  顺带清 `Parcel.mCreators` 与 `PackageManager.sPackageInfoCache`，必要时用 `VMRuntime` 放行 hidden API。
- `使用原包过签` 时，把嵌入的原包释放到 `files/origin.apk`（用 token 文件判断是否需要重写），
  再用 xhook 把 `open / open64 / openat / openat64` 里命中**安装包真实路径**的调用重定向到原包 ——
  这样「直接读 APK 文件算签名」的校验也会读到原始签名。

## 三、产物去哪了

- 私有：`/data/data/bai.sigkiller/files/outputs/<原名>-sigkilled.apk`
- 公共：`Download/BaiSigKiller/`

从文件选择器挑进来的 APK 会先整份复制到 `files/imports/`。

## 四、构建

要求：JDK 17+、Android SDK **Platform 37.2**（`compileSdk = 37` + `compileSdkMinor = 2`）、
Build-Tools 37、NDK 28.2.13676358、CMake 3.22.1。Gradle 用 9.3.1（AGP 9.1.0 的下限，wrapper 已配好）。

```bash
# local.properties 里写 sdk.dir，或设置 ANDROID_HOME
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

`prebuilt/SigKiller-debug.apk` 是已经编好的 debug 包（包名 `bai.sigkiller`），可直接安装。


## 五、改动过 killer/ 之后

```bash
ANDROID_HOME=/path/to/sdk ./scripts/build-killer-dex.sh
```

## 六、引擎自测

`engine` JVM 模块，测试拿真实 APK 跑完整流，用 **aapt2** 和 **apksig** 交叉验证：

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
