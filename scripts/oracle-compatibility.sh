#!/usr/bin/env bash
# =============================================================================
# oracle-compatibility.sh
# Checks Oracle database/client version compatibility for containerised apps.
# Usage: bash oracle-compatibility.sh [--server <ver>] [--client <ver>]
#        bash oracle-compatibility.sh --server 19c --client 12.2
#        bash oracle-compatibility.sh --server 23ai
# =============================================================================

set -euo pipefail

SERVER_VER=""
CLIENT_VER=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --server) SERVER_VER="$2"; shift 2 ;;
        --client) CLIENT_VER="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# Auto-detect client if not specified
if [[ -z "$CLIENT_VER" ]]; then
    if command -v sqlplus &>/dev/null; then
        CLIENT_VER=$(sqlplus -V 2>/dev/null | grep "Release" | grep -oP '\d+\.\d+' | head -1)
        echo "Auto-detected SQL*Plus / Instant Client version: $CLIENT_VER"
    fi
fi

if [[ -z "$SERVER_VER" && -z "$CLIENT_VER" ]]; then
    echo "Usage: $0 [--server <ver>] [--client <ver>]"
    echo "  e.g. $0 --server 19c --client 12.2"
    echo "       $0 --server 23ai"
    exit 1
fi

# ---------- Helpers -----------------------------------------------------------
RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}✅ PASS${NC}  $*"; }
warn() { echo -e "  ${YELLOW}⚠️  WARN${NC}  $*"; }
fail() { echo -e "  ${RED}❌ FAIL${NC}  $*"; }
info() { echo -e "  ${BLUE}ℹ️  INFO${NC}  $*"; }

# Normalise version (strip 'c', 'g', 'ai', 'R1', 'R2', whitespace)
norm_ver() {
    echo "$1" | sed 's/[cCgGaA].*//;s/R[0-9]//g;s/ //g'
}

SERVER_NUM=$(norm_ver "${SERVER_VER:-0}")
CLIENT_NUM=$(norm_ver "${CLIENT_VER:-0}")

# ---------- Database version lookup -------------------------------------------
declare -A DB_EOL DB_LTS DB_JDBC DB_NOTES
DB_EOL["11"]="2013";  DB_LTS["11"]="";    DB_JDBC["11"]="ojdbc6.jar";  DB_NOTES["11"]="EOL — critical security risk"
DB_EOL["12"]="2022";  DB_LTS["12"]="";    DB_JDBC["12"]="ojdbc8.jar";  DB_NOTES["12"]="EOL — plan migration to 19c+"
DB_EOL["18"]="2021";  DB_LTS["18"]="";    DB_JDBC["18"]="ojdbc8.jar";  DB_NOTES["18"]="EOL — short-term release"
DB_EOL["19"]="2027";  DB_LTS["19"]="LTS"; DB_JDBC["19"]="ojdbc8.jar";  DB_NOTES["19"]="Long-term support — recommended"
DB_EOL["21"]="2024";  DB_LTS["21"]="";    DB_JDBC["21"]="ojdbc11.jar"; DB_NOTES["21"]="Innovation release — use 23ai"
DB_EOL["23"]="2030+"; DB_LTS["23"]="LTS"; DB_JDBC["23"]="ojdbc11.jar"; DB_NOTES["23"]="Latest LTS — Oracle 23ai"

echo ""
echo "======================================================"
echo " Oracle Compatibility Report"
[[ -n "$SERVER_VER" ]] && echo " Server:  Oracle DB $SERVER_VER"
[[ -n "$CLIENT_VER" ]] && echo " Client:  Instant Client / JDBC $CLIENT_VER"
echo "======================================================"

# ---------- Server checks -----------------------------------------------------
if [[ -n "$SERVER_VER" && "$SERVER_NUM" != "0" ]]; then
    echo ""
    echo "--- Database Server ---"
    SMAJ="${SERVER_NUM%%.*}"
    EOL="${DB_EOL[$SMAJ]:-unknown}"
    NOTES="${DB_NOTES[$SMAJ]:-See Oracle support policy}"
    JDBC="${DB_JDBC[$SMAJ]:-ojdbc8.jar}"
    LTS="${DB_LTS[$SMAJ]:-}"

    CURRENT_YEAR=$(date +%Y)
    if [[ "$EOL" == "unknown" ]]; then
        warn "Oracle $SERVER_VER — EOL date unknown"
    elif [[ "$EOL" =~ ^[0-9]{4}$ && "$EOL" -lt "$CURRENT_YEAR" ]]; then
        fail "Oracle $SERVER_VER EOL $EOL — $NOTES"
    else
        pass "Oracle $SERVER_VER — EOL $EOL. $NOTES"
    fi
    [[ -n "$LTS" ]] && pass "Oracle $SERVER_VER is a Long-Term Support release"
    info "Recommended JDBC driver: $JDBC (download from Maven Central: com.oracle.database.jdbc)"
