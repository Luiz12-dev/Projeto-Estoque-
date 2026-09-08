@echo off
setlocal enabledelayedexpansion
title Sistema de Gestao - Metalurgica Fantineli

cd /d "%~dp0"

if not exist config.properties (
  echo.
  echo   Arquivo config.properties nao encontrado.
  echo   Rode instalar.ps1 antes de usar este atalho.
  echo.
  pause
  exit /b 1
)

rem ---------------------------------------------------------------------------
rem  Escolhe o Java. O sistema exige a versao 21.
rem
rem  Isto existe porque Java antigo no PATH e comum em maquina de escritorio, e
rem  a mensagem que a JVM da nesse caso ("class file version 65.0") nao diz a
rem  ninguem que o problema e a versao do Java.
rem ---------------------------------------------------------------------------
set "JAVA_EXE="

rem 1) JAVA_HOME, se apontar para um Java 21 ou mais novo
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    call :versao "%JAVA_HOME%\bin\java.exe"
    if !MAJOR! GEQ 21 set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  )
)

rem 2) o java do PATH
if not defined JAVA_EXE (
  where java >nul 2>&1 && (
    call :versao "java"
    if !MAJOR! GEQ 21 set "JAVA_EXE=java"
  )
)

rem 3) instalacoes conhecidas no disco
if not defined JAVA_EXE (
  for %%P in ("C:\Program Files\Eclipse Adoptium" "C:\Program Files\Java" "C:\Program Files\Microsoft") do (
    if exist %%P (
      for /d %%D in (%%P\*) do (
        if exist "%%D\bin\java.exe" (
          call :versao "%%D\bin\java.exe"
          if !MAJOR! GEQ 21 set "JAVA_EXE=%%D\bin\java.exe"
        )
      )
    )
  )
)

if not defined JAVA_EXE (
  echo.
  echo   ===================================================
  echo    NAO ENCONTREI O JAVA 21
  echo   ===================================================
  echo.
  rem Rele a versao aqui fora, em vez de carregar a informacao por dentro dos
  rem blocos aninhados acima — propagar variavel entre blocos do cmd falha em
  rem silencio e faria a mensagem mentir sobre o que a maquina tem.
  call :versao java
  if "!MAJOR!"=="0" (
    echo    Nenhum Java encontrado nesta maquina.
  ) else (
    echo    O Java desta maquina e a versao !MAJOR!.
    echo    O sistema precisa da 21 ou mais nova.
  )
  echo.
  echo    Baixe em:
  echo      https://adoptium.net/temurin/releases/?version=21
  echo.
  echo    Escolha: Windows, x64, JRE, instalador .msi
  echo    Instale e clique neste arquivo de novo.
  echo.
  echo    Instalar o Java 21 NAO remove o Java antigo: os dois
  echo    convivem, e outros programas continuam funcionando.
  echo.
  pause
  exit /b 1
)

rem ---------------------------------------------------------------------------
rem  Os segredos NAO passam por aqui de proposito.
rem
rem  Ler config.txt com "for /f" e repassar via SET destroi senha que contenha
rem  ! ou ^ (a expansao atrasada come esses caracteres) e quebra o script
rem  inteiro quando ha & ou %. Uma senha forte tem exatamente esses simbolos, e
rem  o efeito seria o pior possivel: o sistema subiria com uma senha diferente
rem  da que a pessoa digitou, e a tela diria apenas "senha invalida".
rem
rem  Quem le o arquivo agora e o proprio Spring, que trata cada linha como
rem  propriedade e nao como comando.
rem ---------------------------------------------------------------------------

echo.
echo   Iniciando o sistema...
echo.
echo   Feche esta janela SOMENTE se quiser desligar o sistema
echo   para todo mundo do escritorio.
echo.

rem --server.address=0.0.0.0 faz o sistema atender os outros micros da rede.
"%JAVA_EXE%" -jar estoque.jar ^
  --spring.config.additional-location=file:./config.properties ^
  --server.address=0.0.0.0 --server.port=8080

echo.
echo   O sistema parou. Leia a mensagem acima.
echo.
pause
exit /b %ERRORLEVEL%

rem ---------------------------------------------------------------------------
rem  :versao — le a versao maior do java informado em %1 e devolve em MAJOR.
rem  Lida com os dois formatos: "21.0.8" e o antigo "1.8.0_451".
rem ---------------------------------------------------------------------------
:versao
rem "tokens=3" precisa das aspas: dentro de um .bat, escapar o = com ^ faz o
rem parser do cmd abortar com "3 foi inesperado neste momento". O escape sem
rem aspas so funciona quando digitado direto no prompt.
rem A primeira linha do java -version ja traz a versao no terceiro token,
rem entao nao ha necessidade de findstr nem de cano.
set "MAJOR=0"
set "V="
for /f "tokens=3" %%V in ('"%~1" -version 2^>^&1') do (
  if not defined V set "V=%%~V"
)
if not defined V goto :eof
if "!V:~0,2!"=="1." (
  set "MAJOR=!V:~2,1!"
) else (
  for /f "tokens=1 delims=." %%M in ("!V!") do set "MAJOR=%%M"
)
goto :eof
