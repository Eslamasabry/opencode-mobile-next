import '../../domain/agent_catalog.dart';

/// Catalog-authored payload removal in the fixed oc setup view. This never
/// signs out, deletes a profile home, stops a helper, or kills a process.
abstract final class AgentRemovalScripts {
  static const _executables = {
    'codex': 'codex',
    'gemini': 'gemini',
    'qwen': 'qwen',
    'goose': 'goose',
    'omp-acp': 'omp',
    'fx': 'fx',
  };

  /// Exit codes are stable: 0 absent/removed, 16 busy, 17 unsafe, 18 failed.
  /// Only native-whitelisted check receipts cross the setup channel.
  static String remove(AgentDescriptor agent) {
    final recipe = agent.recipe;
    if (_executables[agent.id] == null ||
        recipe == null ||
        recipe.executable != _executables[agent.id] ||
        !{AgentRoute.paseoNative, AgentRoute.acpPaseo}.contains(agent.route) ||
        !RegExp(
          r'^[0-9A-Za-z][0-9A-Za-z._+-]{0,79}$',
        ).hasMatch(recipe.version)) {
      throw ArgumentError('This agent has no safe payload removal recipe.');
    }
    return '''
set -u
printf '%s\\n' '::oc-check-begin agent-${agent.id}'
oc_result=18
if command -v python3 >/dev/null 2>&1; then
  python3 - '${agent.id}' '${recipe.executable}' '${recipe.version}' 2>/dev/null <<'OC_AGENT_REMOVE'
$_remove
OC_AGENT_REMOVE
  oc_result=\$?
fi
case "\$oc_result" in 0|16|17|18) ;; *) oc_result=18 ;; esac
printf '::oc-check-end agent-${agent.id} %s\\n' "\$oc_result"
exit "\$oc_result"
''';
  }

  static const _remove = r'''
import os, pathlib, re, shutil, stat, sys
agent, executable, version = sys.argv[1:]
allowed = {'codex': 'codex', 'gemini': 'gemini', 'qwen': 'qwen',
           'goose': 'goose', 'omp-acp': 'omp', 'fx': 'fx'}
if (allowed.get(agent) != executable or
    not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]{0,79}', version) or
    os.getuid() != 1000 or os.environ.get('HOME') != '/home/oc'):
    sys.exit(17)
root = pathlib.Path('/home/oc')
parent = root / '.local/share/oc-agents'
base = parent / agent
bin_dir = root / '.local/bin'
link = bin_dir / executable
lock = parent / ('.lock-' + executable)
expected = str(base / version / 'launch')
proc = pathlib.Path('/proc')
class Refused(Exception):
    def __init__(self, code): self.code = code
def info(path):
    try: return path.lstat()
    except FileNotFoundError: return None
def ancestors():
    for path in (root.parent, root, root / '.local', root / '.local/share', parent, bin_dir):
        entry = info(path)
        if entry is not None and not stat.S_ISDIR(entry.st_mode): raise Refused(17)
def tree(path):
    entry = info(path)
    if entry is None: return
    if not stat.S_ISDIR(entry.st_mode): raise Refused(17)
    for directory, dirs, files in os.walk(path, followlinks=False):
        for name in dirs + files:
            entry = (pathlib.Path(directory) / name).lstat()
            # Packaged node_modules may contain links. Neither this walk nor
            # fd-based rmtree follows them; only their directory entries go.
            if not (stat.S_ISDIR(entry.st_mode) or stat.S_ISREG(entry.st_mode) or
                    stat.S_ISLNK(entry.st_mode)):
                raise Refused(17)
def targets():
    tree(base)
    entry = info(link)
    if entry is not None and (not stat.S_ISLNK(entry.st_mode) or os.readlink(link) != expected):
        raise Refused(17)
    transient = list(bin_dir.glob(executable + '.new.*'))
    for path in transient:
        # Install only authors decimal-PID temporary launcher links.
        suffix = path.name[len(executable + '.new.'):]
        entry = path.lstat()
        if (not suffix.isdecimal() or not stat.S_ISLNK(entry.st_mode) or
            os.readlink(path) != expected): raise Refused(17)
    return transient
def live_target():
    guest_base = str(base).encode() + b'/'
    guest_link = str(link).encode()
    # Linux exe links may show the host rootfs prefix rather than guest paths.
    native_suffix = '/home/oc/.local/share/oc-agents/' + agent + '/'
    for path in proc.iterdir():
        if not path.name.isdecimal() or int(path.name) == os.getpid(): continue
        try:
            args = (path / 'cmdline').read_bytes().split(b'\0')
            if any(arg == guest_link or arg.startswith(guest_base) for arg in args): return True
            try: actual = os.readlink(path / 'exe')
            except OSError: actual = ''
            if native_suffix in actual: return True
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            # Android hides other app UIDs; agents in our rootfs use this UID.
            continue
    return False
owned_lock = False
result = 18
try:
    ancestors()
    transient = targets()
    # Absence must not overwrite or remove a foreign/stale install lock.
    if info(lock) is not None: raise Refused(16)
    if info(base) is None and info(link) is None and not transient:
        if live_target(): raise Refused(16)
        result = 0
    else:
        if info(parent) is None: raise Refused(17)
        try: lock.mkdir(mode=0o700)
        except FileExistsError: raise Refused(16)
        owned_lock = True
        # Recheck after acquiring the installer lock and before any deletion.
        ancestors()
        transient = targets()
        if live_target(): raise Refused(16)
        if not shutil.rmtree.avoids_symlink_attacks: raise Refused(18)
        if info(link) is not None: link.unlink()
        for path in transient: path.unlink()
        if info(base) is not None: shutil.rmtree(base)
        if info(base) is not None or info(link) is not None or targets():
            raise Refused(18)
        if live_target(): raise Refused(18)
        result = 0
except Refused as refusal:
    result = refusal.code
except Exception:
    result = 18
finally:
    if owned_lock:
        try: lock.rmdir()
        except OSError: result = 18
sys.exit(result)
''';
}
