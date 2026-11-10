import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../domain/agent_helper_status.dart';
import '../../domain/agent_catalog.dart';
import '../../domain/phone_agent_host.dart';
import '../../domain/phone_agents.dart';
import '../../domain/server_gateway.dart';
import '../../domain/agent_sign_in.dart';
import '../../domain/agent_auth_probe.dart';
import '../../paseo/tested_versions.dart';
import 'agent_scripts.dart';
import '../../paseo/gateway.dart';
import '../../paseo/transport.dart';
import '../../ui/kit/kit_redact.dart';
import '../builtin_linux.dart';
import '../setup/setup_engine.dart';
import '../setup/setup_contract.dart';
import 'agent_components.dart';
import 'agent_install_guard.dart';
import 'agent_removal_scripts.dart';
import 'paseo_scripts.dart';

/// Strict native idle receipt. Missing or malformed fields never admit resume.
final class PhoneAgentIdleState {
  const PhoneAgentIdleState({
    required this.supported,
    this.idleStopped,
    this.helperStopped,
    this.generation,
    this.serverRestartWanted,
    this.serverRunning,
    this.legacyUnsupported = false,
  });
  final bool? supported;
  final bool? idleStopped;
  final bool? helperStopped;
  final int? generation;
  final bool? serverRestartWanted;
  final bool? serverRunning;
  final bool legacyUnsupported;

