#!/usr/bin/env bash
# =============================================================================
#  setup-kafka-ssl.sh
#
#  Creates all keystores / truststores needed to test a secure
#  Windows desktop → Linux Kafka broker connection (one-way or mTLS).
#
#  Prerequisites:  JDK (keytool), OpenSSL (optional, for validation)
#  Output dir:     ./kafka-ssl/
#
#  Usage:
#    chmod +x setup-kafka-ssl.sh
#    BROKER_HOST=broker1.yourdomain.local BROKER_IP=10.0.0.10 ./setup-kafka-ssl.sh
#    ./setup-kafka-ssl.sh        # uses defaults below
#    MTLS=true ./setup-kafka-ssl.sh   # also creates client keystore
# =============================================================================
set -euo pipefail

# ── Customise these ───────────────────────────────────────────────────────────
BROKER_HOST="${BROKER_HOST:-broker1.yourdomain.local}"
BROKER_IP="${BROKER_IP:-127.0.0.1}"
CLIENT_CN="${CLIENT_CN:-windows-secure-desktop}"
STORE_PASS="${STORE_PASS:-changeit}"
VALIDITY_CA="${VALIDITY_CA:-3650}"    # 10 years
VALIDITY_CERT="${VALIDITY_CERT:-825}" # < 2 years (macOS/iOS limit)
ORG="O=Test, L=London, ST=London, C=GB"
MTLS="${MTLS:-false}"
OUT="kafka-ssl"
# ─────────────────────────────────────────────────────────────────────────────

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
step()  { echo -e "\n${YELLOW}══ $* ══${NC}"; }

mkdir -p "$OUT"
cd "$OUT"

# ── Step 1 : Kafka test CA ────────────────────────────────────────────────────
step "Step 1 — Create Kafka Test CA"

keytool -genkeypair \
  -alias kafka-ca \
  -keyalg RSA -keysize 4096 -validity "$VALIDITY_CA" \
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass "$STORE_PASS" \
  -dname "CN=Kafka Test CA, OU=QA, $ORG"

keytool -exportcert \
  -alias kafka-ca \
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass "$STORE_PASS" \
  -rfc -file kafka-ca.crt

info "CA keystore : kafka-ca.p12"
info "CA cert     : kafka-ca.crt"

# ── Step 2 : Broker keystore ──────────────────────────────────────────────────
step "Step 2 — Create Broker Keystore (${BROKER_HOST})"

keytool -genkeypair \
  -alias broker-1 \
  -keyalg RSA -keysize 2048 -validity "$VALIDITY_CERT" \
  -storetype PKCS12 -keystore broker-1.p12 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=${BROKER_HOST}, OU=Kafka, $ORG"

keytool -certreq \
  -alias broker-1 \
  -storetype PKCS12 -keystore broker-1.p12 -storepass "$STORE_PASS" \
  -file broker-1.csr

# Sign with SAN so Kafka hostname verification passes
keytool -gencert \
  -alias kafka-ca \
  -storetype PKCS12 -keystore kafka-ca.p12 -storepass "$STORE_PASS" \
  -validity "$VALIDITY_CERT" \
  -infile broker-1.csr -outfile broker-1-signed.crt -rfc \
  -ext "SAN=DNS:${BROKER_HOST},IP:${BROKER_IP}" \
  -ext "KeyUsage:critical=digitalSignature,keyEncipherment" \
  -ext "ExtendedKeyUsage=serverAuth"

# Import CA cert then signed cert into broker keystore
keytool -importcert -noprompt \
  -alias kafka-ca \
  -file kafka-ca.crt \
  -storetype PKCS12 -keystore broker-1.p12 -storepass "$STORE_PASS"

keytool -importcert -noprompt \
  -alias broker-1 \
  -file broker-1-signed.crt \
  -storetype PKCS12 -keystore broker-1.p12 -storepass "$STORE_PASS"

info "Broker keystore : broker-1.p12"

