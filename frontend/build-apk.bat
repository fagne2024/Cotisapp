@echo off
REM ══════════════════════════════════════════════════════════
REM  Script de génération de l'APK CotisApp
REM  Prérequis : Android Studio + Android SDK installés
REM              Variable ANDROID_HOME configurée
REM ══════════════════════════════════════════════════════════

echo.
echo  ===  BUILD APK CotisApp  ===
echo.

REM -- 1. Build Angular en production
echo [1/4] Build Angular production...
call npx ng build --configuration production
if errorlevel 1 (
    echo ERREUR lors du build Angular.
    exit /b 1
)
echo  OK

REM -- 2. Synchroniser Capacitor
echo [2/4] Sync Capacitor...
call npx cap sync android
if errorlevel 1 (
    echo ERREUR lors du sync Capacitor.
    exit /b 1
)
echo  OK

REM -- 3. Build APK debug (pas besoin de keystore)
echo [3/4] Build APK debug...
cd android
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERREUR lors du build Gradle.
    cd ..
    exit /b 1
)
cd ..
echo  OK

REM -- 4. Afficher l'emplacement de l'APK
echo.
echo [4/4] APK genere avec succes !
echo.
echo  Emplacement :
echo  android\app\build\outputs\apk\debug\app-debug.apk
echo.
echo  Pour l'installer sur un telephone connecte en USB :
echo  adb install android\app\build\outputs\apk\debug\app-debug.apk
echo.
pause