fi

# ---------- Client checks -----------------------------------------------------
if [[ -n "$CLIENT_VER" && "$CLIENT_NUM" != "0" ]]; then
    echo ""
    echo "--- Instant Client / JDBC ---"
    CMAJ="${CLIENT_NUM%%.*}"

    case "$CMAJ" in
        11) warn "Oracle Instant Client 11.x is EOL — upgrade to 19+ or 21+" ;;
        12) warn "Oracle Instant Client 12.x is EOL — upgrade to 19+ or 21+" ;;
        18) warn "Oracle Instant Client 18.x is EOL" ;;
        19) pass "Oracle Instant Client 19.x — current LTS" ;;
        21) pass "Oracle Instant Client 21.x — innovation release" ;;
        23) pass "Oracle Instant Client 23.x — latest" ;;
        *)  warn "Oracle Instant Client $CLIENT_VER — verify compatibility" ;;
    esac

    # Client/server interoperability rule: client >= server - 1 major
    if [[ -n "$SERVER_VER" && "$SERVER_NUM" != "0" ]]; then
        echo ""
        echo "--- Client/Server Interoperability ---"
        SMAJ="${SERVER_NUM%%.*}"
        CMAJ_N="${CMAJ:-0}"
        DIFF=$(( SMAJ - CMAJ_N ))
        if [[ "$CMAJ_N" -ge "$SMAJ" ]]; then
            pass "Client ($CLIENT_VER) >= Server ($SERVER_VER) — full compatibility"
        elif [[ "$DIFF" -le 1 ]]; then
            pass "Client ($CLIENT_VER) is one major version behind Server ($SERVER_VER) — supported"
        elif [[ "$DIFF" -le 2 ]]; then
            warn "Client ($CLIENT_VER) is $DIFF majors behind Server ($SERVER_VER) — limited testing"
        else
            fail "Client ($CLIENT_VER) is $DIFF majors behind Server ($SERVER_VER) — unsupported combination"
        fi
    fi
fi

# ---------- Container-specific guidance ---------------------------------------
echo ""
echo "--- Container Best Practices ---"

pass "Use JDBC thin driver (pure Java) — no Instant Client needed in container"
info "  Maven: com.oracle.database.jdbc:ojdbc11:23.x.x.x"
info "  Gradle: implementation 'com.oracle.database.jdbc:ojdbc11:23.x.x.x'"

pass "Oracle.ManagedDataAccess.Core (.NET) — pure managed, Linux-compatible"
info "  NuGet: Oracle.ManagedDataAccess.Core"

warn "Instant Client in Docker: download requires Oracle account; consider multi-stage build"
info "  OR use: container-registry.oracle.com/database/instantclient:latest"
info "  OR use Oracle Linux base: oraclelinux:8-slim and install via yum"

echo ""
echo "--- Wallet / TNS Configuration ---"
info "Mount tnsnames.ora, sqlnet.ora, and wallet (ewallet.p12) via:"
info "  Kubernetes: Secret + volumeMount → /etc/oracle/wallet"
info "  Docker:     -v /host/wallet:/etc/oracle/wallet:ro"
info "Environment:  TNS_ADMIN=/etc/oracle/wallet"
info "              ORACLE_HOME=/usr/lib/oracle/21/client64"

echo ""
echo "--- Recommended Dockerfile snippet (JDBC thin, no Instant Client) ---"
cat <<'EOF'
  # Java app connecting to Oracle — no Instant Client needed
  FROM eclipse-temurin:17-jre-alpine
  WORKDIR /app
  COPY target/myapp.jar app.jar
  # ojdbc driver already bundled in fat-jar (Maven shade / Spring Boot)
  ENV ORACLE_JDBC_URL="jdbc:oracle:thin:@//oracle-host:1521/ORCL"
  RUN addgroup -S appgroup && adduser -S appuser -G appgroup
  USER appuser
  ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
EOF

echo ""
echo "======================================================"
