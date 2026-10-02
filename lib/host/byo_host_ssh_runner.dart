import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';

import '../builtin/builtin_linux.dart';
import '../domain/byo_host.dart';

/// Native output is deliberately ignored: every operation redirects output to
/// an app-private file before invoking a program which may print credentials.
abstract interface class ByoHostLocalShell {
  Future<bool> available();
  Future<int> run(String script, {required Duration timeout});
  Future<void> start(String name, String script, int port);
  Future<void> stop(String name);
  Future<bool> running(String name);
}

class BuiltinByoHostLocalShell implements ByoHostLocalShell {
  BuiltinByoHostLocalShell({BuiltinLinux? linux})
    : _linux = linux ?? BuiltinLinux();
  final BuiltinLinux _linux;

  @override
  Future<bool> available() async {
    // Termux has no reviewed private shared-files transport. Never fall back.
    if (!BuiltinLinux.supported) return false;
    return (await _linux.status()).installed;
  }

  @override
  Future<int> run(String script, {required Duration timeout}) async =>
      (await _linux.run(script, timeout: timeout)).exitCode;
  @override
  Future<void> start(String name, String script, int port) =>
      _linux.startService(
        name,
        script,
        port: port,
        notice: 'Private machine connection',
      );
  @override
  Future<void> stop(String name) => _linux.stopService(name);
  @override
  Future<bool> running(String name) async =>
      (await _linux.status()).services.contains(name);
}

/// OpenSSH inside the Android built-in Ubuntu. No process receives a secret in
/// its argv, and no secret passes through the logging MethodChannel result.
/// The caller must serialize operations per profile and keep identities in its
/// secure vault. Non-Android/Termux setups return unavailable.
class BuiltinByoHostSshRunner implements ByoHostSshRunner {
  BuiltinByoHostSshRunner({
    ByoHostLocalShell? shell,
    Future<Directory> Function()? supportDirectory,
    HttpClient Function()? httpClient,
  }) : _shell = shell ?? BuiltinByoHostLocalShell(),
       _supportDirectory = supportDirectory ?? getApplicationSupportDirectory,
       _httpClient = httpClient ?? HttpClient.new;

  final ByoHostLocalShell _shell;
  final Future<Directory> Function() _supportDirectory;
  final HttpClient Function() _httpClient;
  final Set<_SshTunnel> _tunnels = {};
  final Set<String> _activePaths = {};
  final Random _random = Random.secure();
  bool _disposed = false;
  static const _short = Duration(seconds: 40);

