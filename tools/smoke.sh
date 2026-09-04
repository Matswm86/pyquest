#!/usr/bin/env bash
# Launches the app on a connected emulator and reports whether it survives.
#
# This lives in a file rather than inline in the workflow because the emulator
# action runs the inline script one line at a time through `sh -c`: a line
# continuation or a multi-line `if` is split apart and fails with a syntax error
# before anything useful is captured.
set -x

PKG=no.mwmai.pyquest.debug
ACTIVITY=no.mwmai.pyquest.MainActivity
OUT=smoke

mkdir -p "$OUT"
chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }

adb logcat -c || true
# No -W here. That flag waits for the launch to settle, which never happens for
# an app that dies and restarts, and it cost a 28 minute job timeout once.
adb shell am start -n "$PKG/$ACTIVITY" || echo "am start returned $?"
sleep 15

adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
adb exec-out screencap -p > "$OUT/launch.png" 2>/dev/null || true
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
wc -l "$OUT/logcat.txt" || true

if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"; then
    echo "::error::App crashed at launch"
    grep -B 2 -A 45 -m 1 -E "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"
    exit 1
fi

if ! grep -q "$PKG" "$OUT/activities.txt"; then
    echo "::error::App is not in the activity stack after launch"
    tail -80 "$OUT/logcat.txt"
    exit 1
fi

echo "App launched and is still running."
