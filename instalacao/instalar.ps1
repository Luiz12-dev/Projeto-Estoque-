# =============================================================================
#  Instalacao — Sistema de Gestao, Metalurgica Fantineli
#
#  Rode este arquivo com o botao direito > "Executar com o PowerShell",
#  ou abra o PowerShell COMO ADMINISTRADOR e execute:
#
#      .\instalar.ps1
#
#  O script confere o que falta, cria o banco, gera a chave de seguranca e
#  deixa o sistema pronto para subir. Ele nao instala Java nem PostgreSQL
#  sozinho: se faltar, avisa e diz onde baixar.
# =============================================================================

# Atencao ao PowerShell 5.1: com ErrorActionPreference = 'Stop', qualquer coisa
# que um programa externo escreva no stderr vira erro fatal — inclusive avisos
# inofensivos do compilador. Por isso as falhas aqui sao conferidas pelo
# codigo de saida ($LASTEXITCODE), que e o sinal confiavel.
$ErrorActionPreference = 'Continue'
$pasta = $PSScriptRoot

function Titulo($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Ok($t)     { Write-Host "  [ok] $t" -ForegroundColor Green }
function Falta($t)  { Write-Host "  [!!] $t" -ForegroundColor Yellow }

Write-Host @'

  ===========================================================
   METALURGICA FANTINELI — Sistema de Gestao
   Instalacao na maquina do escritorio
  ===========================================================
'@ -ForegroundColor White

# --- 1. Java -----------------------------------------------------------------
Titulo 'Java'
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Falta 'Java nao encontrado.'
    Write-Host @'

    Baixe o Java 21 (JRE ou JDK) em:
      https://adoptium.net/temurin/releases/?version=21

    Escolha: Windows, x64, JRE, instalador .msi
    Instale, FECHE este PowerShell, abra de novo e rode o script novamente.
'@
    exit 1
}
$versao = (& java -version 2>&1)[0]
Ok "Java presente — $versao"

# --- 2. PostgreSQL -----------------------------------------------------------
Titulo 'PostgreSQL'
$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    $provavel = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
                Select-Object -Last 1
    if ($provavel) {
        $env:Path += ";$($provavel.Directory)"
        $psql = Get-Command psql -ErrorAction SilentlyContinue
        Ok "PostgreSQL encontrado em $($provavel.Directory)"
    }
}
if (-not $psql) {
    Falta 'PostgreSQL nao encontrado.'
    Write-Host @'

    Baixe o PostgreSQL 16 em:
      https://www.postgresql.org/download/windows/

    Durante a instalacao:
      - ANOTE a senha do usuario "postgres" — voce vai precisar dela agora
      - Deixe a porta em 5432
      - Pode desmarcar o "Stack Builder" no final

    Depois instale, FECHE este PowerShell, abra de novo e rode novamente.
'@
    exit 1
}
Ok 'PostgreSQL presente'

# --- 3. Banco de dados -------------------------------------------------------
Titulo 'Banco de dados'
$senhaPg = Read-Host 'Senha do usuario "postgres" do PostgreSQL' -AsSecureString
$senhaPgTexto = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($senhaPg))
$env:PGPASSWORD = $senhaPgTexto

$existe = & psql -U postgres -h localhost -tAc `
    "SELECT 1 FROM pg_database WHERE datname='estoque_metalurgica'" 2>&1
if ($LASTEXITCODE -ne 0) {
    Falta "Nao consegui conectar no PostgreSQL. Senha errada, ou o servico nao esta rodando."
    Write-Host "    Detalhe: $existe"
    exit 1
}
if ($existe -eq '1') {
    Ok 'Banco "estoque_metalurgica" ja existe — mantido como esta'
} else {
    & psql -U postgres -h localhost -c 'CREATE DATABASE estoque_metalurgica' | Out-Null
    Ok 'Banco "estoque_metalurgica" criado'
}
# As tabelas sao criadas pela propria aplicacao na primeira subida (Flyway).

# --- 4. Primeiro administrador ----------------------------------------------
Titulo 'Primeiro acesso'
$temUsuario = & psql -U postgres -h localhost -d estoque_metalurgica -tAc `
    "SELECT COUNT(*) FROM usuario" 2>$null
if ($LASTEXITCODE -eq 0 -and [int]$temUsuario -gt 0) {
    Ok "Ja existem $temUsuario usuario(s) — o login atual continua valendo"
    $adminLogin = ''
    $adminSenha = ''
} else {
    Write-Host '  Vamos criar o administrador do sistema.'
    $adminLogin = Read-Host '  Login (ex: leo)'
    $s = Read-Host '  Senha' -AsSecureString
    $adminSenha = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
    $adminNome  = Read-Host '  Nome completo'
    if (-not $adminLogin -or -not $adminSenha) {
        Falta 'Login e senha sao obrigatorios.'
        exit 1
    }
    Ok "Administrador '$adminLogin' sera criado na primeira subida"
}

