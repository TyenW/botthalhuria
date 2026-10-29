@echo off
REM ============================================================================
REM  Inicia o Bot Thalhuria a partir de uma COPIA do JAR (pasta run\).
REM  Assim, recompilar o projeto (mvn package) com o bot ligado nao quebra o bot
REM  em execucao. Para aplicar mudancas de codigo: feche o bot e rode de novo.
REM ============================================================================
cd /d "%~dp0"

echo [1/3] Compilando e testando...
call mvn -q -o package
if errorlevel 1 (
    echo Build offline falhou; tentando de novo online para baixar o que faltar...
    call mvn -q package
)
if errorlevel 1 (
    echo.
    echo Build falhou. Corrija os erros acima e tente de novo.
    pause
    exit /b 1
)

echo [2/3] Copiando para run\bot.jar...
if not exist run mkdir run
copy /Y target\botthalhuria-1.0.0.jar run\bot.jar >nul

echo [3/3] Iniciando o bot (logs tambem em logs\bot.log e logs\erros.log)...
java --enable-native-access=ALL-UNNAMED -jar run\bot.jar
pause
