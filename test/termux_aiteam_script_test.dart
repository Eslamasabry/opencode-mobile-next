// AI Team on this phone, Termux path (issue #87): `aiteam.sh` run for real.
//
// The script runs with bash on this machine against a fixture Termux: a temp
// HOME and $PREFIX, and a fake `proot-distro` whose `login` runs the command
// in a private user and mount namespace with the fixture rootfs's /root,
// /opt, /usr/local, /var/cache and /var/lib mounted over the real ones, so
// the scripts meant for Ubuntu run exactly as written (as a mapped root, like
// proot's fake root). A local HTTP server serves the "upstream" archives
// (stub gc/bd/dolt packed as the real releases pack them) and plays the
// loopback supervisor. apt-get, dpkg and pkill are stubbed inside the fake
// Ubuntu; nothing here touches the real system or the network.
//
// Needs unprivileged user namespaces (`unshare --user --map-root-user`);
// the group is skipped, saying so, where the kernel refuses them.
@Timeout(Duration(minutes: 4))
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';
import 'package:opencode_mobile/termux/team_scripts.dart';

/// `proot-distro login <name> [--user u] [--work-dir d] -- command...`.
const _fakeProotDistro = r'''#!/bin/bash
[ -z "${OC_FAKE_PROOT_LOG:-}" ] || printf '%s\n' "$*" >> "$OC_FAKE_PROOT_LOG"
[ "${1:-}" = login ] || { echo "fake proot-distro: only login" >&2; exit 64; }
shift
name='' workdir=/root
while [ $# -gt 0 ]; do
  case "$1" in
    --) shift; break ;;
    --work-dir) workdir=$2; shift 2 ;;
    --user) shift 2 ;;
    --*) shift ;;
    *) name=$1; shift ;;
  esac
done
rootfs="$PREFIX/var/lib/proot-distro/installed-rootfs/$name"
[ -d "$rootfs" ] || { echo "Error: $name is not installed" >&2; exit 1; }
export OC_FAKE_ROOTFS="$rootfs" OC_FAKE_WORKDIR="$workdir"
exec unshare --user --map-root-user --mount --propagation private bash -c '
for d in root opt usr/local var/cache var/lib; do
  mkdir -p "$OC_FAKE_ROOTFS/$d"
  mount --bind "$OC_FAKE_ROOTFS/$d" "/$d" || exit 90
done
cd "$OC_FAKE_WORKDIR" || exit 91
exec "$@"
' fake-proot "$@"
''';

/// Inside Ubuntu: records calls, fakes init/rig/import/register, plays
/// the supervisor.
const _gcStub = r'''#!/bin/bash
calls=/root/gc-calls.log
marker=/root/aiteam/supervisor.marker
case "${1:-}" in
  version) echo '1.4.1'; exit 0 ;;
  init)
    echo "init $*" >> "$calls"
    mkdir -p city/.gc
    cp ./city.toml city/city.toml
    exit 0 ;;
  rig)
    echo "$*" >> "$calls"
    if [ "${2:-}" = add ]; then
      printf '\n[[rigs]]\nname = "%s"\npath = "%s"\n' "$5" "$3" >> city.toml
      mkdir -p .gc
      printf '[[rigs]]\nname = "%s"\npath = "%s"\n' "$5" "$3" >> .gc/site.toml
    fi
    exit 0 ;;
  import)
    if grep -q 'patches.agent' city.toml; then p=yes; else p=no; fi
    echo "$* patches=$p" >> "$calls"
    mkdir -p /root/.gc/cache/repos/abc123/gastown/assets/scripts
    exit 0 ;;
  register) echo "$*" >> "$calls"; exit 0 ;;
  supervisor)
    case "${2:-}" in
      run)
        echo "supervisor run cwd=$PWD path=$PATH" >> "$calls"
        echo $$ > "$marker"
        trap 'rm -f "$marker"; exit 0' TERM INT
        while :; do sleep 0.2; done ;;
      stop)
        echo 'supervisor stop' >> "$calls"
        p=$(cat "$marker" 2>/dev/null || true)
        [ -z "$p" ] || kill -TERM "$p" 2>/dev/null || true
        exit 0 ;;
    esac ;;
esac
echo "other $*" >> "$calls"
exit 0
''';

const _bdStub = r'''#!/bin/bash
case "${1:-}" in
  --version|version) echo 'bd version 1.2.2 (stub)' ;;
  metrics) echo "bd $*" >> /root/bd-calls.log ;;
esac
''';

/// A bd that Android's system-call filter kills (exit 128 + SIGSYS).
const _bdSigsysStub = '#!/bin/bash\nexit 159\n';

const _doltStub = r'''#!/bin/bash
case "${1:-}" in
  version) echo 'dolt version 2.3.5' ;;
  config) echo "dolt $*" >> /root/dolt-calls.log ;;
esac
''';

/// apt-get and dpkg: "installs" into a list dpkg -s reads. A successful
/// status query prints dpkg's installed status as well as returning zero;
/// setup verifies both, so an unpacked package is not mistaken for installed.
const _aptStub = r'''#!/bin/bash
echo "apt-get $*" >> /root/apt-calls.log
case " $* " in
  *" install "*)
    mkdir -p /var/lib/fake-dpkg
    for p in tmux jq lsof procps; do
      case " $* " in *" $p "*) touch "/var/lib/fake-dpkg/$p" ;; esac
    done ;;
esac
exit 0
''';

const _dpkgStub = r'''#!/bin/bash
case "${1:-}" in
  -s)
    [ -f "/var/lib/fake-dpkg/$2" ] || exit 1
    printf 'Package: %s\nStatus: install ok installed\n' "$2" ;;
  *) exit 0 ;;
esac
''';

/// cityScript stops a leftover Dolt by pattern; on this machine that must
/// never reach a real process.
const _pkillStub = '#!/bin/bash\necho "pkill \$*" >> /root/pkill-calls.log\n';

const _wakeStub = r'''#!/bin/bash
echo "$(basename "$0")" >> "$HOME/.oc/wake.log"
''';

bool _namespacesWork() {
  try {
    return Process.runSync('unshare', [
          '--user',
          '--map-root-user',
          '--mount',
          'true',
        ]).exitCode ==
        0;
  } on ProcessException {
    return false;
  }
}

