import 'script_support.dart';

/// Everything the install downloads by version, in one place so raising a
/// pin is one edit with a test run behind it.
///
/// Node.js is the official binary tarball from nodejs.org (Ubuntu's apt
/// Node is too old for Paseo and Claude Code), verified against the
/// SHA-256 below before it is unpacked. The two checksums are the
/// `node-v24.21.0-linux-{arm64,x64}.tar.gz` lines of
/// https://nodejs.org/dist/v24.21.0/SHASUMS256.txt (read 2026-09-19).
/// x64 exists so the x86_64 emulator can run the same flow as a phone.
///
/// Paseo is pinned exactly: lib/paseo/ was verified against daemon 0.8.0
/// and, with a real Claude Opus 5.5 turn, 0.9.1 (the first release whose
/// Claude model list includes Opus 5.5; 2026-09-23). The 0.9.2 patch is
/// reviewed for compatible protocol changes; device validation is pending.
/// Claude Code is not pinned; the installed version is recorded in the
/// script's state and shown in the app.
const termuxLocalAgentsPins = <String, String>{
  'node_version': 'v24.21.0',
  'node_base_url': 'https://nodejs.org/dist',
  'node_sha256_arm64':
      '724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5',
  'node_sha256_x64':
      '6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff',
  'paseo_version': '0.9.2',
};

/// The verbs `claude.sh` runs detached from the bridge shell (their
/// progress is read back through `status`).
const termuxLocalAgentsDetachedVerbs = {
  'install',
  'start',
  'restart',
  'remove',
};

/// Every verb the app may send. `run-daemon` and `queue` are the script's
/// own and `signin` only ever runs in a terminal the person can see.
const termuxLocalAgentsVerbs = {
  ...termuxLocalAgentsDetachedVerbs,
  'status',
  'stop',
  'password',
  'signin-status',
  'projects',
  'ensure-project',
  'log',
};

/// The pins as the `key=value` file the script reads.
String termuxLocalAgentsPinsFile([
  Map<String, String> pins = termuxLocalAgentsPins,
]) {
  final buffer = StringBuffer();
  for (final entry in pins.entries) {
    if (!RegExp(r'^[a-z0-9_]+$').hasMatch(entry.key) ||
        !RegExp(r'^[A-Za-z0-9._:/-]+$').hasMatch(entry.value)) {
      throw ArgumentError.value(entry, 'pins');
    }
    buffer.writeln('${entry.key}=${entry.value}');
  }
  return buffer.toString();
}

/// The last [lines] of the install log, read inline (no script rewrite,
/// no verb lock) so the block can poll it beside `status`.
String termuxLocalAgentsLogTailScript({int lines = 200}) {
  if (lines < 1 || lines > 5000) {
    throw ArgumentError.value(lines, 'lines');
  }
  return 'tail -n $lines "\$HOME/.oc/claude/install.log" 2>/dev/null || true\n';
}

/// The bridge shell for one `claude.sh` verb: rewrites the script and the
/// pins from this build, then either runs the verb inline or queues it and
/// launches it detached in its own process group, printing
/// `claude-started:<pid>` (or `claude-busy:<verb>:<pid>` with exit 75
/// while another verb still runs).
String termuxLocalAgentsVerbScript(
  String verb, {
  List<String> args = const [],
}) {
  if (!termuxLocalAgentsVerbs.contains(verb)) {
    throw ArgumentError.value(verb, 'verb');
  }
  for (final arg in args) {
    if (arg.contains('\n') || arg.contains('\x00')) {
      throw ArgumentError.value(arg, 'args', 'Must be a single line.');
    }
  }
  final quotedArgs = args.map(shellQuote).join(' ');
  final buffer = StringBuffer('''
set -eu
OC_DIR="\$HOME/.oc"
CLAUDE="\$OC_DIR/claude.sh"
mkdir -p "\$OC_DIR"
umask 077
claude_tmp="\$CLAUDE.tmp.\$\$"
cat > "\$claude_tmp" <<'OC_CLAUDE_EOF'
$termuxLocalAgentsScript
OC_CLAUDE_EOF
chmod 700 "\$claude_tmp"
mv "\$claude_tmp" "\$CLAUDE"
[ -x "\$CLAUDE" ] || {
  echo 'claude-install-failed' >&2
  exit 74
}
pins_tmp="\$OC_DIR/claude-pins.tmp.\$\$"
cat > "\$pins_tmp" <<'OC_CLAUDE_PINS_EOF'
${termuxLocalAgentsPinsFile()}OC_CLAUDE_PINS_EOF
mv "\$pins_tmp" "\$OC_DIR/claude-pins"
''');
  if (!termuxLocalAgentsDetachedVerbs.contains(verb)) {
    buffer.write('exec bash "\$CLAUDE" $verb $quotedArgs\n');
    return buffer.toString();
  }
  buffer.write('''
bash "\$CLAUDE" queue '$verb' || exit \$?
set -m
nohup bash "\$CLAUDE" '$verb' $quotedArgs >/dev/null 2>&1 </dev/null &
echo "claude-started:\$!"
''');
  return buffer.toString();
}

