@echo off
:: ============================================================
:: gen-keystore.bat — One-time SSL certificate setup
::
:: Run this ONCE as Administrator before starting the validator
:: for the first time with SSL enabled.
::
:: What it does:
::   1. Creates C:\canet\validator\config\keystore.p12
::      (the validator's server certificate — Tomcat uses this)
::   2. Exports the public certificate to canet-validator.crt
::   3. Imports that certificate into the middle-tier truststore
::      so the middle tier can verify the validator's identity
::
:: After running this script, copy canet-validator.crt to the
:: middleware machine and run step 3 there with the correct
:: truststore path.
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Configuration ────────────────────────────────────────────
set JAVA_HOME=C:\Program Files\Java\jdk-17
set KEYTOOL=%JAVA_HOME%\bin\keytool.exe

set VALIDATOR_DIR=C:\canet\validator\config
set KEYSTORE=%VALIDATOR_DIR%\keystore.p12
set STORE_PASS=changeit
set ALIAS=canet-validator
set CERT_FILE=%VALIDATOR_DIR%\canet-validator.crt

:: Distinguished Name — adjust CN to the hostname or IP that the
:: middle tier will use to connect (e.g. CN=10.0.0.5 or CN=validator.local)
set DNAME=CN=canet-validator, OU=CaNet, O=CaNet, L=Unknown, ST=Unknown, C=ZA

:: Middle-tier truststore — import the cert here so the middleware
:: trusts the validator's certificate.  Run this step on the middleware
:: machine if it is on a different host.
set MIDDLEWARE_TRUST=%VALIDATOR_DIR%\middleware-truststore.p12
:: ─────────────────────────────────────────────────────────────

if not exist "%KEYTOOL%" (
    echo [ERROR] keytool not found at: %KEYTOOL%
    echo         Update JAVA_HOME in this script.
    exit /b 1
)

if not exist "%VALIDATOR_DIR%" mkdir "%VALIDATOR_DIR%"

:: ── Step 1: Generate self-signed keystore ────────────────────
echo.
echo [STEP 1] Generating validator keystore: %KEYSTORE%
echo          CN / SAN must match the hostname the middle tier connects to.
echo          Edit DNAME in this script if needed.
echo.

if exist "%KEYSTORE%" (
    echo [WARN] Keystore already exists: %KEYSTORE%
    echo        Delete it first if you want to regenerate.
    goto :step2
)

"%KEYTOOL%" -genkeypair ^
    -alias      "%ALIAS%"     ^
    -keyalg     RSA           ^
    -keysize    2048          ^
    -validity   3650          ^
    -storetype  PKCS12        ^
    -keystore   "%KEYSTORE%"  ^
    -storepass  "%STORE_PASS%" ^
    -dname      "%DNAME%"

if errorlevel 1 (
    echo [ERROR] keytool failed — check JAVA_HOME and permissions.
    exit /b 1
)
echo [OK] Keystore created.

:step2
:: ── Step 2: Export the public certificate ────────────────────
echo.
echo [STEP 2] Exporting public certificate: %CERT_FILE%
echo          Give this file to the middleware team.
echo.

"%KEYTOOL%" -exportcert ^
    -alias     "%ALIAS%"     ^
    -storetype PKCS12        ^
    -keystore  "%KEYSTORE%"  ^
    -storepass "%STORE_PASS%" ^
    -rfc                     ^
    -file      "%CERT_FILE%"

if errorlevel 1 (
    echo [ERROR] Export failed.
    exit /b 1
)
echo [OK] Certificate exported to: %CERT_FILE%

:: ── Step 3: Import into middleware truststore ─────────────────
echo.
echo [STEP 3] Importing certificate into middleware truststore: %MIDDLEWARE_TRUST%
echo          If the middleware runs on a different machine, run this
echo          step there with the correct truststore path.
echo.

"%KEYTOOL%" -importcert ^
    -noprompt            ^
    -alias     "%ALIAS%"          ^
    -file      "%CERT_FILE%"      ^
    -keystore  "%MIDDLEWARE_TRUST%" ^
    -storetype PKCS12             ^
    -storepass "%STORE_PASS%"

if errorlevel 1 (
    echo [ERROR] Import into truststore failed.
    exit /b 1
)
echo [OK] Middleware truststore created: %MIDDLEWARE_TRUST%

echo.
echo ════════════════════════════════════════════════════════════
echo  Setup complete.  Next steps:
echo.
echo  1. Start the validator:
echo       sc start CaNetValidator
echo       https://^<host^>:8443/api/validator/health
echo.
echo  2. Copy canet-validator.crt to the middleware machine.
echo     On the middleware machine, run:
echo       keytool -importcert -noprompt -alias canet-validator ^
echo               -file canet-validator.crt ^
echo               -keystore ^<middleware-truststore^>.p12 ^
echo               -storetype PKCS12 -storepass changeit
echo.
echo  3. Configure the middleware to use that truststore when
echo     calling https://^<validator-host^>:8443/api/validator/exists
echo.
echo  4. For mutual TLS (middleware must also present a cert):
echo       - Set server.ssl.client-auth=need in application.properties
echo       - Uncomment server.ssl.trust-store* properties
echo       - Import the middleware certificate into
echo         C:\canet\validator\config\truststore.p12
echo ════════════════════════════════════════════════════════════
echo.