class _Fixture {
  _Fixture._(this.root, this.server);

  final Directory root;
  final HttpServer server;

  /// The served archives by file name.
  final files = <String, List<int>>{};

  /// Every archive request, with the byte offset it asked to start at.
  final served = <String>[];

  String get home => '${root.path}/home';
  String get prefix => '${root.path}/prefix';
  String get bin => '$prefix/bin';
  String get stubs => '${root.path}/stubs';
  String get rootfs =>
      '$prefix/var/lib/proot-distro/installed-rootfs/opencode-ubuntu';
  String get ocDir => '$home/.oc';
  String get aiteamDir => '$ocDir/aiteam';
  String get script => '$ocDir/aiteam.sh';
  String get baseUrl => 'http://127.0.0.1:${server.port}/aiteam/';
  String get supervisorUrl => 'http://127.0.0.1:${server.port}';
  String get projectPath => '/root/projects/calc';
  String get project => '$rootfs$projectPath';
  String get team => '$rootfs/root/aiteam';
  String get city => '$team/city';
  String get prootLog => '${root.path}/proot.log';

  bool get supervisorUp => File('$team/supervisor.marker').existsSync();

  static Future<_Fixture> create() async {
    final root = Directory.systemTemp.createTempSync('oc-aiteam-termux-');
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final fixture = _Fixture._(root, server);
    server.listen(fixture._handle);
    for (final dir in [
      fixture.home,
      fixture.bin,
      '${fixture.prefix}/tmp',
      fixture.stubs,
      fixture.ocDir,
      '${fixture.rootfs}/root',
      '${fixture.rootfs}/usr/local/bin',
      '${fixture.rootfs}/tmp',
    ]) {
      Directory(dir).createSync(recursive: true);
    }
    _executable(fixture.script, TermuxBridge.aiteamScriptForTesting());
    _executable('${fixture.stubs}/proot-distro', _fakeProotDistro);
    _executable('${fixture.stubs}/termux-wake-lock', _wakeStub);
    _executable('${fixture.stubs}/termux-wake-unlock', _wakeStub);
    for (final (name, body) in [
      ('apt-get', _aptStub),
      ('dpkg', _dpkgStub),
      ('pkill', _pkillStub),
      ('opencode', '#!/bin/sh\necho opencode "\$@"\n'),
    ]) {
      _executable('${fixture.rootfs}/usr/local/bin/$name', body);
    }
    fixture.packArchives();
    fixture.writePins();
    await fixture._gitProject();
    return fixture;
  }

  static void _executable(String path, String content) {
    File(path).writeAsStringSync(content);
    Process.runSync('chmod', ['755', path]);
  }

  /// The three archives as upstream packs them: gc and bd at the top,
  /// dolt under `dolt-linux-<arch>/bin`.
  void packArchives({String bd = _bdStub}) {
    for (final arch in ['arm64', 'amd64']) {
      for (final (tool, member, body) in [
        ('gc', 'gc', _gcStub),
        ('bd', 'bd', bd),
        ('dolt', 'dolt-linux-$arch/bin/dolt', _doltStub),
      ]) {
        final staging = Directory('${root.path}/pack-$arch-$tool')
          ..createSync(recursive: true);
        final file = File('${staging.path}/$member')
          ..createSync(recursive: true)
          ..writeAsStringSync(body);
        Process.runSync('chmod', ['755', file.path]);
        final archive = '${root.path}/$tool-linux-$arch.tar.gz';
        // Owned by root, as release archives are (tar in the fake Ubuntu,
        // a mapped root like proot's, restores owners).
        final result = Process.runSync('tar', [
          '--owner=0',
          '--group=0',
          '--numeric-owner',
          '-czf',
          archive,
          '-C',
          staging.path,
          member,
        ]);
        expect(result.exitCode, 0, reason: '${result.stderr}');
        files[_fileName(tool, arch)] = File(archive).readAsBytesSync();
        staging.deleteSync(recursive: true);
      }
    }
  }

  static String _fileName(String tool, String arch) => switch (tool) {
    'gc' => 'gascity_1.4.1_linux_$arch.tar.gz',
    'bd' => 'beads_1.2.2_linux_$arch.tar.gz',
    _ => 'dolt-linux-$arch.tar.gz',
  };

  /// The fixture's pins: the upstream layout, served from here.
  List<AiTeamDownload> downloads(
    String arch, {
    Map<String, String> sha256Override = const {},
  }) => [
    for (final (tool, member) in [
      ('gc', 'gc'),
      ('bd', 'bd'),
      ('dolt', 'dolt-linux-$arch/bin/dolt'),
    ])
      AiTeamDownload(
        tool: tool,
        url: '$baseUrl${_fileName(tool, arch)}',
        sha256:
            sha256Override[tool] ??
            sha256.convert(files[_fileName(tool, arch)]!).toString(),
        bytes: files[_fileName(tool, arch)]!.length,
        member: member,
      ),
  ];

  String pins({Map<String, String> sha256Override = const {}}) =>
      TermuxTeamScripts.pinsFile(
        arm64: downloads('arm64', sha256Override: sha256Override),
        x64: downloads('amd64', sha256Override: sha256Override),
        baseUrlOverride: '',
      );

  void writePins({Map<String, String> sha256Override = const {}}) => File(
    '$ocDir/aiteam-pins',
  ).writeAsStringSync(pins(sha256Override: sha256Override));

  /// What the app writes with `init` for [path].
  void writeRig([String? path]) =>
      (File('$aiteamDir/rig.sh')..createSync(recursive: true))
          .writeAsStringSync(TermuxTeamScripts.rigFile(path ?? projectPath));

  Future<void> _gitProject() async {
    Directory(project).createSync(recursive: true);
    File(
      '$project/calc.py',
    ).writeAsStringSync('def add(a, b):\n    return a + b\n');
    for (final args in [
      ['init', '-q', '-b', 'master'],
      ['add', '-A'],
      ['-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-qm', 'init'],
    ]) {
      final result = await Process.run('git', args, workingDirectory: project);
      expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
    }
  }

