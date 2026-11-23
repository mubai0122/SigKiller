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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 注入到目标应用里的过签逻辑。
 *
 * 两件事：
 *   1) 把 PackageInfo.CREATOR 换掉，让所有走 Parcel 的 getPackageInfo 都拿到原签名；
 *   2) 可选地把原始 APK 解出来并 hook open()，让「直接读 APK 文件算签名」的检查
 *      读到的也是原包（那就是「使用原包过签」的意义）。
 *
 * 因为它挂在 ContentProvider 上而不是 Application 上，所以不需要覆盖目标应用
 * 自己的 Application 类，注入面小得多。
 */
public final class Killer {

    private static final String TAG = "SigKiller";
    private static final String META_SIGNATURE = "killer.signature";
    private static final String META_SO = "killer.so";
    private static final String META_ORIGIN_ASSET = "killer.origin.asset";
    private static final String META_ORIGIN_FILE = "killer.origin.file";
    private static final String META_TOKEN = "killer.token";

    private static boolean installed;

    private Killer() {
    }

    public static synchronized void install(Context context) {
        if (installed || context == null) return;
        installed = true;
        try {
            String packageName = context.getPackageName();
            Bundle meta = metaData(context);
            if (meta == null) {
                Log.w(TAG, "no meta-data, nothing to do");
                return;
            }

            String signature = meta.getString(META_SIGNATURE);
            if (signature != null && signature.length() > 0) {
                installSignatureHook(packageName, new Signature(Base64.decode(signature, Base64.DEFAULT)));
            }

            String asset = meta.getString(META_ORIGIN_ASSET);
            String file = meta.getString(META_ORIGIN_FILE);
            String so = meta.getString(META_SO);
            if (asset != null && file != null && so != null) {
                File extracted = extract(context, asset, file, meta.getString(META_TOKEN));
                System.loadLibrary(so);
                Native.hookApkPath(context.getApplicationInfo().sourceDir, extracted.getAbsolutePath());
                Log.i(TAG, "origin apk hooked: " + extracted.getAbsolutePath());
            }
        } catch (Throwable error) {
            Log.e(TAG, "install failed", error);
        }
    }

    // ------------------------------------------------------------------ 签名 hook

    private static void installSignatureHook(final String packageName, final Signature fake) throws Exception {
        final Parcelable.Creator<PackageInfo> original = PackageInfo.CREATOR;
        Parcelable.Creator<PackageInfo> creator = new Parcelable.Creator<PackageInfo>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo info = original.createFromParcel(source);
                if (info != null && packageName.equals(info.packageName)) {
                    if (info.signatures != null && info.signatures.length > 0) {
                        info.signatures[0] = fake;
                    }
                    if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
                        Signature[] signers = info.signingInfo.getApkContentsSigners();
                        if (signers != null && signers.length > 0) {
                            signers[0] = fake;
                        }
                    }
                }
                return info;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return original.newArray(size);
            }
        };

        Field field = findField(PackageInfo.class, "CREATOR");
        field.setAccessible(true);
        field.set(null, creator);

        // 已经解析过的 PackageInfo 会被 PackageManager 缓存，Parcel 也会缓存 CREATOR，
        // 不清理的话新的 CREATOR 可能根本不会被用上
        unlockHiddenApi();
        clearParcelCreators();
        clearPackageInfoCache();
    }

    private static void clearParcelCreators() {
        clearMap(Parcel.class, "mCreators");
        clearMap(Parcel.class, "sPairedCreators");
    }

    private static void clearMap(Class<?> type, String field) {
        try {
            Field target = findField(type, field);
            target.setAccessible(true);
            Object value = target.get(null);
            if (value instanceof java.util.Map) {
                ((java.util.Map<?, ?>) value).clear();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clearPackageInfoCache() {
        try {
            Field field = findField(PackageManager.class, "sPackageInfoCache");
            field.setAccessible(true);
            Object cache = field.get(null);
            if (cache != null) {
                Method clear = cache.getClass().getMethod("clear");
                clear.setAccessible(true);
                clear.invoke(cache);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 允许反射访问 Parcel / PackageManager 里的 hidden 字段，失败就算了。 */
    private static void unlockHiddenApi() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object runtime = getRuntime.invoke(null);
            Method setExemptions = vmRuntime.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setExemptions.setAccessible(true);
            setExemptions.invoke(runtime, (Object) new String[]{
                "Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app"
            });
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ------------------------------------------------------------------ meta-data

    private static Bundle metaData(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData;
        } catch (Throwable error) {
            Log.e(TAG, "read meta-data failed", error);
            return null;
        }
    }

    // ------------------------------------------------------------------ 原包释放

    private static File extract(Context context, String assetPath, String targetPath, String token) throws IOException {
        File base;
        if (Build.VERSION.SDK_INT >= 24) {
            base = context.getDataDir();
        } else {
            base = new File("/data/data/" + context.getPackageName());
        }
        File target = new File(base, targetPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        File stamp = new File(target.getAbsolutePath() + ".sigkiller");
        boolean fresh = target.exists() && target.length() > 0 && stamp.exists()
            && token != null && token.equals(readText(stamp));
        if (fresh) return target;

        String name = assetPath.startsWith("assets/") ? assetPath.substring("assets/".length()) : assetPath;
        File temp = new File(target.getAbsolutePath() + ".tmp");
        AssetManager assets = context.getAssets();
        InputStream input = assets.open(name, AssetManager.ACCESS_STREAMING);
        try {
            OutputStream output = new FileOutputStream(temp);
            try {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "cannot replace " + target);
        }
        if (!temp.renameTo(target)) {
            Log.w(TAG, "cannot rename " + temp + " -> " + target);
            return temp;
        }
        if (token != null) {
            FileOutputStream stampOut = new FileOutputStream(stamp);
            try {
                stampOut.write(token.getBytes("UTF-8"));
            } finally {
                stampOut.close();
            }
        }
        return target;
    }

    private static String readText(File file) {
        try {
            InputStream input = new java.io.FileInputStream(file);
            try {
                byte[] buffer = new byte[(int) Math.min(file.length(), 256)];
                int read = input.read(buffer);
                return read <= 0 ? "" : new String(buffer, 0, read, "UTF-8");
            } finally {
                input.close();
            }
        } catch (IOException error) {
            return "";
        }
    }

    public static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object value = method.invoke(null);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
