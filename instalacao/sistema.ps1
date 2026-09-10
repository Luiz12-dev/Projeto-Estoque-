# =============================================================================
#  Painel do sistema - Metalurgica Fantineli
#
#  Chamado pelo SISTEMA.bat. Nao clique neste arquivo direto.
#
#  Este painel existe por causa do que a tarefa agendada tirou. Enquanto o
#  sistema era uma janela preta aberta, a janela respondia sozinha as tres
#  perguntas que alguem faz quando o telefone toca: esta ligado? qual o
#  endereco? o que ele disse antes de parar? Sem janela nenhuma, alguem
#  precisa responder isso -- e nao pode ser "abra o Agendador de Tarefas do
#  Windows", que nao e' instrucao que se passe por telefone.
#
#  Sem acentos de proposito: o console do Windows nao usa UTF-8 por padrao, e
#  acento aqui vira caractere quebrado na tela de quem le.
# =============================================================================

$ErrorActionPreference = 'Continue'
$pasta   = $PSScriptRoot
$tarefa  = 'Sistema - Estoque Fantineli'
$log     = Join-Path $pasta 'logs\sistema.log'
$endereco = 'http://localhost:8080'

function NoAr {
    try {
        $r = Invoke-WebRequest $endereco -UseBasicParsing -TimeoutSec 3
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

function EstadoDaTarefa {
    $t = Get-ScheduledTask -TaskName $tarefa -ErrorAction SilentlyContinue
    if (-not $t) { return 'nao instalada' }
    return $t.State.ToString()
}

function IpDaRede {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
           Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.*' } |
           Select-Object -First 1).IPAddress
    if ($ip) { return $ip } else { return '(rede nao encontrada)' }
}

function UltimoBackup {
    $h = Join-Path $pasta 'backups\historico.txt'
    if (-not (Test-Path $h)) { return 'nenhum ainda' }
    $linha = Get-Content $h -Tail 1
    if (-not $linha) { return 'nenhum ainda' }
    return $linha.Trim()
}

# Espera o sistema responder. Devolve $true se respondeu dentro do prazo.
function EsperarSubir($segundos) {
    $limite = (Get-Date).AddSeconds($segundos)
    Write-Host -NoNewline '   aguardando'
    while ((Get-Date) -lt $limite) {
        Start-Sleep -Seconds 4
        Write-Host -NoNewline '.'
        if (NoAr) { Write-Host ' pronto'; return $true }
    }
    Write-Host ' nao respondeu'
    return $false
}

function Desligar {
    Write-Host "`n   Desligando..." -ForegroundColor Yellow
    Stop-ScheduledTask -TaskName $tarefa -ErrorAction SilentlyContinue
    # Stop-ScheduledTask nem sempre derruba o java junto; sem isto, a porta
    # 8080 fica ocupada e o proximo "ligar" morre com "porta ja em uso".
    Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*estoque.jar*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
    if (NoAr) { Write-Host '   Ainda respondendo. Tente de novo.' -ForegroundColor Red }
    else      { Write-Host '   Desligado.' -ForegroundColor Green }
}

function Ligar {
    if (NoAr) { Write-Host "`n   O sistema ja esta no ar." -ForegroundColor Green; return }
    Write-Host "`n   Ligando..." -ForegroundColor Yellow
    Start-ScheduledTask -TaskName $tarefa -ErrorAction SilentlyContinue
    if (EsperarSubir 180) {
        Write-Host '   Sistema no ar.' -ForegroundColor Green
    } else {
        Write-Host '   O sistema nao subiu. As ultimas mensagens dele:' -ForegroundColor Red
        VerLog 15
    }
}

function VerLog($quantas) {
    Write-Host ''
    if (-not (Test-Path $log)) {
        Write-Host '   Ainda nao existe arquivo de log.' -ForegroundColor Yellow
        Write-Host '   Isso quer dizer que o sistema nunca chegou a subir por aqui.'
        return
    }
    Write-Host "   Ultimas $quantas linhas de logs\sistema.log:" -ForegroundColor Cyan
    Write-Host '   -----------------------------------------------------------'
    Get-Content $log -Tail $quantas | ForEach-Object {
        # Erro em vermelho: quem esta lendo isso as pressas precisa achar a
        # linha que importa sem ler as outras catorze.
        if ($_ -match ' ERROR | WARN |Caused by|Exception') { Write-Host "   $_" -ForegroundColor Red }
        else { Write-Host "   $_" -ForegroundColor DarkGray }
    }
    Write-Host '   -----------------------------------------------------------'
}

# --- Tela ---------------------------------------------------------------------
while ($true) {
    Clear-Host
    $ar     = NoAr
    $estado = EstadoDaTarefa

    if ($ar)                          { $situacao = 'NO AR';              $cor = 'Green' }
    elseif ($estado -eq 'Running')    { $situacao = 'SUBINDO - aguarde';  $cor = 'Yellow' }
    elseif ($estado -eq 'nao instalada') { $situacao = 'NAO INSTALADO';   $cor = 'Red' }
    else                              { $situacao = 'PARADO';             $cor = 'Red' }

    Write-Host ''
    Write-Host '  ===========================================================' -ForegroundColor White
    Write-Host '   SISTEMA DE GESTAO - METALURGICA FANTINELI' -ForegroundColor White
    Write-Host '  ===========================================================' -ForegroundColor White
    Write-Host ''
    Write-Host -NoNewline '   Situacao ......  '
    Write-Host $situacao -ForegroundColor $cor
    Write-Host "   Neste micro ...  $endereco"
    Write-Host "   Nos outros ....  http://$(IpDaRede):8080"
    Write-Host "   Ultimo backup .  $(UltimoBackup)"
    Write-Host ''
    Write-Host '  -----------------------------------------------------------'
    Write-Host '   1   Abrir o sistema no navegador'
    Write-Host '   2   Reiniciar o sistema'
    Write-Host '   3   Desligar o sistema'
    Write-Host '   4   Ligar o sistema'
    Write-Host '   5   Ver o que o sistema andou dizendo'
    Write-Host '   6   Fazer uma copia de seguranca agora'
    Write-Host '   0   Sair'
    Write-Host '  -----------------------------------------------------------'
    Write-Host ''
    $escolha = Read-Host '   Escolha e tecle Enter'

    switch ($escolha) {
        '1' { Start-Process $endereco }
        '2' { Desligar; Ligar; Write-Host ''; Read-Host '   Tecle Enter' | Out-Null }
        '3' { Desligar; Write-Host ''; Read-Host '   Tecle Enter' | Out-Null }
        '4' { Ligar;    Write-Host ''; Read-Host '   Tecle Enter' | Out-Null }
        '5' { VerLog 25; Write-Host ''; Read-Host '   Tecle Enter' | Out-Null }
        '6' {
              Write-Host "`n   Copiando o banco..." -ForegroundColor Yellow
              & (Join-Path $pasta 'backup.bat') agendado | Out-Null
              if ($LASTEXITCODE -eq 0) { Write-Host "   Pronto. $(UltimoBackup)" -ForegroundColor Green }
              else { Write-Host '   A copia FALHOU. Veja backups\historico.txt' -ForegroundColor Red }
              Write-Host ''; Read-Host '   Tecle Enter' | Out-Null
            }
        '0' { exit 0 }
        default { }
    }
}
