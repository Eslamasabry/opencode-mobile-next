#!/usr/bin/env bash
# T3 (plan 02 §8): the emulator lane, before any APK reaches the owner and at every slice end. PC emulator only.
#
#   native/tool/t3.sh <apk> --signer <sha256> [--flows E2E-01,E2E-05 | --group G2 | --all] [--upgrade <baseline apk>]
#                     [--serial emulator-5584] [--expect-avd OCN_A] [--variant stableRelease] [--detach] [--dry-run]
#
# Steps, in order (each recorded in build/native-t3/<id>/summary.txt):
#   1. APK checks: package and versionCode (>= $OC_T3_MIN_VERSION_CODE, default 10000, the native range) with aapt2;
#      the signing certificate's SHA-256 with apksigner must equal --signer ($OC_T3_SIGNER). Never substitute a signer.
#   2. the emulator lock ($OC_EMULATOR_LOCK, default the PC's oc-emulator.lock) for the whole run, >= 7 GB available;
#   3. the device: the serial must be emulator-<port> (never a phone), `adb get-state` = device, ro.kernel.qemu = 1;
#      the installed versionCode, if any, must not be higher (Android refuses a downgrade; never uninstall);
#   4. Maestro flows on the release build with animations off, through native/e2e/runner/run.py (S0-FLOWS);
#   5. the animations-on smoke E2E-05;
#   6. the instrumented classes: ./gradlew :androidApp:connectedStableReleaseAndroidTest on that serial;
#   7. the S-28 accessibility sweep (02 S-28): tool/qa/a11y_sweep.sh when it exists, otherwise MISSING (a failure);
#   8. with --upgrade: the upgrade journey E2E-39 (Flutter baseline installed, the native APK installed over it).
# Animator scales are restored to 1 at exit. Run detached (--detach) and wait for build/native-t3/<id>/exit-code.
# Exit: 0 pass, 1 a check or flow failed (or a gate is missing), 2 could not run (no device, lock, tools, APK).
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
oc_maybe_detach native-t3 "$@"

apk="" signer="${OC_T3_SIGNER:-}" select=(--all) upgrade="" serial="${OC_T3_SERIAL:-emulator-5584}" avd="" variant=stableRelease dry=0
while (($#)); do
  case "$1" in
    --signer) signer="${2:-}"; shift 2 ;;
    --flows) select=(--flows "${2:-}"); shift 2 ;;
    --group) select=(--group "${2:-}"); shift 2 ;;
    --all) select=(--all); shift ;;
    --upgrade) upgrade="${2:-}"; shift 2 ;;
    --serial) serial="${2:-}"; shift 2 ;;
    --expect-avd) avd="${2:-}"; shift 2 ;;
    --variant) variant="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,21p' "$0"; exit 0 ;;
    -*) oc_usage "t3.sh <apk> --signer <sha256> [--flows L | --group G | --all] [--upgrade <apk>] [--serial S] [--detach] [--dry-run]" ;;
    *) apk="$1"; shift ;;
  esac
done

oc_run_init native-t3 "${OC_RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
[[ -n "$apk" ]] || { oc_say "no APK given"; oc_status 2; exit 2; }
[[ -f "$apk" ]] || { oc_say "APK not found: $apk"; oc_status 2; exit 2; }
[[ "$serial" =~ ^emulator-[0-9]+$ ]] || { oc_say "serial $serial is not an emulator; T3 never targets a phone"; oc_status 2; exit 2; }
oc_say "apk: $apk ($(sha256sum "$apk" | cut -c1-64))"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
bt="$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)"
aapt2="${AAPT2:-$bt/aapt2}" apksigner="${APKSIGNER:-$bt/apksigner}" adb="${ADB:-$sdk/platform-tools/adb}"
for t in "$aapt2" "$apksigner"; do [[ -x "$t" ]] || { oc_say "tool missing: $t (set ANDROID_HOME)"; oc_status 2; exit 2; }; done

# 1. APK checks
badging="$("$aapt2" dump badging "$apk" 2>&1 | head -1)"
pkg="$(sed -nE "s/.*package: name='([^']+)'.*/\1/p" <<<"$badging")"
vc="$(sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p" <<<"$badging")"
[[ -n "$pkg" && -n "$vc" ]] || { oc_say "aapt2 could not read the APK"; oc_status 2; exit 2; }
min_vc="${OC_T3_MIN_VERSION_CODE:-10000}"
oc_say "badging: package $pkg versionCode $vc (need >= $min_vc)"
((vc >= min_vc)) || { oc_say "FAIL: versionCode below the native range"; oc_status 1; }
cert="$("$apksigner" verify --print-certs "$apk" 2>&1 | sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$/\1/p' | tr 'a-f' 'A-F')"
[[ -n "$cert" ]] || { oc_say "FAIL: apksigner found no signer"; oc_status 1; }
oc_say "signer: $cert"
if [[ -z "$signer" ]]; then
  oc_say "signer: not compared (no --signer); T3 cannot pass without the expected certificate"; oc_status 2; exit 2