  String _nonce() => List.generate(
    12,
    (_) => _random.nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();

  @override
  Future<bool> available() async {
    try {
      return !_disposed && await _shell.available();
    } catch (_) {
      return false;
    }
  }

  Future<_PrivateWork> _work(String profileId) async {
    try {
      return await _newWork(profileId);
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
  }

  String _profilePrefix(String profileId) =>
      'oc-byo-${sha256.convert(utf8.encode(profileId)).toString().substring(0, 16)}-';

  Future<Directory> _privateRoot(String profileId) async {
    if (!byoHostSafeId(profileId)) {
      throw const ByoHostFailure(ByoHostFailureCode.invalidTarget);
    }
    if (!await available()) {
      throw const ByoHostFailure(ByoHostFailureCode.unavailable);
    }
    final support = await _supportDirectory();
    final root = Directory('${support.path}/linux/ubuntu');
    // Reject symlinks in the shared-files boundary, including /tmp itself.
    for (final path in [
      '${support.path}/linux',
      root.path,
      '${root.path}/tmp',
    ]) {
      if (await FileSystemEntity.type(path, followLinks: false) !=
          FileSystemEntityType.directory) {
        throw const ByoHostFailure(ByoHostFailureCode.storage);
      }
    }
    return root;
  }

  Future<void> _cleanupProfile(String profileId, Directory root) async {
    final prefix = _profilePrefix(profileId);
    // Recover only this profile's abandoned directories. Stop the exact native
    // tunnel service before deleting its key; never match processes by pattern.
    await for (final entity in Directory(
      '${root.path}/tmp',
    ).list(followLinks: false)) {
      final leaf = entity.uri.pathSegments.where((s) => s.isNotEmpty).last;
      if (!RegExp(
            '^${RegExp.escape(prefix)}'
            r'[a-f0-9]{24}$',
          ).hasMatch(leaf) ||
          _activePaths.contains(entity.path)) {
        continue;
      }
      if (await FileSystemEntity.type(entity.path, followLinks: false) !=
          FileSystemEntityType.directory) {
        continue;
      }
      await _shell.stop('byo-${leaf.substring(prefix.length)}');
      await entity.delete(recursive: true);
    }
  }

  /// Called before deleting profile metadata/vault after a restart, even when
  /// no reconnect was attempted. The controller closes its live tunnel first;
  /// another in-flight operation's private files must never be removed here.
  @override
  Future<void> cleanup(String profileId) async {
    try {
      await _cleanupProfile(profileId, await _privateRoot(profileId));
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
  }

  Future<_PrivateWork> _newWork(String profileId) async {
    final root = await _privateRoot(profileId);
    await _cleanupProfile(profileId, root);
    final prefix = _profilePrefix(profileId);
    final name = '$prefix${_nonce()}';
    final local = Directory('${root.path}/tmp/$name');
    // mkdir/chmod run before Dart writes sensitive bytes. Random exclusive path.
    final remote = '/tmp/$name';
    _activePaths.add(local.path);
    final code = await _shell.run(
      'umask 077; mkdir ${_q(remote)} >/dev/null 2>&1 && chmod 700 ${_q(remote)} >/dev/null 2>&1',
      timeout: _short,
    );
    if (code != 0 ||
        await FileSystemEntity.type(local.path, followLinks: false) !=
            FileSystemEntityType.directory) {
      _activePaths.remove(local.path);
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    return _PrivateWork(
      local,
      remote,
      _shell,
      () => _activePaths.remove(local.path),
    );
  }

  /// An SSH public key is not trusted merely because keyscan returned it.
  /// This checks the wire representation and computes its actual fingerprint.
  static ByoHostKey keyFromPublicKey(String publicKey) {
    try {
      final parts = publicKey.trim().split(RegExp(r'\s+'));
      if (parts.length < 2 || parts[0] != 'ssh-ed25519') {
        throw const FormatException();
      }
      final bytes = base64Decode(base64.normalize(parts[1]));
      final data = ByteData.sublistView(Uint8List.fromList(bytes));
      if (bytes.length != 51 ||
          data.getUint32(0) != 11 ||
          ascii.decode(bytes.sublist(4, 15)) != 'ssh-ed25519' ||
          data.getUint32(15) != 32) {
        throw const FormatException();
      }
      final canonical = base64Encode(bytes);
      return ByoHostKey(
        publicKey: 'ssh-ed25519 $canonical',
        fingerprint:
            'SHA256:${base64Encode(sha256.convert(bytes).bytes).replaceAll('=', '')}',
      );
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
    }
  }

  static void _validatePin(ByoHostKey hostKey) {
    if (keyFromPublicKey(hostKey.publicKey).fingerprint !=
        hostKey.fingerprint) {
      throw const ByoHostFailure(ByoHostFailureCode.hostKeyChanged);
    }
  }

  @override
  Future<ByoHostKey> inspectKey(ByoHostTarget target) async {
    final work = await _work('scan');
    try {
      final result = await work.execute(
        'ssh-keyscan -T 10 -t ed25519 -p ${target.port} ${_q(target.host)}',
      );
      if (result.code != 0) {
        throw const ByoHostFailure(ByoHostFailureCode.transport);
      }
      final keys = <String>{};
      for (final line in const LineSplitter().convert(result.output)) {
        if (line.startsWith('#') || line.trim().isEmpty) continue;
        final fields = line.trim().split(RegExp(r'\s+'));
        if (fields.length == 3 && fields[1] == 'ssh-ed25519') {
          keys.add('${fields[1]} ${fields[2]}');
        }
      }
      if (keys.length != 1) {
        throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
      }
      return keyFromPublicKey(keys.single);
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    } finally {
      await work.remove();
    }
  }

  @override
  Future<ByoHostIdentity> generateIdentity(String profileId) async {
    final work = await _work(profileId);
    try {
      final result = await work.execute(
        'ssh-keygen -q -t ed25519 -N "" -C oc-byo -f ${_q('${work.remote}/identity')}',
      );
      if (result.code != 0) {
        throw const ByoHostFailure(ByoHostFailureCode.unavailable);
      }
      final public = keyFromPublicKey(await work.read('identity.pub'));
      return ByoHostIdentity(
        privateKey: await work.read('identity'),
        publicKey: public.publicKey,
      );
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    } finally {
      await work.remove();
    }
  }

  Future<String> _ssh(
    _PrivateWork work,
    ByoHostTarget target,
    ByoHostKey pin,
    ByoHostLogin login,
  ) async {
    _validatePin(pin);
    await work.write('known_hosts', 'oc-byo-host ${pin.publicKey}\n');
    final args = <String>[
      'ssh',
      '-F',
      '/dev/null',
      '-T',
      '-p',
      '${target.port}',
      '-o',
      'HostKeyAlias=oc-byo-host',
      '-o',
      'UserKnownHostsFile=${work.remote}/known_hosts',
      '-o',
      'GlobalKnownHostsFile=/dev/null',
      '-o',
      'StrictHostKeyChecking=yes',
      '-o',
      'HostKeyAlgorithms=ssh-ed25519',
      '-o',
      'UpdateHostKeys=no',
      '-o',
      'VerifyHostKeyDNS=no',
      '-o',
      'IdentityAgent=none',
      '-o',
      'IdentitiesOnly=yes',
      '-o',
      'ForwardAgent=no',
      '-o',
      'ForwardX11=no',
      '-o',
      'PermitLocalCommand=no',
      '-o',
      'ProxyCommand=none',
      '-o',
      'ProxyJump=none',
      '-o',
      'ControlMaster=no',
      '-o',
      'ControlPath=none',
      '-o',
      'ConnectionAttempts=1',
      '-o',
      'ConnectTimeout=15',
      '-o',
      'ServerAliveInterval=15',
      '-o',
      'ServerAliveCountMax=2',
      '-o',
      'NumberOfPasswordPrompts=1',
      '-o',
      'LogLevel=ERROR',
      '-o',
      'EscapeChar=none',
      '-o',
      'RequestTTY=no',
    ];
    final key = login.privateKey;
    if (key != null && key.isNotEmpty) {
      await work.write('identity', key);
      args.addAll(['-i', '${work.remote}/identity']);
    } else {
      args.addAll(['-o', 'PubkeyAuthentication=no']);
    }
    if (login.passphrase != null && login.password != null) {
      throw const ByoHostFailure(ByoHostFailureCode.authentication);
    }
    final password = login.passphrase ?? login.password;
    String environment = '';
    if (password != null) {
      if (password.contains('\n') || password.contains('\r')) {
        throw const ByoHostFailure(ByoHostFailureCode.authentication);
      }
      await work.write('password', password);
      final prompt = login.passphrase != null ? '*passphrase*' : '*assword*';
      await work.write(
        'askpass',
        '#!/bin/sh\ncase "\$1" in $prompt) exec cat ${_q('${work.remote}/password')};; *) exit 1;; esac\n',
        executable: true,
      );
      environment =
          'DISPLAY=oc-byo SSH_ASKPASS_REQUIRE=force SSH_ASKPASS=${_q('${work.remote}/askpass')} ';
      args.addAll([
        '-o',
        'BatchMode=no',
        '-o',
        'PreferredAuthentications=${login.passphrase != null ? 'publickey' : 'password'}',
      ]);
    } else {
      args.addAll([
        '-o',
        'BatchMode=yes',
        '-o',
        'PreferredAuthentications=publickey',
      ]);
    }
    args.addAll(['-l', target.user]);
    // Caller adds flags before the fixed destination, never a remote command
    // derived from user input.
    return '$environment${args.map(_q).join(' ')}';
  }

  @override
  Future<ByoHostDescriptor> install({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostLogin login,
    required ByoHostIdentity identity,
    required String deviceToken,
    required ByoHostBundle bundle,
  }) async {
    _PrivateWork? work;
    try {
      work = await _work(profileId);
      if (!RegExp(r'^[A-Za-z0-9_-]{32,256}$').hasMatch(deviceToken)) {
        throw const ByoHostFailure(ByoHostFailureCode.authentication);
      }
      keyFromPublicKey(identity.publicKey);
      final ssh = await _ssh(work, target, hostKey, login);
      final architecture = await work.execute(
        '$ssh ${_q(target.host)} uname -m',
      );
      if (architecture.code != 0) throw _sshFailure(architecture.output);
      final arch = switch (architecture.output.trim()) {
        'x86_64' => 'x64',
        'aarch64' => 'arm64',
        _ => '',
      };
      final artifact = bundle.artifacts[arch];
      if (artifact == null) {
        throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
      }
      final payload = jsonEncode({
        'deviceId': profileId,
        'token': deviceToken,
        'publicKey': identity.publicKey,
      });
      // The entire remote script is a private file, carried over SSH stdin.
      // The reviewed archive digest authenticates the installer before execution.
      final script =
          '''set -eu
umask 077
work=\$(mktemp -d)
trap 'rm -rf -- "\$work"' EXIT HUP INT TERM
curl --proto '=https' --proto-redir '=https' --location --tlsv1.2 --fail --silent --show-error --max-time 600 --output "\$work/bundle.tar.gz" ${_q(artifact.url)}
printf '%s  %s\\n' ${_q(artifact.sha256)} "\$work/bundle.tar.gz" | sha256sum -c - >/dev/null || { printf '{"error":"checksum"}\\n'; exit 1; }
tar -xzf "\$work/bundle.tar.gz" -C "\$work" install.sh
sh "\$work/install.sh" --archive "\$work/bundle.tar.gz" --sha256 ${_q(artifact.sha256)} <<'OC_BYO_INPUT'
$payload
OC_BYO_INPUT
''';
      await work.write('input', script);
      final result = await work.execute(
        '$ssh ${_q(target.host)} sh -s < ${_q('${work.remote}/input')}',
        timeout: const Duration(minutes: 15),
      );
      if (result.code != 0) {
        throw _installFailure(result.output);
      }
      final descriptor = _descriptor(result.output);
      if (descriptor.bundleVersion != bundle.version ||
          descriptor.openCodeVersion != bundle.openCodeVersion) {
        throw const ByoHostFailure(ByoHostFailureCode.protocol);
      }
      return descriptor;
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.uncertain);
    } finally {
      login.consume();
      await work?.remove();
    }
  }

  @override
  Future<ByoHostTunnel> forward({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostIdentity identity,
    required int remotePort,
  }) async {
    if (remotePort < 1 || remotePort > 65535) {
      throw const ByoHostFailure(ByoHostFailureCode.invalidTarget);
    }
    final work = await _work(profileId);
    final name = 'byo-${work.remote.substring(work.remote.length - 24)}';
    try {
      final ssh = await _ssh(
        work,
        target,
        hostKey,
        ByoHostLogin(privateKey: identity.privateKey),
      );
      final listener = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      final port = listener.port;
      await listener.close();
      final output = await work.empty('result');
      final script =
          'ulimit -f 128; exec $ssh -o ExitOnForwardFailure=yes -N -L ${_q('127.0.0.1:$port:127.0.0.1:$remotePort')} ${_q(target.host)} > ${_q(output)} 2>&1';
      await _shell.start(name, script, port);
      late final _SshTunnel tunnel;
      tunnel = _SshTunnel(port, () => _shell.running(name), () async {
        await _shell.stop(name);
        await work.remove();
        _tunnels.remove(tunnel);
      });
      _tunnels.add(tunnel);
      // The native start call can precede SSH readiness. Bound the wait and do
      // not fall back to a public HTTP endpoint. Authenticated describe follows.
      for (var attempt = 0; attempt < 40; attempt++) {
        try {
          final socket = await Socket.connect(
            InternetAddress.loopbackIPv4,
            port,
            timeout: const Duration(milliseconds: 200),
          );
          socket.destroy();
          if (await _shell.running(name)) {
            // A stolen bind must make ExitOnForwardFailure terminate SSH before
            // returning a usable handle. fd handoff is unavailable here: this
            // narrows, but cannot eliminate, the bind-close race. Keep the build
            // gate off until the owner validates actual Android service lifetime.
            await Future<void>.delayed(const Duration(milliseconds: 300));
            if (await _shell.running(name)) return tunnel;
          }
          break;
        } catch (_) {
          await Future<void>.delayed(const Duration(milliseconds: 100));
        }
      }
      final failure = _sshFailure(await work.readIfPresent('result'));
      await tunnel.close();
      _tunnels.remove(tunnel);
      throw failure;
    } on ByoHostFailure {
      await _stopWork(name, work);
      rethrow;
    } catch (_) {
      await _stopWork(name, work);
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    }
  }

  Future<void> _stopWork(String name, _PrivateWork work) async {
    try {
      await _shell.stop(name);
      await work.remove();
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    }
  }

  Future<String> _request(
    ByoHostTunnel tunnel,
    String deviceId,
    String token,
    String path,
    String method,
  ) async {
    if (!byoHostSafeId(deviceId) ||
        !RegExp(r'^[A-Za-z0-9_-]{32,256}$').hasMatch(token) ||
        tunnel.localPort < 1 ||
        tunnel.localPort > 65535) {
      throw const ByoHostFailure(ByoHostFailureCode.authentication);
    }
    if (tunnel is _SshTunnel && !await tunnel.alive()) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    }
    final client = _httpClient();
    client.findProxy = (_) => 'DIRECT';
    client.connectionTimeout = const Duration(seconds: 10);
    try {
      final request = await client
          .openUrl(method, Uri.http('127.0.0.1:${tunnel.localPort}', path))
          .timeout(const Duration(seconds: 10));
      request.followRedirects = false;
      request.headers.set(
        HttpHeaders.authorizationHeader,
        'Basic ${base64Encode(utf8.encode('$deviceId:$token'))}',
      );
      final response = await request.close().timeout(
        const Duration(seconds: 10),
      );
      if (response.statusCode != 200) {
        throw const ByoHostFailure(ByoHostFailureCode.protocol);
      }
      final bytes = <int>[];
      await (() async {
        await for (final chunk in response) {
          bytes.addAll(chunk);
          if (bytes.length > 16384) {
            throw const ByoHostFailure(ByoHostFailureCode.protocol);
          }
        }
      })().timeout(const Duration(seconds: 15));
      return utf8.decode(bytes);
    } on ByoHostFailure {
      rethrow;
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    } finally {
      client.close(force: true);
    }
  }

  static ByoHostDescriptor _descriptor(String text) {
    try {
      final data = jsonDecode(text) as Map<String, dynamic>;
      final id = data['hostId'] as String;
      final bundle = data['bundleVersion'] as String;
      final version = data['openCodeVersion'] as String;
      final port = data['port'] as int;
      if (!byoHostSafeId(id) ||
          !RegExp(r'^\d+\.\d+\.\d+$').hasMatch(bundle) ||
          !RegExp(r'^\d+\.\d+\.\d+$').hasMatch(version) ||
          port < 1 ||
          port > 65535) {
        throw const FormatException();
      }
      return ByoHostDescriptor(
        hostId: id,
        bundleVersion: bundle,
        openCodeVersion: version,
        port: port,
      );
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.protocol);
    }
  }

  static ByoHostFailure _installFailure(String output) {
    // stderr may precede the installer's final fixed JSON error. Never expose
    // arbitrary server text, and never infer a code from a substring in it.
    for (final line in const LineSplitter().convert(output).reversed) {
      Object? code;
      try {
        final value = jsonDecode(line);
        if (value is Map<String, dynamic>) code = value['error'];
      } catch (_) {
        continue;
      }
      final failure = switch (code) {
        'checksum' || 'checksumMismatch' => ByoHostFailureCode.checksum,
        'unsupportedHost' => ByoHostFailureCode.unsupportedHost,
        'userServiceUnavailable' => ByoHostFailureCode.unavailable,
        'hostBusy' => ByoHostFailureCode.busy,
        'sshPolicyRequired' ||
        'forwardingPolicyRequired' => ByoHostFailureCode.sshPolicyRequired,
        'lingerRequired' => ByoHostFailureCode.lingerRequired,
        'unsupportedBundle' => ByoHostFailureCode.bundleUnavailable,
        'hostConflict' => ByoHostFailureCode.identityChanged,
        'unsafeArchive' || 'unsafePath' => ByoHostFailureCode.protocol,
        String value when value.startsWith('invalid') =>
          ByoHostFailureCode.protocol,
        _ => null,
      };
      if (failure != null) return ByoHostFailure(failure);
    }
    final failure = _sshFailure(output);
    return failure.code == ByoHostFailureCode.transport
        ? const ByoHostFailure(ByoHostFailureCode.uncertain)
        : failure;
  }

  static ByoHostFailure _sshFailure(String output) {
    if (output.contains('REMOTE HOST IDENTIFICATION HAS CHANGED') ||
        output.contains('Host key verification failed')) {
      return const ByoHostFailure(ByoHostFailureCode.hostKeyChanged);
    }
    if (output.contains('Permission denied')) {
      return const ByoHostFailure(ByoHostFailureCode.authentication);
    }
    return const ByoHostFailure(ByoHostFailureCode.transport);
  }

  @override
  Future<ByoHostDescriptor> describe({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  }) async => _descriptor(
    await _request(tunnel, deviceId, deviceToken, '/_oc/host', 'GET'),
  );

  @override
  Future<bool> revoke({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  }) async {
    try {
      final response =
          jsonDecode(
                await _request(
                  tunnel,
                  deviceId,
                  deviceToken,
                  '/_oc/revoke',
                  'POST',
                ),
              )
              as Map<String, dynamic>;
      return response['revoked'] == true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<void> dispose() async {
    _disposed = true;
    for (final tunnel in _tunnels.toList()) {
      await tunnel.close();
      _tunnels.remove(tunnel);
    }
  }
}

String _q(String value) => "'${value.replaceAll("'", "'\\''")}'";

class _CommandResult {
  const _CommandResult(this.code, this.output);
  final int code;
  final String output;
}

class _PrivateWork {
  _PrivateWork(this.local, this.remote, this.shell, this.onRemove);
  final void Function() onRemove;
  final Directory local;
  final String remote;
  final ByoHostLocalShell shell;
  Future<String> empty(String name, {bool executable = false}) async {
    final path = '$remote/$name';
    final code = await shell.run(
      'umask 077; : > ${_q(path)} && chmod ${executable ? '700' : '600'} ${_q(path)}',
      timeout: const Duration(seconds: 10),
    );
    if (code != 0) throw const ByoHostFailure(ByoHostFailureCode.storage);
    return path;
  }

  Future<void> write(
    String name,
    String value, {
    bool executable = false,
  }) async {
    await empty(name, executable: executable);
    await File('${local.path}/$name').writeAsString(value, flush: true);
  }

  Future<String> read(String name) async {
    final file = File('${local.path}/$name');
    if (await FileSystemEntity.type(file.path, followLinks: false) !=
            FileSystemEntityType.file ||
        await file.length() > 65536) {
      throw const ByoHostFailure(ByoHostFailureCode.protocol);
    }
    return file.readAsString();
  }

  Future<String> readIfPresent(String name) async =>
      await File('${local.path}/$name').exists() ? read(name) : '';
  Future<_CommandResult> execute(
    String command, {
    Duration timeout = const Duration(seconds: 40),
  }) async {
    final output = await empty('result');
    // Redirect the shell itself, so parse/exec errors cannot leak via native logs.
    await write('command', '#!/bin/sh\nulimit -f 128\n$command\n');
    final code = await shell.run(
      'sh ${_q('$remote/command')} > ${_q(output)} 2>&1',
      timeout: timeout,
    );
    return _CommandResult(code, await read('result'));
  }

  Future<void> remove() async {
    try {
      if (await FileSystemEntity.type(local.path, followLinks: false) ==
          FileSystemEntityType.directory) {
        await local.delete(recursive: true);
      }
      onRemove();
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
  }
}

class _SshTunnel implements ByoHostTunnel {
  _SshTunnel(this.localPort, this._alive, this._close);
  final Future<bool> Function() _alive;
  Future<bool> alive() async => _closing == null && await _alive();
  @override
  final int localPort;
  final Future<void> Function() _close;
  Future<void>? _closing;
  @override
  Future<void> close() => _closing ??= _safeClose();
  Future<void> _safeClose() async {
    try {
      await _close();
    } catch (_) {
      _closing = null;
      throw const ByoHostFailure(ByoHostFailureCode.transport);
    }
  }
}
