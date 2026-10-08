"""Private QA installer channel. Caller owns the emulator lock and native commit."""
import copy
import json
import os
import re
import select
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

try:
    from bb3_runtime_acceptance import PACKAGE, SERIAL, PRIVATE, Refused, parse_stat, same_process
except ModuleNotFoundError:
    from tool.qa.bb3_runtime_acceptance import PACKAGE, SERIAL, PRIVATE, Refused, parse_stat, same_process

START_SECONDS = 20
DRAIN_SECONDS = 15
COMMAND_BYTES = 65536
PRIVATE_BYTES = 131072
HEADER_BYTES = 128
STDERR_BYTES = 4096
WRITER = PRIVATE + '/shared_prefs/builtin_component_writer.xml'
IDENTITY_KEYS = {'pid', 'startTicks', 'parent', 'group', 'session'}
TARGETS = {'OPENCODE1', 'OPENCODE2', 'PASEO', 'CLAUDE', 'LEGACY_CLAUDE'}


def require(value, code):
    if not value:
        raise Refused(code)


def identity_map(value):
    require(isinstance(value, dict) and set(value) == IDENTITY_KEYS and
            all(type(v) is int for v in value.values()) and value['pid'] > 1 and
            value['startTicks'] > 0 and value['parent'] >= 0 and value['group'] > 0 and value['session'] >= 0,
            'external_identity_invalid')
    return value


def wire_identity(value):
    return {key: value[key] for key in IDENTITY_KEYS}


def hex64(value):
    return isinstance(value, str) and re.fullmatch('[a-f0-9]{64}', value) is not None


def validate_export(value):
    require(isinstance(value, dict) and set(value) == {'version', 'uid', 'app', 'ticketId', 'command', 'environment'} and
            type(value['version']) is int and value['version'] == 1 and type(value['uid']) is int and
            10000 <= value['uid'] < 2147483647 and hex64(value['ticketId']), 'external_export_invalid')
    identity_map(value['app'])
    command = value['command']
    require(isinstance(command, list) and 3 <= len(command) <= 128 and
            all(isinstance(v, str) and 0 < len(v.encode()) <= 32768 and
                all(ord(c) >= 32 or c in '\n\t' for c in v) and '\x7f' not in v for v in command) and
            sum(len(v.encode()) for v in command) <= COMMAND_BYTES, 'external_command_invalid')
    native_pattern = r'/data/app/[A-Za-z0-9_./+=~-]+/' + re.escape(PACKAGE) + r'-[A-Za-z0-9_=~-]+/lib/(?:arm64|x86_64)'
    proots = [v for v in command if re.fullmatch(native_pattern + r'/libproot\.so', v)]
    require(len(proots) == 1, 'external_private_command_invalid')
    native = proots[0].rsplit('/', 1)[0]
    require(all(part not in {'.','..'} for part in native.split('/')) and
            sum(v.endswith('/libproot.so') for v in command) == 1, 'external_private_command_invalid')
    require(command[0] in {proots[0], native + '/libaiteam_sandbox.so'} and
            (command[0] == proots[0] or '--' in command[:command.index(proots[0])]), 'external_launcher_invalid')
    roots = [v for v in command if v.startswith('--rootfs=')]
    require(len(roots) == 1 and roots[0] in {'--rootfs=' + PRIVATE + '/files/linux/ubuntu',
            '--rootfs=/data/data/' + PACKAGE + '/files/linux/ubuntu'} and '--kill-on-exit' in command,
            'external_private_command_invalid')
    require(any('OC-INSTALL-1' in v and value['ticketId'] in v and 'read -r permit' in v for v in command),
            'external_gate_invalid')
    environment = value['environment']
    require(isinstance(environment, dict) and set(environment) == {'PROOT_LOADER', 'PROOT_TMP_DIR', 'LD_LIBRARY_PATH', 'OC_RUNTIME_OWNER'} and
            environment['PROOT_LOADER'] == native + '/libproot-loader.so' and environment['LD_LIBRARY_PATH'] == native and
            environment['PROOT_TMP_DIR'] == PRIVATE + '/cache/proot-tmp' and environment['OC_RUNTIME_OWNER'] == value['ticketId'],
            'external_environment_invalid')
    return copy.deepcopy(value)


