#!/usr/bin/env bash
# Build the F-Droid variant on the build server with PLAIN upstream Flutter,
# outside the Shorebird cache, and audit the APK for Shorebird / Google SDKs.
#
#   tool/fdroid/vps_build.sh <build-number>      (4330-4339 reserved 2026-10-31)
#
# What it proves (docs/release/fdroid-2026-10-31.md): the F-Droid recipe's
# steps work with the upstream Flutter 3.47.1 tag. It copies this worktree to
# ~/fdroid-scratch/src on OC_VPS_HOST (docs/qa, ~1 GB of evidence, is left out), applies tool/fdroid/prebuild.dart,
# builds with the throwaway signing key (never the owner's key), and leaves the
# APK plus tool/fdroid/audit_apk.sh's report in ~/fdroid-scratch/out. Needs
# ~/fdroid-scratch/flutter (git clone -b 3.47.1 https://github.com/flutter/flutter).
set -euo pipefail
n="${1:-}"
[[ "$n" =~ ^[0-9]+$ ]] || { echo "usage: vps_build.sh <build-number>" >&2; exit 64; }
root="$(git rev-parse --show-toplevel)"
for _env in "$root/tool/qa/build-server.env" "${XDG_CONFIG_HOME:-$HOME/.config}/oc-app/build-server.env"; do
  [ -f "$_env" ] && { . "$_env"; break; }
done
host="${OC_VPS_HOST:-}"
[ -n "$host" ] || { echo "vps_build: set OC_VPS_HOST (or tool/qa/build-server.env)" >&2; exit 64; }
ssh -o BatchMode=yes "$host" "mkdir -p ~/fdroid-scratch/src ~/fdroid-scratch/out"
rsync -a --delete --exclude build/ --exclude .dart_tool/ --exclude .git --exclude docs/qa --exclude tool/qa/build-server.env \
  --exclude 'test/**/failures/' --exclude android/key.properties --exclude '*.keystore' --exclude '*.jks' \
  "$root/" "$host:fdroid-scratch/src/"
ssh -o BatchMode=yes "$host" "bash -lc 'set -euo pipefail
export PATH=\$HOME/fdroid-scratch/flutter/bin:\$(echo \$PATH | tr : \"\\n\" | grep -v shorebird | paste -sd:)
export PUB_CACHE=\$HOME/fdroid-scratch/pub-cache
cd ~/fdroid-scratch/src
flutter --version | head -2
case \$(command -v flutter) in \$HOME/fdroid-scratch/*) ;; *) echo \"not plain Flutter\"; exit 1;; esac
dart tool/fdroid/prebuild.dart
cp ~/.oc-throwaway-signing/key.properties android/key.properties
flutter config --no-analytics >/dev/null
flutter pub get
rm -rf build/app/outputs
flock \$HOME/.oc-vps-apk-build.lock flutter build apk --release --build-number=$n \
  --dart-define=OC_FDROID=true --android-project-arg=ocFdroid=true
cp build/app/outputs/flutter-apk/app-release.apk ~/fdroid-scratch/out/opencode-fdroid-$n.apk
rm -f android/key.properties
bash tool/fdroid/audit_apk.sh ~/fdroid-scratch/out/opencode-fdroid-$n.apk | tee ~/fdroid-scratch/out/audit-$n.txt
'"
