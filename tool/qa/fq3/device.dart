import 'dart:async';
import 'dart:io';

import 'common.dart';

const package = 'io.github.eslamasabry.opencode_mobile';
const serial = 'emulator-5554';
const appFiles = '/data/user/0/$package/files';
const rootfs = '$appFiles/linux/ubuntu';
String quote(String value) => "'${value.replaceAll("'", "'\\''")}'";

/// Called only while the outer runner holds the shared emulator flock.
class PhoneRuntime {
  final int uid;
  final String native;
  final String runID;
  final String password;
  final int appBuild;
  final List<int> _forwards = [];
  String? lastServerKind;
  Process? _server;
  int? _serverPID;
  String? _serverStart;
  String get project => '$appFiles/projects/$runID';
  String get directory => '/root/projects/$runID';
  String get scratch => '$appFiles/linux/fq3-$runID';
  PhoneRuntime._(
    this.uid,
    this.native,
    this.runID,
    this.password,
    this.appBuild,
  );

  static Future<ProcessResult> adb(
    List<String> args, {
    Duration timeout = const Duration(seconds: 15),
  }) async {
    try {
      return await Process.run('adb', ['-s', serial, ...args]).timeout(timeout);
    } catch (_) {
      throw const ProbeFailure('adb_unavailable');
    }
  }

  static Future<String> output(List<String> args) async {
    final value = await adb(args);
    if (value.exitCode != 0) throw const ProbeFailure('device_command_failed');
    return value.stdout.toString().trim();
  }

  /// Restore only the approved normal APK, retaining all application data.
  /// Caller holds the same shared emulator lock as the device operation.
  static Future<void> restoreNormalApp() async {
    Future<int?> installed() async {
      final info = await output(['shell', 'dumpsys', 'package', package]);
      return int.tryParse(
        RegExp(r'versionCode=(\d+)').firstMatch(info)?.group(1) ?? '',
      );
    }

    if (await installed() == 2196) return;
    const apk = '/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk';
    if (!File(apk).existsSync() || FileSystemEntity.isLinkSync(apk)) {
      throw const ProbeFailure('normal_apk_unavailable');
    }
    try {
      final manifest = await Process.run(
        '/home/eslam/Android/Sdk/build-tools/36.1.0/aapt',
        ['dump', 'badging', apk],
      ).timeout(const Duration(seconds: 30));
      if (manifest.exitCode != 0 ||
          !manifest.stdout.toString().contains(
            "package: name='$package' versionCode='2196'",
          )) {
        throw const ProbeFailure('normal_apk_build_mismatch');
      }
      final certificate = await Process.run(
        '/home/eslam/Android/Sdk/build-tools/36.1.0/apksigner',
        ['verify', '--print-certs', apk],
      ).timeout(const Duration(seconds: 30));
      if (certificate.exitCode != 0 ||
          !certificate.stdout.toString().toLowerCase().contains(
            'certificate sha-256 digest: '
            '1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c',
          )) {
        throw const ProbeFailure('normal_apk_signer_mismatch');
      }
      final update = await adb([
        'install',
        '-r',
        '-d',
        apk,
      ], timeout: const Duration(seconds: 180));
      if (update.exitCode != 0 || await installed() != 2196) {
        throw const ProbeFailure('normal_apk_restore_failed');
      }
    } on ProbeFailure {
      rethrow;
    } catch (_) {
      throw const ProbeFailure('normal_apk_restore_failed');
    }
  }

  static Future<PhoneRuntime> inspect(String runID) async {
    if (!RegExp(r'^fq3-[a-zA-Z0-9_-]{1,80}$').hasMatch(runID)) {
      throw const ProbeFailure('invalid_run_id');
    }
    if (await output(['get-state']) != 'device') {
      throw const ProbeFailure('device_unavailable');
    }
    final packageInfo = await output(['shell', 'dumpsys', 'package', package]);
    final build = int.tryParse(
      RegExp(r'versionCode=(\d+)').firstMatch(packageInfo)?.group(1) ?? '',
    );
    if (build != 2196) throw const ProbeFailure('installed_build_changed');
    final listing = await output([
      'shell',
      'cmd',
      'package',
      'list',
      'packages',
      '-U',
      package,
    ]);
    final match = RegExp(
      '^package:${RegExp.escape(package)} uid:(\\d+)\$',
      multiLine: true,
    ).firstMatch(listing);
    final uid = int.tryParse(match?.group(1) ?? '');
    if (uid == null || uid < 10000) {
      throw const ProbeFailure('app_uid_unavailable');
    }
    final path = await output(['shell', 'pm', 'path', package]);
    if (!RegExp(r'^package:/data/app/[^\n]+/base\.apk$').hasMatch(path)) {
      throw const ProbeFailure('native_path_unavailable');
    }
    final native = '${path.substring(8, path.length - 9)}/lib/x86_64';
    // This is the runtime's launch config, not Keystore/secure storage.
    final result = await adb([
      'exec-out',
      'su',
      '$uid',
      'cat',
      '$rootfs/root/.oc-builtin/server.password',
    ]);
    final password = result.stdout.toString();
    if (result.exitCode != 0 ||
        password.isEmpty ||
        password.length > 512 ||
        password.contains('\n') ||
        password.contains('\r')) {
      throw const ProbeFailure('runtime_password_unavailable');
    }
    final runtime = PhoneRuntime._(uid, native, runID, password, build!);
    final actual = await runtime.asApp('id -u');
    if (actual != '$uid') throw const ProbeFailure('app_uid_not_applied');
    await runtime.asApp(
      'umask 077; mkdir -p ${quote(runtime.scratch)} ${quote(runtime.project)}',
    );
    return runtime;
  }

