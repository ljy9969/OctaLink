@echo off
setlocal

rem OctaLink - release keystore generator
rem Generates app/octalink-release.jks (gitignored) using keytool.
rem Run once. NEVER commit the resulting .jks file.

set KEYSTORE_PATH=app\octalink-release.jks
set KEY_ALIAS=octalink-release
set DNAME=CN=OctaLink, OU=BlackCat Strike, O=Unbound Apex Systems, L=Seoul, C=KR
set VALIDITY_DAYS=10000

if exist "%KEYSTORE_PATH%" (
    echo [ERROR] Keystore already exists at %KEYSTORE_PATH%
    echo Refusing to overwrite. Delete it manually if you really want to regenerate.
    exit /b 1
)

where keytool >nul 2>nul
if errorlevel 1 (
    echo [ERROR] keytool not found in PATH.
    echo Install JDK 17+ or add %%JAVA_HOME%%\bin to PATH.
    exit /b 1
)

echo.
echo Generating release keystore: %KEYSTORE_PATH%
echo Alias: %KEY_ALIAS%
echo Validity: %VALIDITY_DAYS% days
echo.
echo You will be asked for:
echo   - keystore password (8+ chars, REMEMBER THIS)
echo   - key password (can be same as keystore password)
echo.

keytool -genkeypair -v ^
    -keystore "%KEYSTORE_PATH%" ^
    -alias "%KEY_ALIAS%" ^
    -keyalg RSA ^
    -keysize 2048 ^
    -validity %VALIDITY_DAYS% ^
    -dname "%DNAME%"

if errorlevel 1 (
    echo.
    echo [ERROR] keytool failed.
    exit /b 1
)

echo.
echo [OK] Keystore created at %KEYSTORE_PATH%
echo.
echo Next steps:
echo   1. Add the following lines to local.properties (gitignored):
echo        RELEASE_KEYSTORE_FILE=app/octalink-release.jks
echo        RELEASE_KEYSTORE_PASSWORD=^<keystore password you just typed^>
echo        RELEASE_KEY_ALIAS=%KEY_ALIAS%
echo        RELEASE_KEY_PASSWORD=^<key password you just typed^>
echo.
echo   2. BACK UP %KEYSTORE_PATH% to a secure location (password manager, encrypted drive).
echo      If you lose this file, you can NEVER update this app on Play Store.
echo.
echo   3. Build a signed release:
echo        gradlew :app:assembleRelease
echo        gradlew :app:bundleRelease   (.aab for Play Console)
echo.

endlocal
