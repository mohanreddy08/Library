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

if not exist "out\lms\Main.class" (
    echo Binaries not found. Running compile first...
    call "%~dp0compile.bat"
    if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

set DATA_DIR=%~1
if "%DATA_DIR%"=="" set DATA_DIR=data

echo Starting Library Management System with data folder '%DATA_DIR%'...
"%JAVA_CMD%" -cp out lms.Main "%DATA_DIR%"
endlocal
