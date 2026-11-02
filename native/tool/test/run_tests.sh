#!/usr/bin/env bash
# Self-tests for native/tool (S0-GATES): every gate is run against a planted violation (it must fail, P9 style) and
# against a clean case (it must pass). Each case builds a throwaway git repository with a copy of native/tool, so
# nothing here touches this checkout's history or any remote. Needs bash, git and python3; no Gradle, no network.
#
#   native/tool/test/run_tests.sh [name filter]
#
# Secret-shaped values, private addresses and e-mail addresses are assembled at run time, so this file itself passes
# scan_push.sh.
set -uo pipefail
TOOL_SRC="$(cd "$(dirname "$0")/.." && pwd)"
filter="${1:-}"
pass=0 fail=0 failed=()
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/oc-tool-test.XXXXXX")"
trap 'rm -rf "$TMP_ROOT"' EXIT

# Private-looking values, built so no literal appears in this file.
P192="$((191 + 1)).$((84 * 2)).1.20"
P10="$((5 * 2)).4.5.6"
P172="$((86 * 2)).20.0.9"
P100="$((50 * 2)).101.2.3"
PLINK="$((169)).$((127 * 2)).3.4"
CIDR="$((5 * 2)).0.0.0/8"
EMU="$((5 * 2)).0.2.2"
ULA6="fd$(printf %x 122):115c:a1e0::1"
TSHOST="phone.tail1234.ts""$(printf '.%s' net)"
PUBLIC="8.8.4.4"
GMAIL="someone.private@$(printf 'gm%s' ail).com"
GHTOKEN="gh""p_$(printf 'a%.0s' {1..36})"
PEMKEY="-----BEGIN ""RSA PRIVATE KEY-----"
NOREPLY_ID="Test Bot <12345+bot@users.noreply.github.com>"

# new_repo: a repo with native/tool copied in and a base branch "native/main" with one commit.
new_repo() {
  local d="$TMP_ROOT/r$RANDOM$RANDOM"
  mkdir -p "$d/native" && cd "$d" || exit 1
  git init -q -b native/main .
  git config user.name "Test Bot"; git config user.email "12345+bot@users.noreply.github.com"
  git config commit.gpgsign false
  cp -r "$TOOL_SRC" native/tool
  rm -rf native/tool/test
  mkdir -p native/core/common native/ui/kit/src/androidHostTest android/app
  printf 'plugins { id("oc.kmp.library") }\n' >native/core/common/build.gradle.kts
  printf 'plugins {\n    id("oc.kmp.compose")\n    alias(libs.plugins.roborazzi)\n}\n' >native/ui/kit/build.gradle.kts
  echo flutter >android/app/Main.kt
  git add -A && git commit -q -m "chore(tool): base [skip ci]" -m "Base." -m "Co-Authored-By: $NOREPLY_ID"
  git checkout -q -b work
  echo "$d"
}

commit() { # commit <subject> <body> [trailers...]
  local args=(-m "$1") t
  [[ -n "$2" ]] && args+=(-m "$2")
  shift 2
  local tr=""
  for t in "$@"; do tr+="$t"$'\n'; done
  [[ -n "$tr" ]] && args+=(-m "${tr%$'\n'}")
  git add -A && git commit -q --allow-empty "${args[@]}"
}
GOOD_TRAILERS=("Co-Authored-By: $NOREPLY_ID" "Claude-Session: https://claude.ai/code/session_x")

# expect <name> <exit code> <cmd...>  (output saved; shown on failure)
expect() {
  local name="$1" want="$2"; shift 2
  [[ -n "$filter" && "$name" != *"$filter"* ]] && return 0
  local out rc
  out="$("$@" 2>&1)"; rc=$?
  if [[ "$rc" == "$want" ]]; then pass=$((pass + 1)); echo "ok   $name"
  else fail=$((fail + 1)); failed+=("$name"); echo "FAIL $name (exit $rc, want $want)"; sed 's/^/     | /' <<<"$out" | tail -15; fi
  LAST_OUT="$out"
}
expect_out() { # expect_out <name> <grep -E pattern>  (on the last expect's output)
  local name="$1"
  [[ -n "$filter" && "$name" != *"$filter"* ]] && return 0
  if grep -qE -- "$2" <<<"$LAST_OUT"; then pass=$((pass + 1)); echo "ok   $name"
  else fail=$((fail + 1)); failed+=("$name"); echo "FAIL $name (output lacks /$2/)"; sed 's/^/     | /' <<<"$LAST_OUT" | tail -10; fi
}
expect_no_out() {
  local name="$1"
  [[ -n "$filter" && "$name" != *"$filter"* ]] && return 0
  if grep -qF -- "$2" <<<"$LAST_OUT"; then fail=$((fail + 1)); failed+=("$name"); echo "FAIL $name (output shows a value it must mask)"
  else pass=$((pass + 1)); echo "ok   $name"; fi
}

