/// The shell side of the phone setup engine
/// (docs/design/phone-setup-v2-2026-09-24.md, "Progress protocol").
///
/// Every install script runs with [setupPrelude] in front of it. The prelude
/// only defines functions, so a script that never calls them behaves as if it
/// were alone. Scripts report with `::oc` lines on stdout; everything else
/// they print is log text for the details view.
library;

import 'component_updates.dart';

/// Helpers for install scripts. POSIX sh on purpose: the job runs each
/// script with Ubuntu's `/bin/sh` (dash), and the script tests run it in
/// both dash and bash on the PC.
///
/// - `oc_stage LABEL…` and `oc_version TEXT` print their `::oc` line.
/// - `oc_download URL FILE SHA256 [BYTES]`: curl into FILE, resuming a partial FILE
///   with `-C -`, printing `::oc bytes <done> <total>` about twice a second
///   from the file's size against the server's Content-Length. A finished
///   file is checked against SHA256 and deleted when it does not match, so a
///   bad file is never resumed. An intact FILE from an earlier run is kept
///   and not downloaded again. When curl itself dies from a signal (exit 128
///   or more, e.g. the SIGSEGV of issue #117 under some proot/ROM pairs) the
///   download is handed on, in this order: the app downloads it with
///   Android's own networking (`::oc fetch URL FILE SHA256`, answered by the
///   `FILE.oc-fetch-ack` and `FILE.oc-fetch-done` marker files), then
///   `curl --http1.1`, then `wget`. The checksum is checked afterwards in
///   every case.
/// - `oc_untar ARCHIVE DEST STRIP [MEMBER]`: tar -xzf; when tar dies from a
///   signal the app unpacks it (`::oc unpack`, see SetupAppUnpack.kt).
/// - `oc_apt_install PKG…`: `apt-get install` whose own status stream
///   (`APT::Status-Fd`) becomes `::oc stage` + `::oc percent`. The package
///   lists are refreshed only when our stamp is older than a day, and once
///   more if the install fails on stale lists. Interrupted package operations
///   are repaired first, and each requested package is verified with `dpkg -s`.
///
/// The polling loop waits for a result file instead of `kill -0`: dash does
/// not reap a finished background job until `wait`, so `kill -0` on it keeps
/// succeeding and the loop would never end.
const setupPrelude = r'''# oc setup prelude
oc_stage() { printf '::oc stage %s\n' "$*"; }
oc_version() { printf '::oc version %s\n' "$*"; }
oc_size_of() { stat -c %s "$1" 2>/dev/null || echo 0; }
oc_sha_ok() { [ "$(sha256sum "$1" 2>/dev/null | cut -d ' ' -f 1)" = "$2" ]; }
oc_fetch() {
  oc_rc_file="$oc_file.oc-rc"
  oc_http_file="$oc_file.oc-http"
  rm -f "$oc_rc_file" "$oc_http_file"
  (
    oc_r=0
    case ${oc_tool:-curl} in
      wget)
        wget -q -c --timeout=20 -O "$oc_file" "$oc_url" || oc_r=$?
        echo 200 > "$oc_http_file" ;;
      *)
        curl -fsSL --connect-timeout 20 ${oc_curl_opts:-} \
          -C - -o "$oc_file" --write-out '%{http_code}' "$oc_url" > "$oc_http_file" || oc_r=$? ;;
    esac
    echo "$oc_r" > "$oc_rc_file"
  ) &
  while [ ! -s "$oc_rc_file" ]; do
    printf '::oc bytes %s %s\n' "$(oc_size_of "$oc_file")" "$oc_total"
    sleep "${OC_POLL_SECONDS:-0.5}"
  done
  wait "$!" 2>/dev/null || true
  oc_rc=$(cat "$oc_rc_file")
  oc_http=$(cat "$oc_http_file")
  rm -f "$oc_rc_file" "$oc_http_file"
}
# Asks the app to download $oc_url into $oc_file. The app answers with two
# marker files next to FILE: -ack at once (an app that does not know the
# request never writes it, so we give up after OC_ACK_POLLS polls), then
# -done holding "ok" or "fail <reason>". Progress is the app's own
# `::oc bytes` lines.
oc_app_fetch() {
  case $oc_file in /*) oc_abs=$oc_file ;; *) oc_abs=$(pwd)/$oc_file ;; esac
  rm -f "$oc_abs.oc-fetch-ack" "$oc_abs.oc-fetch-done"
  printf '::oc fetch %s %s %s\n' "$oc_url" "$oc_abs" "$oc_sha"
  oc_ack_left=${OC_ACK_POLLS:-20}
  while [ ! -e "$oc_abs.oc-fetch-ack" ] && [ "$oc_ack_left" -gt 0 ]; do
    sleep "${OC_POLL_SECONDS:-0.5}"
    oc_ack_left=$((oc_ack_left - 1))
  done
  if [ ! -e "$oc_abs.oc-fetch-ack" ]; then
    echo "[oc] The app did not take over the download."
    return 1
  fi
  while [ ! -s "$oc_abs.oc-fetch-done" ]; do
    sleep "${OC_POLL_SECONDS:-0.5}"
  done
  oc_answer=$(cat "$oc_abs.oc-fetch-done")
  rm -f "$oc_abs.oc-fetch-ack" "$oc_abs.oc-fetch-done"
  [ "$oc_answer" = ok ] || echo "[oc] The app could not download it: ${oc_answer#fail }"
  [ "$oc_answer" = ok ]
}
# curl was killed by a signal. Same download by other means; the checksum is
# still checked by the caller.
oc_crash_fallback() {
  echo "[oc] The download tool crashed (signal $((oc_rc - 128))). Trying another way."
  if oc_app_fetch; then oc_rc=0; return 0; fi
  oc_tool=curl oc_curl_opts=--http1.1
  oc_fetch
  if [ "$oc_rc" -ge 128 ] && command -v wget >/dev/null 2>&1; then
    oc_tool=wget oc_curl_opts=
    oc_fetch
  fi
  oc_tool=curl oc_curl_opts=
}
# Asks the app to unpack the .tar.gz $oc_ar into $oc_dest (`::oc unpack`).
# Same handshake as oc_app_fetch, with marker files next to the archive.
oc_app_unpack() {
  case $oc_ar in /*) oc_abs=$oc_ar ;; *) oc_abs=$(pwd)/$oc_ar ;; esac
  case $oc_dest in /*) oc_dabs=$oc_dest ;; *) oc_dabs=$(pwd)/$oc_dest ;; esac
  rm -f "$oc_abs.oc-unpack-ack" "$oc_abs.oc-unpack-done"
  printf '::oc unpack %s %s %s %s\n' "$oc_abs" "$oc_dabs" "$oc_strip" "${oc_mem:--}"
  oc_ack_left=${OC_ACK_POLLS:-20}
  while [ ! -e "$oc_abs.oc-unpack-ack" ] && [ "$oc_ack_left" -gt 0 ]; do
    sleep "${OC_POLL_SECONDS:-0.5}"
    oc_ack_left=$((oc_ack_left - 1))
  done
  if [ ! -e "$oc_abs.oc-unpack-ack" ]; then
    echo "[oc] The app did not take over the unpacking."
    return 1
  fi
  while [ ! -s "$oc_abs.oc-unpack-done" ]; do
    sleep "${OC_POLL_SECONDS:-0.5}"
  done
  oc_answer=$(cat "$oc_abs.oc-unpack-done")
  rm -f "$oc_abs.oc-unpack-ack" "$oc_abs.oc-unpack-done"
  [ "$oc_answer" = ok ] || echo "[oc] The app could not unpack it: ${oc_answer#fail }"
  [ "$oc_answer" = ok ]
}
# oc_untar ARCHIVE DEST STRIP [MEMBER]: `tar -xzf ARCHIVE -C DEST`, removing STRIP
# leading path parts and, with MEMBER, extracting only that path. ARCHIVE has
# already passed its pinned checksum (oc_download). When tar dies from a signal
# (exit 128 or more, e.g. the SIGSEGV of issue #117) the app unpacks it
# instead. Any other tar failure is returned as it is.
oc_untar() {
  oc_ar=$1 oc_dest=$2 oc_strip=${3:-0} oc_mem=${4:-}
  oc_tar_rc=0
  if [ -n "$oc_mem" ]; then
    tar -xzf "$oc_ar" -C "$oc_dest" --strip-components="$oc_strip" "$oc_mem" || oc_tar_rc=$?
  else
    tar -xzf "$oc_ar" -C "$oc_dest" --strip-components="$oc_strip" || oc_tar_rc=$?
  fi
  if [ "$oc_tar_rc" -ge 128 ]; then
    echo "[oc] The unpacking tool crashed (signal $((oc_tar_rc - 128))). Trying another way."
    if oc_app_unpack; then return 0; fi
    echo "[oc] Unpacking failed: the unpacking tool crashed and the app could not do it: $oc_ar" >&2
  fi
  return "$oc_tar_rc"
}
oc_download() {
  oc_url=$1 oc_file=$2 oc_sha=$3 oc_total=${4:-0}
  mkdir -p "$(dirname "$oc_file")"
  if [ -s "$oc_file" ] && oc_sha_ok "$oc_file" "$oc_sha"; then
    oc_have=$(oc_size_of "$oc_file")
    printf '::oc bytes %s %s\n' "$oc_have" "$oc_have"
    return 0
  fi
  oc_probe_rc=0
  if [ "$oc_total" = 0 ]; then
    oc_head=$(curl -fsSIL --retry 3 --connect-timeout 20 "$oc_url" 2>/dev/null &&
      echo oc-rc:0 || echo "oc-rc:$?") || true
    oc_probe_rc=${oc_head##*oc-rc:}
    case $oc_probe_rc in ''|*[!0-9]*) oc_probe_rc=0 ;; esac
    oc_total=$(printf '%s\n' "$oc_head" | tr -d '\r' |
      awk 'tolower($1) == "content-length:" { n = $2 } END { print n + 0 }') ||
      oc_total=0
  fi
  case $oc_total in ''|*[!0-9]*) oc_total=0 ;; esac
  # A partial file as long as the whole download, yet with the wrong
  # checksum, cannot be resumed into anything good.
  if [ "$oc_total" -gt 0 ] && [ "$(oc_size_of "$oc_file")" -ge "$oc_total" ]; then
    rm -f "$oc_file"
  fi
  if [ "$oc_probe_rc" -ge 128 ]; then
    # curl already died on the size probe; do not run it again.
    oc_rc=$oc_probe_rc
  else
    oc_fetch
  fi
  # Each attempt starts from the CURRENT file size. curl's built-in --retry
  # can rewind to the size at its first attempt, discarding bytes it received.
  # Partial transfers (18), network failures and transient HTTP failures are
  # retried here, preserving progress across attempts.
  oc_try=1
  while [ "$oc_try" -le 4 ] && [ "$oc_rc" -lt 128 ]; do
    case $oc_rc in
      6|7|18|28|35|52|55|56|92) ;;
      22) case $oc_http in 408|429|500|502|503|504) ;; *) break ;; esac ;;
      *) break ;;
    esac
    echo "[oc] The connection dropped; trying again ($oc_try of 4)"
    sleep "${OC_RETRY_SECONDS:-$((oc_try * 5))}"
    oc_fetch
    oc_try=$((oc_try + 1))
  done
  if [ "$oc_rc" -ge 128 ]; then
    oc_crash_fallback
  fi
  # Never silently erase resumable progress if a server refuses Range.
  if [ "$oc_rc" = 33 ] || [ "$oc_rc" = 36 ]; then
    echo "[oc] The download server could not resume this download. Retry later." >&2
    return "$oc_rc"
  fi
  if [ "$oc_rc" -ge 128 ]; then
    echo "[oc] Download failed: the download tool crashed (signal $((oc_rc - 128))) and the other ways did not work: $oc_url" >&2
    return "$oc_rc"
  fi
  if [ "$oc_rc" != 0 ]; then
    echo "[oc] Download failed (curl exit $oc_rc): $oc_url" >&2
    return "$oc_rc"
  fi
  oc_have=$(oc_size_of "$oc_file")
  [ "$oc_total" -gt 0 ] || oc_total=$oc_have
  printf '::oc bytes %s %s\n' "$oc_have" "$oc_total"
  if ! oc_sha_ok "$oc_file" "$oc_sha"; then
    rm -f "$oc_file"
    echo "[oc] The download did not match its checksum and was deleted: $oc_url" >&2
    return 1
  fi
}
oc_apt_status() {
  # A label the caller already announced is not announced again.
  oc_label=$1 oc_last=$1 oc_last_pct=''
  while IFS= read -r oc_line; do
    case $oc_line in
      dlstatus:*) oc_now=${oc_label:-Downloading packages} ;;
      pmstatus:*) oc_now=${oc_label:-Installing packages} ;;
      *) continue ;;
    esac
    # dlstatus:<n>:<pct>:<text> and pmstatus:<pkg[:arch]>:<pct>:<text>:
    # the percent is the first number after the second field.
    oc_rest=${oc_line#*:}
    oc_rest=${oc_rest#*:}
    oc_pct=''
    while [ -n "$oc_rest" ]; do
      oc_field=${oc_rest%%:*}
      case $oc_field in
        ''|*[!0-9.]*) ;;
        *) oc_pct=${oc_field%%.*}; break ;;
      esac
      [ "$oc_rest" != "${oc_rest#*:}" ] || break
      oc_rest=${oc_rest#*:}
    done
    if [ "$oc_now" != "$oc_last" ]; then
      printf '::oc stage %s\n' "$oc_now"
      oc_last=$oc_now oc_last_pct=''
    fi
    if [ -n "$oc_pct" ] && [ "$oc_pct" != "$oc_last_pct" ]; then
      printf '::oc percent %s\n' "$oc_pct"
      oc_last_pct=$oc_pct
    fi
  done
}
oc_apt_run() {
  oc_apt_label=$1
  shift
  oc_apt_rc_file=$(mktemp)
  {
    {
      oc_r=0
      apt-get -o APT::Status-Fd=3 "$@" 3>&1 1>&4 2>&4 || oc_r=$?
      echo "$oc_r" > "$oc_apt_rc_file"
    } | oc_apt_status "$oc_apt_label"
  } 4>&1
  oc_rc=$(cat "$oc_apt_rc_file")
  rm -f "$oc_apt_rc_file"
  return "${oc_rc:-1}"
}
# The overrides exist for the script tests only.
oc_apt_stamp=${OC_APT_STAMP:-/var/lib/oc-setup/apt-updated}
oc_apt_lists=${OC_APT_LISTS:-/var/lib/apt/lists}
oc_apt_update() {
  oc_stage 'Updating package lists'
  # Explicit returns: callers use it after ||, where set -e is off.
  oc_apt_run 'Updating package lists' update -y -o Acquire::Retries=5 || return
  mkdir -p "$(dirname "$oc_apt_stamp")"
  touch "$oc_apt_stamp"
}
oc_apt_lists_fresh() {
  [ -n "$(find "$oc_apt_stamp" -mmin -1440 2>/dev/null)" ] &&
    ls "$oc_apt_lists"/*_Packages* >/dev/null 2>&1
}
oc_apt_failure() {
  oc_failure_rc=$1 oc_failure_message=$2
  echo '[oc] Details: dpkg --audit'
  dpkg --audit 2>&1 || true
  echo "[oc] $oc_failure_message"
  return "$oc_failure_rc"
}
oc_apt_reinstall_required() {
  # A killed unpack can leave a package marked reinstreq even though
  # configure -a succeeds. Dependency repair alone does not unpack it again.
  # Select exact installed package versions from dpkg's machine-readable
  # status, never package names parsed out of its human-readable audit.
  oc_reinstall_status=$(dpkg-query -W -f='${binary:Package}\t${Status}\t${Version}\n' 2>&1) || {
    oc_apt_failure 1 'Package repair could not finish. The damaged packages could not be checked. Retry setup.'
    return 1
  }
  oc_reinstall_packages=$(printf '%s\n' "$oc_reinstall_status" | awk -F '\t' '
    $2 ~ /^[^ ]+ reinstreq / {
      if ($1 !~ /^[a-z0-9][a-z0-9+.-]*(:[a-z0-9][a-z0-9-]*)?$/ ||
          $3 !~ /^[0-9][0-9A-Za-z.+:~_-]*$/) exit 1
      print $1 "=" $3
    }') || {
    oc_apt_failure 1 'Package repair could not finish. The damaged packages could not be checked. Retry setup.'
    return 1
  }
  [ -n "$oc_reinstall_packages" ] || return 0
  oc_stage 'Repairing interrupted package installation'
  if oc_apt_lists_fresh || oc_apt_update; then
    :
  else
    oc_apt_reinstall_rc=$?
    oc_apt_failure "$oc_apt_reinstall_rc" \
      'Package lists could not be updated. Check your connection and retry setup.' || return
  fi
  # Tokens were validated above. Keep the version already recorded by dpkg;
  # never remove packages to make a repair succeed.
  set -- $oc_reinstall_packages
  if oc_apt_run 'Repairing interrupted package installation' \
    --reinstall --no-remove install -y --no-install-recommends \
    -o Acquire::Retries=5 "$@"; then
    :
  # An upgrade stopped mid-unpack records the old version, which the
  # archive may no longer carry (perl-base 0.4 over 0.6 on a fresh
  # phone): reinstall the same packages at the version apt offers.
  elif set -- $(printf '%s\n' "$oc_reinstall_packages" | sed 's/=.*//') &&
    oc_apt_run 'Repairing interrupted package installation' \
      --reinstall --no-remove install -y --no-install-recommends \
      -o Acquire::Retries=5 "$@"; then
    :
  else
    oc_apt_reinstall_rc=$?
    oc_apt_failure "$oc_apt_reinstall_rc" \
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
      oc_apt_repair_rc=$?
      oc_apt_failure "$oc_apt_repair_rc" \
        'Package repair could not finish. Check your connection and retry setup.' || return
    fi
    if oc_apt_run 'Repairing interrupted package installation' \
      -f install -y --no-install-recommends; then
      :
    else
      oc_apt_repair_rc=$?
      oc_apt_failure "$oc_apt_repair_rc" \
        'Package repair could not finish. Retry setup to repair the interrupted installation.' || return
    fi
    if dpkg --configure -a; then
      :
    else
      oc_apt_repair_rc=$?
      oc_apt_failure "$oc_apt_repair_rc" \
        'Package repair could not finish. Retry setup to repair the interrupted installation.' || return
    fi
  fi
  # dpkg --audit can report unfinished packages while returning success.
  oc_apt_audit_rc=0
  oc_apt_audit=$(dpkg --audit 2>&1) || oc_apt_audit_rc=$?
  if [ "$oc_apt_audit_rc" = 0 ] && [ -n "$oc_apt_audit" ]; then
    # Keep the original finding in Details even when reinstallation fails.
    echo '[oc] Details: dpkg --audit'
    printf '%s\n' "$oc_apt_audit"
    oc_apt_reinstall_required || return
    oc_apt_audit=$(dpkg --audit 2>&1) || oc_apt_audit_rc=$?
  fi
  if [ "$oc_apt_audit_rc" != 0 ] || [ -n "$oc_apt_audit" ]; then
    echo '[oc] Details: dpkg --audit'
    [ -z "$oc_apt_audit" ] || printf '%s\n' "$oc_apt_audit"
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
    oc_apt_install_rc=$?
    oc_apt_failure "$oc_apt_install_rc" \
      'Package lists could not be updated. Check your connection and retry setup.' || return
  fi
  if ! oc_apt_run '' install -y --no-install-recommends \
    -o Acquire::Retries=5 "$@"; then
    if oc_apt_update; then
      :
    else
      oc_apt_install_rc=$?
      oc_apt_failure "$oc_apt_install_rc" \
        'Package lists could not be updated. Check your connection and retry setup.' || return
    fi
    if oc_apt_run '' install -y --no-install-recommends \
      -o Acquire::Retries=5 "$@"; then
      :
    else
      oc_apt_install_rc=$?
      oc_apt_failure "$oc_apt_install_rc" \
        'The packages could not be installed. Check your connection and retry setup.' || return
    fi
  fi
  for oc_apt_package in "$@"; do
    oc_apt_package_rc=0
    oc_apt_package_status=$(dpkg -s "$oc_apt_package" 2>&1) || oc_apt_package_rc=$?
    if [ "$oc_apt_package_rc" != 0 ] ||
      ! printf '%s\n' "$oc_apt_package_status" | grep -qx 'Status: install ok installed'; then
      printf '[oc] Details: dpkg -s %s\n%s\n' "$oc_apt_package" "$oc_apt_package_status"
      oc_apt_failure 1 \
        'A required package is not fully installed. Retry setup to finish installing it.' || return
    fi
  done
}
''';

