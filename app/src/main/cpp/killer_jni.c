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

#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <malloc.h>
#include <unistd.h>
#include <dirent.h>
#include <stdbool.h>
#include <string.h>
#include "xhook.h"
#include "xh_log.h"

const char *apkPath__;
const char *repPath__;

int (*old_open)(const char *, int, mode_t);
static int openImpl(const char *pathname, int flags, mode_t mode) {
    //XH_LOG_ERROR("open: %s", pathname);
    if (strcmp(pathname, apkPath__) == 0){
        //XH_LOG_ERROR("replace -> %s", repPath__);
        return old_open(repPath__, flags, mode);
    }
    return old_open(pathname, flags, mode);
}

int (*old_open64)(const char *, int, mode_t);
static int open64Impl(const char *pathname, int flags, mode_t mode) {
    //XH_LOG_ERROR("open64: %s", pathname);
    if (strcmp(pathname, apkPath__) == 0){
        //XH_LOG_ERROR("replace -> %s", repPath__);
        return old_open64(repPath__, flags, mode);
    }
    return old_open64(pathname, flags, mode);
}

int (*old_openat)(int, const char*, int, mode_t);
static int openatImpl(int fd, const char *pathname, int flags, mode_t mode) {
    //XH_LOG_ERROR("openat: %s", pathname);
    if (strcmp(pathname, apkPath__) == 0){
        //XH_LOG_ERROR("replace -> %s", repPath__);
        return old_openat(fd, repPath__, flags, mode);
    }
    return old_openat(fd, pathname, flags, mode);
}

int (*old_openat64)(int, const char*, int, mode_t);
static int openat64Impl(int fd, const char *pathname, int flags, mode_t mode) {
    //XH_LOG_ERROR("openat64: %s", pathname);
    if (strcmp(pathname, apkPath__) == 0){
        //XH_LOG_ERROR("replace -> %s", repPath__);
        return old_openat64(fd, repPath__, flags, mode);
    }
    return old_openat64(fd, pathname, flags, mode);
}

/*
 * 目标应用启动时把「安装包的真实路径」换成「我们释放出来的原包」，
 * 于是任何 open()/openat() 读 base.apk 的校验都会读到原始签名。
 * 符号名和 bai.killer.Native 绑定：打包时按用户配置改包名会跳过那个类。
 */
JNIEXPORT void JNICALL
Java_bai_killer_Native_hookApkPath(JNIEnv *env, __attribute__((unused)) jclass clazz, jstring apkPath, jstring repPath) {
    apkPath__ = (*env)->GetStringUTFChars(env, apkPath, 0);
    repPath__ = (*env)->GetStringUTFChars(env, repPath, 0);

    xhook_register(".*\\.so$", "openat64", openat64Impl, (void **) &old_openat64);
    xhook_register(".*\\.so$", "openat", openatImpl, (void **) &old_openat);
    xhook_register(".*\\.so$", "open64", open64Impl, (void **) &old_open64);
    xhook_register(".*\\.so$", "open", openImpl, (void **) &old_open);

    xhook_refresh(0);
}
