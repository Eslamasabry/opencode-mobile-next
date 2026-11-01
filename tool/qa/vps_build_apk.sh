#!/usr/bin/env bash
# Build a release APK on the build server, then sign it here with the real key.
#
#   tool/qa/vps_build_apk.sh <build-number>
#
# Why: the local Gradle release build needs 4-5 GB and trips low-memory alerts.
# The real signing key never leaves this PC:
#   1. rsync this worktree (build/, .dart_tool/, .git/, key.properties left out)
#      to ~/wt/<name>-apk on OC_VPS_HOST. That folder is the build's alone:
#      vps_test.sh syncs ~/wt/<name> with --delete, and on 2026-10-31 a test
#      run doing so mid-build removed the generated (git-ignored)
#      GeneratedPluginRegistrant.java before Gradle compiled it. APK 4293 then
#      registered no plugins and crashed on every start. The folder's lock is
#      held by the sync and the whole build, so two builds of one worktree
#      cannot overwrite each other either;
#   2. the server signs with a THROWAWAY keystore it creates once under
#      ~/.oc-throwaway-signing/ (its android/key.properties points there), and
#      runs `flutter build apk --release --build-number=N`; Gradle's
#      verifyReleasePhoneEngineApk check still runs and must pass;
#   3. the APK and mapping.txt come back to <worktree>/build/vps-apk/;
#   4. here the APK is re-signed with the real key from key.properties (read
#      only inside this script; passwords go to apksigner by environment
#      variable, never argv or output). The script FAILS unless the signer
#      SHA-256 is the known local release key, and unless the APK still holds
#      the plugin registrant (tool/qa/apk_has_plugins.sh).
# Output: build number, final APK path, size, signer digest, elapsed time.
# Differences from a PC build: signed by apksigner after the build (same key,
# same certificate; the signature blocks are rewritten, contents are not).
# Env: OC_VPS_FLUTTER_REV (Shorebird cache revision on the server; default is
# the current pin, set 91f8bd75076e9c740aa13cf67eb9ec1a093f68f5 for 3.47.1),
# OC_BUILD_ARGS (extra `flutter build apk` arguments, e.g.
# --split-debug-info=build/symbols; empty by default), OC_VPS_HOST, OC_KEY_PROPERTIES (default: this worktree's
# android/key.properties, else the main checkout's), ANDROID_BUILD_TOOLS.
set -euo pipefail
n="${1:-}"
[[ "$n" =~ ^[0-9]+$ ]] || { echo "usage: vps_build_apk.sh <build-number>" >&2; exit 64; }
expected=1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C
start=$SECONDS
root="$(git rev-parse --show-toplevel)"
name="$(basename "$root")"
# The build server's ssh target is private: it comes from the environment or
# from tool/qa/build-server.env (git-ignored) or ~/.config/oc-app/build-server.env.
for _env in "$(git rev-parse --show-toplevel 2>/dev/null)/tool/qa/build-server.env" \
    "${XDG_CONFIG_HOME:-$HOME/.config}/oc-app/build-server.env"; do
  [ -f "$_env" ] && { . "$_env"; break; }
done
host="${OC_VPS_HOST:-${OC_VPS_HOST:-}}"
[ -n "$host" ] || { echo "$(basename "$0"): set OC_VPS_HOST (or tool/qa/build-server.env)" >&2; exit 64; }
bt="${ANDROID_BUILD_TOOLS:-$(ls -d "$HOME"/Android/Sdk/build-tools/* | sort -V | tail -1)}"
kp="${OC_KEY_PROPERTIES:-$root/android/key.properties}"
if [[ ! -f "$kp" ]]; then
  main="$(dirname "$(git -C "$root" rev-parse --path-format=absolute --git-common-dir)")"
  kp="$main/android/key.properties"
fi
[[ -f "$kp" ]] || { echo "vps_build_apk: no key.properties found" >&2; exit 66; }
out="$root/build/vps-apk"
# The build's own server folder and its lock (see step 1).
sdir="$name-apk"
mkdir -p "$out"
log="$out/server-build-$n.log"

