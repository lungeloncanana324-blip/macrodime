#!/usr/bin/env bash
# Installs the debug build on a running emulator and captures the screens the
# Play listing needs, in light and dark, plus the first onboarding screen.
#
#   bash android/scripts/capture_screenshots.sh <output folder>
#
# Run from the repository root with exactly one emulator or device attached.
# Uses the debug-only demo intent (see DemoData.kt), so a release build cannot
# be driven this way. Fails if the app is not running after any launch: a
# screenshot of a crash dialog is not a screenshot.

set -euo pipefail

OUT="${1:-android/play-screenshots}"
APK="android/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.lungelo.macrodime"
ACTIVITY="$PACKAGE/.MainActivity"

mkdir -p "$OUT"
adb wait-for-device
adb install -r "$APK"

# A clean status bar: fixed time, full battery, no notifications.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

alive() {
  if ! adb shell pidof "$PACKAGE" >/dev/null; then
    echo "error: $PACKAGE is not running after: $1" >&2
    adb logcat -d -s AndroidRuntime:E | tail -40 >&2
    exit 1
  fi
}

capture() {
  # exec-out keeps the PNG binary intact, which `adb shell` does not.
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1"
}

# First run: the onboarding welcome, from a cleared install.
adb shell pm clear "$PACKAGE" >/dev/null
adb shell cmd uimode night no
adb shell am start -W -n "$ACTIVITY" >/dev/null
sleep 5
alive "first launch"
capture "0-onboarding"

for mode in light dark; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  index=1
  for tab in today plan groceries settings; do
    adb shell am force-stop "$PACKAGE"
    adb shell am start -W -n "$ACTIVITY" --ez macrodime.demo true --ei macrodime.tab $((index - 1)) >/dev/null
    sleep 6
    alive "$tab ($mode)"
    capture "$index-$tab-$mode"
    index=$((index + 1))
  done
done

adb shell cmd uimode night no
adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
ls -la "$OUT"
