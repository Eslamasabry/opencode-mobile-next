// Claude Code on this phone: `~/.oc/claude.sh` and its Dart runtime.
//
// The shell half runs the embedded script with bash on this machine against
// fixture paths: a temp HOME and $PREFIX, a `proot-distro` stub that runs the
// "inside Ubuntu" command natively, a served Node.js tarball whose node, npm,
// paseo and claude are stubs, and a paseo stub that answers the health check.
// No Termux and no device are involved; docs/claude-on-this-phone.md lists
// the on-device checks that remain.
@Timeout(Duration(minutes: 4))
library;

import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/local_agent_runtime.dart';

const _nodeVersion = 'v24.0.0';

const _prootStub = r'''#!/bin/bash
# proot-distro stub: records its own arguments (the test asserts the daemon
# password never appears there) and runs the command natively.
printf 'proot-distro %s\n' "$*" >> "$OC_TEST_ROOT/proot-calls.log"
[ "${1:-}" = login ] || exit 0
shift
workdir=''
while [ $# -gt 0 ]; do
  case "$1" in
    --work-dir) workdir="$2"; shift 2 ;;
    --) shift; break ;;
    *) shift ;;
  esac
done
[ -f "$OC_TEST_ROOT/proot-broken" ] && exit 1
if [ -n "$workdir" ]; then mkdir -p "$workdir"; cd "$workdir"; fi
exec "$@"
''';

const _nodeStub = r'''#!/bin/bash
case "${1:-}" in --version) echo "__VERSION__" ;; esac
exit 0
''';

const _npmStub = r'''#!/bin/bash
# npm stub: `install -g … --prefix P … <spec>` drops a paseo or claude stub.
printf 'npm %s\n' "$*" >> "$OC_TEST_ROOT/npm-calls.log"
prefix=''
spec=''
while [ $# -gt 0 ]; do
  case "$1" in
    --prefix) prefix="$2"; shift 2 ;;
    --cache) shift 2 ;;
    *) spec="$1"; shift ;;
  esac
done
if [ -f "$OC_TEST_ROOT/npm-fail" ]; then
  cat "$OC_TEST_ROOT/npm-fail"
  exit 1
fi
mkdir -p "$prefix/bin"
case "$spec" in
  @getpaseo/cli@*) cp "$OC_TEST_ROOT/paseo-stub" "$prefix/bin/paseo" ;;
  @anthropic-ai/claude-code@*) cp "$OC_TEST_ROOT/claude-stub" "$prefix/bin/claude" ;;
esac
echo "added 1 package"
''';

const _paseoStub = r'''#!/bin/bash
# Behaves like Paseo 0.11 and later: the old launch flags are refused (the
# real daemon prints "--listen was removed" and exits), settings are set with
# `daemon config set <key> <value> --home <path>`, and `daemon run` runs the
# daemon in the foreground from that config.
if [ "${1:-}" = --version ]; then echo '0.8.0'; exit 0; fi
printf '%s\n' "$*" >> "$OC_TEST_ROOT/paseo-args.log"
case "${1:-}" in
  daemon) ;;
  *) echo 'Error: --listen was removed. Use paseo daemon config set daemon.listen <value> --home <path>, then start or restart.' >&2; exit 1 ;;
esac
for arg in "$@"; do
  case "$arg" in --foreground|--listen|--no-relay|--no-web-ui|--no-inject-mcp|--port|--relay|--web-ui|--no-mcp|--hostnames)
    echo "Error: $arg was removed." >&2; exit 1 ;;
  esac
done
if [ "${2:-}" = config ] && [ "${3:-}" = set ]; then
  printf '%s=%s\n' "$4" "$5" >> "$OC_TEST_ROOT/paseo-config.log"
  echo '{"action":"saved"}'
  exit 0
fi
[ "${2:-}" = run ] || { echo 'Error: unknown command' >&2; exit 1; }
printf '%s' "${PASEO_PASSWORD:-}" > "$OC_TEST_ROOT/paseo-env-password"
listen=$(grep '^daemon.listen=' "$OC_TEST_ROOT/paseo-config.log" | tail -1 | cut -d= -f2)
echo "paseo listening on $listen"
[ -f "$OC_TEST_ROOT/paseo-silent" ] && exec sleep 600
exec python3 - "$listen" <<'PY'
import sys, http.server
host, port = sys.argv[1].rsplit(':', 1)
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b'{"status":"ok","version":"0.8.0"}'
        self.send_response(200 if self.path == '/api/health' else 404)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *a): pass
http.server.HTTPServer((host, int(port)), H).serve_forever()
PY
''';

const _claudeStub = r'''#!/bin/bash
case "${1:-}" in
  --version) echo '2.1.278 (Claude Code)' ;;
  --help) printf 'Usage: claude\n\nCommands:\n  auth       Manage sign-in\n' ;;
esac
''';

const _wakeStub = r'''#!/bin/bash
echo "$(basename "$0")" >> "$OC_TEST_ROOT/wake.log"
''';

class _Fixture {
  _Fixture._(this.root, this.server, this.port);

  final Directory root;
  final HttpServer server;

  /// The port the daemon stub listens on.
  final int port;
  int downloads = 0;
  bool serveNotFound = false;
  late List<int> tarball;