# ------------------------------------------------------------------------------------------------ check_write_set.sh
R="$(new_repo)"; cd "$R" || exit 1
mkdir -p native/tool/x && echo a >native/tool/x/a.sh && commit "build(tool): a [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "write-set: a change inside passes" 0 native/tool/check_write_set.sh --globs 'native/tool/**'
echo b >native/core/common/B.kt
expect "write-set: an untracked file outside fails" 1 native/tool/check_write_set.sh --globs 'native/tool/**'
expect_out "write-set: names the outside path" '^OUTSIDE: native/core/common/B.kt$'
expect "write-set: --also widens the set" 0 native/tool/check_write_set.sh --globs 'native/tool/**' --also 'native/core/**/*.kt'
rm native/core/common/B.kt
echo f >>android/app/Main.kt
expect "write-set: the Flutter android/ tree is not native/android/**" 1 native/tool/check_write_set.sh --globs 'native/android/**, native/tool/**'
git checkout -q android/app/Main.kt
cat >brief.md <<'EOF'
# Brief: native/s0-gates
Write set: `native/tool/**`, `native/{core,ui}/*/build.gradle.kts` (scripts only)   Single-owner modules you may NOT edit: :ui:kit
EOF
echo z >>native/ui/kit/build.gradle.kts
expect "write-set: brief parsing with backticks, braces and notes" 0 native/tool/check_write_set.sh brief.md --also brief.md
git rm -q --cached native/tool/x/a.sh >/dev/null; rm -f native/tool/x/a.sh
expect "write-set: a deletion outside the set fails" 1 native/tool/check_write_set.sh --globs 'native/ui/**' --also brief.md
expect "write-set: unknown base cannot check (2)" 2 native/tool/check_write_set.sh --globs 'x/**' --base no/such/ref
expect "write-set: a brief without a write set cannot check (2)" 2 native/tool/check_write_set.sh README.missing

# -------------------------------------------------------------------------------------------------- check_commits.sh
R="$(new_repo)"; cd "$R" || exit 1
commit "feat(engine-delivery): a marker stops a second send [skip ci]" "After a crash the queue sent the prompt again." \
  "Behaviors: B-STATE-018, B-STATE-019" "Change-Class: fix" "${GOOD_TRAILERS[@]}"
expect "commits: a well-formed commit passes" 0 native/tool/check_commits.sh native/main
for case in \
  "missing-skip|feat(core-common): no marker|Body.|ok|missing .\\[skip ci\\]" \
  "bad-type|feature(core-common): wrong type [skip ci]|Body.|ok|subject must be type" \
  "scope-colon|feat(:core:common): raw module path [skip ci]|Body.|ok|subject must be type" \
  "no-body|fix(core-common): nothing said [skip ci]||ok|no body" \
  "long|fix(core-common): $(printf 'word %.0s' {1..14})[skip ci]|Body.|ok|characters before .skip ci." \
  "no-coauthor|fix(core-common): no trailers [skip ci]|Body.|none|trailer block" \
  "bad-behaviors|fix(core-common): bad ids [skip ci]|Body.|behaviors|Behaviors trailer" \
  "bad-change-class|fix(core-common): bad class [skip ci]|Body.|class|Change-Class must be"; do
  IFS='|' read -r name subj body tr why <<<"$case"
  R="$(new_repo)"; cd "$R" || exit 1; echo "$name" >native/core/common/F.kt
  case "$tr" in
    ok) commit "$subj" "$body" "${GOOD_TRAILERS[@]}" ;;
    none) commit "$subj" "$body" ;;
    behaviors) commit "$subj" "$body" "Behaviors: STATE-18" "${GOOD_TRAILERS[@]}" ;;
    class) commit "$subj" "$body" "Change-Class: minor" "${GOOD_TRAILERS[@]}" ;;
  esac
  expect "commits: planted $name fails" 1 native/tool/check_commits.sh native/main
  expect_out "commits: planted $name fails for its own reason" "$why"
