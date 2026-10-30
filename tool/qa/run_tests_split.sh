#!/usr/bin/env bash
# Full Flutter suite split between this machine and the build server.
#
#   tool/qa/run_tests_split.sh
#
# Golden (screenshot) tests run on both machines: 99.2% of the images are
# pixel-exact on the server, and the few Team screens that rasterize blur a
# hair differently are listed with a small per-image limit in
# test/goldens/tolerances.json (test/golden_tolerance.dart); everything else
# compares exactly. tool/qa/split_plan.py hands files out longest first to the
# machine slot that would finish them earliest, using the measured seconds in
# tool/qa/test_timings.json. A file that fails on the server is run again here
# before it counts, so a machine difference never reads as a failure.
#
# This machine runs at most 2 processes at once (an aggregate is about 1.4 GB,
# and the PC is shared); the server takes the larger share. Small files run in a few aggregated entrypoints (build/agg/, generated on each
# machine; tool/qa/agg_exclude.txt lists the files that must run alone). A file
# that fails inside one is run alone here before it counts, as above.
#
# The committed HEAD goes to the private git remote OC_SPLIT_REMOTE (default
# "vps"; never a public remote) as refs/heads/split/<sha>, and the server
# tests exactly that commit in OC_SPLIT_REPO. Prints every failing file as
# "FAILED <path>" and exits non-zero unless all passed.
# Logs: build/traycer/split-<UTC time>/.
#
# Needs a clean tree (it tests the commit, not the working copy) and key-only
# SSH to OC_SPLIT_HOST, whose ~/.profile puts the pinned Flutter on PATH.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
# The build server's ssh target is private: it comes from the environment or
# from tool/qa/build-server.env (git-ignored) or ~/.config/oc-app/build-server.env.
for _env in "$(git rev-parse --show-toplevel 2>/dev/null)/tool/qa/build-server.env" \
    "${XDG_CONFIG_HOME:-$HOME/.config}/oc-app/build-server.env"; do
  [ -f "$_env" ] && { . "$_env"; break; }
done
host="${OC_SPLIT_HOST:-${OC_VPS_HOST:-}}"
[ -n "$host" ] || { echo "$(basename "$0"): set OC_VPS_HOST (or tool/qa/build-server.env)" >&2; exit 64; }
remote="${OC_SPLIT_REMOTE:-vps}"
remote_repo="${OC_SPLIT_REPO:-Storage/Code/oc_app}"
remote_jobs="${OC_SPLIT_REMOTE_JOBS:-6}"
local_jobs="${OC_SPLIT_LOCAL_JOBS:-2}"
flutter="${FLUTTER:-$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter}"
cd "$root"
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "run_tests_split: commit first; the split tests HEAD, not the working copy" >&2
  exit 64
fi
sha="$(git rev-parse HEAD)"
out="$root/build/traycer/split-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$out"

# Route by measured time (goldens included) across both machines' slots. Small
# files with no private binding or leaked state are packed into a few aggregated
# entrypoints (tool/qa/gen_aggregates.py): one process pays the 4.4 s load once.
find test -name '*_test.dart' | sort >"$out/all.txt"
python3 "$root/tool/qa/gen_aggregates.py" eligible --root "$root" $(cat "$out/all.txt") >"$out/eligible.txt"
python3 "$root/tool/qa/split_plan.py" --all "$out/all.txt" --out "$out" \
  --aggregate "$out/eligible.txt" --bundles "${OC_SPLIT_BUNDLES:-12}" \
  --local-slots "$local_jobs" --remote-slots "$remote_jobs" \
  --remote-speed "${OC_SPLIT_REMOTE_SPEED:-0.33}"
echo "run_tests_split: $(wc -l <"$out/local.txt") files here, $(wc -l <"$out/remote.txt") on the server ($sha)"

git push -q -f "$remote" "HEAD:refs/heads/split/$sha"

