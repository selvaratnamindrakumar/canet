@echo off
:: ============================================================
:: NSSM Windows Service Installer — CANET Validator
::
:: Prerequisites:
::   1. Java 17 installed
::   2. MySQL running and schema created
::   3. nssm.exe on PATH or in the same directory as this script
::   4. Run this script as Administrator
::   5. canet-validator-0.0.1-SNAPSHOT.jar built and present
::
:: Usage:
::   nssm-install.bat           — install with defaults below
::   nssm-install.bat uninstall — remove the service
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Configuration — adjust these before running ──────────────────
set SERVICE_NAME=CaNetValidator
set DISPLAY_NAME=CANET Validator
set DESCRIPTION=Validator REST service — stores message hashes and answers existence queries for Diosma
set INSTALL_DIR=C:\canet\validator
set JAR_NAME=canet-validator-0.0.1-SNAPSHOT.jar
set JAVA_HOME=C:\Program Files\Java\jdk-17
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
set JVM_OPTS=-Xms256m -Xmx1g
:: Optional property overrides at service level
:: set EXTRA_ARGS=--server.port=9090 --dedup.window.seconds=7200
set EXTRA_ARGS=
:: ─────────────────────────────────────────────────────────────────

set NSSM=nssm.exe
if exist "%~dp0nssm.exe" set NSSM=%~dp0nssm.exe

if /i "%1"=="uninstall" goto :uninstall

:: ── Validate ─────────────────────────────────────────────────────
echo [INFO] Checking prerequisites...

if not exist "%JAVA_EXE%" (
    echo [ERROR] Java not found at: %JAVA_EXE%
    echo         Update JAVA_HOME in this script.
    exit /b 1
)

if not exist "%INSTALL_DIR%\%JAR_NAME%" (
    echo [ERROR] JAR not found: %INSTALL_DIR%\%JAR_NAME%
    echo         Copy the built JAR to %INSTALL_DIR%\ first.
    exit /b 1
)

if not exist "%INSTALL_DIR%\logs"   mkdir "%INSTALL_DIR%\logs"
if not exist "%INSTALL_DIR%\config" mkdir "%INSTALL_DIR%\config"

echo [INFO] Installing service: %SERVICE_NAME%

:: ── Install ──────────────────────────────────────────────────────
%NSSM% install "%SERVICE_NAME%" "%JAVA_EXE%"
if errorlevel 1 goto :err

%NSSM% set "%SERVICE_NAME%" AppParameters %JVM_OPTS% -jar "%INSTALL_DIR%\%JAR_NAME%" --spring.config.additional-location=optional:file:%INSTALL_DIR%\config\ %EXTRA_ARGS%

%NSSM% set "%SERVICE_NAME%" AppDirectory "%INSTALL_DIR%"

%NSSM% set "%SERVICE_NAME%" DisplayName "%DISPLAY_NAME%"
%NSSM% set "%SERVICE_NAME%" Description "%DESCRIPTION%"

%NSSM% set "%SERVICE_NAME%" Start SERVICE_AUTO_START

%NSSM% set "%SERVICE_NAME%" ObjectName LocalSystem

:: Stdout/stderr capture for JVM startup errors and pre-Logback output
%NSSM% set "%SERVICE_NAME%" AppStdout "%INSTALL_DIR%\logs\service-stdout.log"
%NSSM% set "%SERVICE_NAME%" AppStderr "%INSTALL_DIR%\logs\service-stderr.log"
%NSSM% set "%SERVICE_NAME%" AppStdoutCreationDisposition 4
%NSSM% set "%SERVICE_NAME%" AppStderrCreationDisposition 4

:: Rotate NSSM stdout/stderr daily or at 10 MB
%NSSM% set "%SERVICE_NAME%" AppRotateFiles 1
%NSSM% set "%SERVICE_NAME%" AppRotateOnline 1
%NSSM% set "%SERVICE_NAME%" AppRotateSeconds 86400
%NSSM% set "%SERVICE_NAME%" AppRotateBytes 10485760

:: Auto-restart on unexpected exit with 5-second cooldown
%NSSM% set "%SERVICE_NAME%" AppExit Default Restart
%NSSM% set "%SERVICE_NAME%" AppRestartDelay 5000

%NSSM% set "%SERVICE_NAME%" AppEnvironmentExtra "JAVA_HOME=%JAVA_HOME%"

echo.
echo [OK] Service "%SERVICE_NAME%" installed.
echo      The validator must be started BEFORE the generator.
echo      Start with:   sc start %SERVICE_NAME%
echo      Stop with:    sc stop  %SERVICE_NAME%
echo      Status:       sc query %SERVICE_NAME%
echo      Health check: http://localhost:8080/api/validator/health
echo      Edit config:  %INSTALL_DIR%\config\application.properties
echo      Logs:         %INSTALL_DIR%\logs\
echo.
goto :eof

:uninstall
echo [INFO] Stopping and removing service: %SERVICE_NAME%
sc stop "%SERVICE_NAME%" 2>nul
timeout /t 3 /nobreak >nul
%NSSM% remove "%SERVICE_NAME%" confirm
echo [OK] Service removed.
goto :eof

:err
echo [ERROR] NSSM command failed. Check that nssm.exe is available and you are running as Administrator.
exit /b 1