done
R="$(new_repo)"; cd "$R" || exit 1; mkdir -p docs && echo d >docs/x.md
commit "docs(tool): a docs change [skip ci]" "Explains the ladder." "Change-Class: feature" "${GOOD_TRAILERS[@]}"
expect "commits: Change-Class on a docs-only commit fails" 1 native/tool/check_commits.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; git checkout -q -b side native/main; echo s >native/s.txt
commit "chore(tool): side [skip ci]" "Side." "${GOOD_TRAILERS[@]}"; git checkout -q work
git merge -q --no-ff side -m "merge: side into work [skip ci]"
expect "commits: merge commits need only [skip ci]" 0 native/tool/check_commits.sh native/main
printf 'fix(core-common): x\n\nBody.\n\nCo-Authored-By: %s\n' "$NOREPLY_ID" >"$TMP_ROOT/msg"
expect "commits: message mode refuses a missing [skip ci]" 1 native/tool/check_commits.sh --message-file "$TMP_ROOT/msg"

# ------------------------------------------------------------------------------------------------------ scan_push.sh
R="$(new_repo)"; cd "$R" || exit 1; echo 'val host = "localhost"' >native/core/common/A.kt
commit "fix(core-common): clean [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: a clean branch passes" 0 native/tool/scan_push.sh native/main
for case in "rfc1918-192|$P192" "rfc1918-10|$P10" "rfc1918-172|$P172" "tailnet-100.64|$P100" "link-local|$PLINK" \
  "ipv6-ula|$ULA6" "personal-email|$GMAIL" "github-token|$GHTOKEN" "pem-key|$PEMKEY" \
  "ts-net-host|$TSHOST"; do
  name="${case%%|*}" value="${case#*|}"
  R="$(new_repo)"; cd "$R" || exit 1; printf 'val x = "%s"\n' "$value" >native/core/common/A.kt
  commit "fix(core-common): plant $name [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
  expect "scan: planted $name blocks" 1 native/tool/scan_push.sh native/main
  expect_out "scan: planted $name is a hit on the added line" "HIT .* native/core/common/A.kt:1 "
  [[ "$name" == github-token || "$name" == personal-email || "$name" == rfc1918-192 ]] &&
    expect_no_out "scan: $name is masked in the output" "$value"
