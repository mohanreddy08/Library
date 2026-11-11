@echo off
setlocal
echo Compiling Library Management System...

where javac >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set JAVAC_CMD=javac
) else if exist "C:\Program Files\Java\jdk-21.0.11\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\Java\jdk-21.0.11\bin\javac.exe"
) else (
    echo Error: JDK 21+ javac compiler not found in PATH or standard directory.
    exit /b 1
)

if not exist out mkdir out
"%JAVAC_CMD%" -d out src\lms\*.java
if %ERRORLEVEL% equ 0 (
    echo Build successful. Classes placed in 'out' folder.
) else (
    echo Build failed with error code %ERRORLEVEL%.
)
endlocal
