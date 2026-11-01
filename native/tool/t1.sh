#!/usr/bin/env bash
# T1 (plan 02 §8): once when the logical change is ready, and after merging native/main into the branch.
#
#   native/tool/t1.sh [--base <ref>] [--brief <brief.md> | --globs '<write set>'] [--also <glob>]...
#                     [--record <module>] [--where vps|local] [--detach] [--dry-run]
#
# PC part (needs git): the modules touched since merge-base(<base>, HEAD) plus uncommitted and untracked files, and
# check_write_set.sh against the brief's write set (without --brief/--globs the summary says "write set: NOT checked").
# Then, for that module list (on the VPS through vps_gradle.sh, or locally with --where local; default: vps when
# $OC_VPS_HOST is set, else local, as in the cloud lane):
#   - each module's tests: desktopTest, testAndroidHostTest (or the Roborazzi verify task where the module records
#     goldens), `test` for JVM modules, overrides in tool/ladder/tasks.tsv;
#   - build-logic touched: `./gradlew -p build-logic test`;
#   - Konsist and the repository gates: `:quality:test` (always);
#   - strings touched (:ui:words, :tools:strings): the :tools:strings drift check;
#   - detekt on the touched modules when tool/qa/detekt.sh exists (plan 03 check 11), otherwise recorded as absent.
# --record <module> re-records that module's Roborazzi goldens (VPS only: the one golden host; OC_GOLDEN_HOST=1 marks
# a host as allowed). Base: --base, $OC_BASE, native/main, origin/native/main.
# Runs detached with --detach (plan 02 §8): wait for build/native-t1/<id>/exit-code.
# Exit: 0 pass, 1 a test or gate failed, 2 could not run. Lock: the build slot locally, the VPS lock remotely.
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
oc_maybe_detach native-t1 "$@"

base_arg="" brief="" globs="" also=() record="" where="" dry=0
while (($#)); do
  case "$1" in
    --base) base_arg="${2:-}"; shift 2 ;;
    --brief) brief="${2:-}"; shift 2 ;;
    --globs) globs="${2:-}"; shift 2 ;;
    --also) also+=(--also "${2:-}"); shift 2 ;;
    --record) record="$(oc_norm_module "${2:-}")"; shift 2 ;;
    --where) where="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,23p' "$0"; exit 0 ;;
    *) oc_usage "t1.sh [--base R] [--brief F | --globs G] [--also G] [--record M] [--where vps|local] [--detach] [--dry-run]" ;;
  esac
done
where="${where:-$([[ -n "${OC_VPS_HOST:-}" ]] && echo vps || echo local)}"
[[ "$where" == vps || "$where" == local ]] || oc_die2 "--where is vps or local"

top="$(oc_repo_root)" || oc_die2 "t1 needs git for its PC part (plan 02 §7.3)"
base="$(oc_resolve_base "$base_arg")" || oc_die2 "unknown base '${base_arg:-${OC_BASE:-native/main}}'"
head_short="$(git -C "$top" rev-parse --short=12 HEAD)"
dirty=""; [[ -n "$(git -C "$top" status --porcelain)" ]] && dirty="-dirty"
oc_run_init native-t1 "${OC_RUN_ID:-$head_short$dirty-$(date +%Y%m%d-%H%M%S)}"
oc_say "tree: HEAD $head_short$dirty; base $base (merge-base $(git -C "$top" merge-base "$base" HEAD | cut -c1-12)); where: $where"
[[ -n "$dirty" ]] && oc_say "note: uncommitted changes are included; this run counts only for this exact tree"

# --- PC part --------------------------------------------------------------------------------------------------------
mapfile -t changed < <(cd "$top" && oc_changed_files "$base")
mapfile -t modules < <(printf '%s\n' "${changed[@]}" | oc_paths_to_modules)
oc_say "changed paths: ${#changed[@]}; touched modules: ${modules[*]:-(none)}"

