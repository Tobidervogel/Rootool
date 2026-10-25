#!/system/bin/sh
# Root Tools – MAC-Adresse
#
# Die App kopiert diese Datei bei jedem Aufruf nach /data/adb/roottools/mac.sh und ruft sie als root auf:
#   mac.sh zustand            Stand als SCHLUESSEL=wert-Zeilen (liest die App)
#   mac.sh setzen MAC ja|nein Eigene MAC festlegen, ja = auch nach Neustart (Magisk service.d)
#   mac.sh aus                Zurueck zu Android: Android wuerfelt wieder pro WLAN eine MAC
#   mac.sh start              Waechter im Hintergrund starten (Boot-Skript)
#   mac.sh waechter           Der Waechter selbst
#
# Warum es so geht (Android 16, packages/modules/Wifi, am 25.10.2026 im Quelltext nachgesehen):
# - Android setzt bei jeder Verbindung und jeder Trennung eine Zufalls-MAC, solange das Overlay
#   config_wifi_connected_mac_randomization_supported an ist (ClientModeImpl). Seit Android 16 liest
#   WifiGlobals den Wert ueber den WifiResourceCache, und den kann root zur Laufzeit ueberschreiben:
#   "cmd wifi force-overlay-config-value bool <name> enabled false". Das gilt bis zum Neustart.
#   Danach fasst Android die MAC nicht mehr an, auch nicht die Geraete-MAC.
# - Beim Einschalten des WLANs legt der Treiber die Schnittstelle mit der Geraete-MAC neu an. Der Waechter
#   merkt das (alle $TAKT Sekunden) und setzt die eigene MAC wieder.
# - Die Adresse laesst sich nur bei unten liegender Schnittstelle aendern. runter/Adresse/hoch laufen in EINEM
#   toybox-ifconfig-Aufruf (Reihenfolge wie angegeben), so kurz, dass Android den Ausfall nicht bemerkt
#   (WifiNative fragt den Zustand erst spaeter auf seinem Handler ab). Der WLAN-HAL macht es bei seiner
#   Zufalls-MAC genauso.
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/mac_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
B=${ROOTTOOLS_BIN:-/system/bin}
SH=${ROOTTOOLS_SH:-/system/bin/sh}
NET=${ROOTTOOLS_NET:-/sys/class/net}
BOOT=${ROOTTOOLS_BOOT:-/data/adb/service.d/roottools-mac.sh}
TAKT=${ROOTTOOLS_TAKT:-2}
CONF=$D/mac.conf
LOG=$D/mac.log
PIDF=$D/mac.pid
IFACE=wlan0
SCHALTER=config_wifi_connected_mac_randomization_supported

log() {
    echo "$(date '+%d.%m. %H:%M:%S') $*" >> "$LOG"
}

# Unicast (zweite Ziffer gerade), Kleinbuchstaben, nicht 00:00:00:00:00:00. Die App prueft schon, das hier ist
# die zweite Sperre, weil die MAC in Befehle eingesetzt wird.
gueltig() {
    case $1 in
        [0-9a-f][02468ace]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]) ;;
        *) return 1 ;;
    esac
    [ "$1" != 00:00:00:00:00:00 ]
}

lesen() {
    MAC=
    while IFS='=' read -r k v; do
        case $k in
            MAC) MAC=$v ;;
        esac
    done < "$CONF"
    gueltig "$MAC" || MAC=
}

waechter_pid() {
    p=$(cat "$PIDF" 2>/dev/null)
    [ -n "$p" ] && grep -q waechter "/proc/$p/cmdline" 2>/dev/null && echo "$p"
}

zufall_aus() {
    "$B/cmd" wifi force-overlay-config-value bool $SCHALTER enabled false 2>&1 | grep -qx true
}

zufall_zurueck() {
    "$B/cmd" wifi force-overlay-config-value bool $SCHALTER disabled > /dev/null 2>&1
}

# 0 = Schnittstelle oben (IFF_UP), sonst unten oder unbekannt.
oben() {
    { read -r flags < "$NET/$IFACE/flags"; } 2>/dev/null || return 2
    case $flags in
        0x*) ;;
        *) return 2 ;;
    esac
    [ $(( $flags & 1 )) -eq 1 ]
}

# Setzt $MAC auf $IFACE. Liegt die Schnittstelle oben, kommt sie im selben Aufruf wieder hoch.
# Was ifconfig bei einem Fehler sagt, steht danach in $grund.
mac_setzen() {
    if oben; then
        grund=$("$B/ifconfig" "$IFACE" down hw ether "$MAC" up 2>&1) && return 0
        # ifconfig bricht beim ersten Fehler ab: dann nie unten liegen lassen, sonst startet Android das WLAN neu.
        "$B/ifconfig" "$IFACE" up > /dev/null 2>&1
        return 1
    fi
    grund=$("$B/ifconfig" "$IFACE" hw ether "$MAC" 2>&1)
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

starten() {
    stoppen
    # Eigene Sitzung, alle Kanaele zu: so lebt der Waechter weiter, wenn su und die App fertig sind.
    if command -v setsid > /dev/null 2>&1; then
        setsid "$SH" "$D/mac.sh" waechter < /dev/null > /dev/null 2>&1 &
    else
        nohup "$SH" "$D/mac.sh" waechter < /dev/null > /dev/null 2>&1 &
    fi
}

boot_an() {
    mkdir -p "${BOOT%/*}"
    {
        echo '#!/system/bin/sh'
        echo '# Root Tools: startet beim Hochfahren den MAC-Waechter. Die App loescht diese Datei,'
        echo '# wenn "Nach Neustart behalten" aus ist.'
        echo "[ -f $CONF ] && $SH $D/mac.sh start"
    } > "$BOOT.neu" && chmod 755 "$BOOT.neu" && mv -f "$BOOT.neu" "$BOOT"
}

zustand() {
    echo "ROOT=$(id -u)"
    { read -r a < "$NET/$IFACE/address"; } 2>/dev/null && echo "AKTUELL=$a"
    # Die Geraete-MAC zeigt nur ein neueres ip an ("permaddr", ab Linux 5.6), und nur, solange eine andere gilt.
    perm=$(ip link show dev $IFACE 2>/dev/null | grep -o 'permaddr [0-9a-f:]*')
    [ -n "$perm" ] && echo "ORIGINAL=${perm#permaddr }"
    hilfe=$("$B/cmd" wifi help 2>/dev/null)
    case $hilfe in
        *force-overlay-config-value*) echo "SCHALTER=1" ;;
    esac
    wert=$("$B/cmd" wifi get-overlay-config-values 2>/dev/null | grep "Name: $SCHALTER,")
    [ -n "$wert" ] && echo "ZUFALL=${wert##*value: }"
    "$B/cmd" wifi status 2>/dev/null | grep '^Wifi is ' | sed 's/^/WLAN=/'
    [ -f "$CONF" ] && lesen && [ -n "$MAC" ] && echo "ZIEL=$MAC"
    [ -n "$(waechter_pid)" ] && echo "WAECHTER=1"
    [ -f "$BOOT" ] && echo "BOOT=1"
    tail -n 12 "$LOG" 2>/dev/null | sed 's/^/LOG=/'
}