# --- 5. Chave de seguranca ---------------------------------------------------
Titulo 'Chave de seguranca'
$arquivoConfig = Join-Path $pasta 'config.properties'
if (Test-Path $arquivoConfig) {
    Ok 'Configuracao ja existe — mantida (a chave nao pode mudar, ou os logins caem)'
} else {
    $chave = -join ((48..57) + (97..122) | Get-Random -Count 64 | ForEach-Object { [char]$_ })
    $ip = (Get-NetIPAddress -AddressFamily IPv4 |
           Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.*' } |
           Select-Object -First 1).IPAddress

    # Formato .properties: a contrabarra e caractere de escape, entao ela e o
    # unico simbolo que precisa ser dobrado. Os demais (! ^ & %), que quebravam
    # a leitura pelo .bat, passam intactos por aqui.
    function Escapar($v) { if ($null -eq $v) { '' } else { $v -replace '\', '\\' } }

    @"
# Configuracao do sistema. NAO APAGUE ESTE ARQUIVO.
# A chave abaixo assina os logins: se ela mudar, todo mundo e deslogado.
# Lido diretamente pela aplicacao — nao e script, nao execute.
JWT_SECRET=$(Escapar $chave)
DATABASE_URL=jdbc:postgresql://localhost:5432/estoque_metalurgica
DATABASE_USER=postgres
DATABASE_PASSWORD=$(Escapar $senhaPgTexto)
ADMIN_LOGIN=$(Escapar $adminLogin)
ADMIN_SENHA=$(Escapar $adminSenha)
ADMIN_NOME=$(Escapar $adminNome)
"@ | Set-Content $arquivoConfig -Encoding UTF8

    Ok 'Chave gerada e configuracao salva em config.txt'
    Falta 'Esse arquivo tem senhas. Nao mande por WhatsApp nem e-mail.'
}

# --- 6. Firewall, para o escritorio acessar ---------------------------------
Titulo 'Acesso pela rede do escritorio'
$regra = Get-NetFirewallRule -DisplayName 'Estoque Fantineli' -ErrorAction SilentlyContinue
if ($regra) {
    Ok 'Porta 8080 ja liberada no firewall'
} else {
    try {
        New-NetFirewallRule -DisplayName 'Estoque Fantineli' -Direction Inbound `
            -LocalPort 8080 -Protocol TCP -Action Allow -Profile Domain,Private | Out-Null
        Ok 'Porta 8080 liberada para a rede local'
    } catch {
        Falta 'Nao consegui liberar o firewall — rode este script como ADMINISTRADOR.'
        Write-Host '    Sem isso, so esta maquina consegue abrir o sistema.'
    }
}

# --- 7. Iniciar junto com o Windows -----------------------------------------
Titulo 'Iniciar sozinho com o computador'
$inicializar = [Environment]::GetFolderPath('Startup')
$atalho = Join-Path $inicializar 'Estoque Fantineli.lnk'
$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($atalho)
$lnk.TargetPath = Join-Path $pasta 'iniciar.bat'
$lnk.WorkingDirectory = $pasta
$lnk.WindowStyle = 7   # minimizado
$lnk.Description = 'Sistema de Gestao — Metalurgica Fantineli'
$lnk.Save()
if (Test-Path $atalho) {
    Ok 'O sistema passa a subir sozinho quando o computador liga'
} else {
    Falta 'Nao consegui criar o atalho de inicializacao — inicie manualmente pelo iniciar.bat'
}

# --- Fim ---------------------------------------------------------------------
$ipLocal = (Get-NetIPAddress -AddressFamily IPv4 |
            Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.*' } |
            Select-Object -First 1).IPAddress

Write-Host "`n===========================================================" -ForegroundColor Green
Write-Host " Instalacao concluida" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host @"

  Para ligar agora:  clique duas vezes em iniciar.bat

  Nesta maquina:     http://localhost:8080
  Nos outros micros: http://${ipLocal}:8080

  A primeira subida demora um pouco mais: o sistema cria as
  tabelas sozinho.

  IMPORTANTE — faca o backup rodar. Clique em backup.bat de vez
  em quando, ou peca para configurarem no Agendador de Tarefas.
  Sem isso, se este computador queimar, tudo se perde.

"@
