import 'package:flutter/services.dart';

import '../platform/platform_capabilities.dart';

/// Drives Termux over the `oc/termux` method channel.
///
/// Termux is an Android app and the channel is implemented only by the
/// Android runner, so every entry point here is a no-op-with-an-answer on
/// desktop rather than a throw: `MissingPluginException` is not a
/// `PlatformException`, so a bridge that caught only the latter let a raw
/// framework exception escape into UI code that had no idea what it meant.
class TermuxBridge {
  static const _channel = MethodChannel('oc/termux');

  /// Every failure the bridge can report for "this platform has no Termux".
  static const unsupportedPlatformCode = 'unsupported_platform';

  /// Whether the Termux bridge can do anything at all here.
  static bool get supported => platformCapabilities.supportsTermux;

  static TermuxBridgeException get _unsupported => const TermuxBridgeException(
    'Termux runs on Android. This desktop build connects to an OpenCode '
    'server you start yourself.',
    code: unsupportedPlatformCode,
  );

  static const termuxHome = '/data/data/com.termux/files/home';
  static const _managerPath = '$termuxHome/.oc/manager.sh';
  static const managedServerPort = 4096;
  static const managedServerUrl = 'http://127.0.0.1:$managedServerPort';

  /// The OpenCode server version a published build installs and updates to.
  ///
  /// Pinned, deliberately. `latest` meant an APK sitting on a phone for
  /// months would one day install a server release published long after this
  /// client was written and tested against it — a protocol change on the
  /// server side would then break setup on a device whose owner changed
  /// nothing. This is the version the app's contracts and fixtures are
  /// verified against; raising it is a code change with a test run behind it,
  /// not something that happens on its own.
  ///
  /// Keep this in step with the shell fallback in [_managerScript]
  /// (`requested_version="${2:-…}"`); a test asserts the two agree.
  static const defaultOpenCodeVersion = '1.18.25';

  /// The npm dist-tag, available only when a caller passes it to
  /// [installAndServeScript] on purpose. Nothing in the app does today: it
  /// exists so a deliberate "install whatever is newest" flow has a name
  /// rather than a magic string.
  static const latestOpenCodeVersion = 'latest';

  static bool managesServerUrl(String? value) {
    final uri = value == null ? null : Uri.tryParse(value);
    if (uri == null || uri.scheme != 'http') return false;
    final port = uri.hasPort ? uri.port : 80;
    return uri.host == '127.0.0.1' && port == managedServerPort;
  }

  static Future<TermuxCapabilities> capabilities() async {
    if (!supported) return const TermuxCapabilities.unavailable();
    try {
      final raw = await _channel.invokeMapMethod<String, dynamic>(
        'getCapabilities',
      );
      return TermuxCapabilities.fromMap(raw ?? const {});
    } on MissingPluginException {
      // A runner with no `oc/termux` handler: honestly nothing installed.
      return const TermuxCapabilities.unavailable();
    }
  }

  static Future<bool> requestPermission() =>
      _invokeFlag('requestRunCommandPermission');

  static Future<bool> openTermux() => _invokeFlag('openTermux');

  static Future<bool> openAppSettings() => _invokeFlag('openAppSettings');

