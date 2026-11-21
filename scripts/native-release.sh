#!/usr/bin/env bash
# Native (Kotlin) fix-forward release (E01-S05). Runbook: docs/native/release.md.
#
#   scripts/native-release.sh [--lineage public|owner] [--no-build] [--go]
#   scripts/native-release.sh --lineage public --tag-only [--go]
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
#   * a passing Native CI verdict: the newest complete candidate lane run for HEAD's commit, including pictures,
#     quality/security and Android tests, read from the verdict file its summary job writes (native/tool/ci/release_verdict.py,
#     `gh` as the person running this), and
#   * a passing device smoke result for HEAD's commit and the current versionCode (native/tool/qa/release_smoke.sh,
#     run on one phone or emulator against the APK this script builds; file in native/build/release-smoke/).
# The gates are checked before the bump commit, so they describe the commit that is being released. --go also needs the lineage key (OC_KEY_PROPERTIES, native/key.properties or the old
# android/key.properties: the file the build reads) and release notes docs/releases/v<X.Y.Z+N>.md. Key values are never read or printed here.
# Version names (release rule 2026-11-17): version.properties holds the plain name of the NEXT public release, and every
# public release takes a name no earlier release used. The public lineage refuses a name that already has a tag
# v<name>+<build> (local or on the remote) and names that tag. The public lineage builds with -Poc.publicRelease=true, so
# the APKs carry <name>. The owner lineage is a test build: its APKs carry <name>-test.<build>, the dry run builds them
# for the owner's phone, and --go refuses (a test build takes a number with version_code.sh allocate --kind build, and
# is never tagged, because a tag would spend the public version name).
# --tag-only (public lineage only; key-free): the same preflight and the same two gates (CI verdict and device smoke for
# HEAD's commit), then with --go: the bump commit with its Release-Tag trailer, the tag, the push of master and the tag.
# NO build, NO identity gate and no key file here: the public files are built, signed and checked by the Native release
# workflow on the tag (native-release.yml), which makes the draft. It needs the release notes docs/releases/<tag>.md.
# Without --go it is a dry run of exactly that: it prints every command it would run and changes nothing.
# Environment: OC_RELEASE_REMOTE (default origin); OC_RELEASE_GH_REPO (owner/repo for the CI verdict, default from the
# remote's URL); OC_SMOKE_RESULT_DIR (default native/build/release-smoke).
# Exit: 0 done (dry run or release), 1 refused or a gate failed, 2 could not run.
set -euo pipefail

readonly RELEASE_BRANCH="master"
readonly OWNER_LOCAL="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
readonly PUBLIC_CI="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
readonly REMOTE="${OC_RELEASE_REMOTE:-origin}"

usage() { sed -n '2,42p' "$0" | sed 's/^# \{0,1\}//'; }
fail() { echo "native-release: REFUSED: $*" >&2; exit 1; }
cannot() { echo "native-release: cannot run: $*" >&2; exit 2; }
say() { printf '%s\n' "$*"; }

GO=false BUILD=true LINEAGE=public TAG_ONLY=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) usage; exit 0 ;;
    --go) GO=true; shift ;;
    --no-build) BUILD=false; shift ;;
    --tag-only) TAG_ONLY=true; shift ;;
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
if $TAG_ONLY; then
  # Key-free: the public files come from the Native release workflow on the tag, never from a build here.
  [[ "$LINEAGE" == public ]] || fail "--tag-only is for the public lineage only (an owner build is a test build and is never tagged)."
  BUILD=false
elif $GO && ! $BUILD; then fail "--go always builds and gates; drop --no-build"; fi

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

python3 -I "$NATIVE/tool/qa/check_tested_pins.py"
prop() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p" "$NATIVE/version.properties" | tail -n 1; }
NAME="$(prop versionName)"
CURRENT="$(prop versionCode)"
[[ "$NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "versionName '$NAME' is not a plain X.Y.Z (a release has no suffix)."
if [[ "$LINEAGE" == public ]]; then
  published="$({
    git for-each-ref --format='%(refname:short)' "refs/tags/v$NAME+*"
    git ls-remote --tags "$REMOTE" "refs/tags/v$NAME+*" 2>/dev/null | sed -n 's#^.*refs/tags/##p; s#\^{}$##'
  } | { grep -v -e '-test' || true; } | sort -u | head -n 1)"
  [[ -z "$published" ]] ||
    fail "version $NAME is already published as $published; every public release needs a name no earlier release used. Set versionName in native/version.properties to the next one (3.0.2 for a fix, 3.1.0 for a bigger change)."
