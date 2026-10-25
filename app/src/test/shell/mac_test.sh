#!/bin/sh
# Test fuer app/src/main/assets/mac.sh auf dem PC (Linux/WSL), ohne Handy.
# ifconfig, cmd, getprop und ip sind Attrappen, /sys/class/net/wlan0 ist ein Ordner.
#
#   sh app/src/test/shell/mac_test.sh            mit /bin/sh
#   sh app/src/test/shell/mac_test.sh mksh       mit der Android-Shell (mksh), dash, "busybox sh" ...
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
SKRIPT=$HIER/../../main/assets/mac.sh
T=$(mktemp -d)
trap 'pkill -f "$T/roottools/mac.sh" 2>/dev/null; rm -rf "$T"' EXIT

mkdir -p "$T/bin" "$T/net/wlan0" "$T/roottools"
echo 0x1003 > "$T/net/wlan0/flags"
echo 3a:11:22:33:44:55 > "$T/net/wlan0/address"
cp "$SKRIPT" "$T/roottools/mac.sh"
# "busybox sh" ist zwei Woerter, mac.sh startet den Waechter aber mit EINEM Programmnamen.
case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac

cat > "$T/bin/ifconfig" <<EOF
#!/bin/sh
echo "ifconfig \$*" >> "$T/aufrufe"
ifc=\$1; shift
while [ \$# -gt 0 ]; do
    case \$1 in
        down) echo 0x1002 > "$T/net/\$ifc/flags" ;;
        up) echo 0x1003 > "$T/net/\$ifc/flags" ;;
        hw)
            # Wie der Kernel: im laufenden Betrieb "busy".
            [ \$((\$(cat "$T/net/\$ifc/flags") & 1)) -eq 1 ] && { echo "ifconfig: busy" >&2; exit 1; }
            # WLAN-Chip aus: Treiber nimmt keine Adresse an.
            [ -f "$T/schlaeft" ] && { echo "ifconfig: SIOCSIFHWADDR: Network is down" >&2; exit 1; }
            [ -f "$T/stur" ] || echo "\$3" > "$T/net/\$ifc/address"
            shift 2 ;;
    esac
    shift
done
EOF
cat > "$T/bin/cmd" <<EOF
#!/bin/sh
echo "cmd \$*" >> "$T/aufrufe"
case "\$2" in
    force-overlay-config-value)
        [ -f "$T/ohne_schalter" ] && { echo "Unknown command"; exit 255; }
        if [ "\$5" = enabled ]; then echo "\$6" > "$T/zufall"; else rm -f "$T/zufall"; fi
        echo true ;;
    help) [ -f "$T/ohne_schalter" ] || echo "  force-overlay-config-value bool|integer ..." ;;
    get-overlay-config-values)
        echo "Resource Name: config_wifi_connected_mac_randomization_supported, value: \$(cat "$T/zufall" 2>/dev/null || echo true)" ;;
    status)
        echo "Wifi is enabled"
        echo "Wifi scanning is always available"
        echo "==== ClientModeManager instance: x ===="
        echo 'Wifi is connected to "Heimnetz"'
        echo "WifiInfo: SSID: Heimnetz" ;;
esac
EOF
cat > "$T/bin/getprop" <<'EOF'
#!/bin/sh
echo 1
EOF
cat > "$T/bin/ip" <<'EOF'
#!/bin/sh
echo "5: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP mode DORMANT group default qlen 3000"
echo "    link/ether 3a:11:22:33:44:55 brd ff:ff:ff:ff:ff:ff permaddr 98:76:54:32:10:fe"
EOF
chmod +x "$T/bin/"*

export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_BIN="$T/bin" ROOTTOOLS_NET="$T/net" \
    ROOTTOOLS_BOOT="$T/service.d/roottools-mac.sh" ROOTTOOLS_TAKT=1 ROOTTOOLS_SH="$SHELL_UNTER_TEST"
