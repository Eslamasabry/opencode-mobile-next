import '../../domain/phone_agent_context.dart';
import '../opencode_ubuntu_setup.dart';

/// The app-managed server's manager, installed as `~/.oc/manager.sh`.
const termuxManagerScript =
    r'''#!/data/data/com.termux/files/usr/bin/bash
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
RUNTIME_FILE="$OC_DIR/runtime"
RECOVERY_PERMIT="$OC_DIR/recovery-permit"
SWITCH_FILE="$OC_DIR/runtime-switch"
V2_DATA_MODE="$OC_DIR/opencode2-data-mode"
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

managed_runtime() {
  local runtime="${CURRENT_RUNTIME:-$(cat "$RUNTIME_FILE" 2>/dev/null || true)}"
  case "$runtime" in
    ''|opencode1) printf opencode1 ;;
    opencode2) printf opencode2 ;;
    *) echo 'unsupported-managed-runtime' >&2; return 64 ;;
  esac
}

runtime_command() {
  case "$(managed_runtime)" in
    opencode1) printf opencode ;;
    opencode2) printf opencode2 ;;
    *) return 64 ;;
  esac
}

runtime_version() {
  local runtime binary version
  runtime=$(managed_runtime) || return
  binary=$(runtime_command) || return
  version=$(proot-distro login "$PROOT_NAME" -- "$binary" --version) || return
  if [ "$runtime" = opencode2 ]; then version=${version#opencode2 v}; version=${version#opencode v}; fi
  printf '%s' "$version" | tr -d '\r\n'
}

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
runtime="${4:-opencode1}"
case "$runtime" in
  opencode1) binary=opencode ;;
  opencode2) binary=opencode2 ;;
  *) exit 64 ;;
esac
runtime_env=()
data_mode=$(cat "$HOME/.oc/opencode2-data-mode" 2>/dev/null || true)
case "$data_mode" in ''|default|isolated) ;; *) exit 64 ;; esac
if [ "$runtime" = opencode2 ] && [ "$data_mode" = isolated ]; then
  # The beta honors these overrides before reading its global configuration.
  # Clear only conflicting OpenCode config inputs, retaining Termux/tool paths.
  unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT
  runtime_env=(
    -u OPENCODE_CONFIG -u OPENCODE_CONFIG_CONTENT
    XDG_DATA_HOME=/root/.oc-opencode2/data
    XDG_CACHE_HOME=/root/.oc-opencode2/cache
    XDG_STATE_HOME=/root/.oc-opencode2/state
    XDG_CONFIG_HOME=/root/.oc-opencode2/config
    OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode
    OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db
  )
fi
# Tell the agent where it runs: the Android phone, through the app
# (lib/domain/phone_agent_context.dart). Never blocks the start.
proot-distro login opencode-ubuntu -- sh -s >/dev/null 2>&1 <<'OC_PHONE_CONTEXT' || true
''' +
    PhoneAgentContext.termuxScript +
    r'''OC_PHONE_CONTEXT
"$manager" rotate-log server
# The server never runs from the container's home folder: OpenCode would watch
# and scan every dotfile and cache under it. Projects live in /root/projects,
# and the app still asks the user to create or open a folder inside it.
proot-distro login opencode-ubuntu -- mkdir -p /root/projects >/dev/null 2>&1 || true
if [ "${#runtime_env[@]}" -gt 0 ]; then
  proot-distro login opencode-ubuntu -- mkdir -p /root/.oc-opencode2/data/opencode \
    /root/.oc-opencode2/cache /root/.oc-opencode2/state /root/.oc-opencode2/config/opencode || exit 74
fi
proot-distro login --work-dir /root/projects opencode-ubuntu -- env \
  "${runtime_env[@]}" \
  OPENCODE_SERVER_USERNAME=opencode \
  OPENCODE_SERVER_PASSWORD="$(cat "$password_file")" \
  OPENCODE_PASSWORD="$(cat "$password_file")" \
  "$binary" serve --hostname 127.0.0.1 --port "$port" \
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
  local operation_result="${7:-}"
  # Phase/status writers retain the accepted operation's clock. Only a new
  # dispatcher or accepted restart initializes it; legacy state stays unknown.
  local started_at="${CURRENT_STARTED_AT-$(read_state_value started_at)}"
  local runtime
  runtime=$(managed_runtime) || return 64
  local tmp="$STATE.tmp.$$"
  printf 'phase=%s\nmessage=%s\nport=%s\nrunner=%s\nversion=%s\npid=%s\noperation=%s\noperation_result=%s\nfailure_kind=%s\nrecovery_token=%s\nstarted_at=%s\nruntime=%s\n' \
    "$phase" "$message" "$port" "$runner" "$version" "$pid" \
    "${CURRENT_OPERATION:-}" "$operation_result" "${8:-${CURRENT_RECOVERY:+recovery}}" "${CURRENT_RECOVERY:-}" "$started_at" "$runtime" > "$tmp"
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

fail_restart_preflight() {
  local message="$1"
  if [ "${OLD_SERVER_LIVE:-0}" = 1 ]; then
    trap - ERR
    write_state ready "$message; the original server is still running" \
      "$CURRENT_PORT" proot "$OLD_SERVER_VERSION" "$OLD_SERVER_PID" not_performed
    printf '[oc] ERROR: %s\n' "$message"
    exit 1
  fi
  fail_setup "$message" "$CURRENT_PORT"
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
  stat_line=${stat_line##*) }
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

process_group() {
  local stat_line
  stat_line=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
  stat_line=${stat_line##*) }
  set -- $stat_line
  printf '%s' "${3:-}"
}

process_parent() {
  local stat_line
  stat_line=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
  stat_line=${stat_line##*) }
  set -- $stat_line
  printf '%s' "${2:-}"
}

group_is_managed_tree() {
  local root="$1"
  local group="$2"
  local stat_file stat_line member member_group current parent found_root=0 depth
  for stat_file in /proc/[0-9]*/stat; do
    stat_line=$(cat "$stat_file" 2>/dev/null || true)
    [ -n "$stat_line" ] || continue
    member=${stat_file#/proc/}
    member=${member%/stat}
    stat_line=${stat_line##*) }
    set -- $stat_line
    member_group="${3:-}"
    [ "$member_group" = "$group" ] || continue
    current="$member"
    depth=0
    while [ "$current" != "$root" ]; do
      parent=$(process_parent "$current" 2>/dev/null || true)
      case "$parent" in ''|0|1|*[!0-9]*) return 1 ;; esac
      current="$parent"
      depth=$((depth + 1))
      [ "$depth" -le 64 ] || return 1
    done
    [ "$member" != "$root" ] || found_root=1
  done
  [ "$found_root" = 1 ]
}

setup_process() {
  local pid="$1"
  local -a args=()
  mapfile -d '' -t args < "/proc/$pid/cmdline" 2>/dev/null || return 1
  [ "${args[1]:-}" = "$MANAGER" ] || return 1
  [ "${args[2]:-}" = setup ] || [ "${args[2]:-}" = restart ]
}

claim_direct_lock() {
  local owner_pid=""
  local owner_start=""
  local live_start=""
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    :
  else
    read -r owner_pid owner_start < <(read_setup_lock) || true
    live_start=$(process_start "$owner_pid" 2>/dev/null || true)
    if [ -n "$owner_pid" ] && [ -n "$owner_start" ] &&
       [ "$owner_start" = "$live_start" ] && kill -0 "$owner_pid" 2>/dev/null; then
      echo 'another-managed-operation-is-running' >&2
      return 75
    fi
    echo 'managed-operation-lock-is-stale; use Stop then retry' >&2
    return 75
  fi
  local self_start
  self_start=$(process_start "$$") || return 75
  printf '%s %s\n' "$$" "$self_start" > "$LOCK_DIR/owner.tmp.$$"
  mv "$LOCK_DIR/owner.tmp.$$" "$LOCK_DIR/owner"
}

server_process() {
  local pid="$1"
  local port="$2"
  local -a args=()
  mapfile -d '' -t args < "/proc/$pid/cmdline" 2>/dev/null || return 1
  [ "${args[1]:-}" = "$SERVER_RUNNER" ] &&
    [ "${args[2]:-}" = "$port" ] &&
    [ "${args[3]:-}" = "$PASSWORD_FILE" ] &&
    [ "${args[4]:-}" = "$MANAGER" ]
}

stop_verified_server_process() {
  local pid="$1"
  local expected_start="$2"
  local port="$3"
  [ -n "$expected_start" ] || return 75
  [ "$(process_start "$pid" 2>/dev/null || true)" = "$expected_start" ] || return 0
  server_process "$pid" "$port" || return 75
  kill -STOP "$pid" 2>/dev/null || return 0
  local group
  group=$(process_group "$pid" 2>/dev/null || true)
  if [ "$(process_start "$pid" 2>/dev/null || true)" != "$expected_start" ] ||
     ! server_process "$pid" "$port" || ! group_is_managed_tree "$pid" "$group"; then
    if [ "$(process_start "$pid" 2>/dev/null || true)" = "$expected_start" ]; then
      kill -CONT "$pid" 2>/dev/null || true
    fi
    return 75
  fi
  kill -STOP -- "-$group" 2>/dev/null || true
  if [ "$(process_start "$pid" 2>/dev/null || true)" != "$expected_start" ] ||
     ! server_process "$pid" "$port" || ! group_is_managed_tree "$pid" "$group"; then
    kill -CONT -- "-$group" 2>/dev/null || true
    return 75
  fi
  kill -KILL -- "-$group" 2>/dev/null || true
}

stop_verified_setup_process() {
  local pid="$1"
  local expected_start="$2"
  [ -n "$expected_start" ] || return 75
  [ "$(process_start "$pid" 2>/dev/null || true)" = "$expected_start" ] || return 0
  setup_process "$pid" || return 75
  kill -STOP "$pid" 2>/dev/null || return 0
  local group
  group=$(process_group "$pid" 2>/dev/null || true)
  if [ "$(process_start "$pid" 2>/dev/null || true)" != "$expected_start" ] ||
     ! setup_process "$pid" || ! group_is_managed_tree "$pid" "$group"; then
    if [ "$(process_start "$pid" 2>/dev/null || true)" = "$expected_start" ]; then
      kill -CONT "$pid" 2>/dev/null || true
    fi
    return 75
  fi
  kill -STOP -- "-$group" 2>/dev/null || true
  if [ "$(process_start "$pid" 2>/dev/null || true)" != "$expected_start" ] ||
     ! setup_process "$pid" || ! group_is_managed_tree "$pid" "$group"; then
    kill -CONT -- "-$group" 2>/dev/null || true
    return 75
  fi
  kill -KILL -- "-$group" 2>/dev/null || true
}

stop_server() {
  local port="${1:-4096}"
  local pid=""
  local saved_start=""
  if [ -f "$SERVER_PID" ]; then
    read -r pid saved_start < "$SERVER_PID" 2>/dev/null || true
  fi
  case "$pid" in
    ''|*[!0-9]*) ;;
    *)
      if kill -0 "$pid" 2>/dev/null; then
        local current_start
        current_start=$(process_start "$pid" 2>/dev/null || true)
        if [ -n "$saved_start" ] && [ "$saved_start" != "$current_start" ]; then
          : # The recorded process exited and its PID was reused. Never kill it.
        else
          server_process "$pid" "$port" || return 75
          stop_verified_server_process "$pid" "$current_start" "$port" || return 75
        fi
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
  local pid=""
  local lock_owned=0
  if [ -n "$lock_pid" ] && [ -n "$lock_start" ] && [ "$lock_start" = "$live_start" ] &&
     kill -0 "$lock_pid" 2>/dev/null; then
    lock_owned=1
    pid="$lock_pid"
  else
    lock_owned=0
    local recorded_start=""
    read -r pid recorded_start < "$MANAGER_PID" 2>/dev/null || true
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && setup_process "$pid"; then
      echo 'bootstrap-owner-is-unverified' >&2
      return 75
    fi
    pid=""
  fi
  case "$pid" in
    ''|*[!0-9]*) ;;
    *)
      if [ "$pid" != "$$" ] && kill -0 "$pid" 2>/dev/null; then
        if setup_process "$pid"; then
          stop_verified_setup_process "$pid" "$lock_start" || return 75
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
    if [ "${KEEP_WAKE_LOCK_ON_FAILURE:-0}" != 1 ]; then
      termux-wake-unlock >/dev/null 2>&1 || true
    fi
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
      filename='ubuntu-base-24.04.5-base-arm64.tar.gz'
      checksum='a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2'
      ;;
    arm|armv7l|armv8l)
      filename='ubuntu-base-24.04.5-base-armhf.tar.gz'
      checksum='4fcee4d278f1c5232e085a021a85e4c6cef3853557a88d98ff380b5e5d5841bb'
      ;;
    x86_64|amd64)
      filename='ubuntu-base-24.04.5-base-amd64.tar.gz'
      checksum='e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86'
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
  printf 'source=canonical-ubuntu-base-24.04.5\n' > "$UBUNTU_INSTALL_MARKER"
  proot-distro install "$archive" --name "$PROOT_NAME"
  rm -f "$archive"
  ubuntu_usable || fail_setup 'Ubuntu Base extraction did not create a usable container' "$CURRENT_PORT"
  rm -f "$UBUNTU_INSTALL_MARKER"
}

install_runtime() {
  local requested_version="$1"
  write_state installing_opencode 'Installing OpenCode' "$CURRENT_PORT"
  proot-distro login "$PROOT_NAME" -- env OC_REQUESTED_VERSION="$requested_version" OC_RUNTIME="$CURRENT_RUNTIME" bash -s <<'OC_PROOT_SETUP'
''' +
    openCodeUbuntuSetupScript +
    r'''OC_PROOT_SETUP

}

setup() {
  CURRENT_PORT="${1:-4096}"
  local requested_version="${2:-}"
  local dispatcher_pid="${3:-}"
  local dispatcher_start="${4:-}"
  CURRENT_RUNTIME="${5:-$(managed_runtime)}"
  managed_runtime >/dev/null || return 64
  if [ -z "$requested_version" ]; then
    case "$CURRENT_RUNTIME" in
      opencode1) requested_version=1.18.32 ;;
      opencode2) requested_version=2.0.10 ;;
    esac
  fi
  SETUP_SUCCEEDED=0
  SERVER_STARTED=0
  if ! claim_setup_lock "$dispatcher_pid" "$dispatcher_start"; then
    write_state failed 'Setup manager could not claim its launch lock' "$CURRENT_PORT"
    rm -f "$MANAGER_PID"
    return 75
  fi
  trap on_setup_error ERR
  trap cleanup_setup EXIT
  [ "$(process_group "$$" 2>/dev/null || true)" = "$$" ] ||
    fail_setup 'Setup manager did not start in an isolated process group' "$CURRENT_PORT"
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

  install_runtime "$requested_version"

  local installed_version
  installed_version=$(runtime_version 2>/dev/null)
  [ -n "$installed_version" ] || fail_setup 'OpenCode installed but did not report a version' "$CURRENT_PORT"
  if [ "$CURRENT_RUNTIME" = opencode1 ]; then
    write_state refreshing_models 'Refreshing the OpenCode model catalog' "$CURRENT_PORT" proot "$installed_version"
    proot-distro login "$PROOT_NAME" -- opencode models --refresh >/dev/null ||
      fail_setup 'OpenCode updated, but its model catalog could not be refreshed' "$CURRENT_PORT"
  fi
  [ -s "$PASSWORD_FILE" ] || fail_setup 'The local server password is missing' "$CURRENT_PORT"

  stop_legacy_server "$CURRENT_PORT"
  stop_server "$CURRENT_PORT"
  start_server "$installed_version"
}

start_server() {
  local installed_version="$1"
  local starting_phase="${2:-starting_server}"
  local password
  local runtime health_path
  runtime=$(managed_runtime) || return 64
  case "$runtime" in
    opencode1) health_path=/global/health ;;
    # OpenCode 2.0.4 and later answer at /api/info; the beta this app
    # installed before answered at /api/health. Either one proves readiness.
    opencode2) health_path='/api/info /api/health' ;;
  esac
  if [ -n "${CURRENT_RECOVERY:-}" ]; then
    recovery_permitted "$CURRENT_RECOVERY" || fail_setup 'Automatic recovery was disabled' "$CURRENT_PORT"
  fi
  password=$(cat "$PASSWORD_FILE")
  install_server_runner
  : > "$SERVER_LOG_ACTIVE"
  if [ "$starting_phase" = restarting ]; then
    write_state restarting 'Restarting the local server' "$CURRENT_PORT" proot "$installed_version"
  else
    write_state starting_server 'Starting the local server' "$CURRENT_PORT" proot "$installed_version"
  fi
  set -m
  nohup "$SERVER_RUNNER" "$CURRENT_PORT" "$PASSWORD_FILE" "$MANAGER" "$runtime" \
    >/dev/null 2>&1 </dev/null &
  local server_pid=$!
  SERVER_STARTED=1
  local server_start
  server_start=$(process_start "$server_pid") ||
    fail_setup 'Could not record the managed server process identity' "$CURRENT_PORT"
  if [ "$(process_group "$server_pid" 2>/dev/null || true)" != "$server_pid" ]; then
    kill -STOP "$server_pid" 2>/dev/null || true
    if [ "$(process_start "$server_pid" 2>/dev/null || true)" = "$server_start" ]; then
      kill -KILL "$server_pid" 2>/dev/null || true
    fi
    fail_setup 'Managed server did not start in an isolated process group' "$CURRENT_PORT"
  fi
  printf '%s %s\n' "$server_pid" "$server_start" > "$SERVER_PID"
  # Which boot this server belongs to: after the phone restarts, a missing
  # server is expected, not a crash.
  cat /proc/sys/kernel/random/boot_id > "$SERVER_PID.boot" 2>/dev/null || true

  for _ in {1..30}; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      fail_setup 'OpenCode server exited during startup' "$CURRENT_PORT"
    fi
    if (exec 3<>"/dev/tcp/127.0.0.1/$CURRENT_PORT") 2>/dev/null; then
      exec 3>&-
      exec 3<&-
      local auth_codes
      auth_codes=$(proot-distro login "$PROOT_NAME" -- env \
        OC_PORT="$CURRENT_PORT" OC_PASSWORD="$password" OC_HEALTH_PATH="$health_path" bash -s <<'OC_AUTH_CHECK'
unauth=000
auth=000
for path in $OC_HEALTH_PATH; do
  unauth=$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' \
    "http://127.0.0.1:$OC_PORT$path" || true)
  auth=$(curl --max-time 2 -s -o /dev/null -w '%{http_code}' \
    -u "opencode:$OC_PASSWORD" "http://127.0.0.1:$OC_PORT$path" || true)
  [ "$unauth $auth" != '401 200' ] || break
done
printf '%s %s' "$unauth" "$auth"
OC_AUTH_CHECK
)
      if [ "$auth_codes" = '401 200' ]; then
        write_state ready 'OpenCode is ready' "$CURRENT_PORT" proot "$installed_version" "$server_pid" "${CURRENT_OPERATION:+completed}"
        printf '[oc] authenticated server ready on 127.0.0.1:%s\n' "$CURRENT_PORT"
        SETUP_SUCCEEDED=1
        if [ "${SWITCH_COMMIT:-0}" = 1 ]; then rm -f "$SWITCH_FILE" || true; fi
        return 0
      fi
    fi
    sleep 1
  done
  fail_setup 'OpenCode server did not become authenticated and ready within 30 seconds' "$CURRENT_PORT"
}

recovery_permitted() {
  [ -f "$RECOVERY_PERMIT" ] && [ "$(cat "$RECOVERY_PERMIT")" = "$1" ]
}

recovery_port_busy() {
  (exec 3<>"/dev/tcp/127.0.0.1/$CURRENT_PORT") 2>/dev/null
}

recovery_server_absent() {
  local tracked_pid='' tracked_start=''
  read -r tracked_pid tracked_start < "$SERVER_PID" 2>/dev/null || true
  if [ -n "$tracked_pid" ] && kill -0 "$tracked_pid" 2>/dev/null; then return 75; fi
  if recovery_port_busy; then return 75; fi
  return 0
}

recovery_preflight() {
  local expected_operation="$1" token="$2"
  recovery_permitted "$token" || return 75
  [ "$(read_state_value operation)" = "$expected_operation" ] || return 75
  [ "$(read_state_value phase)" = failed ] || return 75
  [ "$(read_state_value runner)" = proot ] || return 75
  [ "$(read_state_value port)" = "$CURRENT_PORT" ] || return 75
  case "$(read_state_value failure_kind)" in crash|recovery) ;; *) return 75 ;; esac
  # A live PID, even one whose identity no longer matches, is never stopped
  # by automatic recovery. Manual management can explain that conflict.
  recovery_server_absent
}

recovery_arm() {
  [ ! -f "$SWITCH_FILE" ] || return 75
  local token="$1" pid='' saved_start=''
  [[ "$token" =~ ^[a-zA-Z0-9_-]{1,64}$ ]] || return 64
  [ ! -e "$LOCK_DIR" ] || return 75
  [ "$(read_state_value runner)" = proot ] || return 75
  CURRENT_PORT=$(read_state_value port)
  [ "$CURRENT_PORT" = 4096 ] || return 75
  case "$(read_state_value phase)" in
    ready)
      read -r pid saved_start < "$SERVER_PID" 2>/dev/null || return 75
      [ -n "$saved_start" ] && [ "$(process_start "$pid" 2>/dev/null || true)" = "$saved_start" ] || return 75
      server_process "$pid" "$CURRENT_PORT" || return 75
      ;;
    failed)
      # A policy owner may first attach after a confirmed crash. This only
      # arms a permit: restart still repeats the full locked preflight.
      case "$(read_state_value failure_kind)" in crash|recovery) ;; *) return 75 ;; esac
      recovery_server_absent || return 75
      ;;
    *) return 75 ;;
  esac
  umask 077
  printf '%s' "$token" > "$RECOVERY_PERMIT.tmp.$$"
  mv "$RECOVERY_PERMIT.tmp.$$" "$RECOVERY_PERMIT"
  cat "$STATE"
}

recovery_disarm() {
  local token="$1"
  if [ -f "$RECOVERY_PERMIT" ] && ! recovery_permitted "$token"; then return 0; fi
  rm -f "$RECOVERY_PERMIT"
  # A dispatched recovery may still be in preflight/startup. Revoke first,
  # then cancel only the operation that carries this permit, using the
  # manager's PID/start-time and process-group ownership checks.
  if [ "$(read_state_value recovery_token)" = "$token" ]; then
    case "$(read_state_value phase)" in
      restarting|starting_server)
        stop_setup || return 75
        stop_server "$(read_state_value port)" || return 75
        CURRENT_OPERATION=$(read_state_value operation)
        write_state stopped 'Automatic recovery disabled' "$(read_state_value port)"
        ;;
    esac
  fi
}

write_switch() {
  local previous="$1" target="$2" phase="$3"
  printf 'switch_previous=%s\nswitch_target=%s\nswitch_phase=%s\nswitch_operation=%s\n' \
    "$previous" "$target" "$phase" "$CURRENT_OPERATION" > "$SWITCH_FILE.tmp.$$"
  chmod 600 "$SWITCH_FILE.tmp.$$"
  mv "$SWITCH_FILE.tmp.$$" "$SWITCH_FILE"
}

switch_value() {
  local key="$1" name value
  [ -f "$SWITCH_FILE" ] || return 0
  while IFS='=' read -r name value; do
    [ "$name" != "$key" ] || { printf '%s' "$value"; return; }
  done < "$SWITCH_FILE"
}

# Explicitly authorized generation change. One slot, one lock, one server.
# The old binary/data/credential remain available for a deliberate return.
switch_runtime() {
  local target="$1" previous installed_version current
  case "$target" in opencode1|opencode2) ;; *) return 64 ;; esac
  CURRENT_PORT="${CURRENT_PORT:-4096}"
  local staged_password="$OC_DIR/switch-password-$CURRENT_OPERATION"
  [ -s "$staged_password" ] || return 64
  claim_direct_lock || return 75
  SETUP_SUCCEEDED=0
  SERVER_STARTED=0
  KEEP_WAKE_LOCK_ON_FAILURE=1
  CURRENT_STARTED_AT=$(date +%s)
  printf '%s %s\n' "$$" "$(process_start "$$")" > "$MANAGER_PID"
  trap on_setup_error ERR
  trap cleanup_setup EXIT
  [ "$(process_group "$$" 2>/dev/null || true)" = "$$" ] ||
    fail_setup 'Switch manager did not start in an isolated process group' "$CURRENT_PORT"
  # Rotate only after this operation owns the lock. Start its logger after
  # rotation so neither old output nor the logger's cached old size leaks in.
  rotate_log install
  exec > >("$MANAGER" write-log install) 2>&1
  current=$(managed_runtime) || return 64
  local recorded_data_mode
  recorded_data_mode=$(cat "$V2_DATA_MODE" 2>/dev/null || true)
  case "$recorded_data_mode" in ''|default|isolated) ;; *) fail_setup 'The OpenCode 2 data location record is unreadable' ;; esac
  previous=$(switch_value switch_previous)
  [ -n "$previous" ] || previous="$current"
  case "$previous" in opencode1|opencode2) ;; *) fail_setup 'The previous runtime record is unreadable' ;; esac
  if [ "$target" = opencode1 ] && [ "$current" = opencode2 ] &&
     [ "$(cat "$V2_DATA_MODE" 2>/dev/null || true)" != isolated ]; then
    fail_setup 'This OpenCode 2 installation has no separate OpenCode 1 data to return to'
  fi
  if [ -s "$OC_DIR/password-$target" ] &&
     ! cmp -s "$staged_password" "$OC_DIR/password-$target"; then
    fail_setup 'The saved profile credential differs from this runtime; restore its original saved credential before returning'
  fi
  ubuntu_usable || fail_setup 'The managed Ubuntu environment is unavailable'
  # Revocation is durable and precedes every generation-changing action.
  rm -f "$RECOVERY_PERMIT"
  # Capture current credential only when its runtime is known, never replace a
  # previous credential with a target credential after an interrupted launch.
  if [ ! -f "$SWITCH_FILE" ] && [ -s "$PASSWORD_FILE" ]; then
    cp "$PASSWORD_FILE" "$OC_DIR/password-$current.tmp.$$"
    chmod 600 "$OC_DIR/password-$current.tmp.$$"
    mv "$OC_DIR/password-$current.tmp.$$" "$OC_DIR/password-$current"
  fi
  write_switch "$previous" "$target" preparing
  write_state preparing 'Preparing the selected OpenCode runtime' "$CURRENT_PORT"
  # An existing first-run OC2 install keeps its original default XDG paths.
  # Only OC1 installations opting into OC2 for the first time get isolation.
  if [ ! -f "$V2_DATA_MODE" ]; then
    if [ "$current" = opencode2 ]; then data_mode=default; else data_mode=isolated; fi
    printf '%s' "$data_mode" > "$V2_DATA_MODE.tmp.$$"
    mv "$V2_DATA_MODE.tmp.$$" "$V2_DATA_MODE"
  fi
  CURRENT_RUNTIME="$target"
  installed_version=$(runtime_version 2>/dev/null || true)
  if [ -z "$installed_version" ]; then
    require_setup_space
    local requested_version
    case "$target" in
      opencode1) requested_version=1.18.32 ;;
      opencode2) requested_version=2.0.10 ;;
    esac
    install_runtime "$requested_version"
    installed_version=$(runtime_version 2>/dev/null || true)
  fi
  [ -n "$installed_version" ] || fail_setup 'The selected OpenCode command is unavailable'
  # Journal survives a crash on either side of the only destructive boundary.
  write_switch "$previous" "$target" stopping
  write_state restarting 'Switching the managed local server' "$CURRENT_PORT"
  stop_server "$CURRENT_PORT" || fail_setup 'The tracked process is not the managed OpenCode server'
  KEEP_WAKE_LOCK_ON_FAILURE=0
  local port_released=0
  for _ in {1..30}; do
    if ! (exec 3<>"/dev/tcp/127.0.0.1/$CURRENT_PORT") 2>/dev/null; then port_released=1; break; fi
    exec 3>&- 2>/dev/null || true
    exec 3<&- 2>/dev/null || true
    sleep 0.2
  done
  [ "$port_released" = 1 ] || fail_setup 'The local server port is still in use; no replacement was started'
  cp "$staged_password" "$PASSWORD_FILE.tmp.$$"
  chmod 600 "$PASSWORD_FILE.tmp.$$"
  mv "$PASSWORD_FILE.tmp.$$" "$PASSWORD_FILE"
  rm -f "$staged_password"
  cp "$PASSWORD_FILE" "$OC_DIR/password-$target.tmp.$$"
  mv "$OC_DIR/password-$target.tmp.$$" "$OC_DIR/password-$target"
  printf '%s' "$target" > "$RUNTIME_FILE.tmp.$$"
  mv "$RUNTIME_FILE.tmp.$$" "$RUNTIME_FILE"
  write_switch "$previous" "$target" starting
  termux-wake-lock >/dev/null 2>&1 || true
  SWITCH_COMMIT=1
  start_server "$installed_version"
  # Authenticated readiness is the commit point. Until this deletion, a fresh
  # app process must display an unresolved switch and selectable return.
  rm -f "$SWITCH_FILE"
}

restart() {
  CURRENT_PORT="${1:-4096}"
  CURRENT_OPERATION="${2:-}"
  local expected_operation="${3:-}"
  CURRENT_RECOVERY="${4:-}"
  [[ "$CURRENT_OPERATION" =~ ^[a-zA-Z0-9_-]{1,64}$ ]] || return 64
  if [ -n "${5:-}" ]; then switch_runtime "$5"; return; fi
  [ ! -f "$SWITCH_FILE" ] || { echo 'managed-runtime-switch-pending' >&2; return 75; }
  SETUP_SUCCEEDED=0
  SERVER_STARTED=0
  KEEP_WAKE_LOCK_ON_FAILURE=0
  OLD_SERVER_LIVE=0
  OLD_SERVER_PID=""
  OLD_SERVER_VERSION=$(read_state_value version)
  if ! claim_direct_lock; then
    echo 'another-managed-operation-is-running' >&2
    return 75
  fi
  if [ -n "$CURRENT_RECOVERY" ]; then
    if ! recovery_preflight "$expected_operation" "$CURRENT_RECOVERY"; then
      release_setup_lock
      echo 'recovery-owner-or-state-changed' >&2
      return 75
    fi
  fi
  CURRENT_STARTED_AT=$(date +%s)
  printf '%s %s\n' "$$" "$(process_start "$$")" > "$MANAGER_PID"
  trap on_setup_error ERR
  trap cleanup_setup EXIT
  local old_pid=""
  local old_start=""
  local live_start=""
  read -r old_pid old_start < "$SERVER_PID" 2>/dev/null || true
  live_start=$(process_start "$old_pid" 2>/dev/null || true)
  if [ -n "$old_pid" ] && [ -n "$live_start" ] && kill -0 "$old_pid" 2>/dev/null &&
     { [ -z "$old_start" ] || [ "$old_start" = "$live_start" ]; } &&
     server_process "$old_pid" "$CURRENT_PORT"; then
    OLD_SERVER_LIVE=1
    OLD_SERVER_PID="$old_pid"
    KEEP_WAKE_LOCK_ON_FAILURE=1
  fi
  [ "$(process_group "$$" 2>/dev/null || true)" = "$$" ] ||
    fail_restart_preflight 'Restart manager did not start in an isolated process group'
  printf '\n[oc] server restart started at %s\n' "$(date -Iseconds 2>/dev/null || date)"
  # Publish this operation before any preflight can block. A prior ready
  # snapshot must never be mistaken for completion of the requested restart.
  write_state restarting 'Checking the local server before restart' \
    "$CURRENT_PORT" proot "$OLD_SERVER_VERSION" "$OLD_SERVER_PID"

  ubuntu_usable || fail_restart_preflight 'The managed Ubuntu environment is unavailable'
  [ -s "$PASSWORD_FILE" ] || fail_restart_preflight 'The local server password is missing'
  local installed_version
  if ! installed_version=$(runtime_version 2>/dev/null); then
    fail_restart_preflight 'The installed OpenCode command is unavailable'
  fi
  [ -n "$installed_version" ] || fail_restart_preflight 'The installed OpenCode command is unavailable'

  if [ -n "$CURRENT_RECOVERY" ]; then
    recovery_permitted "$CURRENT_RECOVERY" && recovery_server_absent ||
      fail_restart_preflight 'The server changed before automatic recovery; no replacement was started'
  else
    stop_server "$CURRENT_PORT" ||
      fail_restart_preflight 'The tracked process is not the managed OpenCode server'
  fi
  OLD_SERVER_LIVE=0
  KEEP_WAKE_LOCK_ON_FAILURE=0
  termux-wake-lock >/dev/null 2>&1 || true
  write_state restarting 'Restarting the local server' "$CURRENT_PORT"
  rm -f "$SERVER_LOG_ACTIVE"

  local port_released=0
  for _ in {1..30}; do
    if ! (exec 3<>"/dev/tcp/127.0.0.1/$CURRENT_PORT") 2>/dev/null; then
      port_released=1
      break
    fi
    exec 3>&- 2>/dev/null || true
    exec 3<&- 2>/dev/null || true
    sleep 0.2
  done
  [ "$port_released" = 1 ] ||
    fail_setup 'The local server port is still in use; no replacement was started' "$CURRENT_PORT"

  start_server "$installed_version" restarting
}

status() {
  if [ ! -f "$STATE" ]; then
    printf 'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\nversion=\npid=\n'
    return 0
  fi
  CURRENT_OPERATION=$(read_state_value operation)
  local phase
  phase=$(read_state_value phase)
  if [ "$phase" = ready ]; then
    local pid saved_start current_start
    pid=""
    saved_start=""
    read -r pid saved_start < "$SERVER_PID" 2>/dev/null || true
    current_start=$(process_start "$pid" 2>/dev/null || true)
    if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null ||
       { [ -n "$saved_start" ] && [ "$saved_start" != "$current_start" ]; } ||
       ! server_process "$pid" "$(read_state_value port)"; then
      local saved_boot current_boot
      saved_boot=$(cat "$SERVER_PID.boot" 2>/dev/null || true)
      current_boot=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null || true)
      if [ -n "$saved_boot" ] && [ -n "$current_boot" ] && [ "$saved_boot" != "$current_boot" ]; then
        write_state stopped 'The phone restarted, so the local server stopped' "$(read_state_value port)"
      else
        write_state failed 'The local OpenCode server stopped unexpectedly' "$(read_state_value port)" proot '' '' '' crash
      fi
      rm -f "$SERVER_PID" "$SERVER_PID.boot" "$SERVER_LOG_ACTIVE"
      termux-wake-unlock >/dev/null 2>&1 || true
    elif [ -z "$saved_start" ] && [ -n "$current_start" ]; then
      printf '%s %s\n' "$pid" "$current_start" > "$SERVER_PID"
    fi
  fi
  case "$phase" in
    queued|preparing|installing_dependencies|installing_ubuntu|installing_opencode|refreshing_models|restarting|starting_server)
      local manager_pid manager_start live_start latest_phase
      manager_pid=""
      manager_start=""
      read -r manager_pid manager_start < "$MANAGER_PID" 2>/dev/null || true
      live_start=$(process_start "$manager_pid" 2>/dev/null || true)
      if [ -z "$manager_pid" ] || [ -z "$manager_start" ] ||
         [ "$manager_start" != "$live_start" ] || ! kill -0 "$manager_pid" 2>/dev/null ||
         ! setup_process "$manager_pid"; then
        latest_phase=$(read_state_value phase)
        if [ "$latest_phase" = "$phase" ]; then
          write_state failed 'Setup stopped unexpectedly; see live output for details' "$(read_state_value port)"
        fi
      fi
      ;;
  esac
  cat "$STATE"
  # A persisted authenticated-ready result commits only its own transition.
  # This completes harmless journal cleanup after a crash between those writes.
  if [ "$(read_state_value phase)" = ready ] && [ -f "$SWITCH_FILE" ] &&
     [ -n "$(switch_value switch_operation)" ] &&
     [ "$(read_state_value operation)" = "$(switch_value switch_operation)" ] &&
     [ "$(managed_runtime)" = "$(switch_value switch_target)" ]; then
    rm -f "$SWITCH_FILE" || true
  fi
  if [ -f "$SWITCH_FILE" ]; then
    cat "$SWITCH_FILE"
    # State may describe the package being staged while the old server still
    # owns the slot. Report the durable active-generation marker separately.
    printf 'runtime=%s\n' "$(managed_runtime)"
  fi
  if [ "$(cat "$V2_DATA_MODE" 2>/dev/null || true)" = isolated ]; then
    printf 'switch_return=opencode1\n'
  fi
}

server_exited() {
  local port="${1:-4096}"
  local runner_pid="${2:-}"
  local code="${3:-1}"
  local current_pid current_start
  current_pid=""
  current_start=""
  read -r current_pid current_start < "$SERVER_PID" 2>/dev/null || true
  [ -n "$runner_pid" ] && [ "$current_pid" = "$runner_pid" ] || return 0
  [ -n "$current_start" ] && [ "$(process_start "$runner_pid" 2>/dev/null || true)" = "$current_start" ] || return 0
  CURRENT_OPERATION=$(read_state_value operation)
  rm -f "$SERVER_PID" "$SERVER_LOG_ACTIVE"
  write_state failed "OpenCode server exited (code $code)" "$port" proot '' '' '' crash
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
  restart) shift; restart "$@" ;;
  recovery-arm) shift; recovery_arm "$@" ;;
  recovery-disarm) shift; recovery_disarm "$@" ;;
  status) status ;;
  diagnostics) diagnostics ;;
  stop) shift; stop "$@" ;;
  server-exited) shift; server_exited "$@" ;;
  rotate-log) shift; rotate_log "$@" ;;
  write-log) shift; write_log "$@" ;;
  *) echo "usage: $0 {setup|restart|status|diagnostics|stop}" >&2; exit 64 ;;
esac
''';