fi
if $GO && [[ "$LINEAGE" == owner ]]; then
  fail "the owner lineage builds test builds ($NAME-test.<build>); a test build is never tagged. Take a number with native/tool/version_code.sh allocate --kind build, or release with --lineage public."
fi
NEXT="$("$NATIVE/tool/version_code.sh" next --kind release)" || cannot "the versionCode allocator did not answer"
[[ "$NEXT" =~ ^[0-9]+$ ]] && ((NEXT > CURRENT)) || cannot "the allocator gave '$NEXT', not above $CURRENT"

say "native-release: $($GO && echo "GO" || echo "DRY RUN (nothing is committed, tagged, pushed or published)")$($TAG_ONLY && echo ", TAG ONLY (no build, no identity gate, no key)")"
say "  branch      $branch at $(git rev-parse --short HEAD), synchronized with $REMOTE"
say "  version     $NAME+$CURRENT now; the release takes $NAME+$NEXT"
if [[ "$LINEAGE" == public ]]; then
  say "  name        $NAME (public release: the APKs carry the plain name)"
else
  say "  name        $NAME-test.<build> (owner lineage = test build: the APKs never carry the public name)"
fi
say "  lineage     $LINEAGE (${EXPECT_SIGNER:0:8}…)"

# The same order as the build (native/build-logic ReleaseKey): OC_KEY_PROPERTIES, native/key.properties, then the old
# android/key.properties with a notice. Not looked at with --tag-only: that mode signs nothing.
key_ready=false
if $TAG_ONLY; then
  say "  signing     not used (--tag-only: the Native release workflow builds and signs the public files on the tag)"
else
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
fi

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
    say "              run Native CI (candidate lane, all required tests included) on ${HEAD_SHA:0:10}, then try again"
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
NOTES_HEAD="# OpenCode Mobile $NAME+$NEXT"
notes_ok() { [[ -s "$NOTES" && "$(head -n 1 "$NOTES")" == "$NOTES_HEAD" ]]; }
tag_taken() {
  git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null && return 0
  git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$TAG" >/dev/null 2>&1
}
if $TAG_ONLY && ! $GO; then
  # The dry form of --tag-only: the same checks as --go, then the exact commands, and nothing changes.
  notes_ok || { say "  notes       REFUSED: --go needs $NOTES starting with '$NOTES_HEAD' (commit them first)"; gate_fail=true; }
  if tag_taken; then say "  tag         REFUSED: $TAG already exists (here or on $REMOTE)"; gate_fail=true; fi
  say "  would run   native/tool/version_code.sh allocate --kind release --for \"native release $NAME+$NEXT ($LINEAGE lineage)\""
  say "  would run   git add native/version.properties native/BUILD_NUMBERS.md"
  say "  would run   git commit -m \"build(release): native $NAME+$NEXT\" -m \"Takes versionCode $NEXT for the $LINEAGE-lineage fix-forward release (scripts/native-release.sh --go).\" -m \"Release-Tag: $TAG\""
  say "  would run   git tag -a $TAG -m \"OpenCode Mobile $NAME+$NEXT (native, $LINEAGE lineage)\""
  say "  would run   git push $REMOTE $RELEASE_BRANCH && git push $REMOTE refs/tags/$TAG"
  say "  then        gh workflow run native-release.yml --ref $RELEASE_BRANCH -f tag=$TAG (the public-key DRAFT; a person publishes it)"
  $gate_fail && fail "the tag-only dry run found a refusal above; nothing was committed, tagged or pushed."
  say "Dry run finished (tag only): re-run with --go to take $NAME+$NEXT, commit, tag and push. Nothing was changed."
  exit 0
fi
if $GO; then
  $TAG_ONLY || $key_ready || fail "--go needs the lineage key file (OC_KEY_PROPERTIES, native/key.properties or the old android/key.properties)."
  notes_ok || fail "--go needs release notes $NOTES starting with '$NOTES_HEAD' (commit them first)."
  tag_taken && fail "tag $TAG already exists (here or on $REMOTE)."

  # 1. Take the number and commit the bump (local only until every gate passes).
  # Only the release bump omits the marker: its master push matches no automatic workflow.
  "$NATIVE/tool/version_code.sh" allocate --kind release --for "native release $NAME+$NEXT ($LINEAGE lineage)" >/dev/null ||
    fail "the allocator refused $NEXT"
  [[ "$(prop versionCode)" == "$NEXT" ]] || cannot "version.properties does not hold $NEXT after allocation"
  git add native/version.properties native/BUILD_NUMBERS.md
  git commit --quiet -m "build(release): native $NAME+$NEXT" \
    -m "Takes versionCode $NEXT for the $LINEAGE-lineage fix-forward release (scripts/native-release.sh --go)." \
    -m "Release-Tag: $TAG"
  say "  committed   the version bump ($(git rev-parse --short HEAD)), not pushed"
  [[ "$(git log -1 --format=%B | sed -n 's/^Release-Tag: //p')" == "$TAG" ]] ||
    fail "the bump commit does not carry Release-Tag: $TAG (it is local: inspect it before pushing anything)."