done
R="$(new_repo)"; cd "$R" || exit 1; printf 'range "%s", emulator "%s", public "%s", version "1.10.2.3"\n' "$CIDR" "$EMU" "$PUBLIC" >native/a.txt
commit "fix(core-common): ranges [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: CIDR ranges, the allow-listed emulator alias and public addresses pass" 0 native/tool/scan_push.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; mkdir -p native/fx; printf 'k = "%s"\nimport x.y.internal.Foo\nhost "%s"\n' "$GHTOKEN" "$CIDR" >native/fx/RedactorTest.kt
echo '@path native/fx/RedactorTest.kt github-token  # canary fixture' >>native/tool/scan_push.allow
commit "test(core-common): canary [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: an @path allow, a package named internal and a canonical range pass" 0 native/tool/scan_push.sh native/main
printf 'k = "%s"\n' "$PEMKEY" >>native/fx/RedactorTest.kt; commit "test(core-common): more [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: an @path allow covers only its rule" 1 native/tool/scan_push.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; printf 'k = "%s"\n' "$GHTOKEN" >native/a.txt
commit "fix(core-common): add [skip ci]" "Body." "${GOOD_TRAILERS[@]}"; rm native/a.txt
commit "fix(core-common): remove [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: a secret added then removed still blocks (history is pushed)" 1 native/tool/scan_push.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; echo "storePassword=x" >native/key.properties
commit "fix(core-common): keys [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: a key.properties file blocks" 1 native/tool/scan_push.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; echo ok >native/a.txt
git -c user.email="$GMAIL" commit -q -am x --allow-empty -m "fix(core-common): author [skip ci]" 2>/dev/null || { git add -A; git -c user.email="$GMAIL" commit -q -m "fix(core-common): author [skip ci]"; }
expect "scan: a non-noreply author blocks" 1 native/tool/scan_push.sh native/main
expect_out "scan: names the author rule" 'non-noreply-author'
R="$(new_repo)"; cd "$R" || exit 1; echo "owner: me@corp.example.org" >native/a.txt
commit "fix(core-common): watched [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: an address in OC_SCAN_EMAILS blocks" 1 env OC_SCAN_EMAILS="me@corp.example.org" native/tool/scan_push.sh native/main
R="$(new_repo)"; cd "$R" || exit 1; printf 'host %s\n' "$P10" >native/a.txt
commit "fix(core-common): plant [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
git init -q --bare "$R/remote.git"; git remote add t "$R/remote.git"; git push -q t native/main 2>/dev/null
expect "scan: --push with a hit blocks" 1 native/tool/scan_push.sh --push t work:refs/heads/work
expect "scan: --push blocked leaves the remote without the branch" 1 git --git-dir="$R/remote.git" rev-parse --verify -q refs/heads/work
git reset -q --hard HEAD~1; echo clean >native/a.txt; commit "fix(core-common): clean [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "scan: --push when clean pushes" 0 native/tool/scan_push.sh --push t work:refs/heads/work
expect "scan: the clean push reached the remote" 0 git --git-dir="$R/remote.git" rev-parse --verify -q refs/heads/work
printf 'diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1,0 +1,1 @@\n+%s\n' "$P192" >"$TMP_ROOT/p.diff"
expect "scan: --diff-file mode blocks" 1 native/tool/scan_push.sh --diff-file "$TMP_ROOT/p.diff"

# -------------------------------------------------------------------------------------------------- snapshot_push.sh
R="$(new_repo)"; cd "$R" || exit 1; echo "native work" >native/w.txt; commit "feat(core-common): work [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
git init -q --bare "$R/gh.git"; git remote add gh "$R/gh.git"
git checkout -q --orphan ghmaster; git rm -rqf . >/dev/null; echo "snapshot history" >README; git add README
git commit -q -m "snapshot: old [skip ci]"; git push -q gh ghmaster:master 2>/dev/null; git fetch -q gh; git checkout -q work
export OC_SNAPSHOT_IDENT="$NOREPLY_ID"
expect "snapshot: a dry run is tree-equal and clean" 0 native/tool/snapshot_push.sh --source work --onto gh/master --remote gh --dry-run
expect_out "snapshot: reports tree equality" '^tree-equal: yes'
expect "snapshot: pushes the snapshot branch" 0 native/tool/snapshot_push.sh --source work --onto gh/master --remote gh --name snapshot/t
S="$(git --git-dir="$R/gh.git" rev-parse snapshot/t)"
expect "snapshot: the pushed tree equals the source tree" 0 test "$(git --git-dir="$R/gh.git" rev-parse "$S^{tree}")" = "$(git rev-parse 'work^{tree}')"
expect "snapshot: the parent is the GitHub branch" 0 test "$(git --git-dir="$R/gh.git" rev-parse "$S^")" = "$(git rev-parse gh/master)"
expect "snapshot: author and committer are no-reply" 0 test "$(git --git-dir="$R/gh.git" log -1 --format='%ae %ce' "$S")" = "12345+bot@users.noreply.github.com 12345+bot@users.noreply.github.com"
expect "snapshot: a work snapshot to master is refused" 2 native/tool/snapshot_push.sh --source work --onto gh/master --remote gh --name master
expect "snapshot: a personal identity is refused" 2 env OC_SNAPSHOT_IDENT="X <$GMAIL>" native/tool/snapshot_push.sh --source work --onto gh/master --remote gh --dry-run
printf 'release: bridge [skip ci]\n\nChange-Class: feature\n' >"$TMP_ROOT/rel.txt"
expect "snapshot: release without --go only prints" 0 native/tool/snapshot_push.sh --release --candidate work --message-file "$TMP_ROOT/rel.txt" --onto gh/master --remote gh
expect "snapshot: release --go without OC_OWNER_GO is refused" 2 native/tool/snapshot_push.sh --release --candidate work --message-file "$TMP_ROOT/rel.txt" --onto gh/master --remote gh --go
expect "snapshot: master untouched by the refused release" 0 test "$(git --git-dir="$R/gh.git" rev-parse master)" = "$(git rev-parse gh/master)"
printf 'release: bridge [skip ci]\n' >"$TMP_ROOT/rel2.txt"
expect "snapshot: a release message without Change-Class is refused" 2 native/tool/snapshot_push.sh --release --candidate work --message-file "$TMP_ROOT/rel2.txt" --onto gh/master --remote gh
printf 'lan %s\n' "$P192" >native/w.txt; commit "feat(core-common): leak [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "snapshot: a private address in the tree blocks the push" 1 native/tool/snapshot_push.sh --source work --onto gh/master --remote gh --name snapshot/leak
expect "snapshot: the blocked snapshot was not pushed" 1 git --git-dir="$R/gh.git" rev-parse --verify -q refs/heads/snapshot/leak
unset OC_SNAPSHOT_IDENT

