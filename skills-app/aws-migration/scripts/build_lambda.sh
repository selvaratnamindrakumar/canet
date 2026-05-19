#!/usr/bin/env bash
# build_lambda.sh — Build the Lambda deployment ZIP for the Employee Skills API.
#
# Usage:
#   bash aws-migration/scripts/build_lambda.sh
#
# Output:
#   aws-migration/lambda_package.zip
#
# Requirements:
#   - Python 3.11+ with pip
#   - zip utility

set -euo pipefail

# -----------------------------------------------------------------------
# Paths (relative to repository root; script resolves them absolutely)
# -----------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MIGRATION_DIR}/.." && pwd)"

BACKEND_APP_DIR="${REPO_ROOT}/backend/app"
LAMBDA_DIR="${MIGRATION_DIR}/lambda"
BUILD_DIR="${MIGRATION_DIR}/build"
OUTPUT_ZIP="${MIGRATION_DIR}/lambda_package.zip"

# -----------------------------------------------------------------------
# Clean up any previous build artefacts
# -----------------------------------------------------------------------
echo "==> Cleaning previous build..."
rm -rf "${BUILD_DIR}"
rm -f "${OUTPUT_ZIP}"
mkdir -p "${BUILD_DIR}"

# -----------------------------------------------------------------------
# Install Python dependencies into the build directory
# -----------------------------------------------------------------------
echo "==> Installing Python dependencies from lambda/requirements.txt..."
pip install \
  --requirement "${LAMBDA_DIR}/requirements.txt" \
  --target "${BUILD_DIR}" \
  --quiet \
  --upgrade

# -----------------------------------------------------------------------
# Copy application source
# -----------------------------------------------------------------------
echo "==> Copying FastAPI application (backend/app/)..."
cp -r "${BACKEND_APP_DIR}" "${BUILD_DIR}/app"

echo "==> Copying Lambda handler (lambda/handler.py)..."
cp "${LAMBDA_DIR}/handler.py" "${BUILD_DIR}/handler.py"

# -----------------------------------------------------------------------
# Create the ZIP archive
# -----------------------------------------------------------------------
echo "==> Creating ZIP archive..."
(
  cd "${BUILD_DIR}"
  zip -r "${OUTPUT_ZIP}" . \
    --exclude "*.pyc" \
    --exclude "__pycache__/*" \
    --exclude "*.dist-info/*" \
    --exclude "*.egg-info/*"
)

# -----------------------------------------------------------------------
# Report
# -----------------------------------------------------------------------
ZIP_SIZE=$(du -sh "${OUTPUT_ZIP}" | cut -f1)
echo ""
echo "Lambda package created: ${OUTPUT_ZIP}"
echo "File size: ${ZIP_SIZE}"
echo ""
echo "Next steps:"
echo "  1. Run 'terraform apply' in aws-migration/terraform/ to upload the package."
echo "  2. Or update an existing function:"
echo "     LAMBDA_NAME=\$(cd aws-migration/terraform && terraform output -raw lambda_function_name)"
echo "     aws lambda update-function-code \\"
echo "       --function-name \"\$LAMBDA_NAME\" \\"
echo "       --zip-file fileb://${OUTPUT_ZIP}"
