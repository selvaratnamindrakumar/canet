#!/usr/bin/env bash
# deploy_frontend.sh — Sync frontend static files to S3 and invalidate CloudFront.
#
# Usage (positional args take priority over env vars):
#   bash aws-migration/scripts/deploy_frontend.sh <S3_BUCKET> <CLOUDFRONT_ID>
#
# Or via environment variables:
#   export S3_BUCKET=skills-app-dev-frontend-123456789012
#   export CLOUDFRONT_ID=E1234ABCDEFGH
#   bash aws-migration/scripts/deploy_frontend.sh
#
# Requirements:
#   - AWS CLI v2 configured with appropriate permissions
#   - s3:PutObject, s3:DeleteObject, s3:ListBucket on the bucket
#   - cloudfront:CreateInvalidation on the distribution

set -euo pipefail

# -----------------------------------------------------------------------
# Argument handling — positional args override env vars
# -----------------------------------------------------------------------
S3_BUCKET="${1:-${S3_BUCKET:-}}"
CLOUDFRONT_ID="${2:-${CLOUDFRONT_ID:-}}"

if [ -z "${S3_BUCKET}" ]; then
  echo "ERROR: S3 bucket name is required." >&2
  echo "Usage: $0 <S3_BUCKET> <CLOUDFRONT_ID>" >&2
  echo "       or set S3_BUCKET and CLOUDFRONT_ID env vars." >&2
  exit 1
fi

if [ -z "${CLOUDFRONT_ID}" ]; then
  echo "ERROR: CloudFront distribution ID is required." >&2
  echo "Usage: $0 <S3_BUCKET> <CLOUDFRONT_ID>" >&2
  echo "       or set S3_BUCKET and CLOUDFRONT_ID env vars." >&2
  exit 1
fi

# -----------------------------------------------------------------------
# Paths
# -----------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "${SCRIPT_DIR}/../../frontend" && pwd)"

if [ ! -d "${FRONTEND_DIR}" ]; then
  echo "ERROR: Frontend directory not found at ${FRONTEND_DIR}" >&2
  exit 1
fi

# -----------------------------------------------------------------------
# Sync frontend files to S3
# -----------------------------------------------------------------------
echo "==> Syncing frontend to s3://${S3_BUCKET}/ ..."
aws s3 sync "${FRONTEND_DIR}/" "s3://${S3_BUCKET}/" \
  --delete \
  --exclude "*.md" \
  --cache-control "public, max-age=31536000, immutable" \
  --exclude "index.html" \
  --exclude "*.html"

# HTML files served with short cache (so updates propagate quickly)
aws s3 sync "${FRONTEND_DIR}/" "s3://${S3_BUCKET}/" \
  --delete \
  --exclude "*" \
  --include "*.html" \
  --cache-control "public, max-age=300, must-revalidate"

echo "==> Sync complete."

# -----------------------------------------------------------------------
# Invalidate CloudFront cache
# -----------------------------------------------------------------------
echo "==> Creating CloudFront invalidation for distribution ${CLOUDFRONT_ID} ..."
INVALIDATION_OUTPUT=$(
  aws cloudfront create-invalidation \
    --distribution-id "${CLOUDFRONT_ID}" \
    --paths "/*"
)

INVALIDATION_ID=$(echo "${INVALIDATION_OUTPUT}" | python3 -c "import sys,json; print(json.load(sys.stdin)['Invalidation']['Id'])")
echo "==> Invalidation created: ${INVALIDATION_ID}"

# -----------------------------------------------------------------------
# Print the CloudFront URL
# -----------------------------------------------------------------------
CLOUDFRONT_DOMAIN=$(
  aws cloudfront get-distribution \
    --id "${CLOUDFRONT_ID}" \
    --query "Distribution.DomainName" \
    --output text
)

echo ""
echo "Frontend deployed successfully."
echo "CloudFront URL: https://${CLOUDFRONT_DOMAIN}"
echo ""
echo "Note: Cache invalidation may take 30–60 seconds to propagate globally."