export PATH="$T/bin:$PATH"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
mac() { $SHELL_UNTER_TEST "$T/roottools/mac.sh" "$@"; }
adresse() { cat "$T/net/wlan0/address"; }
warte_auf() {  # bis zu 8 s, bis die Bedingung gilt
    i=0
    while ! eval "$1" && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
}

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Ungueltige MACs werden abgelehnt"
mac setzen 13:00:00:00:00:00 ja > "$T/aus1"; c1=$?
mac setzen 00:00:00:00:00:00 ja > /dev/null; c2=$?
mac setzen 'aa:bb:cc:dd:ee:f;id' ja > /dev/null; c3=$?
pruefe "Gruppenadresse"    '[ $c1 -eq 2 ] && grep -q "^FEHLER=" "$T/aus1"'
pruefe "Nullen"            '[ $c2 -eq 2 ]'
pruefe "Fremde Zeichen"    '[ $c3 -eq 2 ]'
pruefe "nichts angelegt"   '[ ! -f "$T/roottools/mac.conf" ] && [ ! -f "$ROOTTOOLS_BOOT" ]'

echo "2. Setzen bei verbundenem WLAN"
mac setzen 12:34:56:78:9A:BC ja > "$T/aus2"
pruefe "MAC gesetzt"            '[ "$(adresse)" = 12:34:56:78:9a:bc ]'
pruefe "in einem Aufruf"        'grep -q "^ifconfig wlan0 down hw ether 12:34:56:78:9a:bc up$" "$T/aufrufe"'
pruefe "wieder oben"            '[ "$(cat "$T/net/wlan0/flags")" = 0x1003 ]'
pruefe "Zufall aus"             '[ "$(cat "$T/zufall")" = false ]'
pruefe "Boot-Skript"            'grep -q "mac.sh start" "$ROOTTOOLS_BOOT" && [ -x "$ROOTTOOLS_BOOT" ]'
pruefe "Stand: ZIEL"            'grep -qx "ZIEL=12:34:56:78:9a:bc" "$T/aus2"'
pruefe "Stand: AKTUELL"         'grep -qx "AKTUELL=12:34:56:78:9a:bc" "$T/aus2"'
pruefe "Stand: WAECHTER"        'grep -qx "WAECHTER=1" "$T/aus2"'
pruefe "Stand: ORIGINAL"        'grep -qx "ORIGINAL=98:76:54:32:10:fe" "$T/aus2"'
pruefe "Stand: SCHALTER, ZUFALL" 'grep -qx "SCHALTER=1" "$T/aus2" && grep -qx "ZUFALL=false" "$T/aus2"'
pruefe "Stand: WLAN"            'grep -qx "WLAN=Wifi is connected to \"Heimnetz\"" "$T/aus2"'

echo "3. WLAN aus und an: Treiber bringt die Geraete-MAC zurueck"
: > "$T/aufrufe"
echo 0x1002 > "$T/net/wlan0/flags"
echo 98:76:54:32:10:fe > "$T/net/wlan0/address"
warte_auf '[ "$(adresse)" = 12:34:56:78:9a:bc ]'
pruefe "wieder eigene MAC"      '[ "$(adresse)" = 12:34:56:78:9a:bc ]'
pruefe "unten: ohne runter/hoch" 'grep -q "^ifconfig wlan0 hw ether 12:34:56:78:9a:bc$" "$T/aufrufe"'
pruefe "bleibt unten"           '[ "$(cat "$T/net/wlan0/flags")" = 0x1002 ]'

echo "4. Neue MAC ersetzt den alten Waechter"
alt=$(cat "$T/roottools/mac.pid")
mac setzen 02:00:00:00:00:01 nein > "$T/aus4"
neu=$(cat "$T/roottools/mac.pid")
pruefe "neue MAC"               '[ "$(adresse)" = 02:00:00:00:00:01 ]'
pruefe "alter Waechter weg"     '[ "$alt" != "$neu" ] && ! kill -0 "$alt" 2>/dev/null'
pruefe "genau ein Waechter"     '[ "$(pgrep -fc "$T/roottools/mac.sh waechter")" = 1 ]'
pruefe "Boot-Skript entfernt"   '[ ! -f "$ROOTTOOLS_BOOT" ] && ! grep -q "^BOOT=" "$T/aus4"'

