#!/usr/bin/env bash
# Self-tests for the release tools (E01-S01 identity gate, E01-S05 scripts/native-release.sh, E00-S16 owner preflight).
# Each case uses fake apksigner/aapt/adb and throwaway git repositories, so nothing touches this checkout, a phone, a
# key or a remote. Needs bash, git and python3; no Gradle, no network, no Android SDK.
#
#   native/tool/release/test/run_tests.sh [name filter]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
NATIVE="$(cd "$HERE/../../.." && pwd)"
ROOT="$(cd "$NATIVE/.." && pwd)"
filter="${1:-}"
pass=0 fail=0 failed=()
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/oc-release-test.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

OWNER="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
PUBLIC="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
LEGACY="2D010C2103CB2F78ABAACA690EAD4D45F8003A6C0A02082CD2A2AE62FD18D0EC"
DEBUG="$(printf 'AB%.0s' {1..32})"
PKG="io.github.eslamasabry.opencode_mobile"

# expect <name> <exit code> <command...>: runs the command, output in $OUT.
expect() {
  local name="$1" want="$2"; shift 2
  [[ -n "$filter" && "$name" != *"$filter"* ]] && return 0
  OUT="$("$@" 2>&1)"; local got=$?
  if [[ "$got" == "$want" ]]; then pass=$((pass + 1)); echo "PASS $name"
  else fail=$((fail + 1)); failed+=("$name"); echo "FAIL $name (exit $got, wanted $want)"; sed 's/^/    /' <<<"$OUT" | tail -n 15; fi
}
# check <name> <condition command...>: a plain assertion on state after a case.
check() {
  local name="$1"; shift
  [[ -n "$filter" && "$name" != *"$filter"* ]] && return 0
  OUT="${OUT:-}"
  if "$@"; then pass=$((pass + 1)); echo "PASS $name"
  else fail=$((fail + 1)); failed+=("$name"); echo "FAIL $name"; sed 's/^/    /' <<<"${OUT:-}" | tail -n 15; fi
}
has() { grep -qF -- "$1" <<<"$OUT"; }

# ---- fake tools -------------------------------------------------------------------------------------------------------
FAKE="$TMP_ROOT/bin"; mkdir -p "$FAKE"
# apksigner verify --print-certs <apk>: prints <apk>.certs (one fingerprint per line); <apk>.broken fails.
cat >"$FAKE/apksigner" <<'EOF'
#!/usr/bin/env bash
apk="${@: -1}"
[[ -f "$apk.broken" ]] && { echo "DOES NOT VERIFY" >&2; exit 1; }
n=0; while read -r fp; do [[ -n "$fp" ]] || continue; n=$((n + 1))
  lc="$(tr 'A-F' 'a-f' <<<"$fp")"
  if [[ -f "$apk.v2" ]]; then # apksigner's form for an APK without a v1 signature (minSdk 24+): one block per scheme
    echo "V2 Signer: certificate DN: CN=Test"; echo "V2 Signer: certificate SHA-256 digest: $lc"
    echo "V3 Signer: certificate SHA-256 digest: $lc"
  else echo "Signer #$n certificate SHA-256 digest: $lc"; fi
done <"$apk.certs"
EOF
# aapt2 dump badging <apk>: prints <apk>.badging.
cat >"$FAKE/aapt2" <<'EOF'
#!/usr/bin/env bash
cat "${@: -1}.badging"
EOF
chmod +x "$FAKE/apksigner" "$FAKE/aapt2"
export OC_APKSIGNER="$FAKE/apksigner" OC_AAPT="$FAKE/aapt2"

apk() { # apk <file> <package> <code> <name> <signer>...
  local f="$1" p="$2" c="$3" n="$4"; shift 4
  : >"$f"
  echo "package: name='$p' versionCode='$c' versionName='$n' platformBuildVersionName='16'" >"$f.badging"
  printf '%s\n' "$@" >"$f.certs"
}

GATE="$NATIVE/tool/release/identity_gate.sh"
A="$TMP_ROOT/apks"; mkdir -p "$A"