  /// Every one of these answers "did the platform do the thing?", so a
  /// missing channel is simply `false` — never an exception a caller that
  /// wanted a bool has to know about.
  static Future<bool> _invokeFlag(String method) async {
    if (!supported) return false;
    try {
      return await _channel.invokeMethod<bool>(method) ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<TermuxCommandResult> run(
    String script, {
    bool background = true,
    String? workdir,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    if (!supported) throw _unsupported;
    try {
      final raw = await _channel
          .invokeMapMethod<String, dynamic>('runInTermux', {
            'script': script,
            'background': background,
            'workdir': workdir ?? termuxHome,
            'timeoutMs': timeout.inMilliseconds,
          });
      final command = TermuxCommandResult.fromMap(raw ?? const {});
      if (!command.successful) {
        throw TermuxBridgeException(
          command.failureMessage,
          code: 'command_failed',
        );
      }
      return command;
    } on MissingPluginException {
      // The Android runner registers `oc/termux`; nothing else does. Reaching
      // here means a platform slipped past [supported] — report it the way a
      // caller already handles rather than letting a framework exception out.
      throw _unsupported;
    } on PlatformException catch (error) {
      throw TermuxBridgeException(
        error.message ?? 'Termux command failed.',
        code: error.code,
      );
    }
  }

  static Future<void> verifyBridge() async {
    final result = await run("printf 'opencode-bridge-ok'");
    if (result.stdout.trim() != 'opencode-bridge-ok') {
      throw const TermuxBridgeException(
        'Termux returned an unexpected bridge response.',
        code: 'invalid_probe',
      );
    }
  }

  /// Keeps the managed on-device OpenCode server able to reach providers
  /// while Android is idle. The manager releases this lock when the server is
  /// stopped or exits; repeated acquisitions are safe in Termux.
  static Future<void> ensureWakeLock() async {
    await run(ensureWakeLockScript, timeout: const Duration(seconds: 10));
  }

  static const ensureWakeLockScript =
      "termux-wake-lock >/dev/null 2>&1 || true; "
      "echo opencode-server-wake-lock-held";

  static Future<TermuxSetupStatus> status() async {
    final result = await run(statusScript());
    return TermuxSetupStatus.parse(result.stdout);
  }

  static Future<TermuxSetupSnapshot> setupSnapshot() async {
    final result = await run(setupSnapshotScript());
    return TermuxSetupSnapshot.parse(result.stdout);
  }

  static Future<String> diagnostics() async {
    final result = await run(diagnosticsScript());
    return result.stdout.trim();
  }

  static bool isLaunchAcknowledged(String output) => RegExp(
    r'(^|\n)manager-(started|already-running):[0-9]+($|\n)',
  ).hasMatch(output.trim());

  static const unlockCommand =
      "mkdir -p ~/.termux && touch ~/.termux/termux.properties && "
      "grep -q '^allow-external-apps=true' ~/.termux/termux.properties || "
      "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; "
      "termux-reload-settings; echo bridge-unlocked";

  static String installAndServeScript({
    int port = 4096,
    required String password,
    String version = defaultOpenCodeVersion,
  }) {
    if (port < 1024 || port > 65535) {
      throw ArgumentError.value(
        port,
        'port',
        'Must be between 1024 and 65535.',
      );
    }
    if (!RegExp(r'^[A-Za-z0-9._+-]+$').hasMatch(version)) {
      throw ArgumentError.value(version, 'version', 'Invalid package version.');
    }
    if (password.isEmpty) {
      throw ArgumentError.value(password, 'password', 'Must not be empty.');
    }

    final quotedPassword = _shellQuote(password);
    return '''
set -eu
OC_DIR="$termuxHome/.oc"
MANAGER="$_managerPath"
LOCK="\$OC_DIR/setup.lock"
mkdir -p "\$OC_DIR"
umask 077

if [ ! -e "\$MANAGER" ] && [ -d "\$OC_DIR/bin" ]; then
  touch "\$OC_DIR/legacy-install"
fi
manager_tmp="\$MANAGER.tmp.\$\$"
cat > "\$manager_tmp" <<'OC_MANAGER_EOF'
$_managerScript
OC_MANAGER_EOF
chmod 700 "\$manager_tmp"
mv "\$manager_tmp" "\$MANAGER"
[ -x "\$MANAGER" ] || {
  echo 'manager-install-failed' >&2
  exit 74
}

process_start() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line#*) }
  set -- \$stat_line
  printf '%s' "\${20:-}"
}

self_start=\$(process_start "\$\$")
if [ -f "\$LOCK" ]; then
  owner_pid=''
  owner_start=''
  read -r owner_pid owner_start < "\$LOCK" 2>/dev/null || true
  live_start=\$(process_start "\$owner_pid" 2>/dev/null || true)
  if [ -n "\$owner_pid" ] && [ -n "\$owner_start" ] && [ "\$owner_start" = "\$live_start" ] &&
     kill -0 "\$owner_pid" 2>/dev/null; then
    echo "manager-already-running:\$owner_pid"
    exit 0
  fi
  rm -f "\$LOCK"
fi
if mkdir "\$LOCK" 2>/dev/null; then
  printf '%s %s\n' "\$\$" "\$self_start" > "\$LOCK/owner"
else
  owner_pid=''
  owner_start=''
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    read -r owner_pid owner_start < "\$LOCK/owner" 2>/dev/null && break
    sleep 0.1
  done
  live_start=\$(process_start "\$owner_pid" 2>/dev/null || true)
  case "\$owner_pid" in
    ''|*[!0-9]*) ;;
    *) if [ -n "\$owner_start" ] && [ "\$owner_start" = "\$live_start" ] &&
         kill -0 "\$owner_pid" 2>/dev/null; then
         echo "manager-already-running:\$owner_pid"
         exit 0
       fi ;;
  esac
  if [ -z "\$owner_pid" ] || [ -z "\$owner_start" ]; then
    echo 'setup-lock-owner-missing; use Retry' >&2
  else
    echo 'setup-lock-stale; use Retry' >&2
  fi
  exit 75
fi
cleanup_dispatch() {
  lock_pid=''
  lock_start=''
  read -r lock_pid lock_start < "\$LOCK/owner" 2>/dev/null || true
  if [ "\$lock_pid" = "\$\$" ] && [ "\$lock_start" = "\$self_start" ]; then
    rm -f "\$LOCK/owner" "\$LOCK"/owner.tmp.*
    rmdir "\$LOCK" 2>/dev/null || true
  fi
}
trap cleanup_dispatch EXIT

password_tmp="\$OC_DIR/server.password.tmp.\$\$"
printf '%s' $quotedPassword > "\$password_tmp"
chmod 600 "\$password_tmp"
mv "\$password_tmp" "\$OC_DIR/server.password"
printf 'phase=queued\nmessage=Setup queued\nport=$port\nrunner=proot\nversion=\npid=\n' > "\$OC_DIR/state"
# From this point a stale dispatcher lock is safer than deleting a lock while
# the child is claiming it. Stop & retry handles stale ownership explicitly.
trap - EXIT
rm -f "\$OC_DIR/server-log.active"
"\$MANAGER" rotate-log install
nohup "\$MANAGER" setup '$port' '$version' "\$\$" "\$self_start" > >("\$MANAGER" write-log install) 2>&1 </dev/null &
manager_pid=\$!
printf '%s\n' "\$manager_pid" > "\$OC_DIR/manager.pid"
manager_start=\$(process_start "\$manager_pid" || true)
[ -n "\$manager_start" ] || {
  wait "\$manager_pid" 2>/dev/null || true
  echo 'manager-exited-before-start' >&2
  exit 70
}
claimed=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  lock_pid=''
  lock_start=''
  read -r lock_pid lock_start < "\$LOCK/owner" 2>/dev/null || true
  if [ "\$lock_pid" = "\$manager_pid" ] && [ "\$lock_start" = "\$manager_start" ]; then
    claimed=1
    break
  fi
  kill -0 "\$manager_pid" 2>/dev/null || break
  sleep 0.1
done
if [ "\$claimed" != 1 ]; then
  kill -KILL "\$manager_pid" 2>/dev/null || true
  wait "\$manager_pid" 2>/dev/null || true
  echo 'manager-lock-claim-failed' >&2
  exit 70
fi
echo "manager-started:\$manager_pid"
''';
  }

  static String statusScript() =>
      '''
if [ -x "$_managerPath" ]; then
  exec "$_managerPath" status
fi
if [ -f "$termuxHome/.oc/state" ]; then
  port=4096
  while IFS='=' read -r name value; do
    [ "\$name" = port ] && port="\$value"
  done < "$termuxHome/.oc/state"
  printf 'phase=failed\nmessage=Setup manager is missing after launch\nport=%s\nrunner=\nversion=\npid=\n' "\$port"
  exit 0
fi
printf 'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\nversion=\npid=\n'
''';

  static String setupSnapshotScript() =>
      '''
OC_DIR="$termuxHome/.oc"
if [ -x "$_managerPath" ]; then
  if manager_output=\$("$_managerPath" status 2>&1); then
    printf '%s\n' "\$manager_output"
    manager_error=''
  else
    manager_error="\$manager_output"
    port=4096
    if [ -f "\$OC_DIR/state" ]; then
      while IFS='=' read -r name value; do
        [ "\$name" = port ] && port="\$value"
      done < "\$OC_DIR/state"
    fi
    printf 'phase=failed\nmessage=Could not read setup manager status\nport=%s\nrunner=\nversion=\npid=\n' "\$port"
  fi
elif [ -f "\$OC_DIR/state" ]; then
  manager_error='Setup manager is missing after launch'
  port=4096
  while IFS='=' read -r name value; do
    [ "\$name" = port ] && port="\$value"
  done < "\$OC_DIR/state"
  printf 'phase=failed\nmessage=Setup manager is missing after launch\nport=%s\nrunner=\nversion=\npid=\n' "\$port"
else
  manager_error=''
  printf 'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\nversion=\npid=\n'
fi
printf '%s\n' '__OC_SETUP_OUTPUT__'
if [ -n "\$manager_error" ]; then
  printf '[oc] status error: %s\n' "\$manager_error"
fi
tail -n 160 "\$OC_DIR/install.log" 2>/dev/null || true
if [ -f "\$OC_DIR/server-log.active" ] && [ -s "\$OC_DIR/server.log" ]; then
  printf '\n%s\n' '[oc] server output'
  tail -n 60 "\$OC_DIR/server.log" 2>/dev/null || true
fi
''';

  static String diagnosticsScript() =>
      '''
if [ -x "$_managerPath" ]; then
  exec "$_managerPath" diagnostics
fi
OC_DIR="$termuxHome/.oc"
echo '===== OpenCode bootstrap diagnostics ====='
echo 'Manager: missing or not executable'
echo '===== state ====='
if [ -f "\$OC_DIR/state" ]; then cat "\$OC_DIR/state"; else echo 'No state file'; fi
echo '===== bootstrap files ====='
ls -la "\$OC_DIR" 2>&1 || true
echo '===== setup lock ====='
if [ -f "\$OC_DIR/setup.lock" ]; then
  echo 'Legacy file lock:'
  cat "\$OC_DIR/setup.lock"
elif [ -f "\$OC_DIR/setup.lock/owner" ]; then
  cat "\$OC_DIR/setup.lock/owner"
elif [ -d "\$OC_DIR/setup.lock" ]; then
  echo 'Setup lock directory exists without an owner'
else
  echo 'No setup lock'
fi
echo '===== install.log (last 120 lines) ====='
tail -n 120 "\$OC_DIR/install.log" 2>/dev/null || true
echo '===== server.log (last 80 lines) ====='
tail -n 80 "\$OC_DIR/server.log" 2>/dev/null || true
''';

  static String stopScript({int port = 4096}) =>
      '''
if [ -x "$_managerPath" ]; then
  exec "$_managerPath" stop '$port'
fi
OC_DIR="$termuxHome/.oc"
process_start() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line#*) }
  set -- \$stat_line
  printf '%s' "\${20:-}"
}
kill_tree() {
  local root="\$1"
  kill -STOP "\$root" 2>/dev/null || return 0
  local stat_file stat_line child
  for stat_file in /proc/[0-9]*/stat; do
    stat_line=\$(cat "\$stat_file" 2>/dev/null || true)
    [ -n "\$stat_line" ] || continue
    child=\${stat_file#/proc/}
    child=\${child%/stat}
    stat_line=\${stat_line#*) }
    set -- \$stat_line
    [ "\${2:-}" = "\$root" ] && kill_tree "\$child"
  done
  kill -KILL "\$root" 2>/dev/null || true
}
read_lock() {
  if [ -f "\$OC_DIR/setup.lock" ]; then
    cat "\$OC_DIR/setup.lock"
  else
    cat "\$OC_DIR/setup.lock/owner" 2>/dev/null
  fi
}
lock_pid=''
lock_start=''
for _ in 1 2 3 4 5 6 7 8 9 10; do
  read -r lock_pid lock_start < <(read_lock) && break
  [ -d "\$OC_DIR/setup.lock" ] || break
  sleep 0.1
done
live_start=\$(process_start "\$lock_pid" 2>/dev/null || true)
if [ -n "\$lock_pid" ] && [ -n "\$lock_start" ] && [ "\$lock_start" = "\$live_start" ] &&
   kill -0 "\$lock_pid" 2>/dev/null; then
  lock_owned=1
  pid="\$lock_pid"
else
  lock_owned=0
  pid=\$(cat "\$OC_DIR/manager.pid" 2>/dev/null || true)
fi
case "\$pid" in
  ''|*[!0-9]*) ;;
  *)
    command=\$(tr '\\0' ' ' < "/proc/\$pid/cmdline" 2>/dev/null || true)
    case "\$command" in
      *"\$OC_DIR/manager.sh setup "*)
        kill_tree "\$pid"
        ;;
      *)
        if [ "\$lock_owned" = 1 ] && kill -0 "\$pid" 2>/dev/null; then
          echo 'bootstrap-owner-is-still-active' >&2
          exit 75
        fi
        ;;
    esac
    ;;
esac
current_pid=''
current_start=''
read -r current_pid current_start < <(read_lock) || true
if [ "\$current_pid" != "\$lock_pid" ] || [ "\$current_start" != "\$lock_start" ]; then
  echo 'setup-lock-owner-changed' >&2
  exit 75
fi
if [ -f "\$OC_DIR/setup.lock" ]; then
  rm -f "\$OC_DIR/setup.lock"
else
  rm -f "\$OC_DIR/setup.lock/owner" "\$OC_DIR/setup.lock"/owner.tmp.*
  rmdir "\$OC_DIR/setup.lock" 2>/dev/null || true
fi
rm -f "\$OC_DIR/manager.pid"
termux-wake-unlock >/dev/null 2>&1 || true
printf 'phase=stopped\nmessage=Bootstrap state cleared\nport=$port\nrunner=\nversion=\npid=\n' > "$termuxHome/.oc/state"
echo 'bootstrap-state-cleared'
''';

  static String managerScriptForTesting() => _managerScript;

  static String _shellQuote(String value) =>
      "'${value.replaceAll("'", "'\"'\"'")}'";

  static const _managerScript = r'''#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

OC_DIR="$HOME/.oc"
STATE="$OC_DIR/state"
MANAGER="$OC_DIR/manager.sh"
MANAGER_PID="$OC_DIR/manager.pid"
LOCK_DIR="$OC_DIR/setup.lock"
SERVER_PID="$OC_DIR/server.pid"
SERVER_LOG="$OC_DIR/server.log"
SERVER_LOG_ACTIVE="$OC_DIR/server-log.active"
PASSWORD_FILE="$OC_DIR/server.password"
LEGACY_MARKER="$OC_DIR/legacy-install"
UBUNTU_INSTALL_MARKER="$OC_DIR/opencode-ubuntu-installing"
SERVER_RUNNER="$OC_DIR/server-runner.sh"
PROOT_NAME=opencode-ubuntu
LOG_MAX_BYTES=1048576
LOG_BACKUPS=2
DISK_RESERVE_KIB=524288
FRESH_SETUP_REQUIRED_KIB=1572864
UPDATE_REQUIRED_KIB=786432
mkdir -p "$OC_DIR"

log_path() {
  case "${1:-}" in
    install) printf '%s' "$OC_DIR/install.log" ;;
    server) printf '%s' "$SERVER_LOG" ;;
    *) return 64 ;;
  esac
}

rotate_log() {
  local path
  path=$(log_path "${1:-}") || return 64
  local size=0
  if [ -f "$path" ]; then
    size=$(wc -c < "$path" 2>/dev/null || printf '0')
  fi
  case "$size" in ''|*[!0-9]*) size=0 ;; esac
  [ "$size" -eq 0 ] || {
    rm -f "$path.$LOG_BACKUPS"
    local index=$((LOG_BACKUPS - 1))
    while [ "$index" -ge 1 ]; do
      [ ! -f "$path.$index" ] || mv "$path.$index" "$path.$((index + 1))"
      index=$((index - 1))
    done
    mv "$path" "$path.1"
    local bounded="$path.1.tmp.$$"
    tail -c "$LOG_MAX_BYTES" "$path.1" > "$bounded"
    chmod 600 "$bounded"
    mv "$bounded" "$path.1"
  }
  : > "$path"
  chmod 600 "$path"
}

write_log() {
  local name="${1:-}"
  local path
  path=$(log_path "$name") || return 64
  touch "$path"
  chmod 600 "$path"
  local LC_ALL=C
  local size
  size=$(wc -c < "$path" 2>/dev/null || printf '0')
  case "$size" in ''|*[!0-9]*) size=0 ;; esac
  local line=''
  while IFS= read -r line || [ -n "$line" ]; do
    printf '%s\n' "$line" >> "$path"
    size=$((size + ${#line} + 1))
    if [ "$size" -ge "$LOG_MAX_BYTES" ]; then
      rotate_log "$name"
      size=0
    fi
    line=''
  done
}

install_server_runner() {
  local tmp="$SERVER_RUNNER.tmp.$$"
  cat > "$tmp" <<'OC_SERVER_RUNNER'
#!/data/data/com.termux/files/usr/bin/bash
set -uo pipefail
port="$1"
password_file="$2"
manager="$3"
"$manager" rotate-log server
proot-distro login opencode-ubuntu -- env \
  OPENCODE_SERVER_USERNAME=opencode \
  OPENCODE_SERVER_PASSWORD="$(cat "$password_file")" \
  opencode serve --hostname 127.0.0.1 --port "$port" \
  2>&1 | "$manager" write-log server
code="${PIPESTATUS[0]}"
"$manager" server-exited "$port" "$$" "$code" >/dev/null 2>&1 || true
exit "$code"
OC_SERVER_RUNNER
  chmod 700 "$tmp"
  mv "$tmp" "$SERVER_RUNNER"
}

write_state() {
  local phase="$1"
  local message="$2"
  local port="${3:-4096}"
  local runner="${4:-proot}"
  local version="${5:-}"
  local pid="${6:-}"
  local tmp="$STATE.tmp.$$"
  printf 'phase=%s\nmessage=%s\nport=%s\nrunner=%s\nversion=%s\npid=%s\n' \
    "$phase" "$message" "$port" "$runner" "$version" "$pid" > "$tmp"
  mv "$tmp" "$STATE"
}

fail_setup() {
  local message="$1"
  local port="${2:-4096}"
  trap - ERR
  write_state failed "$message" "$port"
  printf '[oc] ERROR: %s\n' "$message"
  exit 1
}

on_setup_error() {
  local code=$?
  local line="${BASH_LINENO[0]:-unknown}"
  local stage
  stage=$(read_state_value message)
  [ -n "$stage" ] || stage='Setup'
  trap - ERR
  write_state failed "$stage failed (exit $code; setup line $line)" "$CURRENT_PORT"
  printf '[oc] ERROR: %s failed at setup line %s (exit %s)\n' "$stage" "$line" "$code"
  exit "$code"
}

read_state_value() {
  local key="$1"
  [ -f "$STATE" ] || return 0
  while IFS='=' read -r name value; do
    if [ "$name" = "$key" ]; then
      printf '%s' "$value"
      return 0
    fi
  done < "$STATE"
}

process_command() {
  local pid="$1"
  [ -r "/proc/$pid/cmdline" ] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null
}

process_start() {
  local stat_line
  stat_line=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
  stat_line=${stat_line#*) }
  set -- $stat_line
  printf '%s' "${20:-}"
}

claim_setup_lock() {
  local expected_pid="$1"
  local expected_start="$2"
  local owner_pid=""
  local owner_start=""
  read -r owner_pid owner_start < "$LOCK_DIR/owner" 2>/dev/null || return 1
  [ "$owner_pid" = "$expected_pid" ] && [ "$owner_start" = "$expected_start" ] || return 1
  local self_start
  self_start=$(process_start "$$") || return 1
  printf '%s %s\n' "$$" "$self_start" > "$LOCK_DIR/owner.tmp.$$"
  mv "$LOCK_DIR/owner.tmp.$$" "$LOCK_DIR/owner"
}

read_setup_lock() {
  if [ -f "$LOCK_DIR" ]; then
    cat "$LOCK_DIR"
  else
    cat "$LOCK_DIR/owner" 2>/dev/null
  fi
}

clear_setup_lock() {
  if [ -f "$LOCK_DIR" ]; then
    rm -f "$LOCK_DIR"
  else
    rm -f "$LOCK_DIR/owner" "$LOCK_DIR"/owner.tmp.*
    rmdir "$LOCK_DIR" 2>/dev/null || true
  fi
}

clear_setup_lock_if_owner() {
  local expected_pid="$1"
  local expected_start="$2"
  local current_pid=""
  local current_start=""
  read -r current_pid current_start < <(read_setup_lock) || true
  if [ "$current_pid" = "$expected_pid" ] && [ "$current_start" = "$expected_start" ]; then
    clear_setup_lock
    return 0
  fi
  echo 'setup-lock-owner-changed' >&2
  return 75
}

kill_tree() {
  local root="$1"
  kill -STOP "$root" 2>/dev/null || return 0
  local stat_file stat_line child
  for stat_file in /proc/[0-9]*/stat; do
    stat_line=$(cat "$stat_file" 2>/dev/null || true)
    [ -n "$stat_line" ] || continue
    child=${stat_file#/proc/}
    child=${child%/stat}
    stat_line=${stat_line#*) }
    set -- $stat_line
    if [ "${2:-}" = "$root" ]; then
      kill_tree "$child"
    fi
  done
  kill -KILL "$root" 2>/dev/null || true
}

setup_process() {
  local pid="$1"
  local command
  command=$(process_command "$pid" || true)
  case "$command" in
    *"$MANAGER setup "*) return 0 ;;
    *) return 1 ;;
  esac
}

server_process() {
  local pid="$1"
  local port="$2"
  local command
  command=$(process_command "$pid" || true)
  case "$command" in
    *"$SERVER_RUNNER $port "*) return 0 ;;
    *) return 1 ;;
  esac
}

stop_server() {
  local port="${1:-4096}"
  local pid=""
  if [ -f "$SERVER_PID" ]; then
    pid=$(cat "$SERVER_PID" 2>/dev/null || true)
  fi
  case "$pid" in
    ''|*[!0-9]*) ;;
    *)
      if kill -0 "$pid" 2>/dev/null && server_process "$pid" "$port"; then
        kill_tree "$pid"
      fi
      ;;
  esac
  rm -f "$SERVER_PID"

}

stop_legacy_server() {
  local port="${1:-4096}"
  [ -f "$LEGACY_MARKER" ] || return 0
  local legacy_pid
  for legacy_pid in $(pgrep -f "[o]pencode serve --hostname 127.0.0.1 --port $port" 2>/dev/null || true); do
    kill "$legacy_pid" 2>/dev/null || true
  done
  rm -f "$LEGACY_MARKER"
}

release_setup_lock() {
  local lock_pid=""
  local lock_start=""
  local self_start
  self_start=$(process_start "$$" || true)
  read -r lock_pid lock_start < <(read_setup_lock) || true
  if [ "$lock_pid" = "$$" ] && [ -n "$self_start" ] && [ "$lock_start" = "$self_start" ]; then
    clear_setup_lock_if_owner "$lock_pid" "$lock_start"
  fi
}

stop_setup() {
  local lock_pid=""
  local lock_start=""
  local live_start
  read -r lock_pid lock_start < <(read_setup_lock) || true
  live_start=$(process_start "$lock_pid" 2>/dev/null || true)
  local pid lock_owned
  if [ -n "$lock_pid" ] && [ -n "$lock_start" ] && [ "$lock_start" = "$live_start" ] &&
     kill -0 "$lock_pid" 2>/dev/null; then
    lock_owned=1
    pid="$lock_pid"
  else
    lock_owned=0
    pid=$(cat "$MANAGER_PID" 2>/dev/null || true)
  fi
  case "$pid" in
    ''|*[!0-9]*) ;;
    *)
      if [ "$pid" != "$$" ] && kill -0 "$pid" 2>/dev/null; then
        if setup_process "$pid"; then
          kill_tree "$pid"
        elif [ "$lock_owned" = 1 ]; then
          echo 'bootstrap-owner-is-still-active' >&2
          return 75
        fi
      fi
      ;;
  esac
  rm -f "$MANAGER_PID"
  clear_setup_lock_if_owner "$lock_pid" "$lock_start"
}

cleanup_setup() {
  rm -f "$MANAGER_PID"
  rm -f "$OC_DIR/ubuntu-base.tar.gz" "$OC_DIR/ubuntu-base.tar.gz.tmp"
  release_setup_lock
  if [ "${SETUP_SUCCEEDED:-0}" != 1 ]; then
    if [ "${SERVER_STARTED:-0}" = 1 ]; then
      stop_server "$CURRENT_PORT"
    fi
    termux-wake-unlock >/dev/null 2>&1 || true
  fi
}

ubuntu_rootfs_exists() {
  [ -d "$PREFIX/var/lib/proot-distro/containers/$PROOT_NAME/rootfs" ] ||
    [ -d "$PREFIX/var/lib/proot-distro/installed-rootfs/$PROOT_NAME" ]
}

ubuntu_usable() {
  ubuntu_rootfs_exists &&
    proot-distro login "$PROOT_NAME" -- true >/dev/null 2>&1
}

cleanup_app_owned_partial_install() {
  rm -f "$OC_DIR/ubuntu-base.tar.gz" "$OC_DIR/ubuntu-base.tar.gz.tmp"
  if [ -f "$UBUNTU_INSTALL_MARKER" ] && ubuntu_rootfs_exists && ! ubuntu_usable; then
    command -v proot-distro >/dev/null 2>&1 || return 0
    printf '[oc] removing interrupted app-owned Ubuntu install\n'
    proot-distro remove "$PROOT_NAME" >/dev/null 2>&1 ||
      fail_setup 'Could not remove the interrupted app-owned Ubuntu install' "$CURRENT_PORT"
    rm -f "$UBUNTU_INSTALL_MARKER"
  fi
}

cleanup_legacy_npm_cache() {
  ubuntu_usable || return 0
  # Older installer revisions used npm's persistent cache. A failed download
  # can leave hundreds of MiB of corrupt entries there, then make the storage
  # preflight fail before the next repair attempt can start.
  proot-distro login "$PROOT_NAME" -- npm cache clean --force >/dev/null 2>&1 || true
}

require_setup_space() {
  local required_kib="$FRESH_SETUP_REQUIRED_KIB"
  if ubuntu_usable; then
    required_kib="$UPDATE_REQUIRED_KIB"
  fi
  local disk_line available_kib
  disk_line=$(df -Pk "$HOME" 2>/dev/null | tail -n 1) ||
    fail_setup 'Could not check available storage before setup' "$CURRENT_PORT"
  set -- $disk_line
  available_kib="${4:-}"
  case "$available_kib" in
    ''|*[!0-9]*) fail_setup 'Could not read available storage before setup' "$CURRENT_PORT" ;;
  esac
  if [ "$available_kib" -lt "$required_kib" ]; then
    local required_mib=$((required_kib / 1024))
    local available_mib=$((available_kib / 1024))
    local reserve_mib=$((DISK_RESERVE_KIB / 1024))
    fail_setup "Not enough storage: ${available_mib} MiB free; setup needs ${required_mib} MiB including a ${reserve_mib} MiB safety reserve" "$CURRENT_PORT"
  fi
  printf '[oc] storage preflight: %s MiB free; preserving %s MiB reserve\n' \
    "$((available_kib / 1024))" "$((DISK_RESERVE_KIB / 1024))"
}

select_official_termux_repository() {
  local source_file="$PREFIX/etc/apt/sources.list"
  local desired_source='deb https://packages.termux.dev/apt/termux-main stable main'
  mkdir -p "$PREFIX/etc/apt"
  # Keep the user's previous main-repository selection recoverable. Other
  # optional Termux repositories in sources.list.d are deliberately untouched.
  if [ -s "$source_file" ] && [ ! -e "$source_file.oc-before-opencode" ]; then
    cp "$source_file" "$source_file.oc-before-opencode" || return 1
  fi
  local source_tmp="$source_file.oc-tmp.$$"
  printf '%s\n' "$desired_source" > "$source_tmp" || return 1
  chmod 644 "$source_tmp" || return 1
  mv "$source_tmp" "$source_file" || return 1
  printf '[oc] selected official Termux repository: packages.termux.dev\n'
}

termux_main_repository_configured() {
  local source_file
  for source_file in \
    "$PREFIX/etc/apt/sources.list" \
    "$PREFIX/etc/apt/sources.list.d/"*.list \
    "$PREFIX/etc/apt/sources.list.d/"*.sources; do
    [ -f "$source_file" ] || continue
    if [ -n "$(grep -Ev '^[[:space:]]*(#|$)' "$source_file" 2>/dev/null || true)" ]; then
      return 0
    fi
  done
  return 1
}

proot_supports_named_containers() {
  local help_output
  help_output=$(proot-distro install --help 2>&1) || return 1
  case "$help_output" in
    *--name*) return 0 ;;
    *) return 1 ;;
  esac
}

termux_dependencies_healthy() {
  command -v curl >/dev/null 2>&1 || return 1
  command -v proot-distro >/dev/null 2>&1 || return 1
  curl --version >/dev/null 2>&1 || return 1
  proot_supports_named_containers
}

prepare_termux_dependencies() {
  if termux_dependencies_healthy; then
    printf '[oc] existing Termux dependencies are healthy; package upgrade skipped\n'
    return 0
  fi

  export DEBIAN_FRONTEND=noninteractive
  write_state installing_dependencies 'Refreshing Termux packages' "$CURRENT_PORT"
  if termux_main_repository_configured; then
    if ! apt-get update; then
      printf '[oc] current Termux repository failed; switching the main repository to packages.termux.dev\n'
      select_official_termux_repository ||
        fail_setup 'Could not select the official Termux package repository' "$CURRENT_PORT"
      apt-get update ||
        fail_setup 'Could not refresh packages.termux.dev; check the network and retry' "$CURRENT_PORT"
    fi
  else
    select_official_termux_repository ||
      fail_setup 'Could not select the official Termux package repository' "$CURRENT_PORT"
    apt-get update ||
      fail_setup 'Could not refresh packages.termux.dev; check the network and retry' "$CURRENT_PORT"
  fi

  # A partial dependency install can leave libcurl ahead of OpenSSL. Repair the
  # entire package set first, keeping existing config files non-interactively.
  write_state installing_dependencies 'Repairing the Termux package set' "$CURRENT_PORT"
  apt-get -y --no-remove -o Dpkg::Options::="--force-confold" --fix-broken install ||
    fail_setup 'Could not repair the interrupted Termux package transaction' "$CURRENT_PORT"
  apt-get -y --no-remove -o Dpkg::Options::="--force-confold" upgrade ||
    fail_setup 'Could not complete the safe Termux package upgrade' "$CURRENT_PORT"

  write_state installing_dependencies 'Installing Termux dependencies' "$CURRENT_PORT"
  apt-get -y --no-remove -o Dpkg::Options::="--force-confold" install \
    proot-distro curl openssl ||
    fail_setup 'Could not install the Termux dependencies' "$CURRENT_PORT"
  termux_dependencies_healthy ||
    fail_setup 'Termux dependencies are still unusable after the package repair' "$CURRENT_PORT"
}

install_ubuntu_base() {
  local archive="$OC_DIR/ubuntu-base.tar.gz"
  local base_url='https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release'
  local filename checksum
  case "$(uname -m)" in
    aarch64|arm64)
      filename='ubuntu-base-24.04.4-base-arm64.tar.gz'
      checksum='04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2'
      ;;
    arm|armv7l|armv8l)
      filename='ubuntu-base-24.04.4-base-armhf.tar.gz'
      checksum='991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4'
      ;;
    x86_64|amd64)
      filename='ubuntu-base-24.04.4-base-amd64.tar.gz'
      checksum='c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58'
      ;;
    *) fail_setup "Unsupported CPU architecture: $(uname -m)" "$CURRENT_PORT" ;;
  esac

  rm -f "$archive"
  curl --fail --location --retry 5 --retry-all-errors --connect-timeout 20 \
    "$base_url/$filename" -o "$archive"
  printf '%s  %s\n' "$checksum" "$archive" | sha256sum -c -

  if ubuntu_rootfs_exists; then
    [ -f "$UBUNTU_INSTALL_MARKER" ] ||
      fail_setup 'An existing Ubuntu container is not usable; setup will not delete it' "$CURRENT_PORT"
    proot-distro remove "$PROOT_NAME" >/dev/null 2>&1 ||
      fail_setup 'Could not remove the interrupted app-owned Ubuntu install' "$CURRENT_PORT"
    if ubuntu_usable; then
      rm -f "$UBUNTU_INSTALL_MARKER"
      return
    fi
  fi
  printf 'source=canonical-ubuntu-base-24.04.4\n' > "$UBUNTU_INSTALL_MARKER"
  proot-distro install "$archive" --name "$PROOT_NAME"
  rm -f "$archive"
  ubuntu_usable || fail_setup 'Ubuntu Base extraction did not create a usable container' "$CURRENT_PORT"
  rm -f "$UBUNTU_INSTALL_MARKER"
}

setup() {
  CURRENT_PORT="${1:-4096}"
  local requested_version="${2:-1.18.25}"
  local dispatcher_pid="${3:-}"
  local dispatcher_start="${4:-}"
  SETUP_SUCCEEDED=0
  SERVER_STARTED=0
  if ! claim_setup_lock "$dispatcher_pid" "$dispatcher_start"; then
    write_state failed 'Setup manager could not claim its launch lock' "$CURRENT_PORT"
    rm -f "$MANAGER_PID"
    return 75
  fi
  trap on_setup_error ERR
  trap cleanup_setup EXIT
  termux-wake-lock >/dev/null 2>&1 || true
  write_state preparing 'Preparing Termux' "$CURRENT_PORT"
  printf '\n[oc] setup started at %s\n' "$(date -Iseconds 2>/dev/null || date)"

  cleanup_app_owned_partial_install
  cleanup_legacy_npm_cache
  require_setup_space

  prepare_termux_dependencies

  if [ -f "$UBUNTU_INSTALL_MARKER" ] || ! ubuntu_usable; then
    write_state installing_ubuntu 'Installing Ubuntu environment' "$CURRENT_PORT"
    install_ubuntu_base
  fi

  write_state installing_opencode 'Installing OpenCode' "$CURRENT_PORT"
  proot-distro login "$PROOT_NAME" -- env OC_REQUESTED_VERSION="$requested_version" bash -s <<'OC_PROOT_SETUP'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
if ! command -v npm >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  apt-get update -y -o Acquire::Retries=5
  apt-get install -y nodejs npm curl ca-certificates -o Acquire::Retries=5
fi
export NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--dns-result-order=ipv4first"
install_opencode() {
  local npm_cache
  local install_code
  npm_cache=$(mktemp -d /tmp/opencode-mobile-npm.XXXXXX)
  if npm install -g \
    --cache "$npm_cache" \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=60000 \
    --fetch-timeout=300000 \
    "opencode-ai@$OC_REQUESTED_VERSION"; then
    install_code=0
  else
    install_code=$?
  fi
  rm -rf -- "$npm_cache"
  return "$install_code"
}
install_opencode || {
  printf '[oc] npm download failed; retrying in 10 seconds\n'
  sleep 10
  install_opencode
}
opencode --version
OC_PROOT_SETUP

  local installed_version
  installed_version=$(proot-distro login "$PROOT_NAME" -- opencode --version 2>/dev/null | tr -d '\r\n')
  [ -n "$installed_version" ] || fail_setup 'OpenCode installed but did not report a version' "$CURRENT_PORT"
  write_state refreshing_models 'Refreshing the OpenCode model catalog' "$CURRENT_PORT" proot "$installed_version"
  proot-distro login "$PROOT_NAME" -- opencode models --refresh >/dev/null ||
    fail_setup 'OpenCode updated, but its model catalog could not be refreshed' "$CURRENT_PORT"
  [ -s "$PASSWORD_FILE" ] || fail_setup 'The local server password is missing' "$CURRENT_PORT"
  local password
  password=$(cat "$PASSWORD_FILE")

  stop_legacy_server "$CURRENT_PORT"
  stop_server "$CURRENT_PORT"
  install_server_runner
  : > "$SERVER_LOG_ACTIVE"
  write_state starting_server 'Starting the local server' "$CURRENT_PORT" proot "$installed_version"
  nohup "$SERVER_RUNNER" "$CURRENT_PORT" "$PASSWORD_FILE" "$MANAGER" \
    >/dev/null 2>&1 </dev/null &
  local server_pid=$!
  SERVER_STARTED=1
  printf '%s\n' "$server_pid" > "$SERVER_PID"

  for _ in {1..30}; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      fail_setup 'OpenCode server exited during startup' "$CURRENT_PORT"
    fi
    if (exec 3<>"/dev/tcp/127.0.0.1/$CURRENT_PORT") 2>/dev/null; then
      exec 3>&-
      exec 3<&-
      local auth_codes
      auth_codes=$(proot-distro login "$PROOT_NAME" -- env \
        OC_PORT="$CURRENT_PORT" OC_PASSWORD="$password" bash -s <<'OC_AUTH_CHECK'
unauth=$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' \
  "http://127.0.0.1:$OC_PORT/global/health" || true)
auth=$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' \
  -u "opencode:$OC_PASSWORD" "http://127.0.0.1:$OC_PORT/global/health" || true)
printf '%s %s' "$unauth" "$auth"
OC_AUTH_CHECK
)
      if [ "$auth_codes" = '401 200' ]; then
        write_state ready 'OpenCode is ready' "$CURRENT_PORT" proot "$installed_version" "$server_pid"
        printf '[oc] authenticated server ready on 127.0.0.1:%s\n' "$CURRENT_PORT"
        SETUP_SUCCEEDED=1
        return 0
      fi
    fi
    sleep 1
  done
  fail_setup 'OpenCode server did not become authenticated and ready within 30 seconds' "$CURRENT_PORT"
}

status() {
  if [ ! -f "$STATE" ]; then
    printf 'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\nversion=\npid=\n'
    return 0
  fi
  local phase
  phase=$(read_state_value phase)
  if [ "$phase" = ready ]; then
    local pid
    pid=$(cat "$SERVER_PID" 2>/dev/null || true)
    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null || ! server_process "$pid" "$(read_state_value port)"; then
      write_state failed 'The local OpenCode server stopped unexpectedly' "$(read_state_value port)"
      rm -f "$SERVER_PID" "$SERVER_LOG_ACTIVE"
      termux-wake-unlock >/dev/null 2>&1 || true
    fi
  fi
  case "$phase" in
    queued|preparing|installing_dependencies|installing_ubuntu|installing_opencode|refreshing_models|starting_server)
      local manager_pid
      manager_pid=$(cat "$MANAGER_PID" 2>/dev/null || true)
      if [ -z "$manager_pid" ] || ! kill -0 "$manager_pid" 2>/dev/null || ! setup_process "$manager_pid"; then
        write_state failed 'Setup stopped unexpectedly; see live output for details' "$(read_state_value port)"
      fi
      ;;
  esac
  cat "$STATE"
}

server_exited() {
  local port="${1:-4096}"
  local runner_pid="${2:-}"
  local code="${3:-1}"
  local current_pid
  current_pid=$(cat "$SERVER_PID" 2>/dev/null || true)
  [ -n "$runner_pid" ] && [ "$current_pid" = "$runner_pid" ] || return 0
  rm -f "$SERVER_PID" "$SERVER_LOG_ACTIVE"
  write_state failed "OpenCode server exited (code $code)" "$port"
  termux-wake-unlock >/dev/null 2>&1 || true
}

diagnostics() {
  printf '%s\n' '===== OpenCode on-device status ====='
  status
  printf '%s\n' '===== setup lock ====='
  if [ -f "$LOCK_DIR" ]; then
    printf '%s\n' 'Legacy file lock:'
    cat "$LOCK_DIR"
  elif [ -d "$LOCK_DIR" ]; then
    read_setup_lock || echo 'Directory lock has no owner'
  else
    echo 'No setup lock'
  fi
  printf '%s\n' '===== install.log (last 120 lines) ====='
  tail -n 120 "$OC_DIR/install.log" 2>/dev/null || true
  printf '%s\n' '===== server.log (last 80 lines) ====='
  tail -n 80 "$SERVER_LOG" 2>/dev/null || true
}

stop() {
  local port="${1:-4096}"
  write_state stopping 'Stopping the local server' "$port"
  stop_setup
  stop_legacy_server "$port"
  stop_server "$port"
  termux-wake-unlock >/dev/null 2>&1 || true
  write_state stopped 'Local server stopped' "$port"
  echo '[oc] server stopped'
}

case "${1:-status}" in
  setup) shift; setup "$@" ;;
  status) status ;;
  diagnostics) diagnostics ;;
  stop) shift; stop "$@" ;;
  server-exited) shift; server_exited "$@" ;;
  rotate-log) shift; rotate_log "$@" ;;
  write-log) shift; write_log "$@" ;;
  *) echo "usage: $0 {setup|status|diagnostics|stop}" >&2; exit 64 ;;
esac
''';
}

class TermuxCapabilities {
  final bool installed;
  final String? version;
  final bool serviceAvailable;
  final bool protocolSupported;
  final bool permissionGranted;

