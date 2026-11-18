@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

title Gakuu - Generador de APK

where flutter >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No se encontro 'flutter' en el PATH del sistema.
    pause
    exit /b 1
)

set "MODE=%~1"
if /i "%MODE%"=="debug" goto build_debug
if /i "%MODE%"=="release" goto build_release

:menu
cls
echo ============================================
echo        Gakuu - Generador de APK Android
echo ============================================
echo.
echo   1. Generar APK Debug
echo   2. Generar APK Release
echo   3. Salir
echo.
choice /c 123 /n /m "Selecciona una opcion (1-3): "
if errorlevel 3 exit /b 0
if errorlevel 2 goto build_release
if errorlevel 1 goto build_debug

:build_debug
echo.
echo [*] Compilando APK Debug...
call flutter build apk --debug
if errorlevel 1 goto build_error
set "APK_PATH=%~dp0build\app\outputs\flutter-apk\app-debug.apk"
goto build_success

:build_release
echo.
echo [*] Compilando APK Release...
call flutter build apk --release
if errorlevel 1 goto build_error
set "APK_PATH=%~dp0build\app\outputs\flutter-apk\app-release.apk"
goto build_success

:build_success
echo.
echo ============================================
echo   [OK] APK generado correctamente:
echo   %APK_PATH%
echo ============================================
echo.
choice /c SN /n /m "Deseas abrir la carpeta del APK? (S/N): "
if errorlevel 2 goto end
if exist "%APK_PATH%" (
    explorer /select,"%APK_PATH%"
) else (
    explorer "%~dp0build\app\outputs\flutter-apk"
)
goto end

:build_error
echo.
echo ============================================
echo   [ERROR] Fallo la compilacion del APK.
echo ============================================
echo.
pause
exit /b 1

:end
if "%~1"=="" pause
exit /b 0
