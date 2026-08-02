#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

STATE_DIR="${1:-$ROOT_DIR/deploy/state}"

if [[ ! -f "$STATE_DIR/evolution-db.dump" ]] || \
   [[ ! -f "$STATE_DIR/evolution-instances.tar.gz" ]]; then
  echo "Arquivos de estado não encontrados em: $STATE_DIR"
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "Configure o arquivo .env antes de restaurar."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

echo "Parando a aplicação..."
docker compose down

docker volume create bot-repasse-evolution-instances >/dev/null
docker volume create bot-repasse-postgres-data >/dev/null

echo "Restaurando o volume de instâncias..."
docker run --rm \
  --volume bot-repasse-evolution-instances:/data \
  --volume "$STATE_DIR:/backup:ro" \
  alpine:3.20 \
  sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; tar xzf /backup/evolution-instances.tar.gz -C /data"

echo "Iniciando somente o PostgreSQL..."
docker compose up --detach postgres-db

echo "Aguardando o PostgreSQL ficar saudável..."
for _ in $(seq 1 60); do
  if [[ "$(docker inspect --format='{{.State.Health.Status}}' bot-repasse-postgres 2>/dev/null || true)" == "healthy" ]]; then
    break
  fi
  sleep 2
done

docker cp "$STATE_DIR/evolution-db.dump" bot-repasse-postgres:/tmp/evolution-db.dump

echo "Restaurando o banco..."
docker compose exec -T postgres-db \
  pg_restore \
  --clean \
  --if-exists \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  /tmp/evolution-db.dump

docker compose exec -T postgres-db rm -f /tmp/evolution-db.dump

echo "Iniciando a pilha completa..."
docker compose up --detach
docker compose ps

echo "A licença ou a sessão ainda podem pedir nova ativação, dependendo do ambiente."