  Future<String> asApp(String command) =>
      output(['shell', 'su $uid sh -c ${quote(command)}']);

  String proot(String script) {
    final args = [
      '$native/libproot.so',
      '--root-id',
      '--kill-on-exit',
      '--link2symlink',
      '-L',
      '--sysvipc',
      '--rootfs=$rootfs',
      '--bind=/dev',
      '--bind=/proc',
      '--bind=/sys',
      '--bind=$rootfs/tmp:/dev/shm',
      for (final name in ['stat', 'loadavg', 'uptime', 'version', 'vmstat'])
        '--bind=$appFiles/linux/proc/$name:/proc/$name',
      '--bind=$appFiles/projects:/root/projects',
      '--cwd=$directory',
      '/usr/bin/env',
      '-i',
      'HOME=/root',
      'LANG=C.UTF-8',
      'PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
      'TERM=xterm-256color',
      'TMPDIR=/tmp',
      '/bin/sh',
      '-c',
      script,
    ];
    return 'export PROOT_LOADER=${quote('$native/libproot-loader.so')} '
        'PROOT_TMP_DIR=${quote(scratch)} LD_LIBRARY_PATH=${quote(native)} '
        '; exec ${args.map(quote).join(' ')}';
  }

  Future<String?> version(bool oc2) async {
    final binary = oc2 ? 'opencode2' : 'opencode';
    final response = await adb([
      'shell',
      'su $uid sh -c ${quote(proot('$binary --version 2>/dev/null'))}',
    ], timeout: const Duration(seconds: 30));
    final raw = response.stdout.toString().trim();
    final text = raw.replaceFirst(RegExp(r'^opencode(?:2)? v'), '');
    // Ignore any arbitrary CLI output (including provider or startup errors).
    return response.exitCode == 0 &&
            RegExp(r'^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$').hasMatch(text)
        ? text
        : null;
  }

