/// AI Team on this phone, Termux path: the scripts of `~/.oc/aiteam.sh`.
///
/// The team runs inside Termux's managed Ubuntu (proot-distro
/// `opencode-ubuntu`), next to the OpenCode server, from the same upstream
/// Linux builds of Gas City, Beads and Dolt the in-app Ubuntu installs
/// ([AiTeamPins]). Termux downloads the three archives with its own curl
/// (resumable, each failure named) and checks their SHA-256 before anything
/// reaches Ubuntu; everything else runs inside Ubuntu, from the in-app
/// team's own scripts:
/// - unpacking, the agents' `opencode`, the identity and the run-once check:
///   [AiTeamScripts.unpackScript];
/// - the team's store with the phone tuning, the projects with their
///   phone-side origin and its hook, the supervisor with the upkeep order
///   and its lock, registration: [BuiltinTeam].
///
/// What differs from the in-app team: the supervisor listens on Termux's
/// port [supervisorPort] (8372, as the Termux team always has; the in-app
/// team keeps 8472, so both can exist on one phone), and it runs as its own
/// long-lived `proot-distro login` started by `aiteam.sh`, not as the app's
/// service.
///
/// Before this, the Termux path ran Android builds of the three programs
/// natively in Termux, downloaded from a release of this project that was
/// never published (issue #87). `assets/aiteam/manifest*.json` described
/// those builds; nothing reads them any more.
library;

import '../builtin/setup/aiteam_scripts.dart';
import '../builtin/setup/setup_scripts.dart' show withSetupPrelude;
import '../builtin/team/builtin_team.dart';

abstract final class TermuxTeamScripts {
  /// The managed Ubuntu the OpenCode server runs in.
  static const proot = 'opencode-ubuntu';

  /// The Termux team's supervisor port (the in-app team uses 8472).
  static const supervisorPort = 8372;
  static const supervisorUrl = 'http://127.0.0.1:$supervisorPort';

  /// Inside Ubuntu: where `aiteam.sh` puts the scripts it runs there. They
  /// run from a file, never from stdin, where a program that reads its
  /// input would swallow the rest of the script.
  static const scriptsDir = '/root/.oc-aiteam';

  /// [BuiltinTeam.supervisorConfig] on [supervisorPort]: loopback only,
  /// loopback Host headers only.
  static String get supervisorConfig {
    const from = 'port = ${BuiltinTeam.port}\n';
    if (!BuiltinTeam.supervisorConfig.contains(from)) {
      throw StateError('BuiltinTeam.supervisorConfig names no port line');
    }
    return BuiltinTeam.supervisorConfig.replaceFirst(
      from,
      'port = $supervisorPort\n',
    );
  }

  static String _onTermuxPort(String script) {
    if (!script.contains(BuiltinTeam.supervisorConfig)) {
      throw StateError(
        'The in-app team script no longer writes its '
        'supervisor settings as BuiltinTeam.supervisorConfig',
      );
    }
    return script.replaceAll(BuiltinTeam.supervisorConfig, supervisorConfig);
  }

  /// The team's store, made once ([BuiltinTeam.cityScript]).
  static String get cityScript => _onTermuxPort(BuiltinTeam.cityScript);

  /// The supervisor ([BuiltinTeam.serviceScript]): tuning, hooks, `exec gc
  /// supervisor run`.
  static String get serviceScript => _onTermuxPort(BuiltinTeam.serviceScript);

  /// The Ubuntu packages the team needs, installed only when one is missing
  /// (so a set-up phone does not need the network for this step), with the
  /// in-app setup's own apt helper.
  static String get packagesScript => withSetupPrelude(
    'oc_missing=\n'
    'for oc_p in ${AiTeamScripts.ubuntuPackages}; do\n'
    '  dpkg -s "\$oc_p" >/dev/null 2>&1 || oc_missing="\$oc_missing \$oc_p"\n'
    'done\n'
    '[ -n "\$oc_missing" ] || exit 0\n'
    '# shellcheck disable=SC2086\n'
    'oc_apt_install \$oc_missing\n',
  );

  /// Unpacks the verified archives Termux moved into
  /// [AiTeamScripts.cache]; `gc_member`, `bd_member` and `dolt_member` come
  /// from the pins, through the environment.
  static String get unpackScript =>
      'set -eu\n'
      ': "\${gc_member:?}" "\${bd_member:?}" "\${dolt_member:?}"\n'
      '${AiTeamScripts.unpackScript}'
      'rm -rf ${AiTeamScripts.cache}\n';

  /// The scripts `aiteam.sh` carries, by the name it runs them under.
  static Map<String, String> get parts => {
    'check': AiTeamScripts.checkScript,
    'packages': packagesScript,
    'unpack': unpackScript,
    'city': cityScript,
    'service': serviceScript,
    'register': BuiltinTeam.registerScript,
  };

  /// The path the managed server gives [path]: a Termux-side path into
  /// the rootfs (an older version stored those) becomes the path inside
  /// Ubuntu.
  static String ubuntuPath(String path) {
    for (final marker in [
      '/containers/$proot/rootfs/',
      '/installed-rootfs/$proot/',
    ]) {
      final index = path.indexOf(marker);
      if (index >= 0) return '/${path.substring(index + marker.length)}';
    }
    return path;
  }

  /// `init`'s script for the project at [project] (a path inside Ubuntu):
  /// [BuiltinTeam.rigScript], headed by the project and the team's name for
  /// it, which `aiteam.sh init` checks before it runs it.
  static String rigFile(String project, {String? rig}) {
    final name = rig ?? BuiltinTeam.rigName(project);
    final script = BuiltinTeam.rigScript(project, name);
    return '# oc-project: $project\n# oc-rig: $name\n$script';
  }

  /// The pins file `aiteam.sh install` reads (`~/.oc/aiteam-pins`): the
  /// versions, then one line per CPU and program: arch, tool, URL, SHA-256,
  /// bytes, the program's path in the archive. [baseUrlOverride] is the
  /// in-app setup's test mirror (same checksums).
  static String pinsFile({
    List<AiTeamDownload> arm64 = AiTeamPins.arm64,
    List<AiTeamDownload> x64 = AiTeamPins.x64,
    String baseUrlOverride = AiTeamPins.baseUrlOverride,
  }) {
    final buffer = StringBuffer()
      ..writeln('gascity ${AiTeamPins.gascity}')
      ..writeln('beads ${AiTeamPins.beads}')
      ..writeln('dolt ${AiTeamPins.dolt}')
      ..writeln('pack ${AiTeamPins.packVersion}');
    for (final (arch, downloads) in [('arm64', arm64), ('x86_64', x64)]) {
      for (final download in downloads) {
        final url = baseUrlOverride.isEmpty
            ? download.url
            : '$baseUrlOverride${download.fileName}';
        final fields = [
          arch,
          download.tool,
          url,
          download.sha256,
          '${download.bytes}',
          download.member,
        ];
        if (fields.any(
          (field) => field.isEmpty || field.contains(RegExp(r'\s')),
        )) {
          throw ArgumentError.value(download.url, 'download');
        }
        buffer.writeln(fields.join(' '));
      }
    }
    return buffer.toString();
  }