  String get home => '${root.path}/home';
  String get prefix => '${root.path}/prefix';
  String get stubs => '${root.path}/stubs';
  String get ocDir => '$home/.oc';
  String get claudeDir => '$ocDir/claude';
  String get script => '$ocDir/claude.sh';
  String get ubuntu => '${root.path}/ubuntu';
  String get ubuntuHome => '$ubuntu/root';
  String get nodeDir => '$ubuntu/opt/oc-node';
  String get agentsDir => '$ubuntu/opt/oc-agents';
  String get rootfs =>
      '$prefix/var/lib/proot-distro/containers/opencode-ubuntu/rootfs';

  static Future<_Fixture> create({bool ubuntu = true}) async {
    // tmpfs where there is one: the script moves many small files, and a
    // busy disk made each verb seconds long.
    final shm = Directory('/dev/shm');
    final root = (shm.existsSync() ? shm : Directory.systemTemp).createTempSync(
      'oc-claude-',
    );
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    // A free port for the daemon stub: bind, read, release.
    final probe = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final port = probe.port;
    await probe.close();
    final fx = _Fixture._(root, server, port);
    server.listen(fx._handle);
    for (final dir in [fx.home, fx.stubs, fx.ocDir, fx.ubuntuHome]) {
      Directory(dir).createSync(recursive: true);
    }
    if (ubuntu) Directory(fx.rootfs).createSync(recursive: true);
    _executable(fx.script, TermuxBridge.localAgentsScriptForTesting());
    _executable('${fx.stubs}/proot-distro', _prootStub);
    // Plenty of room, whatever this machine's disk holds; the full-phone
    // test swaps in a small one.
    _executable(
      '${fx.stubs}/df',
      '#!/bin/bash\necho "Filesystem 1024-blocks Used Available Capacity '
          'Mounted on"\necho "/dev/x 99999999 1 90000000 1% /"\n',
    );
    _executable('${fx.stubs}/termux-wake-lock', _wakeStub);
    _executable('${fx.stubs}/termux-wake-unlock', _wakeStub);
    _executable('${root.path}/paseo-stub', _paseoStub);
    _executable('${root.path}/claude-stub', _claudeStub);
    fx.tarball = fx._buildTarball();
    fx.writePins();
    return fx;
  }

  static void _executable(String path, String content) {
    File(path)
      ..createSync(recursive: true)
      ..writeAsStringSync(content);
    Process.runSync('chmod', ['755', path]);
  }

  List<int> _buildTarball() {
    final name = 'node-$_nodeVersion-linux-x64';
    final stage = '${root.path}/stage/$name';
    _executable(
      '$stage/bin/node',
      _nodeStub.replaceAll('__VERSION__', _nodeVersion),
    );
    _executable('$stage/bin/npm', _npmStub);
    final archive = '${root.path}/$name.tar.gz';
    final result = Process.runSync('tar', [
      '-czf',
      archive,
      '-C',
      '${root.path}/stage',
      name,
    ]);
    expect(result.exitCode, 0, reason: '${result.stderr}');
    return File(archive).readAsBytesSync();
  }

  void writePins({
    String? sha,
    String? baseUrl,
    String? version,
    String? claude,
  }) {
    File('$ocDir/claude-pins').writeAsStringSync(
      TermuxBridge.localAgentsPinsFile({
        'node_version': version ?? _nodeVersion,
        'node_base_url': baseUrl ?? 'http://127.0.0.1:${server.port}/dist',
        'node_sha256_arm64': '0' * 64,
        'node_sha256_x64': sha ?? sha256.convert(tarball).toString(),
        'paseo_version': '0.8.0',
        'claude_version': claude ?? '2.1.278',
      }),
    );
  }

  void _handle(HttpRequest request) {
    final response = request.response;
    final expected = '/dist/$_nodeVersion/node-$_nodeVersion-linux-x64.tar.gz';
    if (serveNotFound || request.uri.path != expected) {
      response.statusCode = 404;
    } else {
      downloads++;
      response.add(tarball);
    }
    response.close();
  }

  Map<String, String> get environment => {
    ...Platform.environment,
    'HOME': home,
    'PREFIX': prefix,
    'PATH': '$stubs:${Platform.environment['PATH']}',
    'OC_TEST_ROOT': root.path,
    'OC_CLAUDE_ROOTFS': '',
    'OC_CLAUDE_NODE_DIR': nodeDir,
    'OC_CLAUDE_AGENTS_DIR': agentsDir,
    'OC_CLAUDE_UBUNTU_HOME': ubuntuHome,
    // The stub runs commands as the host user, which therefore already
    // exists; no `useradd` runs on the test machine.
    'OC_CLAUDE_USER': Platform.environment['USER'] ?? 'root',
    'OC_CLAUDE_PORT': '$port',
    'OC_CLAUDE_ARCH': 'x86_64',
    'OC_CLAUDE_HEALTH_TIMEOUT': '8',
    // A poll tick is a tenth of a second here; the timeouts count ticks.
    'OC_CLAUDE_POLL': '0.1',
    'OC_CLAUDE_ALLOW_LOOPBACK_PINS': '1',
  };

