import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/agent_catalog.dart';
import '../../domain/phone_agent_host.dart';
import '../../domain/phone_agents.dart';
import '../../domain/agent_sign_in.dart';
import '../../domain/agent_auth_probe.dart';
import 'agent_scripts.dart';
import '../../paseo/gateway.dart';
import '../../paseo/transport.dart';
import '../../ui/kit/kit_redact.dart';
import '../builtin_linux.dart';
import '../setup/setup_engine.dart';
import '../setup/setup_contract.dart';
import 'agent_components.dart';
import 'paseo_scripts.dart';

/// Built-in implementation. UI consumes PhoneAgentHost and safe domain rows;
/// the connection controller owns gateways and never creates a second profile.
final class BuiltinPhoneAgents implements PhoneAgentHost {
  BuiltinPhoneAgents({
    required this.profileId,
    required this.prefs,
    FlutterSecureStorage? secure,
    BuiltinLinux? linux,
    MethodChannel? channel,
    AgentCatalog? catalog,
    Future<String> Function()? loadLock,
    PaseoSocketFactory? socketFactory,
    SetupEngine Function(AgentDescriptor, String)? engineFactory,
  }) : secure = secure ?? const FlutterSecureStorage(),
       _linux = linux ?? BuiltinLinux(),
       _channel = channel ?? const MethodChannel(BuiltinLinux.channelName),
       catalog = catalog ?? AgentCatalog.builtIn,
       _loadLock =
           loadLock ??
           (() => rootBundle.loadString(PaseoPhoneScripts.packageLockAsset)),
       _socketFactory = socketFactory ?? connectPaseoSocket,
       _engineFactory = engineFactory {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(profileId)) {
      throw ArgumentError('Invalid profile');
    }
  }

  final String profileId;
  final SharedPreferences prefs;
  final FlutterSecureStorage secure;
  final BuiltinLinux _linux;
  final MethodChannel _channel;
  final AgentCatalog catalog;
  final Future<String> Function() _loadLock;
  final PaseoSocketFactory _socketFactory;
  final SetupEngine Function(AgentDescriptor, String)? _engineFactory;
  final _changes = StreamController<AgentSetupProgress>.broadcast(sync: true);
  SetupEngine? _engine;
  void Function()? _engineListener;
  String? _installAgent;
  bool _installRunPending = false;
  bool _disposed = false;
  int _generation = 0;
  bool _startInFlight = false;
  bool _checkInFlight = false;
  AgentSetupProgress _progress = const AgentSetupProgress(
    agentId: '',
    phase: AgentSetupPhase.idle,
  );

  @override
  Stream<AgentSetupProgress> get setupChanges => _changes.stream;
  @override
  AgentSetupProgress get setupProgress => _progress;

  AgentDescriptor _agent(String id) {
    final agent = catalog.byId(id);
    if (agent == null ||
        agent.recipe == null ||
        !{AgentRoute.paseoNative, AgentRoute.acpPaseo}.contains(agent.route)) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
    return agent;
  }

  bool _authBridgeAvailable = false;
  bool supportsSignOut(String agentId) =>
      _authBridgeAvailable &&
      AgentPhoneScripts.supportsSignOut(_agent(agentId));

  Future<AgentAuthProbeResult> probeSignIn(String agentId) =>
      _authCommand(agentId, logout: false);

  Future<AgentAuthProbeResult> signOut(String agentId) =>
      _authCommand(agentId, logout: true);

  Future<bool?> helperRunning() async {
    final value = (await _invoke('agentHostStatus'))['running'];
    return value is bool ? value : null;
  }

  Future<AgentAuthProbeResult> _authCommand(
    String agentId, {
    required bool logout,
  }) async {
    if (_disposed) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.hostUnavailable,
      );
    }
    final generation = _generation;
    final agent = _agent(agentId);
    // Only authored scripts can use this path. The CLI's stdout/stderr stay
    // inside its bounded Python child; only the narrow auth projection crosses
    // the channel. Match startAgentProcess's native profile environment.
    final home = '/home/oc/.oc-profiles/$profileId';
    final command = logout
        ? AgentPhoneScripts.signOut(agent)
        : AgentPhoneScripts.authProbe(agent);
    final script =
        "export HOME='$home' CLAUDE_CONFIG_DIR='$home/claude' CODEX_HOME='$home/codex' DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n$command";
    try {
      final raw = await _channel
          .invokeMethod<Object?>('agentAuthProbe', {
            'profileId': profileId,
            'agentId': agentId,
            'action': logout ? 'logout' : 'probe',
            'script': script,
            'timeoutSeconds': logout ? 20 : 10,
          })
          .timeout(Duration(seconds: logout ? 20 : 10));
      if (_disposed || generation != _generation || raw is! Map) {
        return const AgentAuthProbeResult.failed(
          AgentAuthProbeError.hostUnavailable,
        );
      }
      if (jsonEncode(raw).length > 2048) {
        return const AgentAuthProbeResult.failed(
          AgentAuthProbeError.invalidResponse,
        );
      }
      _authBridgeAvailable = true;
      return AgentAuthProbeResult.fromJson(Map<String, dynamic>.from(raw));
    } on MissingPluginException {
      _authBridgeAvailable = false;
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.probeUnsupported,
      );
    } on TimeoutException {
      return const AgentAuthProbeResult.failed(AgentAuthProbeError.timedOut);
    } catch (_) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.hostUnavailable,
      );
    }
  }

  Future<Map<Object?, Object?>> _invoke(
    String method, [
    Map<String, Object?> data = const {},
  ]) async {
    if (_disposed) throw const AgentHostException(AgentHostFailure.stale);
    try {
      final raw = await _channel.invokeMethod<Object?>(method, {
        'profileId': profileId,
        ...data,
      });
      return raw is Map ? Map<Object?, Object?>.from(raw) : const {};
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
  }

  Future<AgentArchitecture?> architecture() async =>
      switch ((await _invoke('agentHostStatus'))['abi']) {
        'arm64-v8a' => AgentArchitecture.arm64,
        'x86_64' => AgentArchitecture.x64,
        _ => null,
      };

  Future<SetupEngine> _prepareEngine(String id) async {
    if (_engine != null && _installAgent == id) return _engine!;
    if (_engine?.progress.value.state == SetupState.running) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    _detachEngine();
    final agent = _agent(id);
    final lock = await _loadLock();
    if (_disposed) throw const AgentHostException(AgentHostFailure.stale);
    // Keep restore independent of another native ABI read. Admission uses
    // the largest known authored download and payload independently across
    // supported architectures, without changing download/consent metadata.
    int? downloadBytes;
    int? installedBytes;
    for (final artifact in agent.recipe!.artifacts.values) {
      final bytes = artifact.downloadBytes;
      if (bytes != null && (downloadBytes == null || bytes > downloadBytes)) {
        downloadBytes = bytes;
      }
      final payloadBytes = artifact.installedBytes;
      if (payloadBytes != null &&
          (installedBytes == null || payloadBytes > installedBytes)) {
        installedBytes = payloadBytes;
      }
    }
    _engine =
        _engineFactory?.call(agent, lock) ??
        ChannelSetupEngine(
          linux: _linux,
          components: (l10n, _) => [
            for (final component in phoneAgentComponents(l10n, agent, lock))
              if (component.id == 'agent-${agent.id}')
                component
                    .withDownloadBytes(downloadBytes)
                    .withInstalledBytes(installedBytes)
              else
                component,
          ],
        );
    _installAgent = id;
    _engineListener = () {
      final value = _engine!.progress.value;
      _progress = AgentSetupProgress(
        agentId: id,
        phase: switch (value.state) {
          SetupState.idle => AgentSetupPhase.idle,
          SetupState.running => AgentSetupPhase.installing,
          SetupState.done => AgentSetupPhase.done,
          SetupState.interrupted ||
          SetupState.cancelled => AgentSetupPhase.interrupted,
          SetupState.failed => AgentSetupPhase.failed,
        },
        fraction: value.overall.isFinite
            ? value.overall.clamp(0, 1).toDouble()
            : null,
        componentId: _engine!.registry.any((c) => c.id == value.current)
            ? value.current
            : null,
      );
      if (!_changes.isClosed) _changes.add(_progress);
    };
    _engine!.progress.addListener(_engineListener!);
    return _engine!;
  }

  @override
  Future<void> install(String agentId) async {
    if (_installRunPending ||
        _engine?.progress.value.state == SetupState.running) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    final generation = _generation;
    final arch = await architecture();
    _checkGeneration(generation);
    final agent = _agent(agentId);
    if (arch == null || !agent.installableOn(arch)) {
      throw const AgentHostException(AgentHostFailure.wrongArchitecture);
    }
    await _password();
    _checkGeneration(generation);
    final engine = await _prepareEngine(agentId);
    _checkGeneration(generation);
    if (!await prefs.setString('$phoneAgentInstallPrefix$profileId', agentId)) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
    _checkGeneration(generation);
    _installRunPending = true;
    try {
      await engine.run(
        {'agent-$agentId'},
        params: {
          'phoneAgentOwner': {'profileId': profileId, 'agentId': agentId},
        },
      );
    } finally {
      _installRunPending = false;
    }
  }

  @override
  Future<void> restoreInstall() async {
    final id = prefs.getString('$phoneAgentInstallPrefix$profileId');
    if (id != null && await _ownsSetupJob(id)) {
      await (await _prepareEngine(id)).restore();
    }
  }

  @override
  Future<void> cancelInstall() async {
    _generation++;
    // run() includes local checks before native ownership exists. During
    // that handoff the engine's local cancellation flag prevents dispatch.
    // Once run() returns, only our exact durable owner may stop a native job.
    if (_installRunPending ||
        (_installAgent != null && await _ownsSetupJob(_installAgent!))) {
      await _engine?.cancel();
    }
  }

  Future<bool> _ownsSetupJob(String id) async {
    try {
      final raw = jsonDecode(await _linux.setupStatus() ?? '{}');
      final owner = raw is Map && raw['params'] is Map
          ? raw['params']['phoneAgentOwner']
          : null;
      return owner is Map &&
          owner['profileId'] == profileId &&
          owner['agentId'] == id;
    } catch (_) {
      return false;
    }
  }

  void _checkGeneration(int generation) {
    if (_disposed || generation != _generation) {
      throw const AgentHostException(AgentHostFailure.stale);
    }
  }

  Future<void> _waitInstall() async {
    final engine = _engine!;
    final finished = Completer<void>();
    void changed() {
      final progress = engine.progress.value;
      if (!progress.ended || finished.isCompleted) return;
      if (progress.state == SetupState.done) {
        finished.complete();
      } else {
        finished.completeError(
          AgentHostException(
            progress.state == SetupState.failed
                ? AgentHostFailure.install
                : AgentHostFailure.interrupted,
          ),
        );
      }
    }

    engine.progress.addListener(changed);
    changed();
    try {
      await finished.future.timeout(const Duration(minutes: 40));
    } on TimeoutException {
      throw const AgentHostException(AgentHostFailure.interrupted);
    } finally {
      engine.progress.removeListener(changed);
    }
  }

  String? _cachedPassword;

  Future<String> _password() async {
    final key = '$phoneAgentHostSecretPrefix$profileId';
    try {
      var password = await secure.read(key: key);
      if (password == null) {
        final random = Random.secure();
        password = List.generate(
          32,
          (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
        ).join();
        KitRedact.registerKnownSecret(password);
        await secure.write(key: key, value: password);
        if (await secure.read(key: key) != password) {
          throw const AgentHostException(AgentHostFailure.storage);
        }
      }
      if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(password)) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
      KitRedact.registerKnownSecret(password);
      _cachedPassword = password;
      return password;
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
  }

  /// Install gate can bootstrap the daemon. Chat still uses separate resume proof.
  @override
  Future<void> start() async {
    if (_startInFlight) throw const AgentHostException(AgentHostFailure.busy);
    _startInFlight = true;
    final generation = _generation;
    try {
      final password = await _password();
      if (generation != _generation || _disposed) {
        throw const AgentHostException(AgentHostFailure.stale);
      }
      await _invoke('startAgentHost', {
        'password': password,
        'port': 4099,
        'config': configuration(),
      });
      if (generation != _generation || _disposed) {
        throw const AgentHostException(AgentHostFailure.stale);
      }
    } finally {
      _startInFlight = false;
    }
  }

  /// Catalog authored commands only. ACP installation is discovery, not admission.
  String configuration() => jsonEncode({
    'version': 1,
    'daemon': {
      'listen': '127.0.0.1:4099',
      'relay': {'enabled': false},
      'serviceProxy': {'enabled': false},
      'mcp': {'injectIntoAgents': false},
    },
    // Off by default: on a phone, voice and dictation start a background
    // download of local speech models (hundreds of MB) at daemon start.
    'features': {
      'webUi': {'enabled': false},
      'dictation': {'enabled': false},
      'voiceMode': {'enabled': false},
    },
    // Paseo's supervisor always keeps a rotating file log (worker output plus
    // lifecycle lines) and exits at start when the path is not a regular file,
    // so /dev/null is refused. Keep one small file in the profile's private
    // daemon home; deleting the profile removes it.
    'log': {
      'level': 'fatal',
      'file': {
        'level': 'fatal',
        'path': 'daemon.log',
        'rotate': {'maxSize': '1M', 'maxFiles': 1},
      },
    },
    'agents': {
      'providers': {
        for (final agent in catalog.agents.where(
          (agent) => agent.recipe != null && agent.route == AgentRoute.acpPaseo,
        ))
          agent.providerId: {
            'extends': 'acp',
            'label': agent.name,
            'command': [
              '/home/oc/.local/bin/${agent.recipe!.executable}',
              ...agent.recipe!.launchArgs,
            ],
          },
      },
    },
  });

  @override
  Future<void> stop() async {
    _generation++;
    await _invoke('stopAgentHost');
  }

  Future<PaseoGateway> openGateway(String directory) async {
    if (!directory.startsWith('/root/projects/') ||
        directory.split('/').any((p) => p == '..' || p == '.')) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
    final transport = _transport(await _password());
    try {
      await transport.connect();
    } catch (_) {
      await transport.close();
      throw const AgentHostException(AgentHostFailure.hello);
    }
    return PaseoGateway(
      transport: transport,
      directory: directory,
      defaultProviderModes: const {'claude': 'default'},
    );
  }

  /// A gateway scoped to [directory] that connects on first use. Needs the
  /// password a prior [openGateway]/[start] already read; the controller uses
  /// it to rebuild a live transport synchronously.
  PaseoGateway newGatewaySync(String directory) {
    final password = _cachedPassword;
    if (_disposed ||
        password == null ||
        !directory.startsWith('/root/projects/') ||
        directory.split('/').any((p) => p == '..' || p == '.')) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
    return PaseoGateway(
      transport: _transport(password),
      directory: directory,
      defaultProviderModes: const {'claude': 'default'},
    );
  }

  PaseoTransport _transport(String password) => PaseoTransport(
    endpoint: 'ws://127.0.0.1:4099/ws',
    password: password,
    socketFactory: _socketFactory,
    requestTimeout: const Duration(seconds: 15),
  );

  @override
  Future<AgentPhoneCheckResult> selfTest(String agentId) async {
    if (_checkInFlight) throw const AgentHostException(AgentHostFailure.busy);
    _checkInFlight = true;
    final generation = _generation;
    final completed = <AgentPhoneCheckStep>[];
    AgentArchitecture? arch;
    try {
      arch = await architecture();
      if (arch == null) {
        throw const AgentHostException(AgentHostFailure.wrongArchitecture);
      }
      _checkGeneration(generation);
      final agent = _agent(agentId);
      // Clear old proof before retry: a failed check never keeps a green gate.
      final gates = _readGates()..remove(agentId);
      await _writeGates(gates);
      await install(agentId);
      await _waitInstall();
      _checkGeneration(generation);
      completed.add(AgentPhoneCheckStep.install);
      final version = await _invoke('agentHostVersion', {
        'executable': agent.recipe!.executable,
        'version': agent.recipe!.version,
      });
      if (version['installed'] != true ||
          version['version'] != agent.recipe!.version) {
        throw const AgentHostException(AgentHostFailure.version);
      }
      final shared = await _invoke('agentHostWorkspace');
      if (shared['shared'] != true) {
        throw const AgentHostException(AgentHostFailure.version);
      }
      completed.add(AgentPhoneCheckStep.version);
      _checkGeneration(generation);
      await start();
      if ((await _invoke('agentHostStatus'))['running'] != true) {
        throw const AgentHostException(AgentHostFailure.daemon);
      }
      completed.add(AgentPhoneCheckStep.daemon);
      final transport = _transport(await _password());
      try {
        // Startup is bounded; connection retries send only hello, no agent prompt.
        // A cold PRoot start takes about 5 s on an emulator before the
        // daemon listens; allow a slower phone up to 30 s.
        var connected = false;
        for (var attempt = 0; attempt < 60 && !connected; attempt++) {
          if (generation != _generation || _disposed) {
            throw const AgentHostException(AgentHostFailure.stale);
          }
          try {
            await transport.connect();
            connected = true;
          } catch (_) {
            if (attempt < 59) {
              await Future<void>.delayed(const Duration(milliseconds: 500));
            }
          }
        }
        if (!connected ||
            transport.serverVersion != PaseoPhoneScripts.version) {
          throw const AgentHostException(AgentHostFailure.hello);
        }
      } finally {
        await transport.close();
      }
      completed.add(AgentPhoneCheckStep.hello);
      if (generation != _generation || _disposed) {
        throw const AgentHostException(AgentHostFailure.stale);
      }
      final current = _readGates();
      current[agentId] = {
        'fingerprint': _fingerprint(agent, arch),
        'architecture': arch.name,
      };
      await _writeGates(current);
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: arch,
        passed: true,
        completed: completed,
      );
    } on AgentHostException catch (error) {
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: arch,
        passed: false,
        completed: completed,
        failure: error.reason,
      );
    } catch (_) {
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: arch,
        passed: false,
        completed: completed,
        failure: AgentHostFailure.unavailable,
      );
    } finally {
      _checkInFlight = false;
    }
  }

  String _fingerprint(AgentDescriptor agent, AgentArchitecture arch) =>
      jsonEncode([
        agent.id,
        agent.recipe!.version,
        agent.artifactFor(arch)?.sha256,
        PaseoPhoneScripts.version,
        PaseoPhoneScripts.packageLockSha256,
        PaseoPhoneScripts.nodeVersion,
      ]);

  Map<String, Object?> _readGates() {
    try {
      final raw = jsonDecode(
        prefs.getString('$phoneAgentGatePrefix$profileId') ?? '{}',
      );
      return raw is Map<String, dynamic> ? raw : {};
    } catch (_) {
      return {};
    }
  }

  Future<void> _writeGates(Map<String, Object?> gates) async {
    if (!await prefs.setString(
      '$phoneAgentGatePrefix$profileId',
      jsonEncode(gates),
    )) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
  }

  Future<PhoneAgentRuntime> inspect(
    String agentId, {
    AgentSignInState? signIn,
    AgentCapabilities capabilities = const AgentCapabilities(),
  }) async {
    final agent = _agent(agentId);
    final host = await _invoke('agentHostStatus');
    final arch = switch (host['abi']) {
      'arm64-v8a' => AgentArchitecture.arm64,
      'x86_64' => AgentArchitecture.x64,
      _ => null,
    };
    final pin = _readGates()[agentId];
    final qualified =
        arch != null &&
        pin is Map &&
        pin['fingerprint'] == _fingerprint(agent, arch);
    final version = await _invoke('agentHostVersion', {
      'executable': agent.recipe!.executable,
      'version': agent.recipe!.version,
    });
    // Resume proof is supplied only by the controller's verified host load.
    return PhoneAgentRuntime(
      agentId: agentId,
      installed: version['installed'] == true,
      hostAvailable: host['running'] == true,
      architectureQualified: qualified,
      capabilities: capabilities,
      signInPhase: signIn?.inspected == true ? signIn!.phase : null,
      resetAt: signIn?.resetAt,
      stoppedInBackground: qualified && host['running'] != true,
    );
  }

  void _detachEngine() {
    if (_engineListener != null) {
      _engine?.progress.removeListener(_engineListener!);
    }
    if (_engine is ChannelSetupEngine) {
      (_engine! as ChannelSetupEngine).dispose();
    }
    _engine = null;
    _engineListener = null;
  }

  Future<void> dispose() async {
    _disposed = true;
    _generation++;
    _detachEngine();
    await _changes.close();
  }
}