else
  [[ -s "$NOTES" ]] || say "  notes       missing $NOTES (needed for --go)"
fi

# ---- build and gates --------------------------------------------------------------------------------------------------
OUT="$NATIVE/build/native-release/$TAG"
if $BUILD; then
  built_code="$($GO && echo "$NEXT" || echo "$CURRENT")"
  # The public lineage builds the public name; any other build is <name>-test.<build> (build-logic ReleaseVersionName).
  gate_kind=--test-build public_flag=false built_name="$NAME-test.$built_code"
  if [[ "$LINEAGE" == public ]]; then gate_kind=--plain-release public_flag=true built_name="$NAME"; fi
  rm -rf "$OUT" && mkdir -p "$OUT"
  # Every android/* library that has unit tests (the same list as the android-unit job of native-ci.yml).
  android_tests=()
  for d in "$NATIVE"/android/*/; do
    if [[ -d "${d}src/test" ]]; then android_tests+=(":android:$(basename "$d"):test"); fi
  done
  say "  building    :androidApp:assembleSwitchRelease, lintSwitchRelease, app and ${#android_tests[@]} Android library unit test tasks (this takes a while)"
  (cd "$NATIVE" && ./gradlew --console=plain "-Poc.publicRelease=$public_flag" :androidApp:assembleSwitchRelease :androidApp:lintSwitchRelease \
    :androidApp:testSwitchDebugUnitTest "${android_tests[@]}") >"$OUT/gradle.log" 2>&1 ||
    fail "the build or a gate failed; see $OUT/gradle.log$($GO && echo " (the bump commit is local: push it to keep the number spent, never reuse it)")"
  say "  gate        :quality:test and :quality:secTest with the merged switch-release manifest (component freeze)"
  (cd "$NATIVE" && ./gradlew --console=plain "-Poc.publicRelease=$public_flag" :quality:test :quality:secTest -Poc.mergedManifest=required) >>"$OUT/gradle.log" 2>&1 ||
    fail "quality/security gates failed; see $OUT/gradle.log"
  apk_dir="$NATIVE/androidApp/build/outputs/apk/switch/release"
  for abi in arm64-v8a x86_64 universal; do
    src="$(find "$apk_dir" -maxdepth 1 -name "*-$abi-release.apk" | head -n 1)"
    [[ -n "$src" ]] || fail "no $abi APK in $apk_dir"
    cp "$src" "$OUT/opencode-mobile-$built_name-$built_code-$abi.apk"
  done
  (cd "$OUT" && sha256sum ./*.apk | sed 's# \./# #' >SHA256SUMS)
  say "  gate        frozen classes survive R8 in each APK (kept_classes_check.sh)"
  "$NATIVE/tool/release/kept_classes_check.sh" "$OUT"/*.apk || fail "a frozen class is missing from the minified APK."
  say "  gate        identity gate (package, $built_name+$built_code, one signer = ${EXPECT_SIGNER:0:8}…)"
  "$NATIVE/tool/release/identity_gate.sh" --expect-signer "$EXPECT_SIGNER" --version-code "$built_code" \
    --version-name "$built_name" "$gate_kind" "$OUT"/*.apk || fail "the identity gate refused the APKs (nothing was tagged or pushed)."
fi

# ---- tag and push (--go only, after every gate passed) ----------------------------------------------------------------
if $GO; then
  git tag -a "$TAG" -m "OpenCode Mobile $NAME+$NEXT (native, $LINEAGE lineage)"
  git push "$REMOTE" "$RELEASE_BRANCH" && git push "$REMOTE" "refs/tags/$TAG" ||
    fail "pushing $RELEASE_BRANCH or $TAG failed; retry the push by hand (the tag and commit are local)."
  say "  pushed      $RELEASE_BRANCH and $TAG"
fi

if $TAG_ONLY; then
  cat <<EOF

Tag only: $TAG is pushed. Next, every line a person's check, in order:
  [ ] gh workflow run native-release.yml --ref $RELEASE_BRANCH -f tag=$TAG (it builds, signs and gates the public files; a DRAFT)
  [ ] the draft: sha256sum -c SHA256SUMS, each APK signer ${PUBLIC_CI:0:8}…, versionCode $NEXT
  [ ] the in-place update proof over the previous public release, then publish by hand only on the owner's GO
EOF
  exit 0
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
