#!/usr/bin/env bash
# Run Gradle on the VPS. A single remote lease covers sync, every build and all result collection.
#
#   vps_gradle.sh [--unit U] [--no-sync] [--lock-file F] [--min-disk-gb N] [--dry-run] -- <gradle args>
#   vps_gradle.sh --batch-file <steps.tsv> --results-dir <local dir> [options]
#
# Default workspace: a worktree name plus a hash of its absolute path (explicit --unit also supported).
# APKs use its separate -apk workspace, synchronized before first use even in a batch. --no-sync requires an
# identical source digest already on the VPS. The global build lock is always held; --lock-file adds exclusion
# (T2 holds its extra lock for the whole batch). Only the selected recording tasks' src/screenshots are returned.
# Host comes from OC_VPS_HOST and is never printed. Exit 0 pass, 1 Gradle failed, 2 could not run/collect results.
set -uo pipefail
source "$(dirname "$0")/lib/common.sh"

unit="" sync=1 lock_file='~/.oc-native-build.lock' min_disk=10 dry=0 batch="" results=""
while (($#)); do
  case "$1" in
    --unit) unit="${2:-}"; shift 2 ;;
    --no-sync) sync=0; shift ;;
    --lock-file) lock_file="${2:-}"; shift 2 ;;
    --min-disk-gb) min_disk="${2:-}"; shift 2 ;;
    --batch-file) batch="${2:-}"; shift 2 ;;
    --results-dir) results="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    --) shift; break ;;
    -h | --help) sed -n '2,11p' "$0"; exit 0 ;;
    *) oc_usage "vps_gradle.sh [options] -- <gradle args> | --batch-file F --results-dir D" ;;
  esac
done
if [[ -n "$batch" ]]; then
  [[ -f "$batch" && -n "$results" ]] || oc_die2 "batch needs a steps file and results directory"
