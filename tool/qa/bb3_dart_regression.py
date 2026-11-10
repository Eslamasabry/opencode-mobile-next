"""Run exact BB3 removed-fix regressions, always restoring the source."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
FLUTTER = Path.home() / '.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
EVIDENCE = ROOT / 'docs/qa/BB3-2026-10-07'
CASES = [
    ('status', 'lib/builtin/builtin_linux.dart',
     "restorePhase: BuiltinServerRestorePhase.parse(map['restorePhase']),",
     'restorePhase: BuiltinServerRestorePhase.idle,',
     'test/builtin_linux_test.dart',
     'restoration status exposes fixed state and timeout reason'),
    ('metadata', 'lib/builtin/builtin_linux.dart',
     "if (restoreRecipe != null) 'restoreRecipe': restoreRecipe.toMap(),", '',
     'test/builtin_linux_test.dart',
     'authored start passes only canonical restoration metadata'),
    ('safe-error', 'lib/builtin/builtin_linux.dart',
     """if (restoreRecipe == null) rethrow;
      throw const BuiltinLinuxException(
        'The phone server could not start. Open setup and try again.',
        code: 'server_start_unavailable',
      );""", 'rethrow;',
     'test/builtin_linux_test.dart',
     'restoration start failures expose safe copy and way forward'),
    ('authored-start', 'lib/builtin/builtin_server.dart',
     """restoreRecipe: BuiltinServerRestoreRecipe(
          profileId: profile.id,
          runtime: BuiltinLinux.runtimeFor(profile.flavor),
        ),""", '',
     'test/builtin_server_test.dart',
     'writes the password, starts the profile runtime, waits'),
]

for label, source, before, after, test, name in CASES:
    path = ROOT / source
    original = path.read_text()
    if original.count(before) != 1:
        raise RuntimeError(f'{label}: expected one exact production fix')
    command = [str(ROOT / 'tool/qa/machine_lock.sh'), 'test', '--', str(FLUTTER),
               'test', '--no-pub', '--concurrency=1', '--reporter', 'expanded',
               test, '--plain-name', name]
    log = EVIDENCE / f'dart-red-{label}.txt'
    try:
        path.write_text(original.replace(before, after, 1))
        with log.open('w') as output:
            result = subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        text = log.read_text()
        if result.returncode != 1 or '[E]' not in text or 'Some tests failed.' not in text:
            raise RuntimeError(f'{label}: expected targeted assertion failure, see {log.name}')
        print(f'{label}: targeted red failure proved; source restored', flush=True)
    finally:
        path.write_text(original)

command = [str(ROOT / 'tool/qa/machine_lock.sh'), 'test', '--', str(FLUTTER),
           'test', '--no-pub', '--concurrency=1', '--reporter', 'expanded',
           'test/builtin_linux_test.dart', 'test/builtin_server_test.dart']
with (EVIDENCE / 'dart-restored.txt').open('w') as output:
    subprocess.run(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, check=True)
print('Restored affected Dart files passed', flush=True)
