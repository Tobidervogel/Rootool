#!/bin/sh
# Test fuer app/src/main/assets/apps.sh auf dem PC (Linux/WSL, als root wegen chown), ohne Handy.
# pm, am, dumpsys und restorecon sind Attrappen; /data/data und /sdcard sind Ordner.
#
#   sh app/src/test/shell/apps_test.sh [mksh|dash|"busybox sh"]
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/roottools" "$T/sd" "$T/data" "$T/de" "$T/app" "$T/installiert"
cp "$HIER/../../main/assets/apps.sh" "$T/roottools/apps.sh"
P=de.tobidervogel.liveup
APP_UID=10234

# Installierte App mit Daten
neu_installieren() {  # $1 = APK-Inhalt
    mkdir -p "$T/app/$P" "$T/data/$P/cache" "$T/data/$P/code_cache" "$T/de/$P"
    printf '%s' "$1" > "$T/app/$P/base.apk"
    ln -sfn /data/app/xyz/lib/arm64 "$T/data/$P/lib"
    chown -R $APP_UID:$APP_UID "$T/data/$P" "$T/de/$P"
    touch "$T/installiert/$P"
}
neu_installieren "ALT"
mkdir -p "$T/data/$P/databases" "$T/data/$P/shared_prefs" "$T/de/$P/files"
echo "essen=12" > "$T/data/$P/databases/liveup.db"
echo "<map/>" > "$T/data/$P/shared_prefs/liveup.xml"
echo "cache" > "$T/data/$P/cache/c.tmp"
echo "de" > "$T/de/$P/files/wecker"
chown -R $APP_UID:$APP_UID "$T/data/$P" "$T/de/$P"

cat > "$T/bin/pm" <<EOF
#!/bin/sh
echo "pm \$*" >> "$T/aufrufe"
case \$1 in
    path) [ -f "$T/installiert/\$2" ] || exit 1; echo "package:$T/app/\$2/base.apk" ;;
    install-create) rm -rf "$T/sitzung"; mkdir -p "$T/sitzung"; echo "Success: created install session [42]" ;;
    install-write) cp "\$6" "$T/sitzung/\$5" ;;
    install-abandon) rm -rf "$T/sitzung" ;;
    install-commit)
        inhalt=\$(cat "$T/sitzung/"*.apk)
        case \$inhalt in
            KAPUTT) echo "Failure [INSTALL_PARSE_FAILED_NOT_APK]"; exit 1 ;;
            ANDERESIG) [ -f "$T/installiert/$P" ] && { echo "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]"; exit 1; } ;;
        esac
        if [ ! -f "$T/installiert/$P" ]; then
            mkdir -p "$T/app/$P" "$T/data/$P/cache" "$T/data/$P/code_cache" "$T/de/$P"
            chown -R $APP_UID:$APP_UID "$T/data/$P" "$T/de/$P"
            touch "$T/installiert/$P"
        fi
        cp "$T/sitzung/"*.apk "$T/app/$P/base.apk"
        echo Success ;;
    uninstall) rm -rf "$T/installiert/\$2" "$T/app/\$2" "$T/data/\$2" "$T/de/\$2"; echo Success ;;
esac
EOF
cat > "$T/bin/am" <<EOF
#!/bin/sh
echo "am \$*" >> "$T/aufrufe"
EOF
cat > "$T/bin/dumpsys" <<'EOF'
#!/bin/sh
echo "    versionCode=37 minSdk=26 targetSdk=34"
echo "    versionName=0.24.1"
EOF
cat > "$T/bin/restorecon" <<EOF
#!/bin/sh
echo "restorecon \$*" >> "$T/aufrufe"
EOF
chmod +x "$T/bin/"*

case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac
export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_BIN="$T/bin" ROOTTOOLS_SD="$T/sd" ROOTTOOLS_DATA="$T/data" ROOTTOOLS_DE="$T/de"
export PATH="$T/bin:$PATH"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
apps() { $SHELL_UNTER_TEST "$T/roottools/apps.sh" "$@"; }
B=$T/sd/RootTools/Apps/$P

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Sichern"
apps sichern $P > "$T/a1"
o1=$(sed -n 's/^FERTIG=//p' "$T/a1")
pruefe "fertig"                 '[ -n "$o1" ] && [ -d "$B/$o1" ]'
pruefe "APK dabei"              '[ "$(cat "$B/$o1/apk/base.apk")" = ALT ]'
pruefe "Daten ohne Cache"       'tar -tzf "$B/$o1/data.tar.gz" | grep -q "databases/liveup.db" && ! tar -tzf "$B/$o1/data.tar.gz" | grep -qE "cache|lib"'
pruefe "Geraetedaten"           'tar -tzf "$B/$o1/de.tar.gz" | grep -q "files/wecker"'
pruefe "Version"                'grep -qx "version=0.24.1" "$B/$o1/info.txt"'
pruefe "Pruefsummen"            '( cd "$B/$o1" && sha256sum -c SHA256SUMS > /dev/null )'
pruefe "App gestoppt"           'grep -qx "am force-stop $P" "$T/aufrufe"'
apps sicherungen > "$T/a2"
pruefe "gelistet"               'grep -q "^SICHERUNG=$P|$o1|[0-9]*|0.24.1$" "$T/a2"'

