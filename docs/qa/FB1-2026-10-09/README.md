# FB1 first reply / FQ9 clean first run

Finish line: on a separately provisioned clean AVD, install the approved
candidate, complete phone setup through the app, and receive its first real
reply, with a screenshot and a bounded result for every stage. Non-goal:
changing the shared emulator, importing credentials, or testing sign-in.

**Prepared offline; device qualification pending. No AVD has been created or
started for this task.** Run once in the coordinator's final device pass, after
BD7 and explicit provisioning/start authorization. The clean-first-run row
remains pending until an actual fresh-device receipt exists; simulated driver
tests do not qualify it.

## Resources and location

Crash-recovery read-only inventory at 2026-10-09 02:29 UTC confirms the
existing SDK image `system-images;android-35;google_apis;x86_64` under
`/home/eslam/Android/Sdk`. Reuse the SDK/image in place. All **new mutable AVD
files**, index, preferences, temporary files and logs live under
`/home/eslam/Storage/android-qa-fb1-20261009`; that directory is absent.

The [host receipt](host-verification.json) records the fresh disk/RAM readings:
Storage has about 17.5 GiB free and `/` about 4.3 GiB. Available RAM fluctuates
and was below the required 6 GiB during recovery; this is no resource reservation.
Before provisioning/start, remeasure and require **at least 6 GiB available
host RAM and 16 GiB free on Storage**. These are conservative run budgets:
3 GiB guest RAM plus emulator/desktop overhead; 8 GiB logical userdata plus
cache, APK/evidence and at least 6 GiB growth/headroom. Sparse userdata may
initially consume less; do not budget only its initial allocation. No image
download/copy to the system disk is planned. Check access to `/dev/kvm` and
availability of ports 5556/5557 before start. Never terminate another lane's
emulator to satisfy these checks.

The app supports x86_64 Ubuntu and requires at least 1,800 MiB reported guest
RAM; 3,072 MiB is its comfortable-memory threshold
([preflight](../../../lib/builtin/setup/preflight.dart)). A 3 GiB guest may
report slightly below that threshold and legitimately show the slow-phone
notice. Setup must still pass the app's measured free-space/download consent
checks; the host budget does not substitute for them. The driver retains the shipped setup defaults (including Python), leaving
optional agent/voice/team downloads off. This exercises the actual first-run
path rather than silently changing its default component choices.

## Exact future final-pass commands — not executed

**Do not run this block until the coordinator says the final pass begins.**
It creates and starts a second AVD, so the resource hold applies to the whole
block. `FB1_APPROVED_SHA256` must be the coordinator-approved APK 2202 digest,
not a digest silently accepted from whatever file is present. No build,
signing, provider enrollment or APK copy is needed. It uses the existing
Python environment's Pillow installation (the same dependency as BD7).

Run from this worktree in Bash. All newly created AVD/index/preferences,
cache, temporary files, logs and evidence stay under Storage. The existing
SDK/system image and approved APK are read in place. The name is deliberately
`fb1-*`, matching the driver's dedicated-device admission rule. An existing
run directory refuses the run; never delete/reuse one to manufacture freshness.

