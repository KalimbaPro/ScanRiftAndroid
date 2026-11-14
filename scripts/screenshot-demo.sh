#!/usr/bin/env bash
# Puts a running emulator (or device) into store-screenshot state:
#   1. SystemUI demo mode — 9:41, full battery, full Wi-Fi, no notifications;
#   2. installs the debug build and grants the camera permission;
#   3. replaces the app's data with the demo set (debug-only ScreenshotDemoActivity).
#
# Usage: scripts/screenshot-demo.sh [adb-serial]
# Build first: ./gradlew assembleDebug. See CLAUDE.md, "Store screenshots".
set -euo pipefail

cd "$(dirname "$0")/.."
ADB=(adb)
if [[ $# -ge 1 ]]; then ADB=(adb -s "$1"); fi

APP=com.scanrift.android.debug
APK=app/build/outputs/apk/debug/app-debug.apk

demo() { "${ADB[@]}" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

"${ADB[@]}" shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false -e powersave false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false
demo status -e bluetooth hide -e location hide -e alarm hide -e volume hide

"${ADB[@]}" install -r -g "$APK" >/dev/null
"${ADB[@]}" shell pm grant "$APP" android.permission.CAMERA
"${ADB[@]}" shell am force-stop "$APP"
"${ADB[@]}" shell am start -W -n "$APP/com.scanrift.android.demo.ScreenshotDemoActivity" >/dev/null
echo "Seeding; the app opens on the Collection tab when it is done."
