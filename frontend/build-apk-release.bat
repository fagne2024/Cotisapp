@echo off
REM ══════════════════════════════════════════════════════════
REM  Script APK Release signé (pour publication Play Store)
REM  Créer le keystore d'abord avec generate-keystore.bat
REM ══════════════════════════════════════════════════════════

echo.
echo  ===  BUILD APK RELEASE CotisApp  ===
echo.

REM -- Vérification du keystore
if not exist cotisapp-release.keystore (
    echo ERREUR : Keystore introuvable.
    echo Lancez d'abord : generate-keystore.bat
    exit /b 1
)

REM -- 1. Build Angular production
echo [1/4] Build Angular production...
call npx ng build --configuration production
if errorlevel 1 (echo ERREUR Angular. & exit /b 1)

REM -- 2. Sync Capacitor
echo [2/4] Sync Capacitor...
call npx cap sync android
if errorlevel 1 (echo ERREUR Sync. & exit /b 1)

REM -- 3. Build Release signé
echo [3/4] Build APK release signe...
cd android
call gradlew.bat assembleRelease ^
  -PMYAPP_RELEASE_STORE_FILE=..\cotisapp-release.keystore ^
  -PMYAPP_RELEASE_STORE_PASSWORD=cotisapp2026 ^
  -PMYAPP_RELEASE_KEY_ALIAS=cotisapp ^
  -PMYAPP_RELEASE_KEY_PASSWORD=cotisapp2026
if errorlevel 1 (echo ERREUR Gradle. & cd .. & exit /b 1)
cd ..

echo.
echo [4/4] APK Release genere !
echo.
echo  Emplacement :
echo  android\app\build\outputs\apk\release\app-release.apk
echo.
pause
