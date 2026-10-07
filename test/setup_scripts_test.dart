// The setup prelude and component scripts, run in real shells on this
// machine: oc_download against a local HTTP server (127.0.0.1 only) that
// honours Range and sends slowly enough to watch, oc_apt_install against a
// fake apt-get, and a syntax check of every script. Skipped with a message
// when python3, curl or dash are missing.
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';

/// Serves one directory, honours `Range: bytes=N-` (206 / 416), logs each
/// request's range to stdout, and sends [rate] bytes a second.
const _server = r'''
import http.server, os, re, sys, time
root, rate = sys.argv[1], int(sys.argv[2])
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
        size, start = os.path.getsize(p), 0
        m = re.match(r'bytes=(\d+)-', self.headers.get('Range', ''))
        print('GET', m.group(1) if m else '-', flush=True)
        if m:
            start = int(m.group(1))
            if start >= size:
                self.send_response(416)
                self.send_header('Content-Range', 'bytes */%d' % size)
                self.send_header('Content-Length', '0')
                return self.end_headers()
            self.send_response(206)
            self.send_header('Content-Range', 'bytes %d-%d/%d' % (start, size - 1, size))
        else:
            self.send_response(200)
        self.send_header('Content-Length', str(size - start))
        self.end_headers()
        with open(p, 'rb') as f:
            f.seek(start)
            while True:
                chunk = f.read(max(1, rate // 20))
                if not chunk: break
                try:
                    self.wfile.write(chunk); self.wfile.flush()
                except Exception:
                    return
                time.sleep(0.05)
s = http.server.ThreadingHTTPServer(('127.0.0.1', 0), H)
print(s.server_address[1], flush=True)
s.serve_forever()
''';