  const TermuxCapabilities({
    required this.installed,
    required this.version,
    required this.serviceAvailable,
    required this.protocolSupported,
    required this.permissionGranted,
    this.platformSupported = true,
  });

  /// What a platform without a Termux bridge reports: nothing is installed,
  /// nothing is granted, and — unlike an Android phone that simply has not
  /// installed Termux yet — [platformSupported] says installing it would not
  /// help. Callers use that to choose between "install Termux" and "this is
  /// not a thing here".
  const TermuxCapabilities.unavailable()
    : installed = false,
      version = null,
      serviceAvailable = false,
      protocolSupported = false,
      permissionGranted = false,
      platformSupported = false;

  /// False when the running platform has no Termux bridge at all.
  final bool platformSupported;

  factory TermuxCapabilities.fromMap(Map<String, dynamic> map) =>
      TermuxCapabilities(
        installed: map['installed'] == true,
        version: map['version']?.toString(),
        serviceAvailable: map['serviceAvailable'] == true,
        protocolSupported: map['protocolSupported'] == true,
        permissionGranted: map['permissionGranted'] == true,
      );
}

class TermuxCommandResult {
  final String stdout;
  final String stderr;
  final int exitCode;
  final int errorCode;
  final String errorMessage;

  const TermuxCommandResult({
    required this.stdout,
    required this.stderr,
    required this.exitCode,
    required this.errorCode,
    required this.errorMessage,
  });

