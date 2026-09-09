@echo off
rem SEM expansao atrasada de proposito: com ela, senha que contenha ! ou ^
rem seria lida errada e o backup falharia por autenticacao.
setlocal
title Backup - Metalurgica Fantineli

rem Chamado pelo Agendador de Tarefas com o argumento "agendado". Nesse modo
rem nao pode haver pause nem espera: um backup agendado que trava fica preso
rem para sempre, sem ninguem ver, e a copia do dia nunca acontece.
set "AGENDADO="
if /i "%~1"=="agendado" set "AGENDADO=1"

cd /d "%~dp0"

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("config.properties") do (
  set "%%A=%%B"
)

rem pg_dump costuma nao estar no PATH; procura na instalacao padrao.
set "PGDUMP=pg_dump"
where pg_dump >nul 2>&1 || (
  for /d %%D in ("C:\Program Files\PostgreSQL\*") do set "PGDUMP=%%D\bin\pg_dump.exe"
)

set PGPASSWORD=%DATABASE_PASSWORD%

if not exist backups mkdir backups

rem O wmic foi REMOVIDO do Windows 11 — usa-lo aqui fazia o carimbo sair vazio,
rem e todo backup gravava por cima do anterior, deixando uma copia so. O
rem PowerShell existe em qualquer Windows desde o 7, e -Format nao depende do
rem formato de data configurado na maquina.
for /f "delims=" %%I in ('powershell -NoProfile -Command Get-Date -Format yyyy-MM-dd_HHmm') do set "CARIMBO=%%I"

if not defined CARIMBO (
  echo.
  echo   *** NAO CONSEGUI DESCOBRIR A DATA ***
  echo   Sem isso o backup gravaria por cima do anterior. Abortando.
  echo.
  if not defined AGENDADO pause
  exit /b 1
)

rem CARIMBO tem formato fixo aaaa-mm-dd_hhmm, entao da para montar a data
rem legivel por posicao, sem depender de mais nenhuma chamada externa.
set QUANDO=%CARIMBO:~8,2%/%CARIMBO:~5,2%/%CARIMBO:~0,4% %CARIMBO:~11,2%:%CARIMBO:~13,2%

set ARQUIVO=backups\estoque_%CARIMBO%.sql

echo.
echo   Salvando copia em %ARQUIVO% ...

"%PGDUMP%" -U %DATABASE_USER% -h localhost -d estoque_metalurgica -f "%ARQUIVO%"

if errorlevel 1 (
  rem Uma linha por execucao, em arquivo unico: e' onde se olha para saber se
  rem o backup automatico esta mesmo acontecendo, sem depender de alguem ter
  rem visto a janela passar.
  echo %QUANDO%  FALHOU  - conferir se o PostgreSQL esta ligado e a senha em config.properties>>backups\historico.txt
  echo.
  echo   *** O BACKUP FALHOU ***
  echo   Leia a mensagem acima. Nao ignore isto.
  echo.
  if not defined AGENDADO pause
  exit /b 1
)

rem Mantem as 30 copias mais recentes; apaga as mais antigas.
for /f "skip=30 delims=" %%F in ('dir /b /o-d backups\estoque_*.sql 2^>nul') do (
  del "backups\%%F"
)

echo %QUANDO%  ok      - %ARQUIVO%>>backups\historico.txt

echo   Backup concluido.
echo.
echo   LEMBRE: copie a pasta "backups" para um pendrive ou nuvem
echo   de vez em quando. Backup que fica so nesta maquina nao
echo   protege contra o computador queimar.
echo.
if not defined AGENDADO timeout /t 8 >nul