  void _handle(HttpRequest request) {
    final path = request.uri.path;
    final response = request.response;
    if (path.startsWith('/aiteam/')) {
      final name = path.substring('/aiteam/'.length);
      final body = files[name];
      final range = RegExp(
        r'^bytes=(\d+)-$',
      ).firstMatch(request.headers.value(HttpHeaders.rangeHeader) ?? '');
      final from = range == null ? 0 : int.parse(range.group(1)!);
      served.add('$name@$from');
      if (body == null) {
        response.statusCode = 404;
      } else if (from > 0 && from < body.length) {
        response.statusCode = HttpStatus.partialContent;
        response.headers.set(
          HttpHeaders.contentRangeHeader,
          'bytes $from-${body.length - 1}/${body.length}',
        );
        response.add(body.sublist(from));
      } else {
        response.add(body);
      }
    } else if (path == '/health') {
      _json(response, supervisorUp ? {'status': 'ok'} : null);
    } else if (RegExp(r'^/v0/city/[^/]+/health$').hasMatch(path)) {
      _json(response, supervisorUp ? {'status': 'ok'} : null);
    } else if (RegExp(r'^/v0/city/[^/]+/agents$').hasMatch(path)) {
      _json(response, {
        'items': [
          {'id': 'gastown.polecat'},
          {'id': 'gastown.refinery'},
        ],
      });
    } else {
      response.statusCode = 404;
    }
    response.close();
  }

  void _json(HttpResponse response, Map<String, Object?>? body) {
    if (body == null) {
      response.statusCode = 503;
      return;
    }
    response.headers.contentType = ContentType.json;
    response.write(jsonEncode(body));
  }

  Map<String, String> get environment => {
    ...Platform.environment,
    'HOME': home,
    'XDG_CONFIG_HOME': '$home/.config',
    'PREFIX': prefix,
    'PATH': '$bin:$stubs:${Platform.environment['PATH']}',
    'AITEAM_URL': supervisorUrl,
    'AITEAM_ARCH': 'aarch64',
    'AITEAM_HEALTH_TIMEOUT': '200',
    'AITEAM_SUPERVISOR_WAIT': '200',
    'AITEAM_POLL_INTERVAL': '0.1',
    'OC_FAKE_PROOT_LOG': prootLog,
  };

  Future<ProcessResult> verb(
    List<String> args, {
    Map<String, String> env = const {},
  }) => Process.run(
    'bash',
    [script, ...args],
    environment: {...environment, ...env},
    workingDirectory: root.path,
  );

  /// The bridge's dispatch for [verb], with the fixture's pins.
  Future<ProcessResult> dispatch(String verb, {List<String> args = const []}) =>
      Process.run(
        'bash',
        [
          '-c',
          TermuxBridge.aiteamVerbScript(verb, args: args, pinsFile: pins()),
        ],
        environment: environment,
        workingDirectory: root.path,
      );

  /// A command inside the fixture Ubuntu.
  Future<ProcessResult> inUbuntu(String command) =>
      Process.run('proot-distro', [
        'login',
        'opencode-ubuntu',
        '--',
        'env',
        'HOME=/root',
        'sh',
        '-c',
        command,
      ], environment: environment);

  Future<TeamRuntimeStatus> status() async {
    final result = await verb(['status']);
    expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
    return TeamRuntimeStatus.parse(result.stdout as String);
  }

  Future<TeamRuntimeStatus> waitIdle() async {
    for (var i = 0; i < 600; i++) {
      final current = await status();
      if (!current.busy) return current;
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    fail('a verb stayed busy\n$log');
  }

  String read(String path) =>
      File(path).existsSync() ? File(path).readAsStringSync() : '';

  String get gcCalls => read('$rootfs/root/gc-calls.log');
  String get aptCalls => read('$rootfs/root/apt-calls.log');
  String get log => read('$aiteamDir/aiteam.log');

  Future<void> expectVerb(List<String> args) async {
    final result = await verb(args);
    expect(
      result.exitCode,
      0,
      reason: '${args.first}: ${result.stdout}${result.stderr}\n$log',
    );
  }

  Future<void> install() => expectVerb(['install']);

  Future<void> init() async {
    writeRig();
    await expectVerb(['init', projectPath, '--city', 'phone']);
  }

  Future<void> start() => expectVerb(['start']);

  Future<String> git(List<String> args, {String? inside}) async {
    final result = await Process.run('git', ['-C', inside ?? project, ...args]);
    return (result.stdout as String).trim();
  }

  Future<void> dispose() async {
    // Nothing of ours may survive the fixture: the supervisor's runner (its
    // own session) and the stub supervisor, by their exact pids.
    for (final file in [
      File('$aiteamDir/supervisor.pid'),
      File('$team/supervisor.marker'),
    ]) {
      if (!file.existsSync()) continue;
      final pid = int.tryParse(file.readAsStringSync().trim());
      if (pid == null) continue;
      Process.runSync('kill', ['-KILL', '--', '-$pid']);
      Process.killPid(pid, ProcessSignal.sigkill);
    }
    await server.close(force: true);
    try {
      root.deleteSync(recursive: true);
    } on FileSystemException {
      // A late tee may still hold the log; the temp dir is not precious.
    }
  }
}

/// Runs a group's scenarios concurrently (a few at a time) and hands each
/// test its own scenario's outcome. The scripts are process-bound and
/// mostly waiting, so side by side they cost the slowest, not the sum.
class _Scenarios {
  static const _parallel = 6;
  final _bodies = <String, Future<void> Function(_Fixture)>{};
  final _outcomes = <String, Future<(Object, StackTrace)?>>{};

  void add(String name, Future<void> Function(_Fixture fx) body) {
    _bodies[name] = body;
    test(name, () async {
      final failure = await _outcomes[name]!;
      if (failure != null) Error.throwWithStackTrace(failure.$1, failure.$2);
    });
  }