  bool get successful => errorCode == -1 && exitCode == 0;

  String get failureMessage {
    final details = [
      errorMessage.trim(),
      stderr.trim(),
    ].where((part) => part.isNotEmpty).join('\n');
    return details.isEmpty
        ? 'Termux command failed (error $errorCode, exit $exitCode).'
        : details;
  }

  factory TermuxCommandResult.fromMap(Map<String, dynamic> map) =>
      TermuxCommandResult(
        stdout: map['stdout']?.toString() ?? '',
        stderr: map['stderr']?.toString() ?? '',
        exitCode: (map['exitCode'] as num?)?.toInt() ?? -1,
        errorCode: (map['err'] as num?)?.toInt() ?? -1,
        errorMessage: map['errorMessage']?.toString() ?? '',
      );
}

class TermuxSetupStatus {
  final String phase;
  final String message;
  final int port;
  final String runner;
  final String version;
  final int? pid;

  const TermuxSetupStatus({
    required this.phase,
    required this.message,
    required this.port,
    required this.runner,
    required this.version,
    required this.pid,
  });

  bool get isRunning => const {
    'queued',
    'preparing',
    'installing_dependencies',
    'installing_ubuntu',
    'installing_opencode',
    'starting_server',
  }.contains(phase);
  bool get isReady => phase == 'ready';
  bool get isFailed => phase == 'failed';