# ---- identity gate (E01-S01) ------------------------------------------------------------------------------------------
apk "$A/arm.apk" "$PKG" 10010 3.0.0 "$PUBLIC"
apk "$A/x86.apk" "$PKG" 10010 3.0.0 "$PUBLIC"
apk "$A/uni.apk" "$PKG" 10010 3.0.0 "$PUBLIC"
expect "E01-S01 gate passes three matching APKs" 0 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/arm.apk" "$A/x86.apk" "$A/uni.apk"
check "E01-S01 gate names each passing APK" has "ok      uni.apk"
expect "E01-S01 gate accepts a lower-case colon fingerprint" 0 "$GATE" --expect-signer "$(sed 's/../&:/g; s/:$//' <<<"$PUBLIC" | tr 'A-F' 'a-f')" --version-code 10010 --version-name 3.0.0 "$A/arm.apk"
expect "E01-S01 gate requires --expect-signer" 2 "$GATE" --version-code 10010 --version-name 3.0.0 "$A/arm.apk"
expect "E01-S01 gate refuses the Legacy CI lineage as the expectation" 1 "$GATE" --expect-signer "$LEGACY" --version-code 10010 --version-name 3.0.0 "$A/arm.apk"
expect "E01-S01 gate refuses an unknown (debug) expectation" 1 "$GATE" --expect-signer "$DEBUG" --version-code 10010 --version-name 3.0.0 "$A/arm.apk"
expect "E01-S01 gate refuses the other lineage" 1 "$GATE" --expect-signer "$OWNER" --version-code 10010 --version-name 3.0.0 "$A/arm.apk"
check "E01-S01 the refusal names the signer mismatch" has "is not the expected lineage"
apk "$A/v2only.apk" "$PKG" 10010 3.0.0 "$PUBLIC"; touch "$A/v2only.apk.v2"
expect "E01-S01 gate reads apksigner's V2/V3 signer lines (no v1 signature)" 0 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/v2only.apk"
apk "$A/v2dbg.apk" "$PKG" 10010 3.0.0 "$DEBUG"; touch "$A/v2dbg.apk.v2"
expect "E01-S01 gate refuses a V2-only debug signer" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/v2dbg.apk"
apk "$A/two.apk" "$PKG" 10010 3.0.0 "$PUBLIC" "$OWNER"
expect "E01-S01 gate refuses two signers" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/two.apk"
check "E01-S01 the refusal counts the signers" has "printed 2 signers"
apk "$A/dbg.apk" "$PKG" 10010 3.0.0 "$DEBUG"
expect "E01-S01 gate refuses a debug-signed APK" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/dbg.apk"
apk "$A/unsigned.apk" "$PKG" 10010 3.0.0 "$PUBLIC"; touch "$A/unsigned.apk.broken"
expect "E01-S01 gate refuses an APK apksigner cannot verify" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/unsigned.apk"
apk "$A/preview.apk" "$PKG.native" 10010 3.0.0 "$PUBLIC"
expect "E01-S01 gate refuses the preview package" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/preview.apk"
apk "$A/low.apk" "$PKG" 10004 3.0.0 "$PUBLIC"
expect "E01-S01 gate refuses a versionCode below 10010" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10004 --version-name 3.0.0 "$A/low.apk"
expect "E01-S01 gate refuses a versionCode other than expected" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10020 --version-name 3.0.0 "$A/arm.apk"
apk "$A/dev.apk" "$PKG" 10010 3.0.0-dev "$PUBLIC"
expect "E01-S01 gate refuses a suffixed versionName" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0-dev "$A/dev.apk"
apk "$A/odd.apk" "$PKG" 10011 3.0.0 "$PUBLIC"
expect "E01-S01 gate refuses splits with different versionCodes" 1 "$GATE" --expect-signer "$PUBLIC" --version-code 10010 --version-name 3.0.0 "$A/arm.apk" "$A/odd.apk"
check "E01-S01 the refusal names the versionCode split" has "different versionCodes"
expect "E01-S01 gate reads version.properties by default" 1 "$GATE" --expect-signer "$PUBLIC" "$A/dev.apk"

