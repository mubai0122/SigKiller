<?php
/*
 * Project:    BaiSigKiller
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/SigKiller
 * Website:    https://sigkiller.baiccb.cn/
 *
 * update.php — 远程更新检查（GET）
 *
 * 请求：
 *   GET /api/update.php?versionCode=<当前客户端版本码>
 *   versionCode 可省略，省略时按 0 处理（视为需要更新）。
 *
 * 返回（application/json）：
 *   {
 *     "ok": true,
 *     "hasUpdate": true,           // latest.versionCode > 客户端 versionCode
 *     "force": false,              // 客户端低于 minVersionCode 或 latest.force
 *     "clientVersionCode": 1,
 *     "latest": { versionCode, versionName, publishedAt, changelog,
 *                 downloadUrl, sizeBytes, sha256, force, minVersionCode },
 *     "serverTime": "2026-11-23T18:00:00+08:00"
 *   }
 *
 * 数据来自同目录 releases.json；新增版本只改那个文件，无需动本脚本。
 */

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');

/** 统一输出并结束。 */
function respond(array $payload, int $status = 200): void
{
    http_response_code($status);
    echo json_encode(
        $payload,
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    respond(['ok' => false, 'error' => '仅支持 GET 请求'], 405);
}

// 读取客户端版本码：只接受非负整数，非法值一律当 0（避免注入与误判）
$raw = isset($_GET['versionCode']) ? trim((string)$_GET['versionCode']) : '0';
$clientCode = ctype_digit($raw) ? (int)$raw : 0;
if ($clientCode < 0 || $clientCode > 2147483647) {
    $clientCode = 0;
}

$dataFile = __DIR__ . '/releases.json';
if (!is_file($dataFile)) {
    respond(['ok' => false, 'error' => '版本数据不存在'], 500);
}

$json = file_get_contents($dataFile);
if ($json === false) {
    respond(['ok' => false, 'error' => '版本数据读取失败'], 500);
}

$data = json_decode($json, true);
if (!is_array($data) || !isset($data['releases']) || !is_array($data['releases']) || count($data['releases']) === 0) {
    respond(['ok' => false, 'error' => '版本数据格式错误'], 500);
}

// 取 versionCode 最大的一条作为最新版本（不依赖数组顺序）
$latest = null;
foreach ($data['releases'] as $release) {
    if (!is_array($release) || !isset($release['versionCode'])) {
        continue;
    }
    $code = (int)$release['versionCode'];
    if ($latest === null || $code > (int)$latest['versionCode']) {
        $latest = $release;
    }
}
if ($latest === null) {
    respond(['ok' => false, 'error' => '无可用版本'], 500);
}

$latestCode = (int)$latest['versionCode'];
$minCode = isset($latest['minVersionCode']) ? (int)$latest['minVersionCode'] : 0;
$hasUpdate = $latestCode > $clientCode;
$force = $hasUpdate && (
    !empty($latest['force']) ||
    ($minCode > 0 && $clientCode < $minCode)
);

respond([
    'ok' => true,
    'hasUpdate' => $hasUpdate,
    'force' => $force,
    'clientVersionCode' => $clientCode,
    'latest' => [
        'versionCode' => $latestCode,
        'versionName' => (string)($latest['versionName'] ?? ''),
        'publishedAt' => (string)($latest['publishedAt'] ?? ''),
        'changelog' => (string)($latest['changelog'] ?? ''),
        'downloadUrl' => (string)($latest['downloadUrl'] ?? ''),
        'sizeBytes' => (int)($latest['sizeBytes'] ?? 0),
        'sha256' => (string)($latest['sha256'] ?? ''),
        'force' => !empty($latest['force']),
        'minVersionCode' => $minCode,
    ],
    'serverTime' => date('c'),
]);
