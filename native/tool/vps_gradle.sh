#!/usr/bin/env bash
# Run Gradle tasks on the VPS (plan 02 §7.3 "VPS copy", §7.8).
#
#   native/tool/vps_gradle.sh [--unit <U>] [--no-sync] [--lock-file <remote path>] [--min-disk-gb <n>] [--dry-run]
#                             -- <gradle args...>
#
# 1. rsyncs this worktree's native/ (uncommitted edits included; build outputs, .gradle and local.properties excluded
#    and kept on the VPS) to ~/wt/ocn-<U>/native on $OC_VPS_HOST. A run whose tasks build an APK or bundle uses
#    ~/wt/ocn-<U>-apk instead: never build an APK in a directory a test run is syncing into (shared-emulator-contention).
# 2. runs `./gradlew --console=plain <args>` there under `flock -w` on the lock file (default ~/.oc-native-build.lock;
#    t2.sh passes ~/.oc-native-t2.lock) after checking free disk (default >= 10 GB; t2.sh asks 30);
# 3. pulls build/test-results and build/reports back into the local tree, so the caller's summary can name failures.
# The VPS copy has no usable .git: everything that needs git (touched modules, write set) runs on the PC first and is
# passed in as arguments. The host comes only from $OC_VPS_HOST (an ssh destination or alias) and is never printed.
# Unit: --unit, else the worktree folder name without "ocn-", else "adhoc".
# Exit: 0 tasks passed, 1 Gradle failed, 2 could not run (no host, unreachable, lock timeout, disk, rsync).
# shellcheck disable=SC2088 # "~/..." paths are expanded on the VPS, not here
set -uo pipefail
# shellcheck source=lib/common.sh
source "$(dirname "$0")/lib/common.sh"

unit="" sync=1 lock_file='~/.oc-native-build.lock' min_disk=10 dry=0
while (($#)); do
  case "$1" in
    --unit) unit="${2:-}"; shift 2 ;;
    --no-sync) sync=0; shift ;;
    --lock-file) lock_file="${2:-}"; shift 2 ;;
    --min-disk-gb) min_disk="${2:-}"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    --) shift; break ;;
    -h | --help) sed -n '2,18p' "$0"; exit 0 ;;
    *) oc_usage "vps_gradle.sh [--unit U] [--no-sync] [--lock-file F] [--min-disk-gb N] [--dry-run] -- <gradle args>" ;;
  esac
done
(($#)) || oc_usage "vps_gradle.sh ... -- <gradle args>"
[[ "$unit" =~ ^[A-Za-z0-9._-]*$ ]] || oc_die2 "bad unit name: $unit"

if [[ -z "$unit" ]]; then
  wt="$(basename "$(oc_repo_root 2>/dev/null || dirname "$OC_NATIVE")")"
  unit="${wt#ocn-}"; [[ "$wt" == ocn-* ]] || unit=adhoc
fi
remote_dir="~/wt/ocn-$unit"
for a in "$@"; do
  if [[ "$a" =~ (^|:)(assemble|bundle)[A-Za-z]*$ ]]; then remote_dir="~/wt/ocn-$unit-apk"; break; fi
done

host="${OC_VPS_HOST:-}"
gradle_cmd="$(oc_quote ./gradlew --console=plain "$@")"
remote_cmd="set -e; d=$remote_dir/native; avail=\$(df -Pk \"\$HOME\" | awk 'NR==2{print int(\$4/1048576)}');
echo \"vps: \${avail} GB free (need >= $min_disk)\"; [ \"\$avail\" -ge $min_disk ] || exit 76;
cd \"\$d\" && flock -w ${OC_LOCK_WAIT:-1800} -E 75 $lock_file $gradle_cmd"

if ((dry)); then
  echo "dry run (host from \$OC_VPS_HOST, $([[ -n "$host" ]] && echo set || echo UNSET); never printed):"
  ((sync)) && echo "  rsync -az --delete --exclude build/ --exclude .gradle/ --exclude .kotlin/ --exclude local.properties native/ \$VPS:$remote_dir/native/"
  echo "  ssh \$VPS '$remote_cmd'"
  echo "  rsync -rlz \$VPS:$remote_dir/native/ native/ (only **/build/test-results/** and **/build/reports/**)"
  exit 0
fi
[[ -n "$host" ]] || oc_die2 "OC_VPS_HOST is not set (an ssh destination; keep the address out of git)"
command -v rsync >/dev/null || oc_die2 "rsync is not installed"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=15)
ssh "${ssh_opts[@]}" "$host" true 2>/dev/null || oc_die2 "the VPS is unreachable (ssh \$OC_VPS_HOST failed)"

if ((sync)); then
  echo "vps: syncing native/ to $remote_dir/native"
  ssh "${ssh_opts[@]}" "$host" "mkdir -p $remote_dir/native" || oc_die2 "cannot create $remote_dir on the VPS"
  rsync -az --delete -e "ssh ${ssh_opts[*]}" --exclude build/ --exclude .gradle/ --exclude .kotlin/ \
    --exclude local.properties --exclude key.properties "$OC_NATIVE/" "$host:$remote_dir/native/" ||
    oc_die2 "rsync to the VPS failed"
fi

echo "vps: $gradle_cmd (in $remote_dir/native, lock $lock_file)"
ssh "${ssh_opts[@]}" "$host" "$remote_cmd"
rc=$?

echo "vps: pulling test results and reports"
rsync -rlz -e "ssh ${ssh_opts[*]}" --prune-empty-dirs --include='*/' \
  --include='**/build/test-results/**' --include='**/build/reports/**' --exclude='*' \
  "$host:$remote_dir/native/" "$OC_NATIVE/" 2>/dev/null || echo "vps: warning: could not pull reports"

case $rc in
  0) exit 0 ;;
  75) echo "vps: lock $lock_file not free within ${OC_LOCK_WAIT:-1800} s" >&2; exit 2 ;;
  76) echo "vps: not enough free disk" >&2; exit 2 ;;
  255) echo "vps: ssh failed" >&2; exit 2 ;;
  *) exit 1 ;;
esac
