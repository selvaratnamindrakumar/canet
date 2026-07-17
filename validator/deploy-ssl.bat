@echo off
:: ============================================================
:: deploy-ssl.bat — Copy the validator keystore to multiple
::                  test environment machines.
::
:: Run this from the build / admin machine after running
:: gen-keystore.bat once to produce the master keystore.
::
:: Prerequisites:
::   1. Admin shares (\\<host>\C$) accessible from this machine.
::   2. You have write permission to C:\canet\validator\config\
::      on each target machine.
::   3. The CaNetValidator service should be stopped on each
::      target before copying and restarted after.
::
:: Usage:
::   deploy-ssl.bat           — copy to all hosts in ENVS list
::   deploy-ssl.bat test-env-01 test-env-02   — override hosts
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Configuration ────────────────────────────────────────────
:: Source keystore (generated once by gen-keystore.bat)
set SOURCE_KEYSTORE=C:\canet\validator\config\keystore.p12
set SOURCE_CERT=C:\canet\validator\config\canet-validator.crt

:: Destination path on each remote machine (via admin share \\host\C$)
set DEST_DIR=C$\canet\validator\config

:: Default list of test environment hostnames or IPs.
:: Override on the command line: deploy-ssl.bat host1 host2 host3
set "ENVS="
if not "%~1"=="" (
    set "ENVS=%*"
) else (
    :: Edit this list to match your test environment hostnames / IPs:
    set "ENVS=test-env-01 test-env-02 test-env-03"
)

set PASS=0
set FAIL=0
:: ─────────────────────────────────────────────────────────────

if not exist "%SOURCE_KEYSTORE%" (
    echo [ERROR] Master keystore not found: %SOURCE_KEYSTORE%
    echo         Run gen-keystore.bat first.
    exit /b 1
)

echo.
echo [INFO] Deploying SSL keystore to: %ENVS%
echo.

for %%E in (%ENVS%) do (
    echo ── %%E ────────────────────────────────────────────
    set DEST=\\%%E\%DEST_DIR%

    if not exist "!DEST!" (
        echo [INFO]  Creating directory !DEST!
        mkdir "!DEST!" 2>nul
        if errorlevel 1 (
            echo [ERROR] Cannot create !DEST! — check admin share and permissions.
            set /a FAIL+=1
            goto :next_env
        )
    )

    echo [INFO]  Stopping service on %%E ...
    sc \\%%E stop CaNetValidator >nul 2>&1
    timeout /t 3 /nobreak >nul

    echo [INFO]  Copying keystore ...
    copy /Y "%SOURCE_KEYSTORE%" "!DEST!\keystore.p12"
    if errorlevel 1 (
        echo [ERROR] Copy failed — %%E skipped.
        set /a FAIL+=1
        goto :next_env
    )

    echo [INFO]  Copying public certificate ...
    copy /Y "%SOURCE_CERT%" "!DEST!\canet-validator.crt"

    echo [INFO]  Starting service on %%E ...
    sc \\%%E start CaNetValidator >nul 2>&1

    echo [OK]    %%E complete.
    set /a PASS+=1

    :next_env
)

echo.
echo ════════════════════════════════════════════════
echo  Deployed: %PASS%   Failed: %FAIL%
echo.
if %FAIL% GTR 0 (
    echo  Check network access and admin share permissions
    echo  for the failed hosts above.
)
echo ════════════════════════════════════════════════
echo.
