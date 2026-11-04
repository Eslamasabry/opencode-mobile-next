package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of lib/builtin/setup/setup_scripts.dart (`setupPrelude`, `withSetupPrelude`, `combinedCheckScript`,
 * `parseCombinedChecks`) and lib/builtin/setup/component_updates.dart (`componentUpdatePrelude`), verbatim: the shell
 * helpers every install script runs with (`oc_stage`, `oc_version`, the resumable checked `oc_download`, apt helpers,
 * and the journaled `oc_update_activate` / `oc_update_recover` / `oc_update_commit`). POSIX `sh` (Ubuntu's dash).
 */
public object InstallPrelude {
    /** Flutter `setupPrelude`. Defines functions only. */
    public const val SETUP: String = """# oc setup prelude
oc_stage() { printf '::oc stage %s\n' "${'$'}*"; }
oc_version() { printf '::oc version %s\n' "${'$'}*"; }
oc_size_of() { stat -c %s "${'$'}1" 2>/dev/null || echo 0; }
oc_sha_ok() { [ "${'$'}(sha256sum "${'$'}1" 2>/dev/null | cut -d ' ' -f 1)" = "${'$'}2" ]; }
oc_fetch() {
  oc_rc_file="${'$'}oc_file.oc-rc"
  oc_http_file="${'$'}oc_file.oc-http"
  rm -f "${'$'}oc_rc_file" "${'$'}oc_http_file"
  (
    oc_r=0
    curl -fsSL --connect-timeout 20 \
      -C - -o "${'$'}oc_file" --write-out '%{http_code}' "${'$'}oc_url" > "${'$'}oc_http_file" || oc_r=${'$'}?
    echo "${'$'}oc_r" > "${'$'}oc_rc_file"
  ) &
  while [ ! -s "${'$'}oc_rc_file" ]; do
    printf '::oc bytes %s %s\n' "${'$'}(oc_size_of "${'$'}oc_file")" "${'$'}oc_total"
    sleep "${'$'}{OC_POLL_SECONDS:-0.5}"
  done
  wait "${'$'}!" 2>/dev/null || true
  oc_rc=${'$'}(cat "${'$'}oc_rc_file")
  oc_http=${'$'}(cat "${'$'}oc_http_file")
  rm -f "${'$'}oc_rc_file" "${'$'}oc_http_file"
}
oc_download() {
  oc_url=${'$'}1 oc_file=${'$'}2 oc_sha=${'$'}3 oc_total=${'$'}{4:-0}
  mkdir -p "${'$'}(dirname "${'$'}oc_file")"
  if [ -s "${'$'}oc_file" ] && oc_sha_ok "${'$'}oc_file" "${'$'}oc_sha"; then
    oc_have=${'$'}(oc_size_of "${'$'}oc_file")
    printf '::oc bytes %s %s\n' "${'$'}oc_have" "${'$'}oc_have"
    return 0
  fi
  if [ "${'$'}oc_total" = 0 ]; then
    oc_total=${'$'}(curl -fsSIL --retry 3 --connect-timeout 20 "${'$'}oc_url" 2>/dev/null |
      tr -d '\r' |
      awk 'tolower(${'$'}1) == "content-length:" { n = ${'$'}2 } END { print n + 0 }') ||
      oc_total=0
  fi
  case ${'$'}oc_total in ''|*[!0-9]*) oc_total=0 ;; esac
  # A partial file as long as the whole download, yet with the wrong
  # checksum, cannot be resumed into anything good.
  if [ "${'$'}oc_total" -gt 0 ] && [ "${'$'}(oc_size_of "${'$'}oc_file")" -ge "${'$'}oc_total" ]; then
    rm -f "${'$'}oc_file"
  fi
  oc_fetch
  # Each attempt starts from the CURRENT file size. curl's built-in --retry
  # can rewind to the size at its first attempt, discarding bytes it received.
  # Partial transfers (18), network failures and transient HTTP failures are
  # retried here, preserving progress across attempts.
  oc_try=1
  while [ "${'$'}oc_try" -le 4 ]; do
    case ${'$'}oc_rc in
      6|7|18|28|35|52|55|56|92) ;;
      22) case ${'$'}oc_http in 408|429|500|502|503|504) ;; *) break ;; esac ;;
      *) break ;;
    esac
    echo "[oc] The connection dropped; trying again (${'$'}oc_try of 4)"
    sleep "${'$'}{OC_RETRY_SECONDS:-${'$'}((oc_try * 5))}"
    oc_fetch
    oc_try=${'$'}((oc_try + 1))
  done
  # Never silently erase resumable progress if a server refuses Range.
  if [ "${'$'}oc_rc" = 33 ] || [ "${'$'}oc_rc" = 36 ]; then
    echo "[oc] The download server could not resume this download. Retry later." >&2
    return "${'$'}oc_rc"
  fi
  if [ "${'$'}oc_rc" != 0 ]; then
    echo "[oc] Download failed (curl exit ${'$'}oc_rc): ${'$'}oc_url" >&2
    return "${'$'}oc_rc"
  fi
  oc_have=${'$'}(oc_size_of "${'$'}oc_file")
  [ "${'$'}oc_total" -gt 0 ] || oc_total=${'$'}oc_have
  printf '::oc bytes %s %s\n' "${'$'}oc_have" "${'$'}oc_total"
  if ! oc_sha_ok "${'$'}oc_file" "${'$'}oc_sha"; then
    rm -f "${'$'}oc_file"
    echo "[oc] The download did not match its checksum and was deleted: ${'$'}oc_url" >&2
    return 1
  fi
}
oc_apt_status() {
  # A label the caller already announced is not announced again.
  oc_label=${'$'}1 oc_last=${'$'}1 oc_last_pct=''
  while IFS= read -r oc_line; do
    case ${'$'}oc_line in
      dlstatus:*) oc_now=${'$'}{oc_label:-Downloading packages} ;;
      pmstatus:*) oc_now=${'$'}{oc_label:-Installing packages} ;;
      *) continue ;;
    esac
    # dlstatus:<n>:<pct>:<text> and pmstatus:<pkg[:arch]>:<pct>:<text>:
    # the percent is the first number after the second field.
    oc_rest=${'$'}{oc_line#*:}
    oc_rest=${'$'}{oc_rest#*:}
    oc_pct=''
    while [ -n "${'$'}oc_rest" ]; do
      oc_field=${'$'}{oc_rest%%:*}
      case ${'$'}oc_field in
        ''|*[!0-9.]*) ;;
        *) oc_pct=${'$'}{oc_field%%.*}; break ;;
      esac
      [ "${'$'}oc_rest" != "${'$'}{oc_rest#*:}" ] || break
      oc_rest=${'$'}{oc_rest#*:}
    done
    if [ "${'$'}oc_now" != "${'$'}oc_last" ]; then
      printf '::oc stage %s\n' "${'$'}oc_now"
      oc_last=${'$'}oc_now oc_last_pct=''
    fi
    if [ -n "${'$'}oc_pct" ] && [ "${'$'}oc_pct" != "${'$'}oc_last_pct" ]; then
      printf '::oc percent %s\n' "${'$'}oc_pct"
      oc_last_pct=${'$'}oc_pct
    fi
  done
}
oc_apt_run() {
  oc_apt_label=${'$'}1
  shift
  oc_apt_rc_file=${'$'}(mktemp)
  {
    {
      oc_r=0
      apt-get -o APT::Status-Fd=3 "${'$'}@" 3>&1 1>&4 2>&4 || oc_r=${'$'}?
      echo "${'$'}oc_r" > "${'$'}oc_apt_rc_file"
    } | oc_apt_status "${'$'}oc_apt_label"
  } 4>&1
  oc_rc=${'$'}(cat "${'$'}oc_apt_rc_file")
  rm -f "${'$'}oc_apt_rc_file"
  return "${'$'}{oc_rc:-1}"
}
# The overrides exist for the script tests only.
oc_apt_stamp=${'$'}{OC_APT_STAMP:-/var/lib/oc-setup/apt-updated}
oc_apt_lists=${'$'}{OC_APT_LISTS:-/var/lib/apt/lists}
oc_apt_update() {
  oc_stage 'Updating package lists'
  # Explicit returns: callers use it after ||, where set -e is off.
  oc_apt_run 'Updating package lists' update -y -o Acquire::Retries=5 || return
  mkdir -p "${'$'}(dirname "${'$'}oc_apt_stamp")"
  touch "${'$'}oc_apt_stamp"
}
oc_apt_lists_fresh() {
  [ -n "${'$'}(find "${'$'}oc_apt_stamp" -mmin -1440 2>/dev/null)" ] &&
    ls "${'$'}oc_apt_lists"/*_Packages* >/dev/null 2>&1
}
oc_apt_failure() {
  oc_failure_rc=${'$'}1 oc_failure_message=${'$'}2
  echo '[oc] Details: dpkg --audit'
  dpkg --audit 2>&1 || true
  echo "[oc] ${'$'}oc_failure_message"
  return "${'$'}oc_failure_rc"
}
oc_apt_reinstall_required() {
  # A killed unpack can leave a package marked reinstreq even though
  # configure -a succeeds. Dependency repair alone does not unpack it again.
  # Select exact installed package versions from dpkg's machine-readable
  # status, never package names parsed out of its human-readable audit.
  oc_reinstall_status=${'$'}(dpkg-query -W -f='${'$'}{binary:Package}\t${'$'}{Status}\t${'$'}{Version}\n' 2>&1) || {
    oc_apt_failure 1 'Package repair could not finish. The damaged packages could not be checked. Retry setup.'
    return 1
  }
  oc_reinstall_packages=${'$'}(printf '%s\n' "${'$'}oc_reinstall_status" | awk -F '\t' '
    ${'$'}2 ~ /^[^ ]+ reinstreq / {
      if (${'$'}1 !~ /^[a-z0-9][a-z0-9+.-]*(:[a-z0-9][a-z0-9-]*)?${'$'}/ ||
          ${'$'}3 !~ /^[0-9][0-9A-Za-z.+:~_-]*${'$'}/) exit 1
      print ${'$'}1 "=" ${'$'}3
    }') || {
    oc_apt_failure 1 'Package repair could not finish. The damaged packages could not be checked. Retry setup.'
    return 1
  }
  [ -n "${'$'}oc_reinstall_packages" ] || return 0
  oc_stage 'Repairing interrupted package installation'
  if oc_apt_lists_fresh || oc_apt_update; then
    :
  else
    oc_apt_reinstall_rc=${'$'}?
    oc_apt_failure "${'$'}oc_apt_reinstall_rc" \
      'Package lists could not be updated. Check your connection and retry setup.' || return
  fi
  # Tokens were validated above. Keep the version already recorded by dpkg;
  # never remove packages to make a repair succeed.
  set -- ${'$'}oc_reinstall_packages
  if oc_apt_run 'Repairing interrupted package installation' \
    --reinstall --no-remove install -y --no-install-recommends \
    -o Acquire::Retries=5 "${'$'}@"; then
    :
  # An upgrade stopped mid-unpack records the old version, which the
  # archive may no longer carry (perl-base 0.4 over 0.6 on a fresh
  # phone): reinstall the same packages at the version apt offers.
  elif set -- ${'$'}(printf '%s\n' "${'$'}oc_reinstall_packages" | sed 's/=.*//') &&
    oc_apt_run 'Repairing interrupted package installation' \
      --reinstall --no-remove install -y --no-install-recommends \
      -o Acquire::Retries=5 "${'$'}@"; then
    :
  else
    oc_apt_reinstall_rc=${'$'}?
    oc_apt_failure "${'$'}oc_apt_reinstall_rc" \
      'Package repair could not finish. The damaged packages could not be reinstalled. Check your connection and retry setup.' || return
  fi
  dpkg --configure -a || {
    oc_apt_failure 1 'Package repair could not finish. Retry setup to repair the interrupted installation.'
    return 1
  }
}
oc_apt_repair() {
  # Keep healthy runs quiet. A failed configure needs apt's dependency repair,
  # not an ignored error that poisons every later component install.
  if ! dpkg --configure -a >/dev/null 2>&1; then
    oc_stage 'Repairing interrupted package installation'
    if oc_apt_lists_fresh || oc_apt_update; then
      :
    else
      oc_apt_repair_rc=${'$'}?
      oc_apt_failure "${'$'}oc_apt_repair_rc" \
        'Package repair could not finish. Check your connection and retry setup.' || return
    fi
    if oc_apt_run 'Repairing interrupted package installation' \
      -f install -y --no-install-recommends; then
      :
    else
      oc_apt_repair_rc=${'$'}?
      oc_apt_failure "${'$'}oc_apt_repair_rc" \
        'Package repair could not finish. Retry setup to repair the interrupted installation.' || return
    fi
    if dpkg --configure -a; then
      :
    else
      oc_apt_repair_rc=${'$'}?
      oc_apt_failure "${'$'}oc_apt_repair_rc" \
        'Package repair could not finish. Retry setup to repair the interrupted installation.' || return
    fi
  fi
  # dpkg --audit can report unfinished packages while returning success.
  oc_apt_audit_rc=0
  oc_apt_audit=${'$'}(dpkg --audit 2>&1) || oc_apt_audit_rc=${'$'}?
  if [ "${'$'}oc_apt_audit_rc" = 0 ] && [ -n "${'$'}oc_apt_audit" ]; then
    # Keep the original finding in Details even when reinstallation fails.
    echo '[oc] Details: dpkg --audit'
    printf '%s\n' "${'$'}oc_apt_audit"
    oc_apt_reinstall_required || return
    oc_apt_audit=${'$'}(dpkg --audit 2>&1) || oc_apt_audit_rc=${'$'}?
  fi
  if [ "${'$'}oc_apt_audit_rc" != 0 ] || [ -n "${'$'}oc_apt_audit" ]; then
    echo '[oc] Details: dpkg --audit'
    [ -z "${'$'}oc_apt_audit" ] || printf '%s\n' "${'$'}oc_apt_audit"
    echo '[oc] Some packages are still unfinished. Retry setup to repair the interrupted installation.'
    return 1
  fi
}
oc_apt_install() {
  export DEBIAN_FRONTEND=noninteractive
  oc_apt_repair || return
  if oc_apt_lists_fresh || oc_apt_update; then
    :
  else
    oc_apt_install_rc=${'$'}?
    oc_apt_failure "${'$'}oc_apt_install_rc" \
      'Package lists could not be updated. Check your connection and retry setup.' || return
  fi
  if ! oc_apt_run '' install -y --no-install-recommends \
    -o Acquire::Retries=5 "${'$'}@"; then
    if oc_apt_update; then
      :
    else
      oc_apt_install_rc=${'$'}?
      oc_apt_failure "${'$'}oc_apt_install_rc" \
        'Package lists could not be updated. Check your connection and retry setup.' || return
    fi
    if oc_apt_run '' install -y --no-install-recommends \
      -o Acquire::Retries=5 "${'$'}@"; then
      :
    else
      oc_apt_install_rc=${'$'}?
      oc_apt_failure "${'$'}oc_apt_install_rc" \
        'The packages could not be installed. Check your connection and retry setup.' || return
    fi
  fi
  for oc_apt_package in "${'$'}@"; do
    oc_apt_package_rc=0
    oc_apt_package_status=${'$'}(dpkg -s "${'$'}oc_apt_package" 2>&1) || oc_apt_package_rc=${'$'}?
    if [ "${'$'}oc_apt_package_rc" != 0 ] ||
      ! printf '%s\n' "${'$'}oc_apt_package_status" | grep -qx 'Status: install ok installed'; then
      printf '[oc] Details: dpkg -s %s\n%s\n' "${'$'}oc_apt_package" "${'$'}oc_apt_package_status"
      oc_apt_failure 1 \
        'A required package is not fully installed. Retry setup to finish installing it.' || return
    fi
  done
}
"""

    /** Flutter `componentUpdatePrelude`: pinned component activation journals (`*.oc-pending`, `*.oc-good`). */
    public const val COMPONENT_UPDATE: String = """oc_update_exists() { [ -e "${'$'}1" ] || [ -L "${'$'}1" ]; }
oc_update_failure() {
  echo '[oc] A component update could not be restored. Run setup again.' >&2
  return 1
}
oc_update_target_ok() {
  case "${'$'}1" in /*) [ "${'$'}1" != / ] ;; *) return 1 ;; esac
}
oc_update_recover() (
  oc_update_target_ok "${'$'}1" || { oc_update_failure; exit 1; }
  oc_target=${'$'}1
  oc_pending="${'$'}oc_target.oc-pending"
  oc_good="${'$'}oc_target.oc-good"
  oc_update_exists "${'$'}oc_pending" || exit 0
  [ -f "${'$'}oc_pending" ] && [ ! -L "${'$'}oc_pending" ] || { oc_update_failure; exit 1; }
  oc_state=${'$'}(cat "${'$'}oc_pending") || { oc_update_failure; exit 1; }
  case "${'$'}oc_state" in
    existing)
      if oc_update_exists "${'$'}oc_good"; then
        rm -rf -- "${'$'}oc_target" || { oc_update_failure; exit 1; }
        mv -T -- "${'$'}oc_good" "${'$'}oc_target" || { oc_update_failure; exit 1; }
      else
        # Either publication had not moved the old target yet, or a previous
        # recovery restored it and was interrupted before clearing the marker.
        oc_update_exists "${'$'}oc_target" || { oc_update_failure; exit 1; }
      fi ;;
    new)
      rm -rf -- "${'$'}oc_target" || { oc_update_failure; exit 1; } ;;
    *) oc_update_failure; exit 1 ;;
  esac
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  # The restored target was flushed before this commit point. Repeating an
  # old marker after a power loss remains safe; power-loss qualification is
  # separate from the process-interruption proof.
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || true
)
oc_update_activate() (
  [ "${'$'}#" = 2 ] && oc_update_target_ok "${'$'}1" && oc_update_target_ok "${'$'}2" &&
    [ "${'$'}1" != "${'$'}2" ] || { oc_update_failure; exit 1; }
  oc_target=${'$'}1 oc_candidate=${'$'}2
  oc_pending="${'$'}oc_target.oc-pending"
  oc_good="${'$'}oc_target.oc-good"
  oc_update_recover "${'$'}oc_target" || exit 1
  oc_update_exists "${'$'}oc_candidate" || { oc_update_failure; exit 1; }
  # No journal is active here, so the current target is still the last good
  # install even if preparation stops while removing an older backup.
  rm -rf -- "${'$'}oc_good" || { oc_update_failure; exit 1; }
  oc_state=new
  oc_update_exists "${'$'}oc_target" && oc_state=existing
  (umask 077; printf '%s\n' "${'$'}oc_state" > "${'$'}oc_pending.new") || { oc_update_failure; exit 1; }
  sync -f "${'$'}oc_pending.new" 2>/dev/null || { oc_update_failure; exit 1; }
  mv -T -- "${'$'}oc_pending.new" "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  if [ "${'$'}oc_state" = existing ]; then
    mv -T -- "${'$'}oc_target" "${'$'}oc_good" || { oc_update_failure; exit 1; }
  fi
  mv -T -- "${'$'}oc_candidate" "${'$'}oc_target" || { oc_update_failure; exit 1; }
)
oc_update_commit() (
  oc_update_target_ok "${'$'}1" || { oc_update_failure; exit 1; }
  oc_target=${'$'}1
  oc_pending="${'$'}oc_target.oc-pending"
  [ -f "${'$'}oc_pending" ] && [ ! -L "${'$'}oc_pending" ] &&
    oc_update_exists "${'$'}oc_target" || { oc_update_failure; exit 1; }
  # Caller has probed the active program, not merely the staging candidate.
  # Flush all target changes before deleting the recovery receipt.
  sync -f "${'$'}oc_target" 2>/dev/null &&
    sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || true
  # Keep one .oc-good generation. Multi-target callers commit code before
  # the launch link: a killed link commit can always return to usable code.
)
"""

    /** Flutter `withSetupPrelude`: [script] as the job runs it, after both preludes. */
    public fun withPrelude(script: String): String = SETUP + COMPONENT_UPDATE + script

    /**
     * Flutter `combinedCheckScript`: every check in its own subshell, marked where it starts and how it ended, so one
     * proot start answers them all.
     */
    public fun combinedCheckScript(checks: Map<String, String>): String =
        buildString {
            for ((id, check) in checks) {
                append("printf '\\n::oc-check-begin %s\\n' '").append(id).append("'\n")
                append("(\n")
                append(check.trimEnd()).append('\n')
                append(") 2>/dev/null </dev/null\n")
                append("printf '\\n::oc-check-end %s %s\\n' '").append(id).append("' \"${'$'}?\"\n")
            }
        }

    /** One check's result: whether it passed and the version on its last non-empty line. */
    public data class CheckResult(val ok: Boolean, val version: String?)

    /** Flutter `parseCombinedChecks`: a check with no end marker (the script died) did not pass and is absent. */
    public fun parseCombinedChecks(output: String): Map<String, CheckResult> {
        val results = linkedMapOf<String, CheckResult>()
        var current: String? = null
        var lines = mutableListOf<String>()
        for (raw in output.split('\n')) {
            val line = raw.trimEnd()
            if (line.startsWith("::oc-check-begin ")) {
                current = line.removePrefix("::oc-check-begin ").trim()
                lines = mutableListOf()
                continue
            }
            if (line.startsWith("::oc-check-end ")) {
                val parts = line.removePrefix("::oc-check-end ").trim().split(' ')
                if (parts.size == 2 && parts[0] == current) {
                    val version = lines.lastOrNull { it.isNotBlank() }?.trim()
                    results[current] = CheckResult(ok = parts[1] == "0", version = version?.ifEmpty { null })
                }
                current = null
                continue
            }
            if (current != null) lines += line
        }
        return results
    }
}
