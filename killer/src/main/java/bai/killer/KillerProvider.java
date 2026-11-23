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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * 注入点。
 *
 * 选 ContentProvider 是因为系统在 ActivityThread.installContentProviders() 里创建它，
 * 这一步发生在 Application.onCreate() 之前，所以目标应用在 onCreate 里做的签名校验
 * 已经能看到被替换过的 PackageInfo。同时它不需要覆盖目标应用原有的 Application 类。
 */
public final class KillerProvider extends ContentProvider {

    static {
        // 类加载就尽早装上，别等 onCreate
        try {
            Context context = Killer.currentApplication();
            if (context != null) Killer.install(context);
        } catch (Throwable error) {
            Log.e("SigKiller", "early install failed", error);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) Killer.install(context);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