echo "2. Nur Daten zurueck"
rm "$T/data/$P/databases/liveup.db"
echo muell > "$T/data/$P/neu.txt"
apps zurueck $P "$o1" daten > "$T/a3"
pruefe "fertig"                 'grep -qx "FERTIG=$o1" "$T/a3"'
pruefe "Datenbank wieder da"    '[ "$(cat "$T/data/$P/databases/liveup.db")" = "essen=12" ]'
pruefe "Neues weg"              '[ ! -e "$T/data/$P/neu.txt" ]'
pruefe "Cache unberuehrt"       '[ -f "$T/data/$P/cache/c.tmp" ] && [ -L "$T/data/$P/lib" ]'
pruefe "Besitzer stimmt"        '[ "$(stat -c %u "$T/data/$P/databases/liveup.db")" = $APP_UID ] && [ "$(stat -c %u "$T/de/$P/files/wecker")" = $APP_UID ]'
pruefe "restorecon"             'grep -q "restorecon -RFD $T/data/$P" "$T/aufrufe"'

echo "3. Beschaedigte Sicherung"
cp -r "$B/$o1" "$B/kaputt"
echo x >> "$B/kaputt/data.tar.gz"
apps zurueck $P kaputt daten > "$T/a4"
pruefe "abgelehnt"              'grep -q "^FEHLER=Sicherung ist beschädigt" "$T/a4"'
rm -rf "$B/kaputt"

echo "4. App und Daten zurueck"
apps zurueck $P "$o1" alles > "$T/a4b"
pruefe "installiert und fertig" 'grep -qx "FERTIG=$o1" "$T/a4b" && grep -qx "pm install-commit 42" "$T/aufrufe"'
printf 'ANDERESIG' > "$T/neu.apk"
sleep 1

echo "5. Neue APK einspielen (Signatur wechseln)"
echo "essen=13" > "$T/data/$P/databases/liveup.db"
apps tauschen $P "$T/neu.apk" > "$T/a5"
pruefe "fertig"                 'grep -q "^FERTIG=" "$T/a5" && ! grep -q "^FEHLER=" "$T/a5"'
pruefe "neue APK installiert"   '[ "$(cat "$T/app/$P/base.apk")" = ANDERESIG ]'
pruefe "Daten uebernommen"      '[ "$(cat "$T/data/$P/databases/liveup.db")" = "essen=13" ]'
pruefe "deinstalliert vorher"   'grep -qx "pm uninstall $P" "$T/aufrufe"'
pruefe "Sicherung angelegt"     '[ "$(ls -1 "$B" | wc -l)" = 2 ]'

echo "6. Kaputte neue APK: alte App kommt zurueck"
sleep 1
printf 'KAPUTT' > "$T/kaputt.apk"
apps tauschen $P "$T/kaputt.apk" > "$T/a6"
pruefe "Fehler gemeldet"        'grep -q "^FEHLER=Neue APK liess sich nicht installieren" "$T/a6"'
pruefe "alte App wieder da"     '[ -f "$T/installiert/$P" ] && [ "$(cat "$T/app/$P/base.apk")" = ANDERESIG ]'
pruefe "Daten wieder da"        '[ "$(cat "$T/data/$P/databases/liveup.db")" = "essen=13" ]'

echo "7. Unsinnige Eingaben"
apps sichern 'a;b' > "$T/a7"; c1=$?
apps zurueck $P ../../etc daten > "$T/a8"; c2=$?
apps sichern gibt.es.nicht > "$T/a9"
pruefe "Paketname"              '[ $c1 = 2 ] && grep -q "^FEHLER=Ungültiger Paketname" "$T/a7"'
pruefe "Ordner"                 '[ $c2 = 2 ] && grep -q "^FEHLER=Ungültiger Ordner" "$T/a8"'
pruefe "nicht installiert"      'grep -q "^FEHLER=gibt.es.nicht ist nicht installiert" "$T/a9"'

echo "8. Loeschen"
n=$(ls -1 "$B" | wc -l)
apps loeschen $P "$o1" > /dev/null
pruefe "eine weniger"           '[ "$(ls -1 "$B" | wc -l)" = $((n - 1)) ] && [ ! -e "$B/$o1" ]'

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
