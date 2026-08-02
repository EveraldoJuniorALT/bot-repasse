#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env ]]; then
  echo "Arquivo .env não encontrado."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

STAMP="$(date +'%Y-%m-%d_%H-%M-%S')"
BACKUP_DIR="$ROOT_DIR/backups/$STAMP"
mkdir -p "$BACKUP_DIR"

echo "Criando dump do PostgreSQL..."
docker compose exec -T postgres-db \
  pg_dump \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  -Fc \
  > "$BACKUP_DIR/evolution-db.dump"

echo "Compactando instâncias da Evolution..."
docker run --rm \
  --volume bot-repasse-evolution-instances:/data:ro \
  --volume "$BACKUP_DIR:/backup" \
  alpine:3.20 \
  sh -c "tar czf /backup/evolution-instances.tar.gz -C /data ."

sha256sum \
  "$BACKUP_DIR/evolution-db.dump" \
  "$BACKUP_DIR/evolution-instances.tar.gz" \
  > "$BACKUP_DIR/SHA256SUMS.txt"

echo "Backup criado em: $BACKUP_DIR"
echo "O arquivo .env não foi incluído. Guarde uma cópia segura separadamente."
