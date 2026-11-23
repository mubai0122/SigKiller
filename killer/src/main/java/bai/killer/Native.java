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

package bai.killer;

/**
 * JNI 桥。
 *
 * 注意：这个类必须留在 bai.killer 包里 —— 导出符号是
 * Java_bai_killer_Native_hookApkPath，名称跟着类名走。
 * 打包工具在按用户配置改注入包名时会跳过它。
 */
final class Native {

    static native void hookApkPath(String apkPath, String replacementPath);

    private Native() {
    }
}
