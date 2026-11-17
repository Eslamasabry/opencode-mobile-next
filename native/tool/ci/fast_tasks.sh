#!/usr/bin/env bash
# Prints the desktop-test Gradle tasks for the modules a change touches, plus all their dependents (transitive), one per
# line, or the single word ALL when the whole suite should run. The fast lane (tool/ci/LANES.md) feeds this to its
# shards. Details go to stderr.
#
#   tool/ci/fast_tasks.sh <base-ref> [max-modules]
#
# The change is `git diff --name-only <base>...HEAD` (merge-base diff, committed changes only, like the PR view).
# Path -> module: native/<layer>/<name>/... is :<layer>:<name>; native/{androidApp,desktopApp,quality}/... likewise.
# Dependents: every module that needs a changed module, transitively (see below). ALL when the base cannot be
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

# Dependents, transitively: every module that needs a changed module, directly or through another module.
# Edges come from two places: project(":a:b") in a module's build file (a call may be split over lines), and the
# dependencies the convention plugins add without any build file naming them (build-logic OcKmpFeaturePlugin: every
# oc.kmp.feature module gets :ui:kit :ui:design :ui:words :ui:routes :core:domain :core:model :core:common; OcKmpLibraryPlugin:
# every oc.kmp.library module except :core:testing gets :core:testing). Before, only the first, one hop, was read, so a
# :ui:words change never selected the features (run 44148014813 tested 4 modules). Keep the two lists in step with the plugins.
declare -A needs=()   # module -> space separated modules it needs
for bf in */*/build.gradle.kts androidApp/build.gradle.kts desktopApp/build.gradle.kts quality/build.gradle.kts; do
  [ -f "$bf" ] || continue
  dir=${bf%/build.gradle.kts}; me=":${dir//\//:}"
  deps=$(tr '\n' ' ' <"$bf" | grep -oE 'project\( *"[a-zA-Z0-9:_-]+" *\)' | sed -E 's/project\( *"(.*)" *\)/\1/' || true)
  grep -q 'id("oc\.kmp\.feature")' "$bf" && deps+=" :ui:kit :ui:design :ui:words :ui:routes :core:domain :core:model :core:common"
  if grep -q 'id("oc\.kmp\.library")' "$bf" && [ "$me" != ":core:testing" ]; then deps+=" :core:testing"; fi
  needs[$me]=$deps
done
declare -A sel=()
for m in "${!hit[@]}"; do sel[$m]=1; done
changed_any=1
while [ "$changed_any" = 1 ]; do
  changed_any=0
  for me in "${!needs[@]}"; do
    [ -n "${sel[$me]:-}" ] && continue
    for dep in ${needs[$me]}; do
      if [ -n "${sel[$dep]:-}" ]; then sel[$me]=1; changed_any=1; break; fi
    done
  done
done
[ "${#sel[@]}" -le "$max" ] || all "${#sel[@]} modules selected (limit $max)"

# Keep only modules with a discovered test task. Quality/security always run in the dedicated fast-lane shard;
# when quality is selected here, retain both tasks so every consumer sees their paired contract.
mapfile -t known < <(tool/ci/shard_tasks.sh 0 1)
out=()
for t in "${known[@]}"; do
  m=${t%:*}
  if [ -n "${sel[$m]:-}" ]; then out+=("$t"); fi
done
echo "fast_tasks: changed: $(printf '%s ' "${!hit[@]}")" >&2
echo "fast_tasks: selected ${#out[@]} test tasks (from ${#sel[@]} modules)" >&2
printf '%s\n' "${out[@]}"
