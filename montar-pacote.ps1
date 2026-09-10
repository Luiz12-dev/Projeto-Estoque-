# =============================================================================
#  Monta o pacote que vai para a maquina do cliente.
#
#      .\montar-pacote.ps1
#
#  Ate agora essa montagem era feita a mao, e a mao ela ja falhou de tres
#  jeitos diferentes: pacote sem o estoque.jar, pacote com a tela antiga
#  porque ninguem lembrou de rodar o build do Angular, e pacote com um jar
#  compilado antes da ultima correcao. Nenhum desses erros aparece na hora --
#  todos aparecem na frente do cliente.
#
#  A ordem aqui nao e' arbitraria: o Angular tem que ser compilado ANTES do
#  Maven, porque o site compilado e' copiado para dentro do jar. Inverter isso
#  produz um pacote que sobe, funciona, e mostra a versao anterior da tela.
# =============================================================================

$ErrorActionPreference = 'Stop'

$backend  = $PSScriptRoot
$frontend = Join-Path (Split-Path $backend -Parent) 'Projeto-Estoque-FrontEnd'
$destino  = Join-Path $backend 'pacote-instalacao'
$estatico = Join-Path $backend 'src\main\resources\static'

function Titulo($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Ok($t)     { Write-Host "  [ok] $t" -ForegroundColor Green }
function Parar($t)  { Write-Host "  [!!] $t" -ForegroundColor Red; exit 1 }

# Chama um programa externo e julga o resultado pelo codigo de saida.
#
# No PowerShell 5.1, com ErrorActionPreference = 'Stop', cada linha que um
# programa externo escreve no stderr vira um ErrorRecord fatal. Ferramenta de
# build escreve AVISO no stderr o tempo todo: o build do Angular terminava com
# codigo 0, tudo compilado, e derrubava a montagem por causa de um aviso de
# estilo num arquivo .ts. A atribuicao abaixo e' local a funcao -- some quando
# ela retorna, e o resto do script continua abortando em erro de verdade.
function Rodar($programa, $argumentos, $mensagemDeErro) {
    $ErrorActionPreference = 'Continue'
    & $programa @argumentos 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -ne 0) { Parar $mensagemDeErro }
}

Write-Host @'

  ===========================================================
   Montando o pacote de instalacao
  ===========================================================
'@ -ForegroundColor White

if (-not (Test-Path $frontend)) {
    Parar "Nao encontrei o frontend em $frontend"
}

# --- 1. Tela ------------------------------------------------------------------
Titulo 'Compilando a tela (Angular)'
Push-Location $frontend
try {
    # npm.cmd, e nao npm: no PATH do Windows o nome "npm" resolve primeiro para
    # npm.ps1, um shim que remonta a linha de comando por conta propria e come
    # o primeiro caractere do argumento -- "npm run build" chega no npm como
    # "pm", e o erro ("Unknown command: pm") nao aponta para lugar nenhum.
    Rodar 'npm.cmd' @('run', 'build') 'O build do Angular falhou. Leia as linhas acima.'
} finally {
    Pop-Location
}

$compilado = Join-Path $frontend 'dist\estoque-frontEnd\browser'
if (-not (Test-Path (Join-Path $compilado 'index.html'))) {
    Parar "O build terminou mas nao achei o index.html em $compilado"
}

Titulo 'Levando a tela para dentro do sistema'
# Limpar antes: sem isso, arquivo de build antigo (chunk-XXXX.js que nao existe
# mais) fica para tras e vai junto para o cliente, engordando o pacote com
# codigo morto.
if (Test-Path $estatico) { Remove-Item "$estatico\*" -Recurse -Force }
else { New-Item -ItemType Directory -Path $estatico | Out-Null }
Copy-Item "$compilado\*" $estatico -Recurse -Force
Ok ('{0} arquivos copiados para src\main\resources\static' -f (Get-ChildItem $estatico -Recurse -File).Count)

# --- 2. Sistema ---------------------------------------------------------------
Titulo 'Compilando o sistema (Maven)'
Push-Location $backend
try {
    # mvnw.cmd, o wrapper do proprio projeto, em vez do mvn do PATH: assim a
    # montagem funciona em qualquer maquina que tenha so o Java, e todo mundo
    # compila com a mesma versao do Maven.
    #
    # package roda os testes de unidade pelo surefire. Os de integracao exigem
    # Docker e ficam no verify -- rodar aqui travaria a montagem numa maquina
    # sem Docker ligado.
    Rodar '.\mvnw.cmd' @('clean', 'package') 'O build do Maven falhou. Leia as linhas acima.'
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $backend 'target\*.jar') |
       Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
       Sort-Object Length -Descending | Select-Object -First 1
if (-not $jar) { Parar 'Nao encontrei o .jar em target\' }

# --- 3. Pacote ----------------------------------------------------------------
Titulo 'Montando a pasta do pacote'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
New-Item -ItemType Directory -Path $destino | Out-Null

Copy-Item $jar.FullName (Join-Path $destino 'estoque.jar')

# O config.properties NAO entra: ele tem as senhas da maquina onde foi gerado.
$scripts = @('INSTALAR.bat', 'instalar.ps1',
             'SISTEMA.bat',  'sistema.ps1',
             'iniciar.bat',  'backup.bat', 'LEIA-ME.md')
foreach ($s in $scripts) {
    $origem = Join-Path $backend "instalacao\$s"
    if (-not (Test-Path $origem)) { Parar "Faltou $s em instalacao\" }
    Copy-Item $origem $destino
}
Ok ('estoque.jar ({0:N0} MB) + {1} arquivos de instalacao' -f ($jar.Length / 1MB), $scripts.Count)

# --- 4. Conferencia -----------------------------------------------------------
# Conferir o pacote montado, e nao a intencao de monta-lo. As duas perguntas
# abaixo sao as que ja deram errado de verdade.
Titulo 'Conferindo o pacote'

# O site esta mesmo dentro do jar? Um .jar e' um zip, e o .NET le zip sem
# ajuda de ninguem -- usar o comando "jar" aqui amarraria a montagem a ter um
# JDK instalado, e quem so tem o JRE nao tem esse comando.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $destino 'estoque.jar'))
try {
    $temTela = $zip.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/static/index.html' }
} finally {
    $zip.Dispose()
}
if ($temTela) { Ok 'A tela esta dentro do jar' }
else { Parar 'O jar NAO tem a tela dentro. O pacote subiria mostrando pagina de erro.' }

# Sobrou algum segredo?
if (Test-Path (Join-Path $destino 'config.properties')) {
    Parar 'Tem um config.properties no pacote. Ele carrega senhas -- nao pode sair daqui.'
}
Ok 'Nenhum config.properties no pacote'

Write-Host "`n===========================================================" -ForegroundColor Green
Write-Host " Pacote pronto" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host @"

  Pasta:  $destino

  Leve a PASTA INTEIRA para a maquina do cliente, copie para o
  disco (ex: C:\Fantineli) e clique em INSTALAR.bat.

"@
