#!/system/bin/sh
# Root Tools – ADB uebers Netz
#
#   adb.sh zustand         laeuft ADB uebers Netz?, Port, Adressen (Tailscale, WLAN), Schutzregeln
#   adb.sh an HEIMNETZ     ADB auf Port 5555 oeffnen, nur fuer Tailscale (100.64.0.0/10), localhost und HEIMNETZ
#                          (z. B. 192.168.0.0/24, oder "keins"); bleibt nach dem Neustart an
#   adb.sh aus             zu, Regeln und Boot-Skript weg
#   adb.sh boot            beim Hochfahren (Boot-Skript)
#
# Wie (wie tools/phone/adb-tcp.sh aus dem Cloud-Arbeitsplatz): adbd liest beim Start service.adb.tcp.port
# (AOSP packages/modules/adb, daemon/main.cpp). Vorher kommen iptables-Regeln, die Port 5555 nur aus den erlaubten
# Netzen zulassen. Fremde Rechner brauchen ausserdem weiter die ADB-Freigabe am Handy (Schluessel-Dialog).
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/adb_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
B=${ROOTTOOLS_BIN:-/system/bin}
SH=${ROOTTOOLS_SH:-/system/bin/sh}
BOOT=${ROOTTOOLS_BOOT:-/data/adb/service.d/roottools-adb.sh}
PROCNET=${ROOTTOOLS_PROCNET:-/proc/net}
CONF=$D/adb.conf
PORT=5555
KETTE=roottools_adb

netz_ok() {
    case $1 in
        keins) return 0 ;;
        *[!0-9./]* | "") return 1 ;;
    esac
    ip=${1%/*}
    maske=${1##*/}
    [ "$ip" != "$1" ] || return 1
    case $maske in
        [0-9] | [12][0-9] | 3[0-2]) ;;
        *) return 1 ;;
    esac
    alt_ifs=$IFS
    IFS=.
    set -- $ip
    IFS=$alt_ifs
    [ $# -eq 4 ] || return 1
    for z in "$@"; do
        [ -n "$z" ] && [ "$z" -le 255 ] 2>/dev/null || return 1
    done
}

regeln_an() {
    heim=$1
    for ipt in "$B/iptables" "$B/ip6tables"; do
        "$ipt" -w -N $KETTE 2>/dev/null
        "$ipt" -w -F $KETTE
    done
    "$B/iptables" -w -A $KETTE -s 127.0.0.0/8 -j ACCEPT
    "$B/iptables" -w -A $KETTE -s 100.64.0.0/10 -j ACCEPT
    [ "$heim" != keins ] && "$B/iptables" -w -A $KETTE -s "$heim" -j ACCEPT
    "$B/iptables" -w -A $KETTE -j DROP
    "$B/ip6tables" -w -A $KETTE -s ::1/128 -j ACCEPT
    "$B/ip6tables" -w -A $KETTE -s fd7a:115c:a1e0::/48 -j ACCEPT
    "$B/ip6tables" -w -A $KETTE -j DROP
    for ipt in "$B/iptables" "$B/ip6tables"; do
        "$ipt" -w -C INPUT -p tcp --dport $PORT -j $KETTE 2>/dev/null \
            || "$ipt" -w -I INPUT -p tcp --dport $PORT -j $KETTE
    done
}

regeln_aus() {
    for ipt in "$B/iptables" "$B/ip6tables"; do
        while "$ipt" -w -D INPUT -p tcp --dport $PORT -j $KETTE 2>/dev/null; do :; done
        "$ipt" -w -F $KETTE 2>/dev/null
        "$ipt" -w -X $KETTE 2>/dev/null
    done
}

adbd_neu() {
    "$B/setprop" service.adb.tcp.port "$1"
    "$B/stop" adbd
    "$B/start" adbd
}

zustand() {
    echo "ROOT=$(id -u)"
    p=$("$B/getprop" service.adb.tcp.port)
    echo "PORT=$p"
    [ "$("$B/settings" get global adb_enabled 2>/dev/null)" = 1 ] && echo "USB_DEBUG=1"
    # Lauscht jemand auf 5555 (hex 15B3, Zustand 0A = LISTEN)?
    cat "$PROCNET/tcp" "$PROCNET/tcp6" 2>/dev/null | grep -q ':15B3 [0-9A-F:]* 0A' && echo "LAUSCHT=1"
    "$B/iptables" -w -C INPUT -p tcp --dport $PORT -j $KETTE 2>/dev/null && echo "SCHUTZ=1"
    [ -f "$BOOT" ] && echo "BOOT=1"
    [ -f "$CONF" ] && sed -n 's/^HEIM=/HEIM=/p' "$CONF"
    # Adressen: Tailscale (100.64.0.0/10) und WLAN
    ip -o -4 addr show 2>/dev/null | while read -r nr name fam adr rest; do
        a=${adr%/*}
        case $a in
            100.6[4-9].* | 100.[7-9][0-9].* | 100.1[01][0-9].* | 100.12[0-7].*) echo "TAILSCALE=$a" ;;
        esac
        [ "$name" = wlan0 ] && echo "WLAN=$adr"
    done
}

an() {
    netz_ok "$1" || { echo "FEHLER=Ungültiges Heimnetz: $1 (Form 192.168.0.0/24 oder keins)"; return 1; }
    echo "HEIM=$1" > "$CONF"
    # USB-Debugging muss an sein, sonst laeuft adbd gar nicht.
    "$B/settings" put global adb_enabled 1
    regeln_an "$1"
    adbd_neu $PORT
    mkdir -p "${BOOT%/*}"
    {
        echo '#!/system/bin/sh'
        echo '# Root Tools: oeffnet ADB uebers Netz beim Hochfahren (nur Tailscale und Heimnetz).'
        echo '# Die App loescht diese Datei, wenn ADB uebers Netz aus ist.'
        echo "while [ \"\$(getprop sys.boot_completed)\" != 1 ]; do sleep 2; done"
        echo "[ -f $CONF ] && $SH $D/adb.sh boot"
    } > "$BOOT.neu" && chmod 755 "$BOOT.neu" && mv -f "$BOOT.neu" "$BOOT"
}

mkdir -p "$D"
case $1 in
    zustand) zustand ;;
    an)
        an "$2" || exit 1
        sleep 1
        zustand
        ;;
    boot)
        heim=$(sed -n 's/^HEIM=//p' "$CONF")
        netz_ok "$heim" || heim=keins
        regeln_an "$heim"
        adbd_neu $PORT
        ;;
    aus)
        rm -f "$CONF" "$BOOT"
        adbd_neu -1
        regeln_aus
        sleep 1
        zustand
        ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
