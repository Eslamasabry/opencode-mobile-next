#!/usr/bin/env bash
# Run test files on the build server instead of this machine.
#
#   tool/qa/vps_test.sh test/foo_test.dart test/bar_test.dart [-- flutter test args]
#
# Copies this worktree as it is now (uncommitted edits included; build/,
# .dart_tool/ and .git/ left out) to ~/wt/<worktree name> on OC_VPS_HOST, runs
# `flutter test` there and prints the result. Golden (screenshot) files run
# there too: they compare exactly, except the few images listed in
# test/goldens/tolerances.json. Tests run in UTC, like CI, so clock times in
# goldens do not follow the server's timezone or summer time. Needs key-only SSH to OC_VPS_HOST, whose
# ~/.profile puts the pinned Flutter on PATH.
set -euo pipefail
# The worktree is the one you run it from, so any checkout can use one copy.
root="$(git rev-parse --show-toplevel)"
# The build server's ssh target is private: it comes from the environment or
# from tool/qa/build-server.env (git-ignored) or ~/.config/oc-app/build-server.env.
for _env in "$(git rev-parse --show-toplevel 2>/dev/null)/tool/qa/build-server.env" \
    "${XDG_CONFIG_HOME:-$HOME/.config}/oc-app/build-server.env"; do
  [ -f "$_env" ] && { . "$_env"; break; }
done
host="${OC_VPS_HOST:-${OC_VPS_HOST:-}}"
[ -n "$host" ] || { echo "$(basename "$0"): set OC_VPS_HOST (or tool/qa/build-server.env)" >&2; exit 64; }
jobs="${OC_VPS_JOBS:-4}"
files=()
extra=()
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--" ]]; then shift; extra=("$@"); break; fi
  files+=("$1"); shift
done
((${#files[@]} > 0)) || { echo "vps_test: name the test files to run" >&2; exit 64; }
cd "$root"
for f in "${files[@]}"; do
  [[ -f "$f" ]] || { echo "vps_test: no such file: $f" >&2; exit 64; }
done
name="$(basename "$root")"
ssh -o BatchMode=yes "$host" "mkdir -p ~/wt"
rsync -a --delete --exclude build/ --exclude .dart_tool/ --exclude .git --exclude tool/qa/build-server.env \
  --exclude 'test/**/failures/' --exclude android/key.properties \
  "$root/" "$host:wt/$name/"
quoted=$(printf '%q ' "${files[@]}" ${extra[@]+"${extra[@]}"})
# The run stops when this side does. A cut-off caller (a killed agent, a
# closed terminal) only closes the ssh connection, and without a terminal
# the server's `flutter test` never hears of it: its testers ran on for a
# day (2026-10-27). So the run gets its own process group, and a watcher on
# the connection's stdin (handed over as fd 9: a background job's own
# stdin is /dev/null) stops that group at EOF. Our stdin is held open by
# a sleeper until ssh returns; ServerAlive notices a dead network.
exec 3< <(exec sleep 2147483647)
keep=$!
trap 'kill "$keep" 2>/dev/null' EXIT
inner="cd ~/wt/$name && flutter pub get >/dev/null 2>&1 && (cd widgetbook 2>/dev/null && flutter pub get >/dev/null 2>&1 || true) && TZ=UTC exec flutter test -j $jobs $quoted"
remote="exec 9<&0; setsid bash -c $(printf '%q' "$inner") <&- 9<&- & run=\$!; { cat <&9 >/dev/null; kill -TERM -\$run 2>/dev/null; } & watch=\$!; wait \$run; rc=\$?; kill \$watch 2>/dev/null; exit \$rc"
ssh -o BatchMode=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=4 "$host" "bash -lc $(printf '%q' "$remote")" <&3
