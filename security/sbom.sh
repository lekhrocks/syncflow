#!/bin/bash
set -euo pipefail

echo "=== SyncFlow SBOM Generation ==="
PROJECT_DIR="${1:-/Users/lekhrajkumar/Projects/syncflow}"

# Generate CycloneDX SBOM for the Java project
if command -v cyclonedx-gradle &> /dev/null; then
    cd "$PROJECT_DIR"
    ./gradlew cyclonedxBom
    echo "SBOM generated at: $PROJECT_DIR/build/reports/bom.json"
else
    echo "cyclonedx-gradle not found. Install with:"
    echo "  ./gradlew build -Pcyclonedx"
    echo ""
    echo "Alternatively, use Trivy for file-system scanning:"
    echo "  trivy fs --format cyclonedx --output bom.cdx.json $PROJECT_DIR"
fi

# Dependency check
if command -v trivy &> /dev/null; then
    echo "Running Trivy dependency scan..."
    trivy fs --severity CRITICAL,HIGH --ignore-unfixed "$PROJECT_DIR"
fi
