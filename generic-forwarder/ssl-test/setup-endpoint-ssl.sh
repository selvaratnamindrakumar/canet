#!/usr/bin/env bash
# =============================================================================
#  setup-endpoint-ssl.sh
#
#  Creates keystores / truststores for testing the HTTP endpoint integration
#  (generic-forwarder → mock-endpoint).
#
#  Separate from the Kafka stores so they can be rotated independently.
#
#  Output dir: ./endpoint-ssl/
#
#  Usage:
#    chmod +x setup-endpoint-ssl.sh
#    ENDPOINT_HOST=api.example.com ./setup-endpoint-ssl.sh
#    MTLS=true ./setup-endpoint-ssl.sh   # also creates client keystore
# =============================================================================
set -euo pipefail

# ── Customise these ───────────────────────────────────────────────────────────
ENDPOINT_HOST="${ENDPOINT_HOST:-localhost}"
ENDPOINT_IP="${ENDPOINT_IP:-127.0.0.1}"
CLIENT_CN="${CLIENT_CN:-generic-forwarder-client}"
STORE_PASS="${STORE_PASS:-changeit}"
VALIDITY_CA="${VALIDITY_CA:-3650}"
VALIDITY_CERT="${VALIDITY_CERT:-825}"
ORG="O=Test, L=London, ST=London, C=GB"
MTLS="${MTLS:-false}"
OUT="endpoint-ssl"
# ─────────────────────────────────────────────────────────────────────────────

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
step()  { echo -e "\n${YELLOW}══ $* ══${NC}"; }

mkdir -p "$OUT"
cd "$OUT"

# ── Step 1 : Endpoint test CA ─────────────────────────────────────────────────
step "Step 1 — Create Endpoint Test CA"

keytool -genkeypair \
  -alias endpoint-ca \
  -keyalg RSA -keysize 4096 -validity "$VALIDITY_CA" \
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass "$STORE_PASS" \
  -dname "CN=Endpoint Test CA, OU=QA, $ORG"

keytool -exportcert \
  -alias endpoint-ca \
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass "$STORE_PASS" \
  -rfc -file endpoint-ca.crt

info "CA keystore : endpoint-ca.p12"
info "CA cert     : endpoint-ca.crt"

# ── Step 2 : Endpoint server keystore ────────────────────────────────────────
step "Step 2 — Create Endpoint Server Keystore (${ENDPOINT_HOST})"

keytool -genkeypair \
  -alias endpoint-server \
  -keyalg RSA -keysize 2048 -validity "$VALIDITY_CERT" \
  -storetype PKCS12 -keystore endpoint-server.p12 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=${ENDPOINT_HOST}, OU=API, $ORG"

keytool -certreq \
  -alias endpoint-server \
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass "$STORE_PASS" \
  -file endpoint-server.csr

keytool -gencert \
  -alias endpoint-ca \
  -storetype PKCS12 -keystore endpoint-ca.p12 -storepass "$STORE_PASS" \
  -validity "$VALIDITY_CERT" \
  -infile endpoint-server.csr -outfile endpoint-server-signed.crt -rfc \
  -ext "SAN=DNS:${ENDPOINT_HOST},DNS:localhost,IP:${ENDPOINT_IP},IP:127.0.0.1" \
  -ext "KeyUsage:critical=digitalSignature,keyEncipherment" \
  -ext "ExtendedKeyUsage=serverAuth"

keytool -importcert -noprompt \
  -alias endpoint-ca \
  -file endpoint-ca.crt \
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass "$STORE_PASS"

keytool -importcert -noprompt \
  -alias endpoint-server \
  -file endpoint-server-signed.crt \
  -storetype PKCS12 -keystore endpoint-server.p12 -storepass "$STORE_PASS"

info "Server keystore : endpoint-server.p12  → deploy to mock-endpoint"

# ── Step 3 : Endpoint truststore (for mock-endpoint to trust client certs) ───
step "Step 3 — Create Endpoint Server Truststore"

keytool -importcert -noprompt \
  -alias endpoint-ca \
  -file endpoint-ca.crt \
  -storetype PKCS12 -keystore endpoint-truststore.p12 -storepass "$STORE_PASS"

info "Server truststore : endpoint-truststore.p12  → deploy to mock-endpoint"

# ── Step 4 : Forwarder output truststore ─────────────────────────────────────
step "Step 4 — Create Forwarder Output Truststore"

keytool -importcert -noprompt \
  -alias endpoint-ca \
  -file endpoint-ca.crt \
  -storetype PKCS12 -keystore output-truststore.p12 -storepass "$STORE_PASS"

info "Output truststore : output-truststore.p12  → deploy to generic-forwarder /certs"

# ── Step 5 : Forwarder output keystore (mTLS only) ───────────────────────────
if [[ "$MTLS" == "true" ]]; then
  step "Step 5 — Create Forwarder Client Keystore (mTLS)"

  keytool -genkeypair \
    -alias forwarder-client \
    -keyalg RSA -keysize 2048 -validity "$VALIDITY_CERT" \
    -storetype PKCS12 -keystore output-keystore.p12 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "CN=${CLIENT_CN}, OU=QA, $ORG"

  keytool -certreq \
    -alias forwarder-client \
    -storetype PKCS12 -keystore output-keystore.p12 -storepass "$STORE_PASS" \
    -file output-client.csr

  keytool -gencert \
    -alias endpoint-ca \
    -storetype PKCS12 -keystore endpoint-ca.p12 -storepass "$STORE_PASS" \
    -validity "$VALIDITY_CERT" \
    -infile output-client.csr -outfile output-client-signed.crt -rfc \
    -ext "KeyUsage:critical=digitalSignature" \
    -ext "ExtendedKeyUsage=clientAuth"

  keytool -importcert -noprompt \
    -alias endpoint-ca \
    -file endpoint-ca.crt \
    -storetype PKCS12 -keystore output-keystore.p12 -storepass "$STORE_PASS"

  keytool -importcert -noprompt \
    -alias forwarder-client \
    -file output-client-signed.crt \
    -storetype PKCS12 -keystore output-keystore.p12 -storepass "$STORE_PASS"

  info "Forwarder client keystore : output-keystore.p12  → deploy to generic-forwarder /certs"
else
  info "Skipping forwarder client keystore (set MTLS=true to create)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
step "Generated files"
ls -lh ./*.p12 ./*.crt 2>/dev/null

echo ""
echo "── mock-endpoint application.properties ────────────────────────────────"
cat <<CONFIG
server.ssl.key-store=file:/certs/endpoint-server.p12
server.ssl.key-store-password=${STORE_PASS}
server.ssl.trust-store=file:/certs/endpoint-truststore.p12
server.ssl.trust-store-password=${STORE_PASS}
server.ssl.client-auth=$([ "$MTLS" == "true" ] && echo "NEED" || echo "NONE")
CONFIG

echo ""
echo "── generic-forwarder application.properties ─────────────────────────────"
cat <<CONFIG
endpoint.ssl.enabled=true
endpoint.ssl.truststore-path=/certs/output-truststore.p12
endpoint.ssl.truststore-password=${STORE_PASS}
$([ "$MTLS" == "true" ] && echo "endpoint.ssl.keystore-path=/certs/output-keystore.p12
endpoint.ssl.keystore-password=${STORE_PASS}")
CONFIG

echo ""
echo "── Validate server cert ─────────────────────────────────────────────────"
echo "  openssl s_client -connect ${ENDPOINT_HOST}:8443 -CAfile endpoint-ssl/endpoint-ca.crt"
