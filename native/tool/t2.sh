#!/usr/bin/env bash
# T2 (plan 02 §8): the coordinator's integration gate, on the VPS, at checkpoints and on every candidate.
#
#   native/tool/t2.sh [--where vps|local] [--steps a,b] [--detach] [--dry-run]
#
# Runs the steps in tool/ladder/t2.tasks in order (all JVM and Robolectric tests with -Poc.werror=true, Konsist and the
# repository gates in :quality, Roborazzi verify, lint, both release assembles, the R8 config analysis, the catalog
# report). A task Gradle cannot find is MISSING and fails the run: a gate that does not exist yet is not a pass.
# Refuses to start without ~/.oc-native-t2.lock (one T2 at a time) and >= $OC_T2_MIN_DISK_GB (30) GB free disk
# (plan 02 §7.8). --steps runs a subset for debugging; such a run is PARTIAL and never a T2 result.
# A clean full pass on a committed tree prints the line for native/LAST_GREEN (the coordinator writes it, §9.3).
# Default --where: vps when $OC_VPS_HOST is set, else local. Detached with --detach: wait for build/native-t2/<id>/exit-code.
# Exit: 0 pass, 1 a step failed or is missing, 2 could not run.
# shellcheck disable=SC2088 # "~/..." paths are expanded on the VPS, not here
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"
oc_maybe_detach native-t2 "$@"

where="" only="" dry=0
while (($#)); do
  case "$1" in
    --where) where="${2:-}"; shift 2 ;;
    --steps) only="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h | --help) sed -n '2,15p' "$0"; exit 0 ;;
    *) oc_usage "t2.sh [--where vps|local] [--steps a,b] [--detach] [--dry-run]" ;;
  esac
done
where="${where:-$([[ -n "${OC_VPS_HOST:-}" ]] && echo vps || echo local)}"
min_disk="${OC_T2_MIN_DISK_GB:-30}"

top="$(oc_repo_root)" || oc_die2 "t2 runs from a git checkout (it records the tree it ran on)"
sha="$(git -C "$top" rev-parse HEAD)"
dirty=""; [[ -n "$(git -C "$top" status --porcelain)" ]] && dirty="-dirty"
oc_run_init native-t2 "${OC_RUN_ID:-${sha:0:12}$dirty-$(date +%Y%m%d-%H%M%S)}"
oc_say "tree: $sha$dirty; where: $where"

mapfile -t lines < <(grep -vE '^\s*(#|$)' "$OC_TOOL_DIR/ladder/t2.tasks")
total=${#lines[@]} ran=0 failed=() missing=()

if ((!dry)); then
  if [[ "$where" == local ]]; then
    exec {t2fd}>"$HOME/.oc-native-t2.lock"
    flock -w "${OC_LOCK_WAIT:-1800}" "$t2fd" || { oc_say "lock: ~/.oc-native-t2.lock busy (one T2 at a time)"; oc_status 2; exit 2; }
    oc_say "lock: ~/.oc-native-t2.lock held"
    oc_check_disk "$OC_NATIVE" "$min_disk" || { oc_status 2; exit 2; }
    oc_check_jdk || { oc_status 2; exit 2; }
    oc_check_memory || { oc_status 2; exit 2; }
  else
    batch="$OC_RUN_DIR/steps.tsv"
    : >"$batch"
    for line in "${lines[@]}"; do
      name="${line%%$'\t'*}"
      [[ -n "$only" && ",$only," != *",$name,"* ]] && continue
      printf '%s\n' "$line" >>"$batch"
    done
    oc_step vps-ladder "$OC_TOOL_DIR/vps_gradle.sh" --lock-file '~/.oc-native-t2.lock' \
      --min-disk-gb "$min_disk" --batch-file "$batch" --results-dir "$OC_RUN_DIR"
    oc_status "$?"
  fi
fi

for line in "${lines[@]}"; do
  name="${line%%$'\t'*}"; args_s="${line#*$'\t'}"
  if [[ -n "$only" && ",$only," != *",$name,"* ]]; then continue; fi
  read -r -a args <<<"$args_s"
  ran=$((ran + 1))
  if ((dry)); then oc_say "step $name: ./gradlew $args_s (dry run)"; continue; fi
  if [[ "$where" == vps ]]; then
    if [[ -f "$OC_RUN_DIR/$name.status" ]]; then
      rc="$(cat "$OC_RUN_DIR/$name.status")"
    else
      oc_say "vps: no completion for $name"; oc_status 2; continue
    fi
  else
    oc_step "$name" oc_with_lock "$(oc_build_lock_file)" "$OC_TOOL_DIR/lib/gradlew.sh" "${args[@]}"
    rc=$?
  fi
  ((rc == 0)) && continue
  if ((rc == 75)); then oc_say "lock: build slot busy"; oc_status 2; continue; fi
  m="$(oc_gradle_missing_tasks "$OC_RUN_DIR/$name.log")"
  if [[ -n "$m" ]]; then
    missing+=("$name"); oc_say "  MISSING: $m"; oc_status 1
  else
    failed+=("$name"); oc_status "$(oc_gradle_class "$OC_RUN_DIR/$name.log")"
    grep -E "^e: |FAILED$|What went wrong" -A1 "$OC_RUN_DIR/$name.log" | head -8 | sed 's/^/  /' | tee -a "$OC_SUMMARY"
  fi
done

oc_say "steps: $ran of $total run; failed: ${failed[*]:-none}; missing: ${missing[*]:-none}"
[[ -n "$only" ]] && oc_say "PARTIAL run (--steps $only): not a T2 result"
if ((!dry)); then
  fails="$(oc_failing_tests)"
  [[ -n "$fails" ]] && { oc_say "failing tests:"; sed 's/^/  /' <<<"$fails" | tee -a "$OC_SUMMARY"; }
  oc_say "tests run: $(oc_tests_count)"
  oc_report_paths | sed 's/^/report: /' | tee -a "$OC_SUMMARY"
  if [[ "$(git -C "$top" rev-parse HEAD)" != "$sha" || -n "$(git -C "$top" status --porcelain)" ]]; then
    oc_say "tree changed or dirty: LAST_GREEN withheld"
    dirty="-dirty"
  fi
  if ((OC_STATUS == 0)) && [[ -z "$only" && -z "$dirty" ]]; then
    oc_say "LAST_GREEN line: $sha $(date -u +%Y-%m-%d) build/native-t2/$(basename "$OC_RUN_DIR")/summary.txt parity=<from docs/native/parity.md>"
  fi
fi
exit "$OC_STATUS"