# ---- kept classes on the built APK (E01-S08) --------------------------------------------------------------------------
KEPT="$NATIVE/tool/release/kept_classes_check.sh"
cat >"$FAKE/dexdump" <<'EOF2'
#!/usr/bin/env bash
cat "$1"
EOF2
chmod +x "$FAKE/dexdump"
export OC_DEXDUMP="$FAKE/dexdump"
dex_apk() { # dex_apk <apk> <class to leave out or ""> : a zip whose classes.dex is a fake dexdump listing
  local out="$1" skip="$2"
  awk -F'\t' '!/^#/ && NF >= 2 { if ($1 == "component") { sub(/^[^:]*:/, "", $2); print $2 } else if ($1 == "widget" || $1 == "tile" || $1 == "worker") print $2 }' \
    "$NATIVE/quality/config/component-freeze/flutter-2.1.4.tsv" | grep -vxF "${skip:-<none>}" |
    sed "s#\\.#/#g; s#.*#  Class descriptor  : 'L&;'#" >"$TMP_ROOT/classes.dex"
  echo "  Class descriptor  : 'La/b;'" >>"$TMP_ROOT/classes.dex"
  python3 -I -c 'import sys, zipfile; z = zipfile.ZipFile(sys.argv[1], "w"); z.write(sys.argv[2], "classes.dex"); z.close()' "$out" "$TMP_ROOT/classes.dex"
}
dex_apk "$A/kept.apk" ""
expect "E01-S08 kept-classes passes when every frozen class is defined" 0 "$KEPT" "$A/kept.apk"
dex_apk "$A/renamed.apk" "io.github.eslamasabry.opencode_mobile.WidgetStatusWorker"
expect "E01-S08 kept-classes fails when R8 renamed a frozen worker" 1 "$KEPT" "$A/kept.apk" "$A/renamed.apk"
check "E01-S08 the failure names the class and the APK" has "MISSING renamed.apk: io.github.eslamasabry.opencode_mobile.WidgetStatusWorker"
expect "E01-S08 kept-classes cannot run without an APK" 2 "$KEPT"

# ---- owner preflight (E00-S16) ----------------------------------------------------------------------------------------
PRE="$NATIVE/tool/qa/owner_preflight.sh"
PHONE="$TMP_ROOT/phone"; mkdir -p "$PHONE"
# Fake adb: answers from $PHONE, logs every call; it can only read.
cat >"$FAKE/adb" <<'EOF'
#!/usr/bin/env bash
echo "$*" >>"$PHONE/calls.log"
[[ "${1:-}" == "-s" ]] && shift 2
case "$1" in
  devices) printf 'List of devices attached\n'; cat "$PHONE/devices" ;;
  get-state) echo device ;;
  pull) cp "$PHONE/base.apk" "$3"; cp "$PHONE/base.apk.certs" "$3.certs"; [[ -f "$PHONE/base.apk.v2" ]] && cp "$PHONE/base.apk.v2" "$3.v2"; [[ -f "$PHONE/base.apk.broken" ]] && cp "$PHONE/base.apk.broken" "$3.broken"; exit 0 ;;
  shell)
    shift
    case "$*" in
      "pm path io.github.eslamasabry.opencode_mobile") [[ -f "$PHONE/base.apk" ]] && printf 'package:/data/app/~~x/io.github.eslamasabry.opencode_mobile-1/base.apk\r\n' ;;
      "dumpsys package io.github.eslamasabry.opencode_mobile") printf '    versionCode=%s minSdk=26 targetSdk=36\n    versionName=%s\n' "$(cat "$PHONE/code")" "$(cat "$PHONE/name")" ;;
      "df -k /data") printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n/dev/block/dm-5 100000000 50000000 %s 50%% /data\n' "$(cat "$PHONE/free_kb")" ;;
      "dumpsys diskstats") printf 'Package Names: ["com.other","io.github.eslamasabry.opencode_mobile"]\nApp Sizes: [1,2]\nApp Data Sizes: [10,3221225472]\n' ;;
      *) echo "unexpected shell command: $*" >&2; exit 9 ;;
    esac ;;
  *) echo "unexpected adb command: $*" >&2; exit 9 ;;
