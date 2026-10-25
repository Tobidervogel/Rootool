# Root Tools

Private Android-App für Tobis gerootetes **OnePlus Nord 3** (CPH2493, OxygenOS 16 / Android 16, Magisk).
Sammelt Werkzeuge, die nur mit Root gehen. Alles läuft über `su`; ins Internet geht die App nur, wenn man im
Werbeblocker eine Liste lädt.

Jedes Werkzeug hat ein Shell-Skript unter `app/src/main/assets/`. Die App kopiert es bei jedem Aufruf frisch nach
`/data/adb/roottools/` und ruft es als root auf; die Antwort kommt als `SCHLUESSEL=wert`-Zeilen zurück
(`root/Skript.kt`). Was nach einem Neustart weiterlaufen soll, legt ein Skript unter `/data/adb/service.d/` an.

## Werkzeuge

| Werkzeug | Was es tut | Skript |
|---|---|---|
| **System-Update** | Zeigt Slots und den Zustand der Boot-Abbilder beider Slots (`magiskboot cpio test`). Wartet ein installiertes Update auf den Neustart, sagt die Seite, ob erst Magisk den inaktiven Slot patchen muss oder ob Root den Neustart übersteht. Anleitung Schritt für Schritt, „Lokale Installation freischalten“. | `update.sh` |
| **Notfall-Sicherung** | boot, init_boot, vendor_boot, vbmeta, dtbo, lk (beide Slots) und die MediaTek-Funkdaten (nvram, nvdata, nvcfg, persist, proinfo, protect1/2) nach `RootTools/Notfall/<Datum>`, mit `SHA256SUMS` und Anleitung. Nur lesen. | `update.sh` |
| **App-Sicherung** | Apps mit Daten sichern und zurückspielen. „Neue APK einspielen“ für anders signierte Builds: sichern, deinstallieren, installieren, Daten zurück; scheitert die Installation, kommt die alte App wieder. | `apps.sh` |
| **Firewall** | Apps das Internet sperren, dauerhaft oder nach Zeitplan. Androids eingebaute Sperrliste (`cmd connectivity`, FIREWALL_CHAIN_OEM_DENY_3), kein VPN, läuft neben Tailscale. | `firewall.sh` |
| **Werbeblocker** | hosts-Datei über Magisks „Systemless Hosts“-Modul, Listen von StevenBlack/hosts (auf Wunsch auch soziale Netzwerke), eigene Sperren und Ausnahmen. | `hosts.sh` |
| **MAC-Adresse** | Eigene WLAN-MAC statt Zufalls-MAC. Schaltet Androids Würfeln zur Laufzeit ab (`cmd wifi force-overlay-config-value`, ab Android 16), Wächter setzt die MAC nach WLAN-Neustarts wieder. | `mac.sh` |
| **ADB übers Netz** | ADB auf Port 5555, per iptables nur für Tailscale (100.64.0.0/10), das Heimnetz und localhost; bleibt nach dem Neustart an. Für Claude-Cloud-Sessions und den PC. | `adb.sh` |
| **Logcat** | Meldungen und Abstürze aller Apps, nach App gefiltert, kopieren und teilen. | `logcat.sh` |

Dateien auf dem Handy: `/data/adb/roottools/` (Skripte, Einstellungen, Protokolle), `/data/adb/service.d/roottools-*.sh`
(Start beim Hochfahren), Sicherungen unter `Interner Speicher/RootTools/`. Alles entfernen, falls die App weg ist:

```
su -c 'rm -rf /data/adb/roottools /data/adb/service.d/roottools-*.sh'
```

dann neu starten. Das hosts-Modul (`/data/adb/modules/hosts`) gehört Magisk und lässt sich in Magisk entfernen.

**Noch nicht auf dem Nord 3 ausprobiert** (Stand 25.10.2026). Alle Skripte sind auf dem PC mit nachgebauten
Android-Befehlen getestet, aber Details von OxygenOS (z. B. ob `force-overlay-config-value` da ist, welche
Partitionen es gibt) zeigt erst das Gerät. Die Seiten zeigen Fehler und Protokolle an.

## Bauen und installieren

Gleiche Werkzeuge wie Live Up (JDK 17+, Android SDK 34, Gradle 8.7, AGP 8.4.1, Kotlin 2.3).

```
gradlew.bat assembleDebug
installieren.cmd
```

Beim ersten Start fragt Magisk nach Root. Tipp: In Magisk → Superuser → Root Tools die Benachrichtigung abschalten,
sonst erscheint bei jedem Root-Aufruf ein Hinweis.

## Tests

- `gradlew.bat testDebugUnitTest`: Prüfungen und das Lesen der Skript-Antworten.
- Die Skripte auf Linux/WSL mit nachgebauten Android-Befehlen, jeweils in mksh (Androids Shell), dash und busybox:
  `sh app/src/test/shell/<werkzeug>_test.sh mksh` für `mac`, `update`, `apps`, `firewall`, `hosts`, `adb`, `logcat`.

## Neues Werkzeug

1. Skript `app/src/main/assets/<name>.sh` mit `SCHLUESSEL=wert`-Antworten und Test unter `app/src/test/shell/`.
2. Seite unter `app/src/main/java/de/tobidervogel/roottools/<bereich>/`, Root-Aufrufe über `Skript.aufrufen`.
3. Eintrag in `enum class Werkzeug` und in `App()` (`MainActivity.kt`).
