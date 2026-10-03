import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/paseo_scripts.dart';

void main() {
  final lock = File(PaseoPhoneScripts.packageLockAsset).readAsStringSync();
  late Directory temp;
  setUp(() => temp = Directory.systemTemp.createTempSync('paseo-phone-'));
  tearDown(() => temp.deleteSync(recursive: true));

  String isolated(String script) =>
      script.replaceAll('/home/oc', '${temp.path}/oc');

  Future<ProcessResult> runInstall({bool failNpm = false}) async {
    final bin = Directory('${temp.path}/oc/.local/node/bin')
      ..createSync(recursive: true);
    final node = File('${bin.path}/node')
      ..writeAsStringSync('''#!/bin/sh
if [ "\$1" = --version ]; then echo ${PaseoPhoneScripts.nodeVersion}; fi
''');
    final npm = File('${bin.path}/npm')
      ..writeAsStringSync('''#!/bin/sh
${failNpm ? 'echo private-npm-output >&2; exit 42' : ''}
while [ "\$1" != --prefix ]; do shift; done
shift
mkdir -p "\$1/node_modules/.bin"
printf '#!/bin/sh\\necho ${PaseoPhoneScripts.version}\\n' > "\$1/node_modules/.bin/paseo"
chmod 755 "\$1/node_modules/.bin/paseo"
''');
    await Process.run('chmod', ['755', node.path, npm.path]);
    return Process.run(
      '/bin/sh',
      [
        '-c',
        '''
id() { echo 1000; }
uname() { echo aarch64; }
oc_stage() { :; }
oc_version() { printf '%s\\n' "\$1"; }
${isolated(PaseoPhoneScripts.install(packageLock: lock))}
''',
      ],
      environment: {'HOME': '${temp.path}/oc'},
    );
  }

  test('shipped lock freezes every HTTPS tarball with SRI', () {
    expect(
      sha256.convert(utf8.encode(lock)).toString(),
      PaseoPhoneScripts.packageLockSha256,
    );
    final packages =
        (jsonDecode(lock) as Map<String, dynamic>)['packages']
            as Map<String, dynamic>;
    expect(packages['node_modules/@getpaseo/cli']['version'], '0.9.2');
    for (final entry in packages.entries.where(
      (entry) => entry.key.isNotEmpty,
    )) {
      expect(
        entry.value['resolved'],
        startsWith('https://registry.npmjs.org/'),
      );
      expect(entry.value['integrity'], startsWith('sha512-'));
    }
    for (final arch in ['arm64', 'x64']) {
      expect(
        packages.entries
            .singleWhere((e) => e.key.endsWith('/sherpa-onnx-linux-$arch'))
            .value['version'],
        '1.12.28',
      );
    }
  });

  test('modified lock is rejected before constructing executable script', () {
    expect(
      () => PaseoPhoneScripts.install(packageLock: '$lock '),
      throwsFormatException,
    );
  });

  test('lock transport stays below Linux per-argument limit', () {
    expect(
      utf8.encode(PaseoPhoneScripts.install(packageLock: lock)).length,
      lessThan(64 * 1024),
    );
  });

  test('all install and check scripts parse as POSIX shell', () async {
    for (final script in [
      PaseoPhoneScripts.nodeInstall,
      PaseoPhoneScripts.nodeCheck,
      PaseoPhoneScripts.install(packageLock: lock),
      PaseoPhoneScripts.check,
    ]) {
      final file = File('${temp.path}/script.sh')..writeAsStringSync(script);
      final result = await Process.run('/bin/sh', ['-n', file.path]);
      expect(result.exitCode, 0, reason: result.stderr.toString());
    }
  });

  test('fake installer completes under oc and replaces the launcher', () async {
    final result = await runInstall();
    expect(result.exitCode, 0, reason: result.stderr.toString());
    expect(result.stdout, '${PaseoPhoneScripts.version}\n');
    final marker = File(
      isolated('${PaseoPhoneScripts.installDirectory}/.oc-package-lock-sha256'),
    );
    expect(marker.readAsStringSync(), PaseoPhoneScripts.packageLockSha256);
    expect(Link('${temp.path}/oc/.local/bin/paseo').existsSync(), isTrue);
  });

  test('failed npm retains previous install and hides npm output', () async {
    final old = File(
      isolated('${PaseoPhoneScripts.installDirectory}/previous'),
    );
    old.parent.createSync(recursive: true);
    old.writeAsStringSync('previous');
    final result = await runInstall(failNpm: true);
    expect(result.exitCode, isNot(0));
    expect(result.stderr, contains('Check the connection and retry'));
    expect(
      '${result.stdout}${result.stderr}',
      isNot(contains('private-npm-output')),
    );
    expect(old.readAsStringSync(), 'previous');
    expect(
      Directory('${temp.path}/oc/.cache/oc-paseo-install').existsSync(),
      isFalse,
    );
  });

  test('root is refused before installing', () async {
    final result = await Process.run(
      '/bin/sh',
      ['-c', 'id() { echo 0; }; ${isolated(PaseoPhoneScripts.nodeInstall)}'],
      environment: {'HOME': '${temp.path}/oc'},
    );
    expect(result.exitCode, isNot(0));
    expect(result.stderr, contains('private Linux user'));
    expect(Directory('${temp.path}/oc/.local').existsSync(), isFalse);
  });

  test('Node downloads exactly the pinned artifact for each ABI', () async {
    for (final entry in {
      'aarch64': ('arm64', PaseoPhoneScripts.nodeArm64Sha256),
      'x86_64': ('x64', PaseoPhoneScripts.nodeX64Sha256),
    }.entries) {
      final result = await Process.run(
        '/bin/sh',
        [
          '-c',
          '''
id() { echo 1000; }
uname() { echo ${entry.key}; }
oc_stage() { :; }
oc_download() { printf '%s\\n%s\\n' "\$1" "\$3"; return 23; }
${isolated(PaseoPhoneScripts.nodeInstall)}
''',
        ],
        environment: {'HOME': '${temp.path}/oc'},
      );
      expect(result.exitCode, 23);
      expect(result.stdout, contains('linux-${entry.value.$1}.tar.gz'));
      expect(result.stdout, contains(entry.value.$2));
    }
  });
}
