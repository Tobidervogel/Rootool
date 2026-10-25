#!/system/bin/sh
# Root Tools – System-Update und Notfall-Sicherung
#
#   update.sh zustand      Version, Slots, Magisk, Update-Stand, Boot-Abbilder beider Slots, vorhandene Sicherungen
#   update.sh sicherungen  nur Platzbedarf und vorhandene Sicherungen (schnell)
#   update.sh sichern      Notfall-Sicherung der wichtigen Partitionen nach /sdcard/RootTools/Notfall/<Datum>
#   update.sh ota_frei     "Lokale Installation" in der OnePlus-Update-App freischalten (Trick aus der Nord-3-Notiz)
#   update.sh netz_an      WLAN und mobile Daten wieder an
#
# Root behalten beim Update (Virtual A/B, wie am 17.10.2026 auf dem Nord 3 erprobt): Das OTA schreibt in den
# inaktiven Slot. Vor dem Neustart muss Magisk diesen Slot patchen ("In inaktiven Slot installieren"). Ob das
# geklappt hat, prueft dieses Skript mit Magiskes eigenem Werkzeug: "magiskboot cpio <ramdisk> test" meldet
# 0 = original, 1 = Magisk, 2 = fremd gepatcht (so auch in Magisks boot_patch.sh).
# Ob ein Update auf den Neustart wartet, steht in update_engine: die Datei update-completed-on-boot-id enthaelt die
# boot_id des laufenden Starts (AOSP update_attempter_android.cc). Die OnePlus-App schaltet den Slot erst beim
# "Jetzt neu starten" um; dann gibt es die Datei vorher noch nicht, darum zeigt das Skript auch das Protokoll.
#
# Die Pfade lassen sich fuer den Test auf dem PC umbiegen (app/src/test/shell/update_test.sh).

D=${ROOTTOOLS_DIR:-/data/adb/roottools}
B=${ROOTTOOLS_BIN:-/system/bin}
BLK=${ROOTTOOLS_BLOCK:-/dev/block/by-name}
MB=${ROOTTOOLS_MAGISKBOOT:-/data/adb/magisk/magiskboot}
UE=${ROOTTOOLS_UE:-/data/misc/update_engine/prefs}
BOOTID=${ROOTTOOLS_BOOTID:-/proc/sys/kernel/random/boot_id}
AKKU=${ROOTTOOLS_AKKU:-/sys/class/power_supply/battery/capacity}
SD=${ROOTTOOLS_SD:-/sdcard}
TMP=${ROOTTOOLS_TMP:-/data/local/tmp/roottools}
NOTFALL=$SD/RootTools/Notfall

# Was die Notfall-Sicherung mitnimmt, falls vorhanden: alles zum Starten (beide Slots) und die MediaTek-Funkdaten
# (IMEI, WLAN-/Bluetooth-Adressen, Kalibrierung), siehe Nord-3-Notiz Schritt 5. Nur lesen, nie schreiben.
TEILE="boot_a boot_b init_boot_a init_boot_b vendor_boot_a vendor_boot_b dtbo_a dtbo_b vbmeta_a vbmeta_b
vbmeta_system_a vbmeta_system_b vbmeta_vendor_a vbmeta_vendor_b lk_a lk_b seccfg
nvram nvdata nvcfg persist proinfo protect1 protect2"

prop() {
    "$B/getprop" "$1" 2>/dev/null
}

