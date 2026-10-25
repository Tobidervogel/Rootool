#!/bin/sh
# Test fuer app/src/main/assets/hosts.sh auf dem PC (Linux/WSL), ohne Handy.
# Das Magisk-Modul ist ein Ordner; "eingehaengt" heisst hier: /system/etc/hosts ist ein harter Link auf die Datei im
# Modul (gleicher Inode wie beim Einhaengen durch Magisk).
#
#   sh app/src/test/shell/hosts_test.sh [mksh|dash|"busybox sh"]
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/roottools" "$T/system"
cp "$HIER/../../main/assets/hosts.sh" "$T/roottools/hosts.sh"
printf '127.0.0.1 localhost\n::1 ip6-localhost\n' > "$T/system/hosts"

cat > "$T/liste" <<'EOF'
# Title: StevenBlack/hosts
127.0.0.1 localhost
127.0.0.1 localhost.localdomain
255.255.255.255 broadcasthost
::1 localhost
0.0.0.0 0.0.0.0

# Werbung
0.0.0.0 ads.example.com
0.0.0.0 Tracker.Example.COM # Kommentar
0.0.0.0	tab.example.net
127.0.0.1 alt.example.org
0.0.0.0 ads.example.com
1.2.3.4 kein-block.example.com
EOF

case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac
export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_HOSTSMODUL="$T/modules/hosts" ROOTTOOLS_SYSHOSTS="$T/system/hosts"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
h() { $SHELL_UNTER_TEST "$T/roottools/hosts.sh" "$@"; }
M=$T/modules/hosts/system/etc/hosts

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Erste Liste: Modul anlegen"
printf 'SPERRE Instagram.com\nAUSNAHME ads.example.com\nSPERRE kaputt;rm\nSPERRE .boese\nUNSINN x.de\n' > "$T/roottools/hosts.sh.eingabe"
h schreiben "$T/liste" Werbung_und_Schadsoftware > "$T/h1"
pruefe "Modul wie Magisk"        'grep -qx "id=hosts" "$T/modules/hosts/module.prop" && [ -f "$T/modules/hosts/update" ]'
pruefe "neu gemeldet"            'grep -qx "NEU=1" "$T/h1"'
pruefe "Marke und Name"          'head -n 1 "$M" | grep -q "Root Tools Werbeblocker" && grep -qx "LISTE=Werbung und Schadsoftware" "$T/h1"'
pruefe "gesperrt, klein"         'grep -qx "0.0.0.0 tracker.example.com" "$M" && grep -qx "0.0.0.0 tab.example.net" "$M" && grep -qx "0.0.0.0 alt.example.org" "$M"'
pruefe "eigene Sperre"           'grep -qx "0.0.0.0 instagram.com" "$M"'
pruefe "Ausnahme greift"         '! grep -q "ads.example.com" "$M"'
pruefe "kein localhost-Muell"    '! grep -qE "^0\.0\.0\.0 (localhost|0\.0\.0\.0|broadcasthost)" "$M" && [ "$(grep -c "localhost" "$M")" = 2 ]'
pruefe "andere IPs bleiben weg"  '! grep -q "kein-block" "$M"'
pruefe "Anzahl"                  'grep -qx "ANZAHL=4" "$T/h1"'
pruefe "nur gueltige Eigene"     '[ "$(grep -c "^EIGEN=" "$T/h1")" = 2 ]'
pruefe "noch nicht aktiv"        '! grep -q "^AKTIV=" "$T/h1"'

echo "2. Eingehaengt: Aenderung wirkt sofort"
ln -f "$M" "$T/system/hosts"
pruefe "aktiv"                   'h zustand | grep -qx "AKTIV=1"'
printf 'SPERRE tiktok.com\n' > "$T/roottools/hosts.sh.eingabe"
h schreiben - "Werbung und Schadsoftware" > "$T/h2"
pruefe "gleiche Datei geaendert" 'grep -qx "0.0.0.0 tiktok.com" "$T/system/hosts"'
pruefe "Ausnahme aufgehoben"     'grep -qx "0.0.0.0 ads.example.com" "$T/system/hosts" && ! grep -q "instagram" "$T/system/hosts"'
pruefe "kein neues Modul"        '! grep -q "^NEU=" "$T/h2"'

echo "3. Ohne Liste"
rm -f "$T/roottools/hosts.quelle"
h schreiben - > "$T/h3"
pruefe "Fehler"                  'grep -q "^FEHLER=Keine Liste" "$T/h3"'

echo "4. Aus"
h aus > "$T/h4"
pruefe "neutral"                 '[ "$(wc -l < "$T/system/hosts")" = 2 ] && ! grep -q "^UNSER=" "$T/h4"'
pruefe "Modul geht beim Start"   'grep -qx "ENTFERNEN=1" "$T/h4"'

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
