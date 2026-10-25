#!/system/bin/sh
# Root Tools – Werbeblocker ueber die hosts-Datei
#
#   hosts.sh zustand          aktiv?, Anzahl gesperrter Adressen, Stand der Liste, eigene Sperren und Ausnahmen
#   hosts.sh schreiben QUELLE [LISTENNAME]
#                             hosts aus QUELLE (heruntergeladene Liste, oder "-" fuer die zuletzt benutzte) plus
#                             eigenen Sperren, ohne Ausnahmen. Eigene Listen in hosts.sh.eingabe:
#                             "SPERRE domain" / "AUSNAHME domain"
#   hosts.sh aus              hosts wieder neutral, Modul wird beim naechsten Start entfernt
#
# Wie: Magisk legt das Modul "hosts" (Systemless Hosts, wie in Magisks Einstellungen und bei AdAway) ueber
# /system/etc/hosts. Android fragt diese Datei vor jedem DNS. Root Tools schreibt die Datei im Modul an Ort und Stelle
# (gleicher Inode), dann gilt sie sofort; nur beim allerersten Mal braucht Magisk einen Neustart, um sie
# einzuhaengen. Browser mit eigenem DNS (z. B. "Sicheres DNS" mit festem Anbieter) fragen die Datei nicht.
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/hosts_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
MOD=${ROOTTOOLS_HOSTSMODUL:-/data/adb/modules/hosts}
SYS=${ROOTTOOLS_SYSHOSTS:-/system/etc/hosts}
DATEI=$MOD/system/etc/hosts
QUELLE=$D/hosts.quelle
EIGEN=$D/hosts.eigen
MARKE="# Root Tools Werbeblocker"

domain_ok() {
    case $1 in
        "" | *[!a-z0-9.-]* | .* | *. | *..*) return 1 ;;
    esac
}

# grep -f mit leerer Musterdatei verhaelt sich je nach grep anders (busybox filtert dann alles weg).
ohne_ausnahmen() {
    if [ -s "$D/hosts.ausnahmen" ]; then
        grep -v -x -F -f "$D/hosts.ausnahmen"
    else
        cat
    fi
}

modul_anlegen() {
    [ -d "$MOD" ] && return 0
    mkdir -p "${DATEI%/*}" || return 1
    {
        echo "id=hosts"
        echo "name=Systemless Hosts"
        echo "version=1.0"
        echo "versionCode=1"
        echo "author=Magisk"
        echo "description=Magisk app built-in systemless hosts module"
    } > "$MOD/module.prop"
    cat "$SYS" > "$DATEI" 2>/dev/null || printf '127.0.0.1 localhost\n::1 ip6-localhost\n' > "$DATEI"
    touch "$MOD/update"
    echo "NEU=1"
}

zustand() {
    echo "ROOT=$(id -u)"
    [ -d "$MOD" ] && echo "MODUL=1"
    [ -f "$MOD/remove" ] && echo "ENTFERNEN=1"
    if [ -f "$DATEI" ] && grep -q "^$MARKE" "$DATEI"; then
        echo "UNSER=1"
        echo "ANZAHL=$(grep -c '^0\.0\.0\.0 ' "$DATEI")"
        sed -n 's/^# Liste: //p' "$DATEI" | head -n 1 | sed 's/^/LISTE=/'
        sed -n 's/^# Stand: //p' "$DATEI" | head -n 1 | sed 's/^/STAND=/'
    fi
    # Wirkt sie schon? Dann liest Android unter /system/etc/hosts unsere Datei.
    grep -q "^$MARKE" "$SYS" 2>/dev/null && echo "AKTIV=1"
    [ -f "$EIGEN" ] && sed 's/^/EIGEN=/' "$EIGEN"
}

schreiben() {
    q=$1
    # Die App schickt Leerzeichen als _, weil Argumente keine Leerzeichen haben duerfen.
    name=$(echo "${2:-Liste}" | tr '_' ' ')
    [ "$q" = - ] && q=$QUELLE
    [ -s "$q" ] || { echo "FEHLER=Keine Liste da. Erst laden."; return 1; }
    # Eigene Listen pruefen und merken
    if [ -f "$D/hosts.sh.eingabe" ]; then
        : > "$EIGEN.neu"
        while read -r art dom; do
            dom=$(echo "$dom" | tr 'A-Z' 'a-z')
            case $art in SPERRE | AUSNAHME) domain_ok "$dom" && echo "$art $dom" >> "$EIGEN.neu" ;; esac
        done < "$D/hosts.sh.eingabe"
        mv -f "$EIGEN.neu" "$EIGEN"
        rm -f "$D/hosts.sh.eingabe"
    fi
    [ "$q" = "$QUELLE" ] || cp "$q" "$QUELLE" || { echo "FEHLER=Liste nicht kopierbar"; return 1; }
    modul_anlegen || { echo "FEHLER=Magisk-Modul nicht anlegbar"; return 1; }
    t=$D/hosts.neu
    sed -n 's/^AUSNAHME //p' "$EIGEN" 2>/dev/null > "$D/hosts.ausnahmen"
    {
        echo "$MARKE"
        echo "# Liste: $name"
        echo "# Stand: $(date '+%d.%m.%Y %H:%M')"
        echo "127.0.0.1 localhost"
        echo "::1 ip6-localhost"
        {
            # Nur "0.0.0.0 domain" oder "127.0.0.1 domain" aus der Liste, ohne localhost-Eintraege
            # -E statt \| im einfachen Ausdruck: das koennen nicht alle sed (Androids toybox nutzt die BSD-Regeln)
            sed -n -E 's/^(0\.0\.0\.0|127\.0\.0\.1)[[:space:]]+([A-Za-z0-9._-]+).*/\2/p' "$QUELLE" | tr 'A-Z' 'a-z'
            sed -n 's/^SPERRE //p' "$EIGEN" 2>/dev/null
        } | grep -v -x -E 'localhost|localhost\.localdomain|local|0\.0\.0\.0|broadcasthost|ip6-.*' \
          | ohne_ausnahmen | sort -u | sed 's/^/0.0.0.0 /'
    } > "$t"
    # An Ort und Stelle schreiben (cat statt mv): dann bleibt der Inode, und die eingehaengte Datei aendert sich sofort.
    cat "$t" > "$DATEI" && rm -f "$t" "$D/hosts.ausnahmen"
    rm -f "$MOD/remove"
    zustand
}

aus() {
    if [ -f "$DATEI" ]; then
        printf '127.0.0.1 localhost\n::1 ip6-localhost\n' > "$DATEI"
        touch "$MOD/remove"
    fi
    zustand
}

mkdir -p "$D"
case $1 in
    zustand) zustand ;;
    schreiben) schreiben "$2" "$3" ;;
    aus) aus ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