# Zustand eines Boot-Abbilds: magisk, original, leer (kein Ramdisk), fremd, fehlt oder unbekannt.
abbild() {
    [ -e "$BLK/$1" ] || { echo fehlt; return; }
    [ -x "$MB" ] || { echo unbekannt; return; }
    t=$TMP/pruefen
    rm -rf "$t" && mkdir -p "$t" || { echo unbekannt; return; }
    stand=unbekannt
    if cat "$BLK/$1" > "$t/abbild.img" 2>/dev/null; then
        ( cd "$t" && "$MB" unpack abbild.img > /dev/null 2>&1 )
        r=$?
        # 0 = entpackt, 3 = vendor_boot (auch gut)
        if [ $r -eq 0 ] || [ $r -eq 3 ]; then
            rd=
            for p in ramdisk.cpio vendor_ramdisk/init_boot.cpio vendor_ramdisk/ramdisk.cpio; do
                [ -e "$t/$p" ] && { rd=$p; break; }
            done
            if [ -z "$rd" ]; then
                stand=leer
            else
                ( cd "$t" && "$MB" cpio "$rd" test > /dev/null 2>&1 )
                case $(($? & 3)) in
                    0) stand=original ;;
                    1) stand=magisk ;;
                    *) stand=fremd ;;
                esac
            fi
        fi
    fi
    rm -rf "$t"
    echo $stand
}

groesse_kb() {
    b=$("$B/blockdev" --getsize64 "$BLK/$1" 2>/dev/null) || b=0
    echo $(( ${b:-0} / 1024 ))
}

frei_kb() {
    set -- $(df -k "$SD" 2>/dev/null | tail -n 1)
    echo "${4:-0}"
}

# Platzbedarf, freier Platz und vorhandene Sicherungen (Name|Groesse in KB|komplett), neueste zuerst
sicherungen() {
    gesamt=0
    for p in $TEILE; do
        [ -e "$BLK/$p" ] && gesamt=$((gesamt + $(groesse_kb "$p")))
    done
    echo "TEILE_KB=$gesamt"
    echo "FREI_KB=$(frei_kb)"
    # Sicherungen: Name|Groesse in KB|komplett (SHA256SUMS da), neueste zuerst
    if [ -d "$NOTFALL" ]; then
        for d in $(ls -1 "$NOTFALL" 2>/dev/null | sort -r); do
            [ -d "$NOTFALL/$d" ] || continue
            set -- $(du -sk "$NOTFALL/$d" 2>/dev/null)
            k=0
            [ -f "$NOTFALL/$d/SHA256SUMS" ] && k=1
            echo "SICHERUNG=$d|${1:-0}|$k"
        done
    fi
}

zustand() {
    echo "ROOT=$(id -u)"
    echo "GERAET=$(prop ro.product.model)"
    v=$(prop ro.build.display.ota)
    [ -n "$v" ] || v=$(prop ro.build.display.id)
    echo "VERSION=$v"
    echo "ANDROID=$(prop ro.build.version.release)"
    echo "PATCH=$(prop ro.build.version.security_patch)"
    echo "SLOT=$(prop ro.boot.slot_suffix)"
    echo "MAGISK=$(magisk -v 2>/dev/null) ($(magisk -V 2>/dev/null))"
    { read -r a < "$AKKU"; } 2>/dev/null && echo "AKKU=$a"

    m=$(cat "$UE/update-completed-on-boot-id" 2>/dev/null)
    if [ -n "$m" ] && [ "$m" = "$(cat "$BOOTID" 2>/dev/null)" ]; then
        echo "UPDATE=wartet"
    fi
    # Letzte Meldungen von update_engine: Fortschritt, Fehler, "kUpdatedButNotActive" (OnePlus wartet auf Neustart).
    "$B/logcat" -d -s update_engine 2>/dev/null \
        | grep -E 'ErrorCode|kUpdatedButNotActive|UPDATED_NEED_REBOOT|status|progress|Update successfully' \
        | tail -n 6 | sed 's/^/UELOG=/'

    for s in _a _b; do
        for i in boot init_boot; do
            k=$(echo "IMG_$i$s" | tr 'a-z' 'A-Z')
            echo "$k=$(abbild "$i$s")"
        done
    done

    sicherungen
    "$B/pm" path com.oplus.ota > /dev/null 2>&1 && echo "OTA_APP=1"
}

