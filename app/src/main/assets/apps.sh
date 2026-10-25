#!/system/bin/sh
# Root Tools – App-Sicherung
#
#   apps.sh sicherungen                    alle Sicherungen: SICHERUNG=Paket|Ordner|KB|Version
#   apps.sh sichern PAKET                  APKs und Daten nach /sdcard/RootTools/Apps/PAKET/<Datum>
#   apps.sh zurueck PAKET ORDNER daten     nur die Daten in die installierte App zurueckspielen
#   apps.sh zurueck PAKET ORDNER alles     APKs aus der Sicherung installieren, dann die Daten
#   apps.sh tauschen PAKET APK             neue APK mit anderer Signatur: sichern, deinstallieren, APK
#                                          installieren, Daten zurueck. Scheitert die Installation, kommt die alte
#                                          App samt Daten wieder.
#   apps.sh loeschen PAKET ORDNER          eine Sicherung loeschen
#
# Gesichert werden /data/data/PAKET und /data/user_de/0/PAKET ohne cache, code_cache und lib, dazu die APKs.
# Nicht dabei: /sdcard/Android/data (oft riesig, und die Rechte dort sind heikel). Was eine App mit dem
# Android-Schluesselspeicher verschluesselt, laesst sich nach einer Neuinstallation nicht mehr oeffnen: Die
# Schluessel gehoeren zur alten Installation.
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/apps_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
B=${ROOTTOOLS_BIN:-/system/bin}
SD=${ROOTTOOLS_SD:-/sdcard}
DATA=${ROOTTOOLS_DATA:-/data/data}
DE=${ROOTTOOLS_DE:-/data/user_de/0}
APPS=$SD/RootTools/Apps

paket_ok() {
    case $1 in
        *[!A-Za-z0-9._]* | "" | .* ) return 1 ;;
    esac
}

version() {
    "$B/dumpsys" package "$1" 2>/dev/null | grep -m 1 'versionName=' | sed 's/.*versionName=//'
}

# Packt den Inhalt von $1 ohne cache, code_cache und lib nach $2. Ohne --exclude, das koennen nicht alle tar.
packen() {
    [ -d "$1" ] || return 0
    teile=
    for e in $(ls -A "$1"); do
        case $e in
            cache | code_cache | lib) ;;
            *) teile="$teile $e" ;;
        esac
    done
    [ -n "$teile" ] || return 0
    ( cd "$1" && tar -czf "$2" $teile )
}

# Leert $1 (ausser cache, code_cache, lib), packt $2 aus und gibt alles dem Besitzer von $1 zurueck.
auspacken() {
    [ -d "$1" ] || { echo "FEHLER=$1 fehlt, ist die App installiert?"; return 1; }
    uid=$(stat -c %u "$1")
    gid=$(stat -c %g "$1")
    for e in $(ls -A "$1"); do
        case $e in
            cache | code_cache | lib) ;;
            *) rm -rf "${1:?}/$e" ;;
        esac
    done
    [ -f "$2" ] || return 0
    tar -xzf "$2" -C "$1" || { echo "FEHLER=Auspacken von ${2##*/} fehlgeschlagen"; return 1; }
    for e in $(ls -A "$1"); do
        case $e in
            cache | code_cache | lib) ;;
            *) chown -R "$uid:$gid" "$1/$e" ;;
        esac
    done
    # SELinux-Etiketten neu (-D: auch unter /data/data, sonst laesst restorecon App-Daten aus)
    "$B/restorecon" -RFD "$1" > /dev/null 2>&1 || "$B/restorecon" -RF "$1" > /dev/null 2>&1
    return 0
}

sichern() {
    p=$1
    "$B/pm" path "$p" > /dev/null 2>&1 || { echo "FEHLER=$p ist nicht installiert"; return 1; }
    ziel=$APPS/$p/$(date +%Y-%m-%d_%H%M%S)
    mkdir -p "$ziel/apk" || { echo "FEHLER=Kann $ziel nicht anlegen"; return 1; }
    echo "SCHRITT=APK"
    for a in $("$B/pm" path "$p" | sed 's/^package://'); do
        cp "$a" "$ziel/apk/" || { echo "FEHLER=APK nicht kopierbar: $a"; return 1; }
    done
    {
        echo "paket=$p"
        echo "version=$(version "$p")"
        echo "datum=$(date '+%d.%m.%Y %H:%M')"
    } > "$ziel/info.txt"
    # Stoppen, damit keine Datenbank mitten im Schreiben gesichert wird.
    "$B/am" force-stop "$p" > /dev/null 2>&1
    echo "SCHRITT=Daten"
    packen "$DATA/$p" "$ziel/data.tar.gz" || { echo "FEHLER=Daten nicht lesbar"; return 1; }
    packen "$DE/$p" "$ziel/de.tar.gz" || { echo "FEHLER=Geraetedaten nicht lesbar"; return 1; }
    echo "SCHRITT=Prüfsummen"
    ( cd "$ziel" && find . -type f ! -name SHA256SUMS | sort | xargs sha256sum > SHA256SUMS )
    echo "FERTIG=${ziel##*/}"
}

