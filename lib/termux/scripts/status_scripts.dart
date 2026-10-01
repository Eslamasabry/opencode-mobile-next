import 'script_support.dart';

String termuxStorageScript() =>
    '''
set -euo pipefail
LC_ALL=C timeout -k 1s 5s df -Pk '$termuxHomeDirectory' | awk 'NR == 2 { printf "total_kib=%s\\navailable_kib=%s\\n", \$2, \$4 }'
''';

String termuxInstallationScript() => r'''
set -eu
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
runtime=$(cat "$HOME/.oc/runtime" 2>/dev/null || true)
recorded_runtime="$runtime"
case "$runtime" in
  ''|opencode1) runtime=opencode1; command=opencode ;;
  opencode2) command=opencode2 ;;
  *) echo 'unsupported-managed-runtime' >&2; exit 64 ;;
esac
if [ ! -d "$PREFIX/var/lib/proot-distro/containers/opencode-ubuntu/rootfs" ] &&
   [ ! -d "$PREFIX/var/lib/proot-distro/installed-rootfs/opencode-ubuntu" ]; then
  printf 'ubuntu=absent\nversion=\n'
  [ -z "$recorded_runtime" ] || printf 'runtime=%s\n' "$runtime"
  exit 0
fi
# A missing/broken proot command or a hung version probe is an error, not an
# absent installation. Bound the whole login, including container startup.
timeout -k 2s 20s proot-distro login opencode-ubuntu -- bash -c '
set -eu
runtime="$1"
binary="$2"
recorded_runtime="$3"
if ! command -v "$binary" >/dev/null 2>&1; then
  printf "ubuntu=installed\nversion=\n"
  [ -z "$recorded_runtime" ] || printf "runtime=%s\n" "$runtime"
  exit 0
fi
version=$("$binary" --version)
if [ "$runtime" = opencode2 ]; then version=${version#opencode2 v}; version=${version#opencode v}; fi
printf "ubuntu=installed\nversion=%s\n" "$version"
[ -z "$recorded_runtime" ] || printf "runtime=%s\n" "$runtime"
' -- "$runtime" "$command" "$recorded_runtime"
''';

String termuxStatusScript() =>
    '''
if [ -x "$termuxManagerPath" ]; then
  exec "$termuxManagerPath" status
fi
if [ -f "$termuxHomeDirectory/.oc/state" ]; then
  port=4096
  while IFS='=' read -r name value; do
    [ "\$name" = port ] && port="\$value"
  done < "$termuxHomeDirectory/.oc/state"
  printf 'phase=failed\nmessage=Setup manager is missing after launch\nport=%s\nrunner=\nversion=\npid=\n' "\$port"
  exit 0
fi
printf 'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\nversion=\npid=\n'
''';

String termuxSetupSnapshotScript() =>
    '''
OC_DIR="$termuxHomeDirectory/.oc"
if [ -x "$termuxManagerPath" ]; then
  if manager_output=\$("$termuxManagerPath" status 2>&1); then
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

String termuxDiagnosticsScript() =>
    '''
if [ -x "$termuxManagerPath" ]; then
  exec "$termuxManagerPath" diagnostics
fi
OC_DIR="$termuxHomeDirectory/.oc"
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
