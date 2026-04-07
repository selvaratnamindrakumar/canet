@echo off
REM =============================================================================
REM  setup-endpoint-ssl.bat
REM
REM  Windows equivalent of setup-endpoint-ssl.sh.
REM  Creates stores for testing generic-forwarder -> mock-endpoint over HTTPS.
REM
REM  Output dir: endpoint-ssl\
REM
REM  Usage:
REM    set ENDPOINT_HOST=api.example.com
REM    set MTLS=true
REM    setup-endpoint-ssl.bat
REM =============================================================================
setlocal EnableDelayedExpansion

if not defined ENDPOINT_HOST set ENDPOINT_HOST=localhost
if not defined ENDPOINT_IP   set ENDPOINT_IP=127.0.0.1
if not defined CLIENT_CN     set CLIENT_CN=generic-forwarder-client
if not defined STORE_PASS    set STORE_PASS=changeit
if not defined VALIDITY_CA   set VALIDITY_CA=3650
if not defined VALIDITY_CERT set VALIDITY_CERT=825
if not defined MTLS          set MTLS=false
set ORG=O=Test, L=London, ST=London, C=GB
set OUT=endpoint-ssl

if not exist %OUT% mkdir %OUT%
cd %OUT%

echo.
echo == Step 1 - Create Endpoint Test CA ==
keytool -genkeypair ^
  -alias endpoint-ca ^
  -keyalg RSA -keysize 4096 -validity %VALIDITY_CA% ^
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass %STORE_PASS% ^
  -dname "CN=Endpoint Test CA, OU=QA, %ORG%"
if errorlevel 1 goto :error

keytool -exportcert ^
  -alias endpoint-ca ^
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass %STORE_PASS% ^
  -rfc -file endpoint-ca.crt
if errorlevel 1 goto :error
echo [OK] endpoint-ca.p12  endpoint-ca.crt

echo.
echo == Step 2 - Create Endpoint Server Keystore ==
keytool -genkeypair ^
  -alias endpoint-server ^
  -keyalg RSA -keysize 2048 -validity %VALIDITY_CERT% ^
  -storetype PKCS12 -keystore endpoint-server.p12 ^
  -storepass %STORE_PASS% -keypass %STORE_PASS% ^
  -dname "CN=%ENDPOINT_HOST%, OU=API, %ORG%"
if errorlevel 1 goto :error

keytool -certreq ^
  -alias endpoint-server ^
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass %STORE_PASS% ^
  -file endpoint-server.csr
if errorlevel 1 goto :error

keytool -gencert ^
  -alias endpoint-ca ^
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass %STORE_PASS% ^
  -validity %VALIDITY_CERT% ^
  -infile endpoint-server.csr -outfile endpoint-server-signed.crt -rfc ^
  -ext "SAN=DNS:%ENDPOINT_HOST%,DNS:localhost,IP:%ENDPOINT_IP%,IP:127.0.0.1" ^
  -ext "KeyUsage:critical=digitalSignature,keyEncipherment" ^
  -ext "ExtendedKeyUsage=serverAuth"
if errorlevel 1 goto :error

keytool -importcert -noprompt ^
  -alias endpoint-ca -file endpoint-ca.crt ^
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error

keytool -importcert -noprompt ^
  -alias endpoint-server -file endpoint-server-signed.crt ^
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error
echo [OK] endpoint-server.p12

echo.
echo == Step 3 - Create Endpoint Server Truststore ==
keytool -importcert -noprompt ^
  -alias endpoint-ca -file endpoint-ca.crt ^
  -storetype PKCS12 -keystore endpoint-truststore.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error
echo [OK] endpoint-truststore.p12

echo.
echo == Step 4 - Create Forwarder Output Truststore ==
keytool -importcert -noprompt ^
  -alias endpoint-ca -file endpoint-ca.crt ^
  -storetype PKCS12 -keystore output-truststore.p12 -storepass %STORE_PASS%
if errorlevel 1 goto :error
echo [OK] output-truststore.p12

if /i "%MTLS%"=="true" (
  echo.
  echo == Step 5 - Create Forwarder Client Keystore (mTLS) ==

  keytool -genkeypair ^
    -alias forwarder-client ^
    -keyalg RSA -keysize 2048 -validity %VALIDITY_CERT% ^
    -storetype PKCS12 -keystore output-keystore.p12 ^
    -storepass %STORE_PASS% -keypass %STORE_PASS% ^
    -dname "CN=%CLIENT_CN%, OU=QA, %ORG%"
  if errorlevel 1 goto :error

  keytool -certreq ^
    -alias forwarder-client ^
    -storetype PKCS12 -keystore output-keystore.p12 -storepass %STORE_PASS% ^
    -file output-client.csr
  if errorlevel 1 goto :error

  keytool -gencert ^
    -alias endpoint-ca ^
    -storetype PKCS12 -keystore endpoint-ca.p12 -storepass %STORE_PASS% ^
    -validity %VALIDITY_CERT% ^
    -infile output-client.csr -outfile output-client-signed.crt -rfc ^
    -ext "KeyUsage:critical=digitalSignature" ^
    -ext "ExtendedKeyUsage=clientAuth"
  if errorlevel 1 goto :error

  keytool -importcert -noprompt ^
    -alias endpoint-ca -file endpoint-ca.crt ^
    -storetype PKCS12 -keystore output-keystore.p12 -storepass %STORE_PASS%
  if errorlevel 1 goto :error

  keytool -importcert -noprompt ^
    -alias forwarder-client -file output-client-signed.crt ^
    -storetype PKCS12 -keystore output-keystore.p12 -storepass %STORE_PASS%
  if errorlevel 1 goto :error

  echo [OK] output-keystore.p12
) else (
  echo [SKIP] Forwarder client keystore - set MTLS=true to create
)

echo.
echo == Files created ==
dir /b *.p12 *.crt

echo.
echo == mock-endpoint application.properties ==
echo server.ssl.key-store=file:/certs/endpoint-server.p12
echo server.ssl.key-store-password=%STORE_PASS%
echo server.ssl.trust-store=file:/certs/endpoint-truststore.p12
echo server.ssl.trust-store-password=%STORE_PASS%
if /i "%MTLS%"=="true" (echo server.ssl.client-auth=NEED) else (echo server.ssl.client-auth=NONE)

echo.
echo == generic-forwarder application.properties ==
echo endpoint.ssl.enabled=true
echo endpoint.ssl.truststore-path=/certs/output-truststore.p12
echo endpoint.ssl.truststore-password=%STORE_PASS%
if /i "%MTLS%"=="true" (
  echo endpoint.ssl.keystore-path=/certs/output-keystore.p12
  echo endpoint.ssl.keystore-password=%STORE_PASS%
)

goto :eof

:error
echo [ERROR] keytool command failed.
exit /b 1