/// [script] as the job runs it: the prelude, then the script.
String withSetupPrelude(String script) =>
    '$setupPrelude$componentUpdatePrelude$script';

/// One script that runs every check in [checks] (id → check script) in its
/// own subshell and marks where each starts and how it ended, so the engine
/// pays for one proot start instead of one per component.
String combinedCheckScript(Map<String, String> checks) {
  final buffer = StringBuffer();
  for (final entry in checks.entries) {
    buffer
      ..writeln("printf '\\n::oc-check-begin %s\\n' '${entry.key}'")
      ..writeln('(')
      ..writeln(entry.value.trimRight())
      ..writeln(') 2>/dev/null </dev/null')
      ..writeln("printf '\\n::oc-check-end %s %s\\n' '${entry.key}' \"\$?\"");
  }
  return buffer.toString();
}

/// The result of one check in [combinedCheckScript]'s output.
typedef SetupCheckResult = ({bool ok, String? version});

/// Reads [combinedCheckScript]'s output: which checks passed and the version
/// each printed on its last line. A check with no end marker (the script
/// died) did not pass.
Map<String, SetupCheckResult> parseCombinedChecks(String output) {
  final results = <String, SetupCheckResult>{};
  String? current;
  var lines = <String>[];
  for (final raw in output.split('\n')) {
    final line = raw.trimRight();
    if (line.startsWith('::oc-check-begin ')) {
      current = line.substring('::oc-check-begin '.length).trim();
      lines = [];
      continue;
    }
    if (line.startsWith('::oc-check-end ')) {
      final parts = line.substring('::oc-check-end '.length).trim().split(' ');
      if (parts.length == 2 && parts[0] == current) {
        final version = lines.lastWhere(
          (candidate) => candidate.trim().isNotEmpty,
          orElse: () => '',
        );
        results[current!] = (
          ok: parts[1] == '0',
          version: version.trim().isEmpty ? null : version.trim(),
        );
      }
      current = null;
      continue;
    }
    if (current != null) lines.add(line);
  }
  return results;
}