```bash
set -euo pipefail
: "${FB1_APPROVED_SHA256:?Set the coordinator-approved APK 2202 SHA-256 first}"
export FB1_ROOT=/home/eslam/Storage/android-qa-fb1-20261009
export FB1_AVD=fb1-20261009-api35
export FB1_APK=/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk
export ANDROID_HOME=/home/eslam/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$FB1_ROOT/user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$FB1_ROOT/avd"
export TMPDIR="$FB1_ROOT/tmp"
export XDG_CACHE_HOME="$FB1_ROOT/cache"
export XDG_CONFIG_HOME="$FB1_ROOT/config"
export XDG_DATA_HOME="$FB1_ROOT/data"
export XDG_RUNTIME_DIR="$FB1_ROOT/runtime"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR -Duser.home=$ANDROID_USER_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# One reservation covers resource checks, creation, boot, driver and shutdown.
exec 9>/home/eslam/Storage/tmp/oc-emulator.lock
flock -w 3600 9
python3 - <<'PREFLIGHT'
import os, re, shutil, socket
from pathlib import Path
assert re.fullmatch(r'[0-9a-f]{64}', os.environ['FB1_APPROVED_SHA256'])
root = Path(os.environ['FB1_ROOT'])
assert not root.exists(), 'Run directory exists; obtain a new approved run name.'
assert shutil.disk_usage('/home/eslam/Storage').free >= 16 * 1024**3, 'Need 16 GiB free on Storage.'
mem = dict(line.split(':', 1) for line in Path('/proc/meminfo').read_text().splitlines())
assert int(mem['MemAvailable'].split()[0]) >= 6 * 1024**2, 'Need 6 GiB available host RAM.'
assert os.access('/dev/kvm', os.R_OK | os.W_OK), 'KVM access required.'
held = []
try:
    for port in (5556, 5557):
        sock = socket.socket()
        sock.bind(('127.0.0.1', port))
        held.append(sock)
finally:
    for sock in held:
        sock.close()
assert Path(os.environ['FB1_APK']).is_file(), 'Approved APK missing.'
PREFLIGHT
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$TMPDIR" \
  "$XDG_CACHE_HOME" "$XDG_CONFIG_HOME" "$XDG_DATA_HOME" "$XDG_RUNTIME_DIR"
chmod 700 "$FB1_ROOT" "$XDG_RUNTIME_DIR"
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "$FB1_AVD" --package 'system-images;android-35;google_apis;x86_64' \
  --device pixel_6 --path "$ANDROID_AVD_HOME/$FB1_AVD.avd" \
  >"$FB1_ROOT/provision.log" 2>&1
python3 - <<'CONFIG'
import os
from pathlib import Path
home = Path(os.environ['ANDROID_AVD_HOME'])
name = os.environ['FB1_AVD']
avd = home / (name + '.avd')
index = home / (name + '.ini')
assert avd.resolve().is_relative_to(Path(os.environ['FB1_ROOT']).resolve())
assert 'path=' + str(avd) in index.read_text().splitlines()
config = avd / 'config.ini'
updates = {'disk.dataPartition.size': '8G', 'hw.ramSize': '3072',
           'hw.cpu.ncore': '2', 'fastboot.forceColdBoot': 'yes'}
lines = [line for line in config.read_text().splitlines()
         if line.partition('=')[0].strip() not in updates]
config.write_text('\n'.join(lines + [f'{k}={v}' for k, v in updates.items()]) + '\n')
CONFIG

# The trap verifies PID start identity before stopping only this owned process.
fb1_emulator_pid=
fb1_emulator_start=
fb1_start_identity() {
  python3 - "$1" <<'IDENTITY'
import sys
from pathlib import Path
try:
    raw = Path('/proc/' + sys.argv[1] + '/stat').read_text()
    print(raw.rsplit(')', 1)[1].split()[19])
except (OSError, IndexError):
    raise SystemExit(1)
IDENTITY
}
fb1_shutdown() {
  local result=$?
  trap - EXIT
  if [[ -n "$fb1_emulator_pid" && -n "$fb1_emulator_start" ]] && \
      [[ "$(fb1_start_identity "$fb1_emulator_pid" || true)" == "$fb1_emulator_start" ]]; then
    kill -TERM "$fb1_emulator_pid"
    for _ in {1..30}; do
      kill -0 "$fb1_emulator_pid" 2>/dev/null || break
      sleep 1
   done
    if [[ "$(fb1_start_identity "$fb1_emulator_pid" || true)" == "$fb1_emulator_start" ]]; then
      kill -KILL "$fb1_emulator_pid"
    fi
    wait "$fb1_emulator_pid" 2>/dev/null || true
  fi
  exit "$result"
}
trap fb1_shutdown EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$ANDROID_HOME/emulator/emulator" -avd "$FB1_AVD" -ports 5556,5557 \
  -memory 3072 -cores 2 -no-snapshot-load -no-snapshot-save \
  -no-window -gpu swiftshader_indirect >"$FB1_ROOT/emulator.log" 2>&1 9>&- &
fb1_emulator_pid=$!
fb1_emulator_start=$(fb1_start_identity "$fb1_emulator_pid")
booted=false
for _ in {1..180}; do
  kill -0 "$fb1_emulator_pid" 2>/dev/null || break
  if [[ "$(timeout 10s adb -s emulator-5556 shell getprop sys.boot_completed 9>&- 2>/dev/null | tr -d '\r')" == 1 ]]; then
    booted=true
    break
  fi
  sleep 1
done
[[ "$booted" == true ]]
# Recheck exact owned AVD identity before any device mutation.
[[ "$(timeout 10s adb -s emulator-5556 emu avd name 9>&- | tr -d '\r')" == "$(printf '%s\nOK' "$FB1_AVD")" ]]
[[ "$(timeout 10s adb -s emulator-5556 shell am get-current-user 9>&- | tr -d '\r')" == 0 ]]
# Required by the shared FQ9 private-data admission; Google APIs image is userdebug.
timeout 20s adb -s emulator-5556 root >"$FB1_ROOT/adb-root.log" 2>&1 9>&-
for _ in {1..30}; do
  [[ "$(timeout 10s adb -s emulator-5556 shell id -u 9>&- 2>/dev/null | tr -d '\r')" == 0 ]] && break
  sleep 1
done
[[ "$(timeout 10s adb -s emulator-5556 shell id -u 9>&- | tr -d '\r')" == 0 ]]
python3 tool/qa/fb1_first_run.py --execute --lock-fd 9 \
  --serial emulator-5556 --avd "$FB1_AVD" --run-id fb1-20261009 \
  --apk "$FB1_APK" --build 2202 --version 1.2.0 \
  --sha256 "$FB1_APPROVED_SHA256" --output "$FB1_ROOT/evidence"
```

