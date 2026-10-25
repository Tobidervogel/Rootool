#!/system/bin/sh
# Root Tools – Logcat
#
#   logcat.sh lesen UID PUFFER ANZAHL
#     UID     Kennung einer App (logcat --uid) oder "alle"
#     PUFFER  "normal" (main, system, crash) oder "abstuerze" (nur crash)
#     ANZAHL  die letzten so vielen Zeilen (10 bis 5000)
#
# Als root sieht logcat die Meldungen aller Apps; ohne root nur die eigenen.

echo "ROOT=$(id -u)"
case $2 in
    alle) ;;
    *[!0-9]* | "") echo "FEHLER=Ungültige Kennung: $2"; exit 2 ;;
esac
case $4 in
    *[!0-9]* | "") echo "FEHLER=Ungültige Anzahl: $4"; exit 2 ;;
esac
n=$4
[ "$n" -lt 10 ] && n=10
[ "$n" -gt 5000 ] && n=5000
case $3 in
    abstuerze) puffer=crash ;;
    *) puffer=main,system,crash ;;
esac
if [ "$2" = alle ]; then
    logcat -d -v threadtime -b "$puffer" -t "$n" 2>&1 | sed 's/^/Z=/'
else
    logcat -d -v threadtime -b "$puffer" -t "$n" --uid="$2" 2>&1 | sed 's/^/Z=/'
fi
