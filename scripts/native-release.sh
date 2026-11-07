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
# Refuses unless: on master, the tree is clean (untracked files too), master equals <remote>/master, version.properties
# holds a plain X.Y.Z versionName. --go also needs the lineage key (android/key.properties or OC_KEY_PROPERTIES, the
# file the build reads) and release notes docs/releases/v<X.Y.Z+N>.md. Key values are never read or printed here.
# Environment: OC_RELEASE_REMOTE (default origin).
# Exit: 0 done (dry run or release), 1 refused or a gate failed, 2 could not run.
set -euo pipefail

readonly RELEASE_BRANCH="master"
readonly OWNER_LOCAL="1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
readonly PUBLIC_CI="842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C"
readonly REMOTE="${OC_RELEASE_REMOTE:-origin}"

usage() { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; }
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

key_file="${OC_KEY_PROPERTIES:-$ROOT/android/key.properties}"
key_ready=false
if [[ -f "$key_file" ]]; then
  key_ready=true
  for k in storeFile storePassword keyAlias keyPassword; do
    grep -q "^[[:space:]]*$k[[:space:]]*=[[:space:]]*[^[:space:]]" "$key_file" || key_ready=false
  done
fi
say "  signing     $($key_ready && echo "key file present ($key_file)" || echo "no complete key file: the APKs stay unsigned and the identity gate will refuse them")"

TAG="v$NAME+$NEXT"
NOTES="$ROOT/docs/releases/$TAG.md"
if $GO; then
  $key_ready || fail "--go needs the lineage key file (android/key.properties or OC_KEY_PROPERTIES)."
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
  say "  building    :androidApp:assembleSwitchRelease, lintSwitchRelease, unit tests (this takes a while)"
  (cd "$NATIVE" && ./gradlew --console=plain :androidApp:assembleSwitchRelease :androidApp:lintSwitchRelease \
    :androidApp:testSwitchReleaseUnitTest) >"$OUT/gradle.log" 2>&1 ||
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
  [ ] install the matching APK over the current build on one phone; it updates in place and keeps its projects
  [ ] publish the draft by hand; never mark it latest until the owner says GO
EOF
$GO || say "Dry run finished: re-run with --go to take $NAME+$NEXT, tag and push."
exit 0
