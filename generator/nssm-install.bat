@echo off
:: ============================================================
:: NSSM Windows Service Installer — CANET Generator
::
:: Prerequisites:
::   1. Java 17 installed
::   2. nssm.exe on PATH or in the same directory as this script
::   3. Run this script as Administrator
::   4. canet-generator-0.0.1-SNAPSHOT.jar built and present
::
:: Usage:
::   nssm-install.bat           — install with defaults below
::   nssm-install.bat uninstall — remove the service
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Configuration — adjust these before running ──────────────────
set SERVICE_NAME=CaNetGenerator
set DISPLAY_NAME=CANET Generator
set DESCRIPTION=UDP packet capture service — computes MD5 and registers with CaNet Validator
set INSTALL_DIR=C:\canet\generator
set JAR_NAME=canet-generator-0.0.1-SNAPSHOT.jar
set JAVA_HOME=C:\Program Files\Java\jdk-17
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
set JVM_OPTS=-Xms128m -Xmx512m
:: Optional: override any property at service level.
:: TIP — run the service once with network.interface.name blank to log all
:: available pcap interfaces, then set the chosen name below and reinstall.
:: set EXTRA_ARGS=--network.interface.name=\\Device\\NPF_{XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX}
:: set EXTRA_ARGS=--network.interface.name=eth0 --validator.base-url=http://10.0.0.5:8080
set EXTRA_ARGS=
:: ─────────────────────────────────────────────────────────────────

:: Resolve nssm path — prefer local copy, fall back to PATH
set NSSM=nssm.exe
if exist "%~dp0nssm.exe" set NSSM=%~dp0nssm.exe

:: Handle uninstall
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

:: Create required directories
if not exist "%INSTALL_DIR%\logs" mkdir "%INSTALL_DIR%\logs"
if not exist "%INSTALL_DIR%\config" mkdir "%INSTALL_DIR%\config"

echo [INFO] Installing service: %SERVICE_NAME%

:: ── Install ──────────────────────────────────────────────────────
%NSSM% install "%SERVICE_NAME%" "%JAVA_EXE%"
if errorlevel 1 goto :err

:: JVM + jar + Spring Boot config location
:: config\application.properties overrides the embedded one (Spring Boot convention)
%NSSM% set "%SERVICE_NAME%" AppParameters %JVM_OPTS% -jar "%INSTALL_DIR%\%JAR_NAME%" --spring.config.additional-location=optional:file:%INSTALL_DIR%\config\ %EXTRA_ARGS%

:: Working directory — rolling log paths resolve relative to this
%NSSM% set "%SERVICE_NAME%" AppDirectory "%INSTALL_DIR%"

:: Service identity and description
%NSSM% set "%SERVICE_NAME%" DisplayName "%DISPLAY_NAME%"
%NSSM% set "%SERVICE_NAME%" Description "%DESCRIPTION%"

:: Start automatically with Windows
%NSSM% set "%SERVICE_NAME%" Start SERVICE_AUTO_START

:: Run as LocalSystem (change to a dedicated account for production)
%NSSM% set "%SERVICE_NAME%" ObjectName LocalSystem

:: Stdout/stderr capture — these catch JVM startup errors and anything
:: printed before Logback is initialised (e.g. port-already-in-use).
:: Logback handles the rolling application log separately.
%NSSM% set "%SERVICE_NAME%" AppStdout "%INSTALL_DIR%\logs\service-stdout.log"
%NSSM% set "%SERVICE_NAME%" AppStderr "%INSTALL_DIR%\logs\service-stderr.log"
%NSSM% set "%SERVICE_NAME%" AppStdoutCreationDisposition 4
%NSSM% set "%SERVICE_NAME%" AppStderrCreationDisposition 4

:: Rotate NSSM's own stdout/stderr daily or at 10 MB
%NSSM% set "%SERVICE_NAME%" AppRotateFiles 1
%NSSM% set "%SERVICE_NAME%" AppRotateOnline 1
%NSSM% set "%SERVICE_NAME%" AppRotateSeconds 86400
%NSSM% set "%SERVICE_NAME%" AppRotateBytes 10485760

:: Restart the service automatically if it exits unexpectedly
%NSSM% set "%SERVICE_NAME%" AppExit Default Restart
%NSSM% set "%SERVICE_NAME%" AppRestartDelay 5000

:: Environment — make JAVA_HOME available inside the service
%NSSM% set "%SERVICE_NAME%" AppEnvironmentExtra "JAVA_HOME=%JAVA_HOME%"

echo.
echo [OK] Service "%SERVICE_NAME%" installed.
echo      Start with:   sc start %SERVICE_NAME%
echo      Stop with:    sc stop  %SERVICE_NAME%
echo      Status:       sc query %SERVICE_NAME%
echo      Edit config:  %INSTALL_DIR%\config\application.properties
echo      Logs:         %INSTALL_DIR%\logs\
echo.
goto :eof

:: ── Uninstall ────────────────────────────────────────────────────
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
