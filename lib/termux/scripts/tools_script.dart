import 'script_support.dart';

/// Where the bridge writes [termuxToolsScript].
const termuxToolsPath = '$termuxHomeDirectory/.oc/tools.sh';

const termuxToolVerbs = {
  'storage-scan',
  'storage-status',
  'storage-summary',
  'storage-cancel',
  'storage-clean',
  'procs-scan',
  'procs-stop',
};

/// Shell that installs the current tools script and runs [verb]. A
/// `storage-scan` is dispatched into the background (it measures tens of
/// gigabytes) and acknowledged with `tools-started:<pid>`; every other verb
/// runs in the foreground and prints its answer.
String termuxToolsCommandScript(String verb, {String argument = ''}) {
  if (!termuxToolVerbs.contains(verb)) {
    throw ArgumentError.value(verb, 'verb', 'Unknown tools verb.');
  }
  // A name, a number or a comma-separated list of process IDs (one
  // kind's stop): single-quoted below, so nothing here can escape it.
  if (!RegExp(r'^[A-Za-z0-9_,]{0,400}$').hasMatch(argument)) {
    throw ArgumentError.value(argument, 'argument', 'Invalid argument.');
  }
  final launch = verb == 'storage-scan'
      ? r"""
rm -f "$OC_DIR/storage-scan.cancel"
scan_pid=$(cat "$OC_DIR/storage-scan.pid" 2>/dev/null || true)
case "$scan_pid" in
  ''|*[!0-9]*) ;;
  *) if kill -0 "$scan_pid" 2>/dev/null; then
       echo "tools-already-running:$scan_pid"
       exit 0
     fi ;;
esac
set -m
nohup "$TOOLS" storage-scan >/dev/null 2>&1 </dev/null &
echo "tools-started:$!"
"""
      : 'exec "\$TOOLS" $verb${argument.isEmpty ? '' : " '$argument'"}\n';
  return '''
set -eu
OC_DIR="$termuxHomeDirectory/.oc"
TOOLS="$termuxToolsPath"
mkdir -p "\$OC_DIR"
umask 077
tools_tmp="\$TOOLS.tmp.\$\$"
cat > "\$tools_tmp" <<'OC_TOOLS_EOF'
$termuxToolsScript
OC_TOOLS_EOF
chmod 700 "\$tools_tmp"
mv "\$tools_tmp" "\$TOOLS"
[ -x "\$TOOLS" ] || { echo 'tools-install-failed' >&2; exit 74; }
$launch''';
}

