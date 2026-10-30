// Fakes for the census part `h-termux`: one scriptable `oc/termux` channel
// (capabilities, the bridge probe, the installation inventory, the setup
// manager's status, the tools script's storage and process verbs, and
// claude.sh), the phone's saved servers, and sample data. Nothing here runs
// a command or touches a device.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';

Map<String, Object> termuxResult(String stdout) => {
  'stdout': stdout,
  'stderr': '',
  'exitCode': 0,
  'err': -1,
  'errorMessage': '',
};

/// The Termux side of the phone, as the channel answers it.
class TermuxFake {
  /// Termux itself.
  bool installed = true;
  bool protocolSupported = true;
  bool permissionGranted = true;

  /// Never answers `getCapabilities` (the screen stays on "Checking").
  bool hangCapabilities = false;

  /// The bridge probe times out (the unlock command was not pasted).
  bool bridgeLocked = false;

  /// `ubuntu=… version=… runtime=…`, or a failure when [inventoryFails].
  String inventory = 'ubuntu=absent\nversion=\n';
  bool inventoryFails = false;

  /// The manager's `status` (with `__OC_SETUP_OUTPUT__` and the log).
  String status = idleStatus;

  /// `storage-summary`, `storage-status` and `procs-scan` answers.
  String storageSummary =
      'state=done\ntotal_bytes=6442450944\nscanned_at=1788800000\n';
  String storageStatus = 'state=idle\n';
  String processes = sampleProcessesJson();

  /// claude.sh `status` (null: unreadable, so nothing shows), `projects`
  /// and the install log.
  String? claudeStatus;
  String claudeProjects =
      'project=/root/projects/shopfront\n'
      'project=/root/projects/my-first-project\n';
  String claudeLog = '';

  final _hang = Completer<Object?>();

  Future<Object?> handle(MethodCall call) async {
    switch (call.method) {
      case 'getCapabilities':
        if (hangCapabilities) return _hang.future;
        return <String, Object>{
          'installed': installed,
          'version': installed ? '0.118.3' : '',
          'serviceAvailable': installed,
          'protocolSupported': protocolSupported,
          'permissionGranted': permissionGranted,
        };
      case 'runInTermux':
        return _run((call.arguments as Map)['script'] as String);
      case 'getSigningCertificateSha256':
        return null;
    }
    return true;
  }

  Future<Object?> _run(String script) async {
    if (script.contains('claude/install.log')) return termuxResult(claudeLog);
    if (script.contains(r'"$CLAUDE" status')) {
      final value = claudeStatus;
      if (value == null) {
        throw PlatformException(code: 'command_failed', message: 'no status');
      }
      return termuxResult(value);
    }
    if (script.contains(r'"$CLAUDE" projects')) {
      return termuxResult(claudeProjects);
    }
    if (script.contains(r'"$CLAUDE"')) return termuxResult('');
    if (script.contains("printf 'opencode-bridge-ok'")) {
      if (bridgeLocked) {
        throw PlatformException(
          code: 'command_timeout',
          message: 'Termux did not answer.',
        );
      }
      return termuxResult('opencode-bridge-ok');
    }
    if (script.contains('ubuntu=absent')) {
      if (inventoryFails) {
        throw PlatformException(code: 'command_timeout', message: 'timeout');
      }
      return termuxResult(inventory);
    }
    if (script.contains('server.password')) return termuxResult('');
    if (script.contains(r'exec "$TOOLS" storage-summary')) {
      return termuxResult(storageSummary);
    }
    if (script.contains(r'exec "$TOOLS" storage-status')) {
      return termuxResult(storageStatus);
    }
    if (script.contains(r'exec "$TOOLS" procs-scan')) {
      return termuxResult(processes);
    }
    if (script == TermuxBridge.statusScript() ||
        script == TermuxBridge.setupSnapshotScript() ||
        script.contains('__OC_SETUP_OUTPUT__')) {
      return termuxResult(status);
    }
    return termuxResult('');
  }
}

// ---------------------------------------------------------------------------
// Manager status lines
// ---------------------------------------------------------------------------

