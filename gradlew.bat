@echo off
setlocal
set GRADLE_VERSION=9.6.1
set ROOT_DIR=%~dp0
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Please install Gradle 9.6.1 or run this project through Docker.
exit /b 1
