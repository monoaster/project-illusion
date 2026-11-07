#!/usr/bin/env bash
# Build Project Illusion and make it the TV's Home, over Wi-Fi (ADB). Run from anywhere:
#
#   TV_HOST=192.168.1.50 launcher/build-and-install.sh
#
# Options (environment variables):
#   THEME=<folder>         the theme to install with it (default themes/project-illusion); THEME=none skips it
#   STOCK_LAUNCHER=<pkg>   the TV's own Home app, when the script doesn't recognise it; none = turn nothing off
#   DIAL_SERVICE=0         don't switch on hold-OK-in-any-app (the dial)
#   HOME_KEY_SERVICE=1     for remotes whose Home button sends "All apps" (some Xiaomi sticks)
#
# Order matters and is the point of this script: the new Home is installed and started first, the TV's own
# Home is switched off only once it runs, and if Home doesn't end up on Project Illusion the TV's own Home
# is switched back on. Nothing is uninstalled.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the IP address of the TV (tools/find_tv.py finds it), e.g. TV_HOST=192.168.1.50 $0}"
PKG=com.projectillusion.home
APK=app/build/outputs/apk/release/app-release.apk
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
KNOWN_STOCK="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome"

# ---- build: any JDK 17 or newer, the Android SDK where Android Studio or Homebrew puts it ----
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17+ 2>/dev/null || { [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17; } || java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')}"
JV=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')
case "$JV" in 1.*|[0-9].*|1[0-6].*|"") echo "ERROR: needs JDK 17 or newer; JAVA_HOME=$JAVA_HOME has '${JV:-no java}'." >&2; exit 1 ;; esac
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && echo "$d" && break; done)}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
echo "Building with Java $JV"
./gradlew assembleRelease -q --console=plain

# ---- talking to the TV ----
TOOLS="$(cd ../tools && pwd)"
PY="$TOOLS/venv/bin/python"; [ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "ERROR: first run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1            # Git Bash on Windows: don't rewrite /data/... paths
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }
home_now() { tv "$HOME_QUERY" 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true; }

# ---- which Home app to switch off ----
if [ -z "${STOCK_LAUNCHER:-}" ]; then
  NOW=$(home_now)
  case " $KNOWN_STOCK $PKG " in
    *" $NOW "*) STOCK_LAUNCHER=$NOW ;;
    *) echo "ERROR: Home is '${NOW:-unknown}', which this script doesn't know as a TV's own Home app." >&2
       echo "Run again with STOCK_LAUNCHER=<package> (or none to switch nothing off)." >&2; exit 1 ;;
  esac
fi
[ "$STOCK_LAUNCHER" = "$PKG" ] && STOCK_LAUNCHER=none
echo "TV's own Home: $STOCK_LAUNCHER"

# ---- install, theme, start ----
"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/illusion.apk
OUT=$(tv 'pm install -r /data/local/tmp/illusion.apk; rm -f /data/local/tmp/illusion.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: install failed; nothing else was changed." >&2; exit 1 ;; esac
tv "pm grant $PKG android.permission.READ_TV_LISTINGS 2>/dev/null; cmd notification allow_listener $PKG/$PKG.MediaListenerService 2>/dev/null; cmd package compile -m speed -f $PKG >/dev/null; echo permissions-set"
THEME="${THEME:-../themes/project-illusion}"
if [ "$THEME" != none ]; then bash "$TOOLS/theme.sh" "$THEME" --no-restart; fi
START=$(tv "am start -W -n $PKG/.HomeActivity")
echo "$START" | grep -E 'Status|Error' || true
if ! printf '%s' "$START" | grep -qF 'Status: ok' || printf '%s' "$START" | grep -qi '^Error'; then
  echo "ERROR: Project Illusion didn't start; the TV's own Home was left on." >&2; exit 1
fi

# ---- hand Home over, then check where it landed ----
OFF="echo nothing switched off"; [ "$STOCK_LAUNCHER" = none ] || OFF="pm disable-user --user 0 $STOCK_LAUNCHER"
printf '%s\n' "$OFF" "cmd package set-home-activity --user 0 $PKG/.HomeActivity" | tv --batch
if [ "$(home_now)" != "$PKG" ]; then
  echo "ERROR: Home didn't end up on Project Illusion; switching the TV's own Home back on." >&2
  if [ "$STOCK_LAUNCHER" != none ]; then
    tv "pm enable --user 0 $STOCK_LAUNCHER; cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)" >/dev/null || true
    echo "Home is now: $(home_now)" >&2
  fi
  exit 1
fi
echo "Home is now Project Illusion."

# ---- accessibility services: added to the list without dropping anyone else's ----
enable_service() {
  tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *$PKG/.$1*) ;; null|'') settings put secure enabled_accessibility_services $PKG/.$1 ;; *) settings put secure enabled_accessibility_services \"\$s:$PKG/.$1\" ;; esac; settings put secure accessibility_enabled 1; echo $1 on"
}
[ "${DIAL_SERVICE:-1}" = 1 ] && enable_service DialService
[ "${HOME_KEY_SERVICE:-0}" = 1 ] && enable_service HomeKeyService
echo "Done. Press Home on the remote."
