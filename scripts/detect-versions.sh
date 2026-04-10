#!/usr/bin/env bash
# =============================================================================
# detect-versions.sh
# Detects installed runtime versions on the current host.
# Covers: Java, .NET, Oracle client, Node.js, Python, OS/kernel, Docker.
# Usage: bash detect-versions.sh [--json]
# =============================================================================

set -euo pipefail

OUTPUT_JSON=false
[[ "${1:-}" == "--json" ]] && OUTPUT_JSON=true

# ---------- helpers -----------------------------------------------------------
RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
info()    { $OUTPUT_JSON || echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { $OUTPUT_JSON || echo -e "${YELLOW}[WARN]${NC}  $*"; }
missing() { $OUTPUT_JSON || echo -e "${RED}[MISS]${NC}  $*"; }

declare -A VERSIONS   # key=technology, value=version string

detect() {
    local key="$1"; shift
    if DETECTED=$("$@" 2>/dev/null | head -1); then
        VERSIONS["$key"]="$DETECTED"
        info "$key: $DETECTED"
    else
        VERSIONS["$key"]="not found"
        missing "$key: not found"
    fi
}

# ---------- OS / Kernel -------------------------------------------------------
info "=== Operating System ==="
VERSIONS["os_name"]=$(uname -s 2>/dev/null || echo "unknown")
VERSIONS["os_version"]=$(uname -r 2>/dev/null || echo "unknown")
VERSIONS["arch"]=$(uname -m 2>/dev/null || echo "unknown")

if [[ -f /etc/os-release ]]; then
    source /etc/os-release
    VERSIONS["distro"]="${NAME:-unknown} ${VERSION_ID:-}"
    info "Distro: ${VERSIONS[distro]}"
elif [[ -f /etc/redhat-release ]]; then
    VERSIONS["distro"]=$(cat /etc/redhat-release)
    info "Distro: ${VERSIONS[distro]}"
else
    VERSIONS["distro"]="unknown"
fi
info "Kernel: ${VERSIONS[os_version]} (${VERSIONS[arch]})"

# ---------- Java --------------------------------------------------------------
info ""
info "=== Java ==="
if command -v java &>/dev/null; then
    JAVA_FULL=$(java -version 2>&1 | head -1)
    JAVA_VER=$(echo "$JAVA_FULL" | grep -oP '(?<=version ")[\d._]+' || echo "unknown")
    VERSIONS["java"]="$JAVA_VER"
    info "java: $JAVA_VER ($JAVA_FULL)"

    # Detect JDK vs JRE
    if command -v javac &>/dev/null; then
        VERSIONS["javac"]=$(javac -version 2>&1 | awk '{print $2}')
        info "javac: ${VERSIONS[javac]} (JDK present)"
    else
        VERSIONS["javac"]="not found (JRE only)"
        warn "javac not found — JRE only, no compilation"
    fi
else
    VERSIONS["java"]="not found"
    missing "java: not installed"
fi

# Maven
if command -v mvn &>/dev/null; then
    VERSIONS["maven"]=$(mvn --version 2>/dev/null | head -1 | grep -oP 'Apache Maven \K[\d.]+')
    info "maven: ${VERSIONS[maven]}"
else
    VERSIONS["maven"]="not found"
    missing "maven: not installed"
fi

# Gradle
if command -v gradle &>/dev/null; then
    VERSIONS["gradle"]=$(gradle --version 2>/dev/null | grep "^Gradle" | awk '{print $2}')
    info "gradle: ${VERSIONS[gradle]}"
else
    VERSIONS["gradle"]="not found"
fi

# ---------- .NET --------------------------------------------------------------
info ""
info "=== .NET ==="
if command -v dotnet &>/dev/null; then
    VERSIONS["dotnet"]=$(dotnet --version 2>/dev/null || echo "unknown")
    info "dotnet SDK: ${VERSIONS[dotnet]}"
    # List all installed runtimes
    if dotnet --list-runtimes &>/dev/null; then
        RUNTIMES=$(dotnet --list-runtimes 2>/dev/null | tr '\n' ';')
        VERSIONS["dotnet_runtimes"]="$RUNTIMES"
        info "dotnet runtimes: $RUNTIMES"
    fi
else
    VERSIONS["dotnet"]="not found"
    missing "dotnet: not installed"
fi

# Mono (.NET Framework on Linux)
if command -v mono &>/dev/null; then
    VERSIONS["mono"]=$(mono --version 2>/dev/null | head -1 | grep -oP 'version \K[\d.]+')
    info "mono (.NET Fx compat): ${VERSIONS[mono]}"
else
    VERSIONS["mono"]="not found"
fi

# ---------- Oracle ------------------------------------------------------------
info ""
info "=== Oracle ==="

# Oracle Instant Client
OCI_DIRS=(/usr/lib/oracle /opt/oracle /usr/lib/instantclient*)
VERSIONS["oracle_instant_client"]="not found"
for d in "${OCI_DIRS[@]}"; do
    if [[ -d "$d" ]]; then
        OCI_VER=$(find "$d" -name "libclntsh.so*" 2>/dev/null | head -1 | grep -oP '\d+\.\d+' || echo "")
        if [[ -n "$OCI_VER" ]]; then
            VERSIONS["oracle_instant_client"]="$OCI_VER (found in $d)"
            info "Oracle Instant Client: ${VERSIONS[oracle_instant_client]}"
            break
        fi
    fi
done
[[ "${VERSIONS[oracle_instant_client]}" == "not found" ]] && missing "Oracle Instant Client: not found"

# SQLPlus
if command -v sqlplus &>/dev/null; then
    VERSIONS["sqlplus"]=$(sqlplus -V 2>/dev/null | grep "Release" | head -1 | awk '{print $3}')
    info "sqlplus: ${VERSIONS[sqlplus]}"
else
    VERSIONS["sqlplus"]="not found"
fi

# JDBC jars on classpath
JDBC_SEARCH_DIRS=(/opt /usr/local/lib /home "$HOME")
VERSIONS["ojdbc"]="not found"
for d in "${JDBC_SEARCH_DIRS[@]}"; do
    OJDBC=$(find "$d" -maxdepth 6 -name "ojdbc*.jar" 2>/dev/null | head -1)
    if [[ -n "$OJDBC" ]]; then
        VERSIONS["ojdbc"]="$OJDBC"
        info "ojdbc jar: $OJDBC"
        break
    fi
done

# ---------- Node.js -----------------------------------------------------------
info ""
info "=== Node.js / npm ==="
if command -v node &>/dev/null; then
    VERSIONS["nodejs"]=$(node --version 2>/dev/null)
    VERSIONS["npm"]=$(npm --version 2>/dev/null || echo "not found")
    info "node: ${VERSIONS[nodejs]}, npm: ${VERSIONS[npm]}"
else
    VERSIONS["nodejs"]="not found"
fi

# ---------- Python ------------------------------------------------------------
info ""
info "=== Python ==="
for PY in python3 python python2; do
    if command -v "$PY" &>/dev/null; then
        VERSIONS["python"]=$("$PY" --version 2>&1 | awk '{print $2}')
        info "$PY: ${VERSIONS[python]}"
        break
    fi
done
VERSIONS["python"]="${VERSIONS[python]:-not found}"

# ---------- Docker / container runtime ----------------------------------------
info ""
info "=== Container Runtime ==="
if command -v docker &>/dev/null; then
    VERSIONS["docker"]=$(docker --version 2>/dev/null | grep -oP 'Docker version \K[\d.]+')
    info "docker: ${VERSIONS[docker]}"
else
    VERSIONS["docker"]="not found"
    missing "docker: not installed"
fi

if command -v docker-compose &>/dev/null; then
    VERSIONS["docker_compose"]=$(docker-compose --version 2>/dev/null | grep -oP '[\d.]+' | head -1)
    info "docker-compose: ${VERSIONS[docker_compose]}"
elif docker compose version &>/dev/null 2>&1; then
    VERSIONS["docker_compose"]=$(docker compose version 2>/dev/null | grep -oP '[\d.]+' | head -1)
    info "docker compose (plugin): ${VERSIONS[docker_compose]}"
fi

if command -v kubectl &>/dev/null; then
    VERSIONS["kubectl"]=$(kubectl version --client --short 2>/dev/null | grep -oP 'v[\d.]+' | head -1)
    info "kubectl: ${VERSIONS[kubectl]}"
fi

# ---------- Output ------------------------------------------------------------
if $OUTPUT_JSON; then
    echo "{"
    first=true
    for key in "${!VERSIONS[@]}"; do
        $first && first=false || echo ","
        printf '  "%s": "%s"' "$key" "${VERSIONS[$key]}"
    done
    echo ""
    echo "}"
else
    echo ""
    info "=== Summary ==="
    printf "%-28s %s\n" "Technology" "Version"
    printf "%-28s %s\n" "----------" "-------"
    for key in os_name distro arch java javac maven gradle dotnet mono \
                oracle_instant_client sqlplus ojdbc nodejs npm python \
                docker docker_compose kubectl; do
        printf "%-28s %s\n" "$key" "${VERSIONS[$key]:-not found}"
    done
fi
