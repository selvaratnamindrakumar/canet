@echo off
REM =============================================================================
REM  setup-kafka-ssl.bat
REM
REM  Windows equivalent of setup-kafka-ssl.sh.
REM  Run on the Windows secure desktop where the Kafka client will run.
REM  Copy broker-side files (broker-1.p12, broker-truststore.p12) to the Linux
REM  Kafka broker via SCP or shared drive.
REM
REM  Prerequisites: JDK on PATH (keytool)
REM  Output dir:    kafka-ssl\
REM
REM  Usage:
REM    set BROKER_HOST=broker1.yourdomain.local
REM    set BROKER_IP=10.0.0.10
REM    set MTLS=true
REM    setup-kafka-ssl.bat
REM =============================================================================
setlocal EnableDelayedExpansion

REM ── Customise these ──────────────────────────────────────────────────────────
if not defined BROKER_HOST set BROKER_HOST=broker1.yourdomain.local
if not defined BROKER_IP   set BROKER_IP=127.0.0.1
if not defined CLIENT_CN   set CLIENT_CN=windows-secure-desktop
if not defined STORE_PASS  set STORE_PASS=changeit
if not defined VALIDITY_CA   set VALIDITY_CA=3650
if not defined VALIDITY_CERT set VALIDITY_CERT=825
if not defined MTLS        set MTLS=false
set ORG=O=Test, L=London, ST=London, C=GB
set OUT=kafka-ssl
REM ─────────────────────────────────────────────────────────────────────────────

if not exist %OUT% mkdir %OUT%
cd %OUT%

echo.
echo == Step 1 - Create Kafka Test CA ==
keytool -genkeypair ^
  -alias kafka-ca ^
  -keyalg RSA -keysize 4096 -validity %VALIDITY_CA% ^
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass %STORE_PASS% ^
  -dname "CN=Kafka Test CA, OU=QA, %ORG%"
if errorlevel 1 goto :error

keytool -exportcert ^
  -alias kafka-ca ^
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass %STORE_PASS% ^
  -rfc -file kafka-ca.crt
if errorlevel 1 goto :error

echo [OK] kafka-ca.p12  kafka-ca.crt

echo.
echo == Step 2 - Create Broker Keystore ==
keytool -genkeypair ^
  -alias broker-1 ^
  -keyalg RSA -keysize 2048 -validity %VALIDITY_CERT% ^
  -storetype PKCS12 -keystore broker-1.p12 ^
  -storepass %STORE_PASS% -keypass %STORE_PASS% ^
  -dname "CN=%BROKER_HOST%, OU=Kafka, %ORG%"
if errorlevel 1 goto :error

keytool -certreq ^
  -alias broker-1 ^
  -storetype PKCS12 -keystore broker-1.p12 -storepass %STORE_PASS% ^
  -file broker-1.csr
if errorlevel 1 goto :error

keytool -gencert ^
  -alias kafka-ca ^
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass %STORE_PASS% ^
  -validity %VALIDITY_CERT% ^
  -infile broker-1.csr -outfile broker-1-signed.crt -rfc ^
  -ext "SAN=DNS:%BROKER_HOST%,IP:%BROKER_IP%" ^
  -ext "KeyUsage:critical=digitalSignature,keyEncipherment" ^
  -ext "ExtendedKeyUsage=serverAuth"
if errorlevel 1 goto :error

keytool -importcert -noprompt ^
  -alias kafka-ca -file kafka-ca.crt ^
  -storetype PKCS12 -keystore broker-1.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error

keytool -importcert -noprompt ^
  -alias broker-1 -file broker-1-signed.crt ^
  -storetype PKCS12 -keystore broker-1.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error

echo [OK] broker-1.p12

echo.
echo == Step 3 - Create Broker Truststore ==
keytool -importcert -noprompt ^
  -alias kafka-ca -file kafka-ca.crt ^
  -storetype PKCS12 -keystore broker-truststore.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error
echo [OK] broker-truststore.p12

echo.
echo == Step 4 - Create Windows Client Truststore ==
keytool -importcert -noprompt ^
  -alias kafka-ca -file kafka-ca.crt ^
  -storetype PKCS12 -keystore client-truststore.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error
echo [OK] client-truststore.p12

if /i "%MTLS%"=="true" (
  echo.
  echo == Step 5 - Create Windows Client Keystore (mTLS) ==

  keytool -genkeypair ^
    -alias desktop-client ^
    -keyalg RSA -keysize 2048 -validity %VALIDITY_CERT% ^
    -storetype PKCS12 -keystore client-keystore.p12 ^
    -storepass %STORE_PASS% -keypass %STORE_PASS% ^
    -dname "CN=%CLIENT_CN%, OU=QA, %ORG%"
  if errorlevel 1 goto :error

  keytool -certreq ^
    -alias desktop-client ^
    -storetype PKCS12 -keystore client-keystore.p12 -storepass %STORE_PASS% ^
    -file client.csr
  if errorlevel 1 goto :error

  keytool -gencert ^
    -alias kafka-ca ^
    -storetype PKCS12 -keystore kafka-ca.p12 -storepass %STORE_PASS% ^
    -validity %VALIDITY_CERT% ^
    -infile client.csr -outfile client-signed.crt -rfc ^
    -ext "KeyUsage:critical=digitalSignature" ^
    -ext "ExtendedKeyUsage=clientAuth"
  if errorlevel 1 goto :error

  keytool -importcert -noprompt ^
    -alias kafka-ca -file kafka-ca.crt ^
    -storetype PKCS12 -keystore client-keystore.p12 -storepass %STORE_PASS%
  if errorlevel 1 goto :error

  keytool -importcert -noprompt ^
    -alias desktop-client -file client-signed.crt ^
    -storetype PKCS12 -keystore client-keystore.p12 -storepass %STORE_PASS%
  if errorlevel 1 goto :error

  echo [OK] client-keystore.p12
) else (
  echo [SKIP] Client keystore - set MTLS=true to create
)

echo.
echo == Files created ==
dir /b *.p12 *.crt

echo.
echo == Linux Kafka broker server.properties ==
echo listeners=SSL://0.0.0.0:9093
echo ssl.keystore.location=/var/private/ssl/broker-1.p12
echo ssl.keystore.password=%STORE_PASS%
echo ssl.key.password=%STORE_PASS%
echo ssl.truststore.location=/var/private/ssl/broker-truststore.p12
echo ssl.truststore.password=%STORE_PASS%
if /i "%MTLS%"=="true" (echo ssl.client.auth=required) else (echo ssl.client.auth=none)
echo security.inter.broker.protocol=SSL

echo.
echo == Windows client application.properties ==
echo source.ssl.enabled=true
echo source.ssl.truststore-path=C:\\ssl\\client-truststore.p12
echo source.ssl.truststore-password=%STORE_PASS%
if /i "%MTLS%"=="true" (
  echo source.ssl.keystore-path=C:\\ssl\\client-keystore.p12
  echo source.ssl.keystore-password=%STORE_PASS%
)

goto :eof

:error
echo.
echo [ERROR] Last keytool command failed. Check output above.
exit /b 1