waechter() {
    alt=$(waechter_pid)
    [ -n "$alt" ] && [ "$alt" != $$ ] && exit 0
    echo $$ > "$PIDF"
    if [ -f "$LOG" ] && [ "$(wc -l < "$LOG")" -gt 400 ]; then
        tail -n 200 "$LOG" > "$LOG.neu" && mv -f "$LOG.neu" "$LOG"
    fi
    log "Waechter gestartet"

    # So frueh wie moeglich: der Schalter greift, sobald der WLAN-Dienst laeuft, also schon vor der ersten
    # Verbindung nach dem Hochfahren.
    while ! zufall_aus; do
        if [ "$("$B/getprop" sys.boot_completed)" = 1 ]; then
            log "Android kennt den Schalter nicht: die eigene MAC haelt nur bis zur naechsten Verbindung"
            break
        fi
        sleep 1
    done

    fenster=0
    versuche=0
    unten_versucht=
    while [ -f "$CONF" ] && meins; do
        lesen 2>/dev/null
        if [ -n "$MAC" ] && { read -r jetzt < "$NET/$IFACE/address"; } 2>/dev/null && [ "$jetzt" != "$MAC" ]; then
            if oben; then
                unten_versucht=
                # Jeder Versuch bei laufendem WLAN trennt die Verbindung kurz. Halten Android oder der Treiber
                # dagegen, wuerde das WLAN endlos trennen: darum hoechstens 5 Versuche in 2 Minuten.
                s=$(date +%s)
                if [ $((s - fenster)) -ge 120 ]; then
                    fenster=$s
                    versuche=0
                fi
                versuche=$((versuche + 1))
                if [ $versuche -gt 5 ]; then
                    log "Gestoppt: die MAC wurde 5-mal in 2 Minuten zurueckgesetzt"
                    break
                fi
                zufall_aus
                if mac_setzen; then
                    log "$jetzt -> $MAC"
                else
                    log "Setzen fehlgeschlagen, bleibt $jetzt: $grund"
                fi
            elif [ "$unten_versucht" != "$jetzt" ]; then
                # WLAN unten: Setzen trennt nichts. Nimmt der Treiber die MAC so nicht an (WLAN-Chip aus), wird es pro
                # Adresse nur einmal versucht und gemeldet; sobald das WLAN hochkommt, greift der Zweig oben.
                unten_versucht=$jetzt
                zufall_aus
                if mac_setzen; then
                    unten_versucht=
                    log "$jetzt -> $MAC (WLAN unten)"
                else
                    log "WLAN unten, Treiber nimmt die MAC noch nicht an: $grund"
                fi
            fi
        fi
        sleep "$TAKT"
    done
    [ "$(cat "$PIDF" 2>/dev/null)" = $$ ] && rm -f "$PIDF"
    log "Waechter beendet"
}

mkdir -p "$D"
case $1 in
    zustand)
        zustand
        ;;
    setzen)
        mac=$(echo "$2" | tr 'A-F' 'a-f')
        if ! gueltig "$mac"; then
            echo "FEHLER=Ungueltige MAC: $2"
            exit 2
        fi
        printf 'MAC=%s\n' "$mac" > "$CONF.neu" && mv -f "$CONF.neu" "$CONF"
        if [ "$3" = ja ]; then boot_an; else rm -f "$BOOT"; fi
        log "Eigene MAC: $mac"
        starten
        # Kurz warten, damit der Stand schon die neue MAC zeigt.
        sleep 3
        zustand
        ;;
    aus)
        rm -f "$CONF" "$BOOT"
        stoppen
        zufall_zurueck
        log "Aus: Android verwaltet die MAC wieder"
        # Neu starten, damit Android sofort wieder seine Zufalls-MAC setzt und nicht erst bei der naechsten Verbindung.
        if "$B/cmd" wifi status 2>/dev/null | grep -q '^Wifi is enabled'; then
            "$B/cmd" wifi set-wifi-enabled disabled > /dev/null 2>&1
            sleep 3
            "$B/cmd" wifi set-wifi-enabled enabled > /dev/null 2>&1
            sleep 2
        fi
        zustand
        ;;
    start)
        [ -f "$CONF" ] && starten
        ;;
    waechter)
        waechter
        ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