  Future<void> runAll() async {
    final queue = _bodies.entries.toList();
    var next = 0;
    Future<void> worker() async {
      while (next < queue.length) {
        final entry = queue[next++];
        final done = Completer<(Object, StackTrace)?>();
        _outcomes[entry.key] = done.future;
        _FixtureScope.run(entry.value).then(
          (_) => done.complete(null),
          onError: (Object e, StackTrace st) => done.complete((e, st)),
        );
        await done.future;
      }
    }

    await Future.wait([for (var i = 0; i < _parallel; i++) worker()]);
  }
}

abstract final class _FixtureScope {
  static Future<void> run(Future<void> Function(_Fixture) body) async {
    final fx = await _Fixture.create();
    try {
      await body(fx);
    } finally {
      await fx.dispose();
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final namespaces = _namespacesWork();
  final skip = namespaces
      ? false
      : 'needs unprivileged user namespaces (unshare --user --map-root-user)';
  late _Fixture fx;

  test('what Termux runs: the upstream pins, the in-app team\'s scripts on '
      'port 8372, no aiteam-assets-1', () {
    final script = TermuxBridge.aiteamScriptForTesting();
    final pins = TermuxTeamScripts.pinsFile();
    final install = TermuxBridge.aiteamVerbScript('install');
    for (final text in [script, pins, install]) {
      expect(text, isNot(contains('aiteam-assets-1')));
      expect(text, isNot(contains('opencode-mobile-next/releases')));
      expect(text, isNot(contains('android-arm64')));
    }
    for (final (arch, downloads) in [
      ('arm64', AiTeamPins.arm64),
      ('x86_64', AiTeamPins.x64),
    ]) {
      for (final d in downloads) {
        expect(
          pins,
          contains(
            '$arch ${d.tool} ${d.url} ${d.sha256} ${d.bytes} ${d.member}\n',
          ),
        );
        expect(Uri.parse(d.url).host, 'github.com');
      }
    }
    expect(AiTeamPins.arm64.map((d) => Uri.parse(d.url).path.split('/')[1]), [
      'gastownhall',
      'gastownhall',
      'dolthub',
    ]);
    expect(install, contains(pins));
    // The in-app team's scripts, byte for byte, except the port.
    final parts = TermuxTeamScripts.parts;
    expect(parts['check'], AiTeamScripts.checkScript);
    expect(parts['register'], BuiltinTeam.registerScript);
    expect(parts['unpack'], contains(AiTeamScripts.unpackScript));
    for (final name in ['city', 'service']) {
      final part = parts[name]!;
      final original = name == 'city'
          ? BuiltinTeam.cityScript
          : BuiltinTeam.serviceScript;
      expect(part, contains('port = 8372\n'));
      expect(part, isNot(contains('8472')));
      expect(
        part,
        original.replaceAll('port = 8472\n', 'port = 8372\n'),
        reason: name,
      );
      expect(part, contains('bind = "127.0.0.1"'));
      expect(script, contains(part));
    }
    // The phone tuning and the upkeep lock travel with them.
    expect(parts['city'], contains(BuiltinTeam.phoneTuning));
    expect(parts['city'], contains(BuiltinTeam.acpCommand));
    expect(parts['service'], contains(BuiltinTeam.upkeepScript));
    expect(parts['service'], contains('flock -n 9 || exit 0'));
    expect(
      TermuxTeamScripts.rigFile('/root/projects/calc'),
      allOf(
        startsWith('# oc-project: /root/projects/calc\n# oc-rig: calc\n'),
        contains(BuiltinTeam.originHook),
      ),
    );
    expect(
      TermuxTeamScripts.ubuntuPath(
        '/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/opencode-ubuntu/root/projects/calc',
      ),
      '/root/projects/calc',
    );
    expect(
      TermuxTeamScripts.ubuntuPath(
        '/data/data/com.termux/files/usr/var/lib/proot-distro/containers/opencode-ubuntu/rootfs/root/projects/a',
      ),
      '/root/projects/a',
    );
    expect(
      TermuxTeamScripts.ubuntuPath('/root/projects/a'),
      '/root/projects/a',
    );
  });

  test('the script, its Ubuntu parts and every dispatch parse', () {
    final dir = Directory.systemTemp.createTempSync('oc-aiteam-parse-');
    addTearDown(() => dir.deleteSync(recursive: true));
    final bashScripts = {
      'aiteam.sh': TermuxBridge.aiteamScriptForTesting(),
      'status.sh': TermuxBridge.aiteamStatusScript(),
      'install.sh': TermuxBridge.aiteamVerbScript('install'),
      'init.sh': TermuxBridge.aiteamVerbScript(
        'init',
        args: ["/root/projects/it's here", '--city', 'phone'],
      ),
      'remove.sh': TermuxBridge.aiteamVerbScript('remove'),
    };
    for (final entry in bashScripts.entries) {
      final file = File('${dir.path}/${entry.key}')
        ..writeAsStringSync(entry.value);
      final result = Process.runSync('bash', ['-n', file.path]);
      expect(result.exitCode, 0, reason: '${entry.key}: ${result.stderr}');
    }
    for (final entry in TermuxTeamScripts.parts.entries) {
      final file = File('${dir.path}/${entry.key}.sh')
        ..writeAsStringSync(entry.value);
      for (final shell in ['sh', 'bash']) {
        final result = Process.runSync(shell, ['-n', file.path]);
        expect(result.exitCode, 0, reason: '${entry.key}: ${result.stderr}');
      }
    }
    expect(
      TermuxBridge.aiteamVerbScript('init', args: ["/root/projects/it's here"]),
      contains("# oc-project: /root/projects/it's here\n"),
    );
    expect(
      () => TermuxBridge.aiteamVerbScript('init', args: ['a\nb']),
      throwsArgumentError,
    );
    expect(() => TermuxBridge.aiteamVerbScript('rm -rf'), throwsArgumentError);
  });

  group('aiteam.sh in Termux\'s Ubuntu', skip: skip, () {
    // Every scenario owns its own fixture (temp dir, server, namespaces), so
    // they run side by side; each test reports its own scenario's outcome.
    final scenarios = _Scenarios();
    setUpAll(scenarios.runAll);
    void scenario(String name, Future<void> Function(_Fixture fx) body) =>
        scenarios.add(name, body);

    scenario('status before any install is idle and not installed', (fx) async {
      final status = await fx.status();
      expect(status.phase, TeamRuntimePhase.idle);
      expect(status.installed, isFalse);
      expect(status.busy, isFalse);
      expect(status.url, fx.supervisorUrl);
      // A status read never starts Ubuntu (it is polled every 2 s).
      expect(fx.read(fx.prootLog), isEmpty);
    });

    scenario(
      'install downloads the upstream archives, checks them, and installs '
      'the packages and the programs inside Ubuntu',
      (fx) async {
        await fx.install();
        final status = await fx.status();
        expect(status.phase, TeamRuntimePhase.installed, reason: fx.log);
        expect(status.installed, isTrue);
        expect(status.versions, {
          'gc': '1.4.1',
          'bd': '1.2.2',
          'dolt': '2.3.5',
        });
        expect(fx.served, [
          'gascity_1.4.1_linux_arm64.tar.gz@0',
          'beads_1.2.2_linux_arm64.tar.gz@0',
          'dolt-linux-arm64.tar.gz@0',
        ]);
        for (final tool in ['gc', 'bd', 'dolt']) {
          expect(
            File('${fx.rootfs}/opt/aiteam/bin/$tool').existsSync(),
            isTrue,
            reason: tool,
          );
          expect(
            Link('${fx.rootfs}/usr/local/bin/$tool').targetSync(),
            '/opt/aiteam/bin/$tool',
          );
          expect(fx.log, contains('verified $tool'));
        }
        // The agents' opencode, the in-app setup's own.
        expect(
          fx.read('${fx.rootfs}/opt/aiteam/agent-bin/opencode'),
          AiTeamScripts.agentWrapperScript,
        );
        // The packages, with the in-app setup's apt helper.
        expect(fx.aptCalls, contains('install -y --no-install-recommends'));
        expect(fx.aptCalls, contains('tmux jq lsof procps'));
        // The identity and settings the team needs, inside Ubuntu.
        final gitconfig = fx.read('${fx.rootfs}/root/.gitconfig');
        expect(gitconfig, contains('role = maintainer'));
        expect(gitconfig, contains('createObject = rename'));
        expect(
          fx.read('${fx.rootfs}/root/dolt-calls.log'),
          contains('metrics.disabled true'),
        );
        // Nothing left over on either side; nothing in Termux's own bin.
        expect(Directory('${fx.aiteamDir}/tmp').existsSync(), isFalse);
        expect(
          Directory('${fx.rootfs}/var/cache/oc-setup/aiteam').existsSync(),
          isFalse,
        );
        expect(File('${fx.bin}/gc').existsSync(), isFalse);
        expect(fx.read('${fx.aiteamDir}/config'), contains('source=upstream'));
        // Each stage is logged with the seconds since the verb began.
        expect(
          fx.log,
          matches(
            RegExp(r'\[aiteam\] verifying: Verifying checksums \(at \d+s\)'),
          ),
        );
        expect(
          fx.log,
          matches(
            RegExp(
              r'\[aiteam\] installed: AI Team programs installed \(at \d+s\)',
            ),
          ),
        );
      },
    );

    scenario('a second install downloads and installs nothing', (fx) async {
      await fx.install();
      fx.served.clear();
      File('${fx.rootfs}/root/apt-calls.log').deleteSync();
      await fx.install();
      final status = await fx.status();
      expect(status.phase, TeamRuntimePhase.installed);
      expect(fx.served, isEmpty);
      expect(fx.aptCalls, isEmpty);
      expect(fx.log, contains('are installed already'));
    });

    scenario('a package status query that succeeds but reports an unpacked '
        'package stops before the programs are unpacked', (fx) async {
      _Fixture._executable(
        '${fx.rootfs}/usr/local/bin/dpkg',
        _dpkgStub.replaceFirst(
          'Status: install ok installed',
          'Status: install ok unpacked',
        ),
      );
      final result = await fx.verb(['install']);
      expect(result.exitCode, isNot(0), reason: fx.log);
      final status = await fx.status();
      expect(status.rawPhase, 'failed:packages');
      expect(status.installed, isFalse);
      expect(fx.log, contains('Status: install ok unpacked'));
      expect(fx.log, contains('A required package is not fully installed'));
      expect(Directory('${fx.rootfs}/opt/aiteam').existsSync(), isFalse);
      expect(fx.aptCalls, contains('install -y --no-install-recommends'));
      expect(File('${fx.rootfs}/var/lib/fake-dpkg/tmux').existsSync(), isTrue);
    });

    scenario('a checksum mismatch stops before anything reaches Ubuntu', (
      fx,
    ) async {
      fx.writePins(sha256Override: {'bd': 'f' * 64});
      final result = await fx.verb(['install']);
      expect(result.exitCode, 65, reason: '${result.stdout}${result.stderr}');
      final status = await fx.status();
      expect(status.rawPhase, 'failed:checksum-mismatch bd');
      expect(status.checksumMismatch, isTrue);
      expect(status.lastError, contains('did not match the checksum'));
      expect(status.installed, isFalse);
      // Not unpacked, no packages, no archive moved into Ubuntu, no leftover.
      expect(Directory('${fx.rootfs}/opt/aiteam').existsSync(), isFalse);
      expect(fx.aptCalls, isEmpty);
      expect(
        Directory('${fx.rootfs}/var/cache/oc-setup/aiteam').existsSync(),
        isFalse,
      );
      expect(
        Directory(
          '${fx.aiteamDir}/tmp',
        ).listSync().where((e) => e.path.endsWith('.part')),
        isEmpty,
      );
      // With the right pins, Try again installs.
      fx.writePins();
      await fx.install();
      expect((await fx.status()).phase, TeamRuntimePhase.installed);
    });

    scenario('a refused download names the host and HTTP status; Try again '
        'fetches only what is missing', (fx) async {
      final dolt = fx.files.remove('dolt-linux-arm64.tar.gz');
      final result = await fx.verb(['install']);
      expect(result.exitCode, isNot(0));
      final status = await fx.status();
      final host = '127.0.0.1:${fx.server.port}';
      expect(status.rawPhase, 'failed:download http $host 404');
      expect(status.downloadFailure!.kind, TeamDownloadFailureKind.http);
      expect(status.lastError, contains('Could not download dolt'));
      fx.files['dolt-linux-arm64.tar.gz'] = dolt!;
      fx.served.clear();
      await fx.install();
      expect((await fx.status()).phase, TeamRuntimePhase.installed);
      expect(fx.served, ['dolt-linux-arm64.tar.gz@0']);
      expect(fx.log, contains('already downloaded gc.tar.gz'));
    });

    scenario('a download cut off mid-file resumes where it stopped', (
      fx,
    ) async {
      final body = fx.files['gascity_1.4.1_linux_arm64.tar.gz']!;
      Directory('${fx.aiteamDir}/tmp').createSync(recursive: true);
      File(
        '${fx.aiteamDir}/tmp/gc.tar.gz.part',
      ).writeAsBytesSync(body.sublist(0, body.length ~/ 2));
      await fx.install();
      expect((await fx.status()).phase, TeamRuntimePhase.installed);
      expect(
        fx.served,
        contains('gascity_1.4.1_linux_arm64.tar.gz@${body.length ~/ 2}'),
      );
    });

    scenario('a program Android stops (SIGSYS) fails in plain words, and Try '
        'again downloads nothing', (fx) async {
      fx.packArchives(bd: _bdSigsysStub);
      fx.writePins();
      final result = await fx.verb(['install']);
      expect(result.exitCode, isNot(0));
      var status = await fx.status();
      expect(status.rawPhase, 'failed:blocked-syscall');
      expect(
        status.lastError,
        'Android stopped bd: this phone blocks a system call it needs (SIGSYS).',
      );
      fx.served.clear();
      await fx.verb(['install']);
      status = await fx.status();
      expect(status.rawPhase, 'failed:blocked-syscall');
      expect(fx.served, isEmpty, reason: fx.log);
    });

    scenario('install without Ubuntu, or on a 32-bit phone, says so', (
      fx,
    ) async {
      Directory(fx.rootfs).renameSync('${fx.rootfs}.away');
      var result = await fx.verb(['install']);
      expect(result.exitCode, isNot(0));
      var status = await fx.status();
      expect(status.rawPhase, 'failed:no-ubuntu');
      expect(status.lastError, contains('Ubuntu is not set up'));
      Directory('${fx.rootfs}.away').renameSync(fx.rootfs);
      result = await fx.verb(['install'], env: {'AITEAM_ARCH': 'armv7l'});
      expect(result.exitCode, isNot(0));
      status = await fx.status();
      expect(status.rawPhase, 'failed:unsupported-arch');
      expect(fx.served, isEmpty);
    });

    scenario('init makes the tuned store and adds the project with its '
        'phone-side origin and hook', (fx) async {
      await fx.install();
      await fx.init();
      final status = await fx.status();
      expect(status.phase, TeamRuntimePhase.cityReady, reason: fx.log);
      expect(status.city, 'phone');
      expect(status.rig, 'calc');
      expect(status.project, '/root/projects/calc');
      final calls = fx.gcCalls.split('\n').where((l) => l.isNotEmpty).toList();
      expect(
        calls[0],
        'init init --file ./city.toml --name phone --no-start city',
      );
      expect(calls[1], 'rig add /root/projects/calc --name calc');
      expect(calls[2], 'import install patches=no');
      final cityToml = fx.read('${fx.city}/city.toml');
      // The phone tuning: the orders left out or manual, one process per agent.
      expect(cityToml, contains(BuiltinTeam.phoneTuning));
      expect(cityToml, contains('"dolt-health", "nudge-on-route"'));
      expect(cityToml, contains('name = "reaper"\ntrigger = "manual"'));
      expect(cityToml, contains(BuiltinTeam.acpCommand));
      expect(cityToml, contains('version = "${AiTeamPins.packVersion}"'));
      expect(cityToml, contains('name = "gastown.mayor"\nsuspended = true'));
      expect(
        cityToml,
        contains(
          'name = "gastown.polecat"\ndir = "calc"\nmax_active_sessions = 1',
        ),
      );
      // Loopback only, on Termux's port.
      expect(
        fx.read('${fx.rootfs}/root/.gc/supervisor.toml'),
        TermuxTeamScripts.supervisorConfig,
      );
      expect(TermuxTeamScripts.supervisorConfig, contains('port = 8372'));
      // The origin on the phone, and the hook that brings merges in.
      final origin = '${fx.team}/origins/calc.git';
      expect(Directory(origin).existsSync(), isTrue);
      expect(
        await fx.git(['remote', 'get-url', 'origin']),
        '/root/aiteam/origins/calc.git',
      );
      expect(fx.read('$origin/hooks/post-receive'), BuiltinTeam.originHook);
      expect(
        await fx.git(['config', 'oc-mobile.project'], inside: origin),
        '/root/projects/calc',
      );
      expect(fx.read('${fx.team}/pull.log'), contains('\tcalc\tup-to-date\t'));
    });

    scenario('merged work reaches /root/projects/<project> through the hook', (
      fx,
    ) async {
      await fx.install();
      await fx.init();
      final head = await fx.git(['rev-parse', 'HEAD']);
      // The refinery's merge, as a push to the phone-side origin.
      final push = await fx.inUbuntu(
        'set -e; rm -rf /root/work; '
        'git clone -q /root/aiteam/origins/calc.git /root/work; cd /root/work; '
        'echo hi > hello.txt; git add hello.txt; '
        'git commit -qm "Add hello.txt"; git push -q origin HEAD:master',
      );
      expect(push.exitCode, 0, reason: '${push.stdout}${push.stderr}');
      expect(await fx.git(['log', '-1', '--format=%s']), 'Add hello.txt');
      expect(await fx.git(['rev-parse', 'HEAD~1']), head);
      expect(File('${fx.project}/hello.txt').readAsStringSync(), 'hi\n');
      expect(fx.read('${fx.team}/pull.log'), contains('\tbrought-in\t'));
    });

    scenario('init refuses what it cannot use, in plain words', (fx) async {
      fx.writeRig();
      var result = await fx.verb(['init', fx.projectPath]);
      expect(result.exitCode, isNot(0));
      expect((await fx.status()).rawPhase, 'failed:not-installed');
      await fx.install();
      fx.writeRig('/root/projects/nowhere');
      await fx.verb(['init', '/root/projects/nowhere']);
      expect((await fx.status()).rawPhase, 'failed:project-missing');
      Directory('${fx.rootfs}/root/projects/plain').createSync();
      fx.writeRig('/root/projects/plain');
      await fx.verb(['init', '/root/projects/plain']);
      expect((await fx.status()).rawPhase, 'failed:project-not-git');
      // A project the app did not prepare the script for.
      fx.writeRig('/root/projects/other');
      await fx.verb(['init', fx.projectPath]);
      final status = await fx.status();
      expect(status.rawPhase, 'failed:rig-script');
      expect(status.lastError, contains('did not prepare /root/projects/calc'));
      expect(fx.gcCalls, isEmpty);
    });

    scenario('init takes a Termux-side path and fixes an origin by that path '
        '(the earlier native layout)', (fx) async {
      await fx.install();
      final bare = '${fx.rootfs}/root/projects/calc.git';
      await Process.run('git', ['init', '-q', '--bare', bare]);
      await fx.git(['remote', 'add', 'origin', bare]);
      fx.writeRig();
      await fx.expectVerb(['init', fx.project]);
      expect((await fx.status()).project, '/root/projects/calc');
      expect(
        await fx.git(['remote', 'get-url', 'origin']),
        '/root/projects/calc.git',
      );
      // An origin of the project's own is kept, without the team's hook.
      expect(Directory('${fx.team}/origins').existsSync(), isFalse);
      expect(File('$bare/hooks/post-receive').existsSync(), isFalse);
    });

    scenario('start runs the supervisor in its own Ubuntu login, registers and '
        'waits; stop takes it all down', (fx) async {
      await fx.install();
      await fx.init();
      await fx.start();
      var status = await fx.status();
      expect(status.phase, TeamRuntimePhase.ready, reason: fx.log);
      expect(status.isReady, isTrue);
      expect(status.agents, 2);
      expect(status.supervisorPid, isNotNull);
      expect(fx.supervisorUp, isTrue);
      final calls = fx.gcCalls.split('\n');
      final run = calls.indexWhere((c) => c.startsWith('supervisor run'));
      final register = calls.indexWhere((c) => c.startsWith('register'));
      expect(run, greaterThan(-1));
      expect(register, greaterThan(run));
      // In the store, with the agents' opencode first on its PATH.
      expect(
        calls[run],
        startsWith(
          'supervisor run cwd=/root/aiteam/city path=/opt/aiteam/agent-bin:',
        ),
      );
      expect(calls[register], 'register /root/aiteam/city --name phone --yes');
      expect(
        fx.read(fx.prootLog),
        contains('login opencode-ubuntu -- env PATH='),
      );
      expect(fx.read(fx.prootLog), contains('sh /root/.oc-aiteam/service.sh'));
      // The upkeep order with its lock, and the hooks, from the service.
      expect(
        fx.read('${fx.city}/orders/phone-upkeep.toml'),
        BuiltinTeam.upkeepOrder,
      );
      expect(
        fx.read('${fx.city}/assets/phone-upkeep.sh'),
        BuiltinTeam.upkeepScript,
      );
      expect(fx.read('${fx.ocDir}/wake.log').trim(), 'termux-wake-lock');
      expect(
        fx.log,
        matches(
          RegExp(r'\[aiteam\] starting: Waiting for team phone \(at \d+s\)'),
        ),
      );
      // The runner outlives the start verb, in a session of its own.
      final stat = File(
        '/proc/${status.supervisorPid}/stat',
      ).readAsStringSync();
      final fields = stat.substring(stat.lastIndexOf(') ') + 2).split(' ');
      expect(fields[2], '${status.supervisorPid}', reason: stat);
      // A second start is a no-op on a healthy team.
      await fx.start();
      expect((await fx.status()).supervisorPid, status.supervisorPid);
      expect(fx.gcCalls.split('supervisor run').length - 1, 1);

      final stop = await fx.verb(['stop']);
      expect(stop.exitCode, 0, reason: '${stop.stdout}${stop.stderr}');
      status = await fx.status();
      expect(status.phase, TeamRuntimePhase.stopped);
      expect(status.supervisorPid, isNull);
      expect(fx.supervisorUp, isFalse);
      expect(fx.gcCalls, contains('supervisor stop'));
      expect(fx.read('${fx.ocDir}/wake.log'), endsWith('termux-wake-unlock\n'));
    });

    scenario(
      'a supervisor that vanished while ready reads as killed by Android, '
      'and start brings it back',
      (fx) async {
        await fx.install();
        await fx.init();
        await fx.start();
        final pid = (await fx.status()).supervisorPid!;
        Process.runSync('kill', ['-KILL', '--', '-$pid']);
        File('${fx.team}/supervisor.marker').deleteSync();
        await Future<void>.delayed(const Duration(milliseconds: 300));
        final status = await fx.status();
        expect(status.phase, TeamRuntimePhase.ready);
        expect(status.killedByAndroid, isTrue);
        expect(status.isReady, isFalse);
        await fx.start();
        final again = await fx.status();
        expect(again.isReady, isTrue);
        expect(again.killedByAndroid, isFalse);
      },
    );

    scenario('start without a team, or a supervisor that dies, fails', (
      fx,
    ) async {
      await fx.install();
      var result = await fx.verb(['start']);
      expect(result.exitCode, isNot(0));
      expect((await fx.status()).rawPhase, 'failed:no-city');
      await fx.init();
      File('${fx.rootfs}/opt/aiteam/bin/gc').writeAsStringSync(
        '#!/bin/bash\ncase "\$1" in version) echo 1.4.1;; supervisor) '
        'echo "no store here" >&2; exit 3;; esac\n',
      );
      result = await fx.verb(['start']);
      expect(result.exitCode, isNot(0));
      final status = await fx.status();
      expect(status.rawPhase, 'failed:supervisor-exited');
      expect(fx.log, contains('[supervisor] no store here'));
    });

    scenario('remove deletes the programs and the team; the project and its '
        'origin stay', (fx) async {
      await fx.install();
      await fx.init();
      await fx.start();
      // The earlier native layout's leftovers go too.
      File('${fx.bin}/gc').writeAsStringSync('old');
      File('${fx.bin}/keep-me').writeAsStringSync('x');
      final result = await fx.verb(['remove']);
      expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
      final removed = (result.stdout as String)
          .split('\n')
          .where((l) => l.startsWith('[aiteam] removed '))
          .map((l) => l.substring('[aiteam] removed '.length))
          .toSet();
      expect(
        removed,
        containsAll([
          '/usr/local/bin/gc',
          '/usr/local/bin/bd',
          '/usr/local/bin/dolt',
          '/opt/aiteam',
          '/root/.oc-aiteam',
          '/root/.gc',
          '/root/aiteam/city',
          '/root/aiteam/city.toml',
          '${fx.bin}/gc',
          fx.aiteamDir,
        ]),
      );
      expect(result.stdout, contains('kept /root/aiteam/origins'));
      expect(fx.supervisorUp, isFalse);
      expect(Directory('${fx.rootfs}/opt/aiteam').existsSync(), isFalse);
      expect(Directory(fx.city).existsSync(), isFalse);
      expect(File('${fx.project}/calc.py').existsSync(), isTrue);
      expect(Directory('${fx.team}/origins/calc.git').existsSync(), isTrue);
      expect(File('${fx.bin}/keep-me').existsSync(), isTrue);
      final status = await fx.status();
      expect(status.phase, TeamRuntimePhase.idle);
      expect(status.installed, isFalse);
      expect(status.removed, containsAll(removed));
      // Set up again: the project keeps its origin and gets its hook back.
      await fx.install();
      await fx.init();
      expect((await fx.status()).phase, TeamRuntimePhase.cityReady);
    });

    scenario('remove never deletes a foreign opencode in \$PREFIX/bin', (
      fx,
    ) async {
      await fx.install();
      File('${fx.bin}/opencode').writeAsStringSync('#!/bin/bash\necho real\n');
      await fx.verb(['remove']);
      expect(File('${fx.bin}/opencode').existsSync(), isTrue);
    });

    scenario('a verb that dies mid-way reads as failed, not busy forever', (
      fx,
    ) async {
      await fx.install();
      File('${fx.aiteamDir}/state').writeAsStringSync(
        'phase=downloading\nmessage=Downloading gc\nverb=install\npid=999999\n'
        'supervisor_pid=\nupdated_at=1\n',
      );
      final status = await fx.status();
      expect(status.phase, TeamRuntimePhase.failed);
      expect(status.busy, isFalse);
      expect(status.rawPhase, 'failed:interrupted');
    });

    scenario('every verb appends to aiteam.log and the OpenCode install log', (
      fx,
    ) async {
      File('${fx.ocDir}/manager.sh').writeAsStringSync(
        '#!/bin/bash\n[ "\$1" = write-log ] && cat >> "\$HOME/.oc/install.log"\n',
      );
      Process.runSync('chmod', ['755', '${fx.ocDir}/manager.sh']);
      await fx.install();
      await Future<void>.delayed(const Duration(milliseconds: 200));
      expect(fx.log, contains('[aiteam] install started at'));
      expect(fx.read('${fx.ocDir}/install.log'), contains('install finished'));
    });

    scenario(
      'the bridge dispatch writes the script, the pins and the project\'s '
      'script, queues, detaches and refuses while busy',
      (fx) async {
        final dispatch = await fx.dispatch('install');
        expect(
          dispatch.exitCode,
          0,
          reason: '${dispatch.stdout}${dispatch.stderr}',
        );
        expect(dispatch.stdout, matches(RegExp(r'aiteam-started:[0-9]+')));
        expect(fx.read('${fx.ocDir}/aiteam-pins'), fx.pins());
        expect((await fx.status()).busy, isTrue);
        final busy = await fx.dispatch('start');
        expect(busy.exitCode, 75);
        expect(busy.stderr, contains('aiteam-busy:install:'));
        final done = await fx.waitIdle();
        expect(
          done.phase,
          TeamRuntimePhase.installed,
          reason: '$done\n${fx.log}',
        );
        final init = await fx.dispatch('init', args: [fx.projectPath]);
        expect(init.exitCode, 0, reason: '${init.stdout}${init.stderr}');
        expect(
          fx.read('${fx.aiteamDir}/rig.sh'),
          TermuxTeamScripts.rigFile(fx.projectPath),
        );
        expect(
          (await fx.waitIdle()).phase,
          TeamRuntimePhase.cityReady,
          reason: fx.log,
        );
        await fx.dispatch('start');
        final ready = await fx.waitIdle();
        expect(ready.isReady, isTrue, reason: fx.log);
        await fx.dispatch('stop');
        expect(
          (await fx.waitIdle()).phase,
          TeamRuntimePhase.stopped,
          reason: fx.log,
        );
      },
    );
  });

  group('TermuxTeamRuntime over the oc/termux channel', skip: skip, () {
    setUp(() async => fx = await _Fixture.create());
    tearDown(() async {
      debugPlatformCapabilities = null;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('oc/termux'), null);
      await fx.dispose();
    });

    test('Set up takes the team from idle to running and gives the Termux '
        'profile its loopback config', () async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final sent = <String>[];
      // Termux, played by bash in the fixture. The phone's network is the
      // fixture server: the pins the app sends are swapped for the fixture's.
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('oc/termux'), (
            call,
          ) async {
            if (call.method != 'runInTermux') return null;
            final script = (call.arguments as Map)['script'] as String;
            sent.add(script);
            final result = await Process.run('bash', [
              '-c',
              script.replaceFirst(TermuxTeamScripts.pinsFile(), fx.pins()),
            ], environment: fx.environment);
            return <String, Object>{
              'stdout': result.stdout as String,
              'stderr': result.stderr as String,
              'exitCode': result.exitCode,
              'err': -1,
              'errorMessage': '',
            };
          });
      final runtime = TermuxTeamRuntime(
        archProbe: () async => 'aarch64',
        pollInterval: const Duration(milliseconds: 200),
      );
      expect(await runtime.supportsAiTeam, isTrue);
      expect((await runtime.status()).phase, TeamRuntimePhase.idle);
      final installed = await runtime.install();
      expect(installed.phase, TeamRuntimePhase.installed, reason: fx.log);
      final city = await runtime.init(fx.projectPath);
      expect(city.phase, TeamRuntimePhase.cityReady, reason: fx.log);
      final ready = await runtime.start();
      expect(ready.isReady, isTrue, reason: fx.log);
      expect(ready.city, 'phone');
      final config = runtime.phoneOrchestrationConfig(ready);
      expect(config.hostMode, OrchestrationHostMode.phone);
      expect(config.url, fx.supervisorUrl);
      // What went to Termux named the upstream builds, never the old release.
      expect(sent.join(), isNot(contains('aiteam-assets-1')));
      expect(sent.first, contains(AiTeamPins.arm64.first.url));
      final stopped = await runtime.stop();
      expect(stopped.phase, TeamRuntimePhase.stopped, reason: fx.log);
    });
  });
}
