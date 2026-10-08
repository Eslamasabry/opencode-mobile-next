import '../../domain/agent_catalog.dart';
import '../setup/component_updates.dart';
import 'paseo_scripts.dart';

/// Installs catalog pins as Linux user oc. Does not sign in, launch agents, or
/// qualify phone compatibility. Native setup must use agentUser=true.
abstract final class AgentPhoneScripts {
  /// Runs as uid 1000 in the selected profile's environment. Raw agent output
  /// stays in this process and never enters setup logs or channel diagnostics.
  /// An unknown status protocol is an explicit error, never a guessed sign-out.
  static String authProbe(AgentDescriptor agent) {
    return _authScript(agent, signOut: false);
  }

  static bool supportsSignOut(AgentDescriptor agent) =>
      agent.id == 'claude' || agent.id == 'fx';

  /// Executes the agent's logout and then its status command. A successful
  /// process exit never proves the credentials have gone away.
  static String signOut(AgentDescriptor agent) =>
      _authScript(agent, signOut: true);

  static String _authScript(AgentDescriptor agent, {required bool signOut}) {
    final recipe = _recipe(agent);
    return '''
set -eu
command -v python3 >/dev/null 2>&1 || {
  printf '%s\\n' '{"state":"error","error":"hostUnavailable"}'; exit 0
}
python3 - ${_quote(agent.id)} ${_quote(recipe.executable)} ${signOut ? 'logout' : 'probe'} <<'OC_AUTH_PROBE'
$_authProbe
OC_AUTH_PROBE
''';
  }

  static const _authProbe = r'''
import json, os, re, selectors, signal, subprocess, sys, time
agent, executable, action = sys.argv[1:]
def emit(state, account=None, error=None):
    value = {'state': state}
    if account is not None: value['accountDisplayName'] = account
    if error is not None: value['error'] = error
    print(json.dumps(value, separators=(',', ':')))
def fail(error):
    emit('error', error=error)
    sys.exit(0)
def account(value):
    # Only the CLI's dedicated account field, never tokens/config/free-form errors.
    if value is None: return None
    if not isinstance(value, str) or not value.strip() or len(value) > 160 or any(ord(c) < 32 or ord(c) == 127 for c in value):
        return None
    return value
home = os.environ.get('HOME', '')
if os.getuid() != 1000 or not home.startswith('/home/oc/.oc-profiles/') or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', home[len('/home/oc/.oc-profiles/'):]):
    fail('invalidContext')
if agent not in ('claude', 'fx'):
    fail('probeUnsupported')
binary = '/home/oc/.local/bin/' + executable
if not os.access(binary, os.X_OK): fail('notInstalled')
def run(args, parse=True):
    # Keep even a misbehaving CLI bounded; kill only this owned process group.
    process = subprocess.Popen([binary] + args, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, start_new_session=True)
    output = bytearray()
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + 8
    try:
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0: raise TimeoutError()
            for key, _ in selector.select(remaining):
                block = os.read(key.fileobj.fileno(), 4096)
                if not block:
                    selector.unregister(key.fileobj)
                else:
                    output.extend(block)
                    if len(output) > 65536: raise ValueError()
        return process.wait(timeout=max(0.01, deadline-time.monotonic())), json.loads(output) if parse else None
    finally:
        selector.close()
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        process.wait()
try:
    if action == 'logout':
        if agent == 'claude':
            if os.environ.get('CLAUDE_CONFIG_DIR') != home + '/claude': fail('invalidContext')
            logout_args = ['auth', 'logout']
        elif agent == 'fx': logout_args = ['logout']
        else: fail('probeUnsupported')
        code, _ = run(logout_args, parse=False)
        if code != 0: fail('signOutFailed')
    if agent == 'claude':
        if os.environ.get('CLAUDE_CONFIG_DIR') != home + '/claude': fail('invalidContext')
        code, data = run(['auth', 'status', '--json'])
        if code not in (0, 1) or not isinstance(data, dict) or type(data.get('loggedIn')) is not bool or not isinstance(data.get('authMethod'), str) or not isinstance(data.get('apiProvider'), str):
            fail('invalidResponse')
        if data['loggedIn']:
            if code != 0: fail('invalidResponse')
            if action == 'logout': fail('signOutFailed')
            emit('signedIn', account(data.get('email')))
        else: emit('signedOut')
    elif agent == 'fx':
        code, data = run(['status', '--json'])
        if code != 0 or not isinstance(data, dict) or data.get('kind') != 'status' or not isinstance(data.get('auth'), str):
            fail('invalidResponse')
        # status --json reports the selected credential source, not a provider's
        # model-list failure. Unknown future sources require a new contract.
        if 'auth_expired' in data and type(data['auth_expired']) is not bool:
            fail('invalidResponse')
        if data.get('auth_expired') is True: fail('signInExpired')
        if data['auth'] == 'missing': emit('signedOut')
        elif data['auth'] in ('fx login', 'AI_GATEWAY_API_KEY', 'VERCEL_OIDC_TOKEN', 'stored API key (profile file)', 'Codex subscription', 'Grok subscription'):
            if action == 'logout': fail('signOutFailed')
            emit('signedIn')
        else: fail('invalidResponse')
except (subprocess.TimeoutExpired, TimeoutError): fail('timedOut')
except (ValueError, TypeError, KeyError): fail('invalidResponse')
except OSError: fail('hostUnavailable')
''';

