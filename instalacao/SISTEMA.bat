@echo off
setlocal
title Sistema de Gestao - Metalurgica Fantineli

cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  Ligar, desligar e conferir o sistema. Este e' o arquivo para clicar no
rem  dia a dia -- o INSTALAR.bat so e' usado uma vez.
rem
rem  Precisa de administrador porque a tarefa que roda o sistema pertence ao
rem  SYSTEM: sem elevacao da' para VER a situacao, mas nao para ligar nem
rem  desligar, e um painel que mostra o botao e nao obedece e' pior do que
rem  nao ter botao.
rem ---------------------------------------------------------------------------

if not exist "sistema.ps1" (
  echo.
  echo   Nao encontrei o sistema.ps1 nesta pasta.
  echo   Este arquivo precisa ficar junto com o resto da instalacao.
  echo.
  pause
  exit /b 1
)

net session >nul 2>&1
if errorlevel 1 (
  set "ESTE=%~f0"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath $env:ESTE -Verb RunAs"
  exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sistema.ps1"
exit /b %ERRORLEVEL%
