@echo off
REM Root Tools auf das per USB angesteckte Nord 3 spielen und starten.
REM Voraussetzung: USB-Debugging an, Kabel dran, Abfrage am Handy bestaetigt.
REM -d = das USB-Geraet (das Nord 3 haengt sonst oft zusaetzlich per WLAN-ADB dran).
setlocal
set ADB=A:\Android\Sdk\platform-tools\adb.exe
set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk

if not exist "%APK%" (
  echo [!] Keine APK gefunden. Erst bauen:
  echo     cd /d "%~dp0" ^&^& gradlew.bat assembleDebug
  exit /b 1
)

echo Warte auf Geraet...
"%ADB%" -d wait-for-device

echo Installiere...
"%ADB%" -d install -r "%APK%" || exit /b 1

echo Starte...
"%ADB%" -d shell am start -n de.tobidervogel.roottools/.MainActivity
echo Fertig. Beim ersten Start fragt Magisk nach Root: erlauben.
