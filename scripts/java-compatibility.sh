#!/usr/bin/env bash
# =============================================================================
# java-compatibility.sh
# Checks a given Java version against container / cloud readiness criteria.
# Usage: bash java-compatibility.sh <java-major-version>
#        e.g.  bash java-compatibility.sh 8
#              bash java-compatibility.sh 17
# =============================================================================

set -euo pipefail

JAVA_VER="${1:-}"

if [[ -z "$JAVA_VER" ]]; then
    # Auto-detect
    if command -v java &>/dev/null; then
        JAVA_FULL=$(java -version 2>&1 | head -1)
        # Handle "1.8.0_xxx" style and "11.0.x" style
        RAW=$(echo "$JAVA_FULL" | grep -oP '(?<=version ")[^"]+')
        if [[ "$RAW" == 1.* ]]; then
            JAVA_VER=$(echo "$RAW" | cut -d'.' -f2)   # 1.8 → 8
        else
            JAVA_VER=$(echo "$RAW" | cut -d'.' -f1)   # 17.0.x → 17
        fi
        echo "Auto-detected Java version: $JAVA_VER (from: $JAVA_FULL)"
    else
        echo "ERROR: Java not found and no version argument supplied."
        echo "Usage: $0 <java-major-version>"
        exit 1
    fi
fi

# ---------- Version database --------------------------------------------------
declare -A EOL
EOL[5]="2009";  EOL[6]="2013";  EOL[7]="2022";  EOL[8]="2030"
EOL[9]="2018";  EOL[10]="2018"; EOL[11]="2027"; EOL[12]="2019"
EOL[13]="2019"; EOL[14]="2020"; EOL[15]="2021"; EOL[16]="2021"
EOL[17]="2029"; EOL[18]="2022"; EOL[19]="2023"; EOL[20]="2023"
EOL[21]="2031"; EOL[22]="2024"; EOL[23]="2025"

declare -A LTS
LTS[8]=1; LTS[11]=1; LTS[17]=1; LTS[21]=1

declare -A RECOMMENDED_IMAGE
RECOMMENDED_IMAGE[5]="No official image — build from ubuntu:20.04 + manual JDK install"
RECOMMENDED_IMAGE[6]="No official image — build from ubuntu:20.04 + manual JDK install"
RECOMMENDED_IMAGE[7]="azul/zulu-openjdk:7 OR manually install Azul Zulu 7 on ubuntu:20.04"
RECOMMENDED_IMAGE[8]="eclipse-temurin:8-jre-focal"
RECOMMENDED_IMAGE[11]="eclipse-temurin:11-jre-alpine"
RECOMMENDED_IMAGE[17]="eclipse-temurin:17-jre-alpine  (recommended LTS)"
RECOMMENDED_IMAGE[21]="eclipse-temurin:21-jre-alpine  (latest LTS)"

declare -A CONTAINER_SUPPORT
# Container-aware JVM (UseContainerSupport + cgroup v2 awareness)
CONTAINER_SUPPORT[5]="No  — JVM ignores cgroup limits; OOM risk in containers"
CONTAINER_SUPPORT[6]="No  — JVM ignores cgroup limits; OOM risk in containers"
CONTAINER_SUPPORT[7]="No  — JVM ignores cgroup limits; OOM risk in containers"
CONTAINER_SUPPORT[8]="Partial (8u191+) — requires -XX:+UseContainerSupport"
CONTAINER_SUPPORT[9]="No  — STS, use JDK 11"
CONTAINER_SUPPORT[10]="Partial — experimental container support, use JDK 11"
CONTAINER_SUPPORT[11]="Yes — UseContainerSupport enabled by default"
CONTAINER_SUPPORT[17]="Yes — full cgroup v2 support"
CONTAINER_SUPPORT[21]="Yes — full cgroup v2 + Virtual Threads"

declare -A JVM_FLAGS
JVM_FLAGS[7]="-Xms256m -Xmx512m  # Manual heap — no cgroup awareness"
JVM_FLAGS[8]="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
JVM_FLAGS[11]="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
JVM_FLAGS[17]="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
JVM_FLAGS[21]="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# ---------- Helpers -----------------------------------------------------------
RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}✅ PASS${NC}  $*"; }
warn() { echo -e "  ${YELLOW}⚠️  WARN${NC}  $*"; }
fail() { echo -e "  ${RED}❌ FAIL${NC}  $*"; }
info() { echo -e "  ${BLUE}ℹ️  INFO${NC}  $*"; }

