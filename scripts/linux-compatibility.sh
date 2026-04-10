#!/usr/bin/env bash
# =============================================================================
# linux-compatibility.sh
# Assesses host OS and recommends the right container base image.
# Usage: bash linux-compatibility.sh [--target java|dotnet|oracle|generic]
# =============================================================================

set -euo pipefail

TARGET="${2:-generic}"
[[ "${1:-}" == "--target" ]] && TARGET="${2:-generic}"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; NC='\033[0m'
pass() { echo -e "  ${GREEN}✅ PASS${NC}  $*"; }
warn() { echo -e "  ${YELLOW}⚠️  WARN${NC}  $*"; }
fail() { echo -e "  ${RED}❌ FAIL${NC}  $*"; }
info() { echo -e "  ${BLUE}ℹ️  INFO${NC}  $*"; }

echo ""
echo "======================================================"
echo " Linux / OS Compatibility Report"
echo " Target application type: $TARGET"
echo "======================================================"

# ---------- Gather host info --------------------------------------------------
KERNEL=$(uname -r)
ARCH=$(uname -m)
OS_NAME="unknown"; OS_VER="unknown"; OS_ID="unknown"

if [[ -f /etc/os-release ]]; then
    source /etc/os-release
    OS_NAME="${NAME:-unknown}"
    OS_VER="${VERSION_ID:-unknown}"
    OS_ID="${ID:-unknown}"
fi

echo ""
echo "--- Host Environment ---"
info "Kernel:       $KERNEL"
info "Architecture: $ARCH"
info "OS:           $OS_NAME $OS_VER ($OS_ID)"

# ---------- Kernel version checks -------------------------------------------
echo ""
echo "--- Kernel Requirements ---"
KERNEL_MAJOR=$(echo "$KERNEL" | cut -d'.' -f1)
KERNEL_MINOR=$(echo "$KERNEL" | cut -d'.' -f2)

# Docker requires kernel ≥ 3.10 (cgroup v1), ≥ 4.x recommended
if [[ "$KERNEL_MAJOR" -lt 3 ]]; then
    fail "Kernel $KERNEL is too old — Docker requires ≥ 3.10"
elif [[ "$KERNEL_MAJOR" -eq 3 && "$KERNEL_MINOR" -lt 10 ]]; then
    fail "Kernel $KERNEL is too old — Docker requires ≥ 3.10"
elif [[ "$KERNEL_MAJOR" -lt 4 ]]; then
    warn "Kernel $KERNEL meets minimum — upgrade to 4.x+ for better container performance"
elif [[ "$KERNEL_MAJOR" -lt 5 ]]; then
    warn "Kernel $KERNEL: cgroup v2 not available (requires 5.x+) — JVM container support partial"
else
    pass "Kernel $KERNEL — cgroup v2 supported, full container awareness"
fi

# ---------- cgroup version ---------------------------------------------------
echo ""
echo "--- cgroup Support ---"
if [[ -f /sys/fs/cgroup/cgroup.controllers ]]; then
    pass "cgroup v2 (unified hierarchy) — full JVM and .NET container RAM/CPU awareness"
    CGROUP_V2=true
elif [[ -d /sys/fs/cgroup/memory ]]; then
    warn "cgroup v1 — JVM needs -XX:+UseContainerSupport (JDK 8u191+); .NET 5+ supported"
    CGROUP_V2=false
else
    fail "Cannot determine cgroup version — container memory limits may not be respected"
    CGROUP_V2=false
fi

# ---------- Architecture checks ----------------------------------------------
echo ""
echo "--- Architecture ---"
case "$ARCH" in
    x86_64|amd64)
        pass "amd64/x86_64 — all official Java/dotnet/Oracle images available" ;;
    aarch64|arm64)
        pass "arm64 — Eclipse Temurin, .NET 8, Alpine all have arm64 images"
        warn "Oracle Instant Client arm64: only 19.x+ (aarch64) — verify availability" ;;
    armv7l)
        warn "armv7 — limited official images; verify availability per technology" ;;
    *)
        warn "Architecture $ARCH — check base image availability manually" ;;
