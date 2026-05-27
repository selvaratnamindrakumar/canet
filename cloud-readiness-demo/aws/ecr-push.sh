#!/usr/bin/env bash
# ============================================================
# ecr-push.sh — Build and push Docker image to Amazon ECR
#
# Prerequisites:
#   - AWS CLI v2 configured with sufficient IAM permissions
#   - Docker daemon running
#   - ECR repository created (see create-ecr-repo.sh)
#
# Usage:
#   ./aws/ecr-push.sh [VERSION_TAG]
#
# Example:
#   ./aws/ecr-push.sh 1.0.0
# ============================================================
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
REPO_NAME="cloud-readiness-demo"
VERSION="${1:-latest}"
IMAGE_TAG="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${REPO_NAME}:${VERSION}"
IMAGE_TAG_LATEST="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${REPO_NAME}:latest"

echo "==> AWS Account : ${AWS_ACCOUNT_ID}"
echo "==> Region      : ${AWS_REGION}"
echo "==> Repository  : ${REPO_NAME}"
echo "==> Image tag   : ${IMAGE_TAG}"

# ── Authenticate Docker to ECR ─────────────────────────────────
echo ""
echo "==> Authenticating Docker to ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin \
    "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# ── Build Docker image ─────────────────────────────────────────
echo ""
echo "==> Building Docker image..."
docker build \
  --build-arg APP_VERSION="${VERSION}" \
  --tag "${IMAGE_TAG}" \
  --tag "${IMAGE_TAG_LATEST}" \
  .

# ── Push to ECR ────────────────────────────────────────────────
echo ""
echo "==> Pushing to ECR..."
docker push "${IMAGE_TAG}"
docker push "${IMAGE_TAG_LATEST}"

echo ""
echo "✅  Image pushed successfully: ${IMAGE_TAG}"
echo "    Pull command: docker pull ${IMAGE_TAG}"
