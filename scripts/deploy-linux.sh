#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker não encontrado."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "O plugin Docker Compose não foi encontrado."
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  chmod 600 .env
  echo "Foi criado o arquivo .env. Edite-o antes de executar o deploy novamente."
  exit 1
fi

chmod 600 .env

set -a
# shellcheck disable=SC1091
source .env
set +a

EVOLUTION_IMAGE="${EVOLUTION_IMAGE:-evolution-api-newsletter:2.4.0-rc2-working}"
BOT_IMAGE="${BOT_IMAGE:-bot-repasse:1.0.0}"

if [[ -f deploy/evolution-api-newsletter.tar ]]; then
  echo "Carregando a imagem personalizada da Evolution..."
  docker image load --input deploy/evolution-api-newsletter.tar
fi

if [[ -f deploy/bot-repasse.tar ]]; then
  echo "Carregando a imagem do bot..."
  docker image load --input deploy/bot-repasse.tar
fi

if ! docker image inspect "$EVOLUTION_IMAGE" >/dev/null 2>&1; then
  echo "Imagem da Evolution não encontrada: $EVOLUTION_IMAGE"
  echo "Exporte-a no Windows com scripts/export-images-windows.ps1 e transfira o TAR."
  exit 1
fi

if ! docker image inspect "$BOT_IMAGE" >/dev/null 2>&1; then
  echo "Imagem do bot não encontrada. Construindo no Linux..."
  docker compose build bot-repasse
fi

echo "Validando o compose.yaml..."
docker compose config >/dev/null

echo "Iniciando os serviços..."
docker compose up --detach --remove-orphans

echo
docker compose ps
echo
echo "Logs do bot: docker compose logs -f bot-repasse"
echo "Logs da Evolution: docker compose logs -f evolution-api"
