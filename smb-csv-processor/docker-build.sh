#!/usr/bin/env bash
# =============================================================================
# docker-build.sh — build the smb-csv-processor Docker image with the version
# extracted from pom.xml.
#
# Usage:
#   ./docker-build.sh              # build and tag as both <version> and latest
#   ./docker-build.sh --no-latest  # tag as <version> only
#   ./docker-build.sh --push       # push to registry after build
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------------------------------------------------------------------------
# Extract version from pom.xml
# ---------------------------------------------------------------------------
if command -v mvn &>/dev/null; then
    APP_VERSION="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)"
elif command -v python3 &>/dev/null; then
    APP_VERSION="$(python3 -c "
import xml.etree.ElementTree as ET
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse('pom.xml').getroot()
print(root.find('m:version', ns).text)
")"
elif command -v python &>/dev/null; then
    APP_VERSION="$(python -c "
import xml.etree.ElementTree as ET
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse('pom.xml').getroot()
print(root.find('m:version', ns).text)
")"
else
    echo "ERROR: mvn or python is required to read the version from pom.xml" >&2
    exit 1
fi

IMAGE_NAME="smb-csv-processor"
TAG_LATEST=true
PUSH=false

for arg in "$@"; do
    case "$arg" in
        --no-latest) TAG_LATEST=false ;;
        --push)      PUSH=true ;;
        *) echo "Unknown option: $arg" >&2; exit 1 ;;
    esac
done

echo "Building Docker image: ${IMAGE_NAME}:${APP_VERSION}"
docker build \
    --build-arg APP_VERSION="${APP_VERSION}" \
    -t "${IMAGE_NAME}:${APP_VERSION}" \
    .

if [ "$TAG_LATEST" = "true" ]; then
    echo "Tagging as: ${IMAGE_NAME}:latest"
    docker tag "${IMAGE_NAME}:${APP_VERSION}" "${IMAGE_NAME}:latest"
fi

if [ "$PUSH" = "true" ]; then
    echo "Pushing: ${IMAGE_NAME}:${APP_VERSION}"
    docker push "${IMAGE_NAME}:${APP_VERSION}"
    if [ "$TAG_LATEST" = "true" ]; then
        docker push "${IMAGE_NAME}:latest"
    fi
fi

echo ""
echo "Done. Image: ${IMAGE_NAME}:${APP_VERSION}"