SCORE=0; MAX=0
check() {
    MAX=$((MAX+1))
    local status="$1"; shift
    case "$status" in
        pass) SCORE=$((SCORE+1)); pass "$@" ;;
        warn) SCORE=$((SCORE+1)); warn "$@" ;;  # warn still counts as a pass
        fail) fail "$@" ;;
    esac
}

# ---------- Report ------------------------------------------------------------
echo ""
echo "======================================================"
echo " Java Compatibility Report — JDK $JAVA_VER"
echo "======================================================"

# 1. EOL check
echo ""
echo "--- Lifecycle ---"
EOL_YEAR="${EOL[$JAVA_VER]:-unknown}"
CURRENT_YEAR=$(date +%Y)
if [[ "$EOL_YEAR" == "unknown" ]]; then
    check warn "EOL date unknown for JDK $JAVA_VER"
elif [[ "$EOL_YEAR" -lt "$CURRENT_YEAR" ]]; then
    check fail "JDK $JAVA_VER reached EOL in $EOL_YEAR — security patches no longer available"
else
    check pass "JDK $JAVA_VER supported until $EOL_YEAR"
fi

# 2. LTS check
if [[ -n "${LTS[$JAVA_VER]:-}" ]]; then
    check pass "JDK $JAVA_VER is a Long-Term Support (LTS) release"
else
    check warn "JDK $JAVA_VER is NOT an LTS release — short support window"
fi

# 3. Docker image availability
echo ""
echo "--- Container Image ---"
IMAGE="${RECOMMENDED_IMAGE[$JAVA_VER]:-No known official image}"
if echo "$IMAGE" | grep -qi "No official\|manually"; then
    check warn "Recommended image: $IMAGE"
else
    check pass "Recommended base image: $IMAGE"
fi

# 4. Container-aware JVM
echo ""
echo "--- JVM Container Awareness ---"
COMPAT="${CONTAINER_SUPPORT[$JAVA_VER]:-Unknown}"
if echo "$COMPAT" | grep -qi "^Yes"; then
    check pass "Container support: $COMPAT"
elif echo "$COMPAT" | grep -qi "^Partial"; then
    check warn "Container support: $COMPAT"
else
    check fail "Container support: $COMPAT"
fi

# 5. Recommended JVM flags
FLAGS="${JVM_FLAGS[$JAVA_VER]:-See JDK documentation}"
info "Recommended JVM flags for containers:"
info "  $FLAGS"

# 6. Maven compatibility
echo ""
echo "--- Build Tool Compatibility ---"
if [[ "$JAVA_VER" -le 6 ]]; then
    check fail "Maven 3.9+ requires Java 8+ — use Maven 3.2.x for JDK $JAVA_VER"
elif [[ "$JAVA_VER" -le 7 ]]; then
    check warn "Maven 3.8.x supports JDK 7; Maven 3.9+ requires JDK 8+"
else
    check pass "Maven 3.9.x and Gradle 8.x are compatible with JDK $JAVA_VER"
fi

# 7. Migration recommendation
echo ""
echo "--- Migration Recommendation ---"
if [[ "$JAVA_VER" -le 8 ]]; then
    info "Target migration path: JDK $JAVA_VER → JDK 11 → JDK 17"
    if [[ "$JAVA_VER" -le 7 ]]; then
        warn "JDK $JAVA_VER is EOL. Prioritise migration before containerising."
    fi
elif [[ "$JAVA_VER" == "11" ]]; then
    info "Consider migrating to JDK 17 (LTS) — EOL 2029"
elif [[ "$JAVA_VER" == "17" ]]; then
    pass "JDK 17 is an excellent container target. Consider JDK 21 for new projects."
elif [[ "$JAVA_VER" == "21" ]]; then
    pass "JDK 21 is the current LTS — optimal for new containerised workloads."
fi

# ---------- Score -------------------------------------------------------------
echo ""
echo "======================================================"
echo " Score: $SCORE / $MAX checks passed"
if [[ "$SCORE" -eq "$MAX" ]]; then
    echo -e " ${GREEN}Container-ready${NC}"
elif [[ "$SCORE" -ge $((MAX * 7 / 10)) ]]; then
    echo -e " ${YELLOW}Container-ready with caveats (see warnings above)${NC}"
else
    echo -e " ${RED}Not cloud-ready without remediation${NC}"
fi
echo "======================================================"
