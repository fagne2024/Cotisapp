@echo off
REM ══════════════════════════════════════════════════════════
REM  Génère le keystore de signature pour l'APK Release
REM  A FAIRE UNE SEULE FOIS — gardez ce fichier en sécurité !
REM ══════════════════════════════════════════════════════════

echo.
echo  ===  GENERATION KEYSTORE CotisApp  ===
echo.
echo  ATTENTION : Ce keystore est unique et ne peut pas etre recree.
echo  Conservez-le dans un endroit securise (hors du depot Git).
echo.

"C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\keytool.exe" ^
  -genkey -v ^
  -keystore cotisapp-release.keystore ^
  -alias cotisapp ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storepass cotisapp2026 ^
  -keypass cotisapp2026 ^
  -dname "CN=Intouch CotisApp, OU=Mobile, O=Intouch, L=Dakar, S=Dakar, C=SN"

if errorlevel 1 (
    echo ERREUR lors de la generation du keystore.
) else (
    echo.
    echo  Keystore genere : cotisapp-release.keystore
    echo  IMPORTANT : Ajoutez ce fichier a .gitignore !
)
echo.
pause