  /// The only root step. Refuses unrelated UID/GID collisions and symlinked
  /// homes instead of changing existing accounts or copying project data.
  static const bootstrapUser = r'''
set -eu
[ "$(id -u)" = 0 ] || exit 1
if getent group oc >/dev/null; then
  [ "$(getent group oc | cut -d: -f3)" = 1000 ] || exit 1
else
  ! getent group 1000 >/dev/null || exit 1
  groupadd --gid 1000 oc
fi
if id oc >/dev/null 2>&1; then
  [ "$(id -u oc)" = 1000 ] && [ "$(id -g oc)" = 1000 ] || exit 1
  [ "$(getent passwd oc | cut -d: -f6)" = /home/oc ] || exit 1
else
  ! getent passwd 1000 >/dev/null || exit 1
  useradd --uid 1000 --gid 1000 --home-dir /home/oc --create-home --shell /bin/bash oc
fi
[ ! -L /home/oc ] && [ -d /home/oc ] || exit 1
# Root PRoot maps Android-owned files to UID 0; chown is virtual here.
# The native agent launch maps them to UID 1000 and the phone check proves RW.
for oc_path in /home/oc/.local /home/oc/.local/bin /home/oc/.local/share; do
  [ ! -L "$oc_path" ] || exit 1
done
install -d -o 1000 -g 1000 /home/oc/.local /home/oc/.local/bin /home/oc/.local/share
''';

  static AgentInstallRecipe _recipe(AgentDescriptor agent) {
    final recipe = agent.recipe;
    if (recipe == null ||
        agent.availability == AgentAvailability.hidden ||
        !RegExp(r'^[0-9][a-zA-Z0-9.+_-]*$').hasMatch(recipe.version) ||
        recipe.artifacts.values.any(
          (artifact) =>
              artifact.archiveMember != null &&
              !RegExp(
                r'^(?:\./)?[a-zA-Z0-9_./+-]+$',
              ).hasMatch(artifact.archiveMember!),
        )) {
      throw ArgumentError('This agent has no usable pinned installation.');
    }
    return recipe;
  }

  static String _quote(String text) => "'${text.replaceAll("'", "'\\''")}'";

