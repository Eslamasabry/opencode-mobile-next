// The host scripts the app tells people to download are pinned to one
// published commit and checked against a SHA-256 before they run
// (slice-close-security). These tests keep that pin honest: a script edit
// that neither moves the pin nor records a pending update fails here, and
// so does a guide, a host script or a screen that tells anyone to pipe a
// download straight into a shell.

import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/setup_commands.dart';

const _scripts = {'front': HostScripts.front};

/// Files that show or run shell commands: the app's own copy, the guides at
/// the top of docs/, and every host-side script.
List<File> _commandSources() => [
  ...Directory('lib')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))
      .where((f) => !f.path.contains('app_localizations')),
  File('lib/l10n/app_en.arb'),
  ...Directory(
    'docs',
  ).listSync().whereType<File>().where((f) => f.path.endsWith('.md')),
  for (final dir in ['scripts', 'tool/host'])
    ...Directory(dir)
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => !f.path.contains('__pycache__')),
];

/// `curl … | bash`, `wget … | sh` and friends: a download run unchecked.
final _pipedToShell = RegExp(
  r'(curl|wget)\b[^\n|]*\|\s*(sudo\s+)?(ba|z|da)?sh\b',
);

void main() {
  test('the pin is a full commit hash, never a branch or a tag', () {
    expect(HostScripts.commit, matches(RegExp(r'^[0-9a-f]{40}$')));
    for (final script in _scripts.values) {
      expect(script.sha256, matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(
        script.url,
        'https://raw.githubusercontent.com/${HostScripts.repository}/'
        '${HostScripts.commit}/${script.path}',
      );
      expect(script.url, isNot(contains('/master/')));
      expect(script.url, isNot(contains('/main/')));
    }
  });

  for (final MapEntry(key: name, value: script) in _scripts.entries) {
    test('$name: the checkout holds the pinned bytes', () {
      final actual = sha256
          .convert(File(script.path).readAsBytesSync())
          .toString();
      expect(actual, script.sha256);
    });

    // The checksum is compared with the pinned commit's bytes, not the
    // working tree, so an unpublished script edit leaves the app's pin
    // valid: what people download is what this checksum describes.
    test('$name: the checksum matches the pinned commit\'s bytes', () {
      final result = Process.runSync('git', [
        'cat-file',
        'blob',
        '${HostScripts.commit}:${script.path}',
      ], stdoutEncoding: null);
      if (result.exitCode != 0) {
        markTestSkipped(
          'git has no ${HostScripts.commit} here (shallow clone?): '
          '${result.stderr}',
        );
        return;
      }
      expect(
        sha256.convert(result.stdout as List<int>).toString(),
        script.sha256,
      );
    });

    test('$name: the command checks the download before it is named', () {
      final lines = script.verifiedDownload.split('\n');
      expect(lines.first, startsWith('curl -fsSLo ${script.saveAs}.part'));
      expect(script.verifiedDownload, contains(script.url));
      expect(
        script.verifiedDownload,
        contains(
          "echo '${script.sha256}  ${script.saveAs}.part' | sha256sum -c - &&",
        ),
      );
      expect(lines.last, 'mv ${script.saveAs}.part ${script.saveAs}');
      expect(_pipedToShell.hasMatch(script.verifiedDownload), isFalse);
    });
  }

  test('install uses the official pinned OpenCode 2 package directly', () {
    final command = HostScripts.install(4747);
    expect(
      command,
      'npm install -g @opencode/cli@2.1.1 &&\n'
      'opencode2 serve --hostname 127.0.0.1 --port 4747',
    );
    expect(command, isNot(contains('ubuntu-opencode.sh')));
    expect(command, isNot(contains('opencode-ai')));
  });

  test('the front command checks, then runs the checked copy', () {
    final command = HostScripts.teamFront;
    expect(command, startsWith(HostScripts.front.verifiedDownload));
    expect(command, contains('python3 opencode-mobile-front.py '));
    expect(command, isNot(contains('python3 tool/')));
  });

  test('the guides show the same pinned, checked commands', () {
    final ubuntu = File('docs/ubuntu-host.md').readAsStringSync();
    expect(ubuntu, contains(HostScripts.install(4096)));
    final team = File('docs/ai-team-host.md').readAsStringSync();
    expect(team, contains(HostScripts.front.verifiedDownload));
    for (final guide in [ubuntu, team]) {
      expect(guide, isNot(contains('/master/scripts/')));
      expect(guide, isNot(contains('/master/tool/')));
    }
  });

  test('the repository script installs a pinned, checked OpenCode', () {
    final text = File('scripts/host/ubuntu-opencode.sh').readAsStringSync();
    // No installer piped into a shell, and no unpinned self-upgrade.
    expect(_pipedToShell.hasMatch(text), isFalse);
    expect(text, isNot(contains('opencode.ai/install')));
    expect(text, isNot(contains('" upgrade')));
    // The default release uses the app's reviewed official npm tarballs.
    expect(
      text,
      contains(
        'readonly OPENCODE_VERSION="'
        '${TermuxRuntime.openCode2.pinnedVersion}"',
      ),
    );
    expect(text, contains('https://registry.npmjs.org/@opencode'));
    for (final arch in ['X64', 'ARM64']) {
      expect(
        text,
        matches(RegExp('readonly OPENCODE_${arch}_SHA256="[0-9a-f]{64}"')),
      );
    }
    // The checksum is compared before the archive is unpacked.
    final check = text.indexOf(r'if [[ "$actual" != "$expected" ]]; then');
    final unpack = text.indexOf(r'tar -xzf "$WORK_DIR/$asset"');
    expect(check, greaterThan(0));
    expect(unpack, greaterThan(check));
  });

  test('nothing the app or its guides show pipes a download into a shell', () {
    final offenders = <String>[];
    for (final file in _commandSources()) {
      final lines = file.readAsLinesSync();
      for (final (i, line) in lines.indexed) {
        if (_pipedToShell.hasMatch(line)) {
          offenders.add('${file.path}:${i + 1}: ${line.trim()}');
        }
      }
    }
    expect(offenders, isEmpty);
  });
}