esac
EOF
chmod +x "$FAKE/adb"
export OC_ADB="$FAKE/adb" PHONE
phone() { # phone <signer> <code> [free_kb] [devices]
  rm -f "$PHONE"/*
  : >"$PHONE/base.apk"; echo "$1" >"$PHONE/base.apk.certs"
  echo "$2" >"$PHONE/code"; echo "2.1.4" >"$PHONE/name"; echo "${3:-8000000}" >"$PHONE/free_kb"
  printf '%b' "${4:-R5CX123\tdevice\n}" >"$PHONE/devices"
}
phone "$OWNER" 4610
expect "E00-S16 preflight passes the owner phone" 0 "$PRE" --expect-signer "$OWNER"
check "E00-S16 preflight reports the installed version" has "installed version 2.1.4+4610"
check "E00-S16 preflight reports the Linux data size" has "app data (projects and the built-in Linux) 3072 MB"
check "E00-S16 preflight only sends read-only commands" bash -c "! grep -Eiv '^(-s [^ ]+ )?(devices|get-state|pull /data/app/[^ ]+ [^ ]+|shell (pm path|dumpsys package|dumpsys diskstats|df -k /data).*)$' '$PHONE/calls.log'"
check "E00-S16 preflight never takes a screenshot" bash -c "! grep -Eiq 'screencap|screenrecord|(^| )(install|uninstall|clear|am|rm)( |$)' '$PHONE/calls.log' && ! grep -Eq 'screencap|screenrecord' '$PRE'"
phone "$OWNER" 4610; touch "$PHONE/base.apk.v2"
expect "E00-S16 preflight reads a V2-only installed signer" 0 "$PRE" --expect-signer "$OWNER"
phone "$PUBLIC" 62
expect "E00-S16 preflight stops when the signer is another lineage" 1 "$PRE" --expect-signer "$OWNER"
check "E00-S16 preflight stops before reading the version" bash -c "! grep -q 'dumpsys package' '$PHONE/calls.log'"
check "E00-S16 the stop says not to install" has "Do not install anything"
phone "$OWNER" 4610; echo "$PUBLIC" >>"$PHONE/base.apk.certs"
expect "E00-S16 preflight stops on two installed signers" 1 "$PRE" --expect-signer "$OWNER"
phone "$OWNER" 4610 500000
expect "E00-S16 preflight stops when /data is nearly full" 1 "$PRE" --expect-signer "$OWNER"
phone "$OWNER" 4610 8000000 "A\tdevice\nB\tdevice\n"
expect "E00-S16 preflight stops with two devices" 1 "$PRE" --expect-signer "$OWNER"
expect "E00-S16 preflight takes --serial with two devices" 0 "$PRE" --expect-signer "$OWNER" --serial B
expect "E00-S16 preflight refuses a non-lineage expectation" 1 "$PRE" --expect-signer "$DEBUG"
phone "$OWNER" 4610
apk "$A/new-owner.apk" "$PKG" 10010 3.0.0 "$OWNER"
expect "E00-S16 preflight accepts a newer same-signer APK" 0 "$PRE" --expect-signer "$OWNER" --apk "$A/new-owner.apk"
phone "$OWNER" 20000
expect "E00-S16 preflight refuses an APK that is not newer" 1 "$PRE" --expect-signer "$OWNER" --apk "$A/new-owner.apk"
phone "$OWNER" 4610
expect "E00-S16 preflight refuses an APK of another lineage" 1 "$PRE" --expect-signer "$OWNER" --apk "$A/arm.apk"

# ---- scripts/native-release.sh (E01-S05) ------------------------------------------------------------------------------
# new_release_repo: a clone of a bare "origin" holding the release script, the allocator and a version.properties.
new_release_repo() {
  local d="$TMP_ROOT/rel$RANDOM$RANDOM" origin
  origin="$d-origin.git"
  git init -q --bare -b master "$origin"
  mkdir -p "$d/scripts" "$d/native/tool/release" "$d/docs/releases" && cd "$d" || exit 1
  git init -q -b master .
  git config user.name "Test Bot"; git config user.email "12345+bot@users.noreply.github.com"; git config commit.gpgsign false
  cp "$ROOT/scripts/native-release.sh" scripts/
  cp "$NATIVE/tool/version_code.sh" native/tool/; cp -r "$NATIVE/tool/lib" native/tool/lib
  cp "$NATIVE/tool/release/identity_gate.sh" native/tool/release/
  printf 'versionName=%s\nversionCode=10010\n' "${1:-3.0.0}" >native/version.properties
  printf '# Build numbers\n\n## Ledger\n\n| versionCode | kind | date | for |\n|---|---|---|---|\n| 10010 | release | 2026-11-07 | first |\n' >native/BUILD_NUMBERS.md
  git add -A && git commit -q -m "base [skip ci]"
  git remote add origin "$origin" && git push -q origin master 2>/dev/null
  echo "$d"
}
REL_TRAILER_CHECK() { [[ "$(git -C "$R" rev-list --count HEAD)" == 1 && -z "$(git -C "$R" tag)" && -z "$(git -C "$R" status --porcelain)" ]]; }

R="$(new_release_repo)"
expect "E01-S05 a dry run passes and prints the GO checklist" 0 bash "$R/scripts/native-release.sh" --no-build
check "E01-S05 the dry run says it is one" has "DRY RUN (nothing is committed, tagged, pushed or published)"
check "E01-S05 the dry run shows the +10 version" has "the release takes 3.0.0+10020"
check "E01-S05 the GO checklist is printed" has "GO checklist for v3.0.0+10020"
check "E01-S05 the dry run commits, tags and writes nothing" REL_TRAILER_CHECK
check "E01-S05 the dry run leaves origin without a tag" bash -c "[[ -z \"\$(git -C '$R' ls-remote --tags origin)\" ]]"

git -C "$R" checkout -q -b feature
expect "E01-S05 refuses off master" 1 bash "$R/scripts/native-release.sh" --no-build
check "E01-S05 the refusal names master" has "start on master only"
git -C "$R" checkout -q master
echo x >"$R/untracked.txt"
expect "E01-S05 refuses an untracked file" 1 bash "$R/scripts/native-release.sh" --no-build
rm -f "$R/untracked.txt"
git -C "$R" commit -q --allow-empty -m "ahead [skip ci]"
expect "E01-S05 refuses master ahead of origin" 1 bash "$R/scripts/native-release.sh" --no-build
check "E01-S05 the refusal says not synchronized" has "is not synchronized"
git -C "$R" reset -q --hard origin/master

expect "E01-S05 --go never skips the build" 1 bash "$R/scripts/native-release.sh" --go --no-build
expect "E01-S05 --go without a key file is refused" 1 env OC_KEY_PROPERTIES="$TMP_ROOT/none.properties" bash "$R/scripts/native-release.sh" --go
check "E01-S05 a refused --go commits, tags and writes nothing" REL_TRAILER_CHECK
KEYP="$TMP_ROOT/key.properties"
printf 'storeFile=/nowhere/k.jks\nstorePassword=x\nkeyAlias=x\nkeyPassword=x\n' >"$KEYP"
expect "E01-S05 --go without release notes is refused" 1 env OC_KEY_PROPERTIES="$KEYP" bash "$R/scripts/native-release.sh" --go
check "E01-S05 the refusal names the notes file" has "docs/releases/v3.0.0+10020.md"
check "E01-S05 a refused --go still writes nothing" REL_TRAILER_CHECK
expect "E01-S05 an unknown lineage cannot run" 2 bash "$R/scripts/native-release.sh" --lineage legacy --no-build

R="$(new_release_repo 3.0.0-dev)"
expect "E01-S05 refuses a suffixed versionName" 1 bash "$R/scripts/native-release.sh" --no-build

echo
echo "release tools: $pass passed, $fail failed"
((fail == 0)) || { printf '  failed: %s\n' "${failed[@]}"; exit 1; }
echo "PASS: release tools contract"