anleitung() {
    cat <<EOF
Root Tools – Notfall-Sicherung vom $(date '+%d.%m.%Y %H:%M')
Gerät: $(prop ro.product.model), $v, aktiver Slot $slot, Magisk $(magisk -v 2>/dev/null)

Inhalt: Abbilder der Partitionen (*.img), Prüfsummen in SHA256SUMS.
Prüfen auf dem PC: sha256sum -c SHA256SUMS

Diesen Ordner auf PC oder NAS kopieren. Auf dem Handy selbst hilft er nicht, wenn es nicht mehr startet.

Startet das Handy nach einem Update nicht (Bootschleife):
1. Magisk-Sicherheitsmodus: beim Start Leiser gedrückt halten, dann sind alle Module aus.
2. fastbootd (OxygenOS 16 hat kein Bootloader-Fastboot): adb reboot fastboot
   dann z. B.: fastboot flash boot_b boot_b.img   (Slot und Abbild passend wählen)
   Laut Nord-3-Notiz noch nicht getestet.
3. Nur mit Slot-Wechsel als letztem Ausweg: fastboot set_active a

Funkdaten (nvram, nvdata, nvcfg, persist, proinfo, protect1, protect2) nur zurückspielen,
wenn IMEI, WLAN oder Bluetooth fehlen, bei laufendem System mit Root, z. B.:
   su -c 'dd if=/sdcard/RootTools/Notfall/$name/nvdata.img of=/dev/block/by-name/nvdata'
Niemals Funkdaten eines anderen Geräts verwenden.
EOF
}

sichern() {
    v=$(prop ro.build.display.ota)
    [ -n "$v" ] || v=$(prop ro.build.display.id)
    slot=$(prop ro.boot.slot_suffix)
    teile=
    gesamt=0
    for p in $TEILE; do
        [ -e "$BLK/$p" ] || continue
        teile="$teile $p"
        gesamt=$((gesamt + $(groesse_kb "$p")))
    done
    [ -n "$teile" ] || { echo "FEHLER=Keine Partitionen unter $BLK gefunden"; return 1; }
    frei=$(frei_kb)
    # 100 MB Luft lassen
    if [ "$frei" -lt $((gesamt + 102400)) ]; then
        echo "FEHLER=Zu wenig Platz: braucht $((gesamt / 1024)) MB, frei sind $((frei / 1024)) MB"
        return 1
    fi
    name=$(date +%Y-%m-%d_%H%M)
    ziel=$NOTFALL/$name
    mkdir -p "$ziel" || { echo "FEHLER=Kann $ziel nicht anlegen"; return 1; }
    n=0
    m=$(echo $teile | wc -w)
    for p in $teile; do
        n=$((n + 1))
        echo "SCHRITT=$n/$m $p"
        if ! cat "$BLK/$p" > "$ziel/$p.img" 2>/dev/null; then
            echo "FEHLER=$p liess sich nicht lesen"
            return 1
        fi
    done
    echo "SCHRITT=$m/$m Prüfsummen"
    ( cd "$ziel" && sha256sum *.img > SHA256SUMS.neu && mv -f SHA256SUMS.neu SHA256SUMS ) \
        || { echo "FEHLER=Prüfsummen fehlgeschlagen"; return 1; }
    anleitung > "$ziel/ANLEITUNG.txt"
    echo "FERTIG=$ziel"
}

mkdir -p "$D"
case $1 in
    zustand) zustand ;;
    sicherungen)
        echo "ROOT=$(id -u)"
        sicherungen
        ;;
    sichern)
        echo "ROOT=$(id -u)"
        sichern
        ;;
    ota_frei)
        echo "ROOT=$(id -u)"
        "$B/svc" wifi disable
        "$B/svc" data disable
        "$B/pm" clear com.oplus.ota > /dev/null 2>&1 && echo "OK=1"
        ;;
    netz_an)
        echo "ROOT=$(id -u)"
        "$B/svc" wifi enable
        "$B/svc" data enable
        echo "OK=1"
        ;;
    *)
        echo "FEHLER=Unbekannter Befehl: $1"
        exit 2
        ;;
esac