echo "5. WLAN-Chip aus, Treiber nimmt nichts an: einmal melden, weiter warten"
touch "$T/schlaeft"
echo 0x1002 > "$T/net/wlan0/flags"
echo 98:76:54:32:10:fe > "$T/net/wlan0/address"
sleep 3.5
pruefe "einmal gemeldet"        '[ "$(grep -c "nimmt die MAC noch nicht an" "$T/roottools/mac.log")" = 1 ]'
pruefe "Waechter laeuft weiter" 'mac zustand | grep -q "^WAECHTER=1"'
rm -f "$T/schlaeft"
: > "$T/aufrufe"
echo 0x1003 > "$T/net/wlan0/flags"
warte_auf '[ "$(adresse)" = 02:00:00:00:00:01 ]'
pruefe "beim Hochkommen gesetzt" '[ "$(adresse)" = 02:00:00:00:00:01 ] && grep -q "^ifconfig wlan0 down hw ether 02:00:00:00:00:01 up$" "$T/aufrufe"'

echo "6. Haelt jemand dagegen, gibt der Waechter auf"
touch "$T/stur"
: > "$T/aufrufe"
sleep 1
echo 98:76:54:32:10:fe > "$T/net/wlan0/address"
warte_auf 'grep -q "Gestoppt" "$T/roottools/mac.log"'
pruefe "gestoppt"               'grep -q "Gestoppt" "$T/roottools/mac.log"'
pruefe "hoechstens 5 Versuche"  '[ "$(grep -c "^ifconfig wlan0 down hw ether 02:00:00:00:00:01 up$" "$T/aufrufe")" -le 5 ]'
pruefe "WLAN bleibt oben"       '[ "$(cat "$T/net/wlan0/flags")" = 0x1003 ]'
warte_auf '[ ! -f "$T/roottools/mac.pid" ]'
pruefe "Waechter beendet"       '! mac zustand | grep -q "^WAECHTER="'
rm -f "$T/stur"

echo "7. Boot-Skript startet den Waechter"
mac setzen 02:00:00:00:00:03 ja > /dev/null
pkill -f "$T/roottools/mac.sh waechter"; sleep 0.3; rm -f "$T/roottools/mac.pid"
$SHELL_UNTER_TEST "$ROOTTOOLS_BOOT"
warte_auf 'mac zustand | grep -q "^WAECHTER=1"'
pruefe "laeuft nach Boot"       'mac zustand | grep -q "^WAECHTER=1"'

echo "8. Aus: zurueck zu Android"
: > "$T/aufrufe"
mac aus > "$T/aus7"
pruefe "Waechter weg"           '! grep -q "^WAECHTER=" "$T/aus7" && [ "$(pgrep -fc "$T/roottools/mac.sh waechter")" = 0 ]'
pruefe "Dateien weg"            '[ ! -f "$T/roottools/mac.conf" ] && [ ! -f "$ROOTTOOLS_BOOT" ]'
pruefe "Zufall zurueck"         '[ ! -f "$T/zufall" ] && grep -q "force-overlay-config-value bool config_wifi_connected_mac_randomization_supported disabled" "$T/aufrufe"'
pruefe "WLAN neu gestartet"     'grep -q "set-wifi-enabled disabled" "$T/aufrufe" && grep -q "set-wifi-enabled enabled" "$T/aufrufe"'

echo "9. Android ohne Schalter"
touch "$T/ohne_schalter"
mac setzen 02:00:00:00:00:04 nein > "$T/aus8"
pruefe "kein SCHALTER"          '! grep -q "^SCHALTER=" "$T/aus8"'
pruefe "Hinweis im Protokoll"   'grep -q "kennt den Schalter nicht" "$T/roottools/mac.log"'
pruefe "MAC trotzdem gesetzt"   '[ "$(adresse)" = 02:00:00:00:00:04 ]'
mac aus > /dev/null

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
