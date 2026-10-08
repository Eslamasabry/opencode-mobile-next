#!/usr/bin/env python3
"""No-account stdio launch probe. Run as Ubuntu oc, not Android/root.
No auth/login, prompts, model requests, credential reads, or account writes.
Raw child output is private (0600), bounded, and never printed.
This is a CLI protocol probe; it does not certify the app/Paseo integration.
"""
import argparse
import json
import os
import re
import selectors
import shutil
import signal
import subprocess
import tempfile
import time
from pathlib import Path

AGENTS = {
    'codex': ('codex', ['app-server']),
    'gemini': ('gemini', ['--acp']),
    'qwen': ('qwen', ['--acp']),
    'goose': ('goose', ['acp']),
    'omp-acp': ('omp', ['acp']),
    'fx': ('fx', ['acp']),
}
LIMIT = 2 * 1024 * 1024

class ProbeStop(Exception):
    def __init__(self, state):
        self.state = state


def proc_identity(pid):
    try:
        text = Path('/proc/%d/stat' % pid).read_text()
        fields = text[text.rfind(')') + 2:].split()
        return int(fields[1]), fields[19]  # PPID and start time
    except (OSError, ValueError, IndexError):
        return None


def descendants(root):
    found = {}
    parents = {root}
    for _ in range(20):
        added = False
        for path in Path('/proc').iterdir():
            if not path.name.isdigit():
                continue
            pid = int(path.name)
            if pid in parents:
                continue
            value = proc_identity(pid)
            if value and value[0] in parents:
                found[pid] = value[1]
                parents.add(pid)
                added = True
        if not added:
            break
    return found


def classify_error(error, acp):
    code = error.get('code') if isinstance(error, dict) else None
    # Text only determines a closed enum. No raw message is published.
    message = str(error.get('message', '')) if isinstance(error, dict) else ''
    if (acp and code == -32000) or re.search(r'auth(?:entication)? (?:is )?required|not (?:logged|signed) in|missing.*(?:credential|api.?key)|no.*(?:credential|api.?key)|sign.?in|log.?in', message, re.I):
        state = 'authentication_required' if (acp and code == -32000) or re.search(r'auth|credential|api.?key|sign.?in|log.?in', message, re.I) else 'rpc_error'
    else:
        state = 'rpc_error'
    return {'state': state, 'rpcCode': code if type(code) is int else None}