# ── Step 3 : Broker truststore ────────────────────────────────────────────────
step "Step 3 — Create Broker Truststore"

keytool -importcert -noprompt \
  -alias kafka-ca \
  -file kafka-ca.crt \
  -storetype PKCS12 -keystore broker-truststore.p12 -storepass "$STORE_PASS"

info "Broker truststore : broker-truststore.p12  (trusts client certs signed by kafka-ca)"

# ── Step 4 : Client truststore ────────────────────────────────────────────────
step "Step 4 — Create Windows Client Truststore"

keytool -importcert -noprompt \
  -alias kafka-ca \
  -file kafka-ca.crt \
  -storetype PKCS12 -keystore client-truststore.p12 -storepass "$STORE_PASS"

info "Client truststore : client-truststore.p12  (trusts broker cert signed by kafka-ca)"

# ── Step 5 : Client keystore (mTLS only) ─────────────────────────────────────
if [[ "$MTLS" == "true" ]]; then
  step "Step 5 — Create Windows Client Keystore (mTLS)"

  keytool -genkeypair \
    -alias desktop-client \
    -keyalg RSA -keysize 2048 -validity "$VALIDITY_CERT" \
    -storetype PKCS12 -keystore client-keystore.p12 \
    -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
    -dname "CN=${CLIENT_CN}, OU=QA, $ORG"

  keytool -certreq \
    -alias desktop-client \
    -storetype PKCS12 -keystore client-keystore.p12 -storepass "$STORE_PASS" \
    -file client.csr

  keytool -gencert \
    -alias kafka-ca \
    -storetype PKCS12 -keystore kafka-ca.p12 -storepass "$STORE_PASS" \
    -validity "$VALIDITY_CERT" \
    -infile client.csr -outfile client-signed.crt -rfc \
    -ext "KeyUsage:critical=digitalSignature" \
    -ext "ExtendedKeyUsage=clientAuth"

  keytool -importcert -noprompt \
    -alias kafka-ca \
    -file kafka-ca.crt \
    -storetype PKCS12 -keystore client-keystore.p12 -storepass "$STORE_PASS"

  keytool -importcert -noprompt \
    -alias desktop-client \
    -file client-signed.crt \
    -storetype PKCS12 -keystore client-keystore.p12 -storepass "$STORE_PASS"

  info "Client keystore : client-keystore.p12"
else
  info "Skipping client keystore (set MTLS=true to create)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
step "Generated files"
ls -lh ./*.p12 ./*.crt 2>/dev/null

echo ""
echo "── Deploy on Linux Kafka broker (server.properties) ──────────────────"
cat <<CONFIG
listeners=SSL://0.0.0.0:9093
ssl.keystore.location=/var/private/ssl/broker-1.p12
ssl.keystore.password=${STORE_PASS}
ssl.key.password=${STORE_PASS}
ssl.truststore.location=/var/private/ssl/broker-truststore.p12
ssl.truststore.password=${STORE_PASS}
ssl.client.auth=$([ "$MTLS" == "true" ] && echo "required" || echo "none")
security.inter.broker.protocol=SSL
CONFIG

echo ""
echo "── Windows client application.properties ──────────────────────────────"
cat <<CONFIG
source.kafka.security-protocol=$([ "$MTLS" == "true" ] && echo "SSL" || echo "SSL")
source.ssl.enabled=true
source.ssl.truststore-path=C:\\\\ssl\\\\client-truststore.p12
source.ssl.truststore-password=${STORE_PASS}
$([ "$MTLS" == "true" ] && echo "source.ssl.keystore-path=C:\\\\ssl\\\\client-keystore.p12
source.ssl.keystore-password=${STORE_PASS}")
CONFIG

echo ""
echo "── Quick validation (run on any host with network access to broker) ────"
echo "  openssl s_client -connect ${BROKER_HOST}:9093 -CAfile kafka-ssl/kafka-ca.crt"
