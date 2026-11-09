#!/bin/bash
# run.sh <apk> <package> <out-dir>: the emulator half of native-chat-emu.yml. Installs the APK, connects it to the
# A push to native/chat-emu-crash that touches this folder (commit without the skip marker) starts the bisect; after
# that first run the workflow can be dispatched with chosen suspects.
# runner's OpenCode 2 server (127.0.0.1:4321 through adb reverse, password in $PW), opens a conversation and records
# whether the emulator survived. Never fails the step: the verdict is in <out>/result.txt.
set -u
apk=$1; pkg=$2; out=$3; here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$out"
alive() { timeout 20 adb shell true >/dev/null 2>&1; }
shot() { timeout 30 adb exec-out screencap -p > "$out/$1.png" 2>/dev/null || rm -f "$out/$1.png"; }
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb install -r -t "$apk" > "$out/install.txt" 2>&1
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS || true
adb reverse tcp:4321 tcp:4321
adb logcat -c || true
adb logcat -v threadtime > "$out/logcat.txt" 2>&1 &
logcat=$!
timeout 600 maestro --device emulator-5554 test -e APP_ID="$pkg" -e PW="$PW" "$here/connect.yaml" > "$out/maestro-connect.log" 2>&1
echo "connect=$?" >> "$out/result.txt"
shot list
timeout 20 adb shell uiautomator dump /sdcard/list.xml >/dev/null 2>&1 && adb pull /sdcard/list.xml "$out/list.xml" >/dev/null 2>&1
timeout 300 maestro --device emulator-5554 test -e APP_ID="$pkg" "$here/open-chat.yaml" > "$out/maestro-open.log" 2>&1
echo "open=$?" >> "$out/result.txt"
for i in 1 2 3 4 5 6; do sleep 5; alive || break; done
if alive; then
  echo "emulator=alive" >> "$out/result.txt"
  shot chat
  timeout 20 adb shell uiautomator dump /sdcard/chat.xml >/dev/null 2>&1 && adb pull /sdcard/chat.xml "$out/chat.xml" >/dev/null 2>&1
else
  echo "emulator=gone" >> "$out/result.txt"
fi
kill "$logcat" 2>/dev/null
sed -i "s/$PW/<password>/g" "$out"/*.log "$out"/*.txt 2>/dev/null
cat "$out/result.txt"
exit 0