/// Claude Code and the Paseo daemon, installed as `~/.oc/claude.sh`.
const termuxLocalAgentsScript = r'''#!/data/data/com.termux/files/usr/bin/bash
# Claude Code on this phone: the Paseo daemon (@getpaseo/cli) and Claude Code,
# installed into the app-managed Ubuntu (proot-distro `opencode-ubuntu`) next
# to the OpenCode server and run from there. Nothing here installs Ubuntu;
# that stays the manager's job (manager.sh).
#
# Verbs: status | install | start | stop | restart | remove [--forget-signin] |
#        password | signin-status | signin | projects | ensure-project <path> |
#        log
# install, start, restart and remove run detached (the bridge queues them and
# reads progress back through `status`); the rest answer inline.
#
# Rules this file keeps: the daemon listens on 127.0.0.1 only and its relay is
# never enabled; the daemon password never appears in an argument, a log line
# or the state file; nothing remote is ever piped into a shell.
set -Eeuo pipefail

OC_DIR="$HOME/.oc"
CLAUDE_DIR="$OC_DIR/claude"
SELF="$OC_DIR/claude.sh"
PINS="$OC_DIR/claude-pins"
STATE="$CLAUDE_DIR/state"
CONFIG="$CLAUDE_DIR/config"
LOG="$CLAUDE_DIR/install.log"
DAEMON_LOG="$CLAUDE_DIR/daemon.log"
DAEMON_PID="$CLAUDE_DIR/daemon.pid"
PASSWORD_FILE="$CLAUDE_DIR/password"
VERB_LOCK="$CLAUDE_DIR/verb.lock"
# Downloads stay under ~/.oc, never $PREFIX/tmp: the Termux service clears
# its tmp directory asynchronously when it starts and would race a download.
TMP_DIR="$CLAUDE_DIR/tmp"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
PROOT_NAME=opencode-ubuntu
# Paths inside Ubuntu. The overrides exist for the fixture tests only.
NODE_DIR="${OC_CLAUDE_NODE_DIR:-/opt/oc-node}"
AGENTS_DIR="${OC_CLAUDE_AGENTS_DIR:-/opt/oc-agents}"
# The daemon, Claude Code and the sign-in run as an ordinary Ubuntu user, not
# as root. Claude Code refuses its permission-skipping mode under root, and
# Paseo starts it with that mode available, so as root every turn died at
# launch (seen on the emulator, 2026-09-19). An unprivileged user satisfies
# the guard honestly; the alternative, the IS_SANDBOX switch, would turn the guard off.
AGENT_USER="${OC_CLAUDE_USER:-oc}"
UBUNTU_HOME="${OC_CLAUDE_UBUNTU_HOME:-/home/$AGENT_USER}"
PASEO_HOME="$UBUNTU_HOME/.oc-paseo"
PROJECTS_DIR="$UBUNTU_HOME/projects"
PORT="${OC_CLAUDE_PORT:-6767}"
# Loopback only. There is no verb, flag or variable that widens this.
LISTEN="127.0.0.1:$PORT"
HEALTH_URL="http://127.0.0.1:$PORT/api/health"
HEALTH_TIMEOUT="${OC_CLAUDE_HEALTH_TIMEOUT:-120}"
# One poll tick in seconds; the timeouts above count ticks. Tests shorten it.
POLL="${OC_CLAUDE_POLL:-1}"
LOG_MAX_BYTES=2097152
# Measured on the emulator: Node 216 MB + packages 773 MB, plus npm's cache
# and the download while it unpacks.
INSTALL_REQUIRED_MB=2048
REPAIR_REQUIRED_MB=300
UBUNTU_PATH="$AGENTS_DIR/bin:$NODE_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

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
pin_value() { read_kv "$PINS" "$1"; }

# write_state <phase> <message> [failure_kind]
# Phases: installing installed starting ready stopping removing failed.
# `absent` and `needs_ubuntu` are never written; status derives them.
write_state() {
  local phase="$1" message="$2" kind="${3:-}"
  message=${message//$'\n'/ }
  mkdir -p "$CLAUDE_DIR"
  local tmp="$STATE.tmp.$$"
  printf 'phase=%s\nmessage=%s\nverb=%s\nverb_pid=%s\nstep=%s\nfailure_kind=%s\nupdated_at=%s\n' \
    "$phase" "$message" "${CURRENT_VERB:-}" "${CURRENT_PID:-}" "${CURRENT_STEP:-}" \
    "$kind" "$(date +%s)" > "$tmp"
  mv "$tmp" "$STATE"
}

set_config() {
  local key="$1" value="$2"
  mkdir -p "$CLAUDE_DIR"
  local tmp="$CONFIG.tmp.$$"
  { [ ! -f "$CONFIG" ] || grep -v "^$key=" "$CONFIG" || true; printf '%s=%s\n' "$key" "$value"; } > "$tmp"
  mv "$tmp" "$CONFIG"
}

log() { printf '[claude] %s\n' "$*"; }

file_bytes() {
  local size=0
  [ ! -f "$1" ] || size=$(wc -c < "$1" 2>/dev/null | tr -d ' ' || printf 0)
  case "$size" in ''|*[!0-9]*) size=0 ;; esac
  printf '%s' "$size"
}

# Keeps one bounded backup; a log is never allowed to fill the phone.
rotate_file() {
  local path="$1"
  [ "$(file_bytes "$path")" -ge "$LOG_MAX_BYTES" ] || return 0
  mv -f "$path" "$path.1"
  : > "$path"
  chmod 600 "$path"
}

# Appends stdin to the daemon log line by line, rotating at LOG_MAX_BYTES.
write_daemon_log() {
  mkdir -p "$CLAUDE_DIR"
  touch "$DAEMON_LOG"
  chmod 600 "$DAEMON_LOG"
  local LC_ALL=C size line=''
  size=$(file_bytes "$DAEMON_LOG")
  while IFS= read -r line || [ -n "$line" ]; do
    printf '%s\n' "$line" >> "$DAEMON_LOG"
    size=$((size + ${#line} + 1))
    if [ "$size" -ge "$LOG_MAX_BYTES" ]; then
      rotate_file "$DAEMON_LOG"
      size=0
    fi
    line=''
  done
}

attach_log() {
  mkdir -p "$CLAUDE_DIR"
  touch "$LOG"
  chmod 600 "$LOG"
  rotate_file "$LOG"
  # fd 3 keeps the original stdout (a terminal when run by hand; /dev/null
  # when the bridge dispatched the verb).
  exec 3>&1
  exec > >(tee -a "$LOG" >&3) 2>&1
}

# fail <failure_kind> <message>
fail() {
  local kind="$1"
  shift
  local message="${*:-$kind}"
  trap - ERR
  write_state failed "$message" "$kind"
  log "ERROR: $message"
  release_verb_lock
  exit 1
}

on_verb_error() {
  local code=$?
  local line="${BASH_LINENO[0]:-unknown}"
  local stage
  stage=$(state_value message)
  [ -n "$stage" ] || stage="$CURRENT_VERB"
  fail error "$stage failed (exit $code; line $line)"
}

# ---------------------------------------------------------------------------
# processes and the verb lock
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

# Long verbs own their process group so the bridge's shell exiting (or the
# Termux service going down) never takes the verb with it.
ensure_isolated() {
  [ "$(process_group "$$" 2>/dev/null || true)" = "$$" ] && return 0
  [ "${OC_CLAUDE_ISOLATED:-}" != 1 ] || return 0
  OC_CLAUDE_ISOLATED=1 exec setsid "${BASH:-bash}" "$0" "$@"
}

claim_verb_lock() {
  mkdir -p "$CLAUDE_DIR"
  if mkdir "$VERB_LOCK" 2>/dev/null; then
    printf '%s %s\n' "$$" "$CURRENT_VERB" > "$VERB_LOCK/owner"
    return 0
  fi
  local owner_pid='' owner_verb=''
  [ -f "$VERB_LOCK/owner" ] && { read -r owner_pid owner_verb < "$VERB_LOCK/owner" || true; }
  if process_alive "$owner_pid"; then
    echo "claude-busy:${owner_verb:-unknown}:$owner_pid" >&2
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

transient_phase_for() {
  case "$1" in
    install) printf installing ;;
    start|restart) printf starting ;;
    stop) printf stopping ;;
    remove) printf removing ;;
    *) return 64 ;;
  esac
}

begin_verb() {
  CURRENT_VERB="$1"
  CURRENT_PID="$$"
  CURRENT_STEP=''
  claim_verb_lock || exit 75
  trap on_verb_error ERR
  trap release_verb_lock EXIT
  attach_log
  write_state "$(transient_phase_for "$CURRENT_VERB")" "Starting $CURRENT_VERB"
  printf '\n[claude] %s started at %s\n' "$CURRENT_VERB" "$(date -Iseconds 2>/dev/null || date)"
}

# queue <verb>: the dispatcher's synchronous gate. Refuses while another verb
# owns the lock, else records the verb so a status read between the dispatch
# and the verb's first write already reports it as busy.
queue_verb() {
  local next="${1:-}" phase
  phase=$(transient_phase_for "$next") || exit 64
  local owner_pid='' owner_verb=''
  [ -f "$VERB_LOCK/owner" ] && { read -r owner_pid owner_verb < "$VERB_LOCK/owner" || true; }
  if process_alive "$owner_pid"; then
    echo "claude-busy:${owner_verb:-unknown}:$owner_pid" >&2
    exit 75
  fi
  CURRENT_VERB="$next" CURRENT_PID='' CURRENT_STEP='' write_state "$phase" "Queued $next"
  echo "claude-queued:$next"
}

verb_alive() {
  process_alive "${1:-}" || return 1
  case "$(process_cmdline "$1")" in *claude.sh*) return 0 ;; esac
  return 1
}

# ---------------------------------------------------------------------------
# the managed Ubuntu
# ---------------------------------------------------------------------------

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

# host_path <path inside Ubuntu>: where Termux sees that path. Reading the
# rootfs directly keeps `status` free of a proot login, which takes seconds.
host_path() {
  local rootfs
  if [ -n "${OC_CLAUDE_ROOTFS+x}" ]; then
    rootfs="$OC_CLAUDE_ROOTFS"
  else
    rootfs=$(rootfs_dir) || return 1
  fi
  printf '%s%s' "$rootfs" "$1"
}

# in_ubuntu <command...>: runs as Ubuntu's root, in the manager's container,
# with the app's Node and agents first on PATH. Only installation needs root
# (it writes /opt); everything a person's code touches uses as_agent.
# UV_USE_IO_URING=0 keeps Node's filesystem calls visible to PRoot.
in_ubuntu() {
  proot-distro login "$PROOT_NAME" -- env \
    PATH="$UBUNTU_PATH" HOME=/root UV_USE_IO_URING=0 "$@"
}

# as_agent <command...>: the same container as the unprivileged agent user.
as_agent() {
  proot-distro login --user "$AGENT_USER" "$PROOT_NAME" -- env \
    PATH="$UBUNTU_PATH" HOME="$UBUNTU_HOME" UV_USE_IO_URING=0 "$@"
}

# Idempotent, so start can call it for installs made before the user existed.
ensure_agent_user() {
  in_ubuntu bash -c 'id "$1" >/dev/null 2>&1 || useradd -m -s /bin/bash "$1"' \
    oc-user "$AGENT_USER" >/dev/null 2>&1
}

ubuntu_usable() {
  rootfs_dir >/dev/null 2>&1 &&
    command -v proot-distro >/dev/null 2>&1 &&
    proot-distro login "$PROOT_NAME" -- true >/dev/null 2>&1
}

install_present() {
  local node paseo claude
  node=$(host_path "$NODE_DIR/bin/node") || return 1
  paseo=$(host_path "$AGENTS_DIR/bin/paseo") || return 1
  claude=$(host_path "$AGENTS_DIR/bin/claude") || return 1
  [ -e "$node" ] && { [ -e "$paseo" ] || [ -L "$paseo" ]; } &&
    { [ -e "$claude" ] || [ -L "$claude" ]; }
}

# ---------------------------------------------------------------------------
# install
# ---------------------------------------------------------------------------

free_mb() {
  local kb
  command -v df >/dev/null 2>&1 && command -v awk >/dev/null 2>&1 || { echo 999999; return 0; }
  kb=$(df -Pk "$1" 2>/dev/null | awk 'NR==2 {print $4}') || kb=''
  case "$kb" in ''|*[!0-9]*) echo 999999 ;; *) echo $((kb / 1024)) ;; esac
}

require_space() {
  local need="$1" what="$2" have
  have=$(free_mb "$HOME")
  [ "$have" -ge "$need" ] ||
    fail no_space "Not enough space on this phone for $what: $have MB free, $need MB needed"
}

installed_node_version() {
  local marker
  marker=$(host_path "$NODE_DIR/.oc-node-version") || return 0
  cat "$marker" 2>/dev/null || true
}

install_node() {
  local version="$1" arch="$2" sha="$3" base="$4"
  if [ "$(installed_node_version)" = "$version" ]; then
    log "Node.js $version is already installed"
    return 0
  fi
  local file="node-$version-linux-$arch.tar.gz"
  local part="$TMP_DIR/$file.part" archive="$TMP_DIR/$file"
  mkdir -p "$TMP_DIR"
  chmod 700 "$TMP_DIR"
  rm -f "$part" "$archive"
  write_state installing "Downloading Node.js $version"
  log "downloading $base/$version/$file"
  curl --fail --location --silent --show-error --retry 5 --retry-delay 2 \
    --connect-timeout 20 -o "$part" "$base/$version/$file" ||
    { rm -f "$part"; fail download "Could not download Node.js $version; check the network and try again"; }
  write_state installing 'Verifying Node.js'
  local actual
  actual=$(sha256sum "$part" | cut -d' ' -f1)
  if [ "$actual" != "$sha" ]; then
    rm -f "$part"
    fail checksum "The Node.js download did not match its pinned checksum (got $actual)"
  fi
  # Only a verified archive gets its final name, so an interrupted run can
  # never leave something that looks complete.
  mv -f "$part" "$archive"
  log "verified $file"
  write_state installing "Unpacking Node.js $version"
  # The archive is streamed in: no bind mount, and the unpacked tree only
  # replaces the old one once its own node binary answers.
  in_ubuntu bash -c '
set -eu
rm -rf "$1.new"
mkdir -p "$1.new"
tar -xzf - --strip-components=1 -C "$1.new"
[ "$("$1.new/bin/node" --version)" = "$2" ]
rm -rf "$1"
mv "$1.new" "$1"
printf "%s" "$2" > "$1/.oc-node-version"
' oc-node "$NODE_DIR" "$version" < "$archive" ||
    fail error "Node.js $version could not be unpacked in Ubuntu"
  rm -rf "$TMP_DIR"
  log "installed Node.js $version in $NODE_DIR"
}

# npm_install <package spec>: into the app's own prefix, re-runnable. The
# cache is temporary: npm's persistent cache would keep hundreds of MB.
# npm 11 runs only the install scripts it is told to allow: exactly this
# package's own, by name ("@scope/pkg@1.2.3" -> "@scope/pkg").
npm_install() {
  local spec="$1" output="$CLAUDE_DIR/npm-last.log" code=0 name="$1"
  case "$spec" in ?*@*) name="${spec%@*}" ;; esac
  in_ubuntu env NODE_OPTIONS=--dns-result-order=ipv4first bash -c '
set -u
cache=$(mktemp -d /tmp/oc-claude-npm.XXXXXX)
npm install -g --no-fund --no-audit --prefix "$1" --cache "$cache" \
  --allow-scripts="$3" \
  --fetch-retries=5 --fetch-retry-mintimeout=10000 --fetch-timeout=300000 "$2"
code=$?
rm -rf -- "$cache"
exit "$code"
' oc-npm "$AGENTS_DIR" "$spec" "$name" > "$output" 2>&1 || code=$?
  cat "$output"
  if [ "$code" -ne 0 ]; then
    # A native module that had to compile and could not. No toolchain is
    # installed behind the person's back; the log says what npm wanted.
    if grep -Eqi 'node-gyp|gyp ERR|prebuild|node-pty|make: |g\+\+|not found: (make|python)' "$output"; then
      rm -f "$output"
      fail native_build "$spec needs a native module that has no ready-made build for this phone"
    fi
    rm -f "$output"
    fail npm "Could not install $spec; check the network and try again"
  fi
  rm -f "$output"
}

install_agents() {
  begin_verb install
  if ! ubuntu_usable; then
    fail needs_ubuntu 'Ubuntu is not set up on this phone yet. Run the On this phone setup first, then install Claude Code'
  fi
  local arch
  case "${OC_CLAUDE_ARCH:-$(uname -m)}" in
    aarch64|arm64) arch=arm64 ;;
    x86_64|amd64) arch=x64 ;;
    *) fail unsupported_arch "Claude Code needs a 64-bit phone (this one reports $(uname -m))" ;;
  esac
  local version sha base paseo_version
  version=$(pin_value node_version)
  sha=$(pin_value "node_sha256_$arch")
  base=$(pin_value node_base_url)
  paseo_version=$(pin_value paseo_version)
  [[ "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail pins 'This build has no valid Node.js version pin'
  [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || fail pins 'This build has no valid Node.js checksum pin'
  [[ "$paseo_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail pins 'This build has no valid Paseo version pin'
  case "$base" in
    https://*) ;;
    http://127.0.0.1:*) [ "${OC_CLAUDE_ALLOW_LOOPBACK_PINS:-}" = 1 ] || fail pins 'Node.js must be downloaded over https' ;;
    *) fail pins 'Node.js must be downloaded over https' ;;
  esac

  if install_present && [ "$(installed_node_version)" = "$version" ]; then
    require_space "$REPAIR_REQUIRED_MB" 'updating Claude Code'
  else
    require_space "$INSTALL_REQUIRED_MB" 'Node.js, Paseo and Claude Code (about 1 GB installed)'
  fi

  CURRENT_STEP=node
  install_node "$version" "$arch" "$sha" "$base"

  CURRENT_STEP=paseo
  write_state installing "Installing Paseo $paseo_version"
  npm_install "@getpaseo/cli@$paseo_version"

  CURRENT_STEP=claude
  write_state installing 'Installing Claude Code'
  npm_install '@anthropic-ai/claude-code'

  write_state installing 'Checking the installed versions'
  local paseo_installed claude_installed
  paseo_installed=$(in_ubuntu paseo --version 2>/dev/null | head -1 | tr -d '\r' | awk '{print $NF}' || true)
  claude_installed=$(in_ubuntu claude --version 2>/dev/null | head -1 | tr -d '\r' | awk '{print $1}' || true)
  [ -n "$claude_installed" ] || fail error 'Claude Code installed but does not run in Ubuntu'
  [ -n "$paseo_installed" ] || fail error 'Paseo installed but does not run in Ubuntu'
  # Paseo's terminal support loads node-pty. It ships glibc builds for arm64
  # and x64; say so plainly when this phone cannot load it.
  if in_ubuntu node -e 'require("module").createRequire(process.argv[1] + "/")("node-pty")' \
      "$AGENTS_DIR/lib/node_modules/@getpaseo/cli" >/dev/null 2>&1; then
    set_config node_pty ok
  else
    set_config node_pty failed
    log 'warning: node-pty did not load; terminals inside Paseo may not work'
  fi
  ensure_agent_user
  as_agent mkdir -p "$PROJECTS_DIR" >/dev/null 2>&1 || true
  set_config node_version "$version"
  set_config paseo_version "$paseo_installed"
  set_config claude_version "$claude_installed"
  set_config installed_at "$(date +%s)"
  CURRENT_STEP=''
  write_state installed 'Claude Code is installed'
  log "install finished: node $version, paseo $paseo_installed, claude $claude_installed"
}

# ---------------------------------------------------------------------------
# the daemon
# ---------------------------------------------------------------------------

ensure_password() {
  [ ! -s "$PASSWORD_FILE" ] || return 0
  local value tmp="$PASSWORD_FILE.tmp.$$"
  umask 077
  # 24 random bytes as hex: no spaces or commas, which the daemon's
  # WebSocket subprotocol cannot carry. printf is a builtin, so the value
  # never shows up in a process list.
  value=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
  [ "${#value}" -eq 48 ] || fail error 'Could not create the daemon password'
  printf '%s\n' "$value" > "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$PASSWORD_FILE"
}

daemon_pid() { cat "$DAEMON_PID" 2>/dev/null || true; }

daemon_alive() {
  local pid
  pid=$(daemon_pid)
  process_alive "$pid" || return 1
  case "$(process_cmdline "$pid")" in *claude.sh*run-daemon*) return 0 ;; esac
  return 1
}

healthy() {
  local body
  body=$(curl -s -m 5 "$HEALTH_URL" 2>/dev/null || true)
  printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"'
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; }

# run-daemon: the detached runner. The password reaches the daemon as an
# environment variable that the inner shell reads from stdin, so it is in no
# argument list on either side of proot.
run_daemon() {
  [ -s "$PASSWORD_FILE" ] || exit 66
  rotate_file "$DAEMON_LOG"
  # Dictation and voice mode are off: left on, the daemon starts downloading
  # local speech models (hundreds of MB) the moment it starts, which a phone
  # driving a text conversation never uses.
  proot-distro login --user "$AGENT_USER" --work-dir "$PROJECTS_DIR" "$PROOT_NAME" -- env \
    PATH="$UBUNTU_PATH" HOME="$UBUNTU_HOME" UV_USE_IO_URING=0 \
    PASEO_DICTATION_ENABLED=false PASEO_VOICE_MODE_ENABLED=false \
    bash -c '
IFS= read -r PASEO_PASSWORD || [ -n "${PASEO_PASSWORD:-}" ] || exit 66
export PASEO_PASSWORD
exec paseo start --foreground --home "$1" --listen "$2" --no-relay --no-web-ui --no-inject-mcp < /dev/null
' oc-paseo "$PASEO_HOME" "$LISTEN" < "$PASSWORD_FILE" 2>&1 | write_daemon_log
  exit "${PIPESTATUS[0]}"
}

# Only the OpenCode server and the AI Team share the Termux wake lock with
# this daemon; it is released when none of them needs it any more.
release_wake_lock_if_idle() {
  local other=''
  read -r other _ < "$OC_DIR/server.pid" 2>/dev/null || true
  process_alive "$other" && return 0
  other=$(cat "$OC_DIR/aiteam/supervisor.pid" 2>/dev/null || true)
  process_alive "$other" && return 0
  termux-wake-unlock >/dev/null 2>&1 || true
}

stop_daemon_processes() {
  local pid waited=0 entry other cmdline
  pid=$(daemon_pid)
  if daemon_alive; then
    # Its own session (setsid), so the group is the runner, proot and
    # everything the daemon started.
    if [ "$(process_group "$pid" 2>/dev/null || true)" = "$pid" ]; then
      kill -TERM -- "-$pid" 2>/dev/null || true
    else
      kill -TERM "$pid" 2>/dev/null || true
    fi
    while process_alive "$pid" && [ "$waited" -lt 10 ]; do sleep "$POLL"; waited=$((waited + 1)); done
    if process_alive "$pid"; then
      kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    fi
  fi
  # Whatever of ours outlived its runner (Android kills selectively): only a
  # process started with this daemon's own home qualifies.
  # One grep narrows the scan to processes that mention the home at all, so
  # the per-process reading below does not fork once per process on the phone.
  for entry in $(grep -alF -e "$PASEO_HOME" /proc/[0-9]*/cmdline 2>/dev/null || true); do
    entry=${entry%/cmdline}
    other=${entry#/proc/}
    [ "$other" != "$$" ] || continue
    cmdline=$(process_cmdline "$other")
    case "$cmdline" in
      *"--home $PASEO_HOME --listen"*|*" oc-paseo $PASEO_HOME "*) kill -KILL "$other" 2>/dev/null || true ;;
    esac
  done
  rm -f "$DAEMON_PID"
}

start_daemon() {
  install_present || fail not_installed 'Install Claude Code on this phone first'
  ensure_password
  ensure_agent_user
  # The daemon's working directory must exist before proot enters it.
  as_agent mkdir -p "$PROJECTS_DIR" >/dev/null 2>&1 || true
  if daemon_alive && healthy; then
    write_state ready 'Claude Code is running on this phone'
    log 'daemon already running'
    return 0
  fi
  stop_daemon_processes
  if port_open; then
    fail port_in_use "Port $PORT on this phone is already used by another program"
  fi
  write_state starting 'Starting Claude Code'
  termux-wake-lock >/dev/null 2>&1 || true
  # Its own session with none of this verb's descriptors (the log tee's pipe
  # included): the verb exits, the daemon keeps running.
  (
    for fd in /proc/$BASHPID/fd/*; do
      fd=${fd##*/}
      [ "$fd" -gt 2 ] 2>/dev/null && eval "exec $fd>&-"
    done
    nohup setsid "${BASH:-bash}" "$SELF" run-daemon > /dev/null 2>&1 < /dev/null &
    echo $! > "$DAEMON_PID"
  )
  local pid waited=0
  pid=$(daemon_pid)
  log "daemon pid $pid on $LISTEN"
  until healthy; do
    if ! process_alive "$pid"; then
      tail -n 20 "$DAEMON_LOG" 2>/dev/null | sed 's/^/[daemon] /' || true
      rm -f "$DAEMON_PID"
      fail daemon_exited 'Claude Code stopped while it was starting'
    fi
    if [ "$waited" -ge "$HEALTH_TIMEOUT" ]; then
      tail -n 20 "$DAEMON_LOG" 2>/dev/null | sed 's/^/[daemon] /' || true
      stop_daemon_processes
      fail timeout "Claude Code did not answer on $LISTEN within $HEALTH_TIMEOUT seconds"
    fi
    sleep "$POLL"
    waited=$((waited + 1))
  done
  write_state ready 'Claude Code is running on this phone'
  log 'daemon healthy'
}

start_verb() {
  begin_verb start
  start_daemon
}

restart_verb() {
  begin_verb restart
  write_state starting 'Restarting Claude Code'
  stop_daemon_processes
  start_daemon
}

stop_verb() {
  begin_verb stop
  write_state stopping 'Stopping Claude Code'
  stop_daemon_processes
  release_wake_lock_if_idle
  if install_present; then
    write_state installed 'Claude Code is stopped'
  else
    rm -f "$STATE"
  fi
  log 'stopped'
}

# remove [--forget-signin]: the daemon, Node, the packages, the daemon home
# and ~/.oc/claude. The person's Claude sign-in and history (~/.claude in
# Ubuntu) stay unless --forget-signin is given. Projects are never touched.
remove_verb() {
  local forget=0 arg
  for arg in "$@"; do
    case "$arg" in
      --forget-signin) forget=1 ;;
      *) echo 'usage: claude.sh remove [--forget-signin]' >&2; exit 64 ;;
    esac
  done
  begin_verb remove
  write_state removing 'Removing Claude Code from this phone'
  stop_daemon_processes
  release_wake_lock_if_idle
  local path host
  for path in "$NODE_DIR" "$NODE_DIR.new" "$AGENTS_DIR" "$PASEO_HOME"; do
    host=$(host_path "$path") || continue
    [ -e "$host" ] || continue
    rm -rf -- "$host"
    log "removed $path"
  done
  if [ "$forget" = 1 ]; then
    for path in "$UBUNTU_HOME/.claude" "$UBUNTU_HOME/.claude.json"; do
      host=$(host_path "$path") || continue
      [ -e "$host" ] || continue
      rm -rf -- "$host"
      log "removed $path"
    done
  else
    log "kept $UBUNTU_HOME/.claude (your Claude sign-in and history)"
  fi
  log "removed $CLAUDE_DIR"
  trap - EXIT ERR
  rm -rf -- "$CLAUDE_DIR"
  exit 0
}

# ---------------------------------------------------------------------------
# sign-in
# ---------------------------------------------------------------------------

# yes | no | unknown. Looks for a stored sign-in or a configured API key
# without reading either out.
signed_in() {
  local home
  home=$(host_path "$UBUNTU_HOME") || { printf unknown; return 0; }
  [ -d "$home" ] || { printf unknown; return 0; }
  if [ -s "$home/.claude/.credentials.json" ]; then printf yes; return 0; fi
  if grep -qs '"primaryApiKey"' "$home/.claude.json"; then printf yes; return 0; fi
  if grep -Eqs 'ANTHROPIC_API_KEY|ANTHROPIC_AUTH_TOKEN|apiKeyHelper' "$home/.claude/settings.json"; then
    printf yes
    return 0
  fi
  printf no
}

# signin: runs in a foreground Termux terminal, never through the bridge.
# `claude setup-token` is deliberately not used: it prints a token for an
# environment variable and stores nothing the daemon would find.
signin_terminal() {
  if ! install_present; then
    echo 'Claude Code is not installed on this phone yet. Install it from the app first.'
    return 1
  fi
  echo 'Signing in to Claude Code.'
  echo 'Open the link it shows, approve, and paste the code back here.'
  echo
  ensure_agent_user
  if as_agent claude --help 2>/dev/null | grep -Eq '^[[:space:]]+auth([[:space:]]|$)'; then
    proot-distro login --user "$AGENT_USER" --work-dir "$UBUNTU_HOME" "$PROOT_NAME" -- env \
      PATH="$UBUNTU_PATH" HOME="$UBUNTU_HOME" UV_USE_IO_URING=0 claude auth login || true
  else
    echo 'When Claude Code opens, follow its sign-in steps, then type /exit.'
    proot-distro login --user "$AGENT_USER" --work-dir "$UBUNTU_HOME" "$PROOT_NAME" -- env \
      PATH="$UBUNTU_PATH" HOME="$UBUNTU_HOME" UV_USE_IO_URING=0 claude || true
  fi
  echo
  if [ "$(signed_in)" = yes ]; then
    echo 'Signed in. You can go back to the app now.'
  else
    echo 'No sign-in was saved. Go back to the app and try again.'
  fi
}

# ---------------------------------------------------------------------------
# projects (paths as Ubuntu sees them)
# ---------------------------------------------------------------------------

list_projects() {
  local dir entry found=0
  dir=$(host_path "$PROJECTS_DIR") || { echo 'needs-ubuntu' >&2; exit 69; }
  if [ -d "$dir" ]; then
    for entry in "$dir"/*/; do
      [ -d "$entry" ] || continue
      case "$entry" in *.git/) continue ;; esac
      entry=${entry%/}
      printf 'project=%s/%s\n' "$PROJECTS_DIR" "${entry##*/}"
      found=1
    done
  fi
  [ "$found" = 0 ] || return 0
  # Nothing yet: one ready-to-use project, so the first conversation has a
  # folder and Claude Code has a repository to work in.
  as_agent bash -c 'mkdir -p "$1" && cd "$1" && { [ -d .git ] || git init -q; }' \
    oc-project "$PROJECTS_DIR/my-first-project" >/dev/null 2>&1 ||
    as_agent mkdir -p "$PROJECTS_DIR/my-first-project" >/dev/null 2>&1 || exit 74
  printf 'project=%s/my-first-project\n' "$PROJECTS_DIR"
}

ensure_project() {
  local path="${1:-}"
  case "$path" in
    /*) ;;
    *) echo 'invalid-project-path' >&2; exit 64 ;;
  esac
  case "$path/" in */../*|*/./*) echo 'invalid-project-path' >&2; exit 64 ;; esac
  as_agent mkdir -p "$path" >/dev/null 2>&1 || exit 74
  printf 'project=%s\n' "$path"
}

# ---------------------------------------------------------------------------
# status (key=value)
# ---------------------------------------------------------------------------

status_report() {
  local phase message verb verb_pid step kind updated now age=0
  local busy=no installed=no killed=no pid=''
  phase=$(state_value phase)
  message=$(state_value message)
  verb=$(state_value verb)
  verb_pid=$(state_value verb_pid)
  step=$(state_value step)
  kind=$(state_value failure_kind)
  updated=$(state_value updated_at)
  now=$(date +%s)
  case "$updated" in ''|*[!0-9]*) ;; *) age=$((now - updated)) ;; esac
  if install_present; then installed=yes; fi
  if verb_alive "$verb_pid"; then busy=yes; fi

  case "$phase" in
    installing|starting|stopping|removing)
      if [ "$busy" = no ]; then
        # A dispatched verb has no pid until it writes its first phase, and
        # a running one is briefly unobservable while it re-execs under
        # setsid. Only a phase left unowned for a while is an interruption.
        local grace=10
        [ -n "$verb_pid" ] || grace=30
        if [ "$age" -lt "$grace" ]; then
          busy=yes
        else
          phase=failed
          kind=interrupted
          message="${verb:-The last step} stopped before it finished"
          CURRENT_VERB="$verb" CURRENT_PID='' CURRENT_STEP="$step" write_state failed "$message" interrupted
        fi
      fi ;;
  esac

  if [ "$busy" = no ] && [ "$phase" != failed ]; then
    # Never trust `ready` from the file: the pid must be alive and the
    # daemon must answer its health check right now.
    if daemon_alive; then
      pid=$(daemon_pid)
      if healthy || { sleep "$POLL"; healthy; }; then
        phase=ready
        message='Claude Code is running on this phone'
      else
        phase=failed
        kind=unhealthy
        message="Claude Code is running but does not answer on $LISTEN"
      fi
    else
      if [ "$phase" = ready ]; then
        # Android stopped it while the app was away.
        killed=yes
        CURRENT_VERB="$verb" CURRENT_PID='' CURRENT_STEP='' write_state installed 'Claude Code is not running'
        message='Claude Code is not running'
      fi
      if [ "$installed" = yes ]; then
        phase=installed
        [ -n "$message" ] || message='Claude Code is installed'
      elif rootfs_dir >/dev/null 2>&1; then
        phase=absent
        message='Claude Code is not installed on this phone'
      else
        phase=needs_ubuntu
        message='Ubuntu is not set up on this phone yet'
      fi
    fi
  elif [ "$busy" = no ] && daemon_alive; then
    pid=$(daemon_pid)
  fi

  printf 'phase=%s\nmessage=%s\nbusy=%s\nverb=%s\nstep=%s\ninstalled=%s\nnode_version=%s\npaseo_version=%s\nclaude_version=%s\npid=%s\nport=%s\nsigned_in=%s\nfailure_kind=%s\nkilled=%s\nupdated_at=%s\n' \
    "$phase" "$message" "$busy" "$verb" "$step" "$installed" \
    "$(config_value node_version)" "$(config_value paseo_version)" "$(config_value claude_version)" \
    "$pid" "$PORT" "$(signed_in)" "$([ "$phase" = failed ] && printf '%s' "$kind" || true)" \
    "$killed" "$(state_value updated_at)"
}

verb="${1:-status}"
case "$verb" in
  install|start|restart|remove)
    ensure_isolated "$@"
    shift
    case "$verb" in
      install) install_agents ;;
      start) start_verb ;;
      restart) restart_verb ;;
      remove) remove_verb "$@" ;;
    esac ;;
  stop) stop_verb ;;
  queue) shift; queue_verb "$@" ;;
  run-daemon) run_daemon ;;
  status) status_report ;;
  password)
    [ -s "$PASSWORD_FILE" ] || exit 66
    cat "$PASSWORD_FILE" ;;
  signin-status) printf 'signed_in=%s\n' "$(signed_in)" ;;
  signin) signin_terminal ;;
  projects) list_projects ;;
  ensure-project) shift; ensure_project "$@" ;;
  log) printf '%s\n' "$LOG" ;;
  *) echo "usage: $0 {status|install|start|stop|restart|remove|password|signin-status|signin|projects|ensure-project|log}" >&2; exit 64 ;;
esac
''';
