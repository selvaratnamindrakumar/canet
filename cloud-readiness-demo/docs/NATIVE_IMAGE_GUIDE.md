# Spring Boot Native Image Guide
## GraalVM AOT Compilation — Cloud Readiness PoC

> **Purpose:** This guide documents the investigation and steps for generating a
> GraalVM native image from the Spring Boot Cloud Readiness PoC.
> Native images deliver significantly smaller footprints, faster startup,
> and lower memory — all desirable cloud deployment properties.

---

## Why Native Image Matters for Cloud Readiness

| Metric              | JVM Container Image | Native Image  | Improvement   |
|---------------------|---------------------|---------------|---------------|
| Startup time        | 4–8 seconds         | < 0.5 seconds | ~10–15×       |
| Container image size| ~250–350 MB         | ~80–150 MB    | ~2–3× smaller |
| Memory at idle      | 200–350 MB heap     | 30–80 MB      | ~4–5× less    |
| Memory at load      | 400–600 MB          | 80–200 MB     | ~3–4× less    |
| ECS cold start      | Slow (Fargate spin-up + JVM warm-up) | Fast | Significant cost saving on Fargate Spot |
| Throughput          | Higher after warm-up | Equal or better at steady state | — |

These advantages make native images particularly valuable for:
- **ECS Fargate Spot** — tasks start and stop frequently
- **Lambda** (future) — cold start latency is critical
- **Microservices** — many small services, each wasting 200 MB on JVM overhead
- **Cost optimisation** — fewer vCPUs and memory needed per task

---

## Current Status (Investigation Results)

| Approach | Status | Blocker | Resolution |
|----------|--------|---------|------------|
| `mvn clean package` | ✅ Works | — | — |
| `mvn jib:buildTar` | ✅ Works | SSL (enterprise) | Import CA cert |
| `mvn jib:build` (ECR) | ✅ Works | SSL (enterprise) | Import CA cert |
| `mvn -Pnative native:compile` | ❌ Windows | GraalVM not installed | Install GraalVM or use EC2 |
| `mvn -Pnative spring-boot:build-image` | ❌ Windows | Docker daemon missing | Use EC2 with Docker |
| Native compile on EC2 | ⏳ Next step | Requires GraalVM on Linux EC2 | See Path A below |
| CodeBuild native pipeline | ⏳ Next step | Configuration required | See Path B below |

---

## Path A — Native Compile on EC2 (Recommended for PoC)

This is the **recommended approach** for demonstrating native images in the sandbox:
build on a Linux EC2 instance where you control the full toolchain.

### Step 1 — Launch EC2 Instance

Recommended: `c6i.xlarge` (4 vCPU / 8 GB RAM) — native compilation is CPU and memory intensive.

```bash
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type c6i.xlarge \
  --key-name YOUR-KEY \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=graalvm-build}]'
```

### Step 2 — Install GraalVM on EC2 (Amazon Linux 2023)

```bash
ssh -i your-key.pem ec2-user@<EC2-IP>

# Option A: Install GraalVM CE via SDKMAN (easiest)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17.0.12-graalce
sdk use java 17.0.12-graalce

# Verify
java -version
# Expected: OpenJDK 17 GraalVM CE ...
native-image --version
# Expected: GraalVM Runtime Environment GraalVM CE 17...

# Option B: Install via package manager (Amazon Linux)
sudo yum install -y gcc glibc-devel zlib-devel
# Then download GraalVM CE binary from GitHub releases
```

### Step 3 — Install Maven

```bash
sudo dnf install -y maven
# Or:
curl -O https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar xzf apache-maven-3.9.9-bin.tar.gz
export PATH=$PATH:$PWD/apache-maven-3.9.9/bin
```

### Step 4 — Get the Source Code

```bash
# Option A: Clone from GitHub
git clone https://github.com/selvaratnamindrakumar/canet.git
cd canet/cloud-readiness-demo

# Option B: Upload via S3 (if GitHub not accessible)
aws s3 cp s3://YOUR-BUCKET/poc/cloud-readiness-demo-source.zip .
unzip cloud-readiness-demo-source.zip
cd cloud-readiness-demo
```

### Step 5 — Compile Native Image

```bash
# This takes 3–8 minutes on c6i.xlarge
mvn -Pnative native:compile -DskipTests

# Expected output:
# [native-image-plugin] native-image --no-fallback ...
# ========================================================
# GraalVM Native Image: Generating 'cloud-readiness-demo'...
# ========================================================
# [1/8] Initializing...
# [2/8] Performing analysis...
# [3/8] Building universe...
# ...
# [8/8] Creating image...
# Finished generating 'cloud-readiness-demo' in ...s.
```

Output: `target/cloud-readiness-demo` (native executable, ~80–120 MB)

### Step 6 — Test the Native Executable

```bash
# Run directly — no JVM required!
./target/cloud-readiness-demo --spring.profiles.active=prod

# Expected startup time: < 0.5 seconds
# Look for: Started CloudReadinessApplication in 0.xxx seconds

# Test API
curl http://localhost:8080/api/v1/tasks
curl http://localhost:8080/actuator/health
```

### Step 7 — Record Benchmark Results

