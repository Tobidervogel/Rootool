#!/bin/sh
# Test fuer app/src/main/assets/logcat.sh: logcat ist eine Attrappe, die ihre Argumente ausgibt.
#
#   sh app/src/test/shell/logcat_test.sh [mksh|dash|"busybox sh"]
set -u
SHELL_UNTER_TEST=${1:-/bin/sh}
HIER=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin"
cat > "$T/bin/logcat" <<'EOF'
#!/bin/sh
echo "ARGS $*"
echo "10-25 18:40:01.123  1234  1250 E AndroidRuntime: FATAL EXCEPTION: main"
EOF
chmod +x "$T/bin/logcat"
export PATH="$T/bin:$PATH"
fehler=0
pruefe() {
    if eval "$2"; then echo "  ok    $1"; else echo "  FEHLT $1"; fehler=$((fehler + 1)); fi
}
l() { $SHELL_UNTER_TEST "$HIER/../../main/assets/logcat.sh" lesen "$@"; }
echo "Shell: $SHELL_UNTER_TEST"
l 10234 abstuerze 300 > "$T/a"
pruefe "App, nur Abstuerze"  'grep -qx "Z=ARGS -d -v threadtime -b crash -t 300 --uid=10234" "$T/a"'
pruefe "Zeilen mit Z="       'grep -q "^Z=10-25 18:40:01.123  1234  1250 E AndroidRuntime" "$T/a" && grep -qx "ROOT=$(id -u)" "$T/a"'
l alle normal 99999 > "$T/b"
pruefe "alle, gedeckelt"     'grep -qx "Z=ARGS -d -v threadtime -b main,system,crash -t 5000" "$T/b"'
l '1;x' normal 10 > "$T/c"
pruefe "Kennung geprueft"    'grep -q "^FEHLER=" "$T/c" && ! grep -q "ARGS" "$T/c"'
l alle normal abc > "$T/d"
pruefe "Anzahl geprueft"     'grep -q "^FEHLER=" "$T/d"'
echo
if [ $fehler -eq 0 ]; then echo "Alles ok."; else echo "$fehler Fehler."; fi
exit $fehler
