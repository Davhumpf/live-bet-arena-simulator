#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B verify
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
node scripts/e2e.mjs
