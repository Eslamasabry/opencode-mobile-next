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
  static String remove(AgentDescriptor agent, {required String receiptId}) {
    _validateReceiptId(receiptId);
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
  python3 - '${agent.id}' '${recipe.executable}' '${recipe.version}' '$receiptId' 2>/dev/null <<'OC_AGENT_REMOVE'
$_remove
OC_AGENT_REMOVE
  oc_result=\$?
fi
case "\$oc_result" in 0|16|17|18) ;; *) oc_result=18 ;; esac
printf '::oc-check-end agent-${agent.id} %s\\n' "\$oc_result"
exit "\$oc_result"
''';
  }

  static void _validateReceiptId(String id) {
    if (!RegExp(r'^[a-f0-9]{32}$').hasMatch(id)) {
      throw ArgumentError('Removal needs a private operation receipt.');
    }
  }

  /// Root-authored reader for a single operation. Emits only bytes and a 0/1
  /// absence flag, then deletes the bounded private receipt. Never runs data.
  static String readReceipt(String receiptId) {
    _validateReceiptId(receiptId);
    return '''
set -u
python3 - '$receiptId' 2>/dev/null <<'OC_AGENT_REMOVAL_RECEIPT'
$_readReceipt
OC_AGENT_REMOVAL_RECEIPT
''';
  }

  static const _remove = r'''
import json, os, pathlib, re, shutil, stat, sys
agent, executable, version, receipt_id = sys.argv[1:]
allowed = {'codex': 'codex', 'gemini': 'gemini', 'qwen': 'qwen',
           'goose': 'goose', 'omp-acp': 'omp', 'fx': 'fx'}
if (allowed.get(agent) != executable or
    not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]{0,79}', version) or
    not re.fullmatch(r'[a-f0-9]{32}', receipt_id) or
    os.getuid() != 1000 or os.environ.get('HOME') != '/home/oc'):
    sys.exit(17)
root = pathlib.Path('/home/oc')
parent = root / '.local/share/oc-agents'
base = parent / agent
bin_dir = root / '.local/bin'
link = bin_dir / executable
lock = parent / ('.lock-' + executable)
receipt = root / '.local/share' / ('oc-agent-removal-' + receipt_id)
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
    if entry is not None and (not stat.S_ISLNK(entry.st_mode) or not authored(os.readlink(link))):
        raise Refused(17)
    transient = list(bin_dir.glob(executable + '.new.*'))
    for path in transient:
        # Install only authors decimal-PID temporary launcher links.
        suffix = path.name[len(executable + '.new.'):]
        entry = path.lstat()
        if (not suffix.isdecimal() or not stat.S_ISLNK(entry.st_mode) or
            not authored(os.readlink(path))): raise Refused(17)
    return transient
def authored(target):
    # Older installed pins are still this catalog target, never another agent.
    prefix = str(base) + '/'
    if not target.startswith(prefix): return False
    suffix = target[len(prefix):].split('/')
    return (len(suffix) == 2 and suffix[1] == 'launch' and
            re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]{0,79}', suffix[0]) is not None)
def live_target():
    guest_base = str(base).encode() + b'/'
    guest_link = str(link).encode()
    # Linux exe links may show the host rootfs prefix rather than guest paths.
    native_suffix = '/home/oc/.local/share/oc-agents/' + agent + '/'
    installer = re.compile(rb'(?m)^oc_lock=' + re.escape(str(lock).encode()) + rb'(?:\r?\n|;|$)')
    for path in proc.iterdir():
        if not path.name.isdecimal() or int(path.name) == os.getpid(): continue
        try:
            args = (path / 'cmdline').read_bytes().split(b'\0')
            if any(arg == guest_link or arg.startswith(guest_base) for arg in args): return True
            # The authored installer may hold an empty lock before its first
            # target path reaches curl/Node argv. Never steal that live lock.
            for index in range(len(args) - 2):
                if (args[index].rsplit(b'/', 1)[-1] in (b'sh', b'bash') and
                    args[index + 1] == b'-c' and installer.search(args[index + 2])):
                    return True
            try: actual = os.readlink(path / 'exe')
            except OSError: actual = ''
            if native_suffix in actual: return True
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            # Android hides other app UIDs; agents in our rootfs use this UID.
            continue
    return False
def lock_entry():
    entry = info(lock)
    if entry is None: return None
    if not stat.S_ISDIR(entry.st_mode): raise Refused(17)
    if any(lock.iterdir()): raise Refused(16)
    return entry
def allocated(paths):
    found = {}
    def add(path):
        entry = path.lstat()
        key = (entry.st_dev, entry.st_ino)
        if key not in found: found[key] = [entry, 0]
        found[key][1] += 1
    for path in paths:
        entry = info(path)
        if entry is None: continue
        add(path)
        if stat.S_ISDIR(entry.st_mode):
            for directory, dirs, files in os.walk(path, followlinks=False):
                for name in dirs + files: add(pathlib.Path(directory) / name)
    total = 0
    for entry, links_removed in found.values():
        # A file hard-linked outside the removed set stays allocated there.
        if not stat.S_ISDIR(entry.st_mode) and entry.st_nlink > links_removed: continue
        total += entry.st_blocks * 512
    if not 0 <= total <= 9223372036854775807: raise Refused(18)
    return total