The driver verifies that descriptor 9 refers to the actual emulator lock and
already owns its exclusive reservation. It never independently reacquires an
outer lock. Its normal CLI without `--lock-fd` still acquires a reservation
itself for an already provisioned AVD. The inherited descriptor remains held
until the shutdown trap exits. `adb root` applies only to the new owned AVD;
if root/private-data admission is unavailable, fail the row and retain the
partial evidence instead of weakening admission or touching the shared AVD.

The driver rechecks AVD name, serial `emulator-5556`, user 0, absent package/
private app data and candidate hash/package/version/signer before installing.
No wipe, uninstall or user changes occur. It retains the candidate and project
on this dedicated AVD; only the owned emulator process is stopped. The existing
[FQ9 fresh admission](../../../tool/qa/fq9/fresh.py) refuses reused installations.
After the run, review only the driver's sanitized JPGs and `report.json` for
check-in; host emulator/provision logs stay local. APK 2202 remains on the
shared `emulator-5554`, which this sequence never addresses.

## Final-pass observations and model blockage

Record the actual first-run question; selection of **On this phone**; phone
setup choices and download consent; setup progress; successful connection;
new conversation/model choice; sent owned fixed prompt; and final assistant
reply. Capture intermediate permission/error screens when encountered.
Bound waits and keep the last successfully captured stage when a step fails.
Screenshots must use the driver's private-content guard; never capture an API
key/sign-in form, account identity, unrelated conversation or raw setup logs.
Only an actual assistant reply from the app-managed server qualifies FB1;
onboarding/setup observations separately qualify the narrower FQ9 row.

A fresh AVD has no imported provider account. The driver uses the app's
currently selected/default model. It does not automate choosing an arbitrary
catalog model: the public UI does not expose a stable, credential-free model
identifier sufficient to make that choice safely. If the UI requires a choice,
it records `fb1_model_selection_required` as BLOCKED before sending. A future
coordinator-led picker step may choose an observed free model; do not rerun the
clean-install driver on that now-used AVD or call this initial row PASS. Do not
enroll a provider or reuse the shared emulator's credentials. The driver
distinguishes provider sign-in, model choice, failed catalog and
unavailable selection using public UI copy. These are bounded BLOCKED outcomes
with the safe screen and successful install/setup evidence retained. Generic
unknown states remain failures/timeouts rather than being guessed as auth.

The current app already offers **Connect a provider**, **Add an API key for
{name}**, **Sign in to {name}**, and a free-model notice
([picker contract](../../../lib/ui/widgets/pickers_catalog_sections.dart),
[copy](../../../lib/l10n/app_en.arb)). A “Connect a model” first-reply recovery
row would need to explain the observed blockage, open that provider route,
return to the same pending conversation, refresh the catalog, distinguish
“saved but server has not loaded it yet” from ready, offer a usable model,
and let the person retry the pending prompt. Do not call setup failure a model
failure, promise a free model before fetching the catalog, or treat a saved key
as proof that inference works. UI implementation belongs to the coordinator;
this task records the contract and does not change product copy.

## Offline verification

All 29 focused Python checks pass serially through
`OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 -m unittest
tool.qa.test_fb1_first_run`. Removing the scoped default-project allowance
makes its screenshot regression fail; the restored source passes. Coverage
includes installed/private-data refusal, credential screenshot rejection,
marker-versus-prompt-echo completion, model blockages, and real temporary-file
flock ownership/reuse. The future command block passes `bash -n` without being
executed. No Flutter or native sources changed; no full suite or device
qualification is claimed.
