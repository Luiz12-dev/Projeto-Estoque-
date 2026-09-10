@echo off
setlocal
title Instalacao - Metalurgica Fantineli

cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  Este arquivo existe por causa de duas travas do Windows que barraram a
rem  instalacao na maquina do escritorio. Nenhuma das duas tem a ver com o
rem  sistema, e as duas terminam do mesmo jeito: a pessoa na frente do micro
rem  nao tem como saber o que fazer.
rem
rem  1) Botao direito em instalar.ps1 > "Executar com o PowerShell" esbarra na
rem     politica de execucao de scripts. A alternativa era mandar alguem digitar
rem     Set-ExecutionPolicy num PowerShell aberto como administrador, que nao e
rem     instrucao que se passe por telefone.
rem  2) Sem privilegio de administrador o instalador nao libera o firewall nem
rem     agenda o backup -- e termina dizendo "concluido" com metade do servico
rem     feito.
rem
rem  Um .bat nao passa pela politica de execucao. Entao ele pede a elevacao e
rem  chama o PowerShell com as duas coisas ja resolvidas.
rem ---------------------------------------------------------------------------

if not exist "instalar.ps1" (
  echo.
  echo   Nao encontrei o instalar.ps1 nesta pasta.
  echo.
  rem Sem parenteses nesta mensagem de proposito: dentro de um bloco "if ^(",
  rem um ^) solto no meio de um echo FECHA o bloco ali mesmo. O efeito era a
  rem mensagem sair pela metade e as linhas seguintes rodarem sempre, mesmo
  rem quando a condicao era falsa.
  echo   Copie a PASTA INTEIRA para o disco - por exemplo C:\Fantineli -
  echo   antes de rodar. Nao rode direto do pendrive.
  echo.
  pause
  exit /b 1
)

if not exist "estoque.jar" (
  echo.
  echo   Nao encontrei o estoque.jar nesta pasta.
  echo.
  echo   Este pacote esta incompleto: faltou o programa em si. Peca o
  echo   pacote completo para quem te passou este arquivo.
  echo.
  pause
  exit /b 1
)

rem "net session" so responde com sucesso quando ja se e administrador.
net session >nul 2>&1
if errorlevel 1 (
  echo.
  echo   Este instalador precisa de permissao de administrador para
  echo   liberar a rede e agendar o backup.
  echo.
  echo   Aceite a janela que o Windows vai abrir agora.
  echo.
  rem O caminho vai por variavel de ambiente, nao dentro das aspas do comando:
  rem o ambiente e' entregue ao processo filho em Unicode, enquanto texto na
  rem linha de comando ainda passa pela pagina de codigo do console. Numa pasta
  rem com acento no nome -- "Area de Trabalho", por exemplo -- e' a diferenca
  rem entre abrir e nao encontrar o arquivo.
  set "ESTE=%~f0"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath $env:ESTE -Verb RunAs"
  exit /b 0
)

echo.
echo   ===========================================================
echo    METALURGICA FANTINELI - Sistema de Gestao
echo    Instalacao na maquina do escritorio
echo   ===========================================================
echo.

set "SCRIPT=%~dp0instalar.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "RESULTADO=%ERRORLEVEL%"

if not "%RESULTADO%"=="0" (
  echo.
  echo   ===========================================================
  echo    A INSTALACAO NAO TERMINOU
  echo   ===========================================================
  echo.
  echo   Leia a mensagem em amarelo acima: ela diz o que falta.
  echo   Resolva e clique neste arquivo de novo -- rodar duas vezes
  echo   e seguro, o banco e os usuarios sao preservados.
  echo.
)

pause
exit /b %RESULTADO%