  static const _partEnd = 'OC_AITEAM_PART';

  /// `aiteam.sh`: [_header] with the paths, the parts, then [_body].
  static String get aiteamScript {
    final buffer = StringBuffer()
      ..write(_header)
      ..writeln()
      ..writeln(
        '# The scripts that run inside Ubuntu (TermuxTeamScripts.parts).',
      )
      ..writeln('team_part() {')
      ..writeln('  case "\$1" in');
    for (final entry in parts.entries) {
      if (entry.value.split('\n').contains(_partEnd)) {
        throw StateError('${entry.key} holds the line $_partEnd');
      }
      buffer
        ..writeln("    ${entry.key}) cat <<'$_partEnd'")
        ..write(entry.value)
        ..writeln(_partEnd)
        ..writeln('      ;;');
    }
    buffer
      ..writeln('    *) return 64 ;;')
      ..writeln('  esac')
      ..writeln('}')
      ..write(_body);
    return buffer.toString();
  }

  static String get _header =>
      '''#!/data/data/com.termux/files/usr/bin/bash
# AI Team on this phone, Termux (lib/termux/team_scripts.dart): the Gas City
# team inside the managed Ubuntu (proot-distro `$proot`), next to the
# OpenCode server. gc, bd and dolt are the upstream projects' own Linux
# builds, the ones the in-app Ubuntu installs; Termux downloads and checks
# them, everything else runs inside Ubuntu.
#
# Verbs: install | init <project> [--city n] [--rig n] | start | stop |
#        status | remove | log
# Every verb except status/log appends to ~/.oc/aiteam/aiteam.log and to the
# OpenCode install live output (~/.oc/install.log via manager.sh write-log).
set -Eeuo pipefail

OC_DIR="\$HOME/.oc"
AITEAM_DIR="\$OC_DIR/aiteam"
STATE="\$AITEAM_DIR/state"
CONFIG="\$AITEAM_DIR/config"
LOG="\$AITEAM_DIR/aiteam.log"
VERB_LOCK="\$AITEAM_DIR/verb.lock"
SUPERVISOR_PID="\$AITEAM_DIR/supervisor.pid"
SUPERVISOR_LOG="\$AITEAM_DIR/supervisor.log"
REMOVED_FILE="\$OC_DIR/aiteam-removed"
MANAGER="\$OC_DIR/manager.sh"
# Written by the app on every dispatch (TermuxTeamScripts.pinsFile).
PINS="\$OC_DIR/aiteam-pins"
# Written by the app with each init (TermuxTeamScripts.rigFile).
RIG_FILE="\$AITEAM_DIR/rig.sh"
PREFIX="\${PREFIX:-/data/data/com.termux/files/usr}"
# The earlier native layout: Android builds in \$PREFIX/bin, the city here.
# Only stop and remove still look at them.
BIN_DIR="\$PREFIX/bin"
LEGACY_CITY_DIR="\$AITEAM_DIR/city"
# Downloads live under the AI Team directory, not \$PREFIX/tmp: the Termux
# service clears its tmp directory asynchronously when it starts, which
# raced the first verb after a Termux restart.
TMP_DIR="\$AITEAM_DIR/tmp"
PROOT_NAME=$proot
# Paths inside Ubuntu.
U_PROGRAMS=${AiTeamScripts.home}
U_CACHE=${AiTeamScripts.cache}
U_TEAM=${BuiltinTeam.home}
U_CITY=${BuiltinTeam.cityDir}
U_SCRIPTS=$scriptsDir
UBUNTU_PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
CITY=${BuiltinTeam.city}
TOOLS='gc bd dolt'
GC_URL="\${AITEAM_URL:-$supervisorUrl}"
# Under proot the team takes minutes to come up (2-3 on the emulator).
HEALTH_TIMEOUT="\${AITEAM_HEALTH_TIMEOUT:-360}"
SUPERVISOR_WAIT="\${AITEAM_SUPERVISOR_WAIT:-90}"
# Seconds between polls; the tests shorten it, production keeps 1.
POLL="\${AITEAM_POLL_INTERVAL:-1}"
''';