ssh -o BatchMode=yes "$host" "mkdir -p ~/wt"
rsync -a --delete --exclude build/ --exclude .dart_tool/ --exclude .git/ \
  --exclude 'test/**/failures/' --exclude android/key.properties \
  --exclude '*.keystore' --exclude '*.jks' \
  --rsync-path="flock ~/wt/.$sdir.lock rsync" \
  "$root/" "$host:wt/$sdir/"

# Server side: throwaway keystore (created once), key.properties, build.
ssh -o BatchMode=yes "$host" "bash -lc 'set -euo pipefail
exec 8>~/wt/.$sdir.lock
flock 8
d=\$HOME/.oc-throwaway-signing
if [[ ! -f \$d/throwaway.jks ]]; then
  mkdir -p \$d && chmod 700 \$d
  pw=\$(head -c 24 /dev/urandom | base64 | tr -dc A-Za-z0-9)
  keytool -genkeypair -keystore \$d/throwaway.jks -alias throwaway -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass \$pw -keypass \$pw \
    -dname \"CN=oc throwaway build signer\" >/dev/null 2>&1
  printf \"keyAlias=throwaway\nkeyPassword=%s\nstorePassword=%s\nstoreFile=%s\n\" \
    \$pw \$pw \$d/throwaway.jks > \$d/key.properties
  chmod 600 \$d/key.properties
fi
cp \$d/key.properties ~/wt/$sdir/android/key.properties
export PATH=\$HOME/.shorebird/bin/cache/flutter/${OC_VPS_FLUTTER_REV:-6e5e949520159b5ceb029598be37520cd433b046}/bin:\$PATH
cd ~/wt/$sdir
rm -rf build/app/outputs
flutter pub get >/dev/null 2>&1
# One release build at a time on the server: parallel Gradle builds from
# several worktrees exhausted its memory (it has no swap) and killed a daemon.
flock \$HOME/.oc-vps-apk-build.lock flutter build apk --release --build-number=$n ${OC_BUILD_ARGS:-} > ~/wt/$sdir/vps-build-$n.log 2>&1 || { tail -40 ~/wt/$sdir/vps-build-$n.log; exit 1; }
'" >/dev/null || { ssh -o BatchMode=yes "$host" "tail -40 ~/wt/$sdir/vps-build-$n.log" >&2 || true; echo "vps_build_apk: server build failed" >&2; exit 1; }
ssh -o BatchMode=yes "$host" "cat ~/wt/$sdir/vps-build-$n.log" > "$log" || true
server_secs=$((SECONDS - start))

raw="$out/app-release-server-$n.apk"
scp -q -o BatchMode=yes "$host:wt/$sdir/build/app/outputs/flutter-apk/app-release.apk" "$raw"
scp -q -o BatchMode=yes "$host:wt/$sdir/build/app/outputs/mapping/release/mapping.txt" "$out/mapping-$n.txt" || echo "vps_build_apk: no mapping.txt" >&2

final="$out/app-release-$n.apk"
"$bt/zipalign" -c -P 16 4 "$raw" || "$bt/zipalign" -c 4 "$raw"
getp() { grep -m1 "^$1=" "$kp" | cut -d= -f2-; }
ks="$(getp storeFile)"; alias_="$(getp keyAlias)"
OC_KS_PASS="$(getp storePassword)" OC_KEY_PASS="$(getp keyPassword)" \
  "$bt/apksigner" sign --v1-signing-enabled false --ks "$ks" --ks-key-alias "$alias_" \
  --ks-pass env:OC_KS_PASS --key-pass env:OC_KEY_PASS --out "$final" "$raw" >/dev/null
rm -f "$raw" "$final.idsig"
if ! "$root/tool/qa/apk_has_plugins.sh" "$final"; then
  echo "vps_build_apk: plugin registrant missing; removing $final" >&2
  rm -f "$final"; exit 1
fi

certs="$("$bt/apksigner" verify --print-certs "$final")"
digest="$(grep -m1 'certificate SHA-256 digest' <<<"$certs" | awk '{print toupper($NF)}')"
if [[ "$digest" != "$expected" ]]; then
  echo "vps_build_apk: signer digest mismatch ($digest); removing $final" >&2
  rm -f "$final"; exit 1
fi
echo "build number: $n"
echo "apk:          $final"
echo "size:         $(du -h "$final" | cut -f1)"
echo "signer:       $digest"
echo "elapsed:      $((SECONDS - start))s (server build+transfer ${server_secs}s)"
