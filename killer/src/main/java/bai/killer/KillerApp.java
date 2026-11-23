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

import android.app.Application;
import android.content.Context;

/**
 * 目标应用自己没有 Application 时的注入点。
 *
 * attachBaseContext() 比 ContentProvider 和 Application.onCreate() 都早，
 * 目标应用在那之后才可能去查签名，所以这是能拿到的最早时机。
 */
public final class KillerApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Killer.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Killer.install(this);
    }
}