else
  (($#)) || oc_usage "vps_gradle.sh ... -- <gradle args>"
fi
[[ "$unit" =~ ^[A-Za-z0-9._-]*$ && "$min_disk" =~ ^[0-9]+$ && "$lock_file" =~ ^[A-Za-z0-9_./~-]+$ ]] ||
  oc_die2 "invalid unit, disk floor or lock path"
if [[ -z "$unit" ]]; then
  top="$(oc_repo_root)" || oc_die2 "cannot identify worktree"
  wt="$(printf '%s' "${top##*/}" | tr -c 'A-Za-z0-9._-' '-')"
  unit="${wt#ocn-}-$(printf '%s' "$top" | sha256sum | cut -c1-12)"
fi
host="${OC_VPS_HOST:-}"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=15)
source_id="$(python3 -I "$OC_TOOL_DIR/lib/source_id.py" "$OC_NATIVE")" || oc_die2 "cannot hash source inputs"

workspace() {
  remote_dir="~/wt/ocn-$unit"
  local a
  for a in "$@"; do
    if [[ "$a" =~ (^|:)(assemble|bundle)[A-Za-z]*$ ]]; then remote_dir+="-apk"; break; fi
  done
  rsync_dir="${remote_dir#\~/}"
}
if ((dry)); then
  workspace "$@"
  echo "dry run (host from \$OC_VPS_HOST; never printed): lease global build lock and $lock_file"
  echo "  synchronize $remote_dir/native/ under the lease; verify source digest"
  echo "  ./gradlew $(oc_quote "$@") in $remote_dir/native/"
  echo "  collect reports and selected recorded screenshots before releasing the lease"
  exit 0
fi
[[ -n "$host" ]] || oc_die2 "OC_VPS_HOST is not set"
command -v rsync >/dev/null || oc_die2 "rsync is not installed"

# Keep one SSH connection alive holding the locks until the final local pull completes. All the subsequent
# connections are inside this lease. A failed acquisition must never sync source files.
lease_cmd="set -e; exec 8>~/.oc-native-build.lock; flock -w ${OC_LOCK_WAIT:-1800} -E 75 8 || exit 75;"
if [[ "$lock_file" != '~/.oc-native-build.lock' ]]; then
  lease_cmd+=" exec 9>$lock_file; flock -w ${OC_LOCK_WAIT:-1800} -E 75 9 || exit 75;"
fi
lease_cmd+=$' echo OC_LOCKED\nwhile IFS= read -r command; do\n  [[ "$command" == release ]] && exit 0\n  rc=0\n  bash -c "$command" || rc=$?\n  printf "\\nOC_COMMAND_DONE %s\\n" "$rc"\ndone'
coproc OC_LEASE { ssh "${ssh_opts[@]}" "$host" "$lease_cmd" 2>/dev/null; }
lease_pid=$OC_LEASE_PID
exec {lease_input}>&"${OC_LEASE[1]}"
exec {lease_output}<&"${OC_LEASE[0]}"
release_lease() {
  local rc=$?
  trap - EXIT
  trap '' PIPE
  printf 'release\n' >&"$lease_input" 2>/dev/null || true
  exec {lease_input}>&-
  wait "$lease_pid" 2>/dev/null || true
  exit "$rc"
}
trap release_lease EXIT
trap 'exit 2' INT TERM HUP
read -r ready <&"$lease_output" && [[ "$ready" == OC_LOCKED ]] || oc_die2 "VPS lock unavailable or ssh failed"
# Commands run as children of the remote lock owner. They inherit its lock descriptors, so even a lost SSH
# connection cannot free the build slot while a Gradle child is still running.
lease_run() {
  local line
  printf '%s\n' "$1" >&"$lease_input" || return 2
  while IFS= read -r line <&"$lease_output"; do
    if [[ "$line" =~ ^OC_COMMAND_DONE\ ([0-9]+)$ ]]; then return "${BASH_REMATCH[1]}"; fi
    printf '%s\n' "$line"
  done
  return 2
}
lease_run "avail=\$(df -Pk \"\$HOME\" | awk 'NR==2{print int(\$4/1048576)}'); [ \"\$avail\" -ge $min_disk ]" \
  2>/dev/null || oc_die2 "VPS disk check failed"

declare -A prepared=()
prepare_workspace() {
  [[ -n "${prepared[$remote_dir]:-}" ]] && return 0
  if ((sync)); then
    echo "vps: syncing source to $remote_dir/native under the lease"
    lease_run "mkdir -p $remote_dir/native && rm -f $remote_dir/.oc-source-id" 2>/dev/null || return 2
    rsync -az --delete -e "ssh ${ssh_opts[*]}" --exclude build/ --exclude .gradle/ --exclude .kotlin/ \
      --exclude local.properties --exclude key.properties "$OC_NATIVE/" "$host:$rsync_dir/native/" \
      >/dev/null 2>&1 || return 2
    [[ "$(python3 -I "$OC_TOOL_DIR/lib/source_id.py" "$OC_NATIVE")" == "$source_id" ]] || return 2
    lease_run "test \"\$(python3 -I $remote_dir/native/tool/lib/source_id.py $remote_dir/native)\" = $source_id && echo $source_id >$remote_dir/.oc-source-id" 2>/dev/null || return 2
  else
    lease_run "test \"\$(cat $remote_dir/.oc-source-id)\" = $source_id && test \"\$(python3 -I $remote_dir/native/tool/lib/source_id.py $remote_dir/native)\" = $source_id" \
      2>/dev/null || { echo "vps: unsynchronized workspace"; return 2; }
  fi
  prepared[$remote_dir]=1
}

run_tasks() {
  workspace "$@"
  prepare_workspace || { echo "vps: source synchronization/identity failed"; return 2; }
  local gradle_cmd rc a module module_dir
  gradle_cmd="$(oc_quote ./gradlew --console=plain "$@")"
  echo "vps: $gradle_cmd (in $remote_dir/native, lease held)"
  lease_run "cd $remote_dir/native && $gradle_cmd"
  rc=$?
  ((rc == 255)) && rc=2
  ((rc == 0 || rc == 2)) || rc=1
  rsync -rlz -e "ssh ${ssh_opts[*]}" --prune-empty-dirs --include='*/' \
    --include='**/build/test-results/**' --include='**/build/reports/**' --exclude='*' \
    "$host:$rsync_dir/native/" "$OC_NATIVE/" >/dev/null 2>&1 || { echo "vps: report retrieval failed"; return 2; }
  if ((rc == 0)); then
    for a in "$@"; do
      if [[ "$a" =~ ^(:[A-Za-z0-9_-]+)+:recordRoborazziAndroidHostTest$ ]]; then
        module="${a%:recordRoborazziAndroidHostTest}"; module_dir="${module#:}"; module_dir="${module_dir//://}"
        mkdir -p "$OC_NATIVE/$module_dir/src/screenshots" || return 2
        rsync -rlz --delete -e "ssh ${ssh_opts[*]}" \
          "$host:$rsync_dir/native/$module_dir/src/screenshots/" "$OC_NATIVE/$module_dir/src/screenshots/" \
          >/dev/null 2>&1 || { echo "vps: recorded screenshot retrieval failed"; return 2; }
      fi
    done
  fi
  return "$rc"
}

status=0
if [[ -n "$batch" ]]; then
  mkdir -p "$results" || oc_die2 "cannot create batch results"
  while IFS=$'\t' read -r name args_s; do
    [[ "$name" =~ ^[A-Za-z0-9_-]+$ && -n "$args_s" ]] || oc_die2 "invalid batch step"
    read -r -a args <<<"$args_s"
    run_tasks "${args[@]}" >"$results/$name.log" 2>&1; rc=$?
    echo "$rc" >"$results/$name.status"
    echo "vps: step $name exit $rc"
    ((rc > status)) && status=$rc
  done <"$batch"
else
  run_tasks "$@"; status=$?
fi
# Golden recording intentionally changes local inputs. Other runs must retain their exact source identity.
if [[ -z "$batch" && " $* " == *:recordRoborazziAndroidHostTest* ]]; then
  :
elif [[ "$(python3 -I "$OC_TOOL_DIR/lib/source_id.py" "$OC_NATIVE")" != "$source_id" ]]; then
  echo "vps: local source changed during the run"; status=2
fi
exit "$status"