  static String _selection(AgentDescriptor agent, AgentInstallRecipe recipe) {
    final branches = <String>[];
    for (final entry in recipe.artifacts.entries) {
      final patterns = entry.key == AgentArchitecture.arm64
          ? 'aarch64|arm64'
          : 'x86_64|amd64';
      final artifact = entry.value;
      branches.add(
        '$patterns) oc_arch=${entry.key.name}; oc_url=${_quote(artifact.url.toString())}; oc_sha=${_quote(artifact.sha256)}; oc_format=${artifact.format.name}; oc_member=${_quote(artifact.archiveMember ?? recipe.executable)} ;;',
      );
    }
    return '''
set -eu
[ "\$(id -u)" = 1000 ] && [ "\$HOME" = /home/oc ] || {
  echo '[oc] Agent setup needs its private Linux user' >&2; exit 1
}
umask 077
export PATH=/home/oc/.local/bin:/usr/bin:/bin
case "\$(uname -m)" in
${branches.join('\n')}
*) echo '[oc] This agent is unavailable for this phone' >&2; exit 1 ;;
esac
oc_final=/home/oc/.local/share/oc-agents/${agent.id}/${recipe.version}
oc_bin=/home/oc/.local/bin/${recipe.executable}
oc_pin=${_quote('${recipe.version}|')}"\$oc_arch|\$oc_sha"
''';
  }

  static const _probe = r'''
oc_probe_version() {
  oc_tree=$1
  [ -d "$oc_tree" ] && [ ! -L "$oc_tree" ] &&
    [ -z "$(find "$oc_tree" -type l -print -quit)" ] || return 1
  oc_probe=$(mktemp -d /home/oc/.local/share/oc-agents/.probe.XXXXXX)
  if [ "$oc_format" = npmTarGz ]; then
    oc_output=$(cd "$oc_probe" && timeout 30s env -i HOME="$oc_probe" XDG_CONFIG_HOME="$oc_probe" PATH="$PATH" \
      /home/oc/.local/node/bin/node "$oc_tree/payload/$oc_member" --version 2>/dev/null) || oc_output=
  else
    oc_output=$(cd "$oc_probe" && timeout 30s env -i HOME="$oc_probe" XDG_CONFIG_HOME="$oc_probe" PATH="$PATH" \
      "$oc_tree/payload/$oc_member" --version 2>/dev/null) || oc_output=
  fi
  rm -rf "$oc_probe"
  printf '%s' "$oc_output" | grep -Eq "$oc_version_pattern"
}
''';

  static String _versionPattern(String version) =>
      _quote('(^|[^0-9A-Za-z.])${RegExp.escape(version)}([^0-9A-Za-z.]|\$)');

  static const _claudeUpdateProbe = r'''
oc_claude_probe_active() {
  oc_probe=$(mktemp -d /home/oc/.local/share/oc-agents/.probe.XXXXXX)
  oc_output=$(cd "$oc_probe" && timeout 30s env -i HOME="$oc_probe" XDG_CONFIG_HOME="$oc_probe" PATH="$PATH" \
    "$oc_bin" --version 2>/dev/null) || oc_output=
  rm -rf "$oc_probe"
  printf '%s' "$oc_output" | grep -Eq "$oc_version_pattern"
}
oc_claude_update_failed() {
  oc_restore_failed=0
  oc_update_recover "$oc_final" || oc_restore_failed=1
  oc_update_recover "$oc_bin" || oc_restore_failed=1
  if [ "$oc_restore_failed" != 0 ]; then
    echo '[oc] A component update could not be restored. Run setup again.' >&2
  else
    echo '[oc] Claude could not finish updating. Run setup again.' >&2
  fi
  exit 1
}
''';

  static String check(AgentDescriptor agent) {
    final recipe = _recipe(agent);
    final recovery = agent.id == 'claude' && recipe.executable == 'claude'
        ? '''$componentUpdatePrelude
# Checks and installers hold the same lock across recovery and probes.
# BB owns stale-lock recovery; a successful mkdir proves only our admission.
oc_check_lock=/home/oc/.local/share/oc-agents/.lock-claude
mkdir "\$oc_check_lock" 2>/dev/null || {
  echo '[oc] Another Claude install may still be running. Wait and try again.' >&2
  exit 1
}
trap 'rmdir "\$oc_check_lock" >/dev/null 2>&1 || true' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_update_recover "\$oc_final"
oc_update_recover "\$oc_bin"
'''
        : '';
    return '''${_selection(agent, recipe)}
$recovery
oc_version_pattern=${_versionPattern(recipe.version)}
$_probe
[ -f "\$oc_final/.oc-pin" ] && [ ! -L "\$oc_final/.oc-pin" ]
[ "\$(cat "\$oc_final/.oc-pin")" = "\$oc_pin" ]
[ "\$(readlink "\$oc_bin")" = "\$oc_final/launch" ]
oc_probe_version "\$oc_final"
printf '%s\\n' ${_quote(recipe.version)}
''';
  }

