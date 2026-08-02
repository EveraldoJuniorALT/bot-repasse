#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

docker compose ps
echo
docker stats --no-stream \
  bot-repasse \
  bot-repasse-evolution \
  bot-repasse-postgres 2>/dev/null || true
