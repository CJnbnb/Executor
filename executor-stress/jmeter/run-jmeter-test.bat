@echo off
REM ============================================================================
REM Executor JMeter 压力测试启动脚本
REM 使用方法: run-jmeter-test.bat [low|high]
REM 前置条件: JMETER_HOME 环境变量指向 JMeter 安装目录
REM ============================================================================

setlocal enabledelayedexpansion

if "%JMETER_HOME%"=="" (
    set JMETER_HOME=D:\JMeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3
)

if not exist "%JMETER_HOME%\bin\jmeter.bat" (
    echo [ERROR] JMeter not found at %JMETER_HOME%
    echo Please set JMETER_HOME environment variable or install JMeter.
    exit /b 1
)

set SCRIPT_DIR=%~dp0
set REPORT_DIR=%SCRIPT_DIR%reports
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"

set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

set SCENARIO=%1
if "%SCENARIO%"=="" set SCENARIO=low

if "%SCENARIO%"=="low" (
    echo ================================================================
    echo Running LOW-FREQUENCY Stress Test
    echo ================================================================
    "%JMETER_HOME%\bin\jmeter.bat" -n -t "%SCRIPT_DIR%low-freq-stress.jmx" ^
        -l "%REPORT_DIR%\low-freq-%TIMESTAMP%.jtl" ^
        -e -o "%REPORT_DIR%\low-freq-%TIMESTAMP%" ^
        -JNUM_TASKS=5000 -JNUM_BIZ_GROUPS=5
)

if "%SCENARIO%"=="high" (
    echo ================================================================
    echo Running HIGH-FREQUENCY Stress Test
    echo ================================================================
    "%JMETER_HOME%\bin\jmeter.bat" -n -t "%SCRIPT_DIR%high-freq-stress.jmx" ^
        -l "%REPORT_DIR%\high-freq-%TIMESTAMP%.jtl" ^
        -e -o "%REPORT_DIR%\high-freq-%TIMESTAMP%" ^
        -JNUM_TASKS=3000
)

if "%SCENARIO%"=="all" (
    call %0 low
    call %0 high
)

echo.
echo Test complete. Reports: %REPORT_DIR%
endlocal
