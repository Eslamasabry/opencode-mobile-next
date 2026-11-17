#!/usr/bin/env bash
# detekt on the modules a change touches, for the Native CI lanes (the `detekt` job of native-ci.yml). No Gradle: it
# runs tool/qa/detekt.sh --new-against <base> (the pinned jar, no baseline: a finding counts only when the same
# module at <base> does not have it, by signature) and writes
# reports/detekt.json for tool/ci/summary.py, which turns a new finding into FAIL and prints the result per module.
#
#   tool/ci/detekt_lane.sh <base-ref> [<module>...]    modules given: exactly those; none: the ones the diff touches
#   tool/ci/detekt_lane.sh <base-ref> --all             EVERY module: the entry for the candidate lane (one script, one flag)
#
# Touched modules are the folders under native/ that `git diff <base>...HEAD` changes (the same path rule as
# fast_tasks.sh, without the dependents: detekt reads a module's own sources). Build-wide changes, an unknown base or
# more than 30 modules mean every module. Always exits 0: the verdict is summary.py's, read from detekt.json.
#   detekt.json: {"ran", "base", "modules", "scope", "exit", "ratchet", "findings": {module: NEW count}, "totals": {module: all
#   findings, old included}, "lines": [...]}
set -uo pipefail
base=${1:?base ref (for example origin/native/glass-next)}; shift
cd "$(dirname "$0")/../.."
mkdir -p reports build/reports/detekt
mods=("$@") scope=given
if ((${#mods[@]} == 0)); then
  scope=touched
  if ! git rev-parse --verify -q "$base^{commit}" >/dev/null; then scope=all; else
    declare -A hit=()
    while IFS= read -r p; do
      case "$p" in
        build-logic/*) hit[build-logic]=1 ;;
        gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties) scope=all ;;
        core/*|protocol/*|engine/*|ui/*|feature/*|android/*|tools/*)
          m=$(printf '%s' "$p" | cut -d/ -f1,2)
          [ -f "$m/build.gradle.kts" ] && hit[":${m//\//:}"]=1 ;;
        androidApp/*|desktopApp/*|quality/*|benchmark/*) hit[":${p%%/*}"]=1 ;;
      esac
    done < <(git diff --name-only "$base"...HEAD -- . | sed -n 's|^native/||p')
    mods=("${!hit[@]}")
    ((${#mods[@]} > 30)) && scope=all
  fi
  [ "$scope" = all ] && mods=(--all)
fi
log=build/reports/detekt/run.log
bash tool/qa/detekt.sh "${mods[@]}" --new-against "$base" --report-dir build/reports/detekt >"$log" 2>&1
rc=$?
cat "$log"
python3 -I - "$rc" "$base" "$scope" "$log" "build/reports/detekt" "${mods[@]}" <<'PY'
import json, re, sys
rc, base, scope, log, rdir, *mods = sys.argv[1:]
lines = open(log, encoding='utf-8', errors='replace').read().splitlines()
findings, totals, ran = {}, {}, []
for l in lines:
    m = re.match(r'detekt (\S+?): (.*)', l)
    if not m:
        continue
    mod, what = m.groups()
    ran.append(mod)
    t = re.search(r'total (\d+)', what)
    if t:
        totals[mod] = int(t.group(1))
    n = re.search(r'NEW FINDINGS \(new (\d+)', what)
    if n:
        findings[mod] = int(n.group(1))
    elif what.startswith('could not run') or what.startswith('NEW FINDINGS'):
        findings[mod] = -1 if what.startswith('could not run') else 1
ratchet = 'signature'
detail = [l for l in lines if re.match(r'(  [A-Za-z]+ - |detekt: could not run)', l)][:20]
json.dump({'ran': True, 'base': base, 'scope': scope, 'modules': ran or [m for m in mods if m != '--all'],
           'exit': int(rc), 'ratchet': ratchet, 'findings': findings, 'totals': totals, 'lines': detail},
          open('reports/detekt.json', 'w'))
PY
exit 0
