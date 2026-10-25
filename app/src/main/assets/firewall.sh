#!/system/bin/sh
# Root Tools – Firewall pro App
#
#   firewall.sh zustand    Regeln, was gerade gesperrt ist, Waechter
#   firewall.sh setzen     Regeln aus firewall.sh.eingabe uebernehmen und sofort anwenden
#   firewall.sh start      beim Hochfahren: anwenden, Waechter starten (Boot-Skript)
#   firewall.sh waechter   prueft jede Minute die Zeitplaene
#   firewall.sh aus        alles freigeben, Regeln und Boot-Skript loeschen
#
# Regeln, eine pro Zeile:  PAKET|immer   oder   PAKET|zeit|22:00|07:00  (gesperrt von bis, auch ueber Mitternacht)
#
# Wie: Android hat eingebaute Sperrlisten im Netz-Stack (eBPF). "cmd connectivity" schaltet die Liste
# FIREWALL_CHAIN_OEM_DENY_3 ein (set-chain3-enabled) und sperrt darin einzelne Pakete
# (set-package-networking-enabled false PAKET). Kein VPN, also kein Streit mit Tailscale. Android vergisst das beim
# Neustart; darum stellt das Boot-Skript es wieder her. Quelle: ConnectivityService.java, Zweig android16-release.
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/firewall_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
B=${ROOTTOOLS_BIN:-/system/bin}
SH=${ROOTTOOLS_SH:-/system/bin/sh}
BOOT=${ROOTTOOLS_BOOT:-/data/adb/service.d/roottools-firewall.sh}
TAKT=${ROOTTOOLS_TAKT:-60}
REGELN=$D/firewall.regeln
GESPERRT=$D/firewall.gesperrt
PIDF=$D/firewall.pid
LOG=$D/firewall.log

log() {
    echo "$(date '+%d.%m. %H:%M:%S') $*" >> "$LOG"
}