# The server half, in the background; stdin carries its plan. The JSON report
# is copied back afterwards.
t_start=$SECONDS
ssh -o BatchMode=yes "$host" "bash -c '
  set -uo pipefail; . ~/.profile; export TZ=UTC
  cd ~/$remote_repo || exit 70
  git fetch -q origin refs/heads/split/$sha || exit 70
  # Each run tests in its own worktree: runs from several worktrees at once
  # used to check out their commits into one shared checkout, mid-run.
  mkdir -p ~/runs; wt=~/runs/wt-$sha-\$\$
  git worktree add -q --detach \$wt $sha || exit 70
  cd \$wt
  flutter pub get >/dev/null 2>&1 || exit 70
  (cd widgetbook && flutter pub get >/dev/null 2>&1) || true
  tool/qa/run_plan.sh $remote_jobs ~/runs/split-$sha.json > ~/runs/split-$sha.log 2>&1
  rc=\$?
  cd ~/$remote_repo && git worktree remove --force \$wt
  echo REMOTE_EXIT=\$rc
'" <"$out/remote.plan" >"$out/remote.log" 2>&1 &
remote_pid=$!

# This machine's half, under one machine_lock test slot.
export PATH="$(dirname "$flutter"):$PATH"
"$root/tool/qa/machine_lock.sh" test -- "$root/tool/qa/run_plan.sh" "$local_jobs" \
  "$out/local.json" <"$out/local.plan" >"$out/local.log" 2>&1 || true
local_secs=$((SECONDS - t_start))
wait "$remote_pid" || true
remote_secs=$((SECONDS - t_start))
status=0
grep -q '^REMOTE_EXIT=' "$out/remote.log" || { echo "run_tests_split: the server run did not finish (see remote.log)" >&2; status=1; }
ssh -o BatchMode=yes "$host" "cat ~/runs/split-$sha.json" >"$out/remote.json" 2>/dev/null || : >"$out/remote.json"

collect() { # name report scale
  python3 "$root/tool/qa/split_collect.py" --root "$root" --plan "$out/local.plan" \
    --plan "$out/remote.plan" --out "$out/$1" --scale "$3" \
    --merge-into "$out/timings-merged.json" "$2" >/dev/null
}
cp tool/qa/test_timings.json "$out/timings-merged.json"
collect local "$out/local.json" 1
collect remote "$out/remote.json" "${OC_SPLIT_REMOTE_SPEED:-0.33}"
for side in local remote; do
  if [[ -s "$out/$side/incomplete.txt" ]]; then
    echo "run_tests_split: the $side run was cut short (no final report); see $side.log" >&2
    status=1
  fi
done

# A server failure, and any failure inside an aggregated entrypoint, only counts
# if the file also fails when run alone here: a machine difference or leaked
# state never reads as a failure.
sort -u "$out/local/agg-failed.txt" "$out/remote/agg-failed.txt" "$out/remote/standalone-failed.txt" >"$out/recheck.txt"
: >"$out/recheck-failed.txt"
if [[ -s "$out/recheck.txt" ]]; then
  echo "run_tests_split: running $(wc -l <"$out/recheck.txt") suspect file(s) alone here"
  # At least one process, even when this machine takes no share of the plan
  # (OC_SPLIT_LOCAL_JOBS=0): flutter test refuses -j 0.
  recheck_rc=0
  "$root/tool/qa/machine_lock.sh" test -- "$flutter" test -j "$((local_jobs > 0 ? local_jobs : 1))" \
    --reporter=failures-only $(cat "$out/recheck.txt") >"$out/recheck.log" 2>&1 || recheck_rc=$?
  grep -F '[E]' "$out/recheck.log" | grep -oE 'test/[A-Za-z0-9_/.-]+_test\.dart' |
    sort -u >"$out/recheck-failed.txt" || true
  # A recheck that failed without naming a test (bad arguments, a load error)
  # proves nothing: every suspect file stays failed.
  if [[ $recheck_rc != 0 && ! -s "$out/recheck-failed.txt" ]]; then
    echo "run_tests_split: the recheck did not run (see recheck.log); counting every suspect file as failed" >&2
    cp "$out/recheck.txt" "$out/recheck-failed.txt"
  fi
fi
sort -u "$out/local/standalone-failed.txt" "$out/recheck-failed.txt" | sed 's/^/FAILED /' | tee "$out/failed.txt"
[[ -s "$out/failed.txt" ]] && status=1
echo "run_tests_split: this machine $((local_secs / 60))m$((local_secs % 60))s, server $((remote_secs / 60))m$((remote_secs % 60))s, total $((SECONDS / 60))m$((SECONDS % 60))s"
echo "run_tests_split: measured seconds for the next plan: $out/timings-merged.json"
echo "run_tests_split: $( [[ $status == 0 ]] && echo passed || echo failed ) ($sha, logs in $out)"
exit "$status"
