# BaiSigKiller 远程更新服务

三个文件，无数据库依赖，上传到域名 `sk.baiccb.cn` 的 `api/` 目录即可：

```
sk.baiccb.cn/api/
├── update.php      检查更新（GET）
├── history.php     更新历史（GET）
└── releases.json   版本数据源（改版本只动这个）
```

## 接口

### `GET /api/update.php?versionCode=<当前版本码>`

用 **versionCode**（整数）对比，不看 versionName。省略 `versionCode` 时按 0 处理。

```json
{
  "ok": true,
  "hasUpdate": true,
  "force": false,
  "clientVersionCode": 1,
  "latest": {
    "versionCode": 2,
    "versionName": "1.1.0",
    "publishedAt": "2026-11-23 18:00:00+08:00",
    "changelog": "• 新增远程更新检查…",
    "downloadUrl": "https://…/SigKiller-v1.1.0.apk",
    "sizeBytes": 2600000,
    "sha256": "",
    "force": false,
    "minVersionCode": 1
  },
  "serverTime": "2026-11-23T18:00:00+08:00"
}
```

- `hasUpdate`：`latest.versionCode > 客户端 versionCode`。
- `force`：客户端低于 `minVersionCode`，或该版本 `force=true`，App 不允许跳过更新。

### `GET /api/history.php?versionCode=<当前版本码>&limit=50`

返回按 versionCode 降序的版本列表，每条带 `isCurrent` / `isNewer` 标记，供 App 高亮当前版本。`limit` 范围 1–100，默认 100。

```json
{
  "ok": true,
  "package": "bai.sigkiller",
  "clientVersionCode": 1,
  "count": 2,
  "history": [ { "versionCode": 2, "versionName": "1.1.0", "isCurrent": false, "isNewer": true, "...": "..." } ],
  "serverTime": "..."
}
```

两个接口都返回 `Content-Type: application/json; charset=utf-8`，带 CORS `*` 与 no-cache 头，只接受 GET（其他方法返回 405）。非法 `versionCode` 当 0，无注入风险。

## 发布新版本

编辑 `releases.json`，在 `releases` 数组**最前面**加一条：

- `versionCode`：必须比历史所有版本都大（App 只认这个数字）。
- `versionName`：展示用，如 `1.2.0`。
- `changelog`：更新日志，`\n` 换行，App 会按行拆成圆点列表。
- `downloadUrl`：APK 直链。
- `sizeBytes` / `sha256`：可留 0 / 空字符串（App 目前只用 versionCode 判断，下载交给系统浏览器）。
- `force` / `minVersionCode`：控制强制更新门槛，普通发布都填 `false` / `1` 即可。

> 注意：`releases.json` 必须是**纯 JSON**，不能加 `//` 或 `/* */` 注释，否则 `json_decode` 会失败。

## 上传方式

把本目录三个文件原样上传到站点 `api/` 目录（PHP 7.4+ 即可，无需数据库、无需 Composer）。App 端接口地址写死在 `app/src/main/java/bai/sigkiller/data/update/UpdateRepository.kt` 的 `BASE_URL`。

## App 端行为

- 启动后静默检查一次，只有确实有新版才弹窗，网络不通不打扰。
- 设置页「检查更新」手动触发，无论有无更新都给一次提示；「更新历史」查看全部版本。
- 「去下载」用系统浏览器打开 `downloadUrl`（走浏览器下载，不占用应用自身下载权限）。
