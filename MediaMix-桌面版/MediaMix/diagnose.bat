@echo off
rem ASCII-only script; report is written as UTF-8
chcp 65001 >nul
cd /d "%~dp0"
set "REPORT=%~dp0diagnostic-report.txt"

echo === MediaMix Diagnostics %DATE% %TIME% === > "%REPORT%"
echo [1] Path: %CD% >> "%REPORT%"

echo [2] File count (expect 234): >> "%REPORT%"
dir /s /b /a-d 2>nul | find /c /v "" >> "%REPORT%"

echo [3] Core files: >> "%REPORT%"
if exist "runtime\bin\server\jvm.dll" (echo   jvm.dll OK >> "%REPORT%") else (echo   jvm.dll MISSING >> "%REPORT%")
if exist "runtime\bin\jli.dll" (echo   jli.dll OK >> "%REPORT%") else (echo   jli.dll MISSING >> "%REPORT%")
if exist "runtime\lib\modules" (echo   lib\modules OK >> "%REPORT%") else (echo   lib\modules MISSING >> "%REPORT%")
if exist "app\MediaMix.cfg" (echo   app\MediaMix.cfg OK >> "%REPORT%") else (echo   app\MediaMix.cfg MISSING >> "%REPORT%")

echo [4] runtime java -version test: >> "%REPORT%"
"runtime\bin\java.exe" -version >> "%REPORT%" 2>&1
echo   (java.exe may be absent in slim runtime - check [3] instead) >> "%REPORT%"

echo [5] Launch MediaMix.exe (window will open; close it if it starts): >> "%REPORT%"
MediaMix.exe >> "%REPORT%" 2>&1
echo   launcher exit code: %ERRORLEVEL% >> "%REPORT%"

echo. >> "%REPORT%"
echo Done. Opening report...
notepad "%REPORT%"
