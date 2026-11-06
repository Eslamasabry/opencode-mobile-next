#!/usr/bin/env bash
# Emulator proof for issue #117 part 2. Run it INSIDE the emulator lock:
#   flock <your emulator lock file> tool... or hold the lock yourself, then
#   docs/qa/issue-117-unpack-2026-11-06/emulator-proof.sh path/to/app-release.apk
# It installs over the existing app (same signer, never uninstalls), makes the guest's
# `tar` die with a real SIGSEGV, makes `ln` die with SIGSEGV unless GLIBC_TUNABLES is set
# (so the Node step also needs the compatibility retry), removes /opt/node, and then
# the Update OpenCode / setup run from the app must show: unpack fallback + compatibility
# retry + Node installed. Restores the real tools at the end (`restore` argument).
set -euo pipefail
ADB=${ADB:-adb}
PKG=io.github.eslamasabry.opencode_mobile
ROOT=/data/data/$PKG/files/linux/ubuntu
case ${1:-} in
  restore)
    $ADB shell su 0 sh -c "for t in tar ln; do [ -f $ROOT/usr/bin/\$t.real ] && mv -f $ROOT/usr/bin/\$t.real $ROOT/usr/bin/\$t; done"
    exit 0 ;;
esac
$ADB install -r "$1"
$ADB shell su 0 sh -c "
  cd $ROOT/usr/bin
  [ -f tar.real ] || cp -p tar tar.real
  [ -f ln.real ] || cp -p ln ln.real
  printf '#!/bin/sh\nkill -SEGV \$\$\n' > tar; chmod 755 tar
  printf '#!/bin/sh\n[ -n \"\$GLIBC_TUNABLES\" ] && exec /usr/bin/ln.real \"\$@\"\nkill -SEGV \$\$\n' > ln; chmod 755 ln
  rm -rf $ROOT/opt/node $ROOT/opt/node.new"
echo "Now run the setup/Update step from the app, then: $0 restore"
