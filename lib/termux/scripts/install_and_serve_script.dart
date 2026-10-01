import '../bridge_models.dart';
import 'manager_script.dart';
import 'script_support.dart';

String termuxInstallAndServeScript({
  int port = 4096,
  required String password,
  String? version,
  TermuxRuntime runtime = TermuxRuntime.openCode1,
}) {
  final selectedVersion = version ?? runtime.pinnedVersion;
  if (port < 1024 || port > 65535) {
    throw ArgumentError.value(port, 'port', 'Must be between 1024 and 65535.');
  }
  if (!RegExp(r'^[A-Za-z0-9._+-]+$').hasMatch(selectedVersion)) {
    throw ArgumentError.value(version, 'version', 'Invalid package version.');
  }
  if (password.isEmpty) {
    throw ArgumentError.value(password, 'password', 'Must not be empty.');
  }

  final quotedPassword = shellQuote(password);
  return '''
set -eu
OC_DIR="$termuxHomeDirectory/.oc"
MANAGER="$termuxManagerPath"
LOCK="\$OC_DIR/setup.lock"
mkdir -p "\$OC_DIR"
umask 077

if [ ! -e "\$MANAGER" ] && [ -d "\$OC_DIR/bin" ]; then
  touch "\$OC_DIR/legacy-install"
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

process_start() {
  stat_line=\$(cat "/proc/\$1/stat" 2>/dev/null) || return 1
  stat_line=\${stat_line##*) }
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

# Changing generations in an existing installation is a separate migration.
# First-run selection and same-runtime repair never rewrite that decision.
[ ! -f "\$OC_DIR/runtime-switch" ] || { echo 'managed-runtime-switch-pending' >&2; exit 75; }
old_runtime=\$(cat "\$OC_DIR/runtime" 2>/dev/null || true)
old_version=\$(sed -n 's/^version=//p' "\$OC_DIR/state" 2>/dev/null || true)
if { [ -n "\$old_runtime" ] || [ -n "\$old_version" ]; } &&
   [ "\${old_runtime:-opencode1}" != '${runtime.wireName}' ]; then
  echo 'managed-runtime-migration-required' >&2
  exit 64
fi
printf '%s' '${runtime.wireName}' > "\$OC_DIR/runtime.tmp.\$\$"
mv "\$OC_DIR/runtime.tmp.\$\$" "\$OC_DIR/runtime"
password_tmp="\$OC_DIR/server.password.tmp.\$\$"
printf '%s' $quotedPassword > "\$password_tmp"
chmod 600 "\$password_tmp"
mv "\$password_tmp" "\$OC_DIR/server.password"
started_at=\$(date +%s)
printf 'phase=queued\nmessage=Setup queued\nport=$port\nrunner=proot\nversion=\npid=\nstarted_at=%s\nruntime=${runtime.wireName}\n' "\$started_at" > "\$OC_DIR/state"
# From this point a stale dispatcher lock is safer than deleting a lock while
# the child is claiming it. Stop & retry handles stale ownership explicitly.
trap - EXIT
rm -f "\$OC_DIR/server-log.active"
"\$MANAGER" rotate-log install
set -m
nohup "\$MANAGER" setup '$port' '$selectedVersion' "\$\$" "\$self_start" '${runtime.wireName}' > >("\$MANAGER" write-log install) 2>&1 </dev/null &
manager_pid=\$!
manager_start=\$(process_start "\$manager_pid" || true)
[ -n "\$manager_start" ] || {
  wait "\$manager_pid" 2>/dev/null || true
  echo 'manager-exited-before-start' >&2
  exit 70
}
printf '%s %s\n' "\$manager_pid" "\$manager_start" > "\$OC_DIR/manager.pid"
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
