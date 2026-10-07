// The in-app Ubuntu's OpenCode install (slice-builtin-opencode-pin): the
// pinned native program, never npm. The real script runs in dash against a
// local HTTP server (127.0.0.1 only) with fake archives whose `opencode`
// is a small shell program; [root] keeps every path inside a temp folder.
//
// OC_SMOKE_REAL_OPENCODE=1 also runs the production script with the real
// pins against GitHub on this x86_64 PC (download, checksum, unpack,
// --version, serve probe) — a one-off smoke, not part of the suite.
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxRuntime;
import 'package:opencode_mobile/termux/opencode_ubuntu_setup.dart';

const _server = r'''
import http.server, os, sys
root = sys.argv[1]
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def path_of(self):
        p = os.path.join(root, self.path.lstrip('/'))
        return p if os.path.isfile(p) else None
    def do_HEAD(self):
        p = self.path_of()
        if not p: return self.send_error(404)
        self.send_response(200)
        self.send_header('Content-Length', str(os.path.getsize(p)))
        self.end_headers()
    def do_GET(self):
        p = self.path_of()
        if not p: return self.send_error(404)
        self.send_response(200)
        self.send_header('Content-Length', str(os.path.getsize(p)))
        self.end_headers()
        with open(p, 'rb') as f: self.wfile.write(f.read())
s = http.server.ThreadingHTTPServer(('127.0.0.1', 0), H)
print(s.server_address[1], flush=True)
s.serve_forever()
''';