bool _has(String tool) =>
    Process.runSync('sh', ['-c', 'command -v $tool']).exitCode == 0;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final missing = [
    for (final tool in ['python3', 'curl', 'dash', 'bash', 'sha256sum'])
      if (!_has(tool)) tool,
  ];
  final skip = missing.isEmpty ? null : 'needs ${missing.join(', ')}';

  late Directory dir;
  setUp(() async => dir = await Directory.systemTemp.createTemp('oc-setup-'));
  tearDown(() => dir.delete(recursive: true));

  Future<ProcessResult> sh(
    String script, {
    String shell = 'dash',
    Map<String, String>? env,
    // The progress poll is twice a second on the phone (asserted by the
    // progress tests, which pass their own env); every other test polls fast.
    bool slowPoll = false,
  }) => Process.run(shell, [
    '-c',
    'set -eu\n${withSetupPrelude(script)}',
  ], environment: env ?? (slowPoll ? null : {'OC_POLL_SECONDS': '0.05'}));

  group('oc_download', () {
    late Process server;
    late int port;
    final requests = <String>[];
    late File blob;
    late String sha;

    late Directory www;

    /// A server serving [www]; [rate] bytes a second (the progress tests
    /// throttle it, every other test takes it at full speed).
    Future<void> startServer(String rate) async {
      server = await Process.start('python3', ['-c', _server, www.path, rate]);
      final lines = server.stdout
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .asBroadcastStream();
      port = int.parse(await lines.first);
      lines.listen(requests.add);
    }

    setUp(() async {
      www = Directory('${dir.path}/www')..createSync();
      blob = File('${www.path}/blob.bin');
      // Deterministic bytes; 1.2 MB.
      blob.writeAsBytesSync(List.generate(1200000, (i) => (i * 31 + 7) % 251));
      sha = sha256.convert(blob.readAsBytesSync()).toString();
      requests.clear();
      await startServer('400000000');
    });
    tearDown(() => server.kill());

    List<(int, int)> bytesLines(String out) => [
      for (final line in out.split('\n'))
        if (line.startsWith('::oc bytes '))
          (int.parse(line.split(' ')[2]), int.parse(line.split(' ')[3])),
    ];

    for (final shell in ['dash', 'bash']) {
      test('$shell: reports real byte progress about twice a second and '
          'verifies the checksum', () async {
        // 1.2 MB at 1 MB/s is a little over a second.
        server.kill();
        requests.clear();
        await startServer('1000000');
        final target = '${dir.path}/out/blob.bin';
        final result = await sh(
          'oc_download http://127.0.0.1:$port/blob.bin $target $sha',
          shell: shell,
          slowPoll: true,
        );
        expect(result.exitCode, 0, reason: '${result.stderr}');
        final bytes = bytesLines(result.stdout as String);
        // ~1.2 s of download at two reports a second, plus the final one.
        expect(bytes.length, inInclusiveRange(3, 8), reason: '$bytes');
        expect(bytes.every((b) => b.$2 == 1200000), isTrue);
        final done = [for (final b in bytes) b.$1];
        expect(done, orderedEquals([...done]..sort()));
        expect(done.where((d) => d > 0 && d < 1200000), isNotEmpty);
        expect(done.last, 1200000);
        expect(File(target).lengthSync(), 1200000);
      }, skip: skip);
    }

    test('resumes a partial file with a Range request (curl -C -)', () async {
      final target = File('${dir.path}/out/blob.bin')
        ..parent.createSync(recursive: true)
        ..writeAsBytesSync(blob.readAsBytesSync().sublist(0, 700000));
      final result = await sh(
        'oc_download http://127.0.0.1:$port/blob.bin ${target.path} $sha',
      );
      expect(result.exitCode, 0, reason: '${result.stderr}');
      expect(requests, ['GET 700000']);
      final bytes = bytesLines(result.stdout as String);
      expect(bytes.first.$1, greaterThanOrEqualTo(700000));
      expect(sha256.convert(target.readAsBytesSync()).toString(), sha);
    }, skip: skip);

    test(
      'an intact file from an earlier run is not downloaded again',
      () async {
        final target = File('${dir.path}/blob.bin')
          ..writeAsBytesSync(blob.readAsBytesSync());
        final result = await sh(
          'oc_download http://127.0.0.1:$port/blob.bin ${target.path} $sha',
        );
        expect(result.exitCode, 0);
        expect(requests, isEmpty);
        expect(bytesLines(result.stdout as String), [(1200000, 1200000)]);
      },
      skip: skip,
    );

    test('a checksum mismatch fails and deletes the file', () async {
      final target = '${dir.path}/out/blob.bin';
      final result = await sh(
        'oc_download http://127.0.0.1:$port/blob.bin $target ${'0' * 64}\n'
        'echo not-reached',
      );
      expect(result.exitCode, isNot(0));
      expect(result.stdout, isNot(contains('not-reached')));
      expect(result.stderr, contains('did not match its checksum'));
      expect(File(target).existsSync(), isFalse);
    }, skip: skip);

    test('a whole-length file with the wrong bytes starts over', () async {
      final target = File('${dir.path}/blob.bin')
        ..writeAsBytesSync(List.filled(1200000, 0));
      final result = await sh(
        'oc_download http://127.0.0.1:$port/blob.bin ${target.path} $sha',
      );
      expect(result.exitCode, 0, reason: '${result.stderr}');
      expect(requests, ['GET -']);
      expect(sha256.convert(target.readAsBytesSync()).toString(), sha);
    }, skip: skip);

    test('a failed download keeps the partial file for next time', () async {
      final target = '${dir.path}/out/missing.bin';
      final result = await sh(
        'oc_download http://127.0.0.1:$port/missing.bin $target ${'0' * 64}',
      );
      expect(result.exitCode, 22, reason: 'curl -f on a 404');
      expect(result.stderr, contains('Download failed (curl exit 22)'));
    }, skip: skip);
  });

  group('oc_apt_install', () {
    late Directory bin;
    late Map<String, String> env;

    /// A fake apt-get that writes apt's status lines to fd 3 and logs its
    /// arguments; `install` fails once when FAIL_INSTALL_ONCE is set.
    setUp(() {
      bin = Directory('${dir.path}/bin')..createSync();
      final lists = Directory('${dir.path}/lists')..createSync();
      File('${bin.path}/apt-get')
        ..writeAsStringSync(r'''#!/bin/sh
echo "apt-get $*" >> "$APT_LOG"
case " $* " in
  *" update "*)
    echo 'dlstatus:1:40.5:Retrieving file 1 of 2' >&3
    echo 'dlstatus:2:100:Retrieving file 2 of 2' >&3
    touch "$OC_APT_LISTS/archive_noble_main_binary-amd64_Packages" ;;
  *" install "*)
    if [ -n "${FAIL_INSTALL_ONCE:-}" ] && [ ! -e "$APT_LOG.failed" ]; then
      touch "$APT_LOG.failed"
      echo 'E: Unable to locate package git' >&2
      exit 100
    fi
    echo 'Reading package lists...'
    echo 'dlstatus:1:0:Retrieving file 1 of 3' >&3
    echo 'dlstatus:2:33.3333:Retrieving file 2 of 3' >&3
    echo 'dlstatus:3:66.6667:Retrieving file 3 of 3' >&3
    echo 'pmstatus:dpkg-exec:0:Running dpkg' >&3
    echo 'pmstatus:libc6:amd64:12.5:Preparing libc6 (amd64)' >&3
    echo 'pmstatus:git:87.5:Installing git' >&3
    echo 'pmstatus:git:87.9:Installing git' >&3
    echo 'Setting up git (1:2.43.0-1ubuntu7) ...' ;;
esac
''')
        ..setLastModifiedSync(DateTime.now());
      Process.runSync('chmod', ['+x', '${bin.path}/apt-get']);
      File('${bin.path}/dpkg').writeAsStringSync(r'''#!/bin/sh
echo "dpkg $*" >> "$APT_LOG"
case "$1" in
  -s)
    echo "Package: $2"
    echo 'Status: install ok installed' ;;
esac
''');
      Process.runSync('chmod', ['+x', '${bin.path}/dpkg']);
      env = {
        'PATH': '${bin.path}:${Platform.environment['PATH']}',
        'APT_LOG': '${dir.path}/apt.log',
        'OC_APT_STAMP': '${dir.path}/stamp/apt-updated',
        'OC_APT_LISTS': lists.path,
      };
    });

    List<String> protocol(String out) => [
      for (final line in out.split('\n'))
        if (line.startsWith('::oc ')) line,
    ];

    test('apt status becomes stages and percents; lists refresh only when '
        'stale; dpkg is finished first', () async {
      final first = await sh('oc_apt_install git curl', env: env);
      expect(first.exitCode, 0, reason: '${first.stderr}');
      expect(protocol(first.stdout as String), [
        '::oc stage Updating package lists',
        '::oc percent 40',
        '::oc percent 100',
        '::oc stage Downloading packages',
        '::oc percent 0',
        '::oc percent 33',
        '::oc percent 66',
        '::oc stage Installing packages',
        '::oc percent 0',
        '::oc percent 12',
        '::oc percent 87',
      ]);
      // apt's own output stays log text.
      expect(first.stdout, contains('Setting up git'));
      final log = File(env['APT_LOG']!).readAsLinesSync();
      expect(log.first, 'dpkg --configure -a');
      expect(log[1], 'dpkg --audit');
      expect(log[2], contains('update'));
      expect(log[3], contains('-o APT::Status-Fd=3 install -y'));
      expect(log[3], endsWith('git curl'));
      expect(log.sublist(4), ['dpkg -s git', 'dpkg -s curl']);

      // Fresh lists: no second update.
      final second = await sh('oc_apt_install git', env: env);
      expect(second.exitCode, 0);
      expect(
        File(
          env['APT_LOG']!,
        ).readAsLinesSync().where((l) => l.contains('update')),
        hasLength(1),
      );
    }, skip: skip);

    test('an install that fails on stale lists refreshes them once and '
        'retries', () async {
      File(env['OC_APT_STAMP']!)
        ..parent.createSync(recursive: true)
        ..writeAsStringSync('');
      File('${env['OC_APT_LISTS']}/x_Packages').writeAsStringSync('');
      final result = await sh(
        'oc_apt_install git',
        env: {...env, 'FAIL_INSTALL_ONCE': '1'},
      );
      expect(result.exitCode, 0, reason: '${result.stderr}');
      // apt's own errors go to the log (stdout), where the details show them.
      expect(result.stdout, contains('Unable to locate package'));
      final log = File(env['APT_LOG']!).readAsLinesSync();
      expect(log.where((l) => l.contains(' install ')), hasLength(2));
      expect(log.where((l) => l.contains(' update ')), hasLength(1));
    }, skip: skip);

    test('apt failing fails the script with apt\'s code', () async {
      File(
        '${bin.path}/apt-get',
      ).writeAsStringSync('#!/bin/sh\necho "E: broken" >&2\nexit 100\n');
      final result = await sh('oc_apt_install git\necho not-reached', env: env);
      expect(result.exitCode, 100);
      expect(result.stdout, isNot(contains('not-reached')));
    }, skip: skip);
  });

  group('checks', () {
    test('the combined check script runs each check alone and reads back '
        'versions and failures', () async {
      final script = combinedCheckScript({
        'good': 'echo noise\necho 1.2.3',
        'bad': 'set -e\nfalse\necho unreachable',
        'exits': 'exit 3',
        'after': 'echo 9',
      });
      final result = await Process.run('dash', ['-c', script]);
      final parsed = parseCombinedChecks(result.stdout as String);
      expect(parsed, {
        'good': (ok: true, version: '1.2.3'),
        'bad': (ok: false, version: null),
        'exits': (ok: false, version: null),
        'after': (ok: true, version: '9'),
      });
    }, skip: skip);

    test('the OpenCode check fails on another version or runtime', () async {
      final bin = Directory('${dir.path}/bin')..createSync();
      File(
        '${bin.path}/opencode',
      ).writeAsStringSync('#!/bin/sh\necho 1.18.32\n');
      File(
        '${bin.path}/opencode2',
      ).writeAsStringSync('#!/bin/sh\necho "opencode2 v2.0.9"\n');
      Process.runSync('chmod', [
        '+x',
        '${bin.path}/opencode',
        '${bin.path}/opencode2',
      ]);
      Future<ProcessResult> check(Map<String, String> params) {
        // The Termux host's check; the in-app one wants the pinned native
        // program (test/opencode_native_install_test.dart).
        final component = setupComponents(
          lookupAppLocalizations(const Locale('en')),
          params: {'opencode': params},
          host: SetupHostKind.termux,
        ).firstWhere((c) => c.id == SetupComponentIds.openCode);
        return Process.run(
          'dash',
          ['-c', component.checkScript],
          environment: {'PATH': '${bin.path}:/usr/bin:/bin'},
        );
      }

      final pinned = await check({});
      expect(pinned.exitCode, 0);
      expect((pinned.stdout as String).trim(), '1.18.32');
      expect((await check({'version': '1.18.30'})).exitCode, isNot(0));
      // OpenCode 2 is there but not at the pinned 2.0.10: an update is due.
      expect((await check({'runtime': 'opencode2'})).exitCode, isNot(0));
      final two = await check({'runtime': 'opencode2', 'version': '2.0.9'});
      expect(two.exitCode, 0);
      expect((two.stdout as String).trim(), '2.0.9');
      File('${bin.path}/opencode2').deleteSync();
      expect(
        (await check({'runtime': 'opencode2', 'version': '2.0.9'})).exitCode,
        isNot(0),
      );
    }, skip: skip);
  });

  test('every script parses in dash and bash', () async {
    final l10n = lookupAppLocalizations(const Locale('en'));
    final scripts = <String, String>{'prelude': setupPrelude};
    for (final params in [
      const <String, Map<String, String>>{},
      const {
        'opencode': {'runtime': 'opencode2'},
      },
    ]) {
      for (final component in setupComponents(l10n, params: params)) {
        final key = '${component.id}${params.isEmpty ? '' : '-2'}';
        scripts['$key check'] = component.checkScript;
        if (component.installScript.isNotEmpty) {
          scripts['$key install'] = withSetupPrelude(component.installScript);
        }
        if (component.removeScript != null) {
          scripts['$key remove'] = component.removeScript!;
        }
      }
    }
    for (final entry in scripts.entries) {
      for (final shell in ['dash', 'bash']) {
        final result = await Process.run(shell, ['-n', '-c', entry.value]);
        expect(
          result.exitCode,
          0,
          reason: '${entry.key} in $shell: ${result.stderr}',
        );
      }
    }
  }, skip: skip);
}
