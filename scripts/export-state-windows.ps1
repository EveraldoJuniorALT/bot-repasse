param(
    [string]$PostgresContainer = "postgres-db",
    [string]$EvolutionContainer = "evolution-api",
    [string]$PostgresUser = "evolution",
    [string]$PostgresDatabase = "evolution",
    [switch]$IncludeEnv
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$stateDir = Join-Path $projectRoot "deploy\state"
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$stateDir = (Resolve-Path $stateDir).Path

Write-Host "Criando dump do PostgreSQL..." -ForegroundColor Cyan
docker exec $PostgresContainer `
    pg_dump `
    -U $PostgresUser `
    -d $PostgresDatabase `
    -Fc `
    -f /tmp/evolution-db.dump

docker cp `
    "${PostgresContainer}:/tmp/evolution-db.dump" `
    (Join-Path $stateDir "evolution-db.dump")

docker exec $PostgresContainer rm -f /tmp/evolution-db.dump

Write-Host "Descobrindo o volume de instâncias da Evolution..." -ForegroundColor Cyan
$volumeName = docker inspect $EvolutionContainer `
    --format '{{range .Mounts}}{{if eq .Destination "/evolution/instances"}}{{.Name}}{{end}}{{end}}'

if ([string]::IsNullOrWhiteSpace($volumeName)) {
    throw "Não foi possível localizar o volume montado em /evolution/instances."
}

Write-Host "Exportando volume $volumeName..." -ForegroundColor Cyan
docker run --rm `
    --volume "${volumeName}:/data:ro" `
    --volume "${stateDir}:/backup" `
    alpine:3.20 `
    sh -c "tar czf /backup/evolution-instances.tar.gz -C /data ."

if ($IncludeEnv) {
    Copy-Item ".env" (Join-Path $stateDir ".env") -Force
    Write-Warning "O arquivo .env contém segredos. Transfira e armazene esta pasta com segurança."
}

Get-FileHash `
    -Algorithm SHA256 `
    (Join-Path $stateDir "evolution-db.dump"), `
    (Join-Path $stateDir "evolution-instances.tar.gz") |
    ForEach-Object { "$($_.Hash)  $([System.IO.Path]::GetFileName($_.Path))" } |
    Set-Content -Encoding ASCII (Join-Path $stateDir "SHA256SUMS.txt")

Write-Host ""
Write-Host "Estado exportado para: $stateDir" -ForegroundColor Green
Write-Host "A restauração é opcional. Uma instalação limpa com novo QR Code é mais simples."