# Installiert alle APKs aus Ordner $1 in einer Sitzung (auch geteilte APKs).
installieren() {
    s=$("$B/pm" install-create -r 2>&1 | sed -n 's/.*\[\([0-9]*\)\].*/\1/p')
    [ -n "$s" ] || { echo "FEHLER=Installation liess sich nicht starten"; return 1; }
    for a in "$1"/*.apk; do
        [ -f "$a" ] || continue
        "$B/pm" install-write -S "$(stat -c %s "$a")" "$s" "${a##*/}" "$a" > /dev/null 2>&1 \
            || { "$B/pm" install-abandon "$s" > /dev/null 2>&1; echo "FEHLER=APK nicht schreibbar: ${a##*/}"; return 1; }
    done
    antwort=$("$B/pm" install-commit "$s" 2>&1)
    case $antwort in
        *Success*) return 0 ;;
        *UPDATE_INCOMPATIBLE* | *SIGNATURES*) echo "FEHLER=Andere Signatur: erst deinstallieren (\"Neue APK einspielen\")" ;;
        *) echo "FEHLER=Installation: $antwort" ;;
    esac
    return 1
}

zurueck() {
    p=$1
    q=$APPS/$p/$2
    [ -d "$q" ] || { echo "FEHLER=Sicherung $2 fehlt"; return 1; }
    ( cd "$q" && sha256sum -c SHA256SUMS > /dev/null 2>&1 ) || { echo "FEHLER=Sicherung ist beschädigt (Prüfsummen)"; return 1; }
    if [ "$3" = alles ] || ! "$B/pm" path "$p" > /dev/null 2>&1; then
        echo "SCHRITT=APK"
        installieren "$q/apk" || return 1
    fi
    "$B/am" force-stop "$p" > /dev/null 2>&1
    echo "SCHRITT=Daten"
    auspacken "$DATA/$p" "$q/data.tar.gz" || return 1
    auspacken "$DE/$p" "$q/de.tar.gz" || return 1
    echo "FERTIG=$2"
}

tauschen() {
    p=$1
    neu=$2
    [ -f "$neu" ] || { echo "FEHLER=Neue APK fehlt: $neu"; return 1; }
    ausgabe=$(sichern "$p")
    echo "$ausgabe" | grep '^FEHLER=' && return 1
    ordner=$(echo "$ausgabe" | sed -n 's/^FERTIG=//p')
    echo "SCHRITT=Deinstallieren"
    "$B/pm" uninstall "$p" > /dev/null 2>&1
    echo "SCHRITT=Neue APK"
    t=$D/tauschen
    rm -rf "$t" && mkdir -p "$t" && cp "$neu" "$t/base.apk"
    if ! installieren "$t"; then
        rm -rf "$t"
        echo "SCHRITT=Alte App zurück"
        installieren "$APPS/$p/$ordner/apk" > /dev/null
        auspacken "$DATA/$p" "$APPS/$p/$ordner/data.tar.gz" > /dev/null
        auspacken "$DE/$p" "$APPS/$p/$ordner/de.tar.gz" > /dev/null
        echo "FEHLER=Neue APK liess sich nicht installieren, die alte App ist mit ihren Daten zurück"
        return 1
    fi
    rm -rf "$t"
    echo "SCHRITT=Daten"
    auspacken "$DATA/$p" "$APPS/$p/$ordner/data.tar.gz" || return 1
    auspacken "$DE/$p" "$APPS/$p/$ordner/de.tar.gz" || return 1
    echo "FERTIG=$ordner"
}

sicherungen() {
    [ -d "$APPS" ] || return 0
    for p in $(ls -1 "$APPS"); do
        for o in $(ls -1 "$APPS/$p" | sort -r); do
            [ -d "$APPS/$p/$o" ] || continue
            set -- $(du -sk "$APPS/$p/$o" 2>/dev/null)
            v=$(sed -n 's/^version=//p' "$APPS/$p/$o/info.txt" 2>/dev/null)
            echo "SICHERUNG=$p|$o|${1:-0}|$v"
        done
    done
}

mkdir -p "$D"
echo "ROOT=$(id -u)"
case $1 in
    sicherungen) sicherungen ;;
    sichern | zurueck | tauschen | loeschen)
        paket_ok "$2" || { echo "FEHLER=Ungültiger Paketname: $2"; exit 2; }
        if [ "$1" = zurueck ] || [ "$1" = loeschen ]; then
            case $3 in
                */* | "" | .*) echo "FEHLER=Ungültiger Ordner: $3"; exit 2 ;;
            esac
        fi
        case $1 in
            sichern) sichern "$2" ;;
            zurueck) zurueck "$2" "$3" "$4" ;;
            tauschen) tauschen "$2" "$3" ;;
            loeschen)
                rm -rf "${APPS:?}/$2/$3"
                rmdir "$APPS/$2" 2> /dev/null
                echo "OK=1"
                ;;
        esac
        ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
