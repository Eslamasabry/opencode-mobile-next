#!/usr/bin/env bash
# Native (Kotlin) fix-forward release (E01-S05). Runbook: docs/native/release.md.
#
#   scripts/native-release.sh [--lineage public|owner] [--no-build] [--go]
#
# Without --go (the default) this is a DRY RUN: it checks that a release may start, shows the version it would take,
# builds and gates the current tree (unless --no-build) and prints the GO checklist. It writes nothing tracked, makes no
# commit and never tags, pushes or publishes.
#
# With --go, and only then:
#   1. takes the next release versionCode (+10 steps, native/tool/version_code.sh allocate --kind release) and commits
#      the bump on master;
#   2. builds the switch release (arm64-v8a, x86_64, universal), runs the gates and the identity gate against the
#      chosen lineage, writes SHA256SUMS;
#   3. only when all of that passed: tags v<versionName>+<versionCode> and pushes master and the tag.
# The GitHub release itself is made by the native-release workflow as a DRAFT (make_latest=false); a person
# publishes it by hand after the GO checklist.
#
# Refuses unless (dry run and --go alike): on master, the tree is clean (untracked files too), master equals
# <remote>/master, version.properties holds a plain X.Y.Z versionName, AND the commit has both
#   * a passing Native CI verdict: the newest completed fast or full lane run for HEAD's commit that included the
#     Android library unit tests, read from the verdict file its summary job writes (native/tool/ci/release_verdict.py,
#     `gh` as the person running this), and
#   * a passing device smoke result for HEAD's commit and the current versionCode (native/tool/qa/release_smoke.sh,
#     run on one phone or emulator against the APK this script builds; file in native/build/release-smoke/).
# The gates are checked before the bump commit, so they describe the commit that is being released. --go also needs the lineage key (OC_KEY_PROPERTIES, native/key.properties or the old
# android/key.properties: the file the build reads) and release notes docs/releases/v<X.Y.Z+N>.md. Key values are never read or printed here.
# Environment: OC_RELEASE_REMOTE (default origin); OC_RELEASE_GH_REPO (owner/repo for the CI verdict, default from the
# remote's URL); OC_SMOKE_RESULT_DIR (default native/build/release-smoke).
# Exit: 0 done (dry run or release), 1 refused or a gate failed, 2 could not run.
set -euo pipefail

readonly RELEASE_BRANCH="master"
readonly OWNER_LOCAL="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
readonly PUBLIC_CI="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
readonly REMOTE="${OC_RELEASE_REMOTE:-origin}"

usage() { sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'; }
fail() { echo "native-release: REFUSED: $*" >&2; exit 1; }
cannot() { echo "native-release: cannot run: $*" >&2; exit 2; }
say() { printf '%s\n' "$*"; }

GO=false BUILD=true LINEAGE=public
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --go) GO=true; shift ;;
    --no-build) BUILD=false; shift ;;
    --lineage)
      [[ $# -ge 2 ]] || cannot "--lineage needs public or owner"
      LINEAGE="$2"; shift 2 ;;
    *) cannot "unknown argument $1 (see --help)" ;;
  esac
done
case "$LINEAGE" in
  public) EXPECT_SIGNER="$PUBLIC_CI" ;;
  owner) EXPECT_SIGNER="$OWNER_LOCAL" ;;
  *) cannot "--lineage must be public or owner" ;;
esac
if $GO && ! $BUILD; then fail "--go always builds and gates; drop --no-build"; fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NATIVE="$ROOT/native"
cd "$ROOT"
command -v git >/dev/null || cannot "git is required"
[[ -f "$NATIVE/version.properties" && -x "$NATIVE/tool/version_code.sh" ]] || cannot "not the repository root ($ROOT)"

