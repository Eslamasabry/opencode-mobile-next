import 'dart:convert';

import '../../domain/agent_catalog.dart';
import '../../domain/genui/gen_ui_status.dart';
import 'gen_ui_server.dart';
import 'paseo_scripts.dart';

/// App-authored script; no agent-supplied strings participate in construction.
/// A root-owned Node is required for OpenCode. The agent-writable private
/// Node is only ever used by the uid-1000 Claude runner.
String genUiInstallScript({
  required String profileId,
  required GenUiAgent agent,
  required bool enabled,
}) => _genUiManagedScript(profileId: profileId, agent: agent, enabled: enabled);

String genUiVerificationScript({
  required String profileId,
  required GenUiAgent agent,
}) => _genUiManagedScript(
  profileId: profileId,
  agent: agent,
  enabled: true,
  verify: true,
);

String _genUiManagedScript({
  required String profileId,
  required GenUiAgent agent,
  required bool enabled,
  bool verify = false,
}) {
  if (!RegExp(r'^[a-zA-Z0-9_-]{1,96}$').hasMatch(profileId)) {
    throw ArgumentError('Invalid managed profile identity');
  }
  final claude = agent == GenUiAgent.claude;
  final claudeVersion = AgentCatalog.builtIn.byId('claude')!.recipe!.version;
  final home = '/home/oc/.oc-profiles/$profileId';
  final directory = claude
      ? '$home/.oc-genui'
      : '/root/.oc-genui/${agent.name}';
  final config = switch (agent) {
    GenUiAgent.claude => '$home/claude/.claude.json',
    GenUiAgent.openCode1 => '/root/.config/opencode/opencode.json',
    GenUiAgent.openCode2 => '/root/.oc-opencode2/config/opencode/opencode.json',
  };
  final data = base64.encode(
    utf8.encode(
      jsonEncode({
        'profile': profileId,
        'kind': agent.name,
        'enable': enabled,
        'verify': verify,
        'claudeVersion': claudeVersion,
        'nodeVersion': PaseoPhoneScripts.nodeVersion,
        // Transport qualification is version-specific, not inherited by a
        // future catalog update. Refresh these only with new runtime evidence.
        'qualifiedPins':
            claudeVersion == '2.1.283' &&
            PaseoPhoneScripts.version == '0.9.2' &&
            PaseoPhoneScripts.nodeVersion == 'v24.21.0',
        'home': home,
        'directory': directory,
        'config': config,
        'cli':
            '/home/oc/.local/share/oc-agents/claude/$claudeVersion/payload/claude',
        'node': claude ? '/home/oc/.local/node/bin/node' : '/usr/bin/node',
        'helper': genUiServerScript(enabledMarkerPath: '$directory/enabled'),
      }),
    ),
  );
  return '''
set -eu
umask 077
# Python is a system executable, never the agent-owned Node runtime.
[ -x /usr/bin/python3 ] || exit 20
/usr/bin/python3 - '$data' <<'OC_GENUI_SETUP' 2>/dev/null
$_installer
OC_GENUI_SETUP
''';
}

