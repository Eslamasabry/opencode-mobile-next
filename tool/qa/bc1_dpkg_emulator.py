#!/usr/bin/env python3
"""BC1: interrupt real dpkg inside the dev emulator, in an isolated database.

Uses the installed app's Ubuntu/PRoot. Only the scratch fixture is written.
The apt shim models an offline repository with one dependency: repair installs
that local .deb through real dpkg, never the shared rootfs's apt/database.
Every ADB session is serialized with the shared emulator lock.
"""

import argparse
import hashlib
import pathlib
import re
import shlex
import subprocess
import tempfile
import uuid


REPO = pathlib.Path(__file__).resolve().parents[2]
LOCK = "/home/eslam/Storage/tmp/oc-emulator.lock"
PACKAGE = "io.github.eslamasabry.opencode_mobile"


def run(argv, *, timeout=60):
    result = subprocess.run(argv, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {result.stdout}{result.stderr}")
    return result.stdout


def adb(*args):
    return run(["flock", LOCK, "adb", "-s", "emulator-5554", *args])


def prelude(source):
    match = re.search(r"const setupPrelude = r'''(.*?)''';", source, re.S)
    if not match:
        raise RuntimeError("Could not extract the literal setupPrelude")
    return match.group(1)


FIXTURE = r'''#!/bin/sh
set -eu
cd /bc1-qa
mkdir -p bin tmp home lists root db/updates db/info db/triggers
: > db/status
export OC_BC1_ROOT=/bc1-qa
export TMPDIR=/bc1-qa/tmp
export OC_APT_STAMP=/bc1-qa/stamp
export OC_APT_LISTS=/bc1-qa/lists
touch stamp lists/fixture_Packages
cat > bin/dpkg <<'EOF'
#!/bin/sh
printf 'dpkg %s\n' "$*" >> /bc1-qa/calls.log
exec /usr/bin/dpkg --admindir=/bc1-qa/db --instdir=/bc1-qa/root \
  --force-script-chrootless --log=/bc1-qa/dpkg.log "$@"
EOF
cat > bin/apt-get <<'EOF'
#!/bin/sh
set -eu
printf 'apt-get %s\n' "$*" >> /bc1-qa/calls.log
case " $* " in
  *' update '*) exit 0 ;;
esac
if [ -e /bc1-qa/unrepairable ]; then
  echo 'fixture: local dependency unavailable' >&2
  exit 100
fi
case " $* " in
  *' -f '*|*' --fix-broken '*)
    dpkg -i /bc1-qa/dependency.deb
    dpkg --configure -a
    exit 0 ;;
esac
if ! dpkg -s bc1-qa-package | grep -q '^Status: install ok installed$'; then
  echo 'fixture: interrupted package needs dependency repair' >&2
  exit 100
fi
EOF
chmod +x bin/dpkg bin/apt-get
export PATH=/bc1-qa/bin:/usr/sbin:/usr/bin:/sbin:/bin
mkdir -p dependency/DEBIAN package/DEBIAN
chmod 755 dependency/DEBIAN package/DEBIAN
cat > dependency/DEBIAN/control <<'EOF'
Package: bc1-qa-dependency
Version: 1.0
Architecture: all
Maintainer: OpenCode QA <qa@example.invalid>
Description: isolated interruption dependency
EOF
cat > package/DEBIAN/control <<'EOF'
Package: bc1-qa-package
Version: 1.0
Architecture: all
Maintainer: OpenCode QA <qa@example.invalid>
Depends: bc1-qa-dependency (= 1.0)
Description: isolated interruption fixture
EOF
cat > package/DEBIAN/postinst <<'EOF'
#!/bin/sh
set -eu
if [ ! -e /bc1-qa/released ]; then
  echo "$$" > /bc1-qa/postinst.pid
  while :; do sleep 0.1; done
fi
EOF
chmod 755 package/DEBIAN/postinst
/usr/bin/dpkg-deb --build dependency dependency.deb
/usr/bin/dpkg-deb --build package package.deb
interrupt_install() {
rm -f released postinst.pid
dpkg --unpack package.deb
dpkg --force-depends --configure bc1-qa-package > interruption.log 2>&1 &
install_pid=$!
attempt=0
while [ ! -s postinst.pid ]; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 100 ]; then
    kill -TERM "$install_pid" || true
    wait "$install_pid" || true
    cat interruption.log
    exit 1
  fi
  sleep 0.1
done
postinst_pid=$(cat postinst.pid)
case "$postinst_pid" in ''|*[!0-9]*) exit 1 ;; esac
kill -TERM "$postinst_pid"
wait "$install_pid" || true
touch released
printf '\n=== Real interrupted state ===\n'
cat interruption.log
dpkg -s bc1-qa-package
dpkg --audit
}
interrupt_install

run_prelude() {
  label=$1
  : > calls.log
  printf '\n=== %s ===\n' "$label"
  rc=0
  /bin/sh -c '. "/bc1-qa/'"$label"'.sh"; oc_apt_install bc1-qa-package' \
    > "$label.log" 2>&1 || rc=$?
  printf 'RESULT %s exit=%s\n' "$label" "$rc"
  cat "$label.log"
  cat calls.log
  dpkg -s bc1-qa-package
  if [ "$label" = baseline ]; then
    [ "$rc" -ne 0 ]
    dpkg -s bc1-qa-package | grep -q '^Status: install ok half-configured$'
  elif [ "$label" = fixed ]; then
    [ "$rc" -eq 0 ]
    dpkg -s bc1-qa-package | grep -q '^Status: install ok installed$'
    dpkg -s bc1-qa-dependency | grep -q '^Status: install ok installed$'
    [ -z "$(dpkg --audit)" ]
  else
    [ "$rc" -ne 0 ]
    grep -q 'dpkg --audit' "$label.log"
    grep -q 'bc1-qa-package' "$label.log"
  fi
}
run_prelude baseline
run_prelude fixed
dpkg --purge bc1-qa-package bc1-qa-dependency
interrupt_install
touch unrepairable
run_prelude failure
printf '\nPASS: baseline fails; fixed repairs; failed repair includes dpkg --audit\n'
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-ref", default="HEAD")
    parser.add_argument("--output", type=pathlib.Path,
                        default=REPO / "docs/qa/BC1-2026-10-07/emulator.log")
    args = parser.parse_args()
    source_path = "lib/builtin/setup/setup_scripts.dart"
    baseline = prelude(run(["git", "-C", str(REPO), "show",
                           f"{args.baseline_ref}:{source_path}"]))
    fixed = prelude((REPO / source_path).read_text())
    if baseline == fixed:
        raise RuntimeError("Baseline and fixed preludes are identical; select the pre-fix revision")
    installed = adb("shell", "cmd", "package", "path", PACKAGE).strip()
    apk = installed.removeprefix("package:")
    if "\n" in apk or not apk.startswith("/data/app/") or not apk.endswith("/base.apk"):
        raise RuntimeError("Unexpected installed package path")
    native = apk.removesuffix("/base.apk") + "/lib/x86_64"
    rootfs = f"/data/user/0/{PACKAGE}/files/linux/ubuntu"
    live_status = rootfs + "/var/lib/dpkg/status"
    status_before = adb("shell", "sha256sum", live_status).split()[0]
    scratch = "/data/local/tmp/oc-bc1-" + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="oc-bc1-") as local:
        directory = pathlib.Path(local)
        (directory / "baseline.sh").write_text(baseline)
        (directory / "fixed.sh").write_text(fixed)
        (directory / "failure.sh").write_text(fixed)
        (directory / "fixture.sh").write_text(FIXTURE)
        adb("shell", "mkdir", "-p", scratch + "/tmp")
        try:
            for path in sorted(directory.iterdir()):
                adb("push", str(path), scratch + "/" + path.name)
            environment = (f"PROOT_LOADER={shlex.quote(native + '/libproot-loader.so')} "
                           f"PROOT_TMP_DIR={shlex.quote(scratch + '/tmp')} "
                           f"LD_LIBRARY_PATH={shlex.quote(native)} ")
            command = [native + "/libproot.so", "--root-id", "--kill-on-exit",
                       "--link2symlink", "-L", "--sysvipc", f"--rootfs={rootfs}",
                       "--bind=/dev", "--bind=/proc", "--bind=/sys",
                       f"--bind={scratch}:/bc1-qa", f"--bind={scratch}/tmp:/tmp",
                       "--cwd=/bc1-qa", "/usr/bin/env", "-i", "HOME=/bc1-qa/home",
                       "LANG=C.UTF-8", "PATH=/usr/bin:/bin", "TMPDIR=/bc1-qa/tmp",
                       "/bin/sh", "/bc1-qa/fixture.sh"]
            result = run(["flock", LOCK, "adb", "-s", "emulator-5554", "shell",
                          environment + shlex.join(command)], timeout=60)
            status_after = adb("shell", "sha256sum", live_status).split()[0]
            if status_after != status_before:
                raise RuntimeError("Shared rootfs dpkg status changed during this isolated session")
            metadata = (f"Emulator: emulator-5554 (x86_64), installed Ubuntu/PRoot\n"
                        f"Baseline ref: {args.baseline_ref}\n"
                        f"Baseline prelude SHA256: {hashlib.sha256(baseline.encode()).hexdigest()}\n"
                        f"Fixed prelude SHA256: {hashlib.sha256(fixed.encode()).hexdigest()}\n"
                        f"Live dpkg status SHA256 before/after: {status_before} (unchanged)\n"
                        "Isolation: scratch dpkg DB/root; offline apt shim; no live package mutations\n")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(metadata + result)
            print(result)
        finally:
            adb("shell", "rm", "-rf", scratch)


if __name__ == "__main__":
    main()
