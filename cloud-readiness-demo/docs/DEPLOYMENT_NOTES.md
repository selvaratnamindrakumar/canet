# AWS Sandbox Deployment Notes
## Cloud Readiness PoC — Testing Guide

> **Context:** These notes cover deployment and validation of the Spring Boot Cloud Readiness PoC
> in an AWS Sandbox environment, specifically addressing the constraint that **Docker is not
> available on internet-facing Windows workstations**.

---

## Environment Summary

| Component            | Detail                                      |
|----------------------|---------------------------------------------|
| Development machine  | Windows workstation (no Docker daemon)       |
| Target environment   | AWS Sandbox (EC2 / ECS Fargate)             |
| Java version         | Eclipse Temurin JDK 17 (Windows)            |
| Build tool           | Maven 3.8+                                  |
| Image build strategy | **Jib Maven Plugin** (no Docker required)   |
| Image registry       | Amazon ECR                                  |
| Runtime              | ECS Fargate or EC2                          |

---

## Deployment Paths

Four paths are documented below, ordered from simplest to most advanced.
**Path 2 (Jib → ECR direct) is recommended** for the sandbox PoC.

```
Path 1  JAR on EC2 (simplest — no containers at all)
Path 2  Jib → ECR → ECS Fargate  ← RECOMMENDED for PoC
Path 3  Jib tar → S3 → EC2 → docker load → ECR → ECS
Path 4  AWS CodeBuild pipeline (fully cloud-based)
```

---

## Path 1 — Run JAR Directly on EC2 (No Containers)

**Best for:** Quickest validation that the application works in AWS.

### Step 1 — Build the JAR on Windows

```cmd
cd cloud-readiness-demo
mvn clean package -DskipTests
```

Output: `target\cloud-readiness-demo-1.0.0.jar`

### Step 2 — Upload to S3

```cmd
aws s3 cp target\cloud-readiness-demo-1.0.0.jar s3://YOUR-BUCKET/poc/cloud-readiness-demo-1.0.0.jar
```

### Step 3 — Launch EC2 (Amazon Linux 2023, t3.small)

```bash
# In AWS Console or via CLI:
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type t3.small \
  --key-name YOUR-KEY-PAIR \
  --security-group-ids sg-XXXXXXXX \
  --iam-instance-profile Name=EC2-S3-ReadOnly-Role \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=cloud-readiness-poc}]'
```

### Step 4 — Install Java and Run on EC2

```bash
# SSH into EC2
ssh -i your-key.pem ec2-user@<EC2-PUBLIC-IP>

# Install Java 17
sudo dnf install -y java-17-amazon-corretto

# Download JAR from S3
aws s3 cp s3://YOUR-BUCKET/poc/cloud-readiness-demo-1.0.0.jar .

# Run with production profile
java -jar cloud-readiness-demo-1.0.0.jar \
  --spring.profiles.active=prod \
  --app.environment=aws-sandbox \
  --server.port=8080
```

### Step 5 — Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# Task list API
curl http://localhost:8080/api/v1/tasks | python3 -m json.tool

# Runtime info
curl http://localhost:8080/api/v1/info | python3 -m json.tool
```

---

## Path 2 — Jib → ECR → ECS Fargate (RECOMMENDED)

**Best for:** Full cloud-native demonstration without any Docker installation.

> Jib pushes a production-ready OCI image directly from Maven to ECR.
> No Docker daemon is needed on the Windows workstation at any point.

### Prerequisites

```cmd
# Verify AWS CLI is configured
aws sts get-caller-identity

# Verify Java and Maven
java -version
mvn -version
```

### Step 1 — Create ECR Repository

```cmd
aws ecr create-repository ^
  --repository-name cloud-readiness-demo ^
  --image-scanning-configuration scanOnPush=true ^
  --region us-east-1
```

Save the `repositoryUri` from the output.

### Step 2 — Get ECR Auth Token

```cmd
aws ecr get-login-password --region us-east-1
```

Copy the output (this is your ECR password).

### Step 3 — Build and Push with Jib (no Docker!)

```cmd
set AWS_ACCOUNT_ID=123456789012
set AWS_REGION=us-east-1
set ECR_PASSWORD=<paste-output-from-step-2>

mvn jib:build ^
  -Djib.to.image=%AWS_ACCOUNT_ID%.dkr.ecr.%AWS_REGION%.amazonaws.com/cloud-readiness-demo:1.0.0 ^
  -Djib.to.auth.username=AWS ^
  -Djib.to.auth.password=%ECR_PASSWORD%
```

**Linux/Mac equivalent:**
```bash
ECR_PASSWORD=$(aws ecr get-login-password --region us-east-1)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