  static String install(AgentDescriptor agent) {
    final recipe = _recipe(agent);
    final version = _quote(recipe.version);
    final claudeUpdate = agent.id == 'claude' && recipe.executable == 'claude';
    final updatePrelude = claudeUpdate
        ? '$componentUpdatePrelude\n$_claudeUpdateProbe'
        : '';
    final recoverAfterLock = claudeUpdate
        ? r'''
oc_update_recover "$oc_final"
oc_update_recover "$oc_bin"
'''
        : '';
    final fastLinkUpdate = claudeUpdate
        ? r'''
  ln -s "$oc_final/launch" "$oc_link" || oc_claude_update_failed
  oc_update_activate "$oc_bin" "$oc_link" || oc_claude_update_failed
  oc_claude_probe_active || oc_claude_update_failed
  oc_update_commit "$oc_bin" || oc_claude_update_failed
'''
        : r'''
  ln -s "$oc_final/launch" "$oc_link"
  mv -Tf "$oc_link" "$oc_bin"
''';
    final activation = claudeUpdate
        ? r'''
ln -s "$oc_final/launch" "$oc_link" || oc_claude_update_failed
oc_update_activate "$oc_final" "$oc_stage" || oc_claude_update_failed
oc_update_activate "$oc_bin" "$oc_link" || oc_claude_update_failed
oc_claude_probe_active || oc_claude_update_failed
oc_update_commit "$oc_final" || oc_claude_update_failed
oc_update_commit "$oc_bin" || oc_claude_update_failed
'''
        : r'''
if [ -e "$oc_final" ]; then mv "$oc_final" "$oc_old"; fi
if ! mv "$oc_stage" "$oc_final"; then
  [ ! -d "$oc_old" ] || mv "$oc_old" "$oc_final"
  exit 1
fi
if ! ln -s "$oc_final/launch" "$oc_link" || ! mv -Tf "$oc_link" "$oc_bin"; then
  rm -rf "$oc_final"
  [ ! -d "$oc_old" ] || mv "$oc_old" "$oc_final"
  exit 1
fi
rm -rf "$oc_old"
''';
    return '''${_selection(agent, recipe)}
oc_version_pattern=${_versionPattern(recipe.version)}
$_probe
$updatePrelude
command -v python3 >/dev/null && command -v timeout >/dev/null || {
  echo '[oc] Agent setup needs the Linux tools first' >&2; exit 1
}
if [ "\$oc_format" = npmTarGz ]; then
  [ "\$(/home/oc/.local/node/bin/node --version 2>/dev/null)" = '${PaseoPhoneScripts.nodeVersion}' ] || {
    echo '[oc] Agent setup needs its pinned Node.js first' >&2; exit 1
  }
fi
mkdir -p /home/oc/.local/bin /home/oc/.local/share/oc-agents/${agent.id}
oc_lock=/home/oc/.local/share/oc-agents/.lock-${recipe.executable}
mkdir "\$oc_lock" 2>/dev/null || { echo '[oc] Another agent install is in progress' >&2; exit 1; }
oc_stage="\$oc_final.new"
oc_old="\$oc_final.old.\$\$"
oc_link="\$oc_bin.new.\$\$"
oc_probe=
trap '[ -z "\$oc_probe" ] || rm -rf "\$oc_probe"; rm -rf "\$oc_stage"; rm -f "\$oc_link"; rmdir "\$oc_lock"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
$recoverAfterLock
[ ! -L "\$oc_final" ] && [ ! -L "\$oc_stage" ] || exit 1
if [ -f "\$oc_final/.oc-pin" ] && [ ! -L "\$oc_final/.oc-pin" ] &&
   [ "\$(cat "\$oc_final/.oc-pin")" = "\$oc_pin" ] && oc_probe_version "\$oc_final"; then
$fastLinkUpdate
  oc_version $version
  exit 0
fi
[ ! -L "\$oc_final" ] && [ ! -L "\$oc_stage" ] || exit 1
rm -rf "\$oc_stage"
mkdir -p "\$oc_stage/payload"
oc_stage 'Downloading agent'
oc_download "\$oc_url" "\$oc_stage/artifact" "\$oc_sha"
# Independently enforce the pin even if the shared downloader changes.
[ "\$(sha256sum "\$oc_stage/artifact" | cut -d ' ' -f1)" = "\$oc_sha" ] || {
  echo '[oc] The agent download did not match its checksum' >&2; exit 1
}
if ! python3 - "\$oc_stage/artifact" "\$oc_stage/payload" "\$oc_format" "\$oc_member" <<'OC_ARCHIVE'
$_extract
OC_ARCHIVE
then
  echo '[oc] The agent archive could not be safely prepared' >&2; exit 1
fi
if [ "\$oc_format" != npmTarGz ]; then chmod 700 "\$oc_stage/payload/\$oc_member"; fi
if ! oc_probe_version "\$oc_stage"; then
  echo '[oc] The agent did not pass its version check' >&2; exit 1
fi
rm -f "\$oc_stage/artifact"
printf '%s' "\$oc_pin" > "\$oc_stage/.oc-pin"
if [ "\$oc_format" = npmTarGz ]; then
  printf '#!/bin/sh\\nexec /home/oc/.local/node/bin/node "%s/payload/%s" "\$@"\\n' "\$oc_final" "\$oc_member" > "\$oc_stage/launch"
else
  printf '#!/bin/sh\\nexec "%s/payload/%s" "\$@"\\n' "\$oc_final" "\$oc_member" > "\$oc_stage/launch"
fi
chmod 700 "\$oc_stage/launch"
$activation
oc_version $version
''';
  }

