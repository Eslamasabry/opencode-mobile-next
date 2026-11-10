#!/usr/bin/env python3
"""Isolated LAN / self-signed TLS proof; no owner servers or home packages mutate.

Use --binary to select a pre-existing standalone opencode2 executable. It is
copied to a private scratch directory. All generated credentials, server logs,
keys, XDG directories and copied executable disappear in finally cleanup.
Only sanitized assertions are written to the requested evidence JSON.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import ssl
import subprocess
import tempfile
import threading
import time

REPO = Path(__file__).resolve().parents[2]
DART = Path('/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/cache/dart-sdk/bin/dart')


def lan_address():
    routes = json.loads(subprocess.check_output(['ip', '-j', 'route', 'get', '192.0.2.1']))
    value = routes[0].get('prefsrc')
    if not value or value.startswith('127.'):
        raise RuntimeError('No non-loopback LAN source address available')
    return value


def ensure_free(port):
    with socket.socket() as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('0.0.0.0', port))


class TLSProxy:
    def __init__(self, cert, key, port=0):
        self.context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        self.context.load_cert_chain(cert, key)
        self.listener = socket.socket()
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.listener.bind(('0.0.0.0', port))
        self.port = self.listener.getsockname()[1]
        self.listener.listen()
        self.listener.settimeout(0.2)
        self.stopped = threading.Event()
        self.requests = 0
        self.authorization_requests = 0
        self.failures = []
        self.plaintext_http_requests = 0
        self.plaintext_authorization_requests = 0
        self.worker = threading.Thread(target=self.serve, daemon=True)
        self.worker.start()

    def serve(self):
        while not self.stopped.is_set():
            try:
                connection, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            try:
                connection.settimeout(10)
                first_bytes = connection.recv(65536, socket.MSG_PEEK)
                if first_bytes.startswith((b'GET ', b'POST ', b'PUT ', b'DELETE ', b'PATCH ')):
                    self.plaintext_http_requests += 1
                    if b'\r\nauthorization:' in first_bytes.lower():
                        self.plaintext_authorization_requests += 1
                with self.context.wrap_socket(connection, server_side=True) as tls:
                    data = b''
                    while b'\r\n\r\n' not in data and len(data) < 65536:
                        chunk = tls.recv(8192)
                        if not chunk:
                            break
                        data += chunk
                    if not data:
                        continue
                    self.requests += 1
                    if b'\r\nauthorization:' in data.lower():
                        self.authorization_requests += 1
                    # Raw authenticated bytes are relayed but never logged/stored.
                    with socket.create_connection(('127.0.0.1', 4098), timeout=10) as upstream:
                        upstream.sendall(data)
                        while True:
                            response = upstream.recv(65536)
                            if not response:
                                break
                            tls.sendall(response)
            except (ssl.SSLError, ConnectionError, TimeoutError, OSError) as error:
                self.failures.append(type(error).__name__)
                connection.close()

    def close(self):
        self.stopped.set()
        self.listener.close()
        self.worker.join(timeout=12)
        if self.worker.is_alive():
            raise RuntimeError('TLS proxy failed to stop')


def certificate(scratch, suffix, lan):
    cert, key = scratch / f'{suffix}.pem', scratch / f'{suffix}.key'
    subprocess.run([
        'openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
        '-keyout', str(key), '-out', str(cert), '-subj', '/CN=HTTP trust scratch proof',
        '-addext', f'subjectAltName=IP:{lan}',
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    der = ssl.PEM_cert_to_DER_cert(cert.read_text())
    return cert, key, hashlib.sha256(der).hexdigest()


def run_dart(config):
    env = dict(os.environ, OC_TEST_SLOTS='1', DART_SUPPRESS_ANALYTICS='true',
               FLUTTER_SUPPRESS_ANALYTICS='true')
    command = [str(DART), str(REPO / 'tool/qa/httptrust_live_proof.dart')]
    if env.get('HTTPTRUST_LOCK_HELD') != '1':
        command = [str(REPO / 'tool/qa/machine_lock.sh'), 'analyze', '--'] + command
    result = subprocess.run(command, cwd=REPO, env=env, input=json.dumps(config),
                            capture_output=True, text=True, timeout=180)
    if result.returncode:
        # Never report captured output: stdin contains a scratch credential.
        raise RuntimeError(f'Dart proof process failed with exit {result.returncode}')
    return json.loads(result.stdout.strip().splitlines()[-1])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    proof = {'recordedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'checks': {}, 'passed': False, 'cleanup': False}
    server = proxy = None
    scratch_holder = None
    try:
        ensure_free(4098)
        lan = lan_address()
        scratch_holder = tempfile.TemporaryDirectory(prefix='httptrust-proof-')
        if scratch_holder:
            scratch = Path(scratch_holder.name)
            scratch.chmod(0o700)
            binary = scratch / 'opencode2'
            shutil.copy2(args.binary, binary)
            env = {'PATH': '/usr/local/bin:/usr/bin:/bin', 'TERM': 'dumb', 'NO_COLOR': '1'}
            for variable in ['XDG_CONFIG_HOME', 'XDG_CACHE_HOME', 'XDG_DATA_HOME',
                             'XDG_STATE_HOME', 'XDG_RUNTIME_DIR', 'TMPDIR']:
                directory = scratch / variable.lower()
                directory.mkdir(mode=0o700)
                env[variable] = str(directory)
            env['OPENCODE_CONFIG_DIR'] = env['XDG_CONFIG_HOME']
            log_path = scratch / 'server.log'
            with log_path.open('wb') as log:
                server = subprocess.Popen([
                    str(binary), 'serve', '--port', '4098', '--hostname', '0.0.0.0',
                ], cwd=scratch, env=env, stdout=log, stderr=subprocess.STDOUT)
            password = None
            for _ in range(100):
                if server.poll() is not None:
                    raise RuntimeError('Isolated scratch server exited before ready')
                log = re.sub(r'\x1b\[[0-9;]*m', '', log_path.read_text(errors='replace'))
                match = re.search(r'(?:password|OPENCODE_SERVER_PASSWORD)[\s:=]+["\']?([^\s"\']+)', log, re.I)
                if match:
                    password = match.group(1)
                    try:
                        with socket.create_connection((lan, 4098), timeout=0.2):
                            break
                    except OSError:
                        pass
                time.sleep(0.1)
            if not password:
                raise RuntimeError('Could not capture generated scratch password privately')
            auth = {'username': 'opencode', 'password': password}
            http = f'http://{lan}:4098'
            blocked = run_dart(dict(auth, action='request', url=http))
            assert blocked.get('consentBlocked') is True
            proof['checks']['httpWithoutConsentBlocked'] = True
            approved = run_dart(dict(auth, action='request', url=http, approved=True))
            assert approved.get('ok') is True, 'Approved HTTP /api/info failed'
            proof['checks']['lanHttpApprovedApiInfo200'] = True
            cert_a, key_a, pin_a = certificate(scratch, 'certificate-a', lan)
            cert_b, key_b, pin_b = certificate(scratch, 'certificate-b', lan)
            assert pin_a != pin_b
            proxy = TLSProxy(cert_a, key_a)
            tls = f'https://{lan}:{proxy.port}'
            inspected = run_dart(dict(action='inspect', url=tls))
            assert inspected.get('fingerprint') == pin_a
            assert proxy.requests == proxy.authorization_requests == proxy.plaintext_http_requests == proxy.plaintext_authorization_requests == 0
            proof['checks']['inspectionShowsCorrectSha256WithoutHttpOrAuth'] = True
            untrusted = run_dart(dict(auth, action='request', url=tls))
            assert untrusted.get('ok') is False
            assert proxy.requests == proxy.authorization_requests == proxy.plaintext_http_requests == proxy.plaintext_authorization_requests == 0
            proof['checks']['untrustedSelfSignedBlockedBeforeHttpOrAuth'] = True
            accepted = run_dart(dict(auth, action='request', url=tls, pin=pin_a))
            assert accepted.get('ok') is True, f'Matching pin failed: {json.dumps(accepted)}; proxy counters {proxy.requests}/{proxy.authorization_requests}; proxy failure types {proxy.failures}'
            assert proxy.requests == proxy.authorization_requests == 1, f'Expected one matching request, saw {proxy.requests}/{proxy.authorization_requests}'
            proof['checks']['matchingPinApiInfo200'] = True
            restarted = run_dart(dict(auth, action='request', url=tls, pin=pin_a))
            assert restarted.get('ok') is True
            assert proxy.requests == proxy.authorization_requests == 2
            proof['checks']['samePinFreshDartProcessApiInfo200'] = True
            port = proxy.port
            proxy.close()
            proxy = TLSProxy(cert_b, key_b, port)
            mismatch = run_dart(dict(auth, action='request', url=tls, pin=pin_a))
            assert mismatch.get('ok') is False and mismatch.get('certificateChanged') is True
            assert proxy.requests == proxy.authorization_requests == proxy.plaintext_http_requests == proxy.plaintext_authorization_requests == 0
            proof['checks']['rotatedCertificateBlockedBeforeHttpOrAuth'] = True
            rechecked = run_dart(dict(action='inspect', url=tls))
            assert rechecked.get('fingerprint') == pin_b
            assert proxy.requests == proxy.authorization_requests == proxy.plaintext_http_requests == proxy.plaintext_authorization_requests == 0
            proof['checks']['recheckShowsNewSha256WithoutHttpOrAuth'] = True
            accepted_b = run_dart(dict(auth, action='request', url=tls, pin=pin_b))
            assert accepted_b.get('ok') is True
            assert proxy.requests == proxy.authorization_requests == 1
            proof['checks']['explicitNewPinApiInfo200'] = True
            proof['transport'] = 'lib/domain/server_trust.dart createServerHttpClient'
            proof['endpoint'] = '/api/info'
            proof['server'] = 'OpenCode 2.0.29 isolated scratch serve on port 4098'
            proof['network'] = 'PC non-loopback LAN IPv4'
            proof['restartScope'] = 'Fresh Dart process with same pin; profile persistence covered separately in Flutter tests'
            proof['passed'] = True
            # Kill exact owned process before its executable and XDG trees vanish.
            proxy.close()
            proxy = None
            server.terminate()
            server.wait(timeout=15)
            server = None
    except Exception as error:
        proof['failureType'] = type(error).__name__
        if isinstance(error, (AssertionError, RuntimeError)):
            proof['failureStage'] = str(error)
    finally:
        if proxy is not None:
            proxy.close()
        if server is not None and server.poll() is None:
            server.terminate()
            try:
                server.wait(timeout=15)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=10)
        if scratch_holder is not None:
            scratch_path = Path(scratch_holder.name)
            scratch_holder.cleanup()
            proof['cleanup'] = not scratch_path.exists()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps(proof, indent=2))
    return 0 if proof['passed'] and proof['cleanup'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
