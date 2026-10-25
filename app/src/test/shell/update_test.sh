#!/bin/sh
# Test fuer app/src/main/assets/update.sh auf dem PC (Linux/WSL), ohne Handy.
# Partitionen sind Dateien, magiskboot, getprop, logcat, pm, svc, magisk und df sind Attrappen.
#
#   sh app/src/test/shell/update_test.sh [mksh|dash|"busybox sh"]
set -u

SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/block" "$T/roottools" "$T/sd" "$T/ue" "$T/tmp"
cp "$HIER/../../main/assets/update.sh" "$T/roottools/update.sh"

# Partitionen: Inhalt steuert die magiskboot-Attrappe
printf 'STOCK' > "$T/block/boot_a"
printf 'MAGISK' > "$T/block/boot_b"
printf 'KEINRAMDISK' > "$T/block/init_boot_a"
printf 'vbmeta' > "$T/block/vbmeta_a"
printf 'vbmeta' > "$T/block/vbmeta_b"
head -c 300000 /dev/urandom > "$T/block/nvdata"
printf 'x' > "$T/block/userdata"   # darf nicht mitgesichert werden

cat > "$T/magiskboot" <<'EOF'
#!/bin/sh
case $1 in
    unpack) grep -q KEINRAMDISK "$2" || cp "$2" ramdisk.cpio; exit 0 ;;
    cpio)
        grep -q MAGISK "$2" && exit 1
        grep -q FREMD "$2" && exit 2
        exit 0 ;;
esac
EOF
cat > "$T/bin/getprop" <<'EOF'
#!/bin/sh
case $1 in
    ro.product.model) echo CPH2493 ;;
    ro.build.display.ota) echo 'CPH2493_16.0.5.1200(EX01)' ;;
    ro.build.version.release) echo 16 ;;
    ro.build.version.security_patch) echo 2026-08-01 ;;
    ro.boot.slot_suffix) echo _b ;;
esac
EOF
cat > "$T/bin/logcat" <<'EOF'
#!/bin/sh
echo "10-25 12:00:00.000 I update_engine: Update attempt flags in use = 0x0"
echo "10-25 12:30:00.000 I update_engine: ActionProcessor: finished DownloadAction with code ErrorCode::kSuccess"
echo "10-25 12:31:00.000 I update_engine: Update successfully applied, waiting to reboot."
EOF
cat > "$T/bin/pm" <<EOF
#!/bin/sh
echo "pm \$*" >> "$T/aufrufe"
[ "\$1" = path ] && echo package:/system/app/OTA/OTA.apk
exit 0
EOF
cat > "$T/bin/svc" <<EOF
#!/bin/sh
echo "svc \$*" >> "$T/aufrufe"
EOF
cat > "$T/bin/blockdev" <<'EOF'
#!/bin/sh
stat -c %s "$2"
EOF
cat > "$T/bin/magisk" <<'EOF'
#!/bin/sh
case $1 in -v) echo 30.7:MAGISK:R ;; -V) echo 30700 ;; esac
EOF
# df: freier Platz in KB aus $T/frei_kb
cat > "$T/bin/df" <<EOF
#!/bin/sh
echo "Filesystem 1K-blocks Used Available Use% Mounted on"
echo "/dev/fuse 100000000 1 \$(cat "$T/frei_kb") 1% /sdcard"
EOF
chmod +x "$T/bin/"* "$T/magiskboot"
echo 50000000 > "$T/frei_kb"
echo 4b1d-boot > "$T/bootid"
echo 4b1d-boot > "$T/ue/update-completed-on-boot-id"
echo 87 > "$T/akku"

case $SHELL_UNTER_TEST in
    *" "*)
        printf '#!/bin/sh\nexec %s "$@"\n' "$SHELL_UNTER_TEST" > "$T/bin/shell-unter-test"
        chmod +x "$T/bin/shell-unter-test"
        SHELL_UNTER_TEST=$T/bin/shell-unter-test ;;
esac

export ROOTTOOLS_DIR="$T/roottools" ROOTTOOLS_BIN="$T/bin" ROOTTOOLS_BLOCK="$T/block" \
    ROOTTOOLS_MAGISKBOOT="$T/magiskboot" ROOTTOOLS_UE="$T/ue" ROOTTOOLS_BOOTID="$T/bootid" \
    ROOTTOOLS_AKKU="$T/akku" ROOTTOOLS_SD="$T/sd" ROOTTOOLS_TMP="$T/tmp"
export PATH="$T/bin:$PATH"

fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
upd() { $SHELL_UNTER_TEST "$T/roottools/update.sh" "$@"; }

