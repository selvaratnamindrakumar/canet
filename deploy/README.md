# NEO DB POC — AWS Sandbox Deployment Guide

Three-way database evaluation: **DynamoDB vs Aurora PostgreSQL vs RDS Oracle SE2**

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17 (Corretto or OpenJDK) |
| Maven | 3.9+ |
| AWS CLI | 2.x, configured with sandbox credentials |
| jq | 1.6+ |

Verify AWS access:
```bash
aws sts get-caller-identity
```

---

## Quick Start — Thursday Demo (Local Mode)

This is the fastest path: build the JAR locally and connect it directly to AWS-managed databases.

### Step 1 — Create DynamoDB table

```bash
bash deploy/scripts/02-create-dynamodb.sh
```

### Step 2 — Create Aurora PostgreSQL (5–10 min)

```bash
export AURORA_PASSWORD="choose-a-strong-password"
export DB_SUBNET_GROUP="your-subnet-group"
export VPC_SG_ID="sg-xxxxxxxx"
bash deploy/scripts/03-create-rds-aurora.sh
```

### Step 3 — (Optional) Create RDS Oracle SE2

> **Cost warning**: ~$5–7/day. Delete after the demo.

```bash
export ORACLE_PASSWORD="choose-a-strong-password"
bash deploy/scripts/04-create-rds-oracle.sh
```

### Step 4 — Build and run locally

```bash
export AURORA_ENDPOINT="<from step 2 output>"
export AURORA_USER="pocadmin"
export AURORA_PASSWORD="<your password>"

# DynamoDB + Aurora only:
bash deploy/scripts/05-build-and-deploy.sh

# With Oracle (add oracle profile):
export SPRING_PROFILES="aws,oracle"
export ORACLE_ENDPOINT="<from step 3 output>"
export ORACLE_USER="pocadmin"
export ORACLE_SERVICE="POCDB"
bash deploy/scripts/05-build-and-deploy.sh
```

App starts on **http://localhost:8080**

### Step 5 — Validate

```bash
bash deploy/scripts/06-validate.sh
```

---

## Elastic Beanstalk Deployment (Mode B)

For a long-running hosted demo:

```bash
export DEPLOY_MODE=eb
export AURORA_ENDPOINT="..."
export AURORA_USER="pocadmin"
export AURORA_PASSWORD="..."
bash deploy/scripts/05-build-and-deploy.sh
```

The script creates or updates the `neo-db-poc-sandbox` EB environment running on Corretto 17.

---

## CloudFormation (Full Infrastructure-as-Code)

Provision everything in one command:

```bash
aws cloudformation deploy \
  --template-file deploy/cloudformation/poc-infrastructure.yaml \
  --stack-name neo-db-poc \
  --parameter-overrides \
      AuroraPassword="your-aurora-password" \
      CreateOracle="false" \
  --capabilities CAPABILITY_NAMED_IAM \
  --region eu-west-2
```

Add `CreateOracle=true OraclePassword=...` to include the Oracle instance.

---

## Thursday Demo Flow

| Step | Endpoint | What it shows |
|------|----------|---------------|
| Seed data | `POST /api/admin/init` | Both/all backends initialised |
| Data check | `GET /api/admin/data?backend=dynamo` | 5 records in DynamoDB |
| Data check | `GET /api/admin/data?backend=aurora` | 5 records in Aurora |
| CGI lookup | `GET /api/neo/handset/234-10-1234-56789?backend=dynamo` | DynamoDB response + query time |
| CGI lookup | `GET /api/neo/handset/234-10-1234-56789?backend=aurora` | Aurora response + query time |
| 3-way compare | `GET /api/compare/234-10-1234-56789` | Side-by-side timing + match check |
| Technology filter | `GET /api/neo/handsets/technology/4G?backend=dynamo` | Full scan in DynamoDB |
| Technology filter | `GET /api/neo/handsets/technology/4G?backend=aurora` | Index seek in Aurora |
| Region filter | `GET /api/neo/handsets/region/London?backend=aurora` | Aurora GIN/B-tree index |
| Field projection | `GET /api/neo/handset/234-10-1234-56789/fields?f=manufacturer,model` | Aurora reads only requested columns |
| Full report | `GET /api/report` | Evaluation matrix + recommendation |

---

## Key Talking Points

### DynamoDB
- Sub-millisecond CGI lookups (PK = CGI, document model)
- Queries by technology/region require full table scan or GSI
- Schema-less — additionalAttributes stored natively as Map
- Auto-scaling, no connection pool overhead

### Aurora PostgreSQL Serverless v2
- Scales to zero between tests (0.5–4 ACU)
- GIN index on JSONB `additionalAttributes` for flexible queries
- Full SQL expressiveness — region+technology join in one query
- Field projection reads only requested columns (Aurora advantage)

### RDS Oracle SE2
- Familiar SQL dialect for existing Oracle shops
- `IS JSON` constraint on VARCHAR2(4000) replaces JSONB
- License-included (SE2) — no BYOL complexity
- Higher cost baseline; no serverless option

---

## Cleanup

```bash
# Delete Aurora cluster
aws rds delete-db-cluster --db-cluster-identifier neo-poc-cluster \
    --skip-final-snapshot --region eu-west-2

# Delete Oracle instance (if created)
aws rds delete-db-instance --db-instance-identifier neo-poc-oracle \
    --skip-final-snapshot --region eu-west-2

# Delete DynamoDB table
aws dynamodb delete-table --table-name HandsetReference --region eu-west-2

# Or delete the whole CloudFormation stack:
aws cloudformation delete-stack --stack-name neo-db-poc --region eu-west-2
```