  Future<String> start(
    bool oc2, {
    bool appManagedOnly = false,
    bool forceOwned = false,
  }) async {
    if (appManagedOnly && forceOwned) {
      throw const ProbeFailure('invalid_server_selection');
    }
    await stop();
    lastServerKind = null;
    // Reuse the app-managed service when it already runs this exact dialect.
    final activePort = int.tryParse(
      await output(['forward', 'tcp:0', 'tcp:4097']),
    );
    if (activePort != null && !forceOwned) {
      final endpoint = 'http://127.0.0.1:$activePort';
      final active = Fq3Wire(baseUrl: endpoint, password: password);
      try {
        final health = await active
            .request('GET', oc2 ? '/api/health' : '/global/health')
            .timeout(const Duration(seconds: 3));
        if (health is Map &&
            health['version'] == (oc2 ? '2.0.10' : '1.18.32')) {
          _forwards.add(activePort);
          lastServerKind = 'app-managed';
          return endpoint;
        }
      } catch (_) {
        /* Other dialect or unavailable: owned service below. */
      } finally {
        await active.close();
      }
      await adb(['forward', '--remove', 'tcp:$activePort']);
    } else if (activePort != null) {
      await adb(['forward', '--remove', 'tcp:$activePort']);
    }
    if (appManagedOnly) {
      throw const ProbeFailure('app_managed_engine_unavailable');
    }
    lastServerKind = 'owned';
    await asApp('rm -f ${quote('$scratch/pid')}');
    final socket = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final devicePort = socket.port;
    await socket.close();
    final shell = StringBuffer('set -eu\n');
    if (oc2) {
      shell.write(
        'unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n'
        'export XDG_DATA_HOME=/root/.oc-opencode2/data\n'
        'export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n'
        'export XDG_STATE_HOME=/root/.oc-opencode2/state\n'
        'export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n'
        'export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n'
        'export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n',
      );
    }
    shell.write(
      'password=\$(cat /root/.oc-builtin/server.password)\n'
      'export OPENCODE_SERVER_USERNAME=opencode\n'
      'export OPENCODE_SERVER_PASSWORD="\$password" OPENCODE_PASSWORD="\$password"\n'
      'unset password\n'
      'exec ${oc2 ? 'opencode2' : 'opencode'} serve --hostname 127.0.0.1 --port $devicePort\n',
    );
    final wrapper =
        'umask 077; echo \$\$ > ${quote('$scratch/pid')}; '
        '${proot(shell.toString())} >/dev/null 2>&1';
    _server = await Process.start('adb', [
      '-s',
      serial,
      'shell',
      'su $uid sh -c ${quote(wrapper)}',
    ]);
    _server!.stdout.drain<void>();
    _server!.stderr.drain<void>();
    for (var attempt = 0; attempt < 20; attempt++) {
      final pid = await asApp(
        'cat ${quote('$scratch/pid')} 2>/dev/null || true',
      );
      if (RegExp(r'^\d+$').hasMatch(pid)) {
        _serverPID = int.parse(pid);
        _serverStart = await output([
          'shell',
          'cat /proc/$pid/stat 2>/dev/null || true',
        ]);
        break;
      }
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    final forwarded = await output(['forward', 'tcp:0', 'tcp:$devicePort']);
    final localPort = int.tryParse(forwarded);
    if (localPort == null) throw const ProbeFailure('forward_failed');
    _forwards.add(localPort);
    final endpoint = 'http://127.0.0.1:$localPort';
    final deadline = DateTime.now().add(const Duration(seconds: 60));
    while (DateTime.now().isBefore(deadline)) {
      final wire = Fq3Wire(baseUrl: endpoint, password: password);
      try {
        final health = await wire
            .request('GET', oc2 ? '/api/health' : '/global/health')
            .timeout(const Duration(seconds: 3));
        if (health is Map && health['version'] is String) return endpoint;
      } catch (_) {
        /* Bounded readiness, no response logging. */
      } finally {
        await wire.close();
      }
      await Future<void>.delayed(const Duration(milliseconds: 500));
    }
    throw const ProbeFailure('runtime_start_failed');
  }

  static String? startIdentity(String stat) {
    final end = stat.lastIndexOf(') ');
    if (end < 0) return null;
    return stat.substring(end + 2).split(' ').elementAtOrNull(19);
  }

  Future<void> stop() async {
    final owned = <int, String>{};
    Future<void> capture(int pid, int depth) async {
      if (depth > 12 || owned.length >= 128 || owned.containsKey(pid)) return;
      final stat = await output([
        'shell',
        'cat /proc/$pid/stat 2>/dev/null || true',
      ]);
      final identity = startIdentity(stat);
      if (identity == null) return;
      owned[pid] = identity;
      final children = await output([
        'shell',
        'cat /proc/$pid/task/$pid/children 2>/dev/null || true',
      ]);
      for (final token in children.split(RegExp(r'\s+'))) {
        final child = int.tryParse(token);
        if (child != null) await capture(child, depth + 1);
      }
    }

    if (_server != null) {
      final listing = await output(['shell', 'ps -A -o PID,UID,COMM']);
      for (final line in listing.split('\n').skip(1)) {
        final fields = line.trim().split(RegExp(r'\s+'));
        if (fields.length < 3 || int.tryParse(fields[1]) != uid) continue;
        final pid = int.tryParse(fields[0]);
        if (pid == null) continue;
        final raw = await adb(['exec-out', 'cat', '/proc/$pid/cmdline']);
        if (raw.exitCode != 0) continue;
        final args = raw.stdout.toString().split('\u0000');
        if (args.contains('--cwd=$directory') &&
            args.contains('--rootfs=$rootfs')) {
          await capture(pid, 0);
        }
      }
    }
    if (_serverPID != null && _serverStart != null) {
      final stat = await output([
        'shell',
        'cat /proc/$_serverPID/stat 2>/dev/null || true',
      ]);
      final raw = await adb(['exec-out', 'cat', '/proc/$_serverPID/cmdline']);
      final args = raw.stdout.toString().split('\u0000');
      if (raw.exitCode == 0 &&
          args.contains('--cwd=$directory') &&
          args.contains('--rootfs=$rootfs') &&
          startIdentity(stat) != null &&
          startIdentity(stat) == startIdentity(_serverStart!)) {
        await capture(_serverPID!, 0);
      }
    }
    for (final signal in ['TERM', 'KILL']) {
      for (final pid in owned.keys.toList().reversed) {
        final current = await output([
          'shell',
          'cat /proc/$pid/stat 2>/dev/null || true',
        ]);
        if (startIdentity(current) == owned[pid]) {
          await output(['shell', 'kill -$signal $pid 2>/dev/null || true']);
        }
      }
      if (signal == 'TERM') {
        await Future<void>.delayed(const Duration(milliseconds: 200));
      }
    }
    for (final pid in owned.keys) {
      final current = await output([
        'shell',
        'cat /proc/$pid/stat 2>/dev/null || true',
      ]);
      final tail = current.contains(') ')
          ? current.substring(current.lastIndexOf(') ') + 2)
          : '';
      if (startIdentity(current) == owned[pid] && !tail.startsWith('Z ')) {
        throw const ProbeFailure('owned_process_cleanup_failed');
      }
    }
    _server?.kill();
    _server = null;
    _serverPID = null;
    _serverStart = null;
    for (final port in _forwards) {
      await adb(['forward', '--remove', 'tcp:$port']);
    }
    _forwards.clear();
  }

  Future<void> close() async {
    await stop();
    // Only this run's empty project/scratch; existing app histories stay intact.
    await asApp(
      'rmdir ${quote(project)} 2>/dev/null || true; rm -f ${quote('$scratch/pid')}; rmdir ${quote(scratch)} 2>/dev/null || true',
    );
  }
}
