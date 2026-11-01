#!/usr/bin/env bash
# T0 (plan 02 §8): one module's tests while editing, in the foreground, in seconds.
#
#   native/tool/t0.sh <module> [test filter] [--android] [--vps]
#
#   <module>       :engine:delivery, engine:delivery or engine/delivery
#   [test filter]  a catalog id or name part (B-STATE-018 becomes --tests '*B-STATE-018*'); a filter with '*' is
#                  passed as given
#   --android      the module's Android host test (testAndroidHostTest) instead of its common tests (desktopTest)
#   --vps          run through vps_gradle.sh instead of locally
#
# JVM modules (:quality, :tools:*) run `test`; overrides live in tool/ladder/tasks.tsv. Locally the run takes the
# machine's one build slot (plan 02 §7.8; tool/qa/machine_lock.sh's lock file on the PC) and needs JDK 21 and
# >= $OC_MIN_FREE_MB (7168) MB available. Writes build/native-t0/<id>/summary.txt and exit-code.
# Exit: 0 pass, 1 tests or compilation failed, 2 could not run.
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

module="" filter="" android=0 vps=0
while (($#)); do
  case "$1" in
    --android) android=1; shift ;;
    --vps) vps=1; shift ;;
    -h | --help) sed -n '2,17p' "$0"; exit 0 ;;
    -*) oc_usage "t0.sh <module> [test filter] [--android] [--vps]" ;;
    *) if [[ -z "$module" ]]; then module="$1"; else filter="$1"; fi; shift ;;
  esac
done
[[ -n "$module" ]] || oc_usage "t0.sh <module> [test filter] [--android] [--vps]"
module="$(oc_norm_module "$module")"
[[ -f "$(oc_module_dir "$module")/build.gradle.kts" ]] || oc_die2 "no such module: $module"

oc_run_init native-t0 "${OC_RUN_ID:-$(date +%Y%m%d-%H%M%S)-${module//:/_}}"

mapfile -t tasks < <(oc_module_test_tasks "$module")
task="${tasks[0]}"
if ((android)); then
  task=""
  for t in "${tasks[@]}"; do [[ "$t" == *AndroidHostTest ]] && task=testAndroidHostTest; done
  [[ -n "$task" ]] || { oc_say "module $module has no Android host tests (no src/androidHostTest)"; oc_status 2; exit 2; }
fi
args=("$module:$task")
if [[ -n "$filter" ]]; then
  [[ "$filter" == *'*'* ]] || filter="*$filter*"
  args+=(--tests "$filter")
fi

if ((vps)); then
  oc_step gradle "$OC_TOOL_DIR/vps_gradle.sh" -- "${args[@]}"; rc=$?
else
  oc_check_jdk || { oc_status 2; exit 2; }
  oc_check_memory || { oc_say "memory: below the floor, not starting Gradle (plan 02 §7.8)"; oc_status 2; exit 2; }
  lock="$(oc_build_lock_file)"
  oc_say "lock: build slot $lock"
  oc_step gradle oc_with_lock "$lock" "$OC_NATIVE/gradlew" -p "$OC_NATIVE" --console=plain ${OC_GRADLE_ARGS:-} "${args[@]}"
  rc=$?
  ((rc == 75)) && { oc_say "lock: build slot busy for ${OC_LOCK_WAIT:-1800} s"; oc_status 2; exit 2; }
fi

if ((rc != 0)); then
  oc_status "$(oc_gradle_class "$OC_RUN_DIR/gradle.log")"
  missing="$(oc_gradle_missing_tasks "$OC_RUN_DIR/gradle.log")"
  [[ -n "$missing" ]] && oc_say "missing: $missing"
  grep -E "^e: |No tests found for given includes" "$OC_RUN_DIR/gradle.log" | head -10 | sed 's/^/  /' | tee -a "$OC_SUMMARY"
fi
fails="$(oc_failing_tests)"
[[ -n "$fails" ]] && { oc_say "failing tests:"; sed 's/^/  /' <<<"$fails" | tee -a "$OC_SUMMARY"; }
oc_say "tests run: $(oc_tests_count)"
oc_report_paths | sed 's/^/report: /' | tee -a "$OC_SUMMARY"
exit "$OC_STATUS"
