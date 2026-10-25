#!/bin/sh
# Test fuer app/src/main/assets/firewall.sh auf dem PC (Linux/WSL), ohne Handy.
# cmd merkt sich Kette und Sperren in Dateien; die Uhrzeit kommt aus einer Datei (date-Attrappe fuer %H und %M).
#
#   sh app/src/test/shell/firewall_test.sh [mksh|dash|"busybox sh"]
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'pkill -f "$T/roottools/firewall.sh" 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/roottools" "$T/netz"
cp "$HIER/../../main/assets/firewall.sh" "$T/roottools/firewall.sh"
ECHTES_DATE=$(command -v date)

cat > "$T/bin/cmd" <<EOF
#!/bin/sh
echo "cmd \$*" >> "$T/aufrufe"
[ "\$1" = connectivity ] || exit 0
case \$2 in
    set-chain3-enabled) echo "\$3" > "$T/netz/kette" ;;
    get-chain3-enabled) [ "\$(cat "$T/netz/kette" 2>/dev/null)" = true ] && echo chain:enabled || echo chain:disabled ;;
    set-package-networking-enabled)
        if [ "\$3" = false ]; then touch "$T/netz/\$4"; else rm -f "$T/netz/\$4"; fi
        echo "\$3 networking for \$4" ;;
esac
EOF
cat > "$T/bin/date" <<EOF
#!/bin/sh
case \$1 in
    +%H) cut -d: -f1 "$T/uhr" ;;
    +%M) cut -d: -f2 "$T/uhr" ;;
    *) exec $ECHTES_DATE "\$@" ;;
esac
EOF
chmod +x "$T/bin/"*

case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac
export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_BIN="$T/bin" ROOTTOOLS_SH="$SHELL_UNTER_TEST" \
    ROOTTOOLS_BOOT="$T/service.d/roottools-firewall.sh" ROOTTOOLS_TAKT=1
export PATH="$T/bin:$PATH"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
fw() { $SHELL_UNTER_TEST "$T/roottools/firewall.sh" "$@"; }
gesperrt() { [ -f "$T/netz/$1" ]; }
setzen() { printf '%s\n' "$@" > "$T/roottools/firewall.sh.eingabe"; fw setzen; }

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Regeln setzen (nachts)"
echo 23:30 > "$T/uhr"
setzen 'com.instagram.android|immer' 'com.google.android.youtube|zeit|22:00|07:00' 'kaputt;rm|immer' \
    'com.x.y|zeit|25:00|07:00' 'com.a.b|zeit|09:00|09:00' 'com.c.d|unsinn' > "$T/f1"
pruefe "nur gueltige Regeln"     '[ "$(wc -l < "$T/roottools/firewall.regeln")" = 2 ]'
pruefe "Kette an"                '[ "$(cat "$T/netz/kette")" = true ] && grep -qx "KETTE=1" "$T/f1"'
pruefe "Instagram gesperrt"      'gesperrt com.instagram.android'
pruefe "YouTube nachts gesperrt" 'gesperrt com.google.android.youtube'
pruefe "Stand meldet beides"     '[ "$(grep -c "^GESPERRT=" "$T/f1")" = 2 ]'
pruefe "Boot-Skript"             'grep -q "firewall.sh start" "$ROOTTOOLS_BOOT"'
pruefe "Waechter laeuft"         'grep -qx "WAECHTER=1" "$T/f1"'

echo "2. Mittags gibt der Waechter YouTube frei"
echo 12:00 > "$T/uhr"
sleep 2.5
pruefe "YouTube frei"            '! gesperrt com.google.android.youtube'
pruefe "Instagram bleibt"        'gesperrt com.instagram.android'
n=$(grep -c "set-package-networking-enabled" "$T/aufrufe")
sleep 2.5
pruefe "keine unnoetigen Aufrufe" '[ "$(grep -c "set-package-networking-enabled" "$T/aufrufe")" = $n ]'

echo "3. Fuehrende Null (08:05 ist keine Oktalzahl)"
echo 08:05 > "$T/uhr"
setzen 'com.google.android.youtube|zeit|07:30|08:30' > "$T/f3"
pruefe "kein Rechenfehler, gesperrt" 'gesperrt com.google.android.youtube'
pruefe "Instagram jetzt frei"    '! gesperrt com.instagram.android'

echo "4. Nach dem Neustart: alles neu setzen"
rm -f "$T/netz/"*
fw start
pruefe "wieder gesperrt"         'gesperrt com.google.android.youtube && [ "$(cat "$T/netz/kette")" = true ]'

echo "5. Nur Daueregeln: kein Waechter"
setzen 'com.instagram.android|immer' > "$T/f5"
sleep 0.5
pgrep -fa "$T/roottools/firewall.sh waechter" >&2; cat "$T/roottools/firewall.pid" >&2 2>/dev/null; pruefe "kein Waechter"           '! grep -q "^WAECHTER=" "$T/f5" && [ "$(pgrep -fc "$T/roottools/firewall.sh waechter")" = 0 ]'
pruefe "YouTube frei"            '! gesperrt com.google.android.youtube'

echo "6. Aus"
fw aus > "$T/f6"
pruefe "alles frei"              '[ -z "$(ls "$T/netz" | grep -v kette)" ]'
pruefe "Kette aus"               '[ "$(cat "$T/netz/kette")" = false ]'
pruefe "Dateien weg"             '[ ! -f "$T/roottools/firewall.regeln" ] && [ ! -f "$ROOTTOOLS_BOOT" ]'

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