mvn jib:build \
  -Djib.to.image="${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/cloud-readiness-demo:1.0.0" \
  -Djib.to.auth.username=AWS \
  -Djib.to.auth.password="${ECR_PASSWORD}"

# Or use the included script:
./aws/jib-ecr-push.sh 1.0.0
```

Expected output:
```
[INFO] Built and pushed image as 123456789012.dkr.ecr.us-east-1.amazonaws.com/cloud-readiness-demo:1.0.0
[INFO] Executing tasks:
[INFO] [==============================] 100.0% complete
```

### Step 4 — Verify Image in ECR

```bash
aws ecr describe-images \
  --repository-name cloud-readiness-demo \
  --region us-east-1
```

### Step 5 — Create ECS Cluster

```bash
aws ecs create-cluster \
  --cluster-name cloud-readiness-poc \
  --capacity-providers FARGATE \
  --region us-east-1
```

### Step 6 — Create CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/cloud-readiness-demo \
  --region us-east-1
```

### Step 7 — Register ECS Task Definition

Update `aws/ecs-task-definition.json` — replace `ACCOUNT_ID` and `REGION` — then:

```bash
aws ecs register-task-definition \
  --cli-input-json file://aws/ecs-task-definition.json \
  --region us-east-1
```

### Step 8 — Run a One-Off ECS Task (Quick Test)

```bash
aws ecs run-task \
  --cluster cloud-readiness-poc \
  --task-definition cloud-readiness-demo:1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={
      subnets=[subnet-XXXXXXXX],
      securityGroups=[sg-XXXXXXXX],
      assignPublicIp=ENABLED
  }" \
  --region us-east-1
```

### Step 9 — Get Task IP and Test

```bash
# Get task ARN
TASK_ARN=$(aws ecs list-tasks \
  --cluster cloud-readiness-poc \
  --query taskArns[0] --output text)

# Get task public IP
aws ecs describe-tasks \
  --cluster cloud-readiness-poc \
  --tasks $TASK_ARN \
  --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" \
  --output text | xargs -I {} \
  aws ec2 describe-network-interfaces \
  --network-interface-ids {} \
  --query "NetworkInterfaces[0].Association.PublicIp" --output text
```

Then test:
```bash
PUBLIC_IP=<from-above>
curl http://$PUBLIC_IP:8080/actuator/health
curl http://$PUBLIC_IP:8080/api/v1/tasks | python3 -m json.tool
curl http://$PUBLIC_IP:8080/api/v1/info  | python3 -m json.tool
```

---

## Path 3 — Jib TAR → S3 → EC2 → ECR

**Best for:** When direct ECR push is blocked by corporate firewall but S3 is allowed.

### Step 1 — Build TAR on Windows (no Docker needed)

```cmd
cd cloud-readiness-demo
mvn clean package jib:buildTar -DskipTests
```

Output: `target\jib-image.tar`

### Step 2 — Upload TAR to S3

```cmd
aws s3 cp target\jib-image.tar s3://YOUR-BUCKET/poc/cloud-readiness-demo-1.0.0.tar
```

### Step 3 — Load and Push from EC2 (which has Docker)

```bash
# SSH into an EC2 with Docker installed
ssh -i your-key.pem ec2-user@<EC2-IP>

# Download tar from S3
aws s3 cp s3://YOUR-BUCKET/poc/cloud-readiness-demo-1.0.0.tar .

# Load into Docker
docker load < cloud-readiness-demo-1.0.0.tar

# Authenticate and push to ECR
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com

docker tag cloud-readiness-demo:latest \
  ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/cloud-readiness-demo:1.0.0

docker push ${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com/cloud-readiness-demo:1.0.0
```

---

## Path 4 — AWS CodeBuild (Fully Cloud-Based)

**Best for:** CI/CD pipeline where no local tooling is required at all.

### buildspec.yml

Create `buildspec.yml` in the project root:

```yaml
version: 0.2

phases:
  install:
    runtime-versions:
      java: corretto17

  pre_build:
    commands:
      - echo Logging in to Amazon ECR...
      - aws ecr get-login-password --region $AWS_DEFAULT_REGION |
          docker login --username AWS --password-stdin
          $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com

  build:
    commands:
      - echo Build started on `date`
      - cd cloud-readiness-demo
      - mvn clean package -DskipTests
      - docker build -t $IMAGE_REPO_NAME:$IMAGE_TAG .
      - docker tag $IMAGE_REPO_NAME:$IMAGE_TAG
          $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$IMAGE_REPO_NAME:$IMAGE_TAG

  post_build:
    commands:
      - echo Pushing the Docker image...
      - docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$IMAGE_REPO_NAME:$IMAGE_TAG
      - echo Build completed on `date`

artifacts:
  files:
    - aws/ecs-task-definition.json
```