class StdioProbe:
    def __init__(self, process, raw_dir, timeout, acp):
        self.process = process
        self.acp = acp
        self.timeout = timeout
        self.deadline = time.monotonic() + timeout * 2 + 5
        self.selector = selectors.DefaultSelector()
        self.selector.register(process.stdout, selectors.EVENT_READ, 'stdout')
        self.selector.register(process.stderr, selectors.EVENT_READ, 'stderr')
        self.output = bytearray()
        self.responses = {}
        self.invalid_lines = 0
        self.server_requests = 0
        self.notifications = 0
        self.sizes = {'stdout': 0, 'stderr': 0}
        self.logs = {}
        for name in ('stdout', 'stderr'):
            fd = os.open(str(raw_dir / (name + '.raw')), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            self.logs[name] = os.fdopen(fd, 'wb', buffering=0)

    def send(self, value):
        try:
            self.process.stdin.write((json.dumps(value, separators=(',', ':')) + '\n').encode())
            self.process.stdin.flush()
        except (OSError, BrokenPipeError):
            raise ProbeStop('process_exited')

    def request(self, request_id, method, params):
        self.send({'jsonrpc': '2.0', 'id': request_id, 'method': method, 'params': params})
        end = min(self.deadline, time.monotonic() + self.timeout)
        while request_id not in self.responses:
            if time.monotonic() >= end:
                raise ProbeStop('timed_out')
            events = self.selector.select(max(0, end - time.monotonic()))
            if not events and self.process.poll() is not None:
                raise ProbeStop('process_exited')
            for key, _ in events:
                try:
                    block = os.read(key.fileobj.fileno(), 8192)
                except OSError:
                    block = b''
                if not block:
                    self.selector.unregister(key.fileobj)
                    if key.data == 'stdout':
                        raise ProbeStop('process_exited')
                    continue
                name = key.data
                remaining = LIMIT - self.sizes[name]
                self.logs[name].write(block[:max(0, remaining)])
                self.sizes[name] += len(block)
                if self.sizes[name] > LIMIT:
                    raise ProbeStop('output_limit')
                if name != 'stdout':
                    continue
                self.output.extend(block)
                if len(self.output) > LIMIT:
                    raise ProbeStop('frame_limit')
                while b'\n' in self.output:
                    line, _, tail = self.output.partition(b'\n')
                    self.output = bytearray(tail)
                    try:
                        value = json.loads(line)
                    except (ValueError, UnicodeDecodeError):
                        self.invalid_lines += 1
                        continue
                    if not isinstance(value, dict):
                        self.invalid_lines += 1
                        continue
                    if 'id' in value and 'method' in value:
                        self.server_requests += 1
                        # No capability or permission is granted by this probe.
                        self.send({'jsonrpc': '2.0', 'id': value['id'], 'error': {'code': -32601, 'message': 'Probe does not support client operations'}})
                    elif 'id' in value:
                        if type(value['id']) is int:
                            self.responses[value['id']] = value
                    else:
                        self.notifications += 1
        value = self.responses.pop(request_id)
        if 'error' in value:
            return classify_error(value['error'], self.acp), None
        if not isinstance(value.get('result'), dict):
            return {'state': 'invalid_response'}, None
        return {'state': 'response'}, value['result']

    def close(self):
        self.selector.close()
        for handle in self.logs.values():
            handle.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('agent', choices=AGENTS)
    parser.add_argument('--timeout', type=float, default=25)
    parser.add_argument('--private-parent', default='/tmp')
    args = parser.parse_args()
    if not 1 <= args.timeout <= 60:
        parser.error('timeout must be between 1 and 60 seconds')
    if os.getuid() != 1000:
        print(json.dumps({'agent': args.agent, 'state': 'invalid_context'}))
        return 2
    os.umask(0o077)
    raw_dir = Path(tempfile.mkdtemp(prefix='fqinstall-launch-', dir=args.private_parent))
    home = raw_dir / 'empty-home'
    cwd = home / 'project'
    cwd.mkdir(parents=True)
    for name in ['codex','config','cache','data','state']:
        (home / name).mkdir()
    executable, flags = AGENTS[args.agent]
    env = {
        'HOME': str(home), 'CODEX_HOME': str(home / 'codex'),
        'XDG_CONFIG_HOME': str(home / 'config'), 'XDG_CACHE_HOME': str(home / 'cache'),
        'XDG_DATA_HOME': str(home / 'data'), 'XDG_STATE_HOME': str(home / 'state'),
        'PATH': '/home/oc/.local/bin:/home/oc/.local/node/bin:/usr/bin:/bin', 'LANG': 'C.UTF-8',
        'TERM': 'dumb', 'NO_COLOR': '1', 'BROWSER': '/bin/false',
        'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': '/dev/null',
    }
    result = {'agent': args.agent, 'state': 'not_started', 'sentPrompt': False, 'sentLogin': False}
    process = None
    probe = None
    started = time.monotonic()
    try:
        process = subprocess.Popen(['/home/oc/.local/bin/' + executable] + flags,
                                   cwd=cwd, env=env, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   start_new_session=True)
        probe = StdioProbe(process, raw_dir, args.timeout, args.agent != 'codex')
        if args.agent == 'codex':
            init = {'clientInfo': {'name': 'fq-install-check', 'version': '1.0'}, 'capabilities': {'experimentalApi': False}}
        else:
            init = {'protocolVersion': 1, 'clientCapabilities': {'fs': {'readTextFile': False, 'writeTextFile': False}, 'terminal': False}, 'clientInfo': {'name': 'fq-install-check', 'version': '1.0'}}
        state, data = probe.request(1, 'initialize', init)
        result['initialize'] = state
        if data is None:
            result['state'] = state['state']
            return_result = False
        else:
            return_result = True
        if return_result and args.agent == 'codex':
            probe.send({'jsonrpc': '2.0', 'method': 'initialized', 'params': {}})
            state, data = probe.request(2, 'account/read', {'refreshToken': False})
            result['accountRead'] = state
            if data is not None:
                if 'account' not in data or type(data.get('requiresOpenaiAuth')) is not bool:
                    result['state'] = 'invalid_account_response'
                elif data['account'] is None:
                    result['state'] = 'signed_out'
                    result['requiresSignIn'] = data['requiresOpenaiAuth']
                else:
                    result['state'] = 'unexpected_account_present'
            else:
                result['state'] = state['state']
        elif return_result:
            if data.get('protocolVersion') != 1:
                result['state'] = 'protocol_version_mismatch'
            else:
                result['advertisedAuthMethods'] = len(data.get('authMethods', [])) if isinstance(data.get('authMethods', []), list) else None
                state, data = probe.request(2, 'session/new', {'cwd': str(cwd), 'mcpServers': []})
                result['sessionNew'] = state
                result['state'] = ('session_created_without_prompt' if isinstance(data, dict) and isinstance(data.get('sessionId'), str) and data['sessionId'] else 'invalid_session_response') if data is not None else state['state']
    except ProbeStop as stop:
        result['state'] = stop.state
    except (OSError, ValueError):
        result['state'] = 'launch_failed'
    finally:
        owned = descendants(process.pid) if process is not None else {}
        if process is not None:
            try:
                process.stdin.close()
            except OSError:
                pass
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                pass
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            # Detached children can escape a process group. Stop only captured
            # descendants whose identity has not changed, never name matches.
            for pid, creation in owned.items():
                current = proc_identity(pid)
                if current and current[1] == creation:
                    try:
                        os.kill(pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
            try:
                process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                pass
            result['processExitCode'] = process.poll()
        if probe is not None:
            result['invalidStdoutLines'] = probe.invalid_lines
            result['clientRequestsRefused'] = probe.server_requests
            result['notifications'] = probe.notifications
            result['rawBytes'] = probe.sizes
            probe.close()
            stderr=(raw_dir / 'stderr.raw').read_bytes()
            categories=[('missing_config_directory',rb'canonicalize.*CODEX_HOME|No such file or directory.*codex'),('unsupported_argument',rb'unrecognized|unexpected argument|unknown option'),('missing_runtime',rb'node.*not found|No such file or directory.*node'),('authentication_required',rb'(?i)auth(?:entication)? required|not logged in|missing.*api.?key|no.*api.?key'),('provider_configuration_required',rb'(?i)provider.*config|configure.*provider')]
            result['stderrClass']=next((label for label,pattern in categories if re.search(pattern,stderr)), 'other_output' if stderr else 'none')
        time.sleep(0.05)
        result['leftoverOwnedPids'] = [pid for pid, creation in owned.items() if (proc_identity(pid) or (None, None))[1] == creation]
        result['elapsedSeconds'] = round(time.monotonic() - started, 2)
        shutil.rmtree(raw_dir, ignore_errors=True)
        print(json.dumps(result, separators=(',', ':')))
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