  static const _body = r'''
# ---------------------------------------------------------------------------
# state, config, logging
# ---------------------------------------------------------------------------

read_kv() {
  local file="$1" key="$2" name value
  [ -f "$file" ] || return 0
  while IFS='=' read -r name value; do
    if [ "$name" = "$key" ]; then
      printf '%s' "$value"
      return 0
    fi
  done < "$file"
}

state_value() { read_kv "$STATE" "$1"; }
config_value() { read_kv "$CONFIG" "$1"; }

# write_state <phase> <message> [supervisor_pid]
# Phases: idle downloading verifying installing-packages installed
# creating-city city-ready starting ready stopping stopped removing
# failed:<reason>. Keeps the verb name and pid of the running verb.
write_state() {
  local phase="$1" message="$2" supervisor="${3-$(state_value supervisor_pid)}"
  message=${message//$'\n'/ }
  mkdir -p "$AITEAM_DIR"
  local tmp="$STATE.tmp.$$"
  printf 'phase=%s\nmessage=%s\nverb=%s\npid=%s\nsupervisor_pid=%s\nupdated_at=%s\n' \
    "$phase" "$message" "${CURRENT_VERB:-}" "${CURRENT_PID:-}" "$supervisor" "$(date +%s)" > "$tmp"
  mv "$tmp" "$STATE"
  # Inside a verb, each stage goes to the log with the seconds since the
  # verb began, so a slow phone shows where its minutes go.
  if [ -n "${CURRENT_PID:-}" ]; then
    printf '[aiteam] %s: %s (at %ss)\n' "$phase" "$message" "$SECONDS"
  fi
}

set_config() {
  local key="$1" value="$2"
  mkdir -p "$AITEAM_DIR"
  local tmp="$CONFIG.tmp.$$"
  { [ ! -f "$CONFIG" ] || grep -v "^$key=" "$CONFIG" || true; printf '%s=%s\n' "$key" "$value"; } > "$tmp"
  mv "$tmp" "$CONFIG"
}

log() { printf '[aiteam] %s\n' "$*"; }

live_sink() {
  if [ -x "$MANAGER" ]; then "$MANAGER" write-log install; else cat > /dev/null; fi
}

# Mirror everything a verb prints into aiteam.log and the OpenCode install
# live output so the setup screen's panel shows it.
attach_log() {
  mkdir -p "$AITEAM_DIR"
  touch "$LOG"
  chmod 600 "$LOG"
  # fd 3 keeps the original stdout (the terminal when run by hand over SSH;
  # /dev/null when the bridge dispatched the verb).
  exec 3>&1
  exec > >(tee -a "$LOG" >(live_sink) >&3) 2>&1
}

fail() {
  local reason="$1"
  shift
  local message="${*:-$reason}"
  trap - ERR
  write_state "failed:$reason" "$message"
  log "ERROR: $message"
  release_verb_lock
  exit "${FAIL_CODE:-1}"
}

on_verb_error() {
  local code=$?
  local line="${BASH_LINENO[0]:-unknown}"
  local stage
  stage=$(state_value message)
  [ -n "$stage" ] || stage="$CURRENT_VERB"
  fail "${CURRENT_VERB}-error" "$stage failed (exit $code; line $line)"
}

# ---------------------------------------------------------------------------
# the managed Ubuntu
# ---------------------------------------------------------------------------

# The managed Ubuntu rootfs, in either proot-distro layout.
rootfs_dir() {
  local base="$PREFIX/var/lib/proot-distro"
  if [ -d "$base/containers/$PROOT_NAME/rootfs" ]; then
    printf '%s' "$base/containers/$PROOT_NAME/rootfs"
  elif [ -d "$base/installed-rootfs/$PROOT_NAME" ]; then
    printf '%s' "$base/installed-rootfs/$PROOT_NAME"
  else
    return 1
  fi
}

# host_path <path inside Ubuntu>: where Termux sees it. Reading the rootfs
# directly keeps `status` free of a proot login, which takes seconds.
host_path() {
  local rootfs
  rootfs=$(rootfs_dir) || return 1
  printf '%s%s' "$rootfs" "$1"
}

# ubuntu_path <path>: a Termux-side path into the rootfs (an older version
# stored those) as Ubuntu sees it; any other path as it is.
ubuntu_path() {
  local path="$1" rootfs
  if rootfs=$(rootfs_dir) && [ "${path#"$rootfs"/}" != "$path" ]; then
    path="/${path#"$rootfs"/}"
  fi
  printf '%s' "$path"
}

# in_ubuntu <command...>: as Ubuntu's root, in the OpenCode server's Ubuntu.
in_ubuntu() {
  proot-distro login "$PROOT_NAME" -- env PATH="$UBUNTU_PATH" HOME=/root \
    LANG=C.UTF-8 TMPDIR=/tmp "$@"
}

# ubuntu_part <name> [VAR=value...]: one of the scripts below, run inside
# Ubuntu from a file.
ubuntu_part() {
  local name="$1" dir
  shift
  dir=$(host_path "$U_SCRIPTS") || return 69
  mkdir -p "$dir"
  team_part "$name" > "$dir/$name.sh"
  in_ubuntu "$@" sh "$U_SCRIPTS/$name.sh" < /dev/null
}

ubuntu_ready() {
  rootfs_dir >/dev/null 2>&1 &&
    command -v proot-distro >/dev/null 2>&1 &&
    in_ubuntu true < /dev/null >/dev/null 2>&1
}

# gc, bd and dolt are unpacked in Ubuntu.
programs_installed() {
  local rootfs tool
  rootfs=$(rootfs_dir) || return 1
  for tool in $TOOLS; do
    [ -x "$rootfs$U_PROGRAMS/bin/$tool" ] || return 1
  done
}

# ---------------------------------------------------------------------------
# processes
# ---------------------------------------------------------------------------

process_group() {
  local stat_line
  stat_line=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
  stat_line=${stat_line##*) }
  # shellcheck disable=SC2086
  set -- $stat_line
  printf '%s' "${3:-}"
}

process_alive() {
  case "${1:-}" in ''|*[!0-9]*) return 1 ;; esac
  kill -0 "$1" 2>/dev/null
}

process_cmdline() { { tr '\0' ' ' < "/proc/$1/cmdline"; } 2>/dev/null || true; }

# Long verbs own their process group so a Stop can take the whole tree down
# and so the bridge's shell exiting never takes the verb with it.
ensure_isolated() {
  [ "$(process_group "$$" 2>/dev/null || true)" = "$$" ] && return 0
  [ "${AITEAM_ISOLATED:-}" != 1 ] || return 0
  AITEAM_ISOLATED=1 exec setsid "${BASH:-bash}" "$0" "$@"
}

claim_verb_lock() {
  mkdir -p "$AITEAM_DIR"
  if mkdir "$VERB_LOCK" 2>/dev/null; then
    printf '%s %s\n' "$$" "$CURRENT_VERB" > "$VERB_LOCK/owner"
    return 0
  fi
  local owner_pid='' owner_verb=''
  [ -f "$VERB_LOCK/owner" ] && { read -r owner_pid owner_verb < "$VERB_LOCK/owner" || true; }
  if process_alive "$owner_pid"; then
    echo "aiteam-busy:${owner_verb:-unknown}:$owner_pid" >&2
    return 75
  fi
  rm -rf "$VERB_LOCK"
  mkdir "$VERB_LOCK" 2>/dev/null || return 75
  printf '%s %s\n' "$$" "$CURRENT_VERB" > "$VERB_LOCK/owner"
}

release_verb_lock() {
  local owner_pid=''
  [ -f "$VERB_LOCK/owner" ] && { read -r owner_pid _ < "$VERB_LOCK/owner" || true; }
  [ "$owner_pid" = "$$" ] || return 0
  rm -f "$VERB_LOCK/owner"
  rmdir "$VERB_LOCK" 2>/dev/null || true
}

begin_verb() {
  CURRENT_VERB="$1"
  CURRENT_PID="$$"
  SECONDS=0
  claim_verb_lock || exit 75
  trap on_verb_error ERR
  trap release_verb_lock EXIT
  attach_log
  write_state queued "Starting $CURRENT_VERB"
  printf '\n[aiteam] %s started at %s\n' "$CURRENT_VERB" "$(date -Iseconds 2>/dev/null || date)"
}

# queue <verb>: the dispatcher's synchronous gate. Refuses while another
# verb owns the lock (aiteam-busy:<verb>:<pid>, exit 75), else records the
# queued verb so a status read between dispatch and the verb's first write
# already reports it as busy.
queue_verb() {
  local next="${1:-}"
  case "$next" in install|init|start|stop|remove) ;; *) exit 64 ;; esac
  local owner_pid='' owner_verb=''
  [ -f "$VERB_LOCK/owner" ] && { read -r owner_pid owner_verb < "$VERB_LOCK/owner" || true; }
  if process_alive "$owner_pid"; then
    echo "aiteam-busy:${owner_verb:-unknown}:$owner_pid" >&2
    exit 75
  fi
  CURRENT_VERB="$next" CURRENT_PID='' write_state queued "Queued $next"
  echo "aiteam-queued:$next"
}

verb_alive() {
  process_alive "${1:-}" || return 1
  case "$(process_cmdline "$1")" in *aiteam*) return 0 ;; esac
  return 1
}

# The supervisor's runner (`aiteam.sh run-supervisor`), or the earlier
# layout's native `gc supervisor run`.
supervisor_alive() {
  local pid
  pid=$(cat "$SUPERVISOR_PID" 2>/dev/null || true)
  process_alive "$pid" || return 1
  case "$(process_cmdline "$pid")" in *supervisor*) return 0 ;; esac
  return 1
}

health_json() {
  local city="${1:-}"
  if [ -n "$city" ]; then
    curl -s -m 5 "$GC_URL/v0/city/$city/health" 2>/dev/null || true
  else
    curl -s -m 5 "$GC_URL/health" 2>/dev/null || true
  fi
}

health_status() {
  local body
  body=$(health_json "$@")
  [ -n "$body" ] || { printf unreachable; return 0; }
  printf '%s' "$body" | sed -n 's/.*"status":"\([^"]*\)".*/\1/p' | head -1
}

# The OpenCode server and Claude Code share Termux's wake lock with the
# team; it is released only when none of them runs.
release_wake_lock_if_idle() {
  local other=''
  read -r other _ < "$OC_DIR/server.pid" 2>/dev/null || true
  process_alive "$other" && return 0
  other=$(cat "$OC_DIR/claude/daemon.pid" 2>/dev/null || true)
  process_alive "$other" && return 0
  termux-wake-unlock >/dev/null 2>&1 || true
}

# ---------------------------------------------------------------------------
# downloads
# ---------------------------------------------------------------------------

# url_host <url>: the host a URL names, for the failure sentence.
url_host() {
  local host="${1#*://}"
  host=${host%%/*}
  host=${host##*@}
  printf '%s' "${host:-unknown}"
}

# fetch <url> <target> [expected bytes]: downloads with curl. --location
# matters: GitHub release assets answer with a redirect to their CDN. A
# partial file an earlier attempt left behind is resumed, and a complete one
# is kept as it is, so Try again after a network failure does not start over;
# the checksum step still checks every byte. On failure DOWNLOAD_FAILURE
# holds "<kind> <host> <code>" (the HTTP status for kind http, curl's exit
# code otherwise) and DOWNLOAD_DETAIL a plain sentence of the same.
fetch() {
  local source="$1" target="$2" bytes="${3:-}" have code=0 http='' kind
  DOWNLOAD_FAILURE=''
  DOWNLOAD_DETAIL=''
  local resume=()
  if [ -n "$bytes" ] && [ -f "$target" ]; then
    have=$(wc -c < "$target" | tr -d ' ')
    if [ "$have" = "$bytes" ]; then
      log "already downloaded $(basename "$target" .part)"
      return 0
    elif [ "$have" -gt 0 ] && [ "$have" -lt "$bytes" ]; then
      log "resuming at $have of $bytes bytes"
      resume=(--continue-at -)
    else
      rm -f "$target"
    fi
  fi
  http=$(curl --fail --silent --show-error --location --retry 3 --retry-delay 2 \
    --connect-timeout 20 ${resume[@]+"${resume[@]}"} -w '%{http_code}' \
    -o "$target" "$source") || code=$?
  if [ "$code" = 33 ]; then
    # The server does not do ranges: start this file over.
    log 'the server cannot resume; downloading the whole file again'
    rm -f "$target"
    code=0
    http=$(curl --fail --silent --show-error --location --retry 3 --retry-delay 2 \
      --connect-timeout 20 -w '%{http_code}' -o "$target" "$source") || code=$?
  fi
  [ "$code" = 0 ] && return 0
  local host
  host=$(url_host "$source")
  case "$code" in
    6) kind=dns; DOWNLOAD_DETAIL="could not find $host (DNS)" ;;
    7) kind=connect; DOWNLOAD_DETAIL="could not connect to $host" ;;
    28) kind=timeout; DOWNLOAD_DETAIL="$host did not answer in time" ;;
    22) kind=http; code="${http:-000}"; DOWNLOAD_DETAIL="$host answered HTTP $code" ;;
    35|51|53|54|58|59|60|64|66|77|80|82|83|90|91)
      kind=tls; DOWNLOAD_DETAIL="the secure connection to $host failed (curl $code)" ;;
    16|18|52|55|56|92)
      kind=interrupted; DOWNLOAD_DETAIL="the connection to $host broke off (curl $code)" ;;
    23) kind=write; DOWNLOAD_DETAIL="the file could not be written on this phone (curl $code)" ;;
    *) kind=other; DOWNLOAD_DETAIL="curl failed with exit $code for $host" ;;
  esac
  DOWNLOAD_FAILURE="$kind $host $code"
  log "download failed: $DOWNLOAD_DETAIL"
  return 1
}

# free_mb <path>: megabytes free on the filesystem holding <path>.
free_mb() {
  local kb
  # No df or awk on PATH (test fixtures, odd Termux installs): do not guess,
  # let the install proceed and fail honestly later.
  command -v df >/dev/null 2>&1 && command -v awk >/dev/null 2>&1 || { echo 999999; return 0; }
  kb=$(df -Pk "$1" 2>/dev/null | awk 'NR==2 {print $4}') || kb=''
  case "$kb" in ''|*[!0-9]*) echo 999999 ;; *) echo $((kb / 1024)) ;; esac
}

# require_space <mb> <what>: fail no-space with an honest sentence when the
# phone cannot hold <what>. Dolt and gc misbehave in confusing ways on a
# full disk (a start that only "times out"), so check up front.
require_space() {
  local need="$1" what="$2" have
  have=$(free_mb "$HOME")
  [ "$have" -ge "$need" ] || fail no-space "Not enough space on this phone for $what: $have MB free, $need MB needed"
}

pin_version() {
  local key value extra
  [ -f "$PINS" ] || return 0
  while read -r key value extra; do
    if [ "$key" = "$1" ] && [ -n "$value" ] && [ -z "$extra" ]; then
      printf '%s' "$value"
      return 0
    fi
  done < "$PINS"
}

# pin_download <arch> <tool>: sets P_URL, P_SHA, P_BYTES and P_MEMBER from
# the pins, and refuses anything that is not a pinned https download (a
# loopback mirror is the in-app setup's test override).
pin_download() {
  local arch tool url sha bytes member
  P_URL='' P_SHA='' P_BYTES='' P_MEMBER=''
  [ -f "$PINS" ] || return 1
  while read -r arch tool url sha bytes member; do
    [ "$arch" = "$1" ] && [ "$tool" = "$2" ] || continue
    [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || return 1
    [[ "$bytes" =~ ^[0-9]+$ ]] || return 1
    [ -n "$member" ] || return 1
    case "$url" in https://*|http://127.0.0.1:*) ;; *) return 1 ;; esac
    P_URL=$url P_SHA=$sha P_BYTES=$bytes P_MEMBER=$member
    return 0
  done < "$PINS"
  return 1
}

record_install() {
  set_config gascity "$(pin_version gascity)"
  set_config beads "$(pin_version beads)"
  set_config dolt "$(pin_version dolt)"
  set_config pack "$(pin_version pack)"
  set_config source upstream
  set_config installed_at "$(date +%s)"
}

# ---------------------------------------------------------------------------
# install
# ---------------------------------------------------------------------------

install_runtime() {
  begin_verb install
  rm -f "$REMOVED_FILE"
  local arch="${AITEAM_ARCH:-$(uname -m)}" want_arch
  case "$arch" in
    aarch64|arm64) want_arch=arm64 ;;
    x86_64|amd64) want_arch=x86_64 ;;
    *) fail unsupported-arch "AI Team needs a 64-bit phone (this one reports $arch)" ;;
  esac
  write_state downloading 'Checking Ubuntu'
  ubuntu_ready || fail no-ubuntu 'Ubuntu is not set up in Termux yet. Finish setting up OpenCode on this phone first, then set up the AI Team'
  if programs_installed && ubuntu_part check >/dev/null 2>&1; then
    log "gc $(pin_version gascity), bd $(pin_version beads) and dolt $(pin_version dolt) are installed already"
    record_install
    write_state installed 'AI Team programs installed' ''
    log 'install finished'
    return 0
  fi
  require_space 500 'the AI Team programs (about 115 MB to download, 350 MB unpacked)'
  mkdir -p "$TMP_DIR"
  chmod 700 "$TMP_DIR"
  local cache tool
  cache=$(host_path "$U_CACHE")
  # Archives an earlier try checked and moved into Ubuntu but could not
  # unpack come back, so Try again does not download them again.
  for tool in $TOOLS; do
    if [ -f "$cache/$tool.tar.gz" ] && [ ! -f "$TMP_DIR/$tool.tar.gz.part" ]; then
      mv -f "$cache/$tool.tar.gz" "$TMP_DIR/$tool.tar.gz.part"
    fi
  done
  local -A url sha bytes member
  for tool in $TOOLS; do
    pin_download "$want_arch" "$tool" ||
      fail pins "This app version has no valid download pinned for $tool on $want_arch"
    url[$tool]=$P_URL
    sha[$tool]=$P_SHA
    bytes[$tool]=$P_BYTES
    member[$tool]=$P_MEMBER
  done

  for tool in $TOOLS; do
    write_state downloading "Downloading $tool ($(( ${bytes[$tool]} / 1048576 )) MB)"
    log "downloading ${url[$tool]}"
    if ! fetch "${url[$tool]}" "$TMP_DIR/$tool.tar.gz.part" "${bytes[$tool]}"; then
      # A write error on a full disk is a space problem, not a network one.
      if [ "${DOWNLOAD_FAILURE%% *}" = write ]; then
        require_space $(( ${bytes[$tool]} / 1048576 + 50 )) "$tool"
      fi
      fail "download ${DOWNLOAD_FAILURE:-other unknown 0}" \
        "Could not download $tool: ${DOWNLOAD_DETAIL:-unknown error}"
    fi
  done

  # Every byte is checked before anything reaches Ubuntu.
  write_state verifying 'Verifying checksums'
  local actual_bytes actual_sha
  for tool in $TOOLS; do
    actual_bytes=$(wc -c < "$TMP_DIR/$tool.tar.gz.part" | tr -d ' ')
    actual_sha=$(sha256sum "$TMP_DIR/$tool.tar.gz.part" | cut -d' ' -f1)
    if [ "$actual_bytes" != "${bytes[$tool]}" ] || [ "$actual_sha" != "${sha[$tool]}" ]; then
      rm -f "$TMP_DIR"/*.part
      echo "checksum-mismatch $tool"
      FAIL_CODE=65 fail "checksum-mismatch $tool" \
        "The $tool download did not match the checksum this app pins (got $actual_bytes bytes, $actual_sha), so it was deleted"
    fi
    log "verified $tool (${bytes[$tool]} bytes, sha256 ${sha[$tool]})"
  done

  write_state installing-packages 'Installing tmux, jq, lsof and procps in Ubuntu'
  ubuntu_part packages ||
    fail packages 'Ubuntu could not install tmux, jq, lsof and procps; the output above says why'

  write_state installing-packages 'Unpacking gc, bd and dolt in Ubuntu'
  mkdir -p "$cache"
  for tool in $TOOLS; do
    mv -f "$TMP_DIR/$tool.tar.gz.part" "$cache/$tool.tar.gz"
  done
  local out code=0 said
  out=$(ubuntu_part unpack gc_member="${member[gc]}" bd_member="${member[bd]}" \
    dolt_member="${member[dolt]}" 2>&1) || code=$?
  [ -z "$out" ] || printf '%s\n' "$out"
  if [ "$code" != 0 ]; then
    said=$(printf '%s\n' "$out" | sed -n 's/^\[oc\] //p' | tail -n 1)
    case "$said" in
      *SIGSYS*) fail blocked-syscall "$said" ;;
    esac
    if [ "$code" = 70 ]; then
      fail runs-here "${said:-gc, bd or dolt does not run in Ubuntu here}"
    fi
    fail unpack "Could not unpack gc, bd and dolt in Ubuntu (exit $code)"
  fi
  rm -rf "$TMP_DIR"
  record_install
  write_state installed 'AI Team programs installed' ''
  log 'install finished'
}

# ---------------------------------------------------------------------------
# init <project-path>: the team's store, and the project added to it
# ---------------------------------------------------------------------------

# origin_url <project host path>: the project's origin, read from its git
# config (Termux may have no git of its own).
origin_url() {
  sed -n '/^\[remote "origin"\]/,/^\[/s/^[[:space:]]*url[[:space:]]*=[[:space:]]*//p' \
    "$1/.git/config" 2>/dev/null | head -n 1
}

init_city() {
  local project='' city='' rig=''
  while [ $# -gt 0 ]; do
    case "$1" in
      --city) city="${2:-}"; shift 2 ;;
      --rig) rig="${2:-}"; shift 2 ;;
      *) project="$1"; shift ;;
    esac
  done
  [ -n "$project" ] || { echo 'usage: aiteam.sh init <project-path> [--city name] [--rig name]' >&2; exit 64; }
  begin_verb init
  require_space 150 'the team store'
  programs_installed || fail not-installed 'Install the AI Team programs first'
  [ -z "$city" ] || [ "$city" = "$CITY" ] || log "the team on this phone is always called $CITY"
  project=$(ubuntu_path "${project%/}")
  case "$project" in /*) ;; *) fail project-missing "Project folder not found: $project" ;; esac
  local host
  host=$(host_path "$project")
  [ -d "$host" ] || fail project-missing "Project folder not found: $project"
  [ -e "$host/.git" ] || fail project-not-git "$project is not a git repository"
  local prepared rig_name
  prepared=$(sed -n 's/^# oc-project: //p' "$RIG_FILE" 2>/dev/null | head -n 1)
  rig_name=$(sed -n 's/^# oc-rig: //p' "$RIG_FILE" 2>/dev/null | head -n 1)
  [ "$prepared" = "$project" ] && [ -n "$rig_name" ] ||
    fail rig-script "The app did not prepare $project for the team; try again from the app"
  [ -z "$rig" ] || [ "$rig" = "$rig_name" ] ||
    fail rig-script "The app prepared $project as $rig_name, not $rig; try again from the app"

  # The earlier native layout gave a project an origin by its Termux path
  # into the rootfs, which Ubuntu cannot see; point it at the same folder.
  local origin rootfs
  origin=$(origin_url "$host")
  rootfs=$(rootfs_dir)
  if [ -n "$origin" ] && [ "${origin#"$rootfs"/}" != "$origin" ]; then
    log "pointing $project's origin at /${origin#"$rootfs"/}"
    in_ubuntu git -C "$project" remote set-url origin "/${origin#"$rootfs"/}" < /dev/null ||
      fail project-origin "Could not point $project's origin at /${origin#"$rootfs"/}"
  fi

  write_state creating-city 'Preparing the team store'
  ubuntu_part city || fail gc-init 'Gas City could not make the team store; the output above says why'
  write_state creating-city "Adding $rig_name to the team"
  local dir code=0
  dir=$(host_path "$U_SCRIPTS")
  mkdir -p "$dir"
  cp "$RIG_FILE" "$dir/rig.sh"
  in_ubuntu sh "$U_SCRIPTS/rig.sh" < /dev/null || code=$?
  case "$code" in
    0) ;;
    65) fail project-not-git "$project is not a git repository" ;;
    *) fail gc-rig-add "Gas City could not add $project to the team (exit $code); the output above says why" ;;
  esac
  set_config city "$CITY"
  set_config rig "$rig_name"
  set_config project "$project"
  set_config city_dir "$U_CITY"
  write_state city-ready "Team $CITY ready for $rig_name" ''
  log "init finished: city=$CITY rig=$rig_name project=$project"
}

# ---------------------------------------------------------------------------
# start / stop
# ---------------------------------------------------------------------------

supervisor_tail() {
  tail -n 20 "$SUPERVISOR_LOG" 2>/dev/null | sed 's/^/[supervisor] /' || true
}

# run-supervisor: the detached runner. The supervisor lives exactly as long
# as this proot-distro login: proot stops everything it started when the
# supervisor exits (--kill-on-exit), so the login must not be one that ends.
run_supervisor() {
  in_ubuntu sh "$U_SCRIPTS/service.sh" < /dev/null >> "$SUPERVISOR_LOG" 2>&1
}

start_runtime() {
  begin_verb start
  programs_installed || fail not-installed 'Install the AI Team programs first'
  local city marker
  city=$(config_value city)
  marker=$(host_path "$U_TEAM/city.ready" 2>/dev/null || true)
  [ -n "$city" ] && [ -n "$marker" ] && [ -f "$marker" ] ||
    fail no-city 'Set up the team for a project first'
  write_state starting 'Starting the AI Team supervisor'
  termux-wake-lock >/dev/null 2>&1 || true
  if supervisor_alive && [ "$(health_status "$city")" = ok ]; then
    write_state ready 'AI Team is running on this phone'
    log 'supervisor already running'
    return 0
  fi
  stop_supervisor
  local dir
  dir=$(host_path "$U_SCRIPTS")
  mkdir -p "$dir"
  team_part service > "$dir/service.sh"
  : > "$SUPERVISOR_LOG"
  chmod 600 "$SUPERVISOR_LOG"
  # Its own session with none of this verb's descriptors (the log tee's
  # pipe included): the verb exits, the supervisor keeps running.
  (
    for fd in /proc/$BASHPID/fd/*; do
      fd=${fd##*/}
      [ "$fd" -gt 2 ] 2>/dev/null && eval "exec $fd>&-"
    done
    nohup setsid "${BASH:-bash}" "$0" run-supervisor > /dev/null 2>&1 < /dev/null &
    echo $! > "$SUPERVISOR_PID"
  )
  local pid waited=0
  pid=$(cat "$SUPERVISOR_PID")
  log "supervisor pid $pid"
  while [ "$(health_status)" != ok ]; do
    if ! process_alive "$pid"; then
      supervisor_tail
      fail supervisor-exited 'The supervisor stopped before it answered; the output above says why'
    fi
    if [ "$waited" -ge "$SUPERVISOR_WAIT" ]; then
      supervisor_tail
      stop_supervisor
      fail health-timeout "The supervisor did not answer on $GC_URL within $SUPERVISOR_WAIT s"
    fi
    sleep "$POLL"
    waited=$((waited + 1))
  done
  write_state starting "Registering team $city" "$pid"
  ubuntu_part register || log 'gc register did not confirm; waiting for the team anyway'
  write_state starting "Waiting for team $city" "$pid"
  waited=0
  while [ "$(health_status "$city")" != ok ]; do
    if ! process_alive "$pid"; then
      supervisor_tail
      fail supervisor-exited 'The supervisor stopped before the team answered; the output above says why'
    fi
    [ "$waited" -lt "$HEALTH_TIMEOUT" ] ||
      fail health-timeout "Team $city did not answer within $HEALTH_TIMEOUT s"
    sleep "$POLL"
    waited=$((waited + 1))
  done
  write_state ready 'AI Team is running on this phone' "$pid"
  log "team $city healthy"
}

# Stops every process of ours that works inside <dir> (the team's home in
# Ubuntu, seen from Termux; the earlier layout's city): Dolt, the agents,
# their helpers. Matched by working folder or by that path in the command
# line, never by a program's name.
kill_team_processes() {
  local signal="$1" entry pid cwd cmdline dir hit _
  local -a dirs=() parts=()
  shift
  for dir in "$@"; do [ -z "$dir" ] || dirs+=("$dir"); done
  [ "${#dirs[@]}" -gt 0 ] || return 0
  for entry in /proc/[0-9]*; do
    pid=${entry#/proc/}
    [ "$pid" != "$$" ] && [ "$pid" != "$PPID" ] || continue
    parts=()
    { mapfile -d '' -t parts < "$entry/cmdline"; } 2>/dev/null || true
    cmdline="${parts[*]:-}"
    hit=''
    for dir in "${dirs[@]}"; do
      case "$cmdline" in
        *"$dir"*) hit=1; break ;;
      esac
      # Working folder is <dir> or below it: walk up from /proc/<pid>/cwd with
      # the shell's own same-file test, no process per candidate, stopping at /.
      cwd="$entry/cwd"
      for _ in {1..64}; do
        if [ "$cwd" -ef "$dir" ]; then hit=1; break 2; fi
        [ ! "$cwd" -ef / ] || break
        cwd="$cwd/.."
      done
    done
    [ -n "$hit" ] || continue
    case "$cmdline" in
      *aiteam.sh*) continue ;;
    esac
    log "$signal pid $pid (${cmdline:0:60})"
    kill "-$signal" "$pid" 2>/dev/null || true
  done
}

stop_supervisor() {
  local pid team
  pid=$(cat "$SUPERVISOR_PID" 2>/dev/null || true)
  # Gas City first: the supervisor stops its agents and Dolt, and proot ends
  # whatever is left once the supervisor exits.
  if process_alive "$pid" && programs_installed; then
    timeout -k 5 45 proot-distro login "$PROOT_NAME" -- env PATH="$UBUNTU_PATH" \
      HOME=/root LANG=C.UTF-8 sh -c "cd $U_CITY 2>/dev/null && gc supervisor stop" \
      < /dev/null > /dev/null 2>&1 || true
  fi
  if [ -x "$BIN_DIR/gc" ] && [ -d "$LEGACY_CITY_DIR" ]; then
    (cd "$LEGACY_CITY_DIR" && timeout -k 5 30 "$BIN_DIR/gc" supervisor stop) >/dev/null 2>&1 || true
  fi
  if process_alive "$pid"; then
    # The runner leads its own session: the group is the runner, the
    # proot-distro login and everything the supervisor started.
    if [ "$(process_group "$pid" 2>/dev/null || true)" = "$pid" ]; then
      kill -TERM -- "-$pid" 2>/dev/null || true
    else
      kill -TERM "$pid" 2>/dev/null || true
    fi
    local waited=0
    while process_alive "$pid" && [ "$waited" -lt 10 ]; do sleep "$POLL"; waited=$((waited + 1)); done
    if process_alive "$pid"; then
      kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    fi
  fi
  team=$(host_path "$U_TEAM" 2>/dev/null || true)
  kill_team_processes TERM "$team" "$LEGACY_CITY_DIR"
  sleep "$POLL"
  kill_team_processes KILL "$team" "$LEGACY_CITY_DIR"
  rm -f "$SUPERVISOR_PID"
}

stop_runtime() {
  begin_verb stop
  write_state stopping 'Stopping the AI Team'
  stop_supervisor
  release_wake_lock_if_idle
  write_state stopped 'AI Team stopped' ''
  log 'stopped'
}

# ---------------------------------------------------------------------------
# remove: the programs, the team's store and settings. Never a project, and
# never the phone-side origins a project's `origin` points at (they hold the
# team's branches).
# ---------------------------------------------------------------------------

remove_runtime() {
  begin_verb remove
  write_state removing 'Removing the AI Team from this phone'
  stop_supervisor
  release_wake_lock_if_idle
  local removed=() kept='' path rootfs entry
  if rootfs=$(rootfs_dir); then
    for path in /usr/local/bin/gc /usr/local/bin/bd /usr/local/bin/dolt; do
      [ -e "$rootfs$path" ] || [ -L "$rootfs$path" ] || continue
      rm -f "$rootfs$path"
      removed+=("$path")
    done
    for path in "$U_PROGRAMS" "$U_PROGRAMS.new" "$U_CACHE" "$U_SCRIPTS" /root/.gc; do
      [ -e "$rootfs$path" ] || continue
      rm -rf "$rootfs$path"
      removed+=("$path")
    done
    if [ -d "$rootfs$U_TEAM" ]; then
      for entry in "$rootfs$U_TEAM"/* "$rootfs$U_TEAM"/.[!.]*; do
        [ -e "$entry" ] || continue
        if [ "${entry##*/}" = origins ]; then
          kept="$U_TEAM/origins"
          continue
        fi
        rm -rf "$entry"
        removed+=("$U_TEAM/${entry##*/}")
      done
      if rmdir "$rootfs$U_TEAM" 2>/dev/null; then removed+=("$U_TEAM"); fi
    fi
  fi
  # The earlier native layout.
  for path in "$BIN_DIR/gc" "$BIN_DIR/bd" "$BIN_DIR/dolt"; do
    [ -e "$path" ] || continue
    rm -f "$path"
    removed+=("$path")
  done
  if [ -f "$BIN_DIR/opencode" ] && grep -q 'AI Team' "$BIN_DIR/opencode" 2>/dev/null; then
    rm -f "$BIN_DIR/opencode"
    removed+=("$BIN_DIR/opencode")
  fi
  for path in "$HOME/.gc" "$HOME/.dolt" "$PREFIX/tmp/aiteam"; do
    [ -e "$path" ] || continue
    rm -rf "$path"
    removed+=("$path")
  done
  # Printed before the log itself goes: aiteam.log lives in $AITEAM_DIR.
  for path in ${removed[@]+"${removed[@]}"} "$AITEAM_DIR"; do log "removed $path"; done
  [ -z "$kept" ] || log "kept $kept (each project's team origin, with the team's branches)"
  rm -rf "$AITEAM_DIR"
  removed+=("$AITEAM_DIR")
  printf '%s\n' "${removed[@]}" > "$REMOVED_FILE"
  trap - EXIT
  exit 0
}

# ---------------------------------------------------------------------------
# status (JSON) and log
# ---------------------------------------------------------------------------

json_str() {
  local value="$1"
  value=${value//\\/\\\\}
  value=${value//\"/\\\"}
  value=${value//$'\n'/\\n}
  value=${value//$'\t'/\\t}
  value=${value//$'\r'/}
  printf '"%s"' "$value"
}

json_or_null() { if [ -n "$1" ]; then json_str "$1"; else printf null; fi; }
json_num_or_null() { case "$1" in ''|*[!0-9]*) printf null ;; *) printf '%s' "$1" ;; esac; }

agents_count() {
  local city="$1" body
  [ -n "$city" ] || return 0
  body=$(curl -s -m 5 "$GC_URL/v0/city/$city/agents" 2>/dev/null || true)
  [ -n "$body" ] || return 0
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$body" | jq -r '(.items // []) | length' 2>/dev/null || true
  else
    # Termux has no jq. grep finds nothing while the city lists no agents
    # yet, and under pipefail that failed the whole status read with no
    # output (issue #112: the start step "failed" while start said ready).
    { printf '%s' "$body" | grep -o '"id":' || true; } | wc -l | tr -d ' '
  fi
}

status_json() {
  local phase message verb pid supervisor city rig project installed=false
  phase=$(state_value phase)
  message=$(state_value message)
  verb=$(state_value verb)
  pid=$(state_value pid)
  supervisor=$(state_value supervisor_pid)
  city=$(config_value city)
  rig=$(config_value rig)
  project=$(config_value project)
  local last_error='' busy=false killed=false health='' agents='' gc_v='' bd_v='' dolt_v=''
  # Read from the rootfs and the install record, never by starting Ubuntu:
  # the app polls this every few seconds.
  if programs_installed; then
    installed=true
    gc_v=$(config_value gascity)
    bd_v=$(config_value beads)
    dolt_v=$(config_value dolt)
  fi
  [ -n "$phase" ] || phase=idle
  if verb_alive "$pid"; then
    busy=true
  else
    pid=''
  fi
  local age=0 updated
  updated=$(state_value updated_at)
  case "$updated" in ''|*[!0-9]*) ;; *) age=$(( $(date +%s) - updated )) ;; esac
  local state_phase="$phase" reason=''
  case "$phase" in
    failed:*)
      reason=${phase#failed:}
      last_error="${message:-$reason}"
      phase=failed ;;
    queued)
      # Dispatched but not yet running: busy for a grace period, then a
      # launch that never happened.
      if [ "$busy" = false ] && [ "$age" -lt 30 ]; then busy=true; fi
      if [ "$busy" = false ]; then
        last_error="$verb never started"
        CURRENT_VERB="$verb" CURRENT_PID='' write_state "failed:interrupted" "$last_error"
        phase=failed; reason=interrupted; state_phase=failed:interrupted
      fi ;;
    downloading|verifying|installing-packages|creating-city|starting|stopping|removing)
      # A verb's pid can be momentarily unobservable between two of its own
      # writes (the detached shell re-execs under setsid); only a phase that
      # has sat unowned for a while is a real interruption.
      if [ "$busy" = false ] && [ "$age" -lt 10 ]; then busy=true; fi
      if [ "$busy" = false ]; then
        last_error="$verb stopped unexpectedly while $phase"
        CURRENT_VERB="$verb" CURRENT_PID='' write_state "failed:interrupted" "$last_error"
        phase=failed; reason=interrupted; state_phase=failed:interrupted
      fi ;;
  esac
  local supervisor_live=false
  if supervisor_alive; then supervisor_live=true; else supervisor=''; fi
  if [ "$phase" = ready ]; then
    if [ "$supervisor_live" = true ]; then
      health=$(health_status "$city")
      agents=$(agents_count "$city")
    else
      killed=true
      health=unreachable
    fi
  elif [ "$supervisor_live" = true ]; then
    health=$(health_status "$city")
  fi
  local removed='[]'
  if [ -f "$REMOVED_FILE" ]; then
    removed='['
    local first=1 line
    while IFS= read -r line; do
      [ -n "$line" ] || continue
      [ "$first" = 1 ] || removed="$removed,"
      removed="$removed$(json_str "$line")"
      first=0
    done < "$REMOVED_FILE"
    removed="$removed]"
  fi
  printf '{"installed":%s,"versions":{"gc":%s,"bd":%s,"dolt":%s},"phase":%s,"state_phase":%s,"reason":%s,"message":%s,"verb":%s,"busy":%s,"pid":%s,"supervisor_pid":%s,"health":%s,"agents":%s,"city":%s,"rig":%s,"project":%s,"url":%s,"last_error":%s,"killed_by_android":%s,"removed":%s,"log":%s,"updated_at":%s}\n' \
    "$installed" "$(json_or_null "$gc_v")" "$(json_or_null "$bd_v")" "$(json_or_null "$dolt_v")" \
    "$(json_str "$phase")" "$(json_str "$state_phase")" "$(json_or_null "$reason")" \
    "$(json_or_null "$message")" "$(json_or_null "$verb")" "$busy" \
    "$(json_num_or_null "$pid")" "$(json_num_or_null "$supervisor")" "$(json_or_null "$health")" \
    "$(json_num_or_null "$agents")" "$(json_or_null "$city")" "$(json_or_null "$rig")" \
    "$(json_or_null "$project")" "$(json_str "$GC_URL")" "$(json_or_null "$last_error")" "$killed" \
    "$removed" "$(json_str "$LOG")" "$(json_num_or_null "$(state_value updated_at)")"
}

verb="${1:-status}"
case "$verb" in
  install|init|start|stop|remove)
    ensure_isolated "$@"
    shift
    case "$verb" in
      # install takes no argument; an older app passed a manifest path.
      install) install_runtime ;;
      init) init_city "$@" ;;
      start) start_runtime ;;
      stop) stop_runtime ;;
      remove) remove_runtime ;;
    esac ;;
  run-supervisor) run_supervisor ;;
  queue) shift; queue_verb "$@" ;;
  status) status_json ;;
  log) printf '%s\n' "$LOG" ;;
  *) echo "usage: $0 {install|init|start|stop|status|remove|log}" >&2; exit 64 ;;
esac
''';
}
