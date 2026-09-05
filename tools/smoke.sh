#!/usr/bin/env bash
# Launches the app on a connected emulator and proves it actually rendered.
#
# This lives in a file rather than inline in the workflow because the emulator
# action runs an inline script one line at a time through `sh -c`: a line
# continuation or a multi-line `if` gets split apart and fails with a syntax
# error before anything useful is captured.
set -x

PKG=no.mwmai.pyquest.debug
ACTIVITY=no.mwmai.pyquest.MainActivity
OUT=smoke

mkdir -p "$OUT"
chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }

adb logcat -c || true
# No -W here. That flag waits for the launch to settle, which never happens for
# an app that dies and restarts, and it cost a 30 minute job timeout once.
adb shell am start -n "$PKG/$ACTIVITY" || echo "am start returned $?"
sleep 20

adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true

# Pixels first, by both routes. A headless emulator often hands back an all
# black frame regardless of what is on screen, so the capture is evidence when
# it works and proves nothing when it does not.
adb exec-out screencap -p > "$OUT/launch.png" 2>/dev/null || true
adb shell screencap -p /sdcard/launch2.png >/dev/null 2>&1 || true
adb pull /sdcard/launch2.png "$OUT/launch2.png" >/dev/null 2>&1 || true

# The view hierarchy is the assertion that does not depend on the GPU. Compose
# publishes its text through accessibility semantics, so real rendered content
# shows up here as real strings. The track is a lazy list, so the lower tiers
# only exist once scrolled into view: dump, swipe up, dump again, and check
# both. Compose needs a moment after the swipe before the semantics settle.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
adb shell input swipe 540 1900 540 500 300 >/dev/null 2>&1 || true
sleep 3
adb shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1 || true
adb pull /sdcard/ui2.xml "$OUT/ui2.xml" >/dev/null 2>&1 || true
adb exec-out screencap -p > "$OUT/scrolled.png" 2>/dev/null || true
# uiautomator writes XML, so "Types & collections" arrives as "Types &amp;
# collections". Decode the five predefined entities before grepping for the
# strings a human would type.
cat "$OUT/ui.xml" "$OUT/ui2.xml" 2>/dev/null \
    | sed -e 's/&amp;/\&/g' -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&quot;/"/g' -e "s/&apos;/'/g" \
    > "$OUT/ui_all.xml" || true

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

if [ ! -s "$OUT/ui.xml" ]; then
    echo "::error::Could not dump the view hierarchy, so nothing proves the UI drew"
    exit 1
fi

# Strings the track screen must be showing. If Compose composed but painted
# nothing, or the curriculum failed to load, these are missing.
MISSING=0
for TEXT in "PyQuest" "THE TRACK" "Hello, world" "Types & collections" "LLM engineering" "AI consultancy sims" "Pytor"; do
    if ! grep -qF "$TEXT" "$OUT/ui_all.xml"; then
        echo "::error::The track screen is missing the text: $TEXT"
        MISSING=1
    fi
done
if [ "$MISSING" -ne 0 ]; then
    echo "--- view hierarchy ---"
    head -c 4000 "$OUT/ui_all.xml"
    exit 1
fi

echo "App launched, and the track screen rendered its real content."