# ---------------------------------------------------------------------------------------------------- twin_check.sh
R="$(new_repo)"; cd "$R" || exit 1
mkdir -p android/app/src && echo "class A" >android/app/src/A.kt && commit "chore(tool): flutter A [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
A="$(git rev-parse --short HEAD)"
mkdir -p native/android/linux && cp android/app/src/A.kt native/android/linux/A.kt
twins() { printf '| Native path | Flutter source path | Source commit at copy | Last synced Flutter commit | Native owner |\n|---|---|---|---|---|\n| native/android/linux/A.kt | android/app/src/A.kt | %s | %s | S runtime |\n' "$1" "$2" >native/TWINS.md; }
twins "$A" "$A"; commit "chore(tool): twin [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
expect "twins: an in-sync twin passes" 0 native/tool/twin_check.sh --verify-copy
echo "class A2" >android/app/src/A.kt; commit "fix(tool): flutter fix [skip ci]" "Body." "${GOOD_TRAILERS[@]}"
B="$(git rev-parse --short HEAD)"
expect "twins: a Flutter commit since last synced is drift" 1 native/tool/twin_check.sh
expect_out "twins: lists the drift commit" "$B"
expect "twins: --list-only reports drift without failing" 0 native/tool/twin_check.sh --list-only
twins "$A" "$B"
expect "twins: --verify-copy catches a stale copy" 1 native/tool/twin_check.sh --verify-copy
cp android/app/src/A.kt native/android/linux/A.kt
expect "twins: synced and copied passes" 0 native/tool/twin_check.sh --verify-copy
twins "$A" deadbee
expect "twins: an unknown sha is invalid" 1 native/tool/twin_check.sh
twins "$B" "$A"
expect "twins: last synced older than the copy is invalid" 1 native/tool/twin_check.sh
twins "$A" "$B"
expect "twins: --kind res skips Kotlin rows" 0 native/tool/twin_check.sh --kind res

