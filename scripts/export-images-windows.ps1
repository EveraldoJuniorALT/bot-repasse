param(
    [string]$EvolutionSourceImage = "evolution-api-newsletter-test:2.4.0-rc2",
    [string]$EvolutionTargetImage = "evolution-api-newsletter:2.4.0-rc2-working",
    [string]$BotTargetImage = "bot-repasse:1.0.0"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$deployDir = Join-Path $projectRoot "deploy"
New-Item -ItemType Directory -Force -Path $deployDir | Out-Null

Write-Host "Verificando a imagem funcional da Evolution..." -ForegroundColor Cyan
docker image inspect $EvolutionSourceImage *> $null

Write-Host "Criando a tag portátil: $EvolutionTargetImage" -ForegroundColor Cyan
docker image tag $EvolutionSourceImage $EvolutionTargetImage

Write-Host "Construindo a imagem atual do bot Java..." -ForegroundColor Cyan
docker build --tag $BotTargetImage .

$evolutionTar = Join-Path $deployDir "evolution-api-newsletter.tar"
$botTar = Join-Path $deployDir "bot-repasse.tar"

Write-Host "Exportando Evolution para $evolutionTar" -ForegroundColor Cyan
docker image save --output $evolutionTar $EvolutionTargetImage

Write-Host "Exportando bot para $botTar" -ForegroundColor Cyan
docker image save --output $botTar $BotTargetImage

$checksums = @(
    Get-FileHash -Algorithm SHA256 $evolutionTar
    Get-FileHash -Algorithm SHA256 $botTar
)

$checksumFile = Join-Path $deployDir "SHA256SUMS.txt"
$checksums |
    ForEach-Object { "$($_.Hash)  $([System.IO.Path]::GetFileName($_.Path))" } |
    Set-Content -Encoding ASCII $checksumFile

Write-Host ""
Write-Host "Exportação concluída." -ForegroundColor Green
Write-Host "Arquivos:"
Write-Host "  $evolutionTar"
Write-Host "  $botTar"
Write-Host "  $checksumFile"
Write-Host ""
Write-Host "Transfira a pasta do projeto e os arquivos acima para o notebook Linux."