  Future<ProcessResult> verb(List<String> args, {Map<String, String>? env}) =>
      Process.run(
        'bash',
        [script, ...args],
        environment: {...environment, ...?env},
        workingDirectory: root.path,
      );

  Future<LocalAgentStatus> status() async {
    final result = await verb(['status']);
    expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
    return LocalAgentStatus.parse(result.stdout as String);
  }

  Future<LocalAgentStatus> waitIdle() async {
    for (var i = 0; i < 400; i++) {
      final current = await status();
      if (!current.busy) return current;
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    fail('a verb stayed busy');
  }

  Future<LocalAgentStatus> run(List<String> args) async {
    await verb(args);
    return waitIdle();
  }

  String read(String path) =>
      File(path).existsSync() ? File(path).readAsStringSync() : '';

  String get log => read('$claudeDir/install.log');
  String get password => read('$claudeDir/password').trim();

  Future<void> dispose() async {
    await verb(['stop']);
    await server.close(force: true);
    try {
      root.deleteSync(recursive: true);
    } on FileSystemException {
      // A stub still flushing; the temp directory is reclaimed by the OS.
    }
  }
}

void main() {
  final script = TermuxBridge.localAgentsScriptForTesting();

  group('the embedded script: structure', () {
    test('the script and every dispatch pass bash -n', () {
      final dir = Directory.systemTemp.createTempSync('oc-claude-syntax-');
      addTearDown(() => dir.deleteSync(recursive: true));
      final sources = {
        'claude.sh': script,
        for (final verb in TermuxBridge.localAgentsVerbs)
          'dispatch-$verb.sh': TermuxBridge.localAgentsVerbScript(
            verb,
            args: verb == 'ensure-project' ? ['/root/projects/x'] : const [],
          ),
      };
      for (final entry in sources.entries) {
        final file = File('${dir.path}/${entry.key}')
          ..writeAsStringSync(entry.value);
        final result = Process.runSync('bash', ['-n', file.path]);
        expect(result.exitCode, 0, reason: '${entry.key}: ${result.stderr}');
      }
    });

    test('shellcheck passes when it is installed', () {
      final which = Process.runSync('which', ['shellcheck']);
      if (which.exitCode != 0) {
        markTestSkipped('shellcheck is not installed on this machine');
        return;
      }
      final dir = Directory.systemTemp.createTempSync('oc-claude-sc-');
      addTearDown(() => dir.deleteSync(recursive: true));
      final file = File('${dir.path}/claude.sh')..writeAsStringSync(script);
      final result = Process.runSync('shellcheck', [
        '-s',
        'bash',
        '-S',
        'error',
        file.path,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}');
    });

    test('pins are well-formed and point at nodejs.org over https', () {
      const pins = TermuxBridge.localAgentsPins;
      expect(pins['node_version'], matches(RegExp(r'^v\d+\.\d+\.\d+$')));
      expect(pins['node_sha256_arm64'], matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(pins['node_sha256_x64'], matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(pins['node_sha256_arm64'], isNot(pins['node_sha256_x64']));
      expect(pins['node_base_url'], 'https://nodejs.org/dist');
      // Reviewed patch target; on-device verification is tracked separately.
      expect(pins['paseo_version'], '0.11.2');
      // Claude Code never floats to npm's latest on this path: the pin is the
      // same tested release the built-in Linux installs.
      expect(
        pins['claude_version'],
        AgentCatalog.builtIn.byId('claude')!.recipe!.version,
      );
      final file = TermuxBridge.localAgentsPinsFile();
      for (final entry in pins.entries) {
        expect(file, contains('${entry.key}=${entry.value}\n'));
      }
    });

    test(
      'the daemon runs as an ordinary user, never with the root guard off',
      () {
        // Claude Code refuses its permission-skipping mode under root, and the
        // daemon makes that mode available, so as root every turn died at
        // launch. The fix is an unprivileged user; IS_SANDBOX would instead
        // switch Claude Code's own guard off.
        expect(script, isNot(contains('IS_SANDBOX=')));
        expect(script, contains(r'AGENT_USER="${OC_CLAUDE_USER:-oc}"'));
        expect(
          script,
          contains(
            r'UBUNTU_HOME="${OC_CLAUDE_UBUNTU_HOME:-/home/$AGENT_USER}"',
          ),
        );
        final daemon = RegExp(
          r'run_daemon\(\) \{.*?\n\}\n',
          dotAll: true,
        ).firstMatch(script)![0]!;
        expect(daemon, contains(r'proot-distro login --user "$AGENT_USER"'));
        final signIn = script.substring(script.indexOf('# signin:'));
        expect(
          RegExp(
            r'proot-distro login --user "\$AGENT_USER"[^\n]*\n[^\n]*claude',
          ).allMatches(signIn).length,
          2,
          reason:
              'both sign-in commands must store the sign-in where the '
              "daemon's Claude Code looks for it",
        );
      },
    );

    test('the daemon does not download speech models on a phone', () {
      final daemon = RegExp(
        r'run_daemon\(\) \{.*?\n\}\n',
        dotAll: true,
      ).firstMatch(script)![0]!;
      expect(daemon, contains('PASEO_DICTATION_ENABLED=false'));
      expect(daemon, contains('PASEO_VOICE_MODE_ENABLED=false'));
    });

    test('the daemon is loopback-only, relay off, and never widened', () {
      expect(script, contains(r'LISTEN="127.0.0.1:$PORT"'));
      final run = RegExp(
        r'exec paseo daemon run[^\n]*',
      ).firstMatch(script)![0]!;
      expect(run, contains(r'--home "$1"'));
      // Every setting the old flags carried is set in the daemon's config.
      for (final setting in [
        r'daemon_set daemon.listen "$2" "$1"',
        'daemon_set daemon.relay.enabled false',
        'daemon_set daemon.mcp.injectIntoAgents false',
        'daemon_set features.webUi.enabled false',
      ]) {
        expect(script, contains(setting));
      }
      // Paseo 0.11+ refuses these: launching with any of them is the bug.
      expect(script, isNot(matches(RegExp(r'paseo\s+start\b'))));
      for (final removed in [
        '--foreground',
        '--listen',
        '--no-relay',
        '--no-web-ui',
        '--no-inject-mcp',
        '--hostnames',
      ]) {
        expect(
          RegExp(
            r'^[^#\n]*paseo[^\n]*' + RegExp.escape(removed),
            multiLine: true,
          ).hasMatch(script),
          isFalse,
          reason: removed,
        );
      }
      expect(script, isNot(contains('0.0.0.0')));
      expect(script, isNot(contains('relay.paseo')));
      expect(script, isNot(contains('--hostnames')));
    });

    test('the password is never an argument and nothing is traced', () {
      // An assignment on a command line would show in `ps`; the inner shell
      // reads the value from stdin and exports it instead.
      expect(script, isNot(contains('PASEO_PASSWORD=')));
      expect(script, contains('IFS= read -r PASEO_PASSWORD'));
      expect(script, contains(r'< "$PASSWORD_FILE" 2>&1 | write_daemon_log'));
      expect(script, isNot(matches(RegExp(r'(^|\n)\s*set -[a-zA-Z]*x'))));
      expect(script, isNot(contains('xtrace')));
      // The only reader that prints the file is the `password` verb.
      expect(RegExp(r'cat "\$PASSWORD_FILE"').allMatches(script).length, 1);
    });

    test('Node is verified before it is unpacked, and never piped to a '
        'shell', () {
      final verify = script.indexOf('sha256sum "\$part"');
      final rename = script.indexOf('mv -f "\$part" "\$archive"');
      final unpack = script.indexOf('tar -xzf -');
      expect(verify, greaterThan(0));
      expect(rename, greaterThan(verify));
      expect(unpack, greaterThan(rename));
      expect(script, isNot(matches(RegExp(r'curl[^\n]*\|\s*(ba)?sh'))));
      expect(script, isNot(contains('apt-get install')));
    });

    test('packages go to the app prefix with Paseo pinned exactly', () {
      expect(
        script,
        contains(r'npm install -g --no-fund --no-audit --prefix "$1"'),
      );
      expect(script, contains(r'npm_install "@getpaseo/cli@$paseo_version"'));
      expect(
        script,
        contains(r'npm_install "@anthropic-ai/claude-code@$claude_version"'),
      );
      expect(
        script,
        isNot(contains("npm_install '@anthropic-ai/claude-code'")),
      );
    });

    test('remove keeps ~/.claude unless --forget-signin', () {
      final remove = script.substring(
        script.indexOf('remove_verb() {'),
        script.indexOf('# sign-in'),
      );
      final forget = remove.indexOf('if [ "\$forget" = 1 ]; then');
      expect(forget, greaterThan(0));
      expect(remove.indexOf('.claude'), greaterThan(forget));
      expect(remove, contains('kept \$UBUNTU_HOME/.claude'));
    });

    test('the dispatch rewrites script and pins, and detaches long verbs', () {
      final install = TermuxBridge.localAgentsVerbScript('install');
      expect(install, contains("bash \"\$CLAUDE\" queue 'install'"));
      expect(install, contains("nohup bash \"\$CLAUDE\" 'install'"));
      expect(install, contains('claude-started:'));
      expect(install, contains('node_version=v'));
      final status = TermuxBridge.localAgentsVerbScript('status');
      expect(status, contains('exec bash "\$CLAUDE" status'));
      expect(
        () => TermuxBridge.localAgentsVerbScript('signin'),
        throwsArgumentError,
      );
      expect(
        () => TermuxBridge.localAgentsVerbScript('run-daemon'),
        throwsArgumentError,
      );
      expect(
        TermuxBridge.localAgentsVerbScript(
          'ensure-project',
          args: ["/root/it's"],
        ),
        contains("'/root/it'\"'\"'s'"),
      );
    });
  });

  group('the embedded script: behaviour against fixtures', () {
    late _Fixture fx;

    tearDown(() => fx.dispose());

    test('without Ubuntu: status says so and install refuses', () async {
      fx = await _Fixture.create(ubuntu: false);
      expect((await fx.status()).phase, LocalAgentPhase.needsUbuntu);
      final status = await fx.run(['install']);
      expect(status.phase, LocalAgentPhase.failed);
      expect(status.failureKind, LocalAgentFailureKind.needsUbuntu);
      expect(status.message, contains('On this phone setup'));
      expect(fx.downloads, 0);
    });

    test('install: verified Node, both packages, versions recorded, and a '
        'second run downloads nothing', () async {
      fx = await _Fixture.create();
      expect((await fx.status()).phase, LocalAgentPhase.absent);
      final status = await fx.run(['install']);
      expect(status.phase, LocalAgentPhase.installed, reason: fx.log);
      expect(status.installed, isTrue);
      expect(status.nodeVersion, _nodeVersion);
      expect(status.paseoVersion, '0.8.0');
      expect(status.claudeVersion, '2.1.278');
      expect(fx.read('${fx.nodeDir}/.oc-node-version'), _nodeVersion);
      expect(Directory('${fx.claudeDir}/tmp').existsSync(), isFalse);
      final npm = fx.read('${fx.root.path}/npm-calls.log');
      expect(npm, contains('--prefix ${fx.agentsDir}'));
      expect(npm, contains('@getpaseo/cli@0.8.0'));
      expect(npm, contains('@anthropic-ai/claude-code@2.1.278'));
      expect(fx.downloads, 1);

      final again = await fx.run(['install']);
      expect(again.phase, LocalAgentPhase.installed, reason: fx.log);
      expect(fx.downloads, 1);
    });

    test('Claude Code is installed at the pinned version, never npm latest, '
        'and a different version installed is a failure', () async {
      fx = await _Fixture.create();
      fx.writePins(claude: '2.1.999');
      final status = await fx.run(['install']);
      expect(status.phase, LocalAgentPhase.failed, reason: fx.log);
      final npm = fx.read('${fx.root.path}/npm-calls.log');
      expect(npm, contains('@anthropic-ai/claude-code@2.1.999'));
      expect(fx.log, contains('2.1.999 was pinned but 2.1.278 was installed'));
    });

    test('a checksum mismatch stops before anything is unpacked', () async {
      fx = await _Fixture.create();
      fx.writePins(sha: 'a' * 64);
      final status = await fx.run(['install']);
      expect(status.failureKind, LocalAgentFailureKind.checksum);
      expect(status.step, LocalAgentStep.node);
      expect(Directory(fx.nodeDir).existsSync(), isFalse);
      expect(Directory('${fx.nodeDir}.new').existsSync(), isFalse);
      final tmp = Directory('${fx.claudeDir}/tmp');
      expect(
        tmp.existsSync() ? tmp.listSync() : const <FileSystemEntity>[],
        isEmpty,
      );
    });

    test('a failed download, a plain http pin and a full phone each have '
        'their own failure', () async {
      fx = await _Fixture.create();
      fx.serveNotFound = true;
      var status = await fx.run(['install']);
      expect(status.failureKind, LocalAgentFailureKind.download);

      fx.serveNotFound = false;
      await fx.verb(['install'], env: {'OC_CLAUDE_ALLOW_LOOPBACK_PINS': '0'});
      status = await fx.waitIdle();
      expect(status.failureKind, LocalAgentFailureKind.other);
      expect(status.message, contains('https'));

      File('${fx.stubs}/df').writeAsStringSync(
        '#!/bin/bash\necho "Filesystem 1024-blocks Used Available Capacity '
        'Mounted on"\necho "/dev/x 9999999 1 204800 1% /"\n',
      );
      Process.runSync('chmod', ['755', '${fx.stubs}/df']);
      status = await fx.run(['install']);
      expect(status.failureKind, LocalAgentFailureKind.noSpace);
      expect(status.message, contains('200 MB free, 2048 MB needed'));
    });

    test('a native build failure is reported, not worked around', () async {
      fx = await _Fixture.create();
      File('${fx.root.path}/npm-fail').writeAsStringSync(
        'npm error gyp ERR! build error\nnpm error node-pty\n',
      );
      final status = await fx.run(['install']);
      expect(status.failureKind, LocalAgentFailureKind.nativeBuild);
      expect(status.step, LocalAgentStep.paseo);
      expect(fx.log, contains('gyp ERR! build error'));
      expect(fx.log, isNot(contains('build-essential')));
    });

    test('start: health-confirmed ready, the password only in the '
        'environment; stop; a dead daemon reconciles', () async {
      fx = await _Fixture.create();
      await fx.run(['install']);
      var status = await fx.run(['start']);
      expect(status.phase, LocalAgentPhase.ready, reason: fx.log);
      expect(status.pid, isNotNull);
      expect(status.port, fx.port);

      final password = fx.password;
      expect(password, matches(RegExp(r'^[0-9a-f]{48}$')));
      final mode = Process.runSync('stat', [
        '-c',
        '%a',
        '${fx.claudeDir}/password',
      ]);
      expect((mode.stdout as String).trim(), '600');
      expect(fx.read('${fx.root.path}/paseo-env-password'), password);
      final args = fx.read('${fx.root.path}/paseo-args.log').trim().split('\n');
      final home = '${fx.ubuntuHome}/.oc-paseo';
      expect(args, [
        'daemon config set daemon.listen 127.0.0.1:${fx.port} --home $home',
        'daemon config set daemon.relay.enabled false --home $home',
        'daemon config set daemon.mcp.injectIntoAgents false --home $home',
        'daemon config set features.webUi.enabled false --home $home',
        'daemon config set features.dictation.enabled false --home $home',
        'daemon config set features.voiceMode.enabled false --home $home',
        'daemon run --home $home',
      ]);
      expect(
        fx.read('${fx.root.path}/proot-calls.log'),
        contains(
          'login --user ${Platform.environment['USER'] ?? 'root'} --work-dir',
        ),
      );
      for (final place in [
        fx.read('${fx.root.path}/proot-calls.log'),
        fx.log,
        fx.read('${fx.claudeDir}/state'),
        fx.read('${fx.claudeDir}/daemon.log'),
        fx.read('${fx.claudeDir}/config'),
      ]) {
        expect(place, isNot(contains(password)));
      }
      final printed = await fx.verb(['password']);
      expect((printed.stdout as String), '$password\n');
      expect(fx.read('${fx.root.path}/wake.log'), contains('termux-wake-lock'));

      // Start again: the same daemon, the same password.
      status = await fx.run(['start']);
      expect(status.phase, LocalAgentPhase.ready);
      expect(fx.password, password);

      // Android kills the tree: `ready` in the file is not believed.
      Process.runSync('kill', ['-KILL', '--', '-${status.pid}']);
      await Future<void>.delayed(const Duration(milliseconds: 300));
      expect(fx.read('${fx.claudeDir}/state'), contains('phase=ready'));
      status = await fx.status();
      expect(status.phase, LocalAgentPhase.installed);
      expect(status.killedByAndroid, isTrue);

      status = await fx.run(['restart']);
      expect(status.phase, LocalAgentPhase.ready, reason: fx.log);
      await fx.verb(['stop']);
      status = await fx.status();
      expect(status.phase, LocalAgentPhase.installed);
      expect(status.pid, isNull);
      expect(
        fx.read('${fx.root.path}/wake.log'),
        contains('termux-wake-unlock'),
      );
    });

    test('a port held by something else is port_in_use', () async {
      fx = await _Fixture.create();
      await fx.run(['install']);
      final squatter = await ServerSocket.bind(
        InternetAddress.loopbackIPv4,
        fx.port,
      );
      addTearDown(squatter.close);
      final status = await fx.run(['start']);
      expect(status.failureKind, LocalAgentFailureKind.portInUse);
      expect(File('${fx.root.path}/paseo-args.log').existsSync(), isFalse);
    });

    test('a daemon that never answers times out and is taken down', () async {
      fx = await _Fixture.create();
      await fx.run(['install']);
      File('${fx.root.path}/paseo-silent').writeAsStringSync('');
      final status = await fx.run(['start']);
      expect(status.failureKind, LocalAgentFailureKind.timeout);
      expect(fx.log, contains('[daemon] paseo listening'));
      expect(File('${fx.claudeDir}/daemon.pid').existsSync(), isFalse);
    });

    test('start before install is not_installed', () async {
      fx = await _Fixture.create();
      final status = await fx.run(['start']);
      expect(status.failureKind, LocalAgentFailureKind.notInstalled);
    });

    test(
      'sign-in status names nothing, and remove keeps the sign-in',
      () async {
        fx = await _Fixture.create();
        await fx.run(['install']);
        var result = await fx.verb(['signin-status']);
        expect(result.stdout, 'signed_in=no\n');
        Directory('${fx.ubuntuHome}/.claude').createSync();
        File(
          '${fx.ubuntuHome}/.claude/.credentials.json',
        ).writeAsStringSync('{"claudeAiOauth":{"accessToken":"sk-secret"}}');
        result = await fx.verb(['signin-status']);
        expect(result.stdout, 'signed_in=yes\n');
        expect((await fx.status()).signedIn, LocalAgentSignIn.yes);

        Directory('${fx.ubuntuHome}/projects/keep').createSync(recursive: true);
        var status = await fx.run(['remove']);
        expect(status.phase, LocalAgentPhase.absent);
        expect(Directory(fx.nodeDir).existsSync(), isFalse);
        expect(Directory(fx.agentsDir).existsSync(), isFalse);
        expect(Directory(fx.claudeDir).existsSync(), isFalse);
        expect(
          File('${fx.ubuntuHome}/.claude/.credentials.json').existsSync(),
          isTrue,
        );
        expect(
          Directory('${fx.ubuntuHome}/projects/keep').existsSync(),
          isTrue,
        );

        status = await fx.run(['remove', '--forget-signin']);
        expect(status.phase, LocalAgentPhase.absent);
        expect(Directory('${fx.ubuntuHome}/.claude').existsSync(), isFalse);
        expect(
          Directory('${fx.ubuntuHome}/projects/keep').existsSync(),
          isTrue,
        );
      },
    );

    test('projects: the first one is created with git init', () async {
      fx = await _Fixture.create();
      var result = await fx.verb(['projects']);
      expect(
        result.stdout,
        'project=${fx.ubuntuHome}/projects/my-first-project\n',
      );
      expect(
        Directory(
          '${fx.ubuntuHome}/projects/my-first-project/.git',
        ).existsSync(),
        isTrue,
      );
      result = await fx.verb([
        'ensure-project',
        '${fx.ubuntuHome}/projects/second',
      ]);
      expect(result.exitCode, 0);
      result = await fx.verb(['projects']);
      expect((result.stdout as String).trim().split('\n'), [
        'project=${fx.ubuntuHome}/projects/my-first-project',
        'project=${fx.ubuntuHome}/projects/second',
      ]);
      result = await fx.verb(['ensure-project', '../escape']);
      expect(result.exitCode, 64);
      result = await fx.verb(['ensure-project', '/root/../etc']);
      expect(result.exitCode, 64);
    });

    test('the bridge dispatch writes this build\'s script and pins; a queued '
        'verb already reads as busy', () async {
      fx = await _Fixture.create();
      File(fx.script).deleteSync();
      final dispatch = await Process.run(
        'bash',
        ['-c', TermuxBridge.localAgentsVerbScript('status')],
        environment: fx.environment,
        workingDirectory: fx.root.path,
      );
      expect(dispatch.exitCode, 0, reason: '${dispatch.stderr}');
      expect(
        LocalAgentStatus.parse(dispatch.stdout as String).phase,
        LocalAgentPhase.absent,
      );
      expect(
        fx.read(fx.script),
        '${TermuxBridge.localAgentsScriptForTesting()}\n',
      );
      expect(
        fx.read('${fx.ocDir}/claude-pins'),
        TermuxBridge.localAgentsPinsFile(),
      );

      final queued = await fx.verb(['queue', 'install']);
      expect(queued.stdout, 'claude-queued:install\n');
      final status = await fx.status();
      expect(status.phase, LocalAgentPhase.installing);
      expect(status.busy, isTrue);
      expect((await fx.verb(['queue', 'signin'])).exitCode, 64);
    });
  });

  group('LocalAgentStatus', () {
    test('parses every phase', () {
      const phases = {
        'absent': LocalAgentPhase.absent,
        'needs_ubuntu': LocalAgentPhase.needsUbuntu,
        'installing': LocalAgentPhase.installing,
        'installed': LocalAgentPhase.installed,
        'starting': LocalAgentPhase.starting,
        'ready': LocalAgentPhase.ready,
        'stopping': LocalAgentPhase.stopping,
        'removing': LocalAgentPhase.removing,
        'failed': LocalAgentPhase.failed,
        'something-new': LocalAgentPhase.unknown,
      };
      for (final entry in phases.entries) {
        expect(
          LocalAgentStatus.parse('phase=${entry.key}\n').phase,
          entry.value,
        );
      }
      expect(LocalAgentStatus.parse('garbage').phase, LocalAgentPhase.unknown);
    });

    test('parses a full answer', () {
      final status = LocalAgentStatus.parse(
        'phase=ready\nmessage=Claude Code is running on this phone\n'
        'busy=no\nverb=start\nstep=\ninstalled=yes\nnode_version=v24.21.0\n'
        'paseo_version=0.8.0\nclaude_version=2.1.278\npid=4242\nport=6767\n'
        'signed_in=yes\nfailure_kind=\nkilled=no\nupdated_at=1789000000\n',
      );
      expect(status.isReady, isTrue);
      expect(status.installed, isTrue);
      expect(status.nodeVersion, 'v24.21.0');
      expect(status.paseoVersion, '0.8.0');
      expect(status.claudeVersion, '2.1.278');
      expect(status.pid, 4242);
      expect(status.port, 6767);
      expect(status.signedIn, LocalAgentSignIn.yes);
      expect(status.failureKind, isNull);
      expect(status.failure, isNull);
      expect(status.updatedAt!.millisecondsSinceEpoch, 1789000000000);
    });

    test('maps every failure kind the script writes', () {
      const kinds = {
        'needs_ubuntu': LocalAgentFailureKind.needsUbuntu,
        'no_space': LocalAgentFailureKind.noSpace,
        'download': LocalAgentFailureKind.download,
        'checksum': LocalAgentFailureKind.checksum,
        'native_build': LocalAgentFailureKind.nativeBuild,
        'port_in_use': LocalAgentFailureKind.portInUse,
        'timeout': LocalAgentFailureKind.timeout,
        'not_installed': LocalAgentFailureKind.notInstalled,
        'unsupported_arch': LocalAgentFailureKind.unsupportedArch,
        'interrupted': LocalAgentFailureKind.interrupted,
        'daemon_exited': LocalAgentFailureKind.daemon,
        'unhealthy': LocalAgentFailureKind.daemon,
        'npm': LocalAgentFailureKind.packages,
        'pins': LocalAgentFailureKind.other,
        'error': LocalAgentFailureKind.other,
        '': LocalAgentFailureKind.other,
      };
      for (final entry in kinds.entries) {
        final status = LocalAgentStatus.parse(
          'phase=failed\nmessage=why\nfailure_kind=${entry.key}\n',
        );
        expect(status.failureKind, entry.value, reason: entry.key);
        expect(status.failure!.message, 'why');
      }
      // Every kind the script can write is one this table knows.
      final written = RegExp(
        r'fail ([a-z_]+) ',
      ).allMatches(script).map((match) => match[1]!).toSet();
      expect(written.difference(kinds.keys.toSet()), isEmpty);
      // A failure kind on a phase that is not failed is ignored.
      expect(
        LocalAgentStatus.parse(
          'phase=installed\nfailure_kind=timeout\n',
        ).failureKind,
        isNull,
      );
    });
  });

  group('LocalAgentRuntime', () {
    LocalAgentRuntime runtimeFor(
      Future<String> Function(String verb, String script) answer, {
      List<String>? opened,
    }) => LocalAgentRuntime(
      runner: (script, {Duration timeout = Duration.zero}) async {
        final inline = RegExp(
          r'exec bash "\$CLAUDE" ([a-z-]+)',
        ).firstMatch(script)?[1];
        final detached = RegExp(
          "nohup bash \"\\\$CLAUDE\" '([a-z]+)'",
        ).firstMatch(script)?[1];
        return answer(inline ?? detached ?? 'log-tail', script);
      },
      terminalOpener: (command) async {
        opened?.add(command);
        return true;
      },
      pollInterval: const Duration(milliseconds: 5),
    );

    test('a verb dispatches, polls until idle and answers the last', () async {
      var polls = 0;
      final verbs = <String>[];
      final runtime = runtimeFor((verb, _) async {
        verbs.add(verb);
        if (verb != 'status') return 'claude-queued:$verb\nclaude-started:9\n';
        polls++;
        return polls < 3
            ? 'phase=installing\nbusy=yes\nstep=paseo\n'
            : 'phase=installed\nbusy=no\ninstalled=yes\n';
      });
      final seen = <LocalAgentPhase>[];
      final status = await runtime.install();
      expect(status.phase, LocalAgentPhase.installed);
      expect(polls, 3);
      expect(verbs.first, 'install');

      polls = 0;
      await runtime.statusStream().forEach((s) => seen.add(s.phase));
      expect(seen, [
        LocalAgentPhase.installing,
        LocalAgentPhase.installing,
        LocalAgentPhase.installed,
      ]);
    });

    test('a failed verb throws its typed failure', () async {
      final runtime = runtimeFor(
        (verb, _) async => verb == 'status'
            ? 'phase=failed\nbusy=no\nfailure_kind=port_in_use\n'
                  'message=Port 6767 on this phone is already used\n'
            : 'claude-started:9\n',
      );
      await expectLater(
        runtime.start(),
        throwsA(
          isA<LocalAgentFailure>()
              .having((f) => f.kind, 'kind', LocalAgentFailureKind.portInUse)
              .having((f) => f.message, 'message', contains('6767')),
        ),
      );
    });

    test('a bridge error and a refused dispatch are bridge failures', () async {
      var runtime = runtimeFor(
        (_, _) async => throw const TermuxBridgeException('no Termux'),
      );
      await expectLater(
        runtime.status(),
        throwsA(
          isA<LocalAgentFailure>().having(
            (f) => f.kind,
            'kind',
            LocalAgentFailureKind.bridge,
          ),
        ),
      );
      runtime = runtimeFor((_, _) async => 'claude-busy:install:7\n');
      await expectLater(
        runtime.restart(),
        throwsA(
          isA<LocalAgentFailure>().having(
            (f) => f.kind,
            'kind',
            LocalAgentFailureKind.bridge,
          ),
        ),
      );
      expect(await runtime.logTail(), isNotEmpty);
    });

    test('remove passes --forget-signin only when asked', () async {
      final scripts = <String>[];
      final runtime = runtimeFor((verb, script) async {
        scripts.add(script);
        return verb == 'status'
            ? 'phase=absent\nbusy=no\n'
            : 'claude-started:9\n';
      });
      await runtime.remove();
      expect(scripts.first, contains("nohup bash \"\$CLAUDE\" 'remove'  >"));
      scripts.clear();
      await runtime.remove(forgetSignIn: true);
      expect(scripts.first, contains("'remove' '--forget-signin'"));
    });

    test('password accepts only the script\'s own format', () async {
      final good = 'ab' * 24;
      var runtime = runtimeFor((_, _) async => '$good\n');
      expect(await runtime.password(), good);
      runtime = runtimeFor((_, _) async => 'usage: claude.sh\n');
      await expectLater(runtime.password(), throwsA(isA<LocalAgentFailure>()));
    });

    test('sign-in opens a visible terminal on the script and nothing '
        'else', () async {
      final opened = <String>[];
      final verbs = <String>[];
      final runtime = runtimeFor((verb, _) async {
        verbs.add(verb);
        return 'signed_in=no\n';
      }, opened: opened);
      expect(await runtime.openSignIn(), isTrue);
      // The script is written to disk before the terminal names it.
      expect(verbs, ['signin-status']);
      expect(opened, [r'bash "$HOME/.oc/claude.sh" signin']);
      expect(await runtime.signInStatus(), LocalAgentSignIn.no);
    });

    test('projects reads paths as Ubuntu sees them', () async {
      final runtime = runtimeFor(
        (verb, _) async => verb == 'projects'
            ? 'project=/root/projects/a\nnoise\nproject=/root/projects/b\n'
            : 'project=/root/projects/c\n',
      );
      expect(await runtime.projects(), [
        '/root/projects/a',
        '/root/projects/b',
      ]);
      expect(
        await runtime.ensureProject('/root/projects/c'),
        '/root/projects/c',
      );
      await expectLater(
        runtime.ensureProject('/root/projects/d'),
        throwsA(isA<LocalAgentFailure>()),
      );
    });
  });
}