### Create CodeBuild Project (AWS CLI)

```bash
aws codebuild create-project \
  --name cloud-readiness-build \
  --source type=GITHUB,location=https://github.com/selvaratnamindrakumar/canet \
  --artifacts type=NO_ARTIFACTS \
  --environment type=LINUX_CONTAINER,computeType=BUILD_GENERAL1_SMALL,\
    image=aws/codebuild/standard:7.0,privilegedMode=true \
  --service-role arn:aws:iam::ACCOUNT_ID:role/CodeBuildServiceRole
```

---

## SSL Trust Issues with Jib (Enterprise Environments)

If Jib fails with SSL certificate errors in your enterprise environment:

### Diagnosis

The error typically looks like:
```
PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException:
unable to find valid certification path to requested target
```

### Fix 1 — Import Enterprise CA into JDK Truststore

```cmd
rem Export your enterprise CA certificate (ask your IT team for the .crt file)
rem Then import it into the JDK used by Maven:

keytool -importcert ^
  -file enterprise-ca.crt ^
  -alias enterprise-ca ^
  -keystore "%JAVA_HOME%\lib\security\cacerts" ^
  -storepass changeit ^
  -noprompt
```

### Fix 2 — Pass Custom Truststore to Maven

```cmd
set MAVEN_OPTS=-Djavax.net.ssl.trustStore=C:\path\to\truststore.jks -Djavax.net.ssl.trustStorePassword=changeit
mvn jib:build ...
```

### Fix 3 — Use Jib with allowInsecureRegistries (dev only, not for prod)

```cmd
mvn jib:build -Djib.allowInsecureRegistries=true ...
```

---

## Validation Test Plan

Once deployed, execute this checklist to confirm cloud readiness:

### Health Probes

```bash
BASE_URL="http://<APP-IP>:8080"

# ✅ Overall health
curl -s $BASE_URL/actuator/health | python3 -m json.tool

# ✅ Liveness probe (used by ECS health check)
curl -s $BASE_URL/actuator/health/liveness

# ✅ Readiness probe (used by ALB target group)
curl -s $BASE_URL/actuator/health/readiness

# ✅ Custom backing store health
curl -s $BASE_URL/actuator/health | python3 -m json.tool | grep -A5 taskStore
```

Expected: all return `{"status":"UP"}`

### REST API

```bash
# ✅ List pre-seeded tasks
curl -s $BASE_URL/api/v1/tasks | python3 -m json.tool

# ✅ Create a task
curl -s -X POST $BASE_URL/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"AWS Sandbox validation","description":"Testing cloud readiness PoC"}' \
  | python3 -m json.tool

# ✅ Runtime environment info
curl -s $BASE_URL/api/v1/info | python3 -m json.tool
```

### Observability

```bash
# ✅ Prometheus metrics available
curl -s $BASE_URL/actuator/prometheus | grep -E "^(jvm_|http_server)" | head -20

# ✅ Application info endpoint
curl -s $BASE_URL/actuator/info | python3 -m json.tool
```

### Configuration (12-Factor Factor III)

```bash
# ✅ Profile reflected in response
curl -s $BASE_URL/api/v1/info | python3 -m json.tool | grep profile

# ✅ Externalized config working
curl -s $BASE_URL/api/v1/info | python3 -m json.tool | grep environment
```

---

## CloudWatch Logs Validation

Once the ECS task is running with the provided task definition:

```bash
# View live logs from the ECS task
aws logs tail /ecs/cloud-readiness-demo \
  --follow \
  --region us-east-1

# Query for startup confirmation
aws logs start-query \
  --log-group-name /ecs/cloud-readiness-demo \
  --start-time $(date -d "30 minutes ago" +%s) \
  --end-time $(date +%s) \
  --query-string "fields @timestamp, @message | filter @message like /Started CloudReadiness/"
```

---

## Common Issues and Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| Jib SSL error | Enterprise CA not trusted | Import CA cert into JDK truststore |
| ECR auth expired | Token is valid for 12h only | Re-run `aws ecr get-login-password` |
| ECS task stops immediately | Health check fails | Check CloudWatch logs for startup error |
| Port 8080 not reachable | Security group missing rule | Add inbound TCP 8080 from your IP |
| ECS cannot pull from ECR | Missing IAM role | Attach `AmazonECSTaskExecutionRolePolicy` |
| `OutOfMemoryError` | Container memory too low | Increase task definition memory to 1024+ MB |

---

*Document version 1.1 | Updated for AWS Sandbox validation | NEO Cloud Readiness PoC*
