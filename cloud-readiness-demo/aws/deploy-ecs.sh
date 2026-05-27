#!/usr/bin/env bash
# ============================================================
# deploy-ecs.sh — Register ECS task definition and update service
#
# This script:
#   1. Substitutes ACCOUNT_ID and REGION placeholders in the
#      task definition template
#   2. Registers the new task definition revision with ECS
#   3. Updates the ECS service to use the new revision
#
# Prerequisites:
#   - AWS CLI v2 configured
#   - ECS cluster and service already created
#   - ECR image already pushed (run ecr-push.sh first)
#
# Usage:
#   ./aws/deploy-ecs.sh [CLUSTER_NAME] [SERVICE_NAME]
# ============================================================
set -euo pipefail

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
CLUSTER="${1:-cloud-readiness-cluster}"
SERVICE="${2:-cloud-readiness-svc}"
TASK_DEF_FILE="./aws/ecs-task-definition.json"

echo "==> Account  : ${AWS_ACCOUNT_ID}"
echo "==> Region   : ${AWS_REGION}"
echo "==> Cluster  : ${CLUSTER}"
echo "==> Service  : ${SERVICE}"

# ── Replace placeholders ───────────────────────────────────────
TMP_FILE=$(mktemp)
sed \
  -e "s/ACCOUNT_ID/${AWS_ACCOUNT_ID}/g" \
  -e "s/REGION/${AWS_REGION}/g" \
  "${TASK_DEF_FILE}" > "${TMP_FILE}"

# ── Register new task definition revision ─────────────────────
echo ""
echo "==> Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json "file://${TMP_FILE}" \
  --query "taskDefinition.taskDefinitionArn" \
  --output text)

echo "    New revision: ${TASK_DEF_ARN}"

# ── Update ECS service ─────────────────────────────────────────
echo ""
echo "==> Updating ECS service..."
aws ecs update-service \
  --cluster "${CLUSTER}" \
  --service "${SERVICE}" \
  --task-definition "${TASK_DEF_ARN}" \
  --force-new-deployment \
  --output json | jq '.service | {serviceName, status, desiredCount, runningCount}'

echo ""
echo "✅  Deployment initiated."
echo "    Monitor progress:"
echo "    aws ecs wait services-stable --cluster ${CLUSTER} --services ${SERVICE}"

rm -f "${TMP_FILE}"