  bool get automaticStartAllowed =>
      supported == false ||
      legacyUnsupported ||
      (supported == true &&
          idleStopped == false &&
          helperStopped == false &&
          generation == 0 &&
          serverRestartWanted == true &&
          serverRunning == true);
}

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
    AgentInstallGuard? installGuard,
  }) : secure = secure ?? const FlutterSecureStorage(),
       _linux = linux ?? BuiltinLinux(),
       _channel = channel ?? const MethodChannel(BuiltinLinux.channelName),
       catalog = catalog ?? AgentCatalog.builtIn,
       _loadLock =
           loadLock ??
           (() => rootBundle.loadString(PaseoPhoneScripts.packageLockAsset)),
       _socketFactory = socketFactory ?? connectPaseoSocket,
       _engineFactory = engineFactory,
       _installGuard = installGuard ?? const AgentInstallGuard() {
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
  final AgentInstallGuard _installGuard;
  final _changes = StreamController<AgentSetupProgress>.broadcast(sync: true);
  SetupEngine? _engine;
  void Function()? _engineListener;
  String? _installAgent;
  bool _installRunPending = false;
  bool _installPreparing = false;
  bool _removeInFlight = false;
  String? _removingAgent;
  int _payloadUses = 0;
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

  /// Android runs one status check per profile at a time and refuses a
  /// second one as "host unavailable". The agent list, the sign-in step and
  /// the phone check all ask at once, and a refused check read as "sign in
  /// needed". So checks wait their turn, and a check asked for while the same
  /// agent's check runs shares its answer.
  final _probes = <String, Future<AgentAuthProbeResult>>{};
  Future<void> _authTurn = Future<void>.value();

  Future<AgentAuthProbeResult> probeSignIn(String agentId) {
    final running = _probes[agentId];
    if (running != null) return running;
    late final Future<AgentAuthProbeResult> probe;
    probe = _inAuthTurn(() => _authCommand(agentId, logout: false))
        .whenComplete(() {
          if (identical(_probes[agentId], probe)) _probes.remove(agentId);
        });
    return _probes[agentId] = probe;
  }

  /// A check asked for after the sign-out must not share an answer read
  /// before it.
  Future<AgentAuthProbeResult> signOut(String agentId) {
    _probes.remove(agentId);
    return _inAuthTurn(() => _authCommand(agentId, logout: true));
  }

  Future<T> _inAuthTurn<T>(Future<T> Function() command) {
    final run = _authTurn.then((_) => command());
    _authTurn = run.then<void>((_) {}, onError: (Object _) {});
    return run;
  }

  Future<AgentHelperStatus> helperStatus() async =>
      AgentHelperStatus.fromMap(await _invoke('agentHostStatus'));

  Future<bool?> helperRunning() async => (await helperStatus()).running;

  /// The helper's own health check (`GET /api/health` on the loopback
  /// port, which needs no password): true when it answers 200.
  Future<bool> daemonAnswers() async {
    if (_disposed) return false;
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 3);
    try {
      final request = await client
          .getUrl(Uri.parse('http://127.0.0.1:4099/api/health'))
          .timeout(const Duration(seconds: 3));
      request.followRedirects = false;
      final response = await request.close().timeout(
        const Duration(seconds: 3),
      );
      await response.drain<void>().timeout(const Duration(seconds: 2));
      return response.statusCode == HttpStatus.ok;
    } catch (_) {
      return false;
    } finally {
      client.close(force: true);
    }
  }

  Future<PhoneAgentIdleState> idleState() async {
    final raw = await _invoke('status');
    bool? boolean(String key) => raw[key] is bool ? raw[key] as bool : null;
    final token = raw['serverIdleGeneration'];
    const idleFields = [
      'serverIdlePolicySupported',
      'serverIdleStopped',
      'serverIdleHelperStopped',
      'serverIdleGeneration',
    ];
    return PhoneAgentIdleState(
      supported: boolean('serverIdlePolicySupported'),
      idleStopped: boolean('serverIdleStopped'),
      helperStopped: boolean('serverIdleHelperStopped'),
      generation: token is int && token >= 0 ? token : null,
      serverRestartWanted: boolean('serverRestartWanted'),
      serverRunning: boolean('serverRunning'),
      legacyUnsupported: idleFields.every((key) => !raw.containsKey(key)),
    );
  }

  Future<void>? _idleResume;
  int? _idleToken;
  Completer<void>? _idleCancelled;
  PaseoTransport? _idleProbe;
  bool _idleReadOnlySecret = false;

  Future<void> resumeAfterIdle({
    required int expectedIdleGeneration,
    required bool Function() stillCurrent,
  }) {
    if (_disposed || !stillCurrent() || expectedIdleGeneration < 0) {
      return Future.error(const AgentHostException(AgentHostFailure.stale));
    }
    if (_idleResume != null) {
      if (_idleToken == expectedIdleGeneration) return _idleResume!;
      return Future.error(const AgentHostException(AgentHostFailure.stale));
    }
    if (_removeInFlight || _startInFlight) {
      return Future.error(const AgentHostException(AgentHostFailure.busy));
    }
    final cancelled = _idleCancelled = Completer<void>();
    _idleToken = expectedIdleGeneration;
    _startInFlight = true;
    final generation = _generation;
    final pending = _restoreIdle(
      expectedIdleGeneration,
      generation,
      stillCurrent,
      cancelled,
    );
    _idleResume = pending.whenComplete(() {
      _idleResume = null;
      _idleToken = null;
      _idleCancelled = null;
      _startInFlight = false;
    });
    return _idleResume!;
  }

  Future<T> _idleAwait<T>(
    Future<T> operation,
    Completer<void> cancelled, {
    Duration timeout = const Duration(seconds: 8),
  }) => Future.any<T>([
    operation,
    cancelled.future.then<T>(
      (_) => throw const AgentHostException(AgentHostFailure.stale),
    ),
  ]).timeout(timeout);

  void _idleFence(int generation, bool Function() stillCurrent) {
    _checkGeneration(generation);
    if (!stillCurrent()) {
      throw const AgentHostException(AgentHostFailure.stale);
    }
  }

  Future<String> _existingPassword() async {
    try {
      final value = await secure.read(
        key: '$phoneAgentHostSecretPrefix$profileId',
      );
      if (value == null || !RegExp(r'^[a-f0-9]{64}$').hasMatch(value)) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
      return value;
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
  }

  void _validateIdleReceipt(
    PhoneAgentIdleState receipt,
    int token, {
    bool requireOwedHelper = false,
  }) {
    if (receipt.supported != true ||
        receipt.idleStopped == null ||
        receipt.helperStopped == null ||
        receipt.generation != token ||
        receipt.serverRestartWanted != true ||
        receipt.serverRunning != true ||
        (requireOwedHelper && receipt.helperStopped != true)) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
  }

  Future<void> _restoreIdle(
    int token,
    int generation,
    bool Function() stillCurrent,
    Completer<void> cancelled,
  ) async {
    try {
      final receipt = await _idleAwait(idleState(), cancelled);
      _idleFence(generation, stillCurrent);
      _validateIdleReceipt(receipt, token);
      if (receipt.helperStopped == false) return;
      final password = await _idleAwait(_existingPassword(), cancelled);
      _idleFence(generation, stillCurrent);
      final currentReceipt = await _idleAwait(idleState(), cancelled);
      _idleFence(generation, stillCurrent);
      _validateIdleReceipt(currentReceipt, token, requireOwedHelper: true);
      KitRedact.registerKnownSecret(password);
      _cachedPassword = password;
      _idleReadOnlySecret = true;
      await _idleAwait(
        _invoke('startAgentHost', {
          'password': password,
          'port': 4099,
          'config': configuration(),
          'idleResume': true,
          'expectedIdleGeneration': token,
        }),
        cancelled,
      );
      _idleFence(generation, stillCurrent);
      final probe = _idleProbe = _transport(password);
      final elapsed = Stopwatch()..start();
      try {
        while (elapsed.elapsed < const Duration(seconds: 30)) {
          _idleFence(generation, stillCurrent);
          final remaining = const Duration(seconds: 30) - elapsed.elapsed;
          try {
            await _idleAwait(probe.connect(), cancelled, timeout: remaining);
            _idleFence(generation, stillCurrent);
            // A phone that has not updated yet still runs a tested older daemon.
            if (!paseoSupports(probe.serverVersion)) {
              throw const AgentHostException(AgentHostFailure.hello);
            }
            final currentReceipt = await _idleAwait(
              idleState(),
              cancelled,
              timeout: const Duration(seconds: 30) - elapsed.elapsed,
            );
            _idleFence(generation, stillCurrent);
            _validateIdleReceipt(currentReceipt, token);
            return;
          } on AgentHostException {
            rethrow;
          } catch (_) {
            _idleFence(generation, stillCurrent);
            if (elapsed.elapsed >= const Duration(seconds: 30)) break;
            await _idlePause(cancelled);
          }
        }
        throw const AgentHostException(AgentHostFailure.hello);
      } finally {
        await probe.close();
        if (identical(_idleProbe, probe)) _idleProbe = null;
      }
    } on AgentHostException {
      rethrow;
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
  }

  Future<void> _idlePause(Completer<void> cancelled) async {
    final elapsed = Completer<void>();
    final timer = Timer(const Duration(milliseconds: 500), elapsed.complete);
    try {
      await _idleAwait(elapsed.future, cancelled);
    } finally {
      timer.cancel();
    }
  }

  void _cancelIdleRestore() {
    final cancelled = _idleCancelled;
    if (cancelled != null && !cancelled.isCompleted) cancelled.complete();
    final probe = _idleProbe;
    if (probe != null) unawaited(probe.close());
  }

  /// Removes only an authored agent payload; accounts and chats are retained.
  Future<AgentRemovalResult> removeAgent(String agentId) async {
    final agent = _agent(agentId);
    if (!AgentInstallGuard.agentIds.contains(agentId)) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
    if (_disposed) throw const AgentHostException(AgentHostFailure.stale);
    if (_removeInFlight ||
        _installPreparing ||
        _installRunPending ||
        _checkInFlight ||
        _startInFlight ||
        _payloadUses > 0 ||
        _engine?.progress.value.state == SetupState.running) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    _removeInFlight = true;
    _removingAgent = agentId;
    final generation = _generation;
    try {
      final random = Random.secure();
      final receiptId = List.generate(
        16,
        (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
      ).join();
      final snapshot = await _linux.setupStatus();
      _checkGeneration(generation);
      if (snapshot != null) {
        final job = jsonDecode(snapshot);
        if (job is! Map ||
            !{
              'done',
              'failed',
              'cancelled',
              'interrupted',
            }.contains(job['state'])) {
          throw const AgentHostException(AgentHostFailure.busy);
        }
      }
      final result = await _linux.runAgentSetupCheck(
        AgentRemovalScripts.remove(agent, receiptId: receiptId),
        timeout: const Duration(seconds: 30),
      );
      if (result.exitCode != 0) {
        _checkGeneration(generation);
        throw AgentHostException(
          result.exitCode == 16
              ? AgentHostFailure.busy
              : AgentHostFailure.unavailable,
        );
      }
      // The fixed oc setup channel deliberately filters arbitrary output.
      // Read and clean our exclusive numeric receipt even if the owner was
      // disposed during deletion. Only then apply the generation fence; no
      // stale result reaches callers and no private receipt is left behind.
      final receipt = await _linux.run(
        AgentRemovalScripts.readReceipt(receiptId),
        timeout: const Duration(seconds: 10),
      );
      _checkGeneration(generation);
      final value = RegExp(
        r'^([0-9]{1,19}) ([01])\n?$',
      ).firstMatch(receipt.output);
      final bytes = value == null ? null : int.tryParse(value[1]!);
      if (!receipt.ok ||
          bytes == null ||
          bytes < 0 ||
          (value![2] == '1' && bytes != 0)) {
        throw const AgentHostException(AgentHostFailure.unavailable);
      }
      final removal = AgentRemovalResult(
        agentId: agentId,
        freedBytes: bytes,
        alreadyAbsent: value[2] == '1',
      );
      if (_installAgent == agentId) {
        _detachEngine();
        _installAgent = null;
        _progress = const AgentSetupProgress(
          agentId: '',
          phase: AgentSetupPhase.idle,
        );
        _changes.add(_progress);
      }
      return removal;
    } on AgentHostException {
      rethrow;
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    } finally {
      _removeInFlight = false;
      _removingAgent = null;
    }
  }

  Future<void> _beginPayloadUse() async {
    // Which gate refused goes to Details (the cause names it, in fixed words).
    final gate = _disposed
        ? 'agent host closed'
        : _removeInFlight
        ? 'agent removal running'
        : _checkInFlight
        ? 'phone check running'
        : _installPreparing || _installRunPending
        ? 'agent install running'
        : null;
    if (gate != null) {
      throw ProductException(
        'Agent setup is in progress. Wait for it to finish and try again.',
        cause: gate,
      );
    }
    _payloadUses++;
  }

  void _endPayloadUse() => _payloadUses--;

  Future<AgentAuthProbeResult> _authCommand(
    String agentId, {
    required bool logout,
  }) async {
    if (_disposed || _removingAgent == agentId) {
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
            'timeoutSeconds': logout ? 20 : (agentId == 'claude' ? 65 : 10),
          })
          .timeout(
            Duration(seconds: logout ? 20 : (agentId == 'claude' ? 65 : 10)),
          );
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
    } on PlatformException catch (error) {
      throw AgentHostException(switch (error.code) {
        'helper_update_required' => AgentHostFailure.helperVersion,
        'agent_host_busy' || 'agent_host_unowned' => AgentHostFailure.busy,
        _ => AgentHostFailure.unavailable,
      }, code: AgentHostException.safeCode(error.code));
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
    // the largest known authored download (with the QA floor) and payload
    // independently across supported architectures, without changing
    // download/consent metadata.
    final downloadBytes = _installGuard.downloadBytesFor(agent);
    int? installedBytes;
    for (final artifact in agent.recipe!.artifacts.values) {
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
      final current = value.components
          .where((c) => c.id == value.current)
          .firstOrNull;
      _progress = AgentSetupProgress(
        agentId: id,
        downloadedBytes: current?.bytesDone,
        totalBytes: current?.bytesTotal,
        checkStep: _checkInFlight ? AgentPhoneCheckStep.install : null,
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
        failure:
            value.state == SetupState.failed &&
                value.error ==
                    'There is not enough free space on this phone. Free some storage and try again.'
            ? AgentHostFailure.storage
            : null,
      );
      if (!_changes.isClosed) _changes.add(_progress);
    };
    _engine!.progress.addListener(_engineListener!);
    return _engine!;
  }

  @override
  Future<void> install(String agentId) => _install(agentId);

  Future<void> _install(String agentId, {bool helperOnly = false}) async {
    if (_removeInFlight ||
        _installPreparing ||
        _installRunPending ||
        _engine?.progress.value.state == SetupState.running) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    _installPreparing = true;
    try {
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
      if (!await prefs.setString(
        '$phoneAgentInstallPrefix$profileId',
        agentId,
      )) {
        throw const AgentHostException(AgentHostFailure.storage);
      }
      _checkGeneration(generation);
      _installRunPending = true;
      try {
        await engine.run(
          {helperOnly ? 'agent-paseo' : 'agent-$agentId'},
          params: {
            'phoneAgentOwner': {'profileId': profileId, 'agentId': agentId},
            if (_installGuard.minimumFreeBytes > 0 &&
                AgentInstallGuard.agentIds.contains(agentId))
              'agentInstallGuard': {
                'agentId': agentId,
                'minimumFreeBytes': '${_installGuard.minimumFreeBytes}',
              },
          },
        );
      } finally {
        _installRunPending = false;
      }
    } finally {
      _installPreparing = false;
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
    _cancelIdleRestore();
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
  Future<void> start({
    bool Function()? stillCurrent,
    bool restartIfVersionChanged = false,
  }) async {
    if (_removeInFlight || _startInFlight) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    _startInFlight = true;
    final generation = _generation;
    void fence() {
      _checkGeneration(generation);
      if (stillCurrent != null && !stillCurrent()) {
        throw const AgentHostException(AgentHostFailure.stale);
      }
    }

    try {
      // Ordinary foreground actions can create a secret, but a stale owner
      // cannot begin that work or dispatch after the secure-storage await.
      fence();
      _idleReadOnlySecret = false;
      final password = await _password();
      fence();
      await _invoke('startAgentHost', {
        'password': password,
        'port': 4099,
        'config': configuration(),
        if (restartIfVersionChanged) ...{
          'expectedHelperVersion': PaseoPhoneScripts.version,
          'restartIfVersionChanged': true,
        },
      });
      fence();
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
    _cancelIdleRestore();
    await _invoke('stopAgentHost');
  }

  Future<PaseoGateway> openGateway(String directory) async {
    if (!directory.startsWith('/root/projects/') ||
        directory.split('/').any((p) => p == '..' || p == '.')) {
      throw const AgentHostException(AgentHostFailure.unavailable);
    }
    final transport = _transport(
      await (_idleReadOnlySecret ? _existingPassword() : _password()),
    );
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
      trackLocalWork: true,
      beforePayloadUse: _beginPayloadUse,
      afterPayloadUse: _endPayloadUse,
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
      trackLocalWork: true,
      beforePayloadUse: _beginPayloadUse,
      afterPayloadUse: _endPayloadUse,
    );
  }

  PaseoTransport _transport(String password) => PaseoTransport(
    endpoint: 'ws://127.0.0.1:4099/ws',
    password: password,
    socketFactory: _socketFactory,
    requestTimeout: const Duration(seconds: 15),
  );

  @override
  Future<AgentPhoneCheckResult> selfTest(String agentId) =>
      _phoneCheck(agentId);

  Future<AgentPhoneCheckResult> usePreviousVersion(String agentId) =>
      _phoneCheck(agentId, previous: true);

  void _checkProgress(String agentId, AgentPhoneCheckStep step) {
    _progress = AgentSetupProgress(
      agentId: agentId,
      phase: AgentSetupPhase.done,
      checkStep: step,
    );
    if (!_changes.isClosed) _changes.add(_progress);
  }

  /// A helper update must not interrupt a conversation or a permission reply.
  /// An incomplete/unknown snapshot refuses the restart as busy.
  Future<PaseoGateway> _requireIdleHelper() async {
    final gateway = PaseoGateway(
      transport: _transport(await _password()),
      directory: '/home/oc',
      trackLocalWork: true,
    );
    try {
      await gateway.health();
      // This inventory covers all pages, permissions and provider subagents.
      // Keep its event subscription alive until the update finishes so a new
      // turn after the snapshot invalidates admission before a restart.
      await gateway.sessions();
      if (gateway.localWorkBusy != false) {
        throw const AgentHostException(AgentHostFailure.busy);
      }
      return gateway;
    } catch (_) {
      gateway.close();
      throw const AgentHostException(AgentHostFailure.busy);
    }
  }

  Future<String?> _previousVersion(AgentDescriptor agent) async {
    if (agent.id != 'claude') return null;
    try {
      final result = await _linux.runAgentSetupCheck(
        AgentPhoneScripts.previousVersion(agent),
        timeout: const Duration(seconds: 90),
      );
      return result.ok ? AgentPhoneScripts.previousClaudeRecipe.version : null;
    } catch (_) {
      return null;
    }
  }

  Future<AgentPhoneCheckResult> _phoneCheck(
    String agentId, {
    bool previous = false,
  }) async {
    if (_removeInFlight || _checkInFlight || _payloadUses > 0) {
      throw const AgentHostException(AgentHostFailure.busy);
    }
    _checkInFlight = true;
    final generation = _generation;
    final completed = <AgentPhoneCheckStep>[];
    AgentArchitecture? arch;
    String? observedHelperVersion;
    String? retainedVersion;
    String? activeVersion;
    PaseoGateway? idleProbe;
    var helperStoppedForRollback = false;
    try {
      arch = await architecture();
      if (arch == null) {
        throw const AgentHostException(AgentHostFailure.wrongArchitecture);
      }
      _checkGeneration(generation);
      final agent = _agent(agentId);
      final oldGate = _readGates()[agentId];
      activeVersion =
          oldGate is Map &&
              oldGate['fingerprint'] == _fingerprint(agent, arch) &&
              oldGate['activeVersion'] ==
                  AgentPhoneScripts.previousClaudeRecipe.version &&
              agentId == 'claude'
          ? oldGate['activeVersion'] as String
          : agent.recipe!.version;
      final before = await _invoke('agentHostStatus');
      if (before['running'] == true) {
        idleProbe = await _requireIdleHelper();
        _checkGeneration(generation);
      }
      await _invoke('agentHostPrepareUpdate');
      _checkGeneration(generation);
      if (previous) {
        retainedVersion = await _previousVersion(agent);
        _checkGeneration(generation);
        if (retainedVersion == null) {
          throw const AgentHostException(AgentHostFailure.version);
        }
        if (idleProbe != null && idleProbe.localWorkBusy != false) {
          throw const AgentHostException(AgentHostFailure.busy);
        }
        await _invoke('stopAgentHost', {'guardedForUpdate': true});
        helperStoppedForRollback = true;
        _checkGeneration(generation);
        final activated = await _linux.runAgentSetupCheck(
          AgentPhoneScripts.usePreviousVersion(agent, retainedVersion),
          timeout: const Duration(seconds: 90),
        );
        _checkGeneration(generation);
        if (!activated.ok) {
          throw AgentHostException(
            activated.exitCode == 16
                ? AgentHostFailure.busy
                : AgentHostFailure.version,
          );
        }
        activeVersion = retainedVersion;
      }
      if (!helperStoppedForRollback &&
          idleProbe != null &&
          idleProbe.localWorkBusy != false) {
        throw const AgentHostException(AgentHostFailure.busy);
      }
      // Clear old proof only once the check has exclusive, safe admission.
      final gates = _readGates()..remove(agentId);
      await _writeGates(gates);
      if (!previous && activeVersion != agent.recipe!.version) {
        final retained = await _linux.runAgentSetupCheck(
          AgentPhoneScripts.check(
            agent,
            recipeOverride: AgentPhoneScripts.previousClaudeRecipe,
          ),
          timeout: const Duration(seconds: 90),
        );
        _checkGeneration(generation);
        if (!retained.ok) {
          throw const AgentHostException(AgentHostFailure.version);
        }
      }
      await _install(
        agentId,
        helperOnly: activeVersion != agent.recipe!.version,
      );
      await _waitInstall();
      _checkGeneration(generation);
      completed.add(AgentPhoneCheckStep.install);
      _checkProgress(agentId, AgentPhoneCheckStep.version);
      final version = await _invoke('agentHostVersion', {
        'executable': agent.recipe!.executable,
        'version': activeVersion,
      });
      if (version['installed'] != true || version['version'] != activeVersion) {
        throw const AgentHostException(AgentHostFailure.version);
      }
      final shared = await _invoke('agentHostWorkspace');
      if (shared['shared'] != true) {
        throw const AgentHostException(AgentHostFailure.version);
      }
      completed.add(AgentPhoneCheckStep.version);
      _checkProgress(agentId, AgentPhoneCheckStep.daemon);
      _checkGeneration(generation);
      if (!helperStoppedForRollback &&
          idleProbe != null &&
          idleProbe.localWorkBusy != false) {
        throw const AgentHostException(AgentHostFailure.busy);
      }
      await start(restartIfVersionChanged: true);
      if ((await _invoke('agentHostStatus'))['running'] != true) {
        throw const AgentHostException(AgentHostFailure.daemon);
      }
      completed.add(AgentPhoneCheckStep.daemon);
      _checkProgress(agentId, AgentPhoneCheckStep.hello);
      final transport = _transport(await _password());
      try {
        // This is a helper handshake, never a Claude prompt or a model call.
        // Bound total elapsed time, including individual connection timeouts.
        final deadline = DateTime.now().add(const Duration(seconds: 60));
        var connected = false;
        while (!connected && DateTime.now().isBefore(deadline)) {
          _checkGeneration(generation);
          try {
            await transport.connect().timeout(
              deadline.difference(DateTime.now()),
            );
            connected = true;
          } catch (_) {
            if (DateTime.now().isBefore(deadline)) {
              await Future<void>.delayed(const Duration(milliseconds: 500));
            }
          }
        }
        if (!connected) {
          throw const AgentHostException(AgentHostFailure.timeout);
        }
        final observed = transport.serverVersion;
        observedHelperVersion =
            observed != null &&
                observed.length <= 64 &&
                RegExp(
                  r'^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?$',
                ).hasMatch(observed)
            ? observed
            : null;
        if (observedHelperVersion != PaseoPhoneScripts.version) {
          throw const AgentHostException(AgentHostFailure.helperVersion);
        }
      } finally {
        await transport.close();
      }
      _checkGeneration(generation);
      // Installation proof survives sign-out; signing in does not require a
      // download or repeat qualification when the installed pair is unchanged.
      final current = _readGates();
      current[agentId] = {
        'fingerprint': _fingerprint(agent, arch),
        'architecture': arch.name,
        'activeVersion': activeVersion,
      };
      await _writeGates(current);
      if (agent.id == 'claude') {
        final auth = await probeSignIn(agentId);
        _checkGeneration(generation);
        if (auth.state == AgentAuthProbeState.signedOut ||
            auth.error == AgentAuthProbeError.signInExpired) {
          throw const AgentHostException(AgentHostFailure.needsSignIn);
        }
        if (auth.state != AgentAuthProbeState.signedIn) {
          throw AgentHostException(
            auth.error == AgentAuthProbeError.timedOut
                ? AgentHostFailure.timeout
                : AgentHostFailure.unavailable,
          );
        }
      }
      completed.add(AgentPhoneCheckStep.hello);
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: arch,
        passed: true,
        completed: completed,
        observedHelperVersion: observedHelperVersion,
        expectedHelperVersion: PaseoPhoneScripts.version,
        details:
            '${agent.name} $activeVersion; Paseo $observedHelperVersion. Installation and connection checked.'
            '${agent.id == 'claude' ? ' Sign-in checked.' : ''}',
      );
    } on AgentHostException catch (error) {
      if (!{
        AgentHostFailure.needsSignIn,
        AgentHostFailure.busy,
        AgentHostFailure.stale,
      }.contains(error.reason)) {
        final failedAgent = catalog.byId(agentId);
        if (failedAgent != null) {
          retainedVersion ??= await _previousVersion(failedAgent);
        }
      }
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: arch,
        passed: false,
        completed: completed,
        failure: error.reason,
        observedHelperVersion: observedHelperVersion,
        expectedHelperVersion: PaseoPhoneScripts.version,
        previousVersion: retainedVersion == activeVersion
            ? null
            : retainedVersion,
        details:
            'Agent version: ${activeVersion ?? 'not checked'}. Expected Paseo: ${PaseoPhoneScripts.version}. Observed Paseo: ${observedHelperVersion ?? 'not connected'}. Check: ${error.reason.name}.',
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
      idleProbe?.close();
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
    final fingerprintMatches =
        arch != null &&
        pin is Map &&
        pin['fingerprint'] == _fingerprint(agent, arch);
    final activeVersion =
        fingerprintMatches &&
            agentId == 'claude' &&
            pin['activeVersion'] ==
                AgentPhoneScripts.previousClaudeRecipe.version
        ? AgentPhoneScripts.previousClaudeRecipe.version
        : agent.recipe!.version;
    final version = await _invoke('agentHostVersion', {
      'executable': agent.recipe!.executable,
      'version': activeVersion,
    });
    final qualified =
        fingerprintMatches &&
        version['installed'] == true &&
        version['version'] == activeVersion;
    // Resume proof is supplied only by the controller's verified host load.
    return PhoneAgentRuntime(
      agentId: agentId,
      // Any version that runs is installed: a pin bump or the setup
      // component's copy must not read as "Not installed" (the gate below
      // still asks for the phone check on a version it did not qualify).
      installed:
          version['installed'] == true || version['foundVersion'] is String,
      payloadPresent: version['payloadPresent'] == true,
      hostAvailable: host['running'] == true,
      architectureQualified: qualified,
      checkRequiredReason: qualified
          ? null
          : _gateChangeReason(pin, agent, arch),
      capabilities: capabilities,
      signInPhase: signIn?.inspected == true ? signIn!.phase : null,
      resetAt: signIn?.resetAt,
      stoppedInBackground: qualified && host['running'] != true,
    );
  }

  String _gateChangeReason(
    Object? pin,
    AgentDescriptor agent,
    AgentArchitecture? arch,
  ) {
    if (pin is! Map) return 'firstCheck';
    try {
      final old = jsonDecode(pin['fingerprint'] as String);
      if (old is List && old.length >= 6) {
        if (old[1] != agent.recipe!.version) return 'agentVersion';
        if (old[3] != PaseoPhoneScripts.version ||
            old[4] != PaseoPhoneScripts.packageLockSha256 ||
            old[5] != PaseoPhoneScripts.nodeVersion) {
          return 'helperVersion';
        }
      }
    } catch (_) {
      /* Unreadable proof is never qualification. */
    }
    return 'installationChanged';
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
    _cancelIdleRestore();
    _detachEngine();
    await _changes.close();
  }
}
