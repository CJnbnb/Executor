$ErrorActionPreference = "Stop"
$base = "http://localhost:8083/stress/layer"
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
Write-Host "=== Mock Mode Tests Start: $now ==="

# Cleanup first
Write-Host "`n--- Cleanup ---"
Invoke-RestMethod -Uri "$base/cleanup?numBizGroups=10" -Method Delete -Body '{}' -ContentType 'application/json; charset=utf-8'

# ===== Test 1: Layer 1 Mock — 5000 tasks, 5 groups =====
Write-Host "`n=== Test 1: Layer 1 Mock — 5000 tasks, 5 groups ==="
$body = @{
    numTasks = 5000
    numBizGroups = 5
    cronExpr = '0/1 * * * * ?'
    baseBizName = 'layer-mock'
    maxRounds = 200
} | ConvertTo-Json
$r1 = Invoke-RestMethod -Uri "$base/mock-run" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
Write-Host "TPS: $($r1.tps), created: $($r1.created), processed: $($r1.totalProcessed), fail: $($r1.mqFail), benchMs: $($r1.benchMs), setupMs: $($r1.setupMs)"
Write-Host "Heap: $($r1.system.heapUsedMB)MB, Threads: $($r1.system.threadCount), DBconn: $($r1.system.dbConnections), GC: $($r1.system.gcCount)"

# Cleanup
Invoke-RestMethod -Uri "$base/cleanup?numBizGroups=10" -Method Delete -Body '{}' -ContentType 'application/json; charset=utf-8'

# ===== Test 2: Layer 1 Mock — 10000 tasks, 10 groups =====
Write-Host "`n=== Test 2: Layer 1 Mock — 10000 tasks, 10 groups ==="
$body2 = @{
    numTasks = 10000
    numBizGroups = 10
    cronExpr = '0/1 * * * * ?'
    baseBizName = 'layer-mock'
    maxRounds = 200
} | ConvertTo-Json
$r2 = Invoke-RestMethod -Uri "$base/mock-run" -Method Post -Body $body2 -ContentType 'application/json; charset=utf-8'
Write-Host "TPS: $($r2.tps), created: $($r2.created), processed: $($r2.totalProcessed), fail: $($r2.mqFail), benchMs: $($r2.benchMs), setupMs: $($r2.setupMs)"
Write-Host "Heap: $($r2.system.heapUsedMB)MB, Threads: $($r2.system.threadCount), DBconn: $($r2.system.dbConnections), GC: $($r2.system.gcCount)"

# Cleanup
Invoke-RestMethod -Uri "$base/cleanup?numBizGroups=10" -Method Delete -Body '{}' -ContentType 'application/json; charset=utf-8'

# ===== Test 3: TW Mock — 500 tasks =====
Write-Host "`n=== Test 3: TW Mock — 500 tasks ==="
$body3 = @{
    numTasks = 500
    cronExpr = '0/1 * * * * ?'
    bizName = 'tw-mock'
    bizGroup = 'hft'
    observeSeconds = 30
} | ConvertTo-Json
$r3 = Invoke-RestMethod -Uri "$base/time-wheel/mock-run" -Method Post -Body $body3 -ContentType 'application/json; charset=utf-8'
Write-Host "avgTps: $($r3.avgTps), peakTps: $($r3.peakTps), produced: $($r3.totalProduced), failed: $($r3.totalFailed), pending: $($r3.pendingRemaining)"

# Cleanup
Invoke-RestMethod -Uri "$base/cleanup?numBizGroups=10" -Method Delete -Body '{}' -ContentType 'application/json; charset=utf-8'

# ===== Test 4: TW Mock — 1000 tasks =====
Write-Host "`n=== Test 4: TW Mock — 1000 tasks ==="
$body4 = @{
    numTasks = 1000
    cronExpr = '0/1 * * * * ?'
    bizName = 'tw-mock'
    bizGroup = 'hft'
    observeSeconds = 30
} | ConvertTo-Json
$r4 = Invoke-RestMethod -Uri "$base/time-wheel/mock-run" -Method Post -Body $body4 -ContentType 'application/json; charset=utf-8'
Write-Host "avgTps: $($r4.avgTps), peakTps: $($r4.peakTps), produced: $($r4.totalProduced), failed: $($r4.totalFailed), pending: $($r4.pendingRemaining)"

# Cleanup
Invoke-RestMethod -Uri "$base/cleanup?numBizGroups=10" -Method Delete -Body '{}' -ContentType 'application/json; charset=utf-8'

# ===== Test 5: TW Mock — 3000 tasks =====
Write-Host "`n=== Test 5: TW Mock — 3000 tasks ==="
$body5 = @{
    numTasks = 3000
    cronExpr = '0/1 * * * * ?'
    bizName = 'tw-mock'
    bizGroup = 'hft'
    observeSeconds = 30
} | ConvertTo-Json
$r5 = Invoke-RestMethod -Uri "$base/time-wheel/mock-run" -Method Post -Body $body5 -ContentType 'application/json; charset=utf-8'
Write-Host "avgTps: $($r5.avgTps), peakTps: $($r5.peakTps), produced: $($r5.totalProduced), failed: $($r5.totalFailed), pending: $($r5.pendingRemaining)"

Write-Host "`n=== All Mock Tests Complete ==="
