#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -d .git ]]; then
  git pull --ff-only
fi

docker compose build bot-repasse
docker compose up --detach --remove-orphans
docker compose ps
