@echo off
setlocal enabledelayedexpansion
title Backup - Metalurgica Fantineli

cd /d "%~dp0"

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("config.txt") do (
  set "%%A=%%B"
)

rem pg_dump costuma nao estar no PATH; procura na instalacao padrao.
set "PGDUMP=pg_dump"
where pg_dump >nul 2>&1 || (
  for /d %%D in ("C:\Program Files\PostgreSQL\*") do set "PGDUMP=%%D\bin\pg_dump.exe"
)

set PGPASSWORD=%DATABASE_PASSWORD%

if not exist backups mkdir backups

rem Nome com data no formato ano-mes-dia, para ordenar sozinho na pasta.
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set DT=%%I
set CARIMBO=%DT:~0,4%-%DT:~4,2%-%DT:~6,2%_%DT:~8,2%h%DT:~10,2%

set ARQUIVO=backups\estoque_%CARIMBO%.sql

echo.
echo   Salvando copia em %ARQUIVO% ...

"%PGDUMP%" -U %DATABASE_USER% -h localhost -d estoque_metalurgica -f "%ARQUIVO%"

if errorlevel 1 (
  echo.
  echo   *** O BACKUP FALHOU ***
  echo   Leia a mensagem acima. Nao ignore isto.
  echo.
  pause
  exit /b 1
)

rem Mantem as 30 copias mais recentes; apaga as mais antigas.
for /f "skip=30 delims=" %%F in ('dir /b /o-d backups\estoque_*.sql 2^>nul') do (
  del "backups\%%F"
)

echo   Backup concluido.
echo.
echo   LEMBRE: copie a pasta "backups" para um pendrive ou nuvem
echo   de vez em quando. Backup que fica so nesta maquina nao
echo   protege contra o computador queimar.
echo.
timeout /t 8 >nul
