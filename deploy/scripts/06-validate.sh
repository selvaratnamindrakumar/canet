#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 06-validate.sh  —  Complete validation against deployed app
#
# Run after 05-build-and-deploy.sh.
# Covers the full Thursday demo flow: init → data check → comparisons → report.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SAMPLE_CGI="${SAMPLE_CGI:-234-10-1234-56789}"   # Samsung 4G London
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

pass() { echo -e "${GREEN}  ✓ $1${NC}"; }
fail() { echo -e "${RED}  ✗ $1${NC}"; }
info() { echo -e "${YELLOW}  → $1${NC}"; }

check_json() {
    local desc="$1" url="$2" jq_filter="${3:-.}"
    local result
    result=$(curl -sf "$url" | jq -r "$jq_filter" 2>/dev/null) && \
        pass "$desc: $result" || fail "$desc: FAILED ($url)"
}

echo "=== POC Validation — $BASE_URL ==="
echo ""

# ── 1. Health ─────────────────────────────────────────────────────────────────
echo "─── 1. Health Check ───"
STATUS=$(curl -sf "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNREACHABLE")
[[ "$STATUS" == "UP" ]] && pass "Actuator health: UP" || fail "Actuator health: $STATUS"
echo ""

# ── 2. Initialise ─────────────────────────────────────────────────────────────
echo "─── 2. Initialise backends + seed data ───"
INIT=$(curl -sf -X POST "$BASE_URL/api/admin/init")
echo "$INIT" | jq . 2>/dev/null || echo "$INIT"
[[ "$INIT" == *"initialised"* ]] && pass "Init returned 'initialised'" || fail "Init failed"
echo ""

# ── 3. Data parity check ──────────────────────────────────────────────────────
echo "─── 3. Data parity — all backends should return 5 records ───"
for backend in dynamo aurora; do
    COUNT=$(curl -sf "$BASE_URL/api/admin/data?backend=$backend" | jq length 2>/dev/null || echo 0)
    [[ "$COUNT" -eq 5 ]] && pass "$backend: $COUNT records" || fail "$backend: expected 5, got $COUNT"
done
echo ""

# ── 4. Primary NEO API — CGI lookup ──────────────────────────────────────────
echo "─── 4. Primary NEO API — CGI lookup ($SAMPLE_CGI) ───"
for backend in dynamo aurora; do
    RESP=$(curl -sf "$BASE_URL/api/neo/handset/$SAMPLE_CGI?backend=$backend")
    MFR=$(echo "$RESP" | jq -r '.manufacturer' 2>/dev/null || echo "ERROR")
    TIER=$(echo "$RESP" | jq -r '.networkTier' 2>/dev/null || echo "ERROR")
    LABEL=$(echo "$RESP" | jq -r '.locationLabel' 2>/dev/null || echo "ERROR")
    MS=$(echo "$RESP" | jq -r '.queryTimeMs' 2>/dev/null || echo "?")
    [[ "$MFR" == "Samsung" ]] && \
        pass "$backend: manufacturer=$MFR networkTier=$TIER location='$LABEL' [${MS}ms]" || \
        fail "$backend: unexpected response (manufacturer=$MFR)"
done
echo ""

# ── 5. Three-way comparison ───────────────────────────────────────────────────
echo "─── 5. Three-way comparison ───"
COMPARE=$(curl -sf "$BASE_URL/api/compare/$SAMPLE_CGI")
echo "$COMPARE" | jq '{
    cgi: .cgi,
    dynamoMs: .dynamoDbQueryMs,
    auroraMs: .auroraQueryMs,
    oracleMs: .oracleQueryMs,
    dynamoAuroraMatch: .dynamoAuroraMatch,
    allMatch: .allMatch,
    recommendation: .evaluation.recommendation
}' 2>/dev/null || echo "$COMPARE"
ALL_MATCH=$(echo "$COMPARE" | jq -r '.dynamoAuroraMatch' 2>/dev/null || echo "false")
[[ "$ALL_MATCH" == "true" ]] && pass "DynamoDB ↔ Aurora results match" || fail "Result mismatch"
echo ""

# ── 6. Secondary query patterns ───────────────────────────────────────────────
echo "─── 6. Secondary queries — where DynamoDB scans, Aurora uses indexes ───"
for tech in "4G" "5G"; do
    for backend in dynamo aurora; do
        COUNT=$(curl -sf "$BASE_URL/api/neo/handsets/technology/$tech?backend=$backend" | jq length 2>/dev/null || echo 0)
        info "$backend technology=$tech: $COUNT records"
    done
done
echo ""

for region in "London" "Scotland"; do
    for backend in dynamo aurora; do
        COUNT=$(curl -sf "$BASE_URL/api/neo/handsets/region/$region?backend=$backend" | jq length 2>/dev/null || echo 0)
        info "$backend region=$region: $COUNT records"
    done
done
echo ""

# ── 7. Projected field query ──────────────────────────────────────────────────
echo "─── 7. Field projection (Aurora reads only requested columns) ───"
for backend in dynamo aurora; do
    PROJ=$(curl -sf "$BASE_URL/api/neo/handset/$SAMPLE_CGI/fields?f=manufacturer,model,technology&backend=$backend")
    MODEL=$(echo "$PROJ" | jq -r '.model' 2>/dev/null || echo "ERROR")
    info "$backend projection: model=$MODEL"
done
echo ""

# ── 8. Full evaluation report ─────────────────────────────────────────────────
echo "─── 8. Full evaluation report ───"
REPORT=$(curl -sf "$BASE_URL/api/report" 2>/dev/null || \
         curl -sf "$BASE_URL/api/compare/$SAMPLE_CGI")
echo "$REPORT" | jq '.evaluation.confidenceLevels // .evaluation.recommendation' 2>/dev/null
echo ""

echo "=== Validation complete ==="
echo ""
echo "Thursday demo URLs:"
echo "  Initialise:         POST $BASE_URL/api/admin/init"
echo "  Primary lookup:     GET  $BASE_URL/api/compare/$SAMPLE_CGI"
echo "  NEO DynamoDB:       GET  $BASE_URL/api/neo/handset/$SAMPLE_CGI?backend=dynamo"
echo "  NEO Aurora:         GET  $BASE_URL/api/neo/handset/$SAMPLE_CGI?backend=aurora"
echo "  NEO Oracle:         GET  $BASE_URL/api/neo/handset/$SAMPLE_CGI?backend=oracle"
echo "  Technology filter:  GET  $BASE_URL/api/neo/handsets/technology/4G?backend=dynamo"
echo "  Region+Tech filter: GET  $BASE_URL/api/neo/handsets/region/London/technology/4G?backend=aurora"