```bash
# Measure startup time
time ./target/cloud-readiness-demo &
sleep 3
curl -s http://localhost:8080/actuator/health

# Measure memory
ps aux | grep cloud-readiness-demo

# Compare with JVM startup
time java -jar target/cloud-readiness-demo-1.0.0.jar &
sleep 8
ps aux | grep cloud-readiness
```

### Step 8 — Package as Docker Image (if Docker available on EC2)

```bash
# Install Docker on EC2
sudo dnf install -y docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user
newgrp docker

# Build minimal native image (Alpine with musl — requires recompile)
# Or use a Debian-slim base for the native executable
cat > Dockerfile.native << 'EOF'
FROM debian:12-slim
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup
COPY target/cloud-readiness-demo /app/cloud-readiness-demo
RUN chmod +x /app/cloud-readiness-demo
USER appuser
EXPOSE 8080
ENTRYPOINT ["/app/cloud-readiness-demo"]
EOF

docker build -f Dockerfile.native -t cloud-readiness-demo:native .
docker images cloud-readiness-demo

# Compare sizes:
# cloud-readiness-demo:latest   (JVM)    ~300 MB
# cloud-readiness-demo:native   (GraalVM) ~85 MB
```

---

## Path B — AWS CodeBuild Native Pipeline

For a repeatable, infrastructure-as-code approach without managing EC2:

```yaml
# buildspec-native.yml
version: 0.2

phases:
  install:
    runtime-versions:
      java: corretto17
    commands:
      # Install GraalVM CE via SDKMAN in CodeBuild
      - curl -s "https://get.sdkman.io" | bash -s -- --force
      - source "$HOME/.sdkman/bin/sdkman-init.sh"
      - sdk install java 17.0.12-graalce
      - sdk use java 17.0.12-graalce
      - native-image --version

  build:
    commands:
      - cd cloud-readiness-demo
      - mvn -Pnative native:compile -DskipTests
      - ls -lh target/cloud-readiness-demo

  post_build:
    commands:
      # Package as container image
      - docker build -f Dockerfile.native -t cloud-readiness-demo:native .
      - |
        ECR_URI=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/cloud-readiness-demo
        aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_URI
        docker tag cloud-readiness-demo:native $ECR_URI:native
        docker push $ECR_URI:native

artifacts:
  files:
    - cloud-readiness-demo/target/cloud-readiness-demo
```

---

## Path C — Spring Boot Buildpacks (Docker Available in Sandbox)

If Docker is available in the sandbox EC2 (not the Windows workstation):

```bash
# On EC2 with Docker installed
cd cloud-readiness-demo

# Build native OCI image using Paketo buildpacks
# This downloads GraalVM automatically inside the build container
mvn -Pnative spring-boot:build-image

# The resulting image is tagged: cloud-readiness-demo:1.0.0-native
docker images cloud-readiness-demo

# Run and test
docker run -p 8080:8080 cloud-readiness-demo:1.0.0-native
curl http://localhost:8080/actuator/health
```

> **Note:** This approach starts a Docker build container that automatically downloads
> GraalVM CE and runs the native compilation inside it. You do NOT need GraalVM installed
> on the host — just Docker.

---

## Benchmark Recording Template

Use this table to record results from the sandbox testing:

| Metric | JVM Container | Native Image | Notes |
|--------|--------------|--------------|-------|
| Build time | | | Time for `mvn package` / `mvn -Pnative native:compile` |
| Image size | | | `docker images` or `ls -lh target/` |
| Startup time | | | Time from process start to "Started in Xs" |
| RSS memory (idle) | | | `ps aux` → RSS column |
| RSS memory (under load) | | | After 100 req via `curl` loop |
| `/actuator/health` p99 | | | `wrk -t4 -c10 -d30s .../actuator/health` |
| `/api/v1/tasks` p99 | | | `wrk -t4 -c10 -d30s .../api/v1/tasks` |
| ECS cold start (Fargate) | | | Time from task `PENDING` to health check `HEALTHY` |

---

## Expected Results Summary

```
Standard JVM Image:
  Startup:        4–8 seconds
  Image size:     ~280 MB
  Memory (idle):  ~220 MB
  Good for:       Long-running stable workloads

GraalVM Native Image:
  Startup:        < 0.5 seconds
  Image size:     ~90 MB
  Memory (idle):  ~45 MB
  Good for:       Fargate Spot, scale-to-zero, Lambda-style workloads

Verdict for NEO Cloud Readiness:
  Both approaches are cloud-ready.
  Native image is preferred for new microservices.
  JVM image is safe for migrating existing applications.
```

---

## Known Limitations of Native Image

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| Build time 3–8 min | Slower CI pipeline | Cache GraalVM layer in CodeBuild |
| Reflection requires AOT hints | Some libraries need extra config | Spring Boot 3 generates hints automatically |
| No dynamic class loading | Limits some framework features | Verify with `--no-fallback` flag |
| Debugging is harder | Production diagnosis more complex | CloudWatch Logs + X-Ray tracing |
| No JIT optimisation | Throughput ceiling vs JVM | Profile your specific workload |

---

*Document version 1.0 | NEO Cloud Readiness — GraalVM Native Image Investigation*
