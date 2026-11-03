@echo off
setlocal
set "JAVA_HOME=D:\Android\jdk-17"
set "ANDROID_HOME=D:\Android\sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ========================================================
echo [Deli Q1A] Building Android APK via CLI...
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%
echo ========================================================

cd /d "%~dp0android"
call gradlew.bat clean assembleDebug

if %ERRORLEVEL% EQU 0 (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "..\deli-printer-debug.apk" >nul
    echo.
    echo ========================================================
    echo  [SUCCESS] APK build completed successfully!
    echo  Output file: %~dp0deli-printer-debug.apk
    echo ========================================================
) else (
    echo.
    echo [ERROR] Build failed with exit code %ERRORLEVEL%
)

if "%1" neq "--nopause" pause