def write_receipt(size, absent):
    ancestors()
    fd = os.open(receipt, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
    try:
        entry = os.fstat(fd)
        if (not stat.S_ISREG(entry.st_mode) or entry.st_uid != 1000 or
            stat.S_IMODE(entry.st_mode) != 0o600 or entry.st_nlink != 1):
            raise Refused(18)
        data = json.dumps({'bytes': size, 'absent': int(absent)}, separators=(',', ':')).encode('ascii')
        while data:
            written = os.write(fd, data)
            if written <= 0: raise Refused(18)
            data = data[written:]
        os.fsync(fd)
    finally:
        os.close(fd)
owned_lock = False
owned_identity = None
result = 18
size = 0
absent = False
os.umask(0o077)
try:
    ancestors()
    transient = targets()
    if info(receipt) is not None: raise Refused(17)
    stale = lock_entry()
    if live_target(): raise Refused(16)
    absent = info(base) is None and info(link) is None and not transient and stale is None
    if absent:
        result = 0
    else:
        if info(parent) is None: raise Refused(17)
        # The host also requires a terminal native setup job. Recover only a
        # proven empty real directory with no target or authored installer PID.
        stale_bytes = allocated([lock]) if stale is not None else 0
        if stale is not None:
            current = lock_entry()
            if current is None or (current.st_dev, current.st_ino) != (stale.st_dev, stale.st_ino):
                raise Refused(16)
            lock.rmdir()
        try: lock.mkdir(mode=0o700)
        except FileExistsError: raise Refused(16)
        owned_lock = True
        held = lock.lstat()
        owned_identity = (held.st_dev, held.st_ino)
        # Recheck after acquiring the installer lock and before any deletion.
        ancestors()
        transient = targets()
        if live_target(): raise Refused(16)
        if not shutil.rmtree.avoids_symlink_attacks: raise Refused(18)
        size = allocated([base, link] + transient) + stale_bytes
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
        try:
            held = lock.lstat()
            if not stat.S_ISDIR(held.st_mode) or (held.st_dev, held.st_ino) != owned_identity:
                raise Refused(18)
            lock.rmdir()
        except Exception: result = 18
if result == 0:
    try:
        write_receipt(size, absent)
    except Exception:
        result = 18
sys.exit(result)
''';

  static const _readReceipt = r'''
import json, os, pathlib, re, stat, sys
receipt_id = sys.argv[1]
if not re.fullmatch(r'[a-f0-9]{32}', receipt_id) or os.getuid() != 0: sys.exit(18)
root = pathlib.Path('/home/oc')
parent = root / '.local/share'
name = 'oc-agent-removal-' + receipt_id
fd = directory = None
identity = None
result = None
try:
    for path in (root.parent, root, root / '.local', parent):
        if not stat.S_ISDIR(path.lstat().st_mode): raise ValueError()
    directory = os.open(parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
    entry = os.fstat(fd)
    # PRoot's root-id and change-id views project the same Android app owner
    # as 0 or 1000 respectively. Creation requires the fixed UID1000 view.
    if (not stat.S_ISREG(entry.st_mode) or entry.st_uid not in (0, 1000) or
        stat.S_IMODE(entry.st_mode) != 0o600 or entry.st_nlink != 1): raise ValueError()
    identity = (entry.st_dev, entry.st_ino)
    if not 1 <= entry.st_size <= 96: raise ValueError()
    data = os.read(fd, 97)
    if len(data) != entry.st_size or len(data) > 96: raise ValueError()
    def unique(pairs):
        output = {}
        for key, value in pairs:
            if key in output: raise ValueError()
            output[key] = value
        return output
    value = json.loads(data.decode('ascii'), object_pairs_hook=unique)
    if (not isinstance(value, dict) or set(value) != {'bytes', 'absent'} or
        type(value['bytes']) is not int or type(value['absent']) is not int or
        not 0 <= value['bytes'] <= 9223372036854775807 or value['absent'] not in (0, 1) or
        (value['absent'] == 1 and value['bytes'] != 0)): raise ValueError()
    result = str(value['bytes']) + ' ' + str(value['absent'])
except Exception:
    result = None
finally:
    if fd is not None: os.close(fd)
    if directory is not None:
        try:
            if identity is not None:
                current = os.stat(name, dir_fd=directory, follow_symlinks=False)
                if (current.st_dev, current.st_ino) != identity: raise ValueError()
                os.unlink(name, dir_fd=directory)
        except Exception:
            result = None
        os.close(directory)
if result is None: sys.exit(18)
print(result)
''';
}