echo "Shell: $SHELL_UNTER_TEST"

echo "1. Zustand"
upd zustand > "$T/z1"
pruefe "Version und Slot"        'grep -qx "VERSION=CPH2493_16.0.5.1200(EX01)" "$T/z1" && grep -qx "SLOT=_b" "$T/z1"'
pruefe "Magisk"                  'grep -qx "MAGISK=30.7:MAGISK:R (30700)" "$T/z1"'
pruefe "Akku"                    'grep -qx "AKKU=87" "$T/z1"'
pruefe "boot_a original"         'grep -qx "IMG_BOOT_A=original" "$T/z1"'
pruefe "boot_b Magisk"           'grep -qx "IMG_BOOT_B=magisk" "$T/z1"'
pruefe "init_boot_a ohne Ramdisk" 'grep -qx "IMG_INIT_BOOT_A=leer" "$T/z1"'
pruefe "init_boot_b fehlt"       'grep -qx "IMG_INIT_BOOT_B=fehlt" "$T/z1"'
pruefe "Update wartet"           'grep -qx "UPDATE=wartet" "$T/z1"'
pruefe "Protokoll"               '[ "$(grep -c "^UELOG=" "$T/z1")" = 2 ]'
pruefe "Groesse"                 'grep -qx "TEILE_KB=$(( (5 + 6 + 6 + 6 + 300000) / 1024 ))" "$T/z1"'
pruefe "OTA-App da"              'grep -qx "OTA_APP=1" "$T/z1"'
pruefe "Pruefordner weg"         '[ ! -e "$T/tmp/pruefen" ]'

echo "2. Kein Update nach dem Neustart"
echo neu-boot > "$T/bootid"
upd zustand > "$T/z2"
pruefe "UPDATE fehlt"            '! grep -q "^UPDATE=" "$T/z2"'

echo "3. Notfall-Sicherung"
upd sichern > "$T/s1"
ordner=$(sed -n 's/^FERTIG=//p' "$T/s1")
pruefe "fertig"                  '[ -n "$ordner" ] && [ -d "$ordner" ]'
pruefe "Fortschritt"             '[ "$(grep -c "^SCHRITT=" "$T/s1")" = 7 ]'
pruefe "nur bekannte Teile"      '[ -f "$ordner/nvdata.img" ] && [ -f "$ordner/boot_b.img" ] && [ ! -e "$ordner/userdata.img" ]'
pruefe "Abbild gleich"           'cmp -s "$ordner/nvdata.img" "$T/block/nvdata"'
pruefe "Pruefsummen stimmen"     '( cd "$ordner" && sha256sum -c SHA256SUMS > /dev/null )'
pruefe "Anleitung"               'grep -q "fastboot flash boot_b" "$ordner/ANLEITUNG.txt" && grep -q "Slot _b" "$ordner/ANLEITUNG.txt"'
upd zustand > "$T/z3"
pruefe "als komplett gelistet"   'grep -q "^SICHERUNG=$(basename "$ordner")|[0-9]*|1$" "$T/z3"'
upd sicherungen > "$T/l1"
pruefe "Kurzliste"               'grep -q "^SICHERUNG=$(basename "$ordner")|" "$T/l1" && grep -q "^FREI_KB=50000000$" "$T/l1" && ! grep -q "^IMG_" "$T/l1"'

echo "4. Zu wenig Platz"
echo 1000 > "$T/frei_kb"
rm -rf "$T/sd/RootTools"
upd sichern > "$T/s2"
pruefe "Fehler gemeldet"         'grep -q "^FEHLER=Zu wenig Platz" "$T/s2"'
pruefe "nichts angelegt"         '[ ! -d "$T/sd/RootTools/Notfall" ]'

echo "5. Lokale Installation freischalten"
: > "$T/aufrufe"
upd ota_frei > "$T/o1"
pruefe "WLAN und Daten aus"      'grep -qx "svc wifi disable" "$T/aufrufe" && grep -qx "svc data disable" "$T/aufrufe"'
pruefe "OTA-App geleert"         'grep -qx "pm clear com.oplus.ota" "$T/aufrufe" && grep -qx "OK=1" "$T/o1"'
upd netz_an > /dev/null
pruefe "Netz wieder an"          'grep -qx "svc wifi enable" "$T/aufrufe" && grep -qx "svc data enable" "$T/aufrufe"'

echo "6. Ohne magiskboot"
ROOTTOOLS_MAGISKBOOT="$T/gibtsnicht" upd zustand > "$T/z4"
pruefe "unbekannt"               'grep -qx "IMG_BOOT_A=unbekannt" "$T/z4"'

echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
