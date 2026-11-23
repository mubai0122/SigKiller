<?php
/*
 * Project:    BaiSigKiller
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/SigKiller
 * Website:    https://sigkiller.baiccb.cn/
 *
 * history.php — 更新历史列表（GET）
 *
 * 请求：
 *   GET /api/history.php                       返回全部版本
 *   GET /api/history.php?versionCode=1         每条目标注 isCurrent / isNewer
 *   GET /api/history.php?versionCode=1&limit=5 只取最近 5 条
 *
 * 返回（application/json）：
 *   {
 *     "ok": true,
 *     "package": "bai.sigkiller",
 *     "clientVersionCode": 1,
 *     "count": 2,
 *     "history": [
 *       { ..., "isCurrent": false, "isNewer": true }
 *     ],
 *     "serverTime": "..."
 *   }
 *
 * 数据来自同目录 releases.json。
 */

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');

function respond(array $payload, int $status = 200): void
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    respond(['ok' => false, 'error' => '仅支持 GET 请求'], 405);
}

$raw = isset($_GET['versionCode']) ? trim((string)$_GET['versionCode']) : null;
$clientCode = ($raw !== null && ctype_digit($raw)) ? (int)$raw : null;
if ($clientCode !== null && ($clientCode < 0 || $clientCode > 2147483647)) {
    $clientCode = null;
}

$limit = 100;
if (isset($_GET['limit'])) {
    $limitRaw = trim((string)$_GET['limit']);
    if (ctype_digit($limitRaw)) {
        $limit = max(1, min(100, (int)$limitRaw));
    }
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
if (!is_array($data) || !isset($data['releases']) || !is_array($data['releases'])) {
    respond(['ok' => false, 'error' => '版本数据格式错误'], 500);
}

// 按 versionCode 降序
$releases = array_values(array_filter($data['releases'], static function ($r) {
    return is_array($r) && isset($r['versionCode']);
}));
usort($releases, static function ($a, $b) {
    return (int)$b['versionCode'] <=> (int)$a['versionCode'];
});
$releases = array_slice($releases, 0, $limit);

$history = [];
foreach ($releases as $release) {
    $code = (int)$release['versionCode'];
    $history[] = [
        'versionCode' => $code,
        'versionName' => (string)($release['versionName'] ?? ''),
        'publishedAt' => (string)($release['publishedAt'] ?? ''),
        'changelog' => (string)($release['changelog'] ?? ''),
        'downloadUrl' => (string)($release['downloadUrl'] ?? ''),
        'sizeBytes' => (int)($release['sizeBytes'] ?? 0),
        'force' => !empty($release['force']),
        'isCurrent' => $clientCode !== null && $code === $clientCode,
        'isNewer' => $clientCode !== null && $code > $clientCode,
    ];
}

respond([
    'ok' => true,
    'package' => (string)($data['package'] ?? ''),
    'clientVersionCode' => $clientCode,
    'count' => count($history),
    'history' => $history,
    'serverTime' => date('c'),
]);