# ------------------------------------------------------------------------------------------------- ladder plumbing
R="$(new_repo)"; cd "$R" || exit 1
# shellcheck source=../lib/common.sh
mods="$(source native/tool/lib/common.sh; printf '%s\n' native/core/common/src/x.kt native/ui/kit/src/a.kt android/app/Main.kt native/tool/t0.sh native/build-logic/x.kts | oc_paths_to_modules | paste -sd' ')"
expect "ladder: paths map to modules (Flutter android/ ignored)" 0 test "$mods" = ":core:common :ui:kit build-logic"
tasks="$(source native/tool/lib/common.sh; oc_module_test_tasks :ui:kit | paste -sd' ')"
expect "ladder: a golden module verifies Roborazzi" 0 test "$tasks" = "desktopTest compileAndroidMain verifyRoborazziAndroidHostTest"
tasks="$(source native/tool/lib/common.sh; oc_module_test_tasks :ui:kit record | paste -sd' ')"
expect "ladder: --record records Roborazzi" 0 test "$tasks" = "desktopTest compileAndroidMain recordRoborazziAndroidHostTest"
echo x >native/core/common/C.kt
expect "ladder: t1 dry run lists the touched module" 0 native/tool/t1.sh --base native/main --globs 'native/**' --dry-run
expect_out "ladder: t1 tasks include :core:common:desktopTest" 'tasks: .*:core:common:desktopTest'
expect "ladder: t1 fails the write set before any build" 1 native/tool/t1.sh --base native/main --globs 'native/tool/**' --dry-run
expect "ladder: t1 refuses to record goldens off the VPS" 2 env OC_VPS_HOST= native/tool/t1.sh --base native/main --record :ui:kit --dry-run --where local
expect "ladder: t0 rejects an unknown module (2)" 2 native/tool/t0.sh :no:such
expect "ladder: t3 without an APK cannot run (2)" 2 native/tool/t3.sh
expect "ladder: t3 refuses a phone serial (2)" 2 native/tool/t3.sh "$R/native/tool/t0.sh" --serial ABC123
expect "ladder: t4 without APKs cannot run (2)" 2 native/tool/t4.sh
expect "ladder: vps_gradle without OC_VPS_HOST cannot run (2)" 2 env OC_VPS_HOST= native/tool/vps_gradle.sh -- help
expect "ladder: vps_gradle dry run never prints the host" 0 env OC_VPS_HOST="$P10" native/tool/vps_gradle.sh --dry-run -- :core:common:desktopTest
expect_no_out "ladder: the host stays out of the dry-run output" "$P10"
expect_out "ladder: an APK task uses the separate -apk folder" 'ocn-'
expect "ladder: vps_gradle sends APK builds to a separate folder" 0 bash -c "OC_VPS_HOST=h native/tool/vps_gradle.sh --unit u --dry-run -- :androidApp:assembleStableRelease | grep -q 'ocn-u-apk'"
# vps_gradle.sh end to end through an ssh shim that runs the "remote" side on this machine with its own HOME.
if command -v rsync >/dev/null; then
  R="$(new_repo)"; cd "$R" || exit 1
  mkdir -p "$R/bin" "$R/vpshome"
  cat >"$R/bin/ssh" <<'SH'
#!/usr/bin/env bash
while [[ "${1:-}" == -o ]]; do shift 2; done
shift # host
export HOME="$FAKE_VPS_HOME"; cd "$HOME" || exit 255
if (($# == 1)); then exec bash -c "$1"; else exec "$@"; fi
SH
  cat >native/gradlew <<'SH'
#!/usr/bin/env bash
echo "fake gradle $*"
mkdir -p core/common/build/test-results/desktopTest
echo '<testsuite name="x" tests="3"></testsuite>' >core/common/build/test-results/desktopTest/TEST-x.xml
exit "${FAKE_GRADLE_RC:-0}"
SH
  chmod +x "$R/bin/ssh" native/gradlew; echo "uncommitted" >native/untracked.txt
  export FAKE_VPS_HOME="$R/vpshome"
  expect "vps: syncs, runs under the lock and pulls reports back" 0 env PATH="$R/bin:$PATH" OC_VPS_HOST=vps native/tool/vps_gradle.sh --unit u -- :core:common:desktopTest
  expect "vps: uncommitted files reach the VPS copy" 0 test -f "$R/vpshome/wt/ocn-u/native/untracked.txt"
  expect "vps: test results come back" 0 test -f native/core/common/build/test-results/desktopTest/TEST-x.xml
  expect "vps: a Gradle failure is exit 1" 1 env PATH="$R/bin:$PATH" OC_VPS_HOST=vps FAKE_GRADLE_RC=1 native/tool/vps_gradle.sh --unit u --no-sync -- help
  expect "vps: too little disk cannot run (2)" 2 env PATH="$R/bin:$PATH" OC_VPS_HOST=vps native/tool/vps_gradle.sh --unit u --no-sync --min-disk-gb 999999 -- help
  exec 9>"$R/vpshome/.oc-native-build.lock"; flock 9
  expect "vps: a held lock cannot run (2)" 2 env PATH="$R/bin:$PATH" OC_VPS_HOST=vps OC_LOCK_WAIT=1 native/tool/vps_gradle.sh --unit u --no-sync -- help
  exec 9>&-
  expect "vps: an APK build uses the -apk copy" 0 env PATH="$R/bin:$PATH" OC_VPS_HOST=vps native/tool/vps_gradle.sh --unit u -- :androidApp:assembleStableRelease
  expect "vps: the -apk copy exists" 0 test -d "$R/vpshome/wt/ocn-u-apk/native"
  unset FAKE_VPS_HOME
else
  echo "skip vps end-to-end tests: rsync is not installed"
fi
# Local Gradle runs through a fake gradlew: cwd must be native/, one -p at most, exit classes and MISSING tasks.
if command -v java >/dev/null; then
  R="$(new_repo)"; cd "$R" || exit 1
  cat >native/gradlew <<'SH'
#!/usr/bin/env bash
[[ "$PWD" == */native ]] || { echo "wrong cwd $PWD"; exit 3; }
n=0; for a in "$@"; do [[ "$a" == -p ]] && n=$((n + 1)); done
((n <= 1)) || { echo "Multiple arguments were provided for command-line option '-p'."; exit 1; }
case "${FAKE_GRADLE:-ok}" in
  ok) mkdir -p core/common/build/test-results/desktopTest
      echo '<testsuite name="x" tests="2"><testcase classname="C" name="B-X-001 ok"/></testsuite>' >core/common/build/test-results/desktopTest/TEST-x.xml
      exit 0 ;;
  red) mkdir -p core/common/build/test-results/desktopTest
       echo '<testsuite name="x" tests="1"><testcase classname="C" name="B-X-002 red"><failure/></testcase></testsuite>' >core/common/build/test-results/desktopTest/TEST-y.xml
       exit 1 ;;
  missing) echo "Task 'analyzeStableReleaseR8Config' not found in project ':androidApp'."; exit 1 ;;
  net) echo "Received status code 429 from server: Too Many Requests"; exit 1 ;;