  // All names/types are validated before extraction; no archive links or
  // special files are accepted. Preserve sibling files for bundled npm CLIs.
  static const _extract = r'''
import os, pathlib, shutil, stat, sys, tarfile, zipfile
archive, root, kind, entry = sys.argv[1:]
def safe(name):
    if not name or len(name) > 4096 or '\\' in name or any(ord(c) < 32 for c in name):
        raise ValueError()
    if name.startswith('/') or '..' in name.split('/'):
        raise ValueError()
    value = pathlib.PurePosixPath(name)
    if str(value) == '.':
        return None
    return value
try:
    if kind == 'executable':
        target = pathlib.Path(root) / safe(entry)
        with open(archive, 'rb') as source, open(target, 'xb') as dest:
            shutil.copyfileobj(source, dest)
    else:
        if kind == 'zip':
            handle = zipfile.ZipFile(archive)
            rows = [(m.filename, m.is_dir(), m.file_size, (m.external_attr >> 16) & 0xffff, m) for m in handle.infolist()]
            opener = handle.open
        else:
            handle = tarfile.open(archive, mode='r:gz' if kind in ('tarGz', 'npmTarGz') else 'r:bz2')
            rows = [(m.name, m.isdir(), m.size, m.mode, m) for m in handle.getmembers()]
            if any(not (m.isdir() or m.isfile()) for m in handle.getmembers()):
                raise ValueError()
            opener = handle.extractfile
        if len(rows) > 20000 or sum(row[2] for row in rows) > 2147483648:
            raise ValueError()
        names = set()
        for name, directory, size, mode, member in rows:
            path = safe(name)
            if path is None:
                if not directory: raise ValueError()
                continue
            if path in names or size > 1073741824:
                raise ValueError()
            names.add(path)
            if kind == 'zip' and stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR):
                raise ValueError()
        for name, directory, size, mode, member in rows:
            path = safe(name)
            if path is None: continue
            target = pathlib.Path(root) / path
            if directory:
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with opener(member) as source, open(target, 'xb') as dest:
                    shutil.copyfileobj(source, dest)
                os.chmod(target, 0o700 if mode & 0o111 else 0o600)
        handle.close()
    candidate = pathlib.Path(root) / safe(entry)
    if not candidate.is_file() or candidate.is_symlink(): raise ValueError()
except Exception:
    sys.exit(1)
''';
}