  factory TermuxSetupStatus.parse(String output) {
    final values = <String, String>{};
    for (final line in output.split('\n')) {
      final separator = line.indexOf('=');
      if (separator <= 0) continue;
      values[line.substring(0, separator)] = line.substring(separator + 1);
    }
    return TermuxSetupStatus(
      phase: values['phase'] ?? 'unknown',
      message: values['message'] ?? 'Unknown setup state',
      port: int.tryParse(values['port'] ?? '') ?? 4096,
      runner: values['runner'] ?? '',
      version: values['version'] ?? '',
      pid: int.tryParse(values['pid'] ?? ''),
    );
  }
}

class TermuxSetupSnapshot {
  static const _marker = '__OC_SETUP_OUTPUT__';

  final TermuxSetupStatus status;
  final String output;

  const TermuxSetupSnapshot({required this.status, required this.output});

  factory TermuxSetupSnapshot.parse(String raw) {
    final markerIndex = raw.indexOf(_marker);
    if (markerIndex < 0) {
      return TermuxSetupSnapshot(
        status: TermuxSetupStatus.parse(raw),
        output: '',
      );
    }
    return TermuxSetupSnapshot(
      status: TermuxSetupStatus.parse(raw.substring(0, markerIndex)),
      output: raw.substring(markerIndex + _marker.length).trim(),
    );
  }
}

class TermuxBridgeException implements Exception {
  final String message;
  final String code;

  const TermuxBridgeException(this.message, {this.code = 'termux_error'});

  @override
  String toString() => message;
}