const _installer = r'''
import os, sys, json, re, stat, base64, pathlib, tempfile, hashlib, fcntl, subprocess
class SetupError(Exception):
    def __init__(self, code): self.code = code

def fail(code): raise SetupError(code)
def safe(path, directory=False, create=False):
    path = pathlib.Path(path)
    for part in [*reversed(path.parents), path]:
        try: info = os.lstat(part)
        except FileNotFoundError:
            if create:
                os.mkdir(part, 0o700)
                info = os.lstat(part)
            else:
                return False
        if stat.S_ISLNK(info.st_mode): fail(22)
        if part != path or directory:
            if not stat.S_ISDIR(info.st_mode): fail(22)
        elif not stat.S_ISREG(info.st_mode): fail(22)
    return True

def read(path, maximum=1048576):
    if not safe(path): return None
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as stream:
        value = stream.read(maximum + 1)
    if len(value) > maximum: fail(21)
    return value

def read_json(path):
    raw = read(path)
    if raw is None: return {}
    try: value = json.loads(raw)
    except Exception: fail(21)
    if not isinstance(value, dict): fail(21)
    return value

def read_jsonc(path):
    # Parse only for collision checks. Never reserialize the user's JSONC.
    raw = read(path)
    if raw is None or raw == b'': return {}
    try:
        text = raw.decode('utf-8')
        quoted = r'"(?:[^"\\]|\\.)*"'
        text = re.sub(quoted + r'|//[^\r\n]*|/\*[\s\S]*?\*/',
          lambda m: m[0] if m[0].startswith('"') else ' ' * len(m[0]), text)
        # Mask strings so a literal ",}" or comment-like URL is unchanged.
        masked = re.sub(quoted, lambda m: 'x' * len(m[0]), text)
        chars = list(text)
        for match in re.finditer(r',(?=\s*[}\]])', masked):
            chars[match.start()] = ' '
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result: fail(21)
                result[key] = value
            return result
        value = json.loads(''.join(chars), object_pairs_hook=unique,
          parse_constant=lambda _: fail(21))
    except Exception: fail(21)
    if not isinstance(value, dict): fail(21)
    return value

def oc1_nested_collision(config):
    # OC1 also lowers the native mcp.servers envelope into the flat namespace.
    servers = slot(config, 'openCode1').get('servers')
    return isinstance(servers, dict) and 'oc-ui' in servers

def check_oc1_overlays(parent):
    # v1.18.32 merges config.json -> opencode.json -> opencode.jsonc deeply.
    # A disjoint managed entry in opencode.json survives the JSONC overlay.
    # The older extensionless TOML file migrates/merges last; do not adopt it.
    legacy = parent + '/config'
    root_owned(legacy)
    if safe(legacy): fail(21)
    for path in [parent + '/config.json', parent + '/opencode.jsonc']:
        root_owned(path)
        config = read_jsonc(path)
        if 'oc-ui' in slot(config, 'openCode1') or oc1_nested_collision(config):
            fail(21)

def atomic(path, value):
    safe(path)
    parent = str(pathlib.Path(path).parent)
    safe(parent, directory=True)
    fd, temporary = tempfile.mkstemp(prefix='.oc-ui-', dir=parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def save_json(path, value):
    atomic(path, (json.dumps(value, separators=(',', ':')) + '\n').encode())

def remove(path):
    if safe(path): os.unlink(path)

def slot(config, kind, create=False):
    key = 'mcpServers' if kind == 'claude' else 'mcp'
    if key not in config:
        if not create: return {}
        config[key] = {}
    result = config[key]
    if not isinstance(result, dict): fail(21)
    if kind == 'openCode2':
        if 'servers' not in result:
            if not create: return {}
            result['servers'] = {}
        result = result['servers']
        if not isinstance(result, dict): fail(21)
    return result

# Only this display-only tool is permitted, never the whole MCP server.
SHOW_PERMISSION = 'mcp__oc-ui__show'

def show_permissions(settings, create=False):
    if 'permissions' not in settings:
        if not create: return []
        settings['permissions'] = {}
    permissions = settings['permissions']
    if not isinstance(permissions, dict): fail(21)
    if 'allow' not in permissions:
        if not create: return []
        permissions['allow'] = []
    allowed = permissions['allow']
    if not isinstance(allowed, list) or any(not isinstance(x, str) for x in allowed): fail(21)
    return allowed

def allow_show(path):
    # Re-read immediately before writing to preserve unrelated CLI/user edits.
    settings = read_json(path)
    allowed = show_permissions(settings, True)
    if SHOW_PERMISSION in allowed: return False
    allowed.append(SHOW_PERMISSION)
    save_json(path, settings)
    return True

def remove_show(path):
    settings = read_json(path)
    allowed = show_permissions(settings)
    if SHOW_PERMISSION not in allowed: return
    settings['permissions']['allow'] = [x for x in allowed if x != SHOW_PERMISSION]
    if not settings['permissions']['allow']: settings['permissions'].pop('allow')
    if not settings['permissions']: settings.pop('permissions')
    save_json(path, settings)

# Fixed app-owned candidate paths: the built-in setup installs the first;
# distro-based installations may already provide the second. Never search
# PATH or follow /usr/local/bin/node into agent-controlled locations.
ROOT_NODES = ('/opt/node/bin/node', '/usr/bin/node')

def root_owned(path):
    # Check existing ancestors before creating anything below them. Together
    # with safe() this refuses symlinks, foreign owners and writable ancestors.
    # Guest metadata remains a precondition, not a confinement qualification.
    path = pathlib.Path(path)
    for part in [*reversed(path.parents), path]:
        try: info = os.stat(part, follow_symlinks=False)
        except FileNotFoundError: return
        if stat.S_ISLNK(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o022: fail(22)

def root_executable(path):
    if not safe(path): fail(20)
    root_owned(path)
    if not os.access(path, os.X_OK): fail(20)

def root_node(manifest, helper):
    if manifest:
        entry = manifest.get('entry')
        command = entry.get('command') if isinstance(entry, dict) else None
        if (not isinstance(command, list) or len(command) != 2
          or command[0] not in ROOT_NODES or command[1] != helper): fail(21)
        # Keep the owned argv stable when candidates appear/disappear. Disable
        # must still remove this entry even if its runtime was uninstalled.
        return command[0]
    for candidate in ROOT_NODES:
        if not safe(candidate): continue
        root_executable(candidate)
        return candidate
    fail(20)

def cli(args, environment):
    if not safe(args[0]) or not os.access(args[0], os.X_OK): return False
    try:
        return subprocess.run(args, env=environment, stdin=subprocess.DEVNULL,
          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30).returncode == 0
    except Exception: return False

def verify_claude(d, helper, config_parent):
    if not d['qualifiedPins']: fail(24)
    if not safe(d['cli']) or not safe(d['node']): fail(20)
    env = dict(os.environ, HOME=d['home'], CLAUDE_CONFIG_DIR=config_parent,
      NO_COLOR='1', FORCE_COLOR='0', TERM='dumb')
    def output(args, data=None):
        try:
            result = subprocess.run(args, env=env, input=data, stdin=None if data is not None else subprocess.DEVNULL,
              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30)
        except Exception: fail(24)
        if result.returncode != 0 or len(result.stdout) > 65536: fail(24)
        return result.stdout
    version = output([d['cli'], '--version']).decode('utf-8').strip().split(' ')[0]
    if version != d['claudeVersion']: fail(24)
    if output([d['node'], '--version']).decode('utf-8').strip() != d['nodeVersion']: fail(24)
    # Only start the owned helper. `claude mcp list` would also launch
    # unrelated servers from user config. The pinned CLI's registration
    # semantics are qualified separately against a real fresh Claude session.
    requests = [
      {'jsonrpc':'2.0','id':1,'method':'initialize','params':{
        'protocolVersion':'2024-11-05','capabilities':{},
        'clientInfo':{'name':'oc-ui-check','version':'1'}}},
      {'jsonrpc':'2.0','method':'notifications/initialized'},
      {'jsonrpc':'2.0','id':2,'method':'tools/list','params':{}}]
    payload = ('\n'.join(json.dumps(x, separators=(',',':')) for x in requests) + '\n').encode()
    try:
        responses = [json.loads(x) for x in output([d['node'], helper], payload).splitlines()]
        if len(responses) != 2 or responses[0].get('id') != 1 or responses[1].get('id') != 2: fail(24)
        tools = responses[1]['result']['tools']
        if len(tools) != 1 or tools[0]['name'] != 'show' or not isinstance(tools[0]['inputSchema'], dict): fail(24)
    except SetupError: raise
    except Exception: fail(24)

def main():
    d = json.loads(base64.b64decode(sys.argv[1]))
    kind, owner = d['kind'], d['profile']
    claude = kind == 'claude'
    if os.getuid() != (1000 if claude else 0): fail(22)
    if d['verify'] and not claude: fail(24)
    directory, config_path = d['directory'], d['config']
    if not d['enable'] and not safe(directory, directory=True): sys.exit(11)
    if not claude: root_owned(directory)
    if not safe(directory, directory=True, create=not d['verify']): fail(20)
    lock_path = directory + '/lock'
    if not claude: root_owned(lock_path)
    safe(lock_path)
    lock = os.open(lock_path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError: fail(23)
    marker, helper = directory + '/enabled', directory + '/server.cjs'
    manifest_path = directory + '/owners.json'
    for path in [marker, helper, manifest_path]:
        if not claude: root_owned(path)
        safe(path)
    manifest = read_json(manifest_path)
    if not d['enable'] and not manifest: sys.exit(11)
    config_parent = str(pathlib.Path(config_path).parent)
    if not claude: root_owned(config_path)
    if not safe(config_parent, directory=True, create=not d['verify']): fail(20)
    if kind == 'openCode1':
        check_oc1_overlays(config_parent)
    elif not claude and os.path.lexists(config_path + 'c'):
        # OC2 precedence has not been qualified by the OC1 loader contract.
        fail(21)
    current = read_json(config_path)
    if kind == 'openCode1' and (oc1_nested_collision(current)
      or ('oc-ui' in slot(current, kind) and slot(current, kind)['oc-ui'] is None)):
        fail(21)
    settings_path = config_parent + '/settings.json'
    show_allowed = claude and d['enable'] and SHOW_PERMISSION in show_permissions(read_json(settings_path))
    owned = bool(manifest)
    if not claude: d['node'] = root_node(manifest, helper)
    expected = ({'type':'stdio','command':d['node'],'args':[helper],'env':{}}
      if claude else {'type':'local','command':[d['node'], helper],
      **({'enabled':True} if kind == 'openCode1' else {'disabled':False,'codemode':False})})
    existing = slot(current, kind).get('oc-ui')
    helper_bytes = d['helper'].encode()
    if owned:
        keys = set(manifest)
        if (keys not in ({'v','owners','entry','digest'},
          {'v','owners','entry','digest','showPermissionAdded'} if claude else set())
          or manifest['v'] != 1
          or ('showPermissionAdded' in manifest and not isinstance(manifest['showPermissionAdded'], bool))
          or not isinstance(manifest['owners'], list)
          or any(not isinstance(x, str) for x in manifest['owners'])
          or manifest['entry'] != expected): fail(21)
        if existing is not None and existing != manifest['entry']: fail(21)
    else:
        # A pre-manifest interruption in an older installer can leave only
        # our exact current helper. Recover that narrow case; never adopt an
        # unknown executable, preexisting registration, or enabled marker.
        orphan = read(helper)
        if (existing is not None or read(marker) is not None
          or (orphan is not None and orphan != helper_bytes)): fail(21)
    owners = set(manifest.get('owners', []))
    if d['verify']:
        if (not claude or not show_allowed or not owned or owner not in owners or existing != expected
          or read(marker) != b'enabled\n'
          or manifest['digest'] != hashlib.sha256(helper_bytes).hexdigest()
          or read(helper) != helper_bytes): fail(24)
        verify_claude(d, helper, config_parent)
        sys.exit(0)
    if not d['enable']:
        if not owned: sys.exit(11)
        owners.discard(owner)
        if owners:
            manifest['owners'] = sorted(owners)
            save_json(manifest_path, manifest)
            sys.exit(10)
        # Operational off is first, before CLI/config removal. Existing helper
        # processes then reject new calls even if the catalog remains loaded.
        remove(marker)
        if claude:
            if manifest.get('showPermissionAdded', False): remove_show(settings_path)
            env = dict(os.environ, HOME=d['home'], CLAUDE_CONFIG_DIR=config_parent)
            if existing is not None and not cli([d['cli'],
              'mcp','remove','--scope','user','oc-ui'], env): fail(24)
            if slot(read_json(config_path), kind).get('oc-ui') is not None: fail(24)
        else:
            slot(current, kind, True).pop('oc-ui', None)
            save_json(config_path, current)
        # Keep the inert script while already loaded clients may still refer
        # to it; profile deletion removes the private directory afterwards.
        manifest['owners'] = []
        if claude: manifest['showPermissionAdded'] = False
        save_json(manifest_path, manifest)
        sys.exit(10)
    if claude:
        if not safe(d['node']) or not os.access(d['node'], os.X_OK): fail(20)
        # Invoke the catalog's pinned payload, not a mutable CLI launcher.
        if not safe(d['cli']) or not os.access(d['cli'], os.X_OK): fail(20)
    else:
        root_executable(d['node'])
    new_manifest = {'v':1,'owners':sorted(owners | {owner}),
      'entry':expected,'digest':hashlib.sha256(helper_bytes).hexdigest()}
    if claude:
        # Persist ownership before adding the rule so interruption is repairable.
        # A rule already present before our first install remains user-owned.
        new_manifest['showPermissionAdded'] = manifest.get('showPermissionAdded', False) or not show_allowed
    old_manifest = read(manifest_path)
    old_helper = read(helper)
    old_marker = read(marker)
    if (owned and existing == expected and old_helper == helper_bytes
      and manifest == new_manifest and old_marker == b'enabled\n'
      and (not claude or show_allowed)):
        # Already installed. The caller still runs the independent readiness
        # verifier, without interrupting live helpers or rewriting files.
        sys.exit(0)
    rollback_marker = old_marker if (owned and old_helper is not None
      and hashlib.sha256(old_helper).hexdigest() == manifest['digest']
      and old_marker == b'enabled\n') else None
    permission_added = False
    try:
        remove(marker)
        # Establish ownership before replacing code, so every interrupted
        # update can be repaired even if its helper digest no longer matches.
        save_json(manifest_path, new_manifest)
        atomic(helper, helper_bytes)
        if claude:
            env = dict(os.environ, HOME=d['home'], CLAUDE_CONFIG_DIR=config_parent)
            if existing is None and not cli([d['cli'],
              'mcp','add','--scope','user','--transport','stdio','oc-ui','--',d['node'],helper], env): fail(24)
            if slot(read_json(config_path), kind).get('oc-ui') != expected: fail(24)
            permission_added = allow_show(settings_path)
        else:
            slot(current, kind, True)['oc-ui'] = expected
            save_json(config_path, current)
        # Protocol self-check has no agent payload and no credentials. A
        # persistent registration still does not establish runtime visibility.
        initialize = b'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"oc-ui-check","version":"1"}}}\n'
        try:
            result = subprocess.run([d['node'], helper], input=initialize,
              stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5,
              env={'HOME':d['home'] if claude else '/root','PATH':'/usr/bin:/bin'})
            rows = result.stdout.splitlines()
            response = json.loads(rows[0]) if len(rows) == 1 and len(rows[0]) <= 65536 else {}
            if result.returncode != 0 or response.get('id') != 1 or 'result' not in response: fail(24)
        except SetupError: raise
        except Exception: fail(24)
        atomic(marker, b'enabled\n')
    except Exception:
        # Restore only the entry this attempt wrote, preserving other keys
        # if the CLI or another actor updated the config during this attempt.
        try:
            if permission_added: remove_show(settings_path)
            latest = read_json(config_path)
            entries = slot(latest, kind, True)
            if entries.get('oc-ui') == expected:
                if existing is None: entries.pop('oc-ui', None)
                else: entries['oc-ui'] = existing
                save_json(config_path, latest)
            for path, previous in [(helper,old_helper),(manifest_path,old_manifest),(marker,rollback_marker)]:
                if previous is None: remove(path)
                else: atomic(path, previous)
        except Exception:
            # A partial rollback must still leave new tool calls disabled.
            try: remove(marker)
            except Exception: pass
        raise
    sys.exit(0)
try: main()
except SetupError as error: sys.exit(error.code)
except Exception: sys.exit(24)
''';