esac
SH
  chmod +x native/gradlew
  H="$TMP_ROOT/home$RANDOM"; mkdir -p "$H"
  low=(env OC_MIN_FREE_MB=0 OC_T2_MIN_DISK_GB=0 OC_BUILD_LOCK_FILE="$H/build.lock" HOME="$H")
  expect "ladder: t0 passes on green tests" 0 "${low[@]}" native/tool/t0.sh :core:common B-X-001
  expect_out "ladder: t0 counts the tests" 'tests run: 2'
  expect "ladder: t0 fails on a red test" 1 "${low[@]}" FAKE_GRADLE=red native/tool/t0.sh :core:common
  expect_out "ladder: t0 names the failing test" 'C > B-X-002 red'
  expect "ladder: t0 maps a 429 to could-not-run (2)" 2 "${low[@]}" FAKE_GRADLE=net native/tool/t0.sh :core:common
  expect "ladder: t2 build-logic step runs from native/ with one -p" 0 "${low[@]}" native/tool/t2.sh --where local --steps build-logic
  expect_out "ladder: a --steps run is PARTIAL" 'PARTIAL run'
  expect "ladder: t2 reports a missing gate task as a failure" 1 "${low[@]}" FAKE_GRADLE=missing native/tool/t2.sh --where local --steps r8-config
  expect_out "ladder: t2 names the missing task" 'MISSING: .*analyzeStableReleaseR8Config'
  echo x >native/core/common/C.kt
  expect "ladder: t1 runs the touched module's tasks locally" 0 "${low[@]}" native/tool/t1.sh --base native/main --globs 'native/**' --where local
  exec 8>"$H/build.lock"; flock 8
  expect "ladder: t1 with the build slot held cannot run (2)" 2 "${low[@]}" OC_LOCK_WAIT=1 native/tool/t1.sh --base native/main --where local
  exec 8>&-
else
  echo "skip local ladder tests: no java"
fi
expect "ladder: t2 dry run lists every step" 0 native/tool/t2.sh --dry-run --where local
expect_out "ladder: t2 includes the assemble step" 'step assemble: ./gradlew -Poc.debugSignRelease=true'
expect "ladder: a run writes exit-code" 0 bash -c 'ls native/build/native-t2/*/exit-code >/dev/null'

expect "GATES review: offline regression suite" 0 python3 "$TOOL_SRC/test/test_gates_review.py"

echo
echo "native/tool self-tests: $pass passed, $fail failed"
((fail)) && printf '  failed: %s\n' "${failed[@]}"
((fail == 0))
