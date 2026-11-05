@echo off
setlocal
cd /d "%~dp0"
echo =============================================
echo   RUDRA STAYLINK - BUILD DEBUG APK
echo =============================================
echo.
where java >nul 2>nul || (echo Java not found. Install JDK 17. & pause & exit /b 1)
java -version
call gradlew.bat clean assembleDebug
if errorlevel 1 (
  echo.
  echo Build failed. Check Android SDK Platform 36 and JDK 17 in Android Studio SDK Manager.
  pause
  exit /b 1
)
echo.
echo APK created at: app\build\outputs\apk\debug\app-debug.apk
pause
