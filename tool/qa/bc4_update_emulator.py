#!/usr/bin/env python3
"""Exercise the production component journal in isolated emulator PRoot storage.

Each crash kills only a fixture PID. Fresh shell recovery simulates the proposed
startup hook; this proof does not install that hook or mutate installed agents.
"""

import argparse
import hashlib
import pathlib
import re
import shlex
import subprocess
import sys
import tempfile
import uuid


REPO = pathlib.Path(__file__).resolve().parents[2]
LOCK = "/home/eslam/Storage/tmp/oc-emulator.lock"
PACKAGE = "io.github.eslamasabry.opencode_mobile"


def run(argv, *, timeout=90):
    result = subprocess.run(argv, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {result.stdout}{result.stderr}")
    return result.stdout


def adb(*args):
    return run(["flock", LOCK, "adb", "-s", "emulator-5554", *args], timeout=60)


FIXTURE = r'''#!/bin/sh
set -eu
cd /bc4-qa
mkdir -p bin tmp home
export PATH=/bc4-qa/bin:/usr/sbin:/usr/bin:/sbin:/bin
export TMPDIR=/bc4-qa/tmp
. /bc4-qa/helper.sh
cat > bin/mv <<'EOF'
#!/bin/sh
set -eu
src= dst=
for arg in "$@"; do src=$dst; dst=$arg; done
held=false
case ${QA_MV_MODE:-none} in
  old) [ "$src" != "$QA_TARGET" ] || [ "$dst" != "$QA_TARGET.oc-good" ] || held=true ;;
  recovery) [ "$src" != "$QA_TARGET.oc-good" ] || [ "$dst" != "$QA_TARGET" ] || held=true ;;
esac
/usr/bin/mv "$@"
if "$held"; then
  printf '%s\n' "$$" > /bc4-qa/mv.pid
  awk '{print $4}' "/proc/$$/stat" > /bc4-qa/mv.parent-pid
  : > /bc4-qa/mv-checkpoint
  while :; do sleep 0.05; done
fi
EOF
chmod 755 bin/mv

write_exec() {
  artifact=$1 version=$2 result=$3
  mkdir -p "$(dirname "$artifact")"
  printf '#!/bin/sh\nprintf "%%s\\n" "%s"\nexit %s\n' "$version" "$result" > "$artifact"
  chmod 755 "$artifact"
}
make_artifact() {
  artifact=$1 kind=$2 version=$3 result=$4
  if [ "$kind" = directory ]; then
    mkdir -p "$artifact/bin"
    write_exec "$artifact/bin/paseo" "$version" "$result"
  else
    write_exec "$artifact" "$version" "$result"
  fi
}
version_of() {
  artifact=$1 kind=$2
  if [ "$kind" = directory ]; then "$artifact/bin/paseo"; else "$artifact"; fi
}
fresh_recover() {
  /bin/sh -c '. /bc4-qa/helper.sh; oc_update_recover "$1"' sh "$1"
}
wait_checkpoint() {
  file=$1
  attempt=0
  while [ ! -e "$file" ]; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 200 ] || { echo 'FAIL fixture checkpoint timeout'; return 1; }
    sleep 0.05
  done
}
stop_exact() {
  pid=$1
  case $pid in ''|*[!0-9]*) echo 'FAIL invalid fixture PID'; exit 1 ;; esac
  kill -KILL "$pid"
}
crash_after_activation() {
  target=$1 candidate=$2
  rm -f activated
  /bin/sh -c '. /bc4-qa/helper.sh; oc_update_activate "$1" "$2";
    : > /bc4-qa/activated; while :; do sleep 0.05; done' sh "$target" "$candidate" &
  activation_pid=$!
  wait_checkpoint /bc4-qa/activated
  [ -e "$target.oc-pending" ]
  stop_exact "$activation_pid"
  wait "$activation_pid" 2>/dev/null || true
}
component_crash() {
  label=$1 kind=$2
  target=/bc4-qa/$label/active
  candidate=/bc4-qa/$label/candidate
  make_artifact "$target" "$kind" "$label-v1" 0
  make_artifact "$candidate" "$kind" "$label-broken" 7
  if [ "$kind" = directory ]; then
    ln -s "$target/bin/paseo" /bc4-qa/$label/command
    link_before=$(readlink /bc4-qa/$label/command)
  fi
  crash_after_activation "$target" "$candidate"
  if version_of "$target" "$kind" >/dev/null 2>&1; then
    echo 'FAIL bad candidate unexpectedly succeeded'; exit 1
  fi
  fresh_recover "$target"
  [ "$(version_of "$target" "$kind")" = "$label-v1" ]
  [ ! -e "$target.oc-pending" ]
  fresh_recover "$target"
  [ "$(version_of "$target" "$kind")" = "$label-v1" ]
  if [ "$kind" = directory ]; then
    [ "$(readlink /bc4-qa/$label/command)" = "$link_before" ]
    [ "$(/bc4-qa/$label/command)" = "$label-v1" ]
  fi
  printf 'PASS %s activation crash: fresh recovery restores good code; replay stable\n' "$label"
}
component_crash Claude file
component_crash Paseo directory
component_crash OpenCode file

target=/bc4-qa/Paseo-link/command
candidate=/bc4-qa/Paseo-link/candidate
write_exec /bc4-qa/Paseo-link/old-code Paseo-link-v1 0
write_exec /bc4-qa/Paseo-link/new-code Paseo-link-broken 7
ln -s /bc4-qa/Paseo-link/old-code "$target"
ln -s /bc4-qa/Paseo-link/new-code "$candidate"
crash_after_activation "$target" "$candidate"
[ "$(readlink "$target")" = /bc4-qa/Paseo-link/new-code ]
fresh_recover "$target"
fresh_recover "$target"
[ "$(readlink "$target")" = /bc4-qa/Paseo-link/old-code ]
[ "$("$target")" = Paseo-link-v1 ]
printf 'PASS Paseo launch symlink activation crash restores previous link and runnable code\n'

target=/bc4-qa/rename/active
candidate=/bc4-qa/rename/candidate
make_artifact "$target" file rename-v1 0
make_artifact "$candidate" file rename-broken 7
export QA_TARGET="$target" QA_MV_MODE=old
rm -f mv-checkpoint mv.pid
/bin/sh -c '. /bc4-qa/helper.sh; oc_update_activate "$1" "$2"' sh "$target" "$candidate" &
activation_pid=$!
wait_checkpoint /bc4-qa/mv-checkpoint
[ -e "$target.oc-pending" ] && [ ! -e "$target" ] && [ -e "$target.oc-good" ]
stop_exact "$activation_pid"
helper_pid=$(cat mv.parent-pid)
[ "$helper_pid" = "$activation_pid" ] || stop_exact "$helper_pid"
stop_exact "$(cat mv.pid)"
wait "$activation_pid" 2>/dev/null || true
unset QA_MV_MODE QA_TARGET
fresh_recover "$target"
[ "$(version_of "$target" file)" = rename-v1 ]
printf 'PASS interruption between old rename and candidate activation recovers good code\n'

make_artifact "$candidate" file rename-broken 7
crash_after_activation "$target" "$candidate"
export QA_TARGET="$target" QA_MV_MODE=recovery
rm -f mv-checkpoint mv.pid
/bin/sh -c '. /bc4-qa/helper.sh; oc_update_recover "$1"' sh "$target" &
recovery_pid=$!
wait_checkpoint /bc4-qa/mv-checkpoint
[ -e "$target.oc-pending" ]
stop_exact "$recovery_pid"
helper_pid=$(cat mv.parent-pid)
[ "$helper_pid" = "$recovery_pid" ] || stop_exact "$helper_pid"
stop_exact "$(cat mv.pid)"
wait "$recovery_pid" 2>/dev/null || true
unset QA_MV_MODE QA_TARGET
fresh_recover "$target"
fresh_recover "$target"
[ "$(version_of "$target" file)" = rename-v1 ]
[ ! -e "$target.oc-pending" ]
printf 'PASS interrupted recovery is replayable from a fresh shell\n'

target=/bc4-qa/generation/active
candidate=/bc4-qa/generation/candidate
make_artifact "$target" file generation-v1 0
make_artifact "$candidate" file generation-v2 0
oc_update_activate "$target" "$candidate"
oc_update_commit "$target"
[ "$(version_of "$target" file)" = generation-v2 ]
[ "$(version_of "$target.oc-good" file)" = generation-v1 ]
[ ! -e "$target.oc-pending" ]
fresh_recover "$target"
[ "$(version_of "$target" file)" = generation-v2 ]
make_artifact "$candidate" file generation-v3 0
oc_update_activate "$target" "$candidate"
oc_update_commit "$target"
[ "$(version_of "$target" file)" = generation-v3 ]
[ "$(version_of "$target.oc-good" file)" = generation-v2 ]
printf 'PASS successful commit retains last good version; next update keeps one generation\n'

target=/bc4-qa/first/active
candidate=/bc4-qa/first/candidate
make_artifact "$candidate" file first-broken 7
crash_after_activation "$target" "$candidate"
fresh_recover "$target"
fresh_recover "$target"
[ ! -e "$target" ] && [ ! -e "$target.oc-pending" ]
printf 'PASS interrupted first install without old version removes incomplete activation\n'

target=/bc4-qa/corrupt/active
make_artifact "$target" file corrupt-v1 0
make_artifact "$target.oc-good" file corrupt-v0 0
printf 'invalid-journal\n' > "$target.oc-pending"
if fresh_recover "$target" > corrupt.log 2>&1; then
  echo 'FAIL corrupt journal was accepted'; exit 1
fi
[ "$(version_of "$target" file)" = corrupt-v1 ]
[ "$(version_of "$target.oc-good" file)" = corrupt-v0 ]
[ -e "$target.oc-pending" ]
printf 'PASS corrupt journal fails closed without mutating active or backup code\n'
printf 'PASS: isolated production update helper recovery checks complete\n'
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path,
                        default=REPO / "docs/qa/BC4-2026-10-07/emulator.log")
    parser.add_argument("--locked", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if not args.locked:
        print(run([str(REPO / "tool/qa/machine_lock.sh"), "test", "--", sys.executable,
                   str(pathlib.Path(__file__).resolve()), "--locked", "--output", str(args.output)]), end="")
        return
    source = (REPO / "lib/builtin/setup/component_updates.dart").read_text()
    match = re.search(r"const componentUpdatePrelude = r'''(.*?)''';", source, re.S)
    if not match:
        raise RuntimeError("Production componentUpdatePrelude literal is not ready")
    helper = match.group(1)
    apk = adb("shell", "cmd", "package", "path", PACKAGE).strip().removeprefix("package:")
    if "\n" in apk or not apk.startswith("/data/app/") or not apk.endswith("/base.apk"):
        raise RuntimeError("Unexpected installed package path")
    native = apk.removesuffix("/base.apk") + "/lib/x86_64"
    rootfs = f"/data/user/0/{PACKAGE}/files/linux/ubuntu"
    scratch = "/data/local/tmp/oc-bc4-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="oc-bc4-") as local:
        directory = pathlib.Path(local)
        (directory / "helper.sh").write_text(helper)
        (directory / "fixture.sh").write_text(FIXTURE)
        adb("shell", "mkdir", "-p", scratch + "/tmp")
        try:
            for path in sorted(directory.iterdir()):
                adb("push", str(path), scratch + "/" + path.name)
            environment = (f"PROOT_LOADER={shlex.quote(native + '/libproot-loader.so')} "
                           f"PROOT_TMP_DIR={shlex.quote(scratch + '/tmp')} "
                           f"LD_LIBRARY_PATH={shlex.quote(native)} ")
            argv = [native + "/libproot.so", "--root-id", "--kill-on-exit", "--link2symlink",
                    "-L", "--sysvipc", "--rootfs=" + rootfs, "--bind=/dev", "--bind=/proc",
                    "--bind=/sys", "--bind=" + scratch + ":/bc4-qa",
                    "--bind=" + scratch + "/tmp:/tmp", "--cwd=/bc4-qa",
                    "/usr/bin/env", "-i", "HOME=/bc4-qa/home", "LANG=C.UTF-8",
                    "PATH=/usr/sbin:/usr/bin:/sbin:/bin", "TMPDIR=/bc4-qa/tmp",
                    "/bin/sh", "/bc4-qa/fixture.sh"]
            result = run(["flock", LOCK, "adb", "-s", "emulator-5554", "shell",
                          environment + shlex.join(argv)], timeout=60)
            metadata = ("Emulator: emulator-5554; installed Ubuntu/PRoot; isolated fixture targets\n"
                        f"Production update helper SHA256: {hashlib.sha256(helper.encode()).hexdigest()}\n"
                        "Startup integration: fresh shell simulates coordinator hook; full app hook not applied\n")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(metadata + result)
            print(result, end="")
        finally:
            adb("shell", "rm", "-rf", scratch)


if __name__ == "__main__":
    main()
