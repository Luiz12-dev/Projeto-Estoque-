# =============================================================================
#  Gera o pacote de instalacao da Metalurgica Fantineli.
#
#  Compila o frontend, embute dentro do backend e monta uma pasta pronta para
#  copiar num pendrive e levar para a maquina do cliente.
#
#  Uso:  .\empacotar.ps1
#  Saida: .\pacote-instalacao\
# =============================================================================

# Atencao ao PowerShell 5.1: com ErrorActionPreference = 'Stop', qualquer coisa
# que um programa externo escreva no stderr vira erro fatal — inclusive avisos
# inofensivos do compilador. Por isso as falhas aqui sao conferidas pelo
# codigo de saida ($LASTEXITCODE), que e o sinal confiavel.
$ErrorActionPreference = 'Continue'

$raizBackend  = $PSScriptRoot
$raizFrontend = Join-Path (Split-Path $raizBackend -Parent) 'Projeto-Estoque-FrontEnd'
$estaticos    = Join-Path $raizBackend 'src\main\resources\static'
$pacote       = Join-Path $raizBackend 'pacote-instalacao'

function Passo($texto) { Write-Host "`n>>> $texto" -ForegroundColor Cyan }

if (-not (Test-Path $raizFrontend)) {
    throw "Frontend nao encontrado em $raizFrontend"
}

# --- 1. Frontend -------------------------------------------------------------
Passo 'Compilando o frontend'
Push-Location $raizFrontend
try {
    if (-not (Test-Path 'node_modules')) { npm install }
    npx ng build
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao do frontend falhou' }
} finally { Pop-Location }

Passo 'Embutindo a tela dentro do backend'
if (Test-Path $estaticos) { Remove-Item $estaticos -Recurse -Force }
New-Item -ItemType Directory -Path $estaticos -Force | Out-Null
Copy-Item (Join-Path $raizFrontend 'dist\estoque-frontEnd\browser\*') $estaticos -Recurse -Force
$qtd = (Get-ChildItem $estaticos -Recurse -File).Count
Write-Host "    $qtd arquivo(s) copiado(s)"

# --- 2. Backend --------------------------------------------------------------
Passo 'Empacotando a aplicacao (isto roda os testes)'
Push-Location $raizBackend
try {
    .\mvnw.cmd clean package
    if ($LASTEXITCODE -ne 0) { throw 'O empacotamento falhou — veja os testes acima' }
} finally { Pop-Location }

$jar = Get-ChildItem (Join-Path $raizBackend 'target\*.jar') |
       Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*.original' } |
       Select-Object -First 1
if (-not $jar) { throw 'Jar nao encontrado em target\' }

# --- 3. Pacote ---------------------------------------------------------------
Passo 'Montando a pasta de instalacao'
if (Test-Path $pacote) { Remove-Item $pacote -Recurse -Force }
New-Item -ItemType Directory -Path $pacote -Force | Out-Null

Copy-Item $jar.FullName (Join-Path $pacote 'estoque.jar')

foreach ($arquivo in @('instalar.ps1', 'iniciar.bat', 'backup.bat', 'LEIA-ME.md')) {
    $origem = Join-Path $raizBackend "instalacao\$arquivo"
    if (Test-Path $origem) { Copy-Item $origem $pacote }
}

$tamanho = [math]::Round(((Get-ChildItem $pacote -Recurse -File |
            Measure-Object Length -Sum).Sum / 1MB), 1)

Write-Host "`n=================================================" -ForegroundColor Green
Write-Host " Pacote pronto: $pacote" -ForegroundColor Green
Write-Host " Tamanho: $tamanho MB" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host @'

Copie a pasta inteira para um pendrive.
Na maquina do cliente, leia o LEIA-ME.md e rode instalar.ps1.

'@
