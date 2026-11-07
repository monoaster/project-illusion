#!/usr/bin/env bash
# Put a theme's config.json on the TV and restart Home so it reads it (no rebuild needed).
#
#   TV_HOST=192.168.1.50 tools/theme.sh themes/project-illusion
#   TV_HOST=192.168.1.50 tools/theme.sh --reset          remove it: no name, no weather, the suggested dock
#
# --no-restart (used by build-and-install) only copies the file.
set -euo pipefail
ARG="${1:-}"
case "$ARG" in --reset|"") ;; /*) ;; *) ARG="$(pwd -W 2>/dev/null || pwd)/$ARG" ;; esac
RESTART=1; [ "${2:-}" = --no-restart ] && RESTART=0
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the IP address of the TV, e.g. TV_HOST=192.168.1.50 $0 themes/project-illusion}"
PY=venv/bin/python; [ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "First run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.projectillusion.home
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme/config.json
TMP=/data/local/tmp/illusion-config.json
tv() { "$PY" -I tv.py "$@"; }
# Some firmware locks the shell out of Android/data: then the file goes in through the app's ThemeProvider.
if [ "$ARG" = --reset ]; then
  tv "rm -f $DIR/config.json 2>/dev/null; content delete --uri $URI 2>/dev/null; echo removed" | tail -n 1
else
  [ -f "$ARG/config.json" ] || { echo "no config.json in $ARG" >&2; exit 1; }
  "$PY" -I -c 'import json,sys; json.load(open(sys.argv[1]))' "$ARG/config.json" 2>/dev/null || { echo "$ARG/config.json is not valid JSON" >&2; exit 1; }
  tv --push "$ARG/config.json" "$TMP" >/dev/null
  tv "mkdir -p $DIR 2>/dev/null; cp $TMP $DIR/config.json 2>/dev/null || content write --uri $URI < $TMP; rm -f $TMP; echo theme-copied" | tail -n 1
fi
# Restarting Home: Android also drops a force-stopped app's accessibility services, so put the list back.
[ "$RESTART" = 1 ] && tv "a=\$(settings get secure enabled_accessibility_services); am force-stop $PKG; am start -n $PKG/.HomeActivity >/dev/null; case \"\$a\" in *$PKG*) sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac; echo home-restarted" | tail -n 1
exit 0