class ExternalInstaller:
    """Two-header raw channel; no permit until matching durable native ownership.

    No nested lock. Export must originate from the installed signed QA app. This
    helper does not qualify external cgroup membership or native /proc access;
    native ExternalCommit must prove those before the caller marks its ticket.
    """
    def __init__(self, device):
        require(SERIAL == 'emulator-5554' and getattr(device, 'serial', SERIAL) == SERIAL, 'external_device_invalid')
        self.device = device
        self.process = None
        self.root = self.leader = self.export = self.committed = None
        self.witnessed = {}
        self.root_proven = self.permitted = self.drained = False
        self.deadline = None
        self.stdout_buffer = b''
        self.stderr_count = 0
        self.stderr_eof = False
        self.stderr_flags = {}

    def _remaining(self, cap=2):
        remaining = self.deadline - time.monotonic()
        require(remaining > 0, 'external_deadline_exceeded')
        return min(cap, remaining)

    def _shell(self, script, limit=PRIVATE_BYTES):
        try:
            result = self.device.adb('shell', 'su', '0', 'sh', '-c', shlex.quote(script), timeout=self._remaining())
            require(result.returncode == 0 and isinstance(result.stdout, str) and len(result.stdout.encode()) <= limit,
                    'external_kernel_read_failed')
            return result.stdout
        except Refused:
            raise
        except Exception:
            raise Refused('external_kernel_read_failed') from None

    def _read(self, pid, name, limit=4096):
        require(type(pid) is int and pid > 1 and name in {'stat', 'status', 'cmdline', 'environ'}, 'external_kernel_path_invalid')
        return self._shell(f'if [ -e /proc/{pid}/{name} ]; then head -c {limit+1} /proc/{pid}/{name}; fi', limit)

    def _identity(self, pid):
        raw = self._read(pid, 'stat')
        try:
            return parse_stat(raw) if raw else None
        except Exception:
            raise Refused('external_identity_invalid') from None

    def _uid(self, pid):
        fields = [line.split()[1:] for line in self._read(pid, 'status').splitlines() if line.startswith('Uid:')]
        require(len(fields) == 1 and len(fields[0]) == 4 and all(v.isdecimal() for v in fields[0]), 'external_uid_invalid')
        return list(map(int, fields[0]))

    def _nonce(self, pid):
        raw = self._read(pid, 'environ', PRIVATE_BYTES)
        return ('OC_RUNTIME_OWNER=' + self.export['ticketId']) in raw.split('\x00')

    def _inventory(self):
        raw = self._shell('ps -A -o PID,PPID,UID', 262144)
        result = {}
        lines = raw.splitlines()
        require(len(lines) <= 2049, 'external_inventory_overflow')
        for line in lines:
            fields = line.split()
            if fields == ['PID', 'PPID', 'UID']:
                continue
            require(len(fields) == 3 and all(v.isdecimal() for v in fields), 'external_inventory_invalid')
            pid, parent, uid = map(int, fields)
            if uid == self.export['uid']:
                require(pid > 1 and pid not in result, 'external_inventory_invalid')
                result[pid] = parent
        require(len(result) <= 128, 'external_inventory_overflow')
        return result

    def _prove_root(self):
        require(self.root and same_process(self.root, self._identity(self.root['pid'])), 'external_root_changed')
        pid = self.root['pid']
        current = self._identity(pid)
        require(current and current['session'] == pid and current['group'] == pid, 'external_root_session_invalid')
        require(pid != self.export['app']['pid'] and self._uid(pid) == [self.export['uid']] * 4 and self._nonce(pid), 'external_root_owner_invalid')
        argv = self._read(pid, 'cmdline', PRIVATE_BYTES).rstrip('\x00').split('\x00')
        original = self.export['command']
        proot = next(i for i, v in enumerate(original) if v.endswith('/libproot.so'))
        # The original protected launcher is executed unchanged. Its verified
        # exec into PRoot may replace argv without changing PID/start ticks.
        require(argv == original or (original[0].endswith('/libaiteam_sandbox.so') and argv == original[proot:]),
                'external_root_command_changed')
        require(same_process(self.root, self._identity(pid)), 'external_root_changed')
        self.root_proven = True
        self.witnessed[pid] = copy.deepcopy(self.root)

    def _capture(self):
        require(self.root_proven, 'external_root_unproven')
        rows = self._inventory()
        selected = set()
        for pid, old in self.witnessed.items():
            current = self._identity(pid)
            require(current is None or old['startTicks'] == current['startTicks'], 'external_pid_reused')
            if same_process(old, current):
                require(self._uid(pid) == [self.export['uid']] * 4, 'external_uid_changed')
                selected.add(pid)
        for _ in range(128):
            children = {pid for pid, parent in rows.items() if parent in selected}
            if children <= selected:
                break
            selected |= children
        require(len(selected) <= 128, 'external_inventory_overflow')
        for pid in selected:
            current = self._identity(pid)
            require(current is not None and current['state'] not in {'Z','X'} and current['parent'] == rows.get(pid), 'external_inventory_changed')
            require(pid != self.export['app']['pid'] and self._uid(pid) == [self.export['uid']] * 4 and self._nonce(pid), 'external_child_owner_invalid')
            if self.leader and pid != self.root['pid']:
                require(current['session'] == self.leader['session'] and current['group'] == self.leader['group'], 'external_child_session_invalid')
            old = self.witnessed.get(pid)
            require(old is None or same_process(old, current), 'external_pid_reused')
            require(same_process(current, self._identity(pid)), 'external_inventory_changed')
            self.witnessed[pid] = copy.deepcopy(current)
        sessions = {self.root['session']} | ({self.leader['session']} if self.leader else set())
        for pid in set(rows) - selected - {self.export['app']['pid']}:
            current = self._identity(pid)
            require(current is None or current['state'] in {'Z','X'} or current['session'] not in sessions, 'external_child_unproven')
        return selected

    def _remember_stderr(self, raw):
        lowered = raw.lower()
        for code in ['policy_path_unavailable', 'landlock_rule_failed', 'landlock_restrict_failed',
                     'seccomp_failed', 'sandbox_exec_failed', 'invalid_arguments']:
            self.stderr_flags[code] = self.stderr_flags.get(code, False) or code.encode() in lowered
        patterns = {'permissionDenied': [b'permission denied', b'operation not permitted'],
                    'prootError': [b'proot error', b'proot fatal'],
                    'executableMissing': [b'no such file or directory'],
                    'androidLinkerFailure': [b'cannot link executable'],
                    'suUsageFailure': [b'usage: su', b'invalid uid', b'unknown id'],
                    'envReportedError': [b'env:'], 'suReportedError': [b'su:'],
                    'prootNamed': [b'libproot.so'], 'sandboxNamed': [b'libaiteam_sandbox.so'],
                    'loaderNamed': [b'libproot-loader.so'], 'environmentAssignmentNamed': [b'PROOT_LOADER='.lower(), b'OC_RUNTIME_OWNER='.lower(), b'LD_LIBRARY_PATH='.lower(), b'PROOT_TMP_DIR='.lower()]}
        for code, words in patterns.items():
            self.stderr_flags[code] = self.stderr_flags.get(code, False) or any(word in lowered for word in words)

    def _header(self):
        out, err = self.process.stdout.fileno(), self.process.stderr.fileno()
        while b'\n' not in self.stdout_buffer:
            ready, _, _ = select.select([out] if self.stderr_eof else [out, err], [], [], self._remaining())
            for fd in ready:
                raw = os.read(fd, HEADER_BYTES + 1 if fd == out else STDERR_BYTES + 1)
                if fd == err:
                    self._remember_stderr(raw)
                    self.stderr_count += len(raw)
                    require(self.stderr_count <= STDERR_BYTES, 'external_stderr_overflow')
                    if not raw:
                        self.stderr_eof = True
                else:
                    require(raw, 'external_header_unavailable')
                    self.stdout_buffer += raw
                    require(len(self.stdout_buffer) <= HEADER_BYTES * 2, 'external_stdout_overflow')
        line, self.stdout_buffer = self.stdout_buffer.split(b'\n', 1)
        require(len(line) <= HEADER_BYTES, 'external_header_overflow')
        try:
            return line.decode('ascii')
        except UnicodeError:
            raise Refused('external_header_invalid') from None

    def start(self, exported):
        require(self.process is None, 'external_already_started')
        self.export = validate_export(exported)
        self.deadline = time.monotonic() + START_SECONDS
        app = self._identity(self.export['app']['pid'])
        require(app and app['state'] not in {'Z','X'} and wire_identity(app) == self.export['app'] and self._uid(app['pid']) == [self.export['uid']] * 4,
                'external_app_changed')
        # APK paths may contain '='. env treats such an executable pathname as
        # another assignment, even after '--'. A fixed shell command separates
        # env's assignment parser from the unchanged protected argv. Every
        # launcher exec retains the header PID; native still proves ownership.
        payload = 'printf \'OC-BB9-ROOT-1 %s\\n\' "$$"; exec /system/bin/run-as ' + PACKAGE + ' --user 0 /system/bin/env -i ' + shlex.join(
            [k + '=' + v for k, v in self.export['environment'].items()] +
            ['/system/bin/sh', '-c', 'exec "$@"', 'oc-bb9-owned-launch'] + self.export['command'])
        try:
            self.process = subprocess.Popen(['adb', '-s', SERIAL, 'shell', '-T', payload], stdin=subprocess.PIPE,
                                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0)
            for pipe in [self.process.stdin, self.process.stdout, self.process.stderr]:
                os.set_blocking(pipe.fileno(), False)
            header = re.fullmatch(r'OC-BB9-ROOT-1 ([1-9][0-9]{0,9})', self._header())
            require(header and int(header[1]) > 1, 'external_root_header_invalid')
            self.root = self._identity(int(header[1]))
            require(self.root is not None, 'external_root_exited_before_gate')
            require(self.root['pid'] != app['pid'], 'external_root_is_app')
            gate = re.fullmatch(r'OC-INSTALL-1 ([a-f0-9]{64}) ([1-9][0-9]{0,9})', self._header())
            require(gate and gate[1] == self.export['ticketId'], 'external_gate_header_invalid')
            self._prove_root()
            self.leader = self._identity(int(gate[2]))
            require(self.leader and self.leader['pid'] != self.root['pid'] and self.leader['pid'] != app['pid'] and
                    self.leader['pid'] == self.leader['group'] == self.leader['session'], 'external_leader_invalid')
            require(self._uid(self.leader['pid']) == [self.export['uid']] * 4 and self._nonce(self.leader['pid']), 'external_leader_owner_invalid')
            selected = self._capture()
            require(self.leader['pid'] in selected and same_process(self.leader, self._identity(self.leader['pid'])), 'external_leader_ancestry_invalid')
            return {'root': copy.deepcopy(self.root), 'leader': copy.deepcopy(self.leader)}
        except Refused:
            raise
        except Exception:
            raise Refused('external_launch_unavailable') from None

    def failure_diagnostics(self):
        """Fixed projection of only our authored launcher's bounded stderr."""
        result = {'rootIdentityCaptured': self.root is not None,
                  'rootOwnershipProven': self.root_proven, 'updatePermitted': self.permitted}
        if self.process is None:
            return result
        exit_code = self.process.poll()
        result['ownChannelExited'] = exit_code is not None
        result['gateReadEofExit'] = exit_code == 78
        result['gateHeaderBuffered'] = re.fullmatch(rb'OC-INSTALL-1 [a-f0-9]{64} [1-9][0-9]{0,9}\n', self.stdout_buffer) is not None
        result['stderrPreviouslyDrained'] = self.stderr_count > 0
        chunks = bytearray()
        fd = self.process.stderr.fileno()
        while len(chunks) <= STDERR_BYTES:
            ready, _, _ = select.select([fd], [], [], 0)
            if not ready:
                break
            raw = os.read(fd, min(1024, STDERR_BYTES + 1 - len(chunks)))
            if not raw:
                break
            chunks.extend(raw)
        result['stderrOverflow'] = self.stderr_count + len(chunks) > STDERR_BYTES
        self._remember_stderr(bytes(chunks))
        result.update(self.stderr_flags)
        for code in [1, 78, 126, 127, 255]:
            result['exitCode' + str(code)] = exit_code == code
        return result

    def _durable(self):
        raw = self._shell('head -c ' + str(PRIVATE_BYTES + 1) + ' ' + shlex.quote(WRITER), PRIVATE_BYTES)
        try:
            tree = ET.fromstring(raw)
            nodes = [n for n in tree if n.attrib.get('name') == 'ticket']
            require(tree.tag == 'map' and len(nodes) == 1 and nodes[0].tag == 'string', 'external_durable_ticket_invalid')
            return json.loads(nodes[0].text or '')
        except Refused:
            raise
        except Exception:
            raise Refused('external_durable_ticket_invalid') from None

    def mark_committed(self, ticket):
        require(self.root_proven and self.leader and not self.permitted, 'external_commit_state_invalid')
        self.deadline = time.monotonic() + START_SECONDS
        require(isinstance(ticket, dict) and set(ticket) == {'version','id','rootfsGeneration','targets','operation','ownership','observed'} and
                type(ticket['version']) is int and ticket['version'] == 1 and ticket['id'] == self.export['ticketId'] and
                hex64(ticket['rootfsGeneration']) and ticket['operation'] == 'INSTALL', 'external_ticket_invalid')
        targets = ticket['targets']
        require(isinstance(targets, list) and targets and len(targets) <= 5 and all(isinstance(v,str) for v in targets) and
                len(set(targets)) == len(targets) and set(targets) <= TARGETS, 'external_ticket_invalid')
        receipt = ticket['ownership']
        require(isinstance(receipt, dict) and set(receipt) == {'version','boot','nonce','generation','root','leader','other'} and
                type(receipt['version']) is int and receipt['version'] == 1 and receipt['nonce'] == self.export['ticketId'] and
                type(receipt['generation']) is int and receipt['generation'] > 0 and
                isinstance(receipt['boot'],str) and re.fullmatch('[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',receipt['boot']),
                'external_ticket_invalid')
        require(identity_map(receipt['root']) == wire_identity(self.root) and identity_map(receipt['leader']) == wire_identity(self.leader),
                'external_ticket_identity_mismatch')
        for items in [receipt['other'], ticket['observed']]:
            require(isinstance(items,list) and len(items) <= 128 and all(identity_map(v) for v in items) and
                    len({v['pid'] for v in items}) == len(items), 'external_ticket_invalid')
        require(not {i['pid'] for i in ticket['observed']} & {i['pid'] for i in receipt['other']} and
                self.export['app']['pid'] not in {i['pid'] for i in ticket['observed']}, 'external_ticket_invalid')
        selected = self._capture()
        for item in ticket['observed']:
            require(item['pid'] in selected and item == wire_identity(self.witnessed[item['pid']]), 'external_ticket_observation_mismatch')
        require(self._shell('cat /proc/sys/kernel/random/boot_id',128).strip() == receipt['boot'], 'external_boot_changed')
        require(self._durable() == ticket, 'external_durable_ticket_mismatch')
        app = self._identity(self.export['app']['pid'])
        require(app and app['state'] not in {'Z','X'} and wire_identity(app) == self.export['app'] and
                self._uid(app['pid']) == [self.export['uid']] * 4, 'external_app_changed')
        self.committed = copy.deepcopy(ticket)

    def permit(self):
        require(self.committed is not None and not self.permitted, 'external_permit_requires_commit')
        self.deadline = time.monotonic() + START_SECONDS
        self._prove_root(); self._capture()
        require(self._durable() == self.committed, 'external_durable_ticket_mismatch')
        require(self._shell('cat /proc/sys/kernel/random/boot_id',128).strip() == self.committed['ownership']['boot'], 'external_boot_changed')
        app = self._identity(self.export['app']['pid'])
        require(app and app['state'] not in {'Z','X'} and wire_identity(app) == self.export['app'] and
                self._uid(app['pid']) == [self.export['uid']] * 4, 'external_app_changed')
        data = (self.export['ticketId'] + '\n').encode('ascii')
        try:
            while data:
                _, ready, _ = select.select([], [self.process.stdin.fileno()], [], self._remaining())
                if not ready:
                    continue
                written = os.write(self.process.stdin.fileno(), data)
                require(written > 0, 'external_permit_failed'); data = data[written:]
            self.permitted = True
        except Refused:
            raise
        except Exception:
            raise Refused('external_permit_failed') from None

    def _signal(self, item, signal):
        pid, ticks, uid = item['pid'], item['startTicks'], self.export['uid']
        require(signal in {'TERM','KILL'}, 'external_signal_invalid')
        script = (f's=$(cat /proc/{pid}/stat 2>/dev/null) || exit 0; s=${{s##*) }}; set -- $s; shift 19; '
                  f'[ "$1" = {ticks} ] || exit 0; '
                  f'u=$(awk \'/^Uid:/ {{print $2,$3,$4,$5}}\' /proc/{pid}/status 2>/dev/null); '
                  f'[ "$u" = "{uid} {uid} {uid} {uid}" ] || exit 70; '
                  f's=$(cat /proc/{pid}/stat 2>/dev/null) || exit 0; s=${{s##*) }}; set -- $s; shift 19; '
                  f'[ "$1" = {ticks} ] || exit 0; kill -{signal} {pid}')
        self._shell(script)

    def _close_channel(self):
        for pipe in [self.process.stdin, self.process.stdout, self.process.stderr]:
            if pipe and not pipe.closed:
                pipe.close()
        # At most two further seconds reap only our own captured host adb child.
        deadline = time.monotonic() + 2
        try:
            self.process.wait(timeout=max(0,deadline-time.monotonic()))
        except subprocess.TimeoutExpired:
            self.process.terminate()
            try:
                self.process.wait(timeout=max(0,deadline-time.monotonic()))
            except subprocess.TimeoutExpired:
                self.process.kill()
                try:
                    self.process.wait(timeout=max(0,deadline-time.monotonic()))
                except subprocess.TimeoutExpired:
                    raise Refused('external_channel_not_reaped') from None

    def drain(self):
        if self.drained or self.process is None:
            return
        self.deadline = time.monotonic() + DRAIN_SECONDS
        if not self.process.stdin.closed:
            self.process.stdin.close()  # Before permit, fixed gate sees EOF and cannot run fault script.
        try:
            if not self.root_proven and self.root and same_process(self.root,self._identity(self.root['pid'])):
                self._prove_root()  # Only the exact captured header PID, never discovery.
            require(self.root_proven, 'external_cleanup_unproven')
            if self.root_proven:
                for signal in ['TERM','KILL']:
                    live = self._capture()
                    for pid in sorted(live,key=lambda pid:pid == self.root['pid']):
                        item=self.witnessed[pid]
                        require(self._uid(pid) == [self.export['uid']]*4 and same_process(item,self._identity(pid)), 'external_cleanup_identity_changed')
                        self._signal(item,signal)
                    time.sleep(.1)
                require(not self._capture(), 'external_cleanup_not_drained')
            self.drained = True
        finally:
            self._close_channel()
