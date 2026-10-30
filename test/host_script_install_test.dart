// scripts/host/ubuntu-opencode.sh installs one pinned OpenCode release and
// checks its SHA-256 before anything is unpacked (slice-script-pins). These
// tests run the real script with a fake `curl` (serving a small archive
// instead of npm) and a fake `systemctl`, so nothing touches the network
// or the machine's own services.

@TestOn('linux')
library;

import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

const _script = 'scripts/host/ubuntu-opencode.sh';

final _sha256Line = RegExp(
  r'^(readonly OPENCODE_(X64|ARM64)_SHA256=)"[0-9a-f]{64}"$',
  multiLine: true,
);

/// A system-wide opencode on the fixed PATH would be found before the fake.
final _shadowed = [
  '/usr/bin',
  '/bin',
].any((dir) => File('$dir/opencode').existsSync());

void main() {
  late Directory root;
  late Directory home;
  late Directory tmp;
  late File archive;
  late String archiveSha;

  String read(String name) {
    final file = File('${root.path}/$name');
    return file.existsSync() ? file.readAsStringSync() : '';
  }

  /// A release archive holding one `opencode` that reports [version].
  void buildArchive(String version) {
    final staging = Directory('${root.path}/staging')..createSync();
    Directory('${staging.path}/package/bin').createSync(recursive: true);
    const member = 'package/bin/opencode';
    final binary = File('${staging.path}/$member')
      ..writeAsStringSync('#!/bin/sh\necho "$version"\n');
    Process.runSync('chmod', ['755', binary.path]);
    final tar = Process.runSync('tar', [
      '-czf',
      archive.path,
      '-C',
      staging.path,
      member,
    ]);
    expect(tar.exitCode, 0, reason: '${tar.stderr}');
    archiveSha = sha256.convert(archive.readAsBytesSync()).toString();
  }

  /// A copy of the script whose recorded checksums are the fake archive's,
  /// standing in for the published release it would otherwise download.
  String scriptTrustingFakeArchive() {
    final text = File(_script).readAsStringSync();
    expect(_sha256Line.allMatches(text), hasLength(2));
    final copy = File('${root.path}/ubuntu-opencode.sh')
      ..writeAsStringSync(
        text.replaceAllMapped(_sha256Line, (m) => '${m[1]}"$archiveSha"'),
      );
    return copy.path;
  }

  /// An `opencode` already on the machine at ~/.local/bin, at [version].
  String existingOpenCode(String version) {
    final dir = Directory('${home.path}/.local/bin')
      ..createSync(recursive: true);
    final binary = File('${dir.path}/opencode')
      ..writeAsStringSync('#!/bin/sh\necho "$version"\n');
    Process.runSync('chmod', ['755', binary.path]);
    return binary.path;
  }

  ProcessResult run(String script, String command) => Process.runSync(
    'bash',
    [script, command],
    environment: {
      'HOME': home.path,
      'TMPDIR': tmp.path,
      'PATH': '${root.path}/bin:/usr/bin:/bin',
      'USER': 'tester',
      'FAKE_ROOT': root.path,
    },
    includeParentEnvironment: false,
  );

  String unitExecStart() => File(
    '${home.path}/.config/systemd/user/opencode-serve.service',
  ).readAsLinesSync().firstWhere((l) => l.startsWith('ExecStart='));

  String pinnedAsset() =>
      switch (Process.runSync('uname', ['-m']).stdout.toString().trim()) {
        'x86_64' || 'amd64' => 'cli-linux-x64-baseline-2.1.1.tgz',
        'aarch64' || 'arm64' => 'cli-linux-arm64-2.1.1.tgz',
        final other => throw StateError('no OpenCode build for $other'),
      };

  setUp(() {
    root = Directory.systemTemp.createTempSync('host-script-install-');
    home = Directory('${root.path}/home')..createSync();
    tmp = Directory('${root.path}/tmp')..createSync();
    archive = File('${root.path}/release.tar.gz');
    final bin = Directory('${root.path}/bin')..createSync();
    File('${bin.path}/curl').writeAsStringSync(r'''#!/bin/bash
out=""; url=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    --retry|--connect-timeout) shift 2 ;;
    -*) shift ;;
    *) url="$1"; shift ;;
  esac
done
echo "$url" >> "$FAKE_ROOT/curl.log"
cp "$FAKE_ROOT/release.tar.gz" "$out"
''');
    File('${bin.path}/systemctl').writeAsStringSync(
      '#!/bin/bash\necho "\$*" >> "\$FAKE_ROOT/systemctl.log"\n',
    );
    Process.runSync('chmod', [
      '755',
      '${bin.path}/curl',
      '${bin.path}/systemctl',
    ]);
    buildArchive(TermuxRuntime.openCode2.pinnedVersion);
  });

  tearDown(() => root.deleteSync(recursive: true));

  group(
    'ubuntu-opencode.sh',
    skip: _shadowed ? 'an opencode in /usr/bin would shadow the fake' : false,
    () {
      test('a download that does not match the checksum installs nothing', () {
        final result = run(_script, 'install');

        expect(result.exitCode, 1);
        expect(
          result.stderr,
          contains(
            'The OpenCode download does not match its published checksum, '
            'so nothing was installed.',
          ),
        );
        expect(result.stderr, contains('got:      $archiveSha'));
        expect(
          read('curl.log').trim(),
          'https://registry.npmjs.org/@opencode/'
          '${pinnedAsset().replaceFirst('-2.1.1.tgz', '')}/-/${pinnedAsset()}',
        );
        expect(
          File('${home.path}/.opencode/bin/opencode').existsSync(),
          isFalse,
        );
        expect(tmp.listSync(), isEmpty, reason: 'the download is cleaned up');
        expect(read('systemctl.log'), isNot(contains('enable')));
      });

      test('a matching download is installed and becomes the service', () {
        final result = run(scriptTrustingFakeArchive(), 'install');

        expect(result.exitCode, 0, reason: '${result.stderr}');
        final binary = '${home.path}/.opencode/bin/opencode';
        expect(File(binary).existsSync(), isTrue);
        expect(
          result.stderr,
          contains(
            '==> Installed OpenCode ${TermuxRuntime.openCode2.pinnedVersion} '
            'to $binary',
          ),
        );
        expect(unitExecStart(), startsWith('ExecStart=$binary serve '));
        expect(
          File(
            '${home.path}/.config/systemd/user/opencode-serve.service',
          ).readAsStringSync(),
          contains('UnsetEnvironment=OPENCODE_PASSWORD'),
        );
        expect(
          read('systemctl.log'),
          contains('--user enable --now opencode-serve'),
        );
        expect(tmp.listSync(), isEmpty);
      });

      test(
        'install refuses an existing unsupported server without changing it',
        () {
          final existing = existingOpenCode('1.19.0');
          final result = run(scriptTrustingFakeArchive(), 'install');
          expect(result.exitCode, 1);
          expect(result.stderr, contains('no longer supports'));
          expect(File(existing).readAsStringSync(), contains('1.19.0'));
          expect(read('curl.log'), isEmpty);
          expect(read('systemctl.log'), isNot(contains('enable')));
        },
      );

      test('install leaves an OpenCode that is already there alone', () {
        final existing = existingOpenCode('2.0.0');

        final result = run(scriptTrustingFakeArchive(), 'install');

        expect(result.exitCode, 0, reason: '${result.stderr}');
        expect(
          result.stdout,
          contains('OpenCode 2 is already installed: $existing'),
        );
        expect(read('curl.log'), isEmpty);
        expect(unitExecStart(), startsWith('ExecStart=$existing serve '));
      });

      test('update moves an older OpenCode 2 to the pinned release', () {
        existingOpenCode('2.0.28');

        final result = run(scriptTrustingFakeArchive(), 'update');

        expect(result.exitCode, 0, reason: '${result.stderr}');
        expect(
          result.stdout,
          contains(
            'Moving OpenCode 2.0.28 to the pinned '
            '${TermuxRuntime.openCode2.pinnedVersion}',
          ),
        );
        expect(
          unitExecStart(),
          startsWith('ExecStart=${home.path}/.opencode/bin/opencode serve '),
        );
        expect(
          read('systemctl.log'),
          contains('--user restart opencode-serve'),
        );
      });

      test('update does not migrate an existing Older OpenCode', () {
        final existing = existingOpenCode('1.19.0');
        final result = run(scriptTrustingFakeArchive(), 'update');
        expect(result.exitCode, 1);
        expect(result.stderr, contains('Older OpenCode is unchanged'));
        expect(read('curl.log'), isEmpty);
        expect(read('systemctl.log'), isNot(contains('restart')));
        expect(File(existing).readAsStringSync(), contains('1.19.0'));
      });

      test('update never downgrades a newer OpenCode', () {
        final existing = existingOpenCode('99.0.0');

        final result = run(scriptTrustingFakeArchive(), 'update');

        expect(result.exitCode, 0, reason: '${result.stderr}');
        expect(
          result.stdout,
          contains('OpenCode 99.0.0 is already at or past'),
        );
        expect(read('curl.log'), isEmpty);
        expect(unitExecStart(), startsWith('ExecStart=$existing serve '));
      });

      test('update refuses a mismatched download and keeps the old one', () {
        final existing = existingOpenCode('2.0.28');

        final result = run(_script, 'update');

        expect(result.exitCode, 1);
        expect(
          result.stderr,
          contains('does not match its published checksum'),
        );
        expect(
          File('${home.path}/.opencode/bin/opencode').existsSync(),
          isFalse,
        );
        expect(File(existing).existsSync(), isTrue);
        expect(read('systemctl.log'), isNot(contains('restart')));
      });
    },
  );
}