/// A fake `opencode`: prints [version]; `serve` answers the health path
/// (or, when [starts] is false, says why and exits); `models` succeeds.
String _fakeProgram(
  String version, {
  bool starts = true,
  bool runs = true,
  bool activeFails = false,
}) =>
    '''#!/bin/sh
${runs ? '' : 'echo "cannot execute binary file: Exec format error" >&2; exit 126'}
case "\$1" in
  --version)
    ${activeFails ? '''case "\$0" in
      *.new/*) ;;
      *) exit 44 ;;
    esac''' : ''}
    echo '$version' ;;
  models) exit 0 ;;
  serve)
    ${starts ? '''exec python3 -c '
import http.server, sys
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        self.send_response(401 if self.headers.get("Authorization") is None else 200)
        self.end_headers()
http.server.HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
' "\$5"''' : '''echo "Error: Failed to start server: database is locked" >&2
    exit 1'''}
    ;;
esac
''';

bool _has(String tool) =>
    Process.runSync('sh', ['-c', 'command -v $tool']).exitCode == 0;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final missing = [
    for (final tool in ['python3', 'curl', 'dash', 'tar', 'sha256sum'])
      if (!_has(tool)) tool,
  ];
  final skip = missing.isEmpty ? null : 'needs ${missing.join(', ')}';
  final en = lookupAppLocalizations(const Locale('en'));

  late Directory dir;
  late Directory root;
  late Directory www;
  late Directory fakeBin;
  late Process server;
  late int port;

  setUp(() async {
    dir = await Directory.systemTemp.createTemp('oc-native-');
    root = Directory('${dir.path}/root')..createSync();
    www = Directory('${dir.path}/www')..createSync();
    fakeBin = Directory('${dir.path}/fakebin')..createSync();
    server = await Process.start('python3', ['-c', _server, www.path]);
    port = int.parse(
      await server.stdout
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .first,
    );
  });
  tearDown(() async {
    server.kill();
    await dir.delete(recursive: true);
  });

  void fakeUname(String machine) {
    final file = File('${fakeBin.path}/uname')
      ..writeAsStringSync('#!/bin/sh\necho $machine\n');
    Process.runSync('chmod', ['+x', file.path]);
  }

  /// An archive at www/[name] holding [member] = [program]; its SHA-256.
  String archive(String name, String member, String? program) {
    final stage = Directory('${dir.path}/stage-$name')..createSync();
    final names = <String>[];
    if (program != null) {
      final file = File('${stage.path}/$member')
        ..createSync(recursive: true)
        ..writeAsStringSync(program);
      Process.runSync('chmod', ['+x', file.path]);
      names.add(member);
    } else {
      File('${stage.path}/README').writeAsStringSync('nothing here\n');
      names.add('README');
    }
    final out = '${www.path}/$name';
    final tar = Process.runSync('tar', [
      '-czf',
      out,
      '-C',
      stage.path,
      ...names,
    ]);
    expect(tar.exitCode, 0, reason: '${tar.stderr}');
    return sha256.convert(File(out).readAsBytesSync()).toString();
  }

  OpenCodeAsset asset(String name, String sha, String member) =>
      (url: 'http://127.0.0.1:$port/$name', sha256: sha, member: member);

  Future<ProcessResult> install(
    TermuxRuntime runtime,
    ({OpenCodeAsset arm64, OpenCodeAsset x64}) assets, {
    String machine = 'aarch64',
  }) {
    fakeUname(machine);
    final script = SetupScripts.openCodeNativeInstall(
      runtime,
      assets: assets,
      root: root.path,
      probeSeconds: 150,
    );
    return Process.run(
      'dash',
      ['-c', withSetupPrelude(script)],
      environment: {
        'PATH': '${fakeBin.path}:/usr/bin:/bin',
        'TMPDIR': dir.path,
        // The download progress poll (0.5 s on the phone), shortened here.
        'OC_POLL_SECONDS': '0.05',
        'OC_PROBE_POLL': '0.1',
      },
    );
  }

  Future<ProcessResult> check(TermuxRuntime runtime) => Process.run(
    'dash',
    ['-c', SetupScripts.openCodeNativeCheck(runtime, root: root.path)],
    environment: {'PATH': '${root.path}/usr/local/bin:/usr/bin:/bin'},
  );

  String lastLine(ProcessResult result) =>
      '${result.stdout}${result.stderr}'.trimRight().split('\n').last;

  group('the pinned native install', () {
    test('the production script picks the pinned archive for each CPU, '
        'checks it before unpacking, and uses no npm', () {
      for (final runtime in TermuxRuntime.values) {
        final script = SetupScripts.openCodeNativeInstall(runtime);
        final pins = OpenCodePins.of(runtime);
        final arm = script
            .split('\n')
            .singleWhere((line) => line.trim().startsWith('aarch64|arm64)'));
        final x64 = script
            .split('\n')
            .singleWhere((line) => line.trim().startsWith('x86_64|amd64)'));
        expect(arm, contains(pins.arm64.url));
        expect(arm, contains(pins.arm64.sha256));
        expect(x64, contains(pins.x64.url));
        expect(x64, contains(pins.x64.sha256));
        expect(
          script.indexOf('oc_download "\$oc_url" "\$oc_file" "\$oc_sha"'),
          lessThan(script.indexOf('tar -xzf')),
        );
        // No npm command at all (a comment may name it).
        expect(
          script,
          isNot(matches(RegExp(r'^[^#\n]*\bnpm\s', multiLine: true))),
        );
        expect(script, isNot(matches(RegExp(r'curl[^\n]*\|\s*(ba)?sh'))));
      }
      // The pins scripts/host/ubuntu-opencode.sh carries, and baseline x64.
      expect(
        OpenCodePins.v1Arm64.sha256,
        '568461b7d4d8c19865c97e9a1102e613049c6039d01fe772154de873c1865840',
      );
      expect(
        OpenCodePins.v1X64.sha256,
        '763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce',
      );
      expect(OpenCodePins.v1X64.url, endsWith('linux-x64-baseline.tar.gz'));
      expect(
        OpenCodePins.v1Arm64.url,
        contains('/releases/download/v1.18.32/'),
      );
      expect(OpenCodePins.v2X64.url, contains('cli-linux-x64-baseline-2.0.10'));
      expect(OpenCodePins.v2Arm64.url, contains('cli-linux-arm64-2.0.10'));
      // Another version is not installed from an unpinned file.
      expect(
        SetupScripts.openCodeNativeInstall(
          TermuxRuntime.openCode1,
          version: '1.18.30',
        ),
        allOf(contains(OpenCodeInstallFailure.unpinned), contains('exit 64')),
      );
    });

    test('the in-app components use it; Termux keeps its shared text', () {
      final builtin = setupComponents(
        en,
      ).singleWhere((c) => c.id == SetupComponentIds.openCode);
      expect(
        builtin.installScript,
        SetupScripts.openCodeNativeInstall(TermuxRuntime.openCode1),
      );
      expect(
        builtin.checkScript,
        SetupScripts.openCodeNativeCheck(TermuxRuntime.openCode1),
      );
      final termux = setupComponents(
        en,
        host: SetupHostKind.termux,
      ).singleWhere((c) => c.id == SetupComponentIds.openCode);
      expect(termux.installScript, contains(openCodeUbuntuSetupScript));
    });

    test('npm installs that remain allow exactly their own package\'s '
        'install scripts', () {
      expect(
        openCodeUbuntuSetupScript,
        contains('--allow-scripts="\$main_package"'),
      );
      expect(openCodeUbuntuSetupScript, contains('main_package=opencode-ai'));
      expect(openCodeUbuntuSetupScript, contains('main_package=@opencode/cli'));
    });

    test('ARM64 gets the arm64 archive: installed, proven, linked; the old '
        'npm wrapper is replaced and the check passes afterwards', () async {
      final sha = archive('arm.tgz', 'opencode', _fakeProgram('1.18.32'));
      // What an older build left: npm's wrapper, reporting the right version.
      final npm = Directory('${root.path}/usr/local/lib/node_modules')
        ..createSync(recursive: true);
      final wrapper = File('${npm.path}/opencode-ai/bin/opencode')
        ..createSync(recursive: true)
        ..writeAsStringSync('#!/bin/sh\necho 1.18.32\n');
      Process.runSync('chmod', ['+x', wrapper.path]);
      Directory('${npm.path}/opencode-linux-arm64').createSync();
      Directory('${root.path}/usr/local/bin').createSync(recursive: true);
      Link('${root.path}/usr/local/bin/opencode').createSync(wrapper.path);

      expect(
        (await check(TermuxRuntime.openCode1)).exitCode,
        isNot(0),
        reason: 'the npm wrapper is not the pinned program: Continue redoes it',
      );

      final result = await install(TermuxRuntime.openCode1, (
        arm64: asset('arm.tgz', sha, 'opencode'),
        x64: asset('missing-x64.tgz', '0' * 64, 'opencode'),
      ));
      expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
      final out = result.stdout as String;
      expect(out, contains('::oc stage Checking that OpenCode starts'));
      expect(out, contains('::oc version 1.18.32'));
      expect(
        Link('${root.path}/usr/local/bin/opencode').targetSync(),
        '${root.path}/opt/opencode/bin/opencode',
      );
      expect(Directory('${npm.path}/opencode-ai').existsSync(), isFalse);
      expect(
        Directory('${npm.path}/opencode-linux-arm64').existsSync(),
        isFalse,
      );
      expect(Directory('${root.path}/opt/opencode.new').existsSync(), isFalse);
      expect(
        Directory('${root.path}/var/cache/oc-setup').listSync(),
        isEmpty,
        reason: 'the archive is deleted once installed',
      );

      final after = await check(TermuxRuntime.openCode1);
      expect(after.exitCode, 0, reason: '${after.stderr}');
      expect((after.stdout as String).trim(), '1.18.32');
    }, skip: skip);

    test('x86_64 gets the x64 archive; OpenCode 2 unpacks the npm '
        'platform package\'s program as opencode2', () async {
      final sha = archive(
        'x64.tgz',
        'package/bin/opencode',
        _fakeProgram('opencode v2.0.10'),
      );
      final result = await install(TermuxRuntime.openCode2, (
        arm64: asset('missing-arm.tgz', '0' * 64, 'package/bin/opencode'),
        x64: asset('x64.tgz', sha, 'package/bin/opencode'),
      ), machine: 'x86_64');
      expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
      expect(result.stdout, contains('::oc version 2.0.10'));
      expect(
        File('${root.path}/opt/opencode2/bin/opencode2').existsSync(),
        isTrue,
      );
      expect(
        Directory('${root.path}/opt/opencode2/package').existsSync(),
        isFalse,
      );
      final after = await check(TermuxRuntime.openCode2);
      expect(after.exitCode, 0, reason: '${after.stderr}');
      expect((after.stdout as String).trim(), '2.0.10');
    }, skip: skip);

    test('a checksum mismatch stops before anything is unpacked', () async {
      archive('arm.tgz', 'opencode', _fakeProgram('1.18.32'));
      final result = await install(TermuxRuntime.openCode1, (
        arm64: asset('arm.tgz', 'f' * 64, 'opencode'),
        x64: asset('arm.tgz', 'f' * 64, 'opencode'),
      ));
      expect(result.exitCode, isNot(0));
      expect(lastLine(result), contains('did not match its checksum'));
      expect(Directory('${root.path}/opt').existsSync(), isFalse);
      expect(Link('${root.path}/usr/local/bin/opencode').existsSync(), isFalse);
      final failure = describeSetupFailure(
        SetupJobComponent(
          id: 'opencode',
          state: 'failed',
          weight: 1,
          error: lastLine(result),
        ),
        '${result.stdout}${result.stderr}',
        'OpenCode',
        en,
      );
      expect(failure.component, en.phoneSetupErrorChecksum('OpenCode'));
    }, skip: skip);

    test(
      'post-activation failure restores the previous native program and link',
      () async {
        final previous = File('${root.path}/opt/opencode/bin/opencode')
          ..createSync(recursive: true)
          ..writeAsStringSync(_fakeProgram('1.18.32'));
        await Process.run('chmod', ['755', previous.path]);
        Directory('${root.path}/usr/local/bin').createSync(recursive: true);
        final link = Link('${root.path}/usr/local/bin/opencode')
          ..createSync(previous.path);
        final sha = archive(
          'activation.tgz',
          'opencode',
          _fakeProgram('1.18.32', activeFails: true),
        );
        final pin = asset('activation.tgz', sha, 'opencode');
        final result = await install(TermuxRuntime.openCode1, (
          arm64: pin,
          x64: pin,
        ));
        expect(result.exitCode, isNot(0));
        expect(
          lastLine(result),
          '[oc] OpenCode could not finish updating. Run setup again.',
        );
        expect(previous.readAsStringSync(), _fakeProgram('1.18.32'));
        expect(link.targetSync(), previous.path);
        expect((await check(TermuxRuntime.openCode1)).exitCode, 0);
        expect(
          File('${root.path}/opt/opencode.oc-pending').existsSync(),
          isFalse,
        );
        expect(File('${link.path}.oc-pending').existsSync(), isFalse);
      },
      skip: skip,
    );

    test('an archive without the program, a program that does not run and '
        'one that does not start each end with their own reason and keep '
        'the previous install', () async {
      final previous = File('${root.path}/opt/opencode/bin/opencode')
        ..createSync(recursive: true)
        ..writeAsStringSync('old');
      Future<ProcessResult> run(String? program) async {
        final name = 'a${program.hashCode}.tgz';
        final sha = archive(name, 'opencode', program);
        final pin = asset(name, sha, 'opencode');
        return install(TermuxRuntime.openCode1, (arm64: pin, x64: pin));
      }

      final empty = await run(null);
      expect(lastLine(empty), OpenCodeInstallFailure.noProgram);

      final broken = await run(_fakeProgram('1.18.32', runs: false));
      expect(lastLine(broken), OpenCodeInstallFailure.wontRun);
      expect(broken.stdout, contains('Exec format error'));

      final silent = await run(_fakeProgram('1.18.32', starts: false));
      expect(silent.exitCode, isNot(0));
      final said = '${silent.stdout}${silent.stderr}';
      expect(said, contains('database is locked'));
      expect(lastLine(silent), OpenCodeInstallFailure.noStart);

      expect(previous.readAsStringSync(), 'old');
      expect(Directory('${root.path}/opt/opencode.new').existsSync(), isFalse);

      for (final (result, words) in [
        (empty, en.phoneSetupErrorOpenCodeNoProgram),
        (broken, en.phoneSetupErrorOpenCodeWontRun),
        (silent, en.phoneSetupErrorOpenCodeNoStart),
      ]) {
        final failure = describeSetupFailure(
          SetupJobComponent(
            id: 'opencode',
            state: 'failed',
            weight: 1,
            error: lastLine(result),
          ),
          '${result.stdout}${result.stderr}',
          'OpenCode',
          en,
        );
        expect(failure.component, words);
        expect(failure.job, words);
        expect(words, isNot(contains('[oc]')));
      }
    }, skip: skip);

    test(
      'real pinned OpenCode on this PC (smoke)',
      () async {
        fakeUname('x86_64');
        for (final runtime in TermuxRuntime.values) {
          final script = SetupScripts.openCodeNativeInstall(
            runtime,
            root: root.path,
          );
          final result = await Process.run(
            'dash',
            ['-c', withSetupPrelude(script)],
            environment: {
              'PATH': '${fakeBin.path}:/usr/bin:/bin',
              'TMPDIR': dir.path,
              // The model refresh must not touch this PC's own OpenCode data.
              'HOME': dir.path,
              'XDG_DATA_HOME': '${dir.path}/data',
              'XDG_CACHE_HOME': '${dir.path}/cache',
              'XDG_STATE_HOME': '${dir.path}/state',
              'XDG_CONFIG_HOME': '${dir.path}/config',
            },
          );
          // The smoke's evidence (docs/qa/slice-builtin-opencode-pin-*).
          stdout.writeln(
            '--- ${runtime.wireName}\n${result.stdout}${result.stderr}',
          );
          expect(
            result.exitCode,
            0,
            reason: '${result.stdout}${result.stderr}',
          );
          expect(
            result.stdout,
            contains('::oc version ${runtime.pinnedVersion}'),
          );
          final after = await check(runtime);
          expect(after.exitCode, 0, reason: '${after.stderr}');
          expect((after.stdout as String).trim(), runtime.pinnedVersion);
        }
      },
      skip: Platform.environment['OC_SMOKE_REAL_OPENCODE'] == '1'
          ? skip
          : 'set OC_SMOKE_REAL_OPENCODE=1 (downloads ~150 MB)',
      timeout: const Timeout(Duration(minutes: 8)),
    );
  });
}
