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

  const fixturePayload = 'export const pinnedFixture = true;\n';
  late String fixtureCliSha;
  late String fixtureCodeTreeSha;

  String fixtureScript(String script) => isolated(script)
      .replaceAll(PaseoPhoneScripts.codeTreeSha256, fixtureCodeTreeSha)
      .replaceAll(PaseoPhoneScripts.cliSha256, fixtureCliSha)
      .replaceAll(
        PaseoPhoneScripts.payloadSha256,
        sha256.convert(utf8.encode(fixturePayload)).toString(),
      );

  Future<ProcessResult> runInstall({
    bool failNpm = false,
    bool tamperCli = false,
    bool tamperPayload = false,
    bool wrongLink = false,
    bool failLaunch = false,
    bool wrongVersion = false,
    bool activeFails = false,
  }) async {
    final bin = Directory('${temp.path}/oc/.local/node/bin')
      ..createSync(recursive: true);
    final node = File('${bin.path}/node')
      ..writeAsStringSync('''#!/bin/sh
if [ "\$1" = --version ]; then echo ${PaseoPhoneScripts.nodeVersion}; fi
''');
    final fixtureCli =
        '''#!/bin/sh
${activeFails ? r'''case "$0" in
  *.new/*) ;;
  *) exit 44 ;;
esac''' : ''}
printf executed >> '${temp.path}/cli-executed'
[ -z "\${BC3_PRIVATE_TOKEN:-}" ] || exit 91
case "\$*" in
  --version) echo ${wrongVersion ? '0.0.0' : PaseoPhoneScripts.version} ;;
  'daemon run --help')
    ${failLaunch ? 'echo private-cli-output >&2; exit 42' : 'echo "--home Local daemon home"'} ;;
  *) exit 92 ;;
esac
''';
    fixtureCliSha = sha256.convert(utf8.encode(fixtureCli)).toString();
    final fixtureManifest =
        '$fixtureCliSha  ${PaseoPhoneScripts.cliRelativePath}\n'
        '${sha256.convert(utf8.encode(fixturePayload))}  ${PaseoPhoneScripts.payloadRelativePath}\n';
    fixtureCodeTreeSha = sha256
        .convert(utf8.encode(fixtureManifest))
        .toString();
    final cliBase64 = base64.encode(
      utf8.encode('$fixtureCli${tamperCli ? '# changed\n' : ''}'),
    );
    final payloadBase64 = base64.encode(
      utf8.encode('$fixturePayload${tamperPayload ? '// changed\n' : ''}'),
    );
    final npm = File('${bin.path}/npm')
      ..writeAsStringSync('''#!/bin/sh
${failNpm ? 'echo private-npm-output >&2; exit 42' : ''}
while [ "\$1" != --prefix ]; do shift; done
shift
mkdir -p "\$1/node_modules/.bin" "\$1/node_modules/@getpaseo/cli/bin" "\$1/node_modules/@getpaseo/cli/dist"
printf '%s' '$cliBase64' | base64 -d > "\$1/${PaseoPhoneScripts.cliRelativePath}"
printf '%s' '$payloadBase64' | base64 -d > "\$1/${PaseoPhoneScripts.payloadRelativePath}"
chmod 755 "\$1/${PaseoPhoneScripts.cliRelativePath}"
ln -s '${wrongLink ? '../@getpaseo/cli/bin/other' : '../@getpaseo/cli/bin/paseo'}' "\$1/node_modules/.bin/paseo"
''');
    await Process.run('chmod', ['755', node.path, npm.path]);
    return Process.run(
      '/bin/sh',
      [
        '-c',
        '''
id() { echo 1000; }
uname() { echo aarch64; }
oc_stage() { printf '::oc stage %s\\n' "\$1"; }
oc_version() { printf '%s\\n' "\$1"; }
${fixtureScript(PaseoPhoneScripts.install(packageLock: lock))}
''',
      ],
      environment: {
        'HOME': '${temp.path}/oc',
        'BC3_PRIVATE_TOKEN': 'private-environment-sentinel',
      },
    );
  }

  Future<ProcessResult> runCheck() =>
      Process.run('/bin/sh', ['-c', fixtureScript(PaseoPhoneScripts.check)]);

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

  test('CLI pins match the independently verified registry artifact', () {
    // @getpaseo/cli 0.9.2 tarball checked against the shipped lock's SRI on
    // 2026-10-07. These values come from artifact inspection, not fixtures.
    final package =
        (jsonDecode(lock)
            as Map<String, dynamic>)['packages']['node_modules/@getpaseo/cli'];
    expect(package['bin']['paseo'], 'bin/paseo');
    expect(
      PaseoPhoneScripts.cliRelativePath,
      'node_modules/@getpaseo/cli/bin/paseo',
    );
    expect(
      PaseoPhoneScripts.cliSha256,
      '2b761f40e5a6416e4aaa733f38a47d5a4639b42cc1f88fc060256d2c4ed8cf94',
    );
    expect(
      PaseoPhoneScripts.payloadSha256,
      '8620d03c3e7e6776c3bfc4d22a8875f92209a563612fe7cda63dea7512f83c09',
    );
    expect(
      PaseoPhoneScripts.codeTreeSha256,
      '06d52bf05750cbd269668993f13ed0bbed2087feacbef37844007a44a1bb8ae5',
    );
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
    expect(result.stdout, endsWith('${PaseoPhoneScripts.version}\n'));
    expect(result.stdout, contains('::oc stage Checking Paseo checksum'));
    expect(result.stdout, contains('::oc stage Checking Paseo launch command'));
    expect(result.stdout, contains('::oc stage Checking Paseo version'));
    final checked = await runCheck();
    expect(checked.exitCode, 0, reason: '${checked.stderr}');
    final marker = File(
      isolated('${PaseoPhoneScripts.installDirectory}/.oc-package-lock-sha256'),
    );
    expect(marker.readAsStringSync(), PaseoPhoneScripts.packageLockSha256);
    expect(Link('${temp.path}/oc/.local/bin/paseo').existsSync(), isTrue);
  });

  for (final failure in ['entrypoint', 'payload', 'launcher']) {
    test(
      'changed $failure fails before execution or replacing good tree',
      () async {
        final old =
            File(isolated('${PaseoPhoneScripts.installDirectory}/previous'))
              ..parent.createSync(recursive: true)
              ..writeAsStringSync('previous');
        final result = await runInstall(
          tamperCli: failure == 'entrypoint',
          tamperPayload: failure == 'payload',
          wrongLink: failure == 'launcher',
        );
        expect(result.exitCode, isNot(0));
        expect(result.stdout, contains('::oc stage Checking Paseo checksum'));
        expect(result.stderr, contains('checksum check. Run setup again.'));
        expect(File('${temp.path}/cli-executed').existsSync(), isFalse);
        expect(old.readAsStringSync(), 'previous');
      },
    );
  }

  test(
    'launch command failure names its step and hides private output',
    () async {
      final result = await runInstall(failLaunch: true);
      expect(result.exitCode, isNot(0));
      expect(
        result.stdout,
        contains('::oc stage Checking Paseo launch command'),
      );
      expect(result.stderr, contains('launch command check. Run setup again.'));
      expect(
        '${result.stdout}${result.stderr}',
        isNot(contains('private-cli-output')),
      );
      expect(
        Directory(isolated(PaseoPhoneScripts.installDirectory)).existsSync(),
        isFalse,
      );
    },
  );

  test('version failure names its step before replacing the tree', () async {
    final result = await runInstall(wrongVersion: true);
    expect(result.exitCode, isNot(0));
    expect(result.stdout, contains('::oc stage Checking Paseo version'));
    expect(result.stderr, contains('version check. Run setup again.'));
    expect(
      Directory(isolated(PaseoPhoneScripts.installDirectory)).existsSync(),
      isFalse,
    );
  });

  test('installed marker cannot hide modified CLI implementation', () async {
    expect((await runInstall()).exitCode, 0);
    final execution = File('${temp.path}/cli-executed')..deleteSync();
    File(
      isolated(
        '${PaseoPhoneScripts.installDirectory}/node_modules/@getpaseo/cli/dist/daemon.js',
      ),
    ).writeAsStringSync('throw new Error("changed");');
    final result = await runCheck();
    expect(result.exitCode, isNot(0));
    expect(execution.existsSync(), isFalse);
  });

  test('installed marker cannot hide a changed outer launcher', () async {
    expect((await runInstall()).exitCode, 0);
    final execution = File('${temp.path}/cli-executed')..deleteSync();
    final launcher = Link('${temp.path}/oc/.local/bin/paseo')..deleteSync();
    launcher.createSync(
      isolated(
        '${PaseoPhoneScripts.installDirectory}/${PaseoPhoneScripts.cliRelativePath}',
      ),
    );
    final result = await runCheck();
    expect(result.exitCode, isNot(0));
    expect(execution.existsSync(), isFalse);
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

  for (final activeFails in [false, true]) {
    test(
      activeFails
          ? 'activated Paseo failure restores the previous tree and launcher'
          : 'successful Paseo update retains the previous good generation',
      () async {
        expect((await runInstall()).exitCode, 0);
        final directory = isolated(PaseoPhoneScripts.installDirectory);
        final cli = File('$directory/${PaseoPhoneScripts.cliRelativePath}');
        final previousCode = cli.readAsStringSync();
        final launcher = Link('${temp.path}/oc/.local/bin/paseo');
        final previousLink = launcher.targetSync();
        final result = await runInstall(activeFails: activeFails);
        if (activeFails) {
          expect(result.exitCode, isNot(0));
          expect(
            result.stderr,
            contains('Paseo could not finish updating. Run setup again.'),
          );
          expect(cli.readAsStringSync(), previousCode);
        } else {
          expect(result.exitCode, 0, reason: '${result.stderr}');
          expect(
            File(
              '$directory.oc-good/${PaseoPhoneScripts.cliRelativePath}',
            ).readAsStringSync(),
            previousCode,
          );
          expect(Link('${launcher.path}.oc-good').targetSync(), previousLink);
        }
        expect(launcher.targetSync(), previousLink);
        expect(File('$directory.oc-pending').existsSync(), isFalse);
        expect(File('${launcher.path}.oc-pending').existsSync(), isFalse);
      },
    );
  }

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
