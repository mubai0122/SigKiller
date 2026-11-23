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

# 过签用的 native 层：xhook 把 open/openat 系列换成「命中安装包路径就重定向到原包」
#
# so 名固定为 SignatureKiller（可执行文件名叫什么不影响 JNI 符号），
# 注入到目标 APK 时再按用户配置改成 lib<注入so库名>.so。
cmake_minimum_required(VERSION 3.22.1)

project(sigkiller C)

set(CMAKE_C_STANDARD 11)

add_library(
        SignatureKiller
        SHARED
        killer_jni.c
        xhook.c
        xh_core.c
        xh_elf.c
        xh_log.c
        xh_util.c
        xh_version.c)

# xhook 是 2018 年的代码，新 NDK 的 clang 会报一堆 -Wwarn，但都不影响功能，
# 这里只保留真正致命的诊断
target_compile_options(
        SignatureKiller
        PRIVATE
        -Wall
        -Wno-unused-parameter
        -Wno-unused-variable
        -Wno-incompatible-pointer-types
        -Wno-deprecated-non-prototype
        -Wno-int-conversion
        -fvisibility=hidden)

find_library(log-lib log)

target_link_libraries(SignatureKiller ${log-lib} dl)