# ---- preflight (both modes) -------------------------------------------------------------------------------------------
branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || fail "Detached HEAD is not releasable."
[[ "$branch" == "$RELEASE_BRANCH" ]] || fail "native releases start on $RELEASE_BRANCH only (this is $branch)."
[[ -z "$(git status --porcelain=v1 --untracked-files=all)" ]] || fail "the working tree is not clean (commit or remove every change, untracked files too)."
git fetch --quiet "$REMOTE" "$RELEASE_BRANCH" || cannot "cannot fetch $REMOTE/$RELEASE_BRANCH"
[[ "$(git rev-parse HEAD)" == "$(git rev-parse FETCH_HEAD)" ]] ||
  fail "$RELEASE_BRANCH is not synchronized with $REMOTE/$RELEASE_BRANCH (pull or push first)."

prop() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p" "$NATIVE/version.properties" | tail -n 1; }
NAME="$(prop versionName)"
CURRENT="$(prop versionCode)"
[[ "$NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "versionName '$NAME' is not a plain X.Y.Z (a release has no suffix)."
NEXT="$("$NATIVE/tool/version_code.sh" next --kind release)" || cannot "the versionCode allocator did not answer"
[[ "$NEXT" =~ ^[0-9]+$ ]] && ((NEXT > CURRENT)) || cannot "the allocator gave '$NEXT', not above $CURRENT"

say "native-release: $($GO && echo "GO" || echo "DRY RUN (nothing is committed, tagged, pushed or published)")"
say "  branch      $branch at $(git rev-parse --short HEAD), synchronized with $REMOTE"
say "  version     $NAME+$CURRENT now; the release takes $NAME+$NEXT"
say "  lineage     $LINEAGE (${EXPECT_SIGNER:0:8}…)"

# The same order as the build (native/build-logic ReleaseKey): OC_KEY_PROPERTIES, native/key.properties, then the old
# android/key.properties with a notice.
named_key="${OC_KEY_PROPERTIES:-}"
if [[ -n "${named_key// /}" ]]; then key_file="$named_key"
elif [[ -f "$NATIVE/key.properties" ]]; then key_file="$NATIVE/key.properties"
elif [[ -f "$ROOT/android/key.properties" ]]; then
  key_file="$ROOT/android/key.properties"
  say "  notice      using the old key file android/key.properties; move it to native/key.properties"
else key_file="$NATIVE/key.properties"; fi
key_ready=false
if [[ -f "$key_file" ]]; then
  key_ready=true
  for k in storeFile storePassword keyAlias keyPassword; do
    grep -q "^[[:space:]]*$k[[:space:]]*=[[:space:]]*[^[:space:]]" "$key_file" || key_ready=false
  done
fi
say "  signing     $($key_ready && echo "key file present ($key_file)" || echo "no complete key file: the APKs stay unsigned and the identity gate will refuse them")"

# ---- the two gates that prove tests ran: CI verdict and device smoke (both modes; read before anything is committed) -----
HEAD_SHA="$(git rev-parse HEAD)"
gate_fail=false
gh_repo="${OC_RELEASE_GH_REPO:-}"
if [[ -z "$gh_repo" ]]; then
  gh_repo="$(git remote get-url "$REMOTE" 2>/dev/null | sed -n 's#^.*github\.com[:/]\(.*\)$#\1#p' | sed 's#\.git$##')"
fi
if command -v python3 >/dev/null && command -v "${OC_GH:-gh}" >/dev/null; then
  if verdict="$(python3 -I "$NATIVE/tool/ci/release_verdict.py" "$HEAD_SHA" "$gh_repo" 2>&1)"; then
    say "  ci verdict  ${verdict#release verdict: }"
  else
    say "  ci verdict  REFUSED: ${verdict#release verdict: }"
    say "              run Native CI (fast or full lane, Android unit tests ticked) on ${HEAD_SHA:0:10}, then try again"
    gate_fail=true
  fi
else
  say "  ci verdict  REFUSED: python3 and gh are needed to read the CI verdict"
  gate_fail=true
fi
SMOKE_DIR="${OC_SMOKE_RESULT_DIR:-$NATIVE/build/release-smoke}"
SMOKE_FILE="$SMOKE_DIR/smoke-$HEAD_SHA.json"
smoke_reason=""
if [[ ! -f "$SMOKE_FILE" ]]; then
  smoke_reason="no result file for ${HEAD_SHA:0:10}"
else
  smoke_reason="$(python3 -I - "$SMOKE_FILE" "$HEAD_SHA" "$CURRENT" <<'PY'
import json, sys
path, sha, code = sys.argv[1:4]
try:
    r = json.load(open(path))
except (OSError, ValueError):
    print('the result file cannot be read'); sys.exit(0)
bad = [k for k in ('a', 'b', 'c', 'd') if not (r.get('checks', {}).get(k) or {}).get('pass') is True]
if r.get('commit') != sha: print('the result file is for another commit')
elif str(r.get('version_code')) != code: print(f"the result file tested versionCode {r.get('version_code')}, this tree has {code}")
elif not r.get('device_model'): print('the result file names no device')
elif bad: print('failed or missing checks: ' + ', '.join(bad))
elif r.get('pass') is not True: print('the result file does not say pass')
PY
)"
fi
if [[ -z "$smoke_reason" ]]; then
  say "  smoke       PASS ($(python3 -I -c "import json,sys; r=json.load(open(sys.argv[1])); print(r['device_model'], r['time_utc'])" "$SMOKE_FILE"))"
else
  say "  smoke       REFUSED: $smoke_reason"
  say "              build the APK (dry run does it), then on one device or emulator:"
  say "              OC_SMOKE_SERIAL=<adb serial> native/tool/qa/release_smoke.sh --new-apk <switch arm64-v8a APK> [--previous-apk <last release APK>]"
  gate_fail=true
fi
GATE_MSG="a release needs a passing CI verdict and a passing device smoke for the commit (see above); nothing was committed, tagged or pushed."
# --go stops here. A dry run goes on to build, so the APK exists to run the smoke on, and refuses at its end.
if $GO && $gate_fail; then fail "$GATE_MSG"; fi

TAG="v$NAME+$NEXT"
NOTES="$ROOT/docs/releases/$TAG.md"
if $GO; then
  $key_ready || fail "--go needs the lineage key file (OC_KEY_PROPERTIES, native/key.properties or the old android/key.properties)."
  [[ -s "$NOTES" && "$(head -n 1 "$NOTES")" == "# OpenCode Mobile $NAME+$NEXT" ]] ||
    fail "--go needs release notes $NOTES starting with '# OpenCode Mobile $NAME+$NEXT' (commit them first)."
  git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null && fail "tag $TAG already exists."
  git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$TAG" >/dev/null 2>&1 && fail "tag $TAG already exists on $REMOTE."

  # 1. Take the number and commit the bump (local only until every gate passes).
  "$NATIVE/tool/version_code.sh" allocate --kind release --for "native release $NAME+$NEXT ($LINEAGE lineage)" >/dev/null ||
    fail "the allocator refused $NEXT"
  [[ "$(prop versionCode)" == "$NEXT" ]] || cannot "version.properties does not hold $NEXT after allocation"
  git add native/version.properties native/BUILD_NUMBERS.md
  git commit --quiet -m "build(release): native $NAME+$NEXT [skip ci]" \
    -m "Takes versionCode $NEXT for the $LINEAGE-lineage fix-forward release (scripts/native-release.sh --go)." \
    -m "Release-Tag: $TAG"
  say "  committed   the version bump ($(git rev-parse --short HEAD)), not pushed"
else
  [[ -s "$NOTES" ]] || say "  notes       missing $NOTES (needed for --go)"
fi

# ---- build and gates --------------------------------------------------------------------------------------------------
OUT="$NATIVE/build/native-release/$TAG"
if $BUILD; then
  built_code="$($GO && echo "$NEXT" || echo "$CURRENT")"
  rm -rf "$OUT" && mkdir -p "$OUT"
  # Every android/* library that has unit tests (the same list as the android-unit job of native-ci.yml).
  android_tests=()
  for d in "$NATIVE"/android/*/; do
    if [[ -d "${d}src/test" ]]; then android_tests+=(":android:$(basename "$d"):test"); fi
  done
  say "  building    :androidApp:assembleSwitchRelease, lintSwitchRelease, app and ${#android_tests[@]} Android library unit test tasks (this takes a while)"
  (cd "$NATIVE" && ./gradlew --console=plain :androidApp:assembleSwitchRelease :androidApp:lintSwitchRelease \
    :androidApp:testSwitchDebugUnitTest "${android_tests[@]}") >"$OUT/gradle.log" 2>&1 ||
    fail "the build or a gate failed; see $OUT/gradle.log$($GO && echo " (the bump commit is local: push it to keep the number spent, never reuse it)")"
  say "  gate        :quality:test with the merged switch-release manifest (component freeze)"
  (cd "$NATIVE" && ./gradlew --console=plain :quality:test -Poc.mergedManifest=required) >>"$OUT/gradle.log" 2>&1 ||
    fail ":quality:test failed; see $OUT/gradle.log"
  apk_dir="$NATIVE/androidApp/build/outputs/apk/switch/release"
  for abi in arm64-v8a x86_64 universal; do
    src="$(find "$apk_dir" -maxdepth 1 -name "*-$abi-release.apk" | head -n 1)"
    [[ -n "$src" ]] || fail "no $abi APK in $apk_dir"
    cp "$src" "$OUT/opencode-mobile-$NAME-$built_code-$abi.apk"
  done
  (cd "$OUT" && sha256sum ./*.apk | sed 's# \./# #' >SHA256SUMS)
  say "  gate        frozen classes survive R8 in each APK (kept_classes_check.sh)"
  "$NATIVE/tool/release/kept_classes_check.sh" "$OUT"/*.apk || fail "a frozen class is missing from the minified APK."
  say "  gate        identity gate (package, $NAME+$built_code, one signer = ${EXPECT_SIGNER:0:8}…)"
  "$NATIVE/tool/release/identity_gate.sh" --expect-signer "$EXPECT_SIGNER" --version-code "$built_code" \
    --version-name "$NAME" "$OUT"/*.apk || fail "the identity gate refused the APKs (nothing was tagged or pushed)."
fi

# ---- tag and push (--go only, after every gate passed) ----------------------------------------------------------------
if $GO; then
  git tag -a "$TAG" -m "OpenCode Mobile $NAME+$NEXT (native, $LINEAGE lineage)"
  git push "$REMOTE" "$RELEASE_BRANCH" && git push "$REMOTE" "refs/tags/$TAG" ||
    fail "pushing $RELEASE_BRANCH or $TAG failed; retry the push by hand (the tag and commit are local)."
  say "  pushed      $RELEASE_BRANCH and $TAG"
fi

cat <<EOF

GO checklist for $TAG ($LINEAGE lineage) — every line is a person's check, in order:
  [ ] identity gate PASS for the three APKs above (package io.github.eslamasabry.opencode_mobile, $NAME, one signer)
  [ ] SHA256SUMS in ${OUT#"$ROOT"/} matches the three APKs
  [ ] owner phone: native/tool/qa/owner_preflight.sh --expect-signer <installed lineage> PASS (docs/native/switch-day.md)
  [ ] run the native-release workflow on $TAG (Actions › Native release › Run workflow); it makes a DRAFT release
  [ ] the draft holds the three APKs and SHA256SUMS, identical to the ones built here (compare SHA256SUMS)
  [ ] device smoke PASS for this commit (checked above; native/tool/qa/release_smoke.sh on the APK built here)
  [ ] publish the draft by hand; never mark it latest until the owner says GO
EOF
if $gate_fail; then fail "dry run built and gated the tree, but $GATE_MSG"; fi
$GO || say "Dry run finished: re-run with --go to take $NAME+$NEXT, tag and push."
exit 0
