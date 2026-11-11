import 'dart:convert';

/// Where the in-app OpenCode 2 keeps its config (`OPENCODE_CONFIG_DIR`).
const openCode2ConfigPath = '/root/.oc-opencode2/config/opencode/opencode.json';

/// The provider declaration written into the phone engine's own
/// `opencode.json`.
///
/// OpenCode 2 declares providers in its config file, not over HTTP
/// (`docs/opencode2-protocol-notes.md`: v2 has no config-write endpoint), and
/// the app owns that file inside the in-app Ubuntu. The write is therefore a
/// file operation with the same guards as the Gen UI registration: root owner,
/// no symlinks, an atomic replace, and a lock so two writes cannot interleave.
///
/// Only app-authored values reach the script: [providerID] is revalidated here
/// as well as in the draft, and the block is JSON the app built. No credential
/// is part of it — a key is connected afterwards through the server's own
/// credential store, so it cannot land in a config file the logs can read.
String customProviderWriteScript({
  required String providerID,
  required Map<String, Object?> block,
  String configPath = openCode2ConfigPath,
}) {
  if (!RegExp(r'^[a-z0-9][a-z0-9._-]{0,63}$').hasMatch(providerID)) {
    throw ArgumentError('Invalid custom provider identity');
  }
  // One base64 JSON argument carries everything variable: quotes, newlines and
  // braces in a display name never reach the shell or the Python source.
  final data = base64.encode(
    utf8.encode(jsonEncode({'id': providerID, 'block': block})),
  );
  return '''
set -eu
umask 077
# Python is a system executable, never a provider- or agent-owned runtime.
[ -x /usr/bin/python3 ] || exit 20
/usr/bin/python3 - '$configPath' '$data' <<'OC_PROVIDER_WRITE' 2>/dev/null
$_writer
OC_PROVIDER_WRITE
''';
}

const _writer = r'''
import os, sys, json, re, stat, base64, pathlib, tempfile, fcntl
class WriteError(Exception):
    def __init__(self, code): self.code = code

def fail(code): raise WriteError(code)
def safe(path, directory=False):
    path = pathlib.Path(path)
    for part in [*reversed(path.parents), path]:
        try: info = os.lstat(part)
        except FileNotFoundError: return False
        if stat.S_ISLNK(info.st_mode): fail(22)
        if part != path or directory:
            if not stat.S_ISDIR(info.st_mode): fail(22)
        elif not stat.S_ISREG(info.st_mode): fail(22)
    return True

def root_owned(path):
    for part in [*reversed(pathlib.Path(path).parents), pathlib.Path(path)]:
        try: info = os.stat(part, follow_symlinks=False)
        except FileNotFoundError: return
        if stat.S_ISLNK(info.st_mode) or info.st_uid != 0: fail(22)
        if info.st_mode & 0o022: fail(22)

def read_json(path):
    if not safe(path): return {}
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as stream: raw = stream.read(1048577)
    if len(raw) > 1048576: fail(21)
    if not raw.strip(): return {}
    try: value = json.loads(raw)
    except Exception: fail(21)
    if not isinstance(value, dict): fail(21)
    return value

def atomic(path, value):
    parent = str(pathlib.Path(path).parent)
    safe(parent, directory=True)
    fd, temporary = tempfile.mkstemp(prefix='.oc-provider-', dir=parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)

def main():
    path = sys.argv[1]
    payload = json.loads(base64.b64decode(sys.argv[2]))
    identity, block = payload['id'], payload['block']
    if os.getuid() != 0: fail(22)
    if not safe(pathlib.Path(path).parent, directory=True): fail(20)
    lock_path = str(pathlib.Path(path).parent / '.oc-provider.lock')
    if not safe(lock_path):
        fd = os.open(lock_path, os.O_CREAT | os.O_WRONLY, 0o600)
        os.close(fd)
    lock = os.open(lock_path, os.O_RDWR | os.O_NOFOLLOW)
    try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError: fail(23)
    root_owned(path)
    config = read_json(path)
    providers = config.get('providers')
    if providers is None: providers = {}
    if not isinstance(providers, dict): fail(21)
    if identity in providers: fail(24)
    providers[identity] = block
    config['providers'] = providers
    atomic(path, (json.dumps(config, separators=(',', ':')) + '\n').encode())

try: main()
except WriteError as error: sys.exit(error.code)
except Exception: sys.exit(21)
''';

/// What a write reported, so the app can say something true about it.
enum CustomProviderWriteOutcome {
  /// `providers.<id>` is now in the config file. The running server has not
  /// read it yet: it loads its config at startup.
  saved,

  /// The config folder is not there: Ubuntu or the phone server is not set up.
  notSetUp,

  /// The existing `opencode.json` is not a JSON object, or its `providers` is
  /// not one. Nothing was changed.
  unusableConfig,

  /// The file or one of its folders is a symlink, owned by another account, or
  /// writable by others. Nothing was changed.
  unsafePath,

  /// Another write held the lock. Nothing was changed; retrying is safe.
  busy,

  /// A provider with this ID is already declared. Nothing was changed.
  alreadyExists,
}

CustomProviderWriteOutcome customProviderWriteOutcome(int exitCode) =>
    switch (exitCode) {
      0 => CustomProviderWriteOutcome.saved,
      20 => CustomProviderWriteOutcome.notSetUp,
      22 => CustomProviderWriteOutcome.unsafePath,
      23 => CustomProviderWriteOutcome.busy,
      24 => CustomProviderWriteOutcome.alreadyExists,
      _ => CustomProviderWriteOutcome.unusableConfig,
    };