/// OpenCode Mobile phone tools: storage and process views.
const termuxToolsScript = r'''#!/data/data/com.termux/files/usr/bin/bash
# OpenCode Mobile phone tools: storage and process views of the managed
# Termux environment (TEAM-304/305). Installed to ~/.oc/tools.sh beside
# manager.sh; every verb prints JSON on stdout and nothing else.
set -uo pipefail

OC_DIR="$HOME/.oc"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
PROOT_NAME=opencode-ubuntu
SCAN_JSON="$OC_DIR/storage-scan.json"
SCAN_MANIFEST="$OC_DIR/storage-scan.paths"
SCAN_LOG="$OC_DIR/storage-scan.log"
SCAN_PID="$OC_DIR/storage-scan.pid"
SCAN_STATE="$OC_DIR/storage-scan.state"
SCAN_CANCEL="$OC_DIR/storage-scan.cancel"
STOP_WAIT_SECONDS=${OC_STOP_WAIT_SECONDS:-5}
ORPHAN_CPU_SECONDS=300
mkdir -p "$OC_DIR"
umask 077

# ---------------------------------------------------------------- helpers --

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

json_str() {
  local quoted
  json_str_into quoted "$1"
  printf '%s' "$quoted"
}

# json_str_into <var> <text>: the same quoting without a subshell, for loops
# over hundreds of rows.
json_str_into() {
  local s="$2"
  s=${s//\\/\\\\}
  s=${s//\"/\\\"}
  s=${s//$'\n'/\\n}
  s=${s//$'\t'/\\t}
  s=${s//$'\r'/\\r}
  printf -v "$1" '"%s"' "$s"
}

# Bytes used by a path (file or directory); 0 when absent.
path_bytes() {
  local out
  [ -e "$1" ] || { printf 0; return; }
  out=$(du -sb -- "$1" 2>/dev/null | cut -f1)
  case "$out" in
    ''|*[!0-9]*)
      out=$(du -sk -- "$1" 2>/dev/null | cut -f1)
      case "$out" in ''|*[!0-9]*) out=0 ;; esac
      out=$((out * 1024)) ;;
  esac
  printf '%s' "$out"
}

log() { printf '[oc] %s\n' "$*"; }

now_epoch() { date +%s; }

# ---------------------------------------------------------------- storage --

# One category is described by a key, whether the app may remove its paths
# and an optional note key; the paths are appended by scan_category.
CAT_KEYS=()
CAT_DELETABLE=()
CAT_NOTE=()
CAT_BYTES=()
CAT_PATHS=()   # newline separated "bytes<TAB>path" records per category

scan_check_cancel() {
  if [ -e "$SCAN_CANCEL" ]; then
    log 'Scan cancelled'
    printf cancelled > "$SCAN_STATE"
    rm -f "$SCAN_PID"
    exit 0
  fi
}

# add_path <category index> <path>: measures and records an existing path.
add_path() {
  local index="$1" path="$2" bytes
  [ -e "$path" ] || return 0
  case "$path" in *$'\n'*|*$'\t'*) return 0 ;; esac
  scan_check_cancel
  bytes=$(path_bytes "$path")
  CAT_BYTES[$index]=$(( ${CAT_BYTES[$index]} + bytes ))
  CAT_PATHS[$index]+="$bytes"$'\t'"$path"$'\n'
  log "  $(human_bytes "$bytes")  $(display_path "$path")"
}

# The rootfs prefix is long and identical on every line; the log shows
# "ubuntu:/root/..." instead, with Termux paths relative to $PREFIX and ~.
display_path() {
  local path="$1"
  case "$path" in
    "${SCAN_ROOTFS:-/nonexistent}"/*) printf 'ubuntu:%s' "${path#"$SCAN_ROOTFS"}" ;;
    "$HOME"/*) printf '~%s' "${path#"$HOME"}" ;;
    "$PREFIX"/*) printf 'termux:%s' "${path#"$PREFIX"}" ;;
    *) printf '%s' "$path" ;;
  esac
}

new_category() {
  CAT_KEYS+=("$1")
  CAT_DELETABLE+=("$2")
  CAT_NOTE+=("${3:-}")
  CAT_BYTES+=(0)
  CAT_PATHS+=('')
  NEW_INDEX=$(( ${#CAT_KEYS[@]} - 1 ))
}

human_bytes() {
  local b="$1"
  if [ "$b" -ge 1073741824 ]; then
    printf '%d.%d GB' $((b / 1073741824)) $(( (b % 1073741824) * 10 / 1073741824 ))
  elif [ "$b" -ge 1048576 ]; then
    printf '%d MB' $((b / 1048576))
  elif [ "$b" -ge 1024 ]; then
    printf '%d KB' $((b / 1024))
  else
    printf '%d B' "$b"
  fi
}

PROJECT_JSON=''

scan_projects() {
  local rootfs="$1" projects="$rootfs/root/projects" dir name bytes build_bytes sub kind
  local build_index="$2"
  [ -d "$projects" ] || return 0
  for dir in "$projects"/*/; do
    dir=${dir%/}
    [ -d "$dir" ] || continue
    name=${dir##*/}
    scan_check_cancel
    log "Project $name"
    bytes=$(path_bytes "$dir")
    build_bytes=0
    # Names are inventory hints only: a folder named build may contain sources.
    while IFS= read -r sub; do
      [ -n "$sub" ] || continue
      kind=$(path_bytes "$sub")
      build_bytes=$((build_bytes + kind))
      add_path "$build_index" "$sub"
    done < <(find "$dir" -xdev -type d \( -name build -o -name .dart_tool -o -name node_modules -o -name target \) -prune -print 2>/dev/null | sort)
    PROJECT_JSON+="$( [ -z "$PROJECT_JSON" ] || printf ',' ){\"name\":$(json_str "$name"),\"path\":$(json_str "$dir"),\"bytes\":$bytes,\"build_bytes\":$build_bytes}"
  done
}

write_scan_result() {
  local total="$1" json='' manifest='' index key paths line bytes path first
  for index in "${!CAT_KEYS[@]}"; do
    key=${CAT_KEYS[$index]}
    paths=''
    first=1
    while IFS=$'\t' read -r bytes path; do
      [ -n "$path" ] || continue
      [ "$first" = 1 ] || paths+=','
      first=0
      paths+="{\"path\":$(json_str "$path"),\"bytes\":$bytes}"
      manifest+="$key"$'\t'"${CAT_DELETABLE[$index]}"$'\t'"$bytes"$'\t'"$path"$'\n'
    done <<< "${CAT_PATHS[$index]}"
    [ -z "$json" ] || json+=','
    json+="{\"key\":\"$key\",\"label_key\":\"termuxStorageCat_$key\",\"bytes\":${CAT_BYTES[$index]},\"deletable\":${CAT_DELETABLE[$index]}"
    [ -z "${CAT_NOTE[$index]}" ] || json+=",\"note_key\":\"${CAT_NOTE[$index]}\""
    json+=",\"paths\":[$paths]}"
  done
  printf '{"scanned_at":%s,"total_bytes":%s,"categories":[%s],"projects":[%s],"cleanup_policy":2,"stale":false}\n' \
    "$(now_epoch)" "$total" "$json" "$PROJECT_JSON" > "$SCAN_JSON.tmp.$$"
  printf '%s' "$manifest" > "$SCAN_MANIFEST.tmp.$$"
  mv "$SCAN_MANIFEST.tmp.$$" "$SCAN_MANIFEST"
  mv "$SCAN_JSON.tmp.$$" "$SCAN_JSON"
}

# Scans and cleans share one lock so a scan cannot publish pre-clean sizes
# during removal. A dead owner is recoverable without touching user data.
storage_operation_lock() {
  local lock="$OC_DIR/storage-operation.lock" owner='' modified now identity claim
  if [ -e "$lock" ] || [ -L "$lock" ]; then
    [ -d "$lock" ] && [ ! -L "$lock" ] || return 1
    identity=$(stat -c '%d:%i:%Y' -- "$lock" 2>/dev/null) || return 1
    read -r owner < "$lock/pid" 2>/dev/null || true
    case "$owner" in
      '')
        # Older tools could crash between mkdir and writing the PID. Never
        # reclaim a fresh directory: its creator may still be initializing it.
        modified=${identity##*:}
        now=$(now_epoch)
        case "$modified:$now" in *[!0-9:]*) return 1 ;; esac
        [ "$((now - modified))" -ge 60 ] || return 1
        [ ! -L "$lock/pid" ] && [ ! -s "$lock/pid" ] || return 1 ;;
      *[!0-9]*) return 1 ;;
      *) kill -0 "$owner" 2>/dev/null && return 1 ;;
    esac
    # Another caller may have recovered this lock while it was inspected.
    [ "$(stat -c '%d:%i:%Y' -- "$lock" 2>/dev/null)" = "$identity" ] || return 1
    [ "$(cat "$lock/pid" 2>/dev/null)" = "$owner" ] || return 1
    rm -f -- "$lock/pid"
    rmdir -- "$lock" 2>/dev/null || return 1
  fi
  # Publish a complete directory atomically. A competing complete lock is
  # nonempty, so mv -T cannot replace it. There is no new empty-PID window.
  claim=$(mktemp -d "$OC_DIR/storage-operation-claim.XXXXXX") || return 1
  if ! printf '%s\n' "$$" > "$claim/pid" ||
     ! mv -T -- "$claim" "$lock" 2>/dev/null; then
    rm -f -- "$claim/pid"
    rmdir -- "$claim" 2>/dev/null || true
    return 1
  fi
  trap 'rm -f -- "$OC_DIR/storage-operation.lock/pid"; rmdir -- "$OC_DIR/storage-operation.lock" 2>/dev/null || true' EXIT
}

storage_scan() {
  storage_operation_lock || { echo 'storage-busy' >&2; return 75; }
  # The dispatcher clears a stale cancel file before launching; one placed
  # after that (or before the first measurement) stops the scan early.
  printf '%s\n' "$$" > "$SCAN_PID"
  printf running > "$SCAN_STATE"
  : > "$SCAN_LOG"
  exec >> "$SCAN_LOG" 2>&1
  if ( storage_scan_body ); then
    [ "$(cat "$SCAN_STATE" 2>/dev/null)" != running ] || printf done > "$SCAN_STATE"
  else
    log 'Scan failed'
    printf failed > "$SCAN_STATE"
  fi
  rm -f "$SCAN_PID"
}

storage_scan_body() {
  local rootfs i_build i_scratch i_outputs i_tool i_team i_oc i_projects i_shared dir total
  log 'Measuring storage on this phone'
  rootfs=$(rootfs_dir || true)
  SCAN_ROOTFS=$rootfs
  if [ -n "$rootfs" ]; then
    log "Ubuntu rootfs: $rootfs"
  else
    log 'Ubuntu rootfs: not installed'
  fi

  new_category build_caches true termuxStorageNoteBuildCaches; i_build=$NEW_INDEX
  new_category agent_scratch false termuxStorageNoteAgentScratch; i_scratch=$NEW_INDEX
  new_category project_build_outputs false termuxStorageNoteProjectBuildOutputs; i_outputs=$NEW_INDEX
  new_category toolchains false termuxStorageNoteToolchains; i_tool=$NEW_INDEX
  new_category ai_team false termuxStorageNoteAiTeam; i_team=$NEW_INDEX
  new_category opencode false termuxStorageNoteOpenCode; i_oc=$NEW_INDEX
  new_category projects false termuxStorageNoteProjects; i_projects=$NEW_INDEX

  new_category shared_caches false termuxStorageNoteSharedCaches; i_shared=$NEW_INDEX

  log 'Build caches'
  if [ -n "$rootfs" ]; then
    add_path "$i_build" "$rootfs/root/.gradle/caches"
    add_path "$i_build" "$rootfs/root/.npm/_cacache"
  fi
  add_path "$i_build" "$HOME/.npm/_cacache"

  log 'Other caches and package data (read only)'
  if [ -n "$rootfs" ]; then
    for dir in "$rootfs/root/.gradle/wrapper" "$rootfs/root/.pub-cache" \
               "$rootfs/root/.cache" "$rootfs/root/.dartServer"; do
      add_path "$i_shared" "$dir"
    done
  fi
  add_path "$i_shared" "$HOME/.cache"
  # npm may contain configuration and other user data outside _cacache.
  for dir in "$HOME/.npm"/* "$HOME/.npm"/.[!.]*; do
    [ "${dir##*/}" = _cacache ] || add_path "$i_shared" "$dir"
  done

  log 'Agent scratch'
  if [ -n "$rootfs" ] && [ -d "$rootfs/tmp/opencode" ]; then
    for dir in "$rootfs/tmp/opencode"/* "$rootfs/tmp/opencode"/.[!.]*; do
      [ -e "$dir" ] || continue
      case "${dir##*/}" in
        flutter|flutter-*|*.tar.xz|*.zip) continue ;;
      esac
      add_path "$i_scratch" "$dir"
    done
  fi
  add_path "$i_scratch" "$PREFIX/tmp"

  log 'Toolchains'
  if [ -n "$rootfs" ]; then
    add_path "$i_tool" "$rootfs/usr/lib/android-sdk"
    add_path "$i_tool" "$rootfs/usr/lib/jvm"
    if [ -d "$rootfs/tmp/opencode" ]; then
      for dir in "$rootfs/tmp/opencode"/*; do
        [ -e "$dir" ] || continue
        case "${dir##*/}" in
          flutter|flutter-*|*.tar.xz|*.zip) add_path "$i_tool" "$dir" ;;
        esac
      done
    fi
  fi

  log 'AI Team'
  add_path "$i_team" "$HOME/.oc/aiteam"
  add_path "$i_team" "$HOME/.oc/city"
  for dir in "$PREFIX/bin/gc" "$PREFIX/bin/bd" "$PREFIX/bin/dolt"; do
    add_path "$i_team" "$dir"
  done
  if [ -n "$rootfs" ]; then
    for dir in "$rootfs"/root/aiteam* "$rootfs/opt/aiteam"; do
      add_path "$i_team" "$dir"
    done
  fi

  log 'OpenCode'
  if [ -n "$rootfs" ]; then
    add_path "$i_oc" "$rootfs/usr/local/lib/node_modules"
    add_path "$i_oc" "$rootfs/root/.local/share/opencode"
  fi

  log 'Projects'
  if [ -n "$rootfs" ]; then
    scan_projects "$rootfs" "$i_outputs"
    for dir in "$rootfs"/root/projects/*/; do
      dir=${dir%/}
      [ -d "$dir" ] || continue
      add_path "$i_projects" "$dir"
    done
  fi

  scan_check_cancel
  log 'Measuring the whole Termux install'
  total=$(( $(path_bytes "$PREFIX") + $(path_bytes "$HOME") ))
  write_scan_result "$total"
  log "Scan complete: $(human_bytes "$total") used"
}

scan_pid_alive() {
  local pid
  pid=$(cat "$SCAN_PID" 2>/dev/null || true)
  case "$pid" in ''|*[!0-9]*) return 1 ;; esac
  kill -0 "$pid" 2>/dev/null
}

storage_status() {
  local state
  state=$(cat "$SCAN_STATE" 2>/dev/null || printf idle)
  if [ "$state" = running ] && ! scan_pid_alive; then
    state=failed
    printf failed > "$SCAN_STATE"
  fi
  printf 'state=%s\n' "$state"
  printf '%s\n' '__OC_TOOLS_LOG__'
  tail -n 80 "$SCAN_LOG" 2>/dev/null || true
  printf '%s\n' '__OC_TOOLS_JSON__'
  # While a scan runs the file is the previous scan's; the app already has it.
  [ "$state" = running ] || [ ! -f "$SCAN_JSON" ] || cat "$SCAN_JSON"
}

# Cheap answer for the settings row: the last scan's total and clock.
storage_summary() {
  local state
  state=$(cat "$SCAN_STATE" 2>/dev/null || printf idle)
  if [ "$state" = running ] && ! scan_pid_alive; then state=failed; fi
  printf 'state=%s\n' "$state"
  [ "$state" != stale ] || return 0
  [ -f "$SCAN_JSON" ] || return 0
  sed -n 's/^{"scanned_at":\([0-9]*\),"total_bytes":\([0-9]*\),.*/scanned_at=\1\ntotal_bytes=\2/p' "$SCAN_JSON"
}

storage_cancel() {
  : > "$SCAN_CANCEL"
  if scan_pid_alive; then
    local pid
    pid=$(cat "$SCAN_PID")
    # du is the long pole; interrupt the whole scan group politely.
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
    printf cancelled > "$SCAN_STATE"
    rm -f "$SCAN_PID"
  fi
  printf '{"cancelled":true}\n'
}

# process_users <category> <snapshot>: classifies a successfully captured
# process snapshot, printing users of a category one per line.
process_users() {
  local category="$1" snapshot="$2" line pid comm args
  while IFS= read -r line; do
    set -- $line
    pid=${1:-}
    case "$pid" in ''|*[!0-9]*) continue ;; esac
    [ "$pid" != "$$" ] || continue
    comm=${7:-}
    shift 7 2>/dev/null || shift $#
    args="$*"
    case "$category" in
      build_caches|project_build_outputs|toolchains)
        case "$args" in
          *GradleDaemon*|*gradle*wrapper*|*"gradle "*|*KotlinCompileDaemon*|*analysis_server*|*frontend_server*|*"flutter "*|*"flutter_tools"*|*npm-cli.js*|*npx-cli.js*|*"npm install"*|*"npm ci"*|*"npm cache"*)
            printf '%s\n' "$comm" ;;
        esac ;;
      agent_scratch)
        case "$args" in
          *"opencode acp"*|*"opencode run"*|*"opencode2 acp"*|*"opencode2 run"*|*/tmp/opencode/*)
            printf '%s\n' "$comm" ;;
        esac ;;
      ai_team)
        case "$comm" in gc|dolt|bd|tmux) printf '%s\n' "$comm" ;; esac
        case "$args" in *"/bin/gc "*|*"dolt sql-server"*|*"gc start"*) printf '%s\n' "$comm" ;; esac ;;
      opencode)
        case "$args" in *"opencode serve"*|*"opencode2 serve"*|*node*) printf '%s\n' "$comm" ;; esac ;;
    esac
  done <<< "$snapshot"
}

# Exact regenerable caches only. Never authorize by basename, category flag,
# or a broad HOME/PREFIX prefix from a previous scan.
clean_path_allowed() {
  local path="$1" rootfs="$2" canonical expected base
  case "$path" in
    "$HOME/.npm/_cacache")
      base=$(realpath -e -- "$HOME" 2>/dev/null) || return 1
      expected="$base/.npm/_cacache" ;;
    *)
      [ -n "$rootfs" ] || return 1
      case "$path" in
        "$rootfs/root/.gradle/caches"|"$rootfs/root/.npm/_cacache") ;;
        *) return 1 ;;
      esac
      base=$(realpath -e -- "$PREFIX" 2>/dev/null) || return 1
      expected="$base${path#"$PREFIX"}" ;;
  esac
  # Android may alias /data/data and /data/user/0. Resolve the trusted Termux
  # anchors, then refuse replaced parents, rootfs or cache symlinks beneath them.
  canonical=$(realpath -e -- "$path" 2>/dev/null) || return 1
  [ "$canonical" = "$expected" ] && [ -d "$path" ] && [ ! -L "$path" ] || return 1
  [ "$(stat -c %u -- "$path" 2>/dev/null)" = "$(id -u)" ]
}

# A failed measurement must never become zero in destructive accounting.
clean_path_bytes() {
  local out
  out=$(du -sb -- "$1" 2>/dev/null) || return 1
  out=${out%%$'\t'*}
  case "$out" in ''|*[!0-9]*) return 1 ;; esac
  printf '%s' "$out"
}

storage_clean() {
  local category="${1:-}" rootfs users line manifest_key deletable bytes path freed=0 reason
  local removed='' refused='' kept='' before after snapshot process_names
  case "$category" in
    build_caches) ;;
    agent_scratch|project_build_outputs|toolchains|ai_team|opencode|projects|shared_caches)
      printf '{"freed_bytes":0,"removed":[],"refused":[{"path":"","reason":"never_deletable"}]}\n'
      return 0 ;;
    *) echo 'unknown-category' >&2; return 64 ;;
  esac
  storage_operation_lock || { echo 'storage-busy' >&2; return 75; }
  [ -f "$SCAN_MANIFEST" ] || { echo 'no-scan' >&2; return 65; }
  # Legacy reports do not carry the narrower policy. Interrupted cleanups and
  # scans cannot supply an actionable report either.
  if [ "$(cat "$SCAN_STATE" 2>/dev/null)" != done ] ||
     ! grep -q '"cleanup_policy":2,"stale":false' "$SCAN_JSON" 2>/dev/null; then
    printf '{"freed_bytes":0,"removed":[],"refused":[{"path":"","reason":"rescan_required"}],"rescan_required":true}\n'
    return 0
  fi
  rootfs=$(rootfs_dir || true)
  # Process substitution hides its producer's exit status. Capture and check
  # discovery and classification before changing any report or cache path.
  if ! snapshot=$(list_processes) ||
     ! process_names=$(process_users "$category" "$snapshot" | sort -u); then
    printf '{"freed_bytes":0,"removed":[],"refused":[{"path":"","reason":"process_check_failed"}]}\n'
    return 0
  fi
  users=''
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    [ -z "$users" ] || users+=','
    users+=$(json_str "$line")
  done <<< "$process_names"
  if [ -n "$users" ]; then
    printf '{"freed_bytes":0,"removed":[],"refused":[{"path":"","reason":"in_use","processes":[%s]}]}\n' "$users"
    return 0
  fi
  # Invalidate before mutation so crashes cannot leave a fresh-looking report.
  printf stale > "$SCAN_STATE"
  sed 's/"stale":false/"stale":true/' "$SCAN_JSON" > "$SCAN_JSON.tmp.$$" || return 1
  mv "$SCAN_JSON.tmp.$$" "$SCAN_JSON" || return 1
  while IFS= read -r line; do
    IFS=$'\t' read -r manifest_key deletable bytes path <<< "$line"
    if [ "$manifest_key" != "$category" ]; then
      kept+="$line"$'\n'
      continue
    fi
    reason=''
    if [ "$deletable" != true ] || ! clean_path_allowed "$path" "$rootfs"; then
      reason=protected
    elif ! before=$(clean_path_bytes "$path"); then
      reason=measure_failed
    else
      rm -rf --one-file-system -- "$path" 2>/dev/null || true
      if [ -e "$path" ] || [ -L "$path" ]; then
        # Keep the entry on partial failure, and count only measured shrinkage.
        reason=remove_failed
        if after=$(clean_path_bytes "$path") && [ "$after" -lt "$before" ]; then
          freed=$((freed + before - after))
        fi
      else
        freed=$((freed + before))
        [ -z "$removed" ] || removed+=','
        removed+="{\"path\":$(json_str "$path"),\"bytes\":$before}"
      fi
    fi
    if [ -n "$reason" ]; then
      kept+="$line"$'\n'
      [ -z "$refused" ] || refused+=','
      refused+="{\"path\":$(json_str "$path"),\"reason\":\"$reason\"}"
    fi
  done < "$SCAN_MANIFEST"
  printf '%s' "$kept" > "$SCAN_MANIFEST.tmp.$$"
  mv "$SCAN_MANIFEST.tmp.$$" "$SCAN_MANIFEST"
  printf '{"freed_bytes":%s,"removed":[%s],"refused":[%s],"rescan_required":true}\n' "$freed" "$removed" "$refused"
}

# -------------------------------------------------------------- processes --

# Every process this user can see, one per line:
# pid ppid pcpu time rss etimes comm args. Termux ships procps; the /proc
# walk covers a base install without it.
list_processes() {
  if command -v ps >/dev/null 2>&1; then
    ps -eo pid=,ppid=,pcpu=,time=,rss=,etimes=,comm=,args= 2>/dev/null
    return
  fi
  local dir pid stat rest fields comm ppid utime stime rss start uptime ticks=100 cpu elapsed args cmd_parts pcpu
  uptime=$(cut -d' ' -f1 /proc/uptime 2>/dev/null); uptime=${uptime%%.*}
  for dir in /proc/[0-9]*; do
    pid=${dir#/proc/}
    { IFS= read -r stat < "$dir/stat"; } 2>/dev/null || continue
    comm=${stat#*(}; comm=${comm%%)*}
    rest=${stat##*) }
    fields=($rest)
    ppid=${fields[1]:-0}; utime=${fields[11]:-0}; stime=${fields[12]:-0}
    start=${fields[19]:-0}; rss=$(( ${fields[21]:-0} * 4 ))
    cpu=$(( (utime + stime) / ticks ))
    elapsed=$(( ${uptime:-0} - start / ticks )); [ "$elapsed" -ge 0 ] || elapsed=0
    cmd_parts=()
    { mapfile -d "" -t cmd_parts < "$dir/cmdline"; } 2>/dev/null || true
    args="${cmd_parts[*]:-}"
    [ -n "$args" ] || args=$comm
    pcpu=0
    [ "$elapsed" -le 0 ] || pcpu=$((cpu * 100 / elapsed))
    printf '%s %s %s %s %s %s %s %s\n' "$pid" "$ppid" \
      "$pcpu" "$cpu" "$rss" "$elapsed" "$comm" "$args"
  done
}


declare -A P_PPID P_CPU P_TIME P_RSS P_ELAPSED P_COMM P_ARGS P_GROUP P_PROTECTED P_REASON
P_ORDER=()

time_to_seconds() {
  local t="$1" days=0 h=0 m=0 s=0 hms
  case "$t" in *-*) days=${t%%-*}; t=${t#*-} ;; esac
  hms=(${t//:/ })
  case "${#hms[@]}" in
    3) h=${hms[0]}; m=${hms[1]}; s=${hms[2]} ;;
    2) m=${hms[0]}; s=${hms[1]} ;;
    1) s=${hms[0]} ;;
  esac
  for v in days h m s; do
    case "${!v}" in ''|*[!0-9]*) printf -v "$v" 0 ;; esac
  done
  printf '%s' $(( ((days * 24 + 10#$h) * 60 + 10#$m) * 60 + 10#$s ))
}

read_processes() {
  local line pid ppid cpu time rss elapsed comm args
  while IFS= read -r line; do
    set -- $line
    pid=${1:-}
    case "$pid" in ''|*[!0-9]*) continue ;; esac
    ppid=${2:-0}; cpu=${3:-0}; time=${4:-0}; rss=${5:-0}; elapsed=${6:-0}; comm=${7:-}
    shift 7 2>/dev/null || shift $#
    args="$*"
    [ "$pid" != "$$" ] && [ "$pid" != 1 ] || continue
    case "$comm" in ps) continue ;; esac
    case "$args" in "$0 "*|*"/.oc/tools.sh "*) continue ;; esac
    case "$ppid" in ''|*[!0-9]*) ppid=0 ;; esac
    case "$rss" in ''|*[!0-9]*) rss=0 ;; esac
    case "$elapsed" in ''|*[!0-9]*) elapsed=0 ;; esac
    case "$cpu" in ''|*[!0-9.]*) cpu=0 ;; esac
    P_ORDER+=("$pid")
    P_PPID[$pid]=$ppid
    P_CPU[$pid]=$cpu
    P_TIME[$pid]=$(time_to_seconds "$time")
    P_RSS[$pid]=$rss
    P_ELAPSED[$pid]=$elapsed
    P_COMM[$pid]=$comm
    P_ARGS[$pid]=$args
    P_GROUP[$pid]=''
    P_PROTECTED[$pid]=false
    P_REASON[$pid]=''
  done < <(list_processes)
}

is_opencode_server() {
  case "${P_ARGS[$1]}" in
    *"opencode serve"*|*"opencode2 serve"*|*"/.oc/manager.sh"*|*"/.oc/server-runner.sh"*) return 0 ;;
  esac
  return 1
}

is_sshd() {
  [ "${P_COMM[$1]}" = sshd ] && return 0
  case "${P_ARGS[$1]}" in sshd|"sshd "*|*/sshd|*"/sshd "*) return 0 ;; esac
  return 1
}

is_ai_team_root() {
  case "${P_COMM[$1]}" in gc|dolt|tmux|bd) return 0 ;; esac
  case "${P_ARGS[$1]}" in
    *"opencode acp"*|*"opencode2 acp"*|*"/bin/gc "*|*"dolt sql-server"*|"tmux"*|*"/bin/tmux"*|*"/bin/dolt"*|*"/bin/bd "*|*"/.oc/aiteam/"*) return 0 ;;
  esac
  return 1
}

is_build_daemon() {
  case "${P_ARGS[$1]}" in
    *GradleDaemon*|*GradleWrapperMain*|*KotlinCompileDaemon*|*analysis_server*|*frontend_server*|*"dart:analysis"*|*"gradle"*) return 0 ;;
  esac
  case "${P_COMM[$1]}" in java|gradle|kotlin*|dart) return 0 ;; esac
  return 1
}

# has_ancestor <pid> <predicate>: walks live parents.
has_ancestor() {
  local pid="${P_PPID[$1]:-0}" guard=0
  while [ -n "${pid:-}" ] && [ "$pid" != 0 ] && [ "$guard" -lt 64 ]; do
    [ -n "${P_COMM[$pid]+x}" ] || return 1
    if "$2" "$pid"; then return 0; fi
    pid=${P_PPID[$pid]:-0}
    guard=$((guard + 1))
  done
  return 1
}

group_in() { [ "${P_GROUP[$1]}" = "$2" ]; }
group_opencode() { group_in "$1" opencode_server; }
group_ai_team() { group_in "$1" ai_team; }
group_build() { group_in "$1" build_daemons; }
group_owner() { group_opencode "$1" || group_ai_team "$1"; }
is_named_root() { is_opencode_server "$1" || is_ai_team_root "$1" || is_build_daemon "$1"; }

classify_processes() {
  local pid
  # Roots first, then children inherit their root's group.
  for pid in "${P_ORDER[@]}"; do
    if is_sshd "$pid"; then
      P_GROUP[$pid]=other; P_PROTECTED[$pid]=true
    elif is_opencode_server "$pid"; then
      P_GROUP[$pid]=opencode_server; P_PROTECTED[$pid]=true
    elif is_ai_team_root "$pid"; then
      P_GROUP[$pid]=ai_team
    fi
  done
  for pid in "${P_ORDER[@]}"; do
    [ -z "${P_GROUP[$pid]}" ] || continue
    if has_ancestor "$pid" group_ai_team; then
      P_GROUP[$pid]=ai_team
    elif has_ancestor "$pid" group_opencode; then
      P_GROUP[$pid]=opencode_server
    fi
  done
  for pid in "${P_ORDER[@]}"; do
    [ -z "${P_GROUP[$pid]}" ] || continue
    if is_build_daemon "$pid"; then
      P_GROUP[$pid]=build_daemons
    elif [ "${P_COMM[$pid]}" = node ] || [ "${P_COMM[$pid]}" = bun ]; then
      P_GROUP[$pid]=build_daemons
    fi
  done
  for pid in "${P_ORDER[@]}"; do
    [ -z "${P_GROUP[$pid]}" ] || continue
    if has_ancestor "$pid" group_build; then P_GROUP[$pid]=build_daemons; fi
  done
  # Orphans: a helper whose parent is gone, or one that has burned real CPU
  # with no live OpenCode or AI Team ancestor. Shells and the Termux app's
  # own processes are never orphans; the root of a named group keeps it.
  local ppid parent_gone
  for pid in "${P_ORDER[@]}"; do
    [ "${P_PROTECTED[$pid]}" = false ] || continue
    case "${P_COMM[$pid]}" in
      sh|bash|zsh|fish|login|sleep|com.termux*|termux*|tail|cat|proot|su) continue ;;
    esac
    ppid=${P_PPID[$pid]}
    parent_gone=0
    if [ "$ppid" = 1 ] || [ "$ppid" = 0 ] || [ -z "${P_COMM[$ppid]+x}" ]; then parent_gone=1; fi
    if is_named_root "$pid"; then continue; fi
    if [ "$parent_gone" = 1 ]; then
      P_GROUP[$pid]=orphans; P_REASON[$pid]=parent_gone
    elif [ "${P_TIME[$pid]}" -gt "$ORPHAN_CPU_SECONDS" ] && ! has_ancestor "$pid" group_owner; then
      P_GROUP[$pid]=orphans; P_REASON[$pid]=cpu_no_owner
    fi
  done
  for pid in "${P_ORDER[@]}"; do
    [ -n "${P_GROUP[$pid]}" ] || P_GROUP[$pid]=other
  done
}

proc_name() {
  local pid="$1" name="${P_COMM[$1]}" args="${P_ARGS[$1]}" first
  # A short, recognisable label: the binary plus its first verb.
  first=${args%% *}
  first=${first##*/}
  case "$args" in
    *"opencode serve"*) name='opencode serve' ;;
    *"opencode2 serve"*) name='opencode2 serve' ;;
    *"opencode acp"*) name='opencode acp' ;;
    *"opencode run"*) name='opencode run' ;;
    *GradleDaemon*) name='Gradle daemon' ;;
    *KotlinCompileDaemon*) name='Kotlin daemon' ;;
    *analysis_server*) name='Dart analysis server' ;;
    *"/.oc/manager.sh"*|*"/.oc/server-runner.sh"*) name='OpenCode manager' ;;
    *) case "$name" in node|bun|python*|sh|bash|java)
         # Name the script, not the interpreter: "minimax-coding-plan-mcp".
         local rest=${args#* } script part
         script=${rest%% *}
         if [ "$rest" != "$args" ] && [ -n "$script" ]; then
           case "$script" in -*) ;; *)
             while [ -n "$script" ]; do
               part=${script##*/}
               case "$part" in
                 ''|index.js|index.mjs|index.cjs|main.js|cli.js|server.js|dist|bin|build|lib|out|src|.bin)
                   [ "$script" != "$part" ] || { script=''; break; }
                   script=${script%/*} ;;
                 *) name=$part; break ;;
               esac
             done ;;
           esac
         fi ;;
       esac ;;
  esac
  printf '%s' "$name"
}

procs_json() {
  local pid cwd first=1 jname jcmd jcwd jreason
  printf '['
  for pid in "${P_ORDER[@]}"; do
    cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null || true)
    [ "$first" = 1 ] || printf ','
    first=0
    json_str_into jname "$(proc_name "$pid")"
    json_str_into jcmd "${P_ARGS[$pid]}"
    json_str_into jcwd "$cwd"
    if [ -z "${P_REASON[$pid]}" ]; then jreason=null; else jreason="\"${P_REASON[$pid]}\""; fi
    printf '{"pid":%s,"ppid":%s,"group":"%s","name":%s,"cmd":%s,"cpu_pct":%s,"cpu_seconds":%s,"rss_kb":%s,"elapsed_s":%s,"cwd":%s,"orphan_reason":%s,"protected":%s}' \
      "$pid" "${P_PPID[$pid]}" "${P_GROUP[$pid]}" "$jname" \
      "$jcmd" "${P_CPU[$pid]}" "${P_TIME[$pid]}" "${P_RSS[$pid]}" \
      "${P_ELAPSED[$pid]}" "$jcwd" \
      "$jreason" \
      "${P_PROTECTED[$pid]}"
  done
  printf ']\n'
}

procs_scan() {
  read_processes
  classify_processes
  procs_json
}

procs_stop() {
  local target="${1:-}" pid targets=() refused='' first_refused=1 remaining='' stopped='' killed=''
  local first_stopped=1 first_killed=1 first_remaining=1 waited
  read_processes
  classify_processes
  case "$target" in
    '') echo 'usage: procs-stop <pid|pid,pid,...|group>' >&2; return 64 ;;
    *[!0-9,]*)
      case "$target" in
        ai_team|build_daemons|orphans|other) ;;
        opencode_server) printf '{"stopped":[],"killed":[],"remaining":[],"refused":[{"pid":0,"reason":"protected_group"}]}\n'; return 0 ;;
        *) echo 'unknown-group' >&2; return 64 ;;
      esac
      for pid in "${P_ORDER[@]}"; do
        [ "${P_GROUP[$pid]}" = "$target" ] || continue
        if [ "${P_PROTECTED[$pid]}" = true ]; then
          [ "$first_refused" = 1 ] || refused+=','
          first_refused=0
          refused+="{\"pid\":$pid,\"reason\":\"protected\"}"
        else
          targets+=("$pid")
        fi
      done ;;
    *,*)
      # One kind's processes, confirmed together: each is checked again
      # now, so a process that ended or became protected is refused.
      local list=()
      IFS=, read -r -a list <<<"$target"
      for pid in "${list[@]}"; do
        case "$pid" in ''|*[!0-9]*) continue ;; esac
        if [ -z "${P_COMM[$pid]+x}" ]; then
          [ "$first_refused" = 1 ] || refused+=','
          first_refused=0
          refused+="{\"pid\":$pid,\"reason\":\"not_found\"}"
        elif [ "${P_PROTECTED[$pid]}" = true ]; then
          [ "$first_refused" = 1 ] || refused+=','
          first_refused=0
          refused+="{\"pid\":$pid,\"reason\":\"protected\"}"
        else
          targets+=("$pid")
        fi
      done ;;
    *)
      if [ -z "${P_COMM[$target]+x}" ]; then
        printf '{"stopped":[],"killed":[],"remaining":[],"refused":[{"pid":%s,"reason":"not_found"}]}\n' "$target"
        return 0
      fi
      if [ "${P_PROTECTED[$target]}" = true ]; then
        printf '{"stopped":[],"killed":[],"remaining":[],"refused":[{"pid":%s,"reason":"protected"}]}\n' "$target"
        return 0
      fi
      targets=("$target") ;;
  esac
  for pid in "${targets[@]}"; do kill -TERM "$pid" 2>/dev/null || true; done
  waited=0
  while [ "$waited" -lt "$((STOP_WAIT_SECONDS * 10))" ]; do
    local alive=0
    for pid in "${targets[@]}"; do kill -0 "$pid" 2>/dev/null && alive=1; done
    [ "$alive" = 1 ] || break
    sleep 0.1
    waited=$((waited + 1))
  done
  for pid in "${targets[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -KILL "$pid" 2>/dev/null || true
      [ "$first_killed" = 1 ] || killed+=','
      first_killed=0
      killed+="$pid"
    else
      [ "$first_stopped" = 1 ] || stopped+=','
      first_stopped=0
      stopped+="$pid"
    fi
  done
  sleep 0.2
  for pid in "${targets[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      [ "$first_remaining" = 1 ] || remaining+=','
      first_remaining=0
      remaining+="{\"pid\":$pid,\"name\":$(json_str "$(proc_name "$pid")")}"
    fi
  done
  printf '{"stopped":[%s],"killed":[%s],"remaining":[%s],"refused":[%s]}\n' "$stopped" "$killed" "$remaining" "$refused"
}

# ------------------------------------------------------------------ verbs --

case "${1:-}" in
  storage-scan) storage_scan ;;
  storage-status) storage_status ;;
  storage-summary) storage_summary ;;
  storage-cancel) storage_cancel ;;
  storage-clean) shift; storage_clean "$@" ;;
  procs-scan) procs_scan ;;
  procs-stop) shift; procs_stop "$@" ;;
  *) echo "usage: $0 {storage-scan|storage-status|storage-summary|storage-cancel|storage-clean <category>|procs-scan|procs-stop <pid|pid,pid,...|group>}" >&2; exit 64 ;;
esac
''';