const idleStatus =
    'phase=idle\nmessage=No setup has been started\nport=4096\nrunner=\n'
    'version=\npid=\n__OC_SETUP_OUTPUT__\n';

String managerStatus({
  required String phase,
  required String message,
  String version = '',
  String runtime = '',
  String pid = '123',
  String extra = '',
  String output = '',
}) =>
    'phase=$phase\nmessage=$message\nport=4096\nrunner=proot\n'
    'version=$version\n${runtime.isEmpty ? '' : 'runtime=$runtime\n'}'
    'pid=$pid\n${extra}__OC_SETUP_OUTPUT__\n$output';

const setupLog = '''[oc] existing Termux dependencies are healthy
[oc] Ubuntu environment is ready
[oc] Installing OpenCode 2.1.1
Resolving packages…
Downloading opencode-linux-arm64
  32 MB / 68 MB
  48 MB / 68 MB
npm WARN network connection slow; retrying
Download resumed
  64 MB / 68 MB
  68 MB / 68 MB
[oc] Checking the installed command
OpenCode 2.1.1
[oc] Refreshing the model catalog
Fetching available models…''';

const ubuntuLog = '''[oc] Updating Termux packages
Get:1 https://packages-cf.termux.dev/apt/termux-main stable InRelease
Reading package lists... Done
[oc] Installing proot-distro
Setting up proot-distro (4.18.0) ...
[oc] Downloading Ubuntu 24.04 (arm64)
[*] Downloading rootfs tarball...
  41.2 MiB / 58.9 MiB''';

const failedLog = '''[oc] Ubuntu environment is ready
[oc] Installing OpenCode 2.1.1
npm error code ECONNRESET
npm error network aborted
npm error network This is a problem related to network connectivity.
[oc] ERROR: npm install failed (exit 1)''';

// ---------------------------------------------------------------------------
// Saved servers
// ---------------------------------------------------------------------------

/// The phone's own OpenCode 2 server.
ServerProfile phoneProfileV2() => ServerProfile(
  id: 'phone-oc2',
  name: 'This device (Termux)',
  baseUrl: TermuxBridge.managedServerUrl,
  flavor: ServerFlavor.v2,
  serverVersion: '2.0.10',
  password: 'synthetic-phone',
)..username = 'opencode';

ServerProfile laptopProfile() => ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);

// ---------------------------------------------------------------------------
// Running now and Storage
// ---------------------------------------------------------------------------

Map<String, Object> _proc(
  int pid,
  int ppid,
  String group,
  String name,
  String cmd, {
  double cpu = 0,
  int cpuSeconds = 5,
  int rssKb = 20480,
  int elapsed = 3600,
  String cwd = '/root/projects/shopfront',
  String? orphan,
  bool protected = false,
}) => {
  'pid': pid,
  'ppid': ppid,
  'group': group,
  'name': name,
  'cmd': cmd,
  'cpu_pct': cpu,
  'cpu_seconds': cpuSeconds,
  'rss_kb': rssKb,
  'elapsed_s': elapsed,
  'cwd': cwd,
  'orphan_reason': ?orphan,
  'protected': protected,
};