if [[ -n "$brief" || -n "$globs" ]]; then
  ws_args=(--base "$base" "${also[@]}")
  if [[ -n "$brief" ]]; then ws_args=("$brief" "${ws_args[@]}"); else ws_args=(--globs "$globs" "${ws_args[@]}"); fi
  oc_step write-set "$OC_TOOL_DIR/check_write_set.sh" "${ws_args[@]}"
  rc=$?
  tail -n 1 "$OC_RUN_DIR/write-set.log" | sed 's/^/  /' | tee -a "$OC_SUMMARY"
  grep '^OUTSIDE' "$OC_RUN_DIR/write-set.log" | sed 's/^/  /' | tee -a "$OC_SUMMARY"
  oc_status "$rc"
else
  oc_say "write set: NOT checked (no --brief or --globs; plan 15 §3.2 step 7 needs it before the report)"
fi

if [[ -n "$record" ]]; then
  if [[ "$where" != vps && "${OC_GOLDEN_HOST:-0}" != 1 ]]; then
    oc_say "record: refused; goldens are recorded only on the VPS (the one golden host)"; oc_status 2; exit 2
  fi
fi

# --- task list ------------------------------------------------------------------------------------------------------
tasks=() build_logic=0 strings=0
for m in "${modules[@]}"; do
  case "$m" in
    build-logic) build_logic=1; continue ;;
    :ui:words | :tools:strings) strings=1 ;;
  esac
  mode=""; [[ "$m" == "$record" ]] && mode=record
  while read -r t; do [[ -n "$t" ]] && tasks+=("$m:$t"); done < <(oc_module_test_tasks "$m" "$mode")
done
# shellcheck disable=SC2076 # a literal word match
[[ -n "$record" && ! " ${modules[*]} " =~ " $record " ]] &&
  while read -r t; do tasks+=("$record:$t"); done < <(oc_module_test_tasks "$record" record)
[[ -f "$OC_NATIVE/quality/build.gradle.kts" && ! " ${tasks[*]} " =~ " :quality:test " ]] && tasks+=(:quality:test)
oc_say "tasks: ${tasks[*]}"

if ((dry)); then oc_say "dry run: nothing started"; exit 0; fi

run_gradle() { # <step name> <gradle args...>
  local name="$1"; shift
  if [[ "$where" == vps ]]; then
    oc_step "$name" "$OC_TOOL_DIR/vps_gradle.sh" --no-sync -- "$@"
  else
    oc_step "$name" oc_with_lock "$(oc_build_lock_file)" "$OC_NATIVE/gradlew" -p "$OC_NATIVE" --console=plain \
      ${OC_GRADLE_ARGS:-} "$@"
  fi
  local rc=$?
  if ((rc == 75)); then oc_say "lock: build slot busy"; oc_status 2; return; fi
  if ((rc != 0)); then
    local cls; cls="$(oc_gradle_class "$OC_RUN_DIR/$name.log")"
    oc_status "$cls"
    oc_gradle_missing_tasks "$OC_RUN_DIR/$name.log" | sed 's/^/  missing: /' | tee -a "$OC_SUMMARY"
    grep -E "^e: " "$OC_RUN_DIR/$name.log" | head -10 | sed 's/^/  /' | tee -a "$OC_SUMMARY"
  fi
}

if [[ "$where" == vps ]]; then
  oc_step sync "$OC_TOOL_DIR/vps_gradle.sh" -- help -q || { oc_status 2; exit 2; }
else
  oc_check_jdk || { oc_status 2; exit 2; }
  oc_check_memory || { oc_say "memory: below the floor (plan 02 §7.8)"; oc_status 2; exit 2; }
fi

((build_logic)) && run_gradle build-logic -p build-logic test
run_gradle tests --continue "${tasks[@]}"
((strings)) && run_gradle strings-drift :tools:strings:run --args="check --repo .."

if [[ -x "$OC_NATIVE/tool/qa/detekt.sh" ]]; then
  oc_step detekt "$OC_NATIVE/tool/qa/detekt.sh" "${modules[@]}" || oc_status 1
else
  oc_say "detekt: not run (tool/qa/detekt.sh does not exist yet; plan 03 check 11, P-03-2)"
fi

fails="$(oc_failing_tests)"
[[ -n "$fails" ]] && { oc_say "failing tests:"; sed 's/^/  /' <<<"$fails" | tee -a "$OC_SUMMARY"; }
oc_say "tests run: $(oc_tests_count)"
oc_report_paths | sed 's/^/report: /' | tee -a "$OC_SUMMARY"
exit "$OC_STATUS"
