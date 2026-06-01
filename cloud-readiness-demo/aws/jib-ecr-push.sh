#!/usr/bin/env bash
# ============================================================
# jib-ecr-push.sh — Push image to ECR using Jib (no Docker needed)
#
# WHY JIB?
#   In enterprise environments where Docker daemon is unavailable
#   on developer workstations or internet-facing systems, Jib
#   builds and pushes OCI-compliant images directly from Maven —
#   no Docker installation required.
#
# Prerequisites:
#   - AWS CLI v2 configured (aws configure)
#   - Java 17 JDK and Maven 3.8+
#   - IAM permissions: ecr:GetAuthorizationToken, ecr:BatchCheckLayerAvailability,
#     ecr:InitiateLayerUpload, ecr:UploadLayerPart, ecr:CompleteLayerUpload,
#     ecr:PutImage
#
# Usage:
#   ./aws/jib-ecr-push.sh [VERSION_TAG]
#
# Example:
#   ./aws/jib-ecr-push.sh 1.0.0
# ============================================================
set -euo pipefail

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
REPO_NAME="cloud-readiness-demo"
VERSION="${1:-latest}"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
FULL_IMAGE="${ECR_REGISTRY}/${REPO_NAME}:${VERSION}"

echo "==> Jib ECR Push (no Docker daemon required)"
echo "    Account  : ${AWS_ACCOUNT_ID}"
echo "    Region   : ${AWS_REGION}"
echo "    Image    : ${FULL_IMAGE}"

# ── Ensure ECR repository exists ───────────────────────────────
echo ""
echo "==> Ensuring ECR repository exists..."
aws ecr describe-repositories --repository-names "${REPO_NAME}" \
    --region "${AWS_REGION}" > /dev/null 2>&1 || \
aws ecr create-repository \
    --repository-name "${REPO_NAME}" \
    --image-scanning-configuration scanOnPush=true \
    --region "${AWS_REGION}" > /dev/null

# ── Get ECR auth token ─────────────────────────────────────────
echo ""
echo "==> Retrieving ECR authentication token..."
ECR_PASSWORD=$(aws ecr get-login-password --region "${AWS_REGION}")

# ── Build and push with Jib ────────────────────────────────────
echo ""
echo "==> Building and pushing image with Jib (no Docker required)..."
cd "$(dirname "$0")/.."

mvn jib:build \
    -Djib.to.image="${FULL_IMAGE}" \
    -Djib.to.auth.username=AWS \
    -Djib.to.auth.password="${ECR_PASSWORD}" \
    --batch-mode \
    -q

echo ""
echo "✅  Image pushed successfully via Jib (no Docker daemon used)."
echo "    Image : ${FULL_IMAGE}"
echo "    Also tagged as: ${ECR_REGISTRY}/${REPO_NAME}:latest"
echo ""
echo "    Next step — deploy to ECS:"
echo "    ./aws/deploy-ecs.sh cloud-readiness-cluster cloud-readiness-svc"