esac

# ---------- OS-specific checks -----------------------------------------------
echo ""
echo "--- OS-Specific Notes ---"
case "$OS_ID" in
    ubuntu)
        pass "Ubuntu — excellent base image ecosystem (ubuntu:22.04 LTS)"
        if [[ "$OS_VER" == "18.04" ]]; then
            fail "Ubuntu 18.04 is EOL (April 2023) — upgrade to 22.04"
        elif [[ "$OS_VER" == "20.04" ]]; then
            warn "Ubuntu 20.04 — supported to 2025; consider 22.04 for new containers"
        else
            pass "Ubuntu $OS_VER — current and well-supported"
        fi ;;
    debian)
        pass "Debian — lean, stable; debian:12-slim is a great container base"
        if [[ "${OS_VER%%.*}" -lt 11 ]]; then
            warn "Debian $OS_VER — older release; consider debian:12-slim as base image"
        fi ;;
    rhel|centos|ol)
        if [[ "$OS_ID" == "ol" ]]; then
            pass "Oracle Linux — best compatibility for Oracle DB client (oraclelinux:8-slim)"
        else
            info "$OS_NAME $OS_VER — use UBI9-minimal or oraclelinux:8-slim for Oracle workloads"
        fi ;;
    alpine)
        pass "Alpine — minimal footprint (~5 MB); ideal for final container images"
        warn "Alpine uses musl libc — some legacy C extensions may not work (use gcompat layer)" ;;
    *)
        info "OS $OS_NAME — evaluate base image options in versioning/linux-base-images.json" ;;
esac

# ---------- Container runtime on host ----------------------------------------
echo ""
echo "--- Container Runtime on Host ---"
if command -v docker &>/dev/null; then
    DOCKER_VER=$(docker --version 2>/dev/null | grep -oP '[\d.]+' | head -1)
    pass "Docker $DOCKER_VER installed"
    # Check for buildx (multi-arch builds)
    if docker buildx version &>/dev/null 2>&1; then
        pass "docker buildx available — multi-arch (amd64/arm64) builds supported"
    else
        warn "docker buildx not found — single-arch builds only"
    fi
else
    fail "Docker not found — install Docker Engine 24+"
fi

if command -v kubectl &>/dev/null; then
    pass "kubectl $(kubectl version --client --short 2>/dev/null | grep -oP 'v[\d.]+') — Kubernetes deployment ready"
fi

# ---------- Recommended base images per target --------------------------------
echo ""
echo "--- Recommended Base Images for: $TARGET ---"
case "$TARGET" in
    java)
        info "Production runtime:  eclipse-temurin:17-jre-alpine"
        info "Build stage:         eclipse-temurin:17-jdk-alpine"
        info "Legacy Java 8:       eclipse-temurin:8-jre-focal   (Ubuntu 20.04)"
        info "Legacy Java 7:       azul/zulu-openjdk-debian:7    (Debian)"
        ;;
    dotnet)
        info "Production runtime:  mcr.microsoft.com/dotnet/aspnet:8.0-alpine"
        info "Build stage:         mcr.microsoft.com/dotnet/sdk:8.0-alpine"
        info ".NET Framework:      mcr.microsoft.com/dotnet/framework/aspnet:4.8 (Windows only)"
        ;;
    oracle)
        info "Oracle apps:         oraclelinux:8-slim  (best client compat)"
        info "Oracle Instant Cl.:  container-registry.oracle.com/database/instantclient:21"
        info "Java + Oracle:       eclipse-temurin:17-jre-alpine  (JDBC thin, no Instant Client)"
        ;;
    *)
        info "Generic Linux:       ubuntu:22.04  (widest compat)"
        info "Minimal:             alpine:3.19   (~7 MB)"
        info "RHEL-compatible:     registry.access.redhat.com/ubi9-minimal"
        ;;
esac

echo ""
echo "======================================================"
