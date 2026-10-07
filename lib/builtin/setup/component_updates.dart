/// Pinned component activation. All paths come from app-owned install scripts.
/// Journals contain only an activation state; no credentials or project data.
/// The helpers define no work until called. Native next-process recovery is
/// handed to the BuiltinLinux owner in docs/design/BC4-contract.md.
const componentUpdatePrelude = r'''
oc_update_exists() { [ -e "$1" ] || [ -L "$1" ]; }
oc_update_failure() {
  echo '[oc] A component update could not be restored. Run setup again.' >&2
  return 1
}
oc_update_target_ok() {
  case "$1" in /*) [ "$1" != / ] ;; *) return 1 ;; esac
}
oc_update_recover() (
  oc_update_target_ok "$1" || { oc_update_failure; exit 1; }
  oc_target=$1
  oc_pending="$oc_target.oc-pending"
  oc_good="$oc_target.oc-good"
  oc_update_exists "$oc_pending" || exit 0
  [ -f "$oc_pending" ] && [ ! -L "$oc_pending" ] || { oc_update_failure; exit 1; }
  oc_state=$(cat "$oc_pending") || { oc_update_failure; exit 1; }
  case "$oc_state" in
    existing)
      if oc_update_exists "$oc_good"; then
        rm -rf -- "$oc_target" || { oc_update_failure; exit 1; }
        mv -T -- "$oc_good" "$oc_target" || { oc_update_failure; exit 1; }
      else
        # Either publication had not moved the old target yet, or a previous
        # recovery restored it and was interrupted before clearing the marker.
        oc_update_exists "$oc_target" || { oc_update_failure; exit 1; }
      fi ;;
    new)
      rm -rf -- "$oc_target" || { oc_update_failure; exit 1; } ;;
    *) oc_update_failure; exit 1 ;;
  esac
  sync -f "$(dirname "$oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "$oc_pending" || { oc_update_failure; exit 1; }
  # The restored target was flushed before this commit point. Repeating an
  # old marker after a power loss remains safe; power-loss qualification is
  # separate from the process-interruption proof.
  sync -f "$(dirname "$oc_target")" 2>/dev/null || true
)
oc_update_activate() (
  [ "$#" = 2 ] && oc_update_target_ok "$1" && oc_update_target_ok "$2" &&
    [ "$1" != "$2" ] || { oc_update_failure; exit 1; }
  oc_target=$1 oc_candidate=$2
  oc_pending="$oc_target.oc-pending"
  oc_good="$oc_target.oc-good"
  oc_update_recover "$oc_target" || exit 1
  oc_update_exists "$oc_candidate" || { oc_update_failure; exit 1; }
  # No journal is active here, so the current target is still the last good
  # install even if preparation stops while removing an older backup.
  rm -rf -- "$oc_good" || { oc_update_failure; exit 1; }
  oc_state=new
  oc_update_exists "$oc_target" && oc_state=existing
  (umask 077; printf '%s\n' "$oc_state" > "$oc_pending.new") || { oc_update_failure; exit 1; }
  sync -f "$oc_pending.new" 2>/dev/null || { oc_update_failure; exit 1; }
  mv -T -- "$oc_pending.new" "$oc_pending" || { oc_update_failure; exit 1; }
  sync -f "$(dirname "$oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  if [ "$oc_state" = existing ]; then
    mv -T -- "$oc_target" "$oc_good" || { oc_update_failure; exit 1; }
  fi
  mv -T -- "$oc_candidate" "$oc_target" || { oc_update_failure; exit 1; }
)
oc_update_commit() (
  oc_update_target_ok "$1" || { oc_update_failure; exit 1; }
  oc_target=$1
  oc_pending="$oc_target.oc-pending"
  [ -f "$oc_pending" ] && [ ! -L "$oc_pending" ] &&
    oc_update_exists "$oc_target" || { oc_update_failure; exit 1; }
  # Caller has probed the active program, not merely the staging candidate.
  # Flush all target changes before deleting the recovery receipt.
  sync -f "$oc_target" 2>/dev/null &&
    sync -f "$(dirname "$oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "$oc_pending" || { oc_update_failure; exit 1; }
  sync -f "$(dirname "$oc_target")" 2>/dev/null || true
  # Keep one .oc-good generation. Multi-target callers commit code before
  # the launch link: a killed link commit can always return to usable code.
)
''';
