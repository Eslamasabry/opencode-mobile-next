#!/usr/bin/env python3
"""Run one focused Dart regression at a time; restore exact source bytes."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
FLUTTER = Path.home() / '.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
CASES = [
    ('strict-integer', 'lib/builtin/builtin_linux.dart',
     "map[key] is int && (map[key] as int) >= 0 ? map[key] as int : null;",
     "map[key] is num ? (map[key] as num).toInt() : null;",
     'test/builtin_idle_policy_test.dart',
     'supporting status requires every correctly typed admission field'),
    ('identity', 'lib/builtin/builtin_linux.dart',
     "if (RegExp(r'^[A-Za-z0-9_-]{1,80}$').firstMatch(profileId)?.end !=\n            profileId.length ||\n        generation <= 0) {",
     "if (false) {",
     'test/builtin_idle_policy_test.dart',
     'unsafe owners and nonpositive generations never reach the native channel'),
    ('first-read-fence', 'lib/builtin/phone_server_idle.dart',
     'var status = await linux.status();\n      if (!_current(owner, epoch)) return;',
     'var status = await linux.status();',
     'test/phone_server_idle_test.dart',
     'owner or foreground change after the first read cancels continuation'),
    ('server-return-fence', 'lib/builtin/phone_server_idle.dart',
     'expectedIdleGeneration: expected,\n        );\n        if (!_current(owner, epoch)) return;',
     'expectedIdleGeneration: expected,\n        );',
     'test/phone_server_idle_test.dart',
     'owner change during native server restore cannot start its helper'),
    ('previous-helper-intent', 'lib/builtin/phone_server_idle.dart',
     'if (helperWasStopped) {', 'if (true) {',
     'test/phone_server_idle_test.dart',
     'prior stopped helper is never resumed and completion retains its positive generation'),
    ('fresh-stop-generation', 'lib/builtin/phone_server_idle.dart',
     'status = await linux.status();\n      if (!_current(owner, epoch)) return;\n      _requireLive(status, expected);',
     'status = await linux.status();\n      if (!_current(owner, epoch)) return;',
     'test/phone_server_idle_test.dart',
     'Stop or changed generation during helper readiness prevents native completion'),
]


def run(test, logfile, name=None):
    command = ['tool/qa/machine_lock.sh', 'test', '--', str(FLUTTER),
               'test', '--no-pub', '--concurrency=1', '--reporter', 'expanded', test]
    if name:
        command += ['--plain-name', name]
    env = dict(os.environ, OC_TEST_SLOTS='1')
    with logfile.open('w') as output:
        return subprocess.run(command, cwd=ROOT, env=env,
                              stdout=output, stderr=subprocess.STDOUT).returncode


results = []
for label, relative, before, after, test, name in CASES:
    path = ROOT / relative
    original = path.read_bytes()
    source = original.decode()
    if source.count(before) != 1:
        raise RuntimeError('non-unique mutation: ' + label)
    logfile = OUT / ('dart-red-' + label + '.txt')
    try:
        path.write_text(source.replace(before, after, 1))
        code = run(test, logfile, name)
    finally:
        path.write_bytes(original)
    log = logfile.read_text()
    behavioral = code != 0 and 'Expected:' in log and 'Actual:' in log
    behavioral = behavioral and 'Compilation failed' not in log
    results.append({'case': label, 'exit': code, 'behavioralFailure': behavioral,
                    'exactBytesRestored': path.read_bytes() == original})
    print(json.dumps(results[-1]), flush=True)
    if not behavioral:
        raise RuntimeError('invalid red proof: ' + label)

for test, label in [('test/builtin_idle_policy_test.dart', 'bridge'),
                    ('test/phone_server_idle_test.dart', 'resume')]:
    code = run(test, OUT / ('dart-final-' + label + '.txt'))
    print(json.dumps({'restoredFile': test, 'exit': code}), flush=True)
    if code:
        raise RuntimeError('restored tests failed: ' + label)
(OUT / 'dart-red-summary.json').write_text(json.dumps(results, indent=2) + '\n')
