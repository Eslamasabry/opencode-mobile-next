import 'manager_script.dart';
import '../bridge_models.dart';
import 'script_support.dart';

String termuxRestartScript({
  int port = termuxManagedServerPort,
  required String operationID,
  String? recoveryToken,
  String? expectedOperationID,
  TermuxRuntime? switchTarget,
  String? switchPassword,
}) {
  if (port < 1024 || port > 65535) {
    throw ArgumentError.value(port, 'port', 'Must be between 1024 and 65535.');
  }
  if (!RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(operationID)) {
    throw ArgumentError.value(operationID, 'operationID', 'Invalid restart ID');
  }
  if (recoveryToken != null &&
      (!RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(recoveryToken) ||
          expectedOperationID == null ||
          !RegExp(r'^[a-zA-Z0-9_-]{0,64}$').hasMatch(expectedOperationID))) {
    throw ArgumentError('Invalid recovery identity');
  }
  if (switchTarget != null &&
      (switchPassword == null ||
          switchPassword.isEmpty ||
          recoveryToken != null)) {
    throw ArgumentError('A runtime switch needs its saved credential');
  }
  final recoveryArgs = recoveryToken == null
      ? ''
      : ' ${shellQuote(expectedOperationID!)} ${shellQuote(recoveryToken)}';
  final stagedPassword = switchTarget == null
      ? ''
      : 'printf \'%s\' ${shellQuote(switchPassword!)} > "\$OC_DIR/switch-password-$operationID"';
  final launchCommand = switchTarget == null
      ? '"\$MANAGER" restart \'$port\' \'$operationID\'$recoveryArgs &\n'
            'operation_pid=\$!\nwait "\$operation_pid"'
      : 'nohup "\$MANAGER" restart \'$port\' \'$operationID\' \'\' \'\' '
            '\'${switchTarget.wireName}\' >/dev/null 2>&1 </dev/null &\n'
            'operation_pid=\$!\nprintf "manager-started:%s\\n" "\$operation_pid"';
  final staleLockAction = switchTarget == null
      ? r'''echo 'managed-operation-lock-is-stale; use Stop then retry' >&2
  exit 75'''
      : r'''# Only an explicit runtime operation may reclaim a dead dispatcher.
  # A live manager with missing ownership stays unavailable; never stop it here.
  recorded_pid=''; recorded_start=''
  read -r recorded_pid recorded_start < "$OC_DIR/manager.pid" 2>/dev/null || true
  if [ -n "$recorded_pid" ] && [ -n "$recorded_start" ] &&
     [ "$(process_start "$recorded_pid" 2>/dev/null || true)" = "$recorded_start" ] &&
     kill -0 "$recorded_pid" 2>/dev/null; then
    echo 'managed-operation-owner-is-still-active' >&2; exit 75
  fi
  current_pid=''; current_start=''
  if [ -f "$LOCK" ]; then
    read -r current_pid current_start < "$LOCK" 2>/dev/null || true
  else
    read -r current_pid current_start < "$LOCK/owner" 2>/dev/null || true
  fi
  [ "$current_pid" = "$owner_pid" ] && [ "$current_start" = "$owner_start" ] || {
    echo 'managed-operation-owner-changed' >&2; exit 75;
  }
  if [ -f "$LOCK" ]; then rm -f "$LOCK";
  else rm -f "$LOCK/owner"; rmdir "$LOCK" || exit 75; fi''';
  return '''
set -eu
OC_DIR="$termuxHomeDirectory/.oc"
MANAGER="$termuxManagerPath"
LOCK="\$OC_DIR/setup.lock"
mkdir -p "\$OC_DIR"
umask 077

process_start() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line##*) }
  set -- \$stat_line
  printf '%s' "\${20:-}"
}

owner_pid=''
owner_start=''
if [ -f "\$LOCK" ]; then
  read -r owner_pid owner_start < "\$LOCK" 2>/dev/null || true
elif [ -f "\$LOCK/owner" ]; then
  read -r owner_pid owner_start < "\$LOCK/owner" 2>/dev/null || true
fi
live_start=\$(process_start "\$owner_pid" 2>/dev/null || true)
if [ -n "\$owner_pid" ] && [ -n "\$owner_start" ] &&
   [ "\$owner_start" = "\$live_start" ] && kill -0 "\$owner_pid" 2>/dev/null; then
  echo 'another-managed-operation-is-running' >&2
  exit 75
fi
if [ -e "\$LOCK" ]; then
  $staleLockAction
fi

manager_tmp="\$MANAGER.tmp.\$\$"
cat > "\$manager_tmp" <<'OC_MANAGER_EOF'
$termuxManagerScript
OC_MANAGER_EOF
chmod 700 "\$manager_tmp"
mv "\$manager_tmp" "\$MANAGER"
[ -x "\$MANAGER" ] || {
  echo 'manager-install-failed' >&2
  exit 74
}
$stagedPassword
set -m
$launchCommand
''';
}

