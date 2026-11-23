# ---------------------------------------------------------------------------
# Release R8 混淆
# 目标：类名混淆成 a1 / a2 这种短名字，但保留原包名目录结构（不扁平化到根包）。
# ---------------------------------------------------------------------------

# 类名混淆字典：按顺序取 a1、a2……；只在包内唯一即可，跨包可复用
-classobfuscationdictionary obfuscation-dictionary.txt

# 保留包名目录：混淆后的类仍落在原来的 package 路径下
-keeppackagenames **

# ---------------------------------------------------------------------------
# 功能必需的保留项（混淆这些会让去签引擎或框架崩掉）
# ---------------------------------------------------------------------------

# 引擎依赖 apksig / dexlib2，它们大量使用反射与 Java 8 特性
-keep class com.android.apksig.** { *; }
-keep class org.jf.dexlib2.** { *; }
-dontwarn com.android.apksig.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**

# Manifest 里声明的组件（Hilt 的 Application、Activity、FileProvider）
# 由 AGP/Hilt 的 consumer rules 自动保留，这里不重复。

# JNI 载荷在 killer.dex 里独立存在，不随本应用混淆，无需 special keep。

# ---------------------------------------------------------------------------
# 远程更新：Retrofit 接口靠注解反射调用，kotlinx-serialization 靠生成的 serializer
# ---------------------------------------------------------------------------
-keep interface bai.sigkiller.data.update.** { *; }
-keep class bai.sigkiller.data.update.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class bai.sigkiller.data.update.**$$serializer { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn kotlinx.serialization.**
