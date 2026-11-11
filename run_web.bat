@echo off
setlocal

where java >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set JAVA_CMD=java
) else if exist "C:\Program Files\Java\jdk-21.0.11\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
) else (
    echo Error: Java runtime not found in PATH or standard directory.
    exit /b 1
)

if not exist "out\lms\WebServer.class" (
    echo Binaries not found. Running compile first...
    call "%~dp0compile.bat"
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

set DATA_DIR=%~1
if "%DATA_DIR%"=="" set DATA_DIR=data

set PORT=%~2
if "%PORT%"=="" set PORT=8080

echo Starting Library Management Web Application...
echo Data directory: %DATA_DIR%
echo Web server port: %PORT%
echo Web UI will be available at: http://localhost:%PORT%

start "" "http://localhost:%PORT%"

"%JAVA_CMD%" -cp out lms.WebServer "%DATA_DIR%" %PORT%
endlocal
