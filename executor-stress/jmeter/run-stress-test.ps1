# ============================================================================
# Executor 压力测试脚本 (PowerShell)
# 使用方法: .\run-stress-test.ps1 [-Scenario low|high|full] [-Host localhost] [-Port 8083]
# ============================================================================
param(
    [ValidateSet("low","high","full")]
    [string]$Scenario = "full",
    [string]$HostAddr = "localhost",
    [int]$Port = 8083
)

$BaseUrl = "http://${HostAddr}:${Port}/stress"
$ErrorActionPreference = "Continue"

function Invoke-StressApi {
    param([string]$Method, [string]$Path, $Body = $null)
    $uri = "$BaseUrl$Path"
    $params = @{
        Uri = $uri
        Method = $Method
        ContentType = "application/json"
    }
    if ($Body) {
        $params.Body = ($Body | ConvertTo-Json -Compress)
    }
    try {
        $response = Invoke-RestMethod @params
        return $response
    } catch {
        Write-Host "ERROR: $_" -ForegroundColor Red
        return $null
    }
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan
}

function Write-Metric {
    param([string]$Label, $Value)
    Write-Host "  $($Label.PadRight(30)): $Value" -ForegroundColor White
}

# ============================================================================
# Health Check
# ============================================================================
Write-Section "Health Check"
$health = Invoke-StressApi -Method "GET" -Path "/health"
if ($health.status -eq "UP") {
    Write-Host "  [OK] executor-stress is running" -ForegroundColor Green
    Write-Host "  DB: $($health.db)" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Cannot reach executor-stress at $BaseUrl" -ForegroundColor Red
    exit 1
}

# ============================================================================
# Low-Frequency Stress Test
# ============================================================================
if ($Scenario -eq "low" -or $Scenario -eq "full") {
    Write-Section "LOW-FREQUENCY STRESS TEST (CommonTask / XXL-Job 调度模拟)"

    # --- Setup ---
    $numTasks = 5000
    $numBizGroups = 5
    Write-Host "[1/6] Setting up $numTasks tasks across $numBizGroups biz groups..." -ForegroundColor Yellow
    $setup = Invoke-StressApi -Method "POST" -Path "/low-freq/setup" -Body @{
        numTasks = $numTasks
        numBizGroups = $numBizGroups
        cronExpr = "0/1 * * * * ?"
        baseBizName = "stress-test"
        topic = "executorConsumeTask"
    }
    if ($setup.success) {
        Write-Host "  Created: $($setup.totalCreated) tasks, $($setup.numBizGroups) biz groups" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $($setup.error)" -ForegroundColor Red
    }

    # --- Serial Trigger (Single Biz TPS) ---
    Write-Host "[2/6] Serial trigger test (single biz throughput)..." -ForegroundColor Yellow
    $serialResults = @()
    for ($i = 0; $i -lt 5; $i++) {
        $result = Invoke-StressApi -Method "POST" -Path "/low-freq/trigger" -Body @{
            bizParam = "stress-test,group-0"
        }
        if ($result.success) {
            $serialResults += $result
        }
        Start-Sleep -Milliseconds 200
    }
    $serialTotal = ($serialResults | Measure-Object -Property processed -Sum).Sum
    Write-Host "  Serial: $serialTotal tasks processed"

    # --- Concurrent Multi-Biz Trigger ---
    Write-Host "[3/6] Concurrent multi-biz trigger (sharding)..." -ForegroundColor Yellow
    $jobs = @()
    for ($g = 0; $g -lt $numBizGroups; $g++) {
        $bizParam = "stress-test,group-$g"
        $jobs += Start-Job -ScriptBlock {
            param($url, $biz)
            Invoke-RestMethod -Uri "$url/low-freq/trigger" -Method POST -ContentType "application/json" -Body "{`"bizParam`":`"$biz`"}"
        } -ArgumentList $BaseUrl, $bizParam
    }
    $concurrentResults = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job
    $concurrentTotal = ($concurrentResults | Measure-Object -Property processed -Sum).Sum
    Write-Host "  Concurrent: $concurrentTotal tasks across $numBizGroups biz groups"

    # --- Run All (Continuous) ---
    Write-Host "[4/6] Run-all continuous trigger (max TPS)..." -ForegroundColor Yellow
    $startTime = Get-Date
    $runResults = @()
    for ($g = 0; $g -lt $numBizGroups; $g++) {
        $result = Invoke-StressApi -Method "POST" -Path "/low-freq/run" -Body @{
            bizParam = "stress-test,group-$g"
            maxRounds = 100
        }
        if ($result.success) {
            $runResults += $result
        }
    }
    $elapsed = (Get-Date) - $startTime
    $totalProcessed = ($runResults | Measure-Object -Property totalProcessed -Sum).Sum
    $totalSuccess = ($runResults | Measure-Object -Property mqSuccess -Sum).Sum
    $totalFail = ($runResults | Measure-Object -Property mqFail -Sum).Sum
    $tps = if ($elapsed.TotalSeconds -gt 0) { [math]::Round($totalProcessed / $elapsed.TotalSeconds, 2) } else { 0 }

    Write-Host ""
    Write-Host "  === LOW-FREQ RESULTS ===" -ForegroundColor Green
    Write-Metric "Total Processed" $totalProcessed
    Write-Metric "MQ Success" $totalSuccess
    Write-Metric "MQ Fail" $totalFail
    Write-Metric "Elapsed" "$([math]::Round($elapsed.TotalSeconds, 2))s"
    Write-Metric "Throughput (TPS)" $tps
    Write-Metric "Success Rate" "$([math]::Round(100 * $totalSuccess / [Math]::Max($totalProcessed, 1), 2))%"

    # --- Status Check ---
    Write-Host "[5/6] Checking remaining pending tasks..." -ForegroundColor Yellow
    $status = Invoke-StressApi -Method "GET" -Path "/low-freq/status?baseBizName=stress-test&numBizGroups=$numBizGroups"
    Write-Host "  Remaining pending: $($status.totalPending)"
}

