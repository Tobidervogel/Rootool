#!/bin/sh
# Test fuer app/src/main/assets/adb.sh auf dem PC (Linux/WSL), ohne Handy.
# iptables/ip6tables fuehren ihre Regeln als Dateien, getprop/setprop und settings als Schluessel-Dateien.
#
#   sh app/src/test/shell/adb_test.sh [mksh|dash|"busybox sh"]
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/roottools" "$T/props" "$T/ipt" "$T/net"
cp "$HIER/../../main/assets/adb.sh" "$T/roottools/adb.sh"

# iptables-Attrappe: Ketten als Dateien $T/ipt/<prog>.<kette>, eine Regel pro Zeile
for prog in iptables ip6tables; do
cat > "$T/bin/$prog" <<EOF
#!/bin/sh
echo "$prog \$*" >> "$T/aufrufe"
[ "\$1" = -w ] && shift
op=\$1; kette=\$2; shift 2
f="$T/ipt/$prog.\$kette"
case \$op in
    -N) [ -f "\$f" ] && exit 1; : > "\$f" ;;
    -F) [ -f "\$f" ] || exit 1; : > "\$f" ;;
    -X) [ -f "\$f" ] || exit 1; rm -f "\$f" ;;
    -A) echo "\$*" >> "\$f" ;;
    -I) { echo "\$*"; cat "\$f" 2>/dev/null; } > "\$f.neu"; mv "\$f.neu" "\$f" ;;
    -C) grep -qxF -- "\$*" "\$f" 2>/dev/null ;;
    -D) grep -qxF -- "\$*" "\$f" 2>/dev/null || exit 1; grep -vxF -- "\$*" "\$f" > "\$f.neu"; mv "\$f.neu" "\$f" ;;
esac
EOF
done
cat > "$T/bin/getprop" <<EOF
#!/bin/sh
cat "$T/props/\$1" 2>/dev/null
EOF
cat > "$T/bin/setprop" <<EOF
#!/bin/sh
echo "\$2" > "$T/props/\$1"
EOF
cat > "$T/bin/settings" <<EOF
#!/bin/sh
[ "\$1" = put ] && echo "\$4" > "$T/props/\$3"
[ "\$1" = get ] && cat "$T/props/\$3" 2>/dev/null
exit 0
EOF
cat > "$T/bin/stop" <<EOF
#!/bin/sh
echo "stop \$*" >> "$T/aufrufe"
EOF
cat > "$T/bin/start" <<EOF
#!/bin/sh
echo "start \$*" >> "$T/aufrufe"
EOF
cat > "$T/bin/ip" <<'EOF'
#!/bin/sh
echo "1: lo    inet 127.0.0.1/8 scope host lo\       valid_lft forever preferred_lft forever"
echo "28: tun0    inet 100.101.7.12/32 scope global tun0\       valid_lft forever preferred_lft forever"
echo "31: wlan0    inet 192.168.0.115/24 brd 192.168.0.255 scope global wlan0\       valid_lft forever"
EOF
chmod +x "$T/bin/"*
printf '  sl  local_address rem_address   st\n   0: 00000000:15B3 00000000:0000 0A 00000000:00000000\n' > "$T/net/tcp"

case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac
export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_BIN="$T/bin" ROOTTOOLS_SH="$SHELL_UNTER_TEST" \
    ROOTTOOLS_BOOT="$T/service.d/roottools-adb.sh" ROOTTOOLS_PROCNET="$T/net"
export PATH="$T/bin:$PATH"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
adb() { $SHELL_UNTER_TEST "$T/roottools/adb.sh" "$@"; }
V4=$T/ipt/iptables.roottools_adb
V6=$T/ipt/ip6tables.roottools_adb

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Unsinnige Heimnetze"
for n in 192.168.0.0 192.168.0.0/33 1.2.3/24 '1.2.3.4/24;rm' 300.1.1.1/24 ''; do
    adb an "$n" > "$T/a0"
    pruefe "abgelehnt: '$n'" 'grep -q "^FEHLER=Ungültiges Heimnetz" "$T/a0"'
done
pruefe "nichts veraendert"       '[ ! -f "$T/props/service.adb.tcp.port" ] && [ ! -f "$V4" ]'

echo "2. An"
adb an 192.168.0.0/24 > "$T/a1"
pruefe "Port gesetzt"            '[ "$(cat "$T/props/service.adb.tcp.port")" = 5555 ]'
pruefe "adbd neu gestartet"      'grep -qx "stop adbd" "$T/aufrufe" && grep -qx "start adbd" "$T/aufrufe"'
pruefe "USB-Debugging an"        'grep -qx "USB_DEBUG=1" "$T/a1"'
pruefe "erlaubt: lo, Tailscale, Heim" 'grep -qx -- "-s 127.0.0.0/8 -j ACCEPT" "$V4" && grep -qx -- "-s 100.64.0.0/10 -j ACCEPT" "$V4" && grep -qx -- "-s 192.168.0.0/24 -j ACCEPT" "$V4"'
pruefe "Rest verworfen"          '[ "$(tail -n 1 "$V4")" = "-j DROP" ] && [ "$(tail -n 1 "$V6")" = "-j DROP" ]'
pruefe "IPv6 Tailscale"          'grep -qx -- "-s fd7a:115c:a1e0::/48 -j ACCEPT" "$V6"'
pruefe "Regeln vor adbd"         '[ "$(grep -n "iptables -w -I INPUT" "$T/aufrufe" | head -n 1 | cut -d: -f1)" -lt "$(grep -n "start adbd" "$T/aufrufe" | cut -d: -f1)" ]'
pruefe "Stand"                   'grep -qx "SCHUTZ=1" "$T/a1" && grep -qx "LAUSCHT=1" "$T/a1" && grep -qx "HEIM=192.168.0.0/24" "$T/a1"'
pruefe "Adressen"                'grep -qx "TAILSCALE=100.101.7.12" "$T/a1" && grep -qx "WLAN=192.168.0.115/24" "$T/a1"'
pruefe "Boot-Skript"             'grep -q "adb.sh boot" "$ROOTTOOLS_BOOT"'

echo "3. Zweimal an: keine doppelten Regeln"
adb an keins > /dev/null
pruefe "INPUT-Sprung einmal"     '[ "$(grep -c roottools_adb "$T/ipt/iptables.INPUT")" = 1 ]'
pruefe "ohne Heimnetz"           '! grep -q "192.168" "$V4" && [ "$(wc -l < "$V4")" = 3 ]'

echo "4. Neustart: Boot-Skript stellt alles her"
rm -f "$T/ipt/"* "$T/props/service.adb.tcp.port"
$SHELL_UNTER_TEST "$T/roottools/adb.sh" boot
pruefe "wieder offen und geschuetzt" '[ "$(cat "$T/props/service.adb.tcp.port")" = 5555 ] && [ "$(tail -n 1 "$V4")" = "-j DROP" ]'

echo "5. Aus"
adb aus > "$T/a5"
pruefe "Port zu"                 '[ "$(cat "$T/props/service.adb.tcp.port")" = -1 ]'
pruefe "Regeln weg"              '[ ! -f "$V4" ] && [ ! -s "$T/ipt/iptables.INPUT" ] && ! grep -q "^SCHUTZ=" "$T/a5"'
pruefe "Boot-Skript weg"         '[ ! -f "$ROOTTOOLS_BOOT" ]'

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
