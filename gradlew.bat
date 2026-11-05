@echo off
setlocal
set "GRADLE_VERSION=8.13"
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "GRADLE_HOME=%GRADLE_USER_HOME%\rudra\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_HOME%\bin\gradle.bat" goto RUN_GRADLE
set "ZIP_FILE=%GRADLE_USER_HOME%\rudra\gradle-%GRADLE_VERSION%-bin.zip"
if not exist "%GRADLE_USER_HOME%\rudra" mkdir "%GRADLE_USER_HOME%\rudra"
echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'; Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%GRADLE_USER_HOME%\rudra' -Force"
if errorlevel 1 (
  echo Could not download Gradle. Check your internet connection and try again.
  exit /b 1
)
:RUN_GRADLE
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