# ============================================================================
# High-Frequency Stress Test
# ============================================================================
if ($Scenario -eq "high" -or $Scenario -eq "full") {
    Write-Section "HIGH-FREQUENCY STRESS TEST (RealtimeTask / Time Wheel)"

    # --- Setup ---
    $numTasks = 3000
    Write-Host "[1/4] Setting up $numTasks realtime tasks..." -ForegroundColor Yellow
    $setup = Invoke-StressApi -Method "POST" -Path "/high-freq/setup" -Body @{
        numTasks = $numTasks
        cronExpr = "0/1 * * * * ?"
        bizName = "stress-realtime"
        bizGroup = "hft"
        topic = "executorConsumeTask"
    }
    if ($setup.success) {
        Write-Host "  Created: $($setup.totalCreated) realtime tasks" -ForegroundColor Green
    }

    # --- Observe Time Wheel Processing ---
    Write-Host "[2/4] Observing time wheel auto-processing (30 seconds)..." -ForegroundColor Yellow
    $observations = @()
    for ($i = 0; $i -lt 30; $i++) {
        $status = Invoke-StressApi -Method "GET" -Path "/high-freq/status?bizName=stress-realtime&bizGroup=hft"
        $metrics = Invoke-StressApi -Method "GET" -Path "/metrics"
        $observations += [PSCustomObject]@{
            Second = $i
            Pending = $status.pendingTasks
            ProducedOk = $metrics.tasksProduced
            ProducedFail = $metrics.tasksProducedFailed
        }
        Write-Host "  t=${i}s  pending=$($status.pendingTasks)  tasksProduced=$($metrics.tasksProduced)  tasksProducedFailed=$($metrics.tasksProducedFailed)"
        Start-Sleep -Seconds 1
    }

    # --- Analysis ---
    Write-Host "[3/4] Analyzing time wheel performance..." -ForegroundColor Yellow
    $initialPending = $observations[0].Pending
    $finalPending = $observations[-1].Pending
    $processedInPeriod = $initialPending - $finalPending
    $tps = [math]::Round($processedInPeriod / 30, 2)

    $initialProduced = $observations[0].ProducedOk
    $finalProduced = $observations[-1].ProducedOk
    $mqThroughput = [math]::Round(($finalProduced - $initialProduced) / 30, 2)

    Write-Host ""
    Write-Host "  === HIGH-FREQ RESULTS ===" -ForegroundColor Green
    Write-Metric "Initial Pending" $initialPending
    Write-Metric "Final Pending" $finalPending
    Write-Metric "Processed in 30s" $processedInPeriod
    Write-Metric "Time Wheel TPS" $tps
    Write-Metric "MQ Send Throughput" $mqThroughput
    Write-Metric "Misfire Rate" "$([math]::Round(100 * $finalPending / [Math]::Max($initialPending, 1), 2))% (pending after 30s)"
}

# ============================================================================
# Metrics Snapshot
# ============================================================================
Write-Section "FINAL METRICS"
$metrics = Invoke-StressApi -Method "GET" -Path "/metrics"
if ($metrics) {
    foreach ($prop in $metrics.PSObject.Properties) {
        Write-Metric $prop.Name $prop.Value
    }
}

# ============================================================================
# Cleanup
# ============================================================================
Write-Section "CLEANUP"
$cleanup = Invoke-StressApi -Method "DELETE" -Path "/cleanup?numLowFreqGroups=5"
if ($cleanup.success) {
    Write-Host "  Low-freq deleted: $($cleanup.lowFreqDeleted)" -ForegroundColor Green
    Write-Host "  High-freq deleted: $($cleanup.highFreqDeleted)" -ForegroundColor Green
} else {
    Write-Host "  [WARN] Cleanup error: $($cleanup.error)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Stress test complete!" -ForegroundColor Green