String termuxStopScript({int port = 4096}) =>
    '''
rm -f "$termuxHomeDirectory/.oc/recovery-permit"
if [ -x "$termuxManagerPath" ]; then
  exec "$termuxManagerPath" stop '$port'
fi
OC_DIR="$termuxHomeDirectory/.oc"
process_start() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line##*) }
  set -- \$stat_line
  printf '%s' "\${20:-}"
}
read_lock() {
  if [ -f "\$OC_DIR/setup.lock" ]; then
    cat "\$OC_DIR/setup.lock"
  else
    cat "\$OC_DIR/setup.lock/owner" 2>/dev/null
  fi
}
setup_process() {
  local -a args=()
  mapfile -d '' -t args < "/proc/\$1/cmdline" 2>/dev/null || return 1
  [ "\${args[1]:-}" = "\$OC_DIR/manager.sh" ] || return 1
  [ "\${args[2]:-}" = setup ] || [ "\${args[2]:-}" = restart ]
}
process_group() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line##*) }
  set -- \$stat_line
  printf '%s' "\${3:-}"
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
  manager_start=''
  read -r pid manager_start < "\$OC_DIR/manager.pid" 2>/dev/null || true
fi
case "\$pid" in
  ''|*[!0-9]*) ;;
  *)
    if setup_process "\$pid"; then
      if [ "\$lock_owned" != 1 ]; then
        echo 'bootstrap-owner-is-unverified' >&2
        exit 75
      fi
      kill -STOP "\$pid" 2>/dev/null || true
      stopped_start=\$(process_start "\$pid" 2>/dev/null || true)
      if [ "\$stopped_start" != "\$lock_start" ] || ! setup_process "\$pid"; then
        [ -z "\$stopped_start" ] || kill -CONT "\$pid" 2>/dev/null || true
        echo 'bootstrap-owner-changed-before-stop' >&2
        exit 75
      fi
      [ "\$(process_group "\$pid" 2>/dev/null || true)" = "\$pid" ] || {
        kill -CONT "\$pid" 2>/dev/null || true
        echo 'bootstrap-owner-is-not-isolated' >&2
        exit 75
      }
      kill -STOP -- "-\$pid" 2>/dev/null || true
      if [ "\$(process_start "\$pid" 2>/dev/null || true)" != "\$lock_start" ] ||
         ! setup_process "\$pid"; then
        kill -CONT -- "-\$pid" 2>/dev/null || true
        echo 'bootstrap-owner-changed-before-stop' >&2
        exit 75
      fi
      kill -KILL -- "-\$pid" 2>/dev/null || true
    elif [ "\$lock_owned" = 1 ] && kill -0 "\$pid" 2>/dev/null; then
      echo 'bootstrap-owner-is-still-active' >&2
      exit 75
    fi
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
printf 'phase=stopped\nmessage=Bootstrap state cleared\nport=$port\nrunner=\nversion=\npid=\n' > "$termuxHomeDirectory/.oc/state"
echo 'bootstrap-state-cleared'
''';
