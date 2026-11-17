#!/usr/bin/env bash
# detekt on the modules a change touches, for the Native CI lanes (the `detekt` job of native-ci.yml). No Gradle: it
# runs tool/qa/detekt.sh (the pinned jar, each module's own baseline, and the baseline ratchet) and writes
# reports/detekt.json for tool/ci/summary.py, which turns a new finding into FAIL and prints the result per module.
#
#   tool/ci/detekt_lane.sh <base-ref> [<module>...]    modules given: exactly those; none: the ones the diff touches
#
# Touched modules are the folders under native/ that `git diff <base>...HEAD` changes (the same path rule as
# fast_tasks.sh, without the dependents: detekt reads a module's own sources). Build-wide changes, an unknown base or
# more than 30 modules mean every module. Always exits 0: the verdict is summary.py's, read from detekt.json.
#   detekt.json: {"ran", "base", "modules", "scope", "exit", "ratchet", "findings": {module: count}, "lines": [...]}
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
bash tool/qa/detekt.sh "${mods[@]}" --base "$base" --report-dir build/reports/detekt >"$log" 2>&1
rc=$?
cat "$log"
python3 -I - "$rc" "$base" "$scope" "$log" "build/reports/detekt" "${mods[@]}" <<'PY'
import json, re, sys
rc, base, scope, log, rdir, *mods = sys.argv[1:]
lines = open(log, encoding='utf-8', errors='replace').read().splitlines()
ratchet = next((re.match(r'ratchet: (\w+)', l).group(1) for l in lines if re.match(r'ratchet: \w+', l)), 'unknown')
findings, ran = {}, []
for l in lines:
    m = re.match(r'detekt (\S+?): (.*)', l)
    if not m:
        continue
    mod, what = m.groups()
    ran.append(mod)
    if what.startswith('NEW FINDINGS'):
        key = mod.lstrip(':').replace(':', '-')
        try:
            n = sum(1 for x in open(f'{rdir}/{key}.txt', encoding='utf-8') if re.match(r'[A-Za-z]+ - ', x))
        except OSError:
            n = 1
        findings[mod] = n or 1
    elif what.startswith('could not run'):
        findings[mod] = -1
detail = [l for l in lines if re.match(r'(ratchet: (FAIL|NEW)|  \+ |  [A-Za-z]+ - )', l)][:20]
json.dump({'ran': True, 'base': base, 'scope': scope, 'modules': ran or [m for m in mods if m != '--all'],
           'exit': int(rc), 'ratchet': ratchet, 'findings': findings, 'lines': detail},
          open('reports/detekt.json', 'w'))
PY
exit 0
