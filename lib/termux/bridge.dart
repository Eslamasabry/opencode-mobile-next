import 'package:flutter/services.dart';

class TermuxBridge {
  static const _channel = MethodChannel('oc/termux');

  static const termuxHome = '/data/data/com.termux/files/home';
  static const _managerPath = '$termuxHome/.oc/manager.sh';

  static Future<TermuxCapabilities> capabilities() async {
    final raw = await _channel.invokeMapMethod<String, dynamic>(
      'getCapabilities',
    );
    return TermuxCapabilities.fromMap(raw ?? const {});
  }

  static Future<bool> requestPermission() async =>
      await _channel.invokeMethod<bool>('requestRunCommandPermission') ?? false;

  static Future<bool> openTermux() async =>
      await _channel.invokeMethod<bool>('openTermux') ?? false;

  static Future<bool> openAppSettings() async =>
      await _channel.invokeMethod<bool>('openAppSettings') ?? false;

  static Future<TermuxCommandResult> run(
    String script, {
    bool background = true,
    String? workdir,
    Duration timeout = const Duration(seconds: 30),
  }) async {
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

  static Future<TermuxSetupStatus> status() async {
    final result = await run(statusScript());
    return TermuxSetupStatus.parse(result.stdout);
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
    String version = 'latest',
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
    echo 'setup-lock-owner-missing; use Stop & retry' >&2
  else
    echo 'setup-lock-stale; use Stop & retry' >&2
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
nohup "\$MANAGER" setup '$port' '$version' "\$\$" "\$self_start" >> "\$OC_DIR/install.log" 2>&1 </dev/null &
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
PASSWORD_FILE="$OC_DIR/server.password"
LEGACY_MARKER="$OC_DIR/legacy-install"
mkdir -p "$OC_DIR"

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
  trap - ERR
  write_state failed "Setup failed at line $line (exit $code)" "$CURRENT_PORT"
  printf '[oc] ERROR: setup failed at line %s (exit %s)\n' "$line" "$code"
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
    *opencode*serve*--hostname*127.0.0.1*--port*"$port"*) return 0 ;;
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
        kill "$pid" 2>/dev/null || true
        for _ in {1..10}; do
          kill -0 "$pid" 2>/dev/null || break
          sleep 0.2
        done
        kill -9 "$pid" 2>/dev/null || true
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
  release_setup_lock
  if [ "${SETUP_SUCCEEDED:-0}" != 1 ]; then
    if [ "${SERVER_STARTED:-0}" = 1 ]; then
      stop_server "$CURRENT_PORT"
    fi
    termux-wake-unlock >/dev/null 2>&1 || true
  fi
}

setup() {
  CURRENT_PORT="${1:-4096}"
  local requested_version="${2:-latest}"
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

  if ! command -v proot-distro >/dev/null 2>&1; then
    write_state installing_dependencies 'Installing Termux dependencies' "$CURRENT_PORT"
    pkg update -y
    pkg install -y proot-distro
  fi

  if [ ! -d "$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ]; then
    write_state installing_ubuntu 'Installing Ubuntu environment' "$CURRENT_PORT"
    proot-distro install ubuntu
  fi

  write_state installing_opencode 'Installing OpenCode' "$CURRENT_PORT"
  proot-distro login ubuntu -- env OC_REQUESTED_VERSION="$requested_version" bash -s <<'OC_PROOT_SETUP'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
if ! command -v npm >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  apt-get update -y -o Acquire::Retries=5
  apt-get install -y nodejs npm curl ca-certificates -o Acquire::Retries=5
fi
npm install -g "opencode-ai@$OC_REQUESTED_VERSION" || {
  sleep 5
  npm install -g "opencode-ai@$OC_REQUESTED_VERSION"
}
opencode --version
OC_PROOT_SETUP

  local installed_version
  installed_version=$(proot-distro login ubuntu -- opencode --version 2>/dev/null | tr -d '\r\n')
  [ -n "$installed_version" ] || fail_setup 'OpenCode installed but did not report a version' "$CURRENT_PORT"
  [ -s "$PASSWORD_FILE" ] || fail_setup 'The local server password is missing' "$CURRENT_PORT"
  local password
  password=$(cat "$PASSWORD_FILE")

  write_state starting_server 'Starting the local server' "$CURRENT_PORT" proot "$installed_version"
  stop_legacy_server "$CURRENT_PORT"
  stop_server "$CURRENT_PORT"
  : > "$SERVER_LOG"
  nohup proot-distro login ubuntu -- env \
    OPENCODE_SERVER_USERNAME=opencode \
    OPENCODE_SERVER_PASSWORD="$password" \
    opencode serve --hostname 127.0.0.1 --port "$CURRENT_PORT" \
    >> "$SERVER_LOG" 2>&1 </dev/null &
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
      auth_codes=$(proot-distro login ubuntu -- env \
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
    fi
  fi
  case "$phase" in
    queued|preparing|installing_dependencies|installing_ubuntu|installing_opencode|starting_server)
      local manager_pid
      manager_pid=$(cat "$MANAGER_PID" 2>/dev/null || true)
      if [ -z "$manager_pid" ] || ! kill -0 "$manager_pid" 2>/dev/null || ! setup_process "$manager_pid"; then
        write_state failed 'Setup stopped unexpectedly; open diagnostics for details' "$(read_state_value port)"
      fi
      ;;
  esac
  cat "$STATE"
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
  });

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

class TermuxBridgeException implements Exception {
  final String message;
  final String code;

  const TermuxBridgeException(this.message, {this.code = 'termux_error'});

  @override
  String toString() => message;
}
