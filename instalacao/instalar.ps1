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

# O sistema e compilado para Java 21: num JVM mais antigo ele nem inicia, e o
# erro (UnsupportedClassVersionError) nao diz nada para quem esta instalando.
# Antes daqui so se conferia se EXISTIA algum java, e uma maquina com Java 17
# no PATH passava direto.
#
# A busca acompanha o iniciar.bat de proposito -- JAVA_HOME, depois o PATH,
# depois as pastas onde os instaladores costumam colocar. Conferir apenas o
# PATH daria falso negativo em maquina que ja tinha outro Java instalado antes.
function VersaoMaiorDoJava($exe) {
    if (-not (Test-Path $exe)) { return 0 }
    $texto = (& $exe -version 2>&1 | ForEach-Object { "$_" }) -join ' '
    # Aceita os dois formatos: "21.0.8" e o antigo "1.8.0_451".
    if ($texto -match '"(\d+)(?:\.(\d+))?') {
        $maior = [int]$Matches[1]
        if ($maior -eq 1 -and $Matches[2]) { $maior = [int]$Matches[2] }
        return $maior
    }
    return 0
}

$candidatos = @()
if ($env:JAVA_HOME) { $candidatos += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$noPath = Get-Command java -ErrorAction SilentlyContinue
if ($noPath) { $candidatos += $noPath.Source }
foreach ($raiz in @("$env:ProgramFiles\Eclipse Adoptium",
                    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
                    "$env:ProgramFiles\Java",
                    "$env:ProgramFiles\Microsoft\jdk")) {
    if (Test-Path $raiz) {
        $candidatos += (Get-ChildItem $raiz -Directory -ErrorAction SilentlyContinue |
                        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' })
    }
}

$javaBom = $null
$maiorEncontrado = 0
foreach ($c in ($candidatos | Where-Object { $_ } | Select-Object -Unique)) {
    $v = VersaoMaiorDoJava $c
    if ($v -gt $maiorEncontrado) { $maiorEncontrado = $v }
    if ($v -ge 21) { $javaBom = $c; break }
}

if (-not $javaBom) {
    if ($maiorEncontrado -gt 0) {
        Falta "Java $maiorEncontrado encontrado, mas o sistema precisa do Java 21."
    } else {
        Falta 'Java nao encontrado.'
    }
    Write-Host @'

    Baixe o Java 21 (JRE ou JDK) em:
      https://adoptium.net/temurin/releases/?version=21

    Escolha: Windows, x64, JRE, instalador .msi
    Instale, FECHE este PowerShell, abra de novo e rode o script novamente.
'@
    exit 1
}
Ok "Java 21 encontrado em $javaBom"
if ($noPath -and (VersaoMaiorDoJava $noPath.Source) -lt 21) {
    Write-Host "    (o 'java' do PATH e uma versao antiga; o iniciar.bat usa o 21 encontrado acima)"
}

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
    $adminNome  = ''
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

# Formato .properties: a contrabarra e caractere de escape, entao ela e o
# unico simbolo que precisa ser dobrado. Os demais (! ^ & %), que quebravam
# a leitura pelo .bat, passam intactos por aqui.
# .Replace e' literal. Com -replace o primeiro argumento e' expressao
# regular, e uma contrabarra sozinha e' padrao invalido: a funcao lancava
# erro para QUALQUER valor, e o arquivo saia com todos os campos em branco.
# O Java le .properties como ISO-8859-1 por especificacao, mas este arquivo
# e gravado em UTF-8: um "a" com acento chegava a aplicacao como dois
# caracteres. No nome e feio; na SENHA do administrador e fatal, porque a
# pessoa digita a senha certa e nunca mais entra.
#
# A saida canonica e a mesma do antigo native2ascii: tudo fora do ASCII
# vira \uXXXX, e o arquivo deixa de depender de qual codificacao o leitor
# assume. A contrabarra continua sendo dobrada, que e o escape do formato.
function Escapar($v) {
    if ($null -eq $v) { return '' }
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $v.ToCharArray()) {
        if ($ch -eq '\') { [void]$sb.Append('\\') }
        elseif ([int]$ch -gt 126) { [void]$sb.AppendFormat('\u{0:x4}', [int]$ch) }
        else { [void]$sb.Append($ch) }
    }
    return $sb.ToString()
}

# Le cada linha "CHAVE=valor" de um .properties. Serve tanto para julgar o
# arquivo que ja estava na maquina quanto para reconferir o que acabamos de
# gravar.
function LerConfig($caminho) {
    $mapa = @{}
    if (Test-Path $caminho) {
        foreach ($linha in (Get-Content $caminho)) {
            if ($linha -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
                $mapa[$Matches[1]] = $Matches[2].Trim()
            }
        }
    }
    return $mapa
}

# Um config.properties PRESENTE nao e um config.properties BOM. A versao
# anterior deste script gravava o arquivo com TODOS os valores em branco
# quando a funcao de escape falhava -- e como aqui so se perguntava se o
# arquivo existia, reinstalar por cima nao consertava nada: o instalador
# dizia "concluido", o iniciar.bat nao subia, e nada na tela ligava as duas
# coisas. Foi exatamente isso que aconteceu na maquina do escritorio.
$obrigatorias = @('JWT_SECRET', 'DATABASE_URL', 'DATABASE_USER', 'DATABASE_PASSWORD')
$existia = Test-Path $arquivoConfig
$atual   = LerConfig $arquivoConfig
$vazias  = @($obrigatorias | Where-Object { -not $atual[$_] })

if ($existia -and $vazias.Count -eq 0) {
    Ok 'Configuracao ja existe e esta completa — mantida (a chave nao pode mudar, ou os logins caem)'
} else {
    if ($existia) {
        Falta ('config.properties estava incompleto — faltava: {0}' -f ($vazias -join ', '))
        Move-Item $arquivoConfig "$arquivoConfig.quebrado" -Force
        Write-Host '    O arquivo antigo virou config.properties.quebrado. Refazendo agora.'
    }

    # A chave so e sorteada de novo se a que estava la nao servir: trocar uma
    # chave boa desloga todo o escritorio sem necessidade nenhuma.
    if ($atual['JWT_SECRET']) {
        $chave = $atual['JWT_SECRET']
        Ok 'Chave de seguranca anterior aproveitada — ninguem sera deslogado'
    } else {
        # Sorteio COM reposicao, um caractere por vez.
        #
        # A versao anterior era "(48..57) + (97..122) | Get-Random -Count 64":
        # o -Count do Get-Random sorteia sem repetir, e a lista tem 36 simbolos
        # (0-9 e a-z). Pedir 64 de um conjunto de 36 devolve 36 e nao reclama --
        # a chave saia com pouco mais da metade do tamanho pretendido, e nada na
        # tela dizia isso. Continua sendo chave forte o bastante para o HMAC256,
        # mas codigo que promete 64 e entrega 36 e' codigo em que nao se confia.
        $alfabeto = [char[]]((48..57) + (97..122))
        $chave = -join (1..64 | ForEach-Object { $alfabeto | Get-Random })
    }

    @"
# Configuracao do sistema. NAO APAGUE ESTE ARQUIVO.
# A chave abaixo assina os logins: se ela mudar, todo mundo e deslogado.
# Lido diretamente pela aplicacao - nao e script, nao execute.
JWT_SECRET=$(Escapar $chave)
DATABASE_URL=jdbc:postgresql://localhost:5432/estoque_metalurgica
DATABASE_USER=postgres
DATABASE_PASSWORD=$(Escapar $senhaPgTexto)
ADMIN_LOGIN=$(Escapar $adminLogin)
ADMIN_SENHA=$(Escapar $adminSenha)
ADMIN_NOME=$(Escapar $adminNome)
"@ | Set-Content $arquivoConfig -Encoding ASCII

    # ASCII e nao UTF8 porque o Set-Content do PowerShell 5.1 escreve BOM em
    # UTF8, e o Java leria esses tres bytes como parte da primeira linha. Hoje
    # a primeira linha e comentario e o BOM passa despercebido -- depender
    # disso e' sorte, e o Escapar ja garante que nao sobra nada fora do ASCII.

    # Conferir o que FOI GRAVADO, e nao o que se pretendia gravar. A
    # instalacao do escritorio passou por "concluida" com o arquivo todo em
    # branco justamente porque ninguem releu o resultado.
    $aindaVazias = @($obrigatorias | Where-Object { -not (LerConfig $arquivoConfig)[$_] })
    if ($aindaVazias.Count -gt 0) {
        Falta ('config.properties saiu incompleto: {0}' -f ($aindaVazias -join ', '))
        Write-Host '    Nao continue: o sistema nao vai subir assim.' -ForegroundColor Red
        exit 1
    }

    Ok 'Configuracao salva em config.properties'
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

# --- 7. O sistema roda sozinho, sem janela aberta ----------------------------
Titulo 'Rodar sozinho, sem depender de janela aberta'

# Antes daqui o sistema era um atalho na pasta Inicializar apontando para o
# iniciar.bat. Duas coisas erradas com isso, e as duas terminam com os outros
# quatro micros do escritorio sem sistema:
#
#  1. Atalho na Inicializar so dispara DEPOIS que alguem faz login. Se a
#     maquina reiniciar de madrugada por causa de atualizacao do Windows, o
#     sistema fica fora do ar ate a primeira pessoa chegar e entrar no
#     Windows. A tarefa abaixo sobe com o computador, sem login.
#  2. A janela preta era o sistema. Qualquer um que a fechasse -- e alguem
#     fecha -- derrubava o escritorio inteiro.
#
# javaw.exe, e nao java.exe: e' o mesmo Java sem console anexado.
$javaw = Join-Path (Split-Path $javaBom -Parent) 'javaw.exe'
if (-not (Test-Path $javaw)) { $javaw = $javaBom }

$nomeServico = 'Sistema - Estoque Fantineli'
$logs = Join-Path $pasta 'logs'
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Path $logs | Out-Null }

# Sem console, o log de arranque nao tem para onde ir -- e' justamente quando
# algo falha que ele faz falta. Vai para arquivo, com rodizio, senao cresce sem
# limite numa maquina que fica ligada o ano todo.
$argumentos = @(
    '-jar', 'estoque.jar',
    '--spring.config.additional-location=file:./config.properties',
    '--server.address=0.0.0.0', '--server.port=8080',
    '--logging.file.name=logs/sistema.log',
    '--logging.logback.rollingpolicy.max-file-size=10MB',
    '--logging.logback.rollingpolicy.max-history=14'
) -join ' '

try {
    $acao = New-ScheduledTaskAction -Execute $javaw -Argument $argumentos -WorkingDirectory $pasta

    # 90 segundos de espera: o PostgreSQL tambem esta subindo nesse momento, e
    # a aplicacao morre no arranque se o banco ainda nao aceita conexao.
    $gatilho = New-ScheduledTaskTrigger -AtStartup
    $gatilho.Delay = 'PT90S'

    # SYSTEM para nao depender de nenhum usuario logado, nem da senha de
    # ninguem -- senha de usuario expira ou muda, e a tarefa para de rodar sem
    # aviso.
    $quem = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

    # ExecutionTimeLimit zero e' obrigatorio: o padrao do Agendador e' matar a
    # tarefa depois de 3 dias. Numa maquina que fica ligada, isso seria o
    # sistema caindo sozinho toda semana, sem explicacao nenhuma.
    $conf = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) `
                                         -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
                                         -MultipleInstances IgnoreNew `
                                         -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                                         -StartWhenAvailable
    Register-ScheduledTask -TaskName $nomeServico -Action $acao -Trigger $gatilho `
        -Principal $quem -Settings $conf -Force `
        -Description 'Sistema de gestao da Metalurgica Fantineli. Sobe com o computador.' | Out-Null
    Ok 'O sistema passa a subir com o computador, sem janela e sem login'
} catch {
    Falta 'Nao consegui registrar o sistema para subir sozinho.'
    Write-Host "    Detalhe: $($_.Exception.Message)"
    Write-Host '    Rode este instalador como ADMINISTRADOR.'
}

# O atalho antigo na pasta Inicializar tem que sair, ou a maquina sobe DUAS
# copias do sistema: a tarefa e o atalho. A segunda morre com "porta 8080 ja
# em uso", e quem estiver olhando vai achar que o sistema nao funciona.
$atalhoVelho = Join-Path ([Environment]::GetFolderPath('Startup')) 'Estoque Fantineli.lnk'
if (Test-Path $atalhoVelho) {
    Remove-Item $atalhoVelho -Force
    Ok 'Atalho antigo da pasta Inicializar removido (viraria uma segunda copia)'
}

# Sobe agora, para nao precisar reiniciar a maquina so por causa disso.
if (Get-ScheduledTask -TaskName $nomeServico -ErrorAction SilentlyContinue) {
    Start-ScheduledTask -TaskName $nomeServico
    Write-Host '    Subindo o sistema... a primeira vez demora, o banco esta sendo criado.'
    $limite = (Get-Date).AddSeconds(180)
    $noAr = $false
    while (-not $noAr -and (Get-Date) -lt $limite) {
        Start-Sleep -Seconds 5
        try {
            $r = Invoke-WebRequest 'http://localhost:8080/' -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { $noAr = $true }
        } catch { }
    }
    if ($noAr) {
        Ok 'Sistema no ar'
    } else {
        Falta 'O sistema nao respondeu em 3 minutos.'
        Write-Host "    Abra $logs\sistema.log e leia o final do arquivo."
    }
}


# --- 8. Backup automatico todo dia -------------------------------------------
Titulo 'Backup automatico'
# Backup que depende de alguem lembrar de clicar num .bat nao acontece. Meio-dia
# de proposito: o computador do escritorio esta ligado nesse horario, enquanto
# uma tarefa de madrugada nunca dispararia numa maquina que passa a noite off.
$nomeTarefa = 'Backup - Estoque Fantineli'
try {
    # Sem "start /min": rodando como SYSTEM nao ha area de trabalho onde
    # minimizar janela, e o backup.bat em modo agendado ja nao abre nada.
    $acao = New-ScheduledTaskAction -Execute 'cmd.exe' `
        -Argument ('/c "' + (Join-Path $pasta 'backup.bat') + '" agendado') `
        -WorkingDirectory $pasta
    $gatilho = New-ScheduledTaskTrigger -Daily -At '12:30'
    # StartWhenAvailable: se a maquina estava desligada na hora marcada, roda
    # assim que ligar, em vez de simplesmente pular o dia.
    $conf = New-ScheduledTaskSettingsSet -StartWhenAvailable `
                                         -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
    # SYSTEM, como o sistema. Registrada no usuario que instalou, a tarefa so
    # dispara enquanto ESSE usuario estiver logado -- e o dia em que ninguem
    # entrar na maquina e' um dia sem copia, sem ninguem ficar sabendo.
    $quemBackup = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName $nomeTarefa -Action $acao -Trigger $gatilho `
        -Principal $quemBackup -Settings $conf -Force `
        -Description 'Copia diaria do banco do sistema de gestao da Metalurgica Fantineli.' | Out-Null
    Ok 'Backup agendado para todo dia as 12:30'
} catch {
    Falta 'Nao consegui agendar o backup automatico.'
    Write-Host '    Rode este script como ADMINISTRADOR, ou agende o backup.bat'
    Write-Host '    manualmente no Agendador de Tarefas do Windows.'
}

# Roda um backup agora mesmo. Descobrir que o backup nao funciona com o
# instalador ainda aberto e' barato; descobrir dentro de seis meses, quando
# precisar dele, nao tem conserto.
Titulo 'Conferindo o backup agora'
& (Join-Path $pasta 'backup.bat') agendado | Out-Null
if ($LASTEXITCODE -eq 0) {
    $copia = Get-ChildItem (Join-Path $pasta 'backups\estoque_*.sql') -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($copia) {
        Ok ('Copia de teste gerada: {0} ({1:N0} KB)' -f $copia.Name, ($copia.Length / 1KB))
    } else {
        Falta 'O backup disse que deu certo, mas nao encontrei o arquivo gerado.'
    }
} else {
    Falta 'O backup de teste FALHOU — veja backups\historico.txt e a mensagem acima.'
    Write-Host '    Nao considere a instalacao pronta sem resolver isto.'
}
# --- Fim ---------------------------------------------------------------------
$ipLocal = (Get-NetIPAddress -AddressFamily IPv4 |
            Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.*' } |
            Select-Object -First 1).IPAddress

Write-Host "`n===========================================================" -ForegroundColor Green
Write-Host " Instalacao concluida" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host @"

  O sistema JA ESTA RODANDO, e volta sozinho toda vez que o
  computador ligar. Nao existe mais janela para deixar aberta,
  nem janela que alguem possa fechar por engano.

  Nesta maquina:     http://localhost:8080
  Nos outros micros: http://${ipLocal}:8080

  Deixe esse segundo endereco anotado num papel no monitor.

  PARA LIGAR, DESLIGAR OU CONFERIR: clique em SISTEMA.bat.
  E de la tambem que se le o que o sistema andou dizendo, quando
  alguem reclamar que parou.

  BACKUP — roda sozinho todo dia as 12:30. Para conferir se esta
  acontecendo, abra backups\historico.txt: e uma linha por dia.

  Isso NAO protege contra o computador queimar. Copie a pasta
  "backups" para um pendrive ou nuvem de tempos em tempos.

"@
