#!/usr/bin/env bash
# Deploys the sandbox file-ingestion pipeline stack.
#
# Usage:
#   ./deploy.sh [stack-name] [environment-name] [aws-region]
#
# Requires: AWS CLI configured with credentials for the target sandbox account.

set -euo pipefail

STACK_NAME="${1:-file-ingestion-sandbox}"
ENVIRONMENT_NAME="${2:-sandbox}"
REGION="${3:-${AWS_REGION:-us-east-1}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

aws cloudformation deploy \
  --template-file "${SCRIPT_DIR}/pipeline.yaml" \
  --stack-name "${STACK_NAME}" \
  --region "${REGION}" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "EnvironmentName=${ENVIRONMENT_NAME}"

aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --region "${REGION}" \
  --query 'Stacks[0].Outputs' \
  --output table