fi
[[ "$cert" == "$(tr 'a-f' 'A-F' <<<"$signer")" ]] || { oc_say "FAIL: signer differs from --signer"; oc_status 1; }

if ((dry)); then oc_say "dry run: device steps not started"; exit "$OC_STATUS"; fi

# 2. lock and memory
lock="${OC_EMULATOR_LOCK:-/home/eslam/Storage/tmp/oc-emulator.lock}"
[[ -d "$(dirname "$lock")" ]] || lock="${TMPDIR:-/tmp}/oc-emulator.lock"
exec {emufd}>"$lock"
flock -w "${OC_LOCK_WAIT:-1800}" "$emufd" || { oc_say "lock: emulator lock busy"; oc_status 2; exit 2; }
oc_say "lock: emulator lock $lock held by pid $$"
oc_check_memory || { oc_status 2; exit 2; }

# 3. device
[[ -x "$adb" ]] || adb="$(command -v adb || true)"
[[ -n "$adb" ]] || { oc_say "adb missing"; oc_status 2; exit 2; }
state="$("$adb" -s "$serial" get-state 2>/dev/null)"
[[ "$state" == device ]] || { oc_say "device: $serial is not online (state '${state:-none}')"; oc_status 2; exit 2; }
[[ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] ||
  { oc_say "device: $serial is not an emulator (ro.kernel.qemu != 1)"; oc_status 2; exit 2; }
installed="$("$adb" -s "$serial" shell dumpsys package "$pkg" 2>/dev/null | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -1)"
oc_say "device: $serial online; installed versionCode ${installed:-none}"
if [[ -n "$installed" ]] && ((installed > vc)); then
  oc_say "FAIL: the emulator has versionCode $installed > $vc; Android refuses the downgrade (never uninstall)"; oc_status 1; exit 1
fi
anim() { local v rc=0; for v in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb" -s "$serial" shell settings put global "$v" "$1" || rc=1; done; return "$rc"; }
oc_restore_animations() { anim 1 >/dev/null 2>&1; }
OC_CLEANUP=oc_restore_animations

runpy=(python3 -I "$OC_NATIVE/e2e/runner/run.py" --app native --variant "$variant" --apk "$apk" --serial "$serial"
  --expect-signer "$signer" --lock "$OC_RUN_DIR/inner.lock" --skip-resource-checks)
[[ -n "$avd" ]] && runpy+=(--expect-avd "$avd")

# 4–5. flows
anim 0
oc_step flows "${runpy[@]}" "${select[@]}" --out "$OC_RUN_DIR/flows" || oc_status 1
anim 1
oc_step flows-animations-on "${runpy[@]}" --flows E2E-05 --out "$OC_RUN_DIR/flows-anim" || oc_status 1

# 6. instrumented classes
oc_step instrumented oc_with_lock "$(oc_build_lock_file)" env ANDROID_SERIAL="$serial" "$OC_TOOL_DIR/lib/gradlew.sh" \
  :androidApp:connectedStableReleaseAndroidTest
rc=$?
if ((rc != 0)); then
  m="$(oc_gradle_missing_tasks "$OC_RUN_DIR/instrumented.log")"
  [[ -n "$m" ]] && oc_say "  MISSING: $m"
  if ((rc == 75)); then oc_status 2; else oc_status 1; fi
fi

# 7. S-28 sweep
if [[ -x "$OC_NATIVE/tool/qa/a11y_sweep.sh" ]]; then
  oc_step a11y-sweep "$OC_NATIVE/tool/qa/a11y_sweep.sh" --serial "$serial" --out "$OC_RUN_DIR/a11y" || oc_status 1
else
  oc_say "S-28 sweep: MISSING (tool/qa/a11y_sweep.sh does not exist yet); not a T3 pass"; oc_status 1
fi

# 8. upgrade journey
if [[ -n "$upgrade" ]]; then
  oc_step upgrade python3 -I "$OC_NATIVE/e2e/runner/run.py" --app flutter --apk "$upgrade" --native-apk "$apk" \
    --serial "$serial" --expect-signer "$signer" --lock "$OC_RUN_DIR/inner.lock" --skip-resource-checks \
    --flows E2E-39 --out "$OC_RUN_DIR/upgrade" || oc_status 1
fi
exit "$OC_STATUS"
