#!/usr/bin/env bash
# Seed and dump a Flutter 2.1.4+62 install on the e2e AVD (E00-S02; plan 05 §10.4, adapted to the released line).
#
# Run on the PC, from native/, holding the single emulator lock, outside 00:00-02:00. Never on the owner's phone and
# never on the walk AVD. Every adb call names the serial. The script stops at the first failure and before any change
# until it has proven which device it talks to.
#
#   E2E_AVD=<e2e AVD name> APK=<path to the 2.1.4 release APK> VC=<its versionCode> COMMIT=<its commit> \
#     tools/flutter-fixtures/seed_2_1_4.sh
#
# The APK: built from origin/master (2.1.4+62) with the release Flutter (AGENTS.md toolchain), universal,
# --build-number=$VC with VC >= 4612 (above every owner build), signed with the owner's local release key. Building and
# signing need the maintainer's explicit go-ahead; this script only installs what it is given and checks the signer.
set -euo pipefail

PKG=io.github.eslamasabry.opencode_mobile
export ANDROID_SERIAL=emulator-5584
ADB="adb -s $ANDROID_SERIAL"
LOCAL_KEY=1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C
OUT=e2e/fixtures/flutter-2.1.4
SNAPSHOT=flutter-2.1.4-seeded

: "${E2E_AVD:?set E2E_AVD to the e2e AVD name}" "${APK:?set APK}" "${VC:?set VC}" "${COMMIT:?set COMMIT}"
case "$E2E_AVD" in *walk*|*Walk*) echo "refusing: $E2E_AVD looks like the walk AVD"; exit 1;; esac
[ "$VC" -ge 4612 ] || { echo "versionCode $VC is not above every owner build (>= 4612)"; exit 1; }
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "COMMIT must be a full commit id"; exit 1; }

# The signer must be the local release key, or the owner's phone would refuse the native update over it.
SIGNER=$(apksigner verify --print-certs "$APK" | sed -n 's/.*SHA-256 digest: //p' | head -1 | tr -d ':' | tr a-f A-F)
[ "$SIGNER" = "$LOCAL_KEY" ] || { echo "APK signer is not the local release key"; exit 1; }

# 1. A fresh rootable AVD on the fixed port, then prove it is that AVD.
emulator -avd "$E2E_AVD" -port 5584 -wipe-data -no-snapshot-load -no-boot-anim &
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
[ "$($ADB shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo "not an emulator"; exit 1; }
[ "$($ADB emu avd name | head -1 | tr -d '\r')" = "$E2E_AVD" ] || { echo "wrong AVD"; exit 1; }
$ADB root
$ADB wait-for-device

# 2. Install and record what is installed.
$ADB install -r "$APK"
INSTALLED_VC=$($ADB shell dumpsys package $PKG | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
[ "$INSTALLED_VC" = "$VC" ] || { echo "installed versionCode $INSTALLED_VC != $VC"; exit 1; }

# 3. Fixture servers (passwords stay in the run dir; never printed).
RUN=$(mktemp -d)
python3 -I e2e/fixtures/fxctl.py start --run-dir "$RUN" FX-OC2 FX-TLS FX-PASEO FX-CODEX FX-LINUXDL FX-AUTH

# 4. Seed through the UI: every step listed in $OUT/seed-manifest.json "seeds", in order. Each step is a Maestro flow
#    under e2e/seed/flutter-2.1.4/<seed id>.yaml (to be written against the 2.1.4 UI; Flutter debug APKs don't run on
#    the Shorebird engine, so this is the release APK). Home-screen surfaces are placed with UIAutomator.
for step in $(python3 -I -c 'import json,sys;print(" ".join(s["id"] for s in json.load(open(sys.argv[1]))["seeds"]))' "$OUT/seed-manifest.json"); do
  maestro --device "$ANDROID_SERIAL" test "e2e/seed/flutter-2.1.4/$step.yaml"
done

# 5. OS-held state before any native install (J1 compares after it).
$ADB shell am force-stop $PKG
$ADB shell pm unstop $PKG
$ADB shell dumpsys package $PKG > "$OUT/package-before.txt"
$ADB shell appops get $PKG > "$OUT/appops-before.txt"
$ADB shell dumpsys shortcut | grep -A30 "Package: $PKG" > "$OUT/shortcuts-before.txt"
$ADB shell dumpsys notification --noredact | grep -A3 "pkg=$PKG" > "$OUT/channels-before.txt"

# 6. Dump: prefs and listings only; the rootfs and projects are counted, never copied.
$ADB shell "cd /data/data/$PKG && tar -cf - shared_prefs" > "$OUT/shared_prefs.tar"
$ADB shell "cd /data/data/$PKG && find files app_flutter cache code_cache no_backup databases app_webview \
  -maxdepth 3 -not -path 'files/linux/*' -not -path 'files/projects/*' -exec stat -c '%F|%s|%n' {} +" > "$OUT/tree.txt"
$ADB shell "cd /data/data/$PKG && du -sk files/linux files/projects; find files/linux | wc -l" >> "$OUT/tree.txt"
python3 -I tools/flutter-fixtures/xml_to_prefs_json.py "$OUT"

# 7. Save the snapshot every journey starts from (on the e2e AVD only).
$ADB emu avd snapshot save "$SNAPSHOT"
python3 -I e2e/fixtures/fxctl.py stop --run-dir "$RUN"

echo "Now fill in $OUT/seed-manifest.json (commit $COMMIT, versionCode $VC, signer, AVD name, reviewer),"
echo "read the dump, and run: python3 -I tools/flutter-fixtures/check_dump.py $OUT"