# Minuten seit Mitternacht; ohne fuehrende Null, sonst haelt die Shell "08" fuer eine Oktalzahl.
minuten() {
    h=$(date +%H)
    m=$(date +%M)
    echo $(( ${h#0} * 60 + ${m#0} ))
}

zeit_ok() {
    case $1 in
        [01][0-9]:[0-5][0-9] | 2[0-3]:[0-5][0-9]) return 0 ;;
    esac
    return 1
}

in_minuten() {
    h=${1%%:*}
    m=${1##*:}
    echo $(( ${h#0} * 60 + ${m#0} ))
}

# Soll die Regel jetzt sperren? $1 = Art, $2/$3 = von/bis
sperrt_jetzt() {
    [ "$1" = immer ] && return 0
    jetzt=$(minuten)
    von=$(in_minuten "$2")
    bis=$(in_minuten "$3")
    if [ "$von" -lt "$bis" ]; then
        [ "$jetzt" -ge "$von" ] && [ "$jetzt" -lt "$bis" ]
    else
        [ "$jetzt" -ge "$von" ] || [ "$jetzt" -lt "$bis" ]
    fi
}

paket_ok() {
    case $1 in
        *[!A-Za-z0-9._]* | "" | .*) return 1 ;;
    esac
}

sperren() {
    "$B/cmd" connectivity set-package-networking-enabled false "$1" > /dev/null 2>&1
}

freigeben() {
    "$B/cmd" connectivity set-package-networking-enabled true "$1" > /dev/null 2>&1
}

# Bringt die Sperren auf den Stand der Regeln. Merkt sich, was gesperrt ist, und ruft cmd nur fuer Aenderungen.
anwenden() {
    "$B/cmd" connectivity set-chain3-enabled true > /dev/null 2>&1
    soll=
    if [ -f "$REGELN" ]; then
        while IFS='|' read -r p art von bis; do
            paket_ok "$p" || continue
            sperrt_jetzt "$art" "$von" "$bis" && soll="$soll $p"
        done < "$REGELN"
    fi
    for p in $(cat "$GESPERRT" 2>/dev/null); do
        case " $soll " in
            *" $p "*) ;;
            *) freigeben "$p" && log "frei: $p" ;;
        esac
    done
    : > "$GESPERRT.neu"
    for p in $soll; do
        if ! grep -qx "$p" "$GESPERRT" 2>/dev/null || [ "$1" = alle ]; then
            sperren "$p" && log "gesperrt: $p"
        fi
        echo "$p" >> "$GESPERRT.neu"
    done
    mv -f "$GESPERRT.neu" "$GESPERRT"
}

waechter_pid() {
    p=$(cat "$PIDF" 2>/dev/null)
    [ -n "$p" ] && grep -q waechter "/proc/$p/cmdline" 2>/dev/null && echo "$p"
}

stoppen() {
    p=$(waechter_pid)
    rm -f "$PIDF"
    [ -n "$p" ] || return 0
    kill "$p" 2>/dev/null
    # mksh (die Shell von Android) faengt TERM ab und beendet sich dann nicht immer: nach kurzer Frist hart beenden.
    # Ausserdem hoert jeder Waechter von selbst auf, sobald die PID-Datei nicht mehr ihm gehoert (meins).
    sleep 0.3
    kill -0 "$p" 2>/dev/null && kill -9 "$p" 2>/dev/null
    return 0
}

# Gehoert die PID-Datei noch diesem Waechter?
meins() {
    { read -r wer < "$PIDF"; } 2>/dev/null && [ "$wer" = "$$" ]
}

# Ein Waechter laeuft nur, wenn es Zeitplaene gibt.
starten() {
    stoppen
    grep -q '|zeit|' "$REGELN" 2>/dev/null || return 0
    if command -v setsid > /dev/null 2>&1; then
        setsid "$SH" "$D/firewall.sh" waechter < /dev/null > /dev/null 2>&1 &
    else
        nohup "$SH" "$D/firewall.sh" waechter < /dev/null > /dev/null 2>&1 &
    fi
}

boot_anpassen() {
    if [ -s "$REGELN" ]; then
        mkdir -p "${BOOT%/*}"
        {
            echo '#!/system/bin/sh'
            echo '# Root Tools: stellt die Firewall beim Hochfahren wieder her. Die App loescht diese Datei,'
            echo '# wenn keine Regeln mehr da sind.'
            echo "while [ \"\$(getprop sys.boot_completed)\" != 1 ]; do sleep 2; done"
            echo "[ -s $REGELN ] && $SH $D/firewall.sh start"
        } > "$BOOT.neu" && chmod 755 "$BOOT.neu" && mv -f "$BOOT.neu" "$BOOT"
    else
        rm -f "$BOOT"
    fi
}

zustand() {
    echo "ROOT=$(id -u)"
    c=$("$B/cmd" connectivity get-chain3-enabled 2>/dev/null)
    case $c in *enabled*) case $c in *disabled*) ;; *) echo "KETTE=1" ;; esac ;; esac
    [ -f "$REGELN" ] && sed 's/^/REGEL=/' "$REGELN"
    [ -f "$GESPERRT" ] && sed 's/^/GESPERRT=/' "$GESPERRT"
    [ -n "$(waechter_pid)" ] && echo "WAECHTER=1"
    [ -f "$BOOT" ] && echo "BOOT=1"
    tail -n 8 "$LOG" 2>/dev/null | sed 's/^/LOG=/'
}

waechter() {
    alt=$(waechter_pid)
    [ -n "$alt" ] && [ "$alt" != $$ ] && exit 0
    echo $$ > "$PIDF"
    while meins && grep -q '|zeit|' "$REGELN" 2>/dev/null; do
        anwenden
        sleep "$TAKT"
    done
    [ "$(cat "$PIDF" 2>/dev/null)" = $$ ] && rm -f "$PIDF"
}

mkdir -p "$D"
case $1 in
    zustand) zustand ;;
    setzen)
        # Nur gueltige Zeilen uebernehmen; alles andere faellt still heraus.
        : > "$REGELN.neu"
        while IFS='|' read -r p art von bis; do
            paket_ok "$p" || continue
            case $art in
                immer) echo "$p|immer" >> "$REGELN.neu" ;;
                zeit)
                    zeit_ok "$von" && zeit_ok "$bis" && [ "$von" != "$bis" ] && echo "$p|zeit|$von|$bis" >> "$REGELN.neu"
                    ;;
            esac
        done < "$D/firewall.sh.eingabe"
        mv -f "$REGELN.neu" "$REGELN"
        rm -f "$D/firewall.sh.eingabe"
        anwenden
        boot_anpassen
        starten
        zustand
        ;;
    start)
        # Nach dem Neustart weiss Android nichts mehr von den Sperren: alle neu setzen.
        rm -f "$GESPERRT"
        anwenden alle
        starten
        ;;
    waechter) waechter ;;
    aus)
        stoppen
        rm -f "$REGELN" "$BOOT"
        anwenden
        "$B/cmd" connectivity set-chain3-enabled false > /dev/null 2>&1
        log "Firewall aus"
        zustand
        ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
