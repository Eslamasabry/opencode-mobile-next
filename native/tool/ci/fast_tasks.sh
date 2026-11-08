#!/usr/bin/env bash
# Prints the desktop-test Gradle tasks for the modules a change touches, plus their direct dependents, one per
# line, or the single word ALL when the whole suite should run. The fast lane (tool/ci/LANES.md) feeds this to its
# shards. Details go to stderr.
#
#   tool/ci/fast_tasks.sh <base-ref> [max-modules]
#
# The change is `git diff --name-only <base>...HEAD` (merge-base diff, committed changes only, like the PR view).
# Path -> module: native/<layer>/<name>/... is :<layer>:<name>; native/{androidApp,desktopApp,quality}/... likewise.
# Direct dependents: every module whose build.gradle.kts names project(":<changed>"). ALL when the base cannot be
# resolved, when build logic, the version catalog, the root build or settings changed, when a changed path under
# native/ belongs to no module, or when more than [max-modules] (default 30) modules end up selected.
# Only paths under native/ count; the rest of the repo (Flutter app, docs) cannot affect these tests.
set -euo pipefail
base=${1:?base ref (for example origin/native/screens-merge41)}
max=${2:-30}
cd "$(dirname "$0")/../.."
all() { echo "fast_tasks: $1 -> ALL" >&2; echo ALL; exit 0; }

git rev-parse --verify -q "$base^{commit}" >/dev/null || all "base $base not found"
changed=$(git diff --name-only "$base"...HEAD -- . ) || all "diff failed"
# git prints paths relative to the repo root; make them relative to native/.
changed=$(printf '%s\n' "$changed" | sed -n 's|^native/||p')
[ -n "$changed" ] || { echo "fast_tasks: nothing under native/ changed" >&2; exit 0; }

declare -A hit=()
while IFS= read -r p; do
  case "$p" in
    build-logic/*|gradle/*|build.gradle.kts|settings.gradle.kts|gradle.properties|version.properties) all "build-wide file $p" ;;
    tool/ci/goldens.gradle.kts|tool/ci/quarantine.gradle.kts) all "test-wide file $p" ;;
    core/*|protocol/*|engine/*|ui/*|feature/*|android/*|tools/*)
      m=$(printf '%s' "$p" | cut -d/ -f1,2)
      [ -f "$m/build.gradle.kts" ] && hit[":${m//\//:}"]=1 || all "path $p is under no module" ;;
    androidApp/*|desktopApp/*|quality/*|benchmark/*) hit[":${p%%/*}"]=1 ;;
    # Docs, scripts, catalogs and other non-code paths: not module sources. Fixtures feed the golden fonts.
    fixtures/*) all "fixtures changed ($p)" ;;
    *) : ;;
  esac
done <<<"$changed"

# Direct dependents from the dependency declarations (project(":a:b") in a module's build file).
declare -A sel=()
for m in "${!hit[@]}"; do sel[$m]=1; done
for bf in */*/build.gradle.kts androidApp/build.gradle.kts desktopApp/build.gradle.kts quality/build.gradle.kts; do
  [ -f "$bf" ] || continue
  dir=${bf%/build.gradle.kts}; me=":${dir//\//:}"
  for dep in $(grep -oE 'project\(":[a-zA-Z0-9:_-]+"\)' "$bf" | sed -E 's/project\("(.*)"\)/\1/'); do
    [ -n "${hit[$dep]:-}" ] && sel[$me]=1
  done
done
[ "${#sel[@]}" -le "$max" ] || all "${#sel[@]} modules selected (limit $max)"

# Keep only modules that have a desktop-test task (same discovery as shard_tasks.sh), and always the repo gates.
mapfile -t known < <(tool/ci/shard_tasks.sh 0 1)
out=()
for t in "${known[@]}"; do
  m=${t%:*}
  if [ -n "${sel[$m]:-}" ] || [ "$m" = ":quality" ]; then out+=("$t"); fi
done
echo "fast_tasks: changed: $(printf '%s ' "${!hit[@]}")" >&2
echo "fast_tasks: selected ${#out[@]} test tasks (from ${#sel[@]} modules plus :quality)" >&2
printf '%s\n' "${out[@]}"
