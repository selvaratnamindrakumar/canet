#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# 01-setup-env.sh  —  Verify AWS sandbox access and set shared environment
# Run this first. Source it to export variables for subsequent scripts.
# Usage:  source deploy/scripts/01-setup-env.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Configuration — edit these for your sandbox ───────────────────────────────
export AWS_REGION="${AWS_REGION:-us-east-1}"
export AWS_PROFILE="${AWS_PROFILE:-default}"
export ENVIRONMENT="${ENVIRONMENT:-poc}"

export DYNAMO_TABLE="handset-reference-${ENVIRONMENT}"
export AURORA_DB_CLUSTER="neo-poc-aurora-${ENVIRONMENT}"
export AURORA_DB_NAME="pocdb"
export AURORA_USER="${AURORA_USER:-poc}"
export ORACLE_DB_IDENTIFIER="neo-poc-oracle-${ENVIRONMENT}"
export ORACLE_SERVICE="ORCL"
export ORACLE_USER="${ORACLE_USER:-poc}"

export APP_PORT=8080

# ─────────────────────────────────────────────────────────────────────────────

echo "=== Accumulo Migration POC — Environment Setup ==="
echo "Region:      $AWS_REGION"
echo "Profile:     $AWS_PROFILE"
echo "Environment: $ENVIRONMENT"
echo ""

# Verify AWS credentials
echo "─── Verifying AWS credentials ───"
IDENTITY=$(aws sts get-caller-identity --output json 2>&1) || {
    echo "ERROR: AWS credentials not configured."
    echo "Run: aws configure  OR  export AWS_PROFILE=<your-sandbox-profile>"
    return 1 2>/dev/null || exit 1
}
echo "$IDENTITY" | python3 -m json.tool 2>/dev/null || echo "$IDENTITY"
echo ""

# Verify required tools
echo "─── Checking required tools ───"
for tool in java mvn aws jq; do
    if command -v "$tool" &>/dev/null; then
        echo "  ✓ $tool ($(command -v $tool))"
    else
        echo "  ✗ $tool — NOT FOUND"
        [[ "$tool" == "jq" ]] && echo "    Install: brew install jq | apt install jq"
        [[ "$tool" == "java" ]] && echo "    Install: Java 17+ required"
    fi
done
echo ""

echo "=== Environment ready. Run subsequent scripts in order ==="
echo "  02-create-dynamodb.sh"
echo "  03-create-rds-aurora.sh"
echo "  04-create-rds-oracle.sh  (optional)"
echo "  05-build-and-deploy.sh"
echo "  06-validate.sh"
