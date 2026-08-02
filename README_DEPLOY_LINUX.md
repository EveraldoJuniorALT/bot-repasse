# Deploy Linux — bot-repasse

Este pacote prepara o projeto para executar continuamente em um notebook Linux com:

- bot Java;
- Evolution API personalizada com o patch de Newsletter;
- PostgreSQL;
- volumes persistentes;
- health checks;
- reinício automático;
- rotação de logs;
- scripts de deploy, atualização e backup.

## Alterações obrigatórias no repositório

1. Substitua o `Dockerfile` pelo arquivo deste pacote.
2. Adicione `compose.yaml` na raiz.
3. Remova o arquivo antigo `Docker-compose.yml`.
   O Linux diferencia letras maiúsculas e minúsculas, e o nome padrão recomendado é `compose.yaml`.
4. Substitua `.dockerignore`.
5. Copie `.env.example`.
6. Copie a pasta `scripts`.
7. Acrescente o conteúdo de `.gitignore.additions` ao `.gitignore`.
8. Remova o salvamento temporário das fotos:

```bash
git apply remove-telegram-debug.patch
```

## Por que a Evolution não pode voltar para `latest`

O projeto depende da imagem personalizada que recebeu o patch de mídia para Newsletter.
Não troque por:

```yaml
image: evoapicloud/evolution-api:latest
```

Isso descartaria as correções que fizeram as imagens funcionarem.

## Etapa 1 — exportar as imagens funcionais no Windows

Na raiz do projeto:

```powershell
Set-ExecutionPolicy -Scope Process Bypass

.\scripts\export-images-windows.ps1 `
  -EvolutionSourceImage "evolution-api-newsletter-test:2.4.0-rc2"
```

O script:

- cria a tag `evolution-api-newsletter:2.4.0-rc2-working`;
- constrói a imagem atual do bot;
- gera:
  - `deploy/evolution-api-newsletter.tar`;
  - `deploy/bot-repasse.tar`;
  - `deploy/SHA256SUMS.txt`.

Esses arquivos podem ser grandes. Não os envie para o GitHub.

### Verificar arquitetura

No Windows:

```powershell
docker image inspect `
  evolution-api-newsletter:2.4.0-rc2-working `
  --format "{{.Os}}/{{.Architecture}}"
```

No Linux:

```bash
uname -m
```

Um PC Windows comum cria `linux/amd64`. O notebook precisa usar arquitetura compatível.
Se o notebook for ARM, será necessário reconstruir a Evolution para ARM.

## Etapa 2 — levar o projeto para o notebook

Opções:

- fazer commit das alterações e clonar o repositório no notebook;
- copiar a pasta por SSH/SCP;
- usar um pendrive.

Além do código, transfira os dois arquivos `.tar` da pasta `deploy`.

Nunca publique o `.env`.

## Etapa 3 — instalar Docker no Linux

Instale Docker Engine, Buildx e o plugin Docker Compose pela documentação oficial da sua distribuição.

Verifique:

```bash
docker --version
docker compose version
sudo systemctl enable --now docker
```

Opcionalmente, permita executar Docker sem `sudo`:

```bash
sudo usermod -aG docker "$USER"
```

Saia e entre novamente na sessão após alterar o grupo.

## Etapa 4 — configurar variáveis

Na raiz:

```bash
cp .env.example .env
nano .env
chmod 600 .env
```

Preencha todos os campos.

Gere valores aleatórios:

```bash
openssl rand -hex 32
```

Use uma senha do PostgreSQL composta por letras, números e `_`.
Caracteres como `@`, `:`, `/`, `?` e `#` precisam ser URL-encoded na URI.

## Etapa 5 — deploy

```bash
chmod +x scripts/*.sh
./scripts/deploy-linux.sh
```

O script carrega as imagens transferidas, valida o Compose e inicia a pilha.

Verifique:

```bash
docker compose ps
./scripts/status-linux.sh
```

Logs:

```bash
docker compose logs -f bot-repasse
docker compose logs -f evolution-api
docker compose logs -f postgres-db
```

## Primeiro uso no novo servidor

Em uma instalação limpa:

1. abra o Manager da Evolution;
2. ative a licença, caso seja solicitado;
3. crie ou reconecte a instância com o nome definido em `WHATSAPP_INSTANCE_NAME`;
4. leia o QR Code;
5. publique uma foto de teste no Telegram.

Por segurança, o Manager fica ligado apenas em `127.0.0.1:8081`.

Para acessar do seu computador por SSH:

```bash
ssh -L 8081:127.0.0.1:8081 usuario@IP_DO_NOTEBOOK
```

Depois abra no computador:

```text
http://localhost:8081/manager
```

Não use `EVOLUTION_BIND_ADDRESS=0.0.0.0` sem firewall, autenticação adicional ou proxy reverso.

## Migrar a sessão atual — opcional

Uma instalação limpa é mais simples. Para tentar manter banco e instância atuais:

No Windows:

```powershell
.\scripts\export-state-windows.ps1 -IncludeEnv
```

Transfira `deploy/state` para o Linux.

No Linux, antes do primeiro deploy completo:

```bash
chmod +x scripts/*.sh
./scripts/import-state-linux.sh
```

A licença ou a sessão podem pedir nova ativação mesmo após restaurar os dados.

## Backup no Linux

```bash
./scripts/backup-linux.sh
```

Os backups ficam em:

```text
backups/AAAA-MM-DD_HH-MM-SS/
```

Guarde também uma cópia segura do `.env`, separada do repositório.

## Atualização do bot

Depois de enviar alterações ao GitHub:

```bash
./scripts/update-linux.sh
```

Esse script atualiza o código, recompila somente o bot e recria os containers necessários.

Não substitua ou atualize automaticamente a Evolution personalizada enquanto o patch não estiver incorporado oficialmente.

## Reinício e disponibilidade

Todos os serviços usam:

```yaml
restart: unless-stopped
```

Com o serviço Docker habilitado no boot, os containers voltam após reinicialização do notebook.

## Comandos úteis

```bash
docker compose ps
docker compose logs --tail 200 bot-repasse
docker compose restart bot-repasse
docker compose restart evolution-api
docker compose up -d
docker compose stop
```

Nunca execute isto sem ter certeza:

```bash
docker compose down -v
```

A opção `-v` apaga os volumes persistentes do PostgreSQL e da Evolution.

## Energia e rede do notebook

Para operação 24/7:

- desative suspensão e hibernação ao fechar a tampa;
- use conexão por cabo quando possível;
- configure o notebook para ligar novamente após queda de energia, caso a BIOS ofereça essa opção;
- mantenha espaço livre em disco;
- faça backups periódicos;
- acompanhe temperatura e estado da bateria.

## Estrutura final

```text
bot-repasse/
├── compose.yaml
├── Dockerfile
├── .dockerignore
├── .env
├── .env.example
├── pom.xml
├── src/
├── deploy/
│   ├── evolution-api-newsletter.tar
│   └── bot-repasse.tar
└── scripts/
    ├── deploy-linux.sh
    ├── update-linux.sh
    ├── status-linux.sh
    ├── backup-linux.sh
    ├── import-state-linux.sh
    ├── export-images-windows.ps1
    └── export-state-windows.ps1
```