/// A busy phone: the server and its MCP helper, a runaway orphan, a Gradle
/// daemon, an AI Team and a stray watcher.
String sampleProcessesJson() => jsonEncode([
  _proc(
    100,
    1,
    'opencode_server',
    'manager.sh',
    'bash ~/.oc/manager.sh setup 4096',
    cwd: '/data/data/com.termux/files/home',
  ),
  _proc(
    101,
    100,
    'opencode_server',
    'opencode',
    'opencode serve --hostname 127.0.0.1 --port 4096',
    cpu: 4.2,
    cpuSeconds: 60,
    rssKb: 190000,
    protected: true,
  ),
  _proc(
    102,
    101,
    'opencode_server',
    'node',
    'node /root/.npm/_npx/abc/node_modules/.bin/some-mcp',
    rssKb: 30000,
  ),
  _proc(
    200,
    1,
    'orphans',
    'minimax-coding-plan-mcp',
    'node /root/.npm/_npx/xyz/node_modules/minimax-coding-plan-mcp/dist/index.js',
    cpu: 99,
    cpuSeconds: 3723,
    rssKb: 45000,
    elapsed: 3900,
    orphan: 'cpu_no_owner',
  ),
  _proc(
    300,
    1,
    'build_daemons',
    'java',
    'java -Xmx2g org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.5',
    cpu: 1,
    cpuSeconds: 7200,
    rssKb: 300000,
    elapsed: 7200,
  ),
  _proc(400, 1, 'ai_team', 'gc', 'gc start', rssKb: 20000),
  _proc(401, 400, 'ai_team', 'dolt', 'dolt sql-server', rssKb: 40000),
  _proc(
    402,
    400,
    'ai_team',
    'opencode',
    'opencode acp',
    cpu: 0.5,
    rssKb: 50000,
  ),
  _proc(
    800,
    700,
    'other',
    'node',
    'node build/watch.js',
    cpu: 3,
    cpuSeconds: 30,
    rssKb: 5000,
  ),
]);

String storageReportJson() => jsonEncode({
  'scanned_at': 1788800000,
  'cleanup_policy': 2,
  'stale': false,
  'total_bytes': 6442450944,
  'categories': [
    {
      'key': 'build_caches',
      'bytes': 734003200,
      'deletable': true,
      'note_key': 'termuxStorageNoteBuildCaches',
      'paths': [
        {
          'path': '/data/data/com.termux/files/home/.npm/_cacache',
          'bytes': 524288000,
        },
        {
          'path': '/data/data/com.termux/files/home/.gradle/caches',
          'bytes': 209715200,
        },
      ],
    },
    {
      'key': 'projects',
      'bytes': 2147483648,
      'deletable': false,
      'paths': [
        {
          'path': '/data/data/com.termux/files/home/.oc/projects/shopfront',
          'bytes': 2147483648,
        },
      ],
    },
    {
      'key': 'ai_team',
      'bytes': 419430400,
      'deletable': false,
      'note_key': 'termuxStorageNoteAiTeam',
      'paths': [
        {
          'path': '/data/data/com.termux/files/home/.oc/aiteam',
          'bytes': 419430400,
        },
      ],
    },
    {
      'key': 'shared_caches',
      'bytes': 314572800,
      'deletable': false,
      'note_key': 'termuxStorageNoteSharedCaches',
      'paths': [
        {'path': '/data/data/com.termux/files/home/.cache', 'bytes': 314572800},
      ],
    },
  ],
  'projects': [],
});

const storageScanLog = '''Measuring Ubuntu (proot-distro)…
  /data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu
Measuring caches…
  ~/.npm/_cacache  500 MB
  ~/.gradle/caches  200 MB
Measuring projects…
  ~/.oc/projects/shopfront''';

// ---------------------------------------------------------------------------
// Claude Code (claude.sh status)
// ---------------------------------------------------------------------------

String claudeStatusLine({
  required String phase,
  bool installed = false,
  bool busy = false,
  String step = '',
  String verb = '',
  String signedIn = 'no',
  String message = '',
  String failureKind = '',
}) =>
    'phase=$phase\nmessage=$message\nbusy=${busy ? 'yes' : 'no'}\n'
    'verb=$verb\nstep=$step\ninstalled=${installed ? 'yes' : 'no'}\n'
    '${installed ? 'node_version=v24.21.0\npaseo_version=0.8.0\nclaude_version=2.1.278\n' : ''}'
    'port=6767\nsigned_in=$signedIn\n'
    '${failureKind.isEmpty ? '' : 'failure_kind=$failureKind\n'}';

const claudeInstallLog = '''[claude] Installing Node.js 24 inside Ubuntu
Get:1 https://deb.nodesource.com/node_24.x nodistro InRelease
Setting up nodejs (24.21.0-1nodesource1) ...
[claude] Node.js v24.21.0
[claude] Installing the Paseo daemon 0.8.0
added 212 packages in 41s''';
