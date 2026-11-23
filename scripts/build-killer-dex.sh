#!/usr/bin/env bash
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

# 重新生成 app/src/main/assets/killer.dex
#
# killer.dex 是注入到目标应用里的那份字节码，源在 killer/src/main/java。
# 只有改动过 killer/ 下的 Java 代码时才需要跑这个脚本，产物已经随仓库提交。
#
# 依赖：JDK 17+、Android SDK（用其中的 android.jar 做编译目标）、d8（build-tools）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
PLATFORM="${PLATFORM:-android-37.2}"
BUILD_TOOLS="${BUILD_TOOLS:-37.0.0}"

if [[ -z "$SDK" ]]; then
  echo "请先设置 ANDROID_HOME 指向 Android SDK" >&2
  exit 1
fi

ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"
D8="$SDK/build-tools/$BUILD_TOOLS/d8"
[[ -f "$ANDROID_JAR" ]] || { echo "找不到 $ANDROID_JAR" >&2; exit 1; }
[[ -x "$D8" ]] || { echo "找不到 $D8" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> 编译 payload"
find "$ROOT/killer/src/main/java" -name '*.java' > "$WORK/sources.txt"
javac -source 17 -target 17 -nowarn -cp "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.txt"

echo "==> d8 转 dex"
"$D8" --min-api 21 --lib "$ANDROID_JAR" --output "$WORK" $(find "$WORK/classes" -name '*.class')

cp "$WORK/classes.dex" "$ROOT/app/src/main/assets/killer.dex"
echo "==> 已写入 app/src/main/assets/killer.dex ($(stat -c%s "$ROOT/app/src/main/assets/killer.dex") 字节)"
