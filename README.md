# Root Tools

Private Android-App für Tobis gerootetes **OnePlus Nord 3** (CPH2493, OxygenOS 16 / Android 16, Magisk).
Sammelt Werkzeuge, die nur mit Root gehen. Keine Berechtigungen, kein Internet: alles läuft über `su`.

## Werkzeuge

### MAC-Adresse

Eigene WLAN-MAC statt Androids Zufalls-MAC. Eintippen oder würfeln, „Übernehmen“, fertig. Gilt für alle WLANs.

So funktioniert es (im Android-16-Quelltext von `packages/modules/Wifi` nachgesehen):

1. Android setzt bei jeder Verbindung und Trennung eine Zufalls-MAC, solange das Overlay
   `config_wifi_connected_mac_randomization_supported` an ist. Root schaltet es zur Laufzeit ab:
   `cmd wifi force-overlay-config-value bool config_wifi_connected_mac_randomization_supported enabled false`.
   Das hält bis zum nächsten Neustart. Erst ab Android 16 wirkt das (Android 15 liest den Wert am Cache vorbei).
2. Danach setzt Root Tools die MAC mit `ifconfig wlan0 down hw ether … up` in **einem** Aufruf. So kurz, dass
   Android den Ausfall nicht als Absturz wertet; der WLAN-HAL macht es bei seiner Zufalls-MAC genauso.
3. Ein kleiner Wächter (Root-Shell im Hintergrund) setzt die MAC wieder, wenn das WLAN neu startet und der
   Treiber die Geräte-MAC zurückbringt. Er gibt auf, wenn sie 5-mal in 2 Minuten überschrieben wird, damit das WLAN
   nicht endlos trennt.
4. „Nach Neustart behalten“ legt `/data/adb/service.d/roottools-mac.sh` an: Magisk startet den Wächter dann beim
   Hochfahren.

Beim Setzen trennt das WLAN kurz und verbindet sich mit der neuen MAC neu (neue IP vom Router).
„Zurücksetzen“ schaltet alles ab und startet das WLAN neu, dann würfelt Android wieder.

Dateien auf dem Handy:

| Datei | Inhalt |
|---|---|
| `/data/adb/roottools/mac.sh` | Das Skript aus `app/src/main/assets/mac.sh`, bei jedem Aufruf frisch kopiert |
| `/data/adb/roottools/mac.conf` | Die eigene MAC (`MAC=…`). Fehlt sie, beendet sich der Wächter |
| `/data/adb/roottools/mac.log` | Protokoll (die App zeigt die letzten Zeilen) |
| `/data/adb/service.d/roottools-mac.sh` | Startet den Wächter beim Hochfahren |

Komplett entfernen, falls die App weg ist: `su -c 'rm -rf /data/adb/roottools /data/adb/service.d/roottools-mac.sh'`
und neu starten.

Noch nicht auf dem Nord 3 ausprobiert (Stand 25.10.2026): ob OxygenOS den Befehl `force-overlay-config-value`
mitbringt und ob der MediaTek-Treiber die Adresse annimmt. Die App zeigt beides an („Android-Schalter fehlt“,
Protokoll).

## Bauen und installieren

Gleiche Werkzeuge wie Live Up (JDK 17+, Android SDK 34, Gradle 8.7, AGP 8.4.1, Kotlin 2.3).

```
gradlew.bat assembleDebug
installieren.cmd
```

Tests: `gradlew.bat testDebugUnitTest` (MAC-Prüfung, Zustand lesen) und auf Linux/WSL
`sh app/src/test/shell/mac_test.sh mksh` (das Root-Skript mit nachgebauten Android-Befehlen).

## Neues Werkzeug

Eintrag in `enum class Werkzeug` (`MainActivity.kt`), eigene Seite unter `app/src/main/java/.../<werkzeug>/`,
Root-Befehle gebündelt über `Root.ausfuehren` (jeder Aufruf zeigt einen Magisk-Hinweis).
