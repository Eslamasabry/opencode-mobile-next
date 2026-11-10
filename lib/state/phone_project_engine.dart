import 'dart:async';
import 'dart:convert';

import 'package:uuid/uuid.dart';

import '../builtin/builtin_linux.dart';
import '../domain/phone_project_engine.dart';
import '../domain/orchestration_gateway.dart';
import '../orchestration/adapters/inapp/phone_engine_gateway.dart';
import '../orchestration/adapters/gascity/gascity_probe.dart';
import '../ui/kit/kit_redact.dart';
import 'profiles.dart';

/// Small native seam: callers cannot start shell scripts or inject credentials.
abstract interface class PhoneProjectEngineBridge {
  Future<void> start(String profileId, {int port = 4098, String? notice});
  Future<({String baseUrl, String bearerToken})> credentials(String profileId);
  Future<void> delete(String profileId);
  Future<void> stop(String profileId);
}

class BuiltinPhoneProjectEngineBridge implements PhoneProjectEngineBridge {
  BuiltinPhoneProjectEngineBridge([BuiltinLinux? builtin])
    : _builtin = builtin ?? BuiltinLinux();
  final BuiltinLinux _builtin;

  /// Match the app-owned OC1 profile before native snapshots server auth.
  /// The protected restart writes this same password; it must not strand the
  /// already-started daemon with a stale rootfs copy.
  Future<void> prepareServerAuthentication(ServerProfile profile) async {
    if (!BuiltinLinux.managesServerUrl(profile.baseUrl) ||
        profile.password.isEmpty) {
      throw const PhoneEngineException('server_auth_unavailable');
    }
    final written = await _builtin.writeServerPassword(profile.password);
    if (!written.ok) {
      throw const PhoneEngineException('server_credentials_write_failed');
    }
  }

  @override
  Future<void> start(
    String profileId, {
    int port = 4098,
    String? notice,
  }) async {
    await _builtin.startPhoneEngine(
      profileId: profileId,
      port: port,
      notice: notice,
    );
  }

  @override
  Future<({String baseUrl, String bearerToken})> credentials(
    String profileId,
  ) async {
    final value = await _builtin.phoneEngineCredentials(profileId);
    return (baseUrl: value.baseUrl, bearerToken: value.bearerToken);
  }

  @override
  Future<void> stop(String profileId) async {
    final status = await _builtin.stopPhoneEngine(profileId);
    if (status.running) {
      throw const PhoneEngineException('engineStopFailed');
    }
  }

  @override
  Future<void> delete(String profileId) =>
      _builtin.deletePhoneEngine(profileId);
}

typedef PhoneEngineGatewayBuilder =
    PhoneEngineGateway Function({
      required String baseUrl,
      required String profileId,
      required String bearerToken,
    });

/// An observation of this app's current reconciled connection, never global idle.
class PhoneChatActivity {
  const PhoneChatActivity({
    this.known = false,
    this.sessionIds = const [],
    this.directories = const [],
  });
  final bool known;
  final List<String> sessionIds, directories;
  String get signature => jsonEncode([
    known,
    (List<String>.of(sessionIds)..sort()),
    (List<String>.of(directories)..sort()),
  ]);
}

/// Exact dispatch latches; only an observation begun after transport settlement
/// in the same directory can establish idle for that session.
class PhoneChatDispatchTracker {
  int epoch = 0;
  final Map<
    String,
    ({
      int pending,
      int settledEpoch,
      String? directory,
      bool started,
      bool retained,
    })
  >
  _turns = {};
  Iterable<String> get sessionIds => _turns.keys;
  Iterable<String> get directories =>
      _turns.values.map((t) => t.directory).whereType<String>();
  bool get isNotEmpty => _turns.isNotEmpty;
  bool contains(String id) => _turns.containsKey(id);
  void begin(String id, String? directory) {
    _turns[id] = (
      pending: (_turns[id]?.pending ?? 0) + 1,
      settledEpoch: ++epoch,
      directory: directory,
      started: false,
      retained: _turns.containsKey(id),
    );
  }

  void settled(String id, {bool removeUnsent = false}) {
    final turn = _turns[id];
    if (turn == null) {
      return;
    }
    final pending = turn.pending - 1;
    if (removeUnsent && pending == 0 && !turn.retained) {
      _turns.remove(id);
    } else {
      _turns[id] = (
        pending: pending,
        settledEpoch: ++epoch,
        directory: turn.directory,
        // An overlapping request can observe the earlier turn still busy.
        // Require a new busy observation after this batch settles.
        started: turn.started && !turn.retained,
        retained: turn.retained,
      );
    }
  }

  void observeBusy(String id) {
    final turn = _turns[id];
    if (turn == null) {
      return;
    }
    _turns[id] = (
      pending: turn.pending,
      settledEpoch: turn.settledEpoch,
      directory: turn.directory,
      started: true,
      retained: turn.retained,
    );
  }

  void reconcile(
    Map<String, String> statuses,
    int readEpoch,
    String? directory,
  ) {
    for (final entry in statuses.entries) {
      final turn = _turns[entry.key];
      if (turn != null &&
          turn.pending == 0 &&
          readEpoch >= turn.settledEpoch &&
          turn.directory == directory &&
          (entry.value == 'busy' || entry.value == 'retry')) {
        observeBusy(entry.key);
      }
    }
    _turns.removeWhere(
      (id, turn) =>
          turn.pending == 0 &&
          turn.started &&
          readEpoch >= turn.settledEpoch &&
          turn.directory == directory &&
          (statuses[id] == null || statuses[id] == 'idle'),
    );
  }

  void reset() {
    _turns.clear();
    epoch++;
  }
}

typedef PhoneChatSource = PhoneChatActivity Function(String profileId);
typedef PhoneChatRenewalScheduler =
    void Function() Function(Duration period, void Function() tick);

/// Serial, monotonic producer. A stopped Flutter client never fabricates idle.
class PhoneChatHeartbeat {
  PhoneChatHeartbeat({
    required this.gateway,
    required this.source,
    DateTime Function()? now,
    PhoneChatRenewalScheduler? schedule,
    String? appInstance,
    int Function()? nextSequence,
  }) : _now = now ?? DateTime.now,
       _schedule = schedule ?? _periodic,
       appInstance = appInstance ?? const Uuid().v4(),
       _nextSequence = nextSequence;
  final PhoneEngineGateway gateway;
  final PhoneChatActivity Function() source;
  final DateTime Function() _now;
  final PhoneChatRenewalScheduler _schedule;
  final String appInstance;
  final int Function()? _nextSequence;
  static const renewal = Duration(seconds: 10);
  static const lease = Duration(seconds: 30);
  static void Function() _periodic(Duration period, void Function() tick) {
    final timer = Timer.periodic(period, (_) => tick());
    return timer.cancel;
  }

  void Function()? _cancel;
  Future<void> _tail = Future.value();
  int _sequence = 0;
  String? _signature;
  bool _stopped = false;
  void start() {
    if (_stopped || _cancel != null) {
      return;
    }
    _cancel = _schedule(
      renewal,
      () => unawaited(push(force: true).catchError((Object _) {})),
    );
    unawaited(push(force: true).catchError((Object _) {}));
  }

  Future<void> push({bool force = false, bool requireDelivery = false}) {
    if (_stopped) {
      return Future.value();
    }
    final snapshot = source();
    if (!force && snapshot.signature == _signature) {
      return _tail;
    }
    _signature = snapshot.signature;
    return _enqueue(snapshot, requireDelivery: requireDelivery);
  }

  Future<void> _enqueue(
    PhoneChatActivity snapshot, {
    bool terminal = false,
    bool requireDelivery = false,
  }) {
    final seq = ++_sequence;
    final wireSequence = _nextSequence?.call() ?? seq;
    final until = _now().add(lease).millisecondsSinceEpoch;
    final sessions = List<String>.of(snapshot.sessionIds);
    final directories = List<String>.of(snapshot.directories);
    final next = _tail.then((_) async {
      // Coalesce unsent older observations. Already-sent requests drain in order.
      if ((_stopped && !terminal) || until <= _now().millisecondsSinceEpoch) {
        if (requireDelivery) {
          throw const PhoneEngineException('chatLeaseExpired');
        }
        return;
      }
      if (seq != _sequence && !requireDelivery) {
        return;
      }
      await gateway.sendChatBusy(
        until: until,
        sessionIds: sessions,
        directories: directories,
        known: snapshot.known,
        appInstance: appInstance,
        sequence: wireSequence,
      );
    });
    _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  Future<void> stop({bool publishUnknown = true}) async {
    if (_stopped) {
      await _tail;
      return;
    }
    _stopped = true;
    _cancel?.call();
    _cancel = null;
    if (publishUnknown) {
      try {
        await _enqueue(const PhoneChatActivity(), terminal: true);
      } catch (_) {
        /* Expiry remains UNKNOWN when final delivery fails. */
      }
    } else {
      await _tail;
    }
    await gateway.close();
  }
}

/// Profile-owned lifecycle, including inactive profiles. Never auto-starts daemon.
class PhoneProjectEngineController {
  PhoneProjectEngineController({
    required this.store,
    PhoneProjectEngineBridge? bridge,
    PhoneEngineGatewayBuilder? gatewayBuilder,
    this.onAttached,
    this.onReady,
    this.chatSource,
    this.chatSchedule,
    this.chatActive,
  }) : bridge = bridge ?? BuiltinPhoneProjectEngineBridge(),
       _build = gatewayBuilder ?? _defaultBuild;
  final ProfileStore store;
  final void Function(String profileId)? onAttached;

  /// One attach/start completion signal, after saved credentials and producer
  /// ownership. A health poll alone never requests app transport recovery.
  final void Function(String profileId)? onReady;
  final PhoneChatSource? chatSource;
  final bool Function(String profileId)? chatActive;
  final Set<String> _admissionSuspended = {};
  final Set<String> _admissionRequired = {};
  final Map<String, Future<void>> _admissionStops = {};
  final PhoneChatRenewalScheduler? chatSchedule;
  final Map<String, PhoneChatHeartbeat> _heartbeats = {};
  final Map<String, Future<void>> _heartbeatStops = {};
  final String _chatInstance = const Uuid().v4();
  int _chatSequence = 0;
  final PhoneProjectEngineBridge bridge;
  final PhoneEngineGatewayBuilder _build;
  final Map<String, Set<PhoneEngineGateway>> _gateways = {};
  final Map<String, Future<void>> _tails = {};
  final Set<String> _deleted = {};
  bool _closed = false;
  final Map<String, Future<void>> _deletions = {};
  static PhoneEngineGateway _defaultBuild({
    required String baseUrl,
    required String profileId,
    required String bearerToken,
  }) => PhoneEngineGateway(
    baseUrl: baseUrl,
    profileId: profileId,
    bearerToken: bearerToken,
  );
  String _tombstone(String id) => 'oc.teamEngineDeleted.$id';
  bool _blocked(String id) =>
      _deleted.contains(id) || store.prefs.getBool(_tombstone(id)) == true;

  /// Includes a first activation that has not persisted its credentials yet.
  bool hasLifecycleOwnership(String profileId) =>
      _tails.containsKey(profileId) ||
      _gateways.containsKey(profileId) ||
      _deletions.containsKey(profileId) ||
      _blocked(profileId);

  ServerProfile _profile(String id) => store.profiles.firstWhere(
    (p) => p.id == id,
    orElse: () => throw const PhoneEngineException('profileMissing'),
  );
  Future<T> _serial<T>(String id, Future<T> Function() action) {
    if (_closed) {
      return Future.error(const PhoneEngineException('engineClosed'));
    }
    if (_blocked(id)) {
      return Future.error(const PhoneEngineException('profileDeleted'));
    }
    final next = (_tails[id] ?? Future<void>.value()).then((_) async {
      if (_blocked(id)) {
        throw const PhoneEngineException('profileDeleted');
      }
      {
        return action();
      }
    });
    _tails[id] = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  PhoneEngineGateway gateway(ServerProfile profile) {
    if (_closed) {
      throw const PhoneEngineException('engineClosed');
    }
    if (_blocked(profile.id)) {
      throw const PhoneEngineException('profileDeleted');
    }
    final config = profile.orchestration;
    if (config?.provider != OrchestrationProvider.phoneEngine) {
      {
        throw const PhoneEngineException('engineUnavailable');
      }
    }
    final client = _build(
      baseUrl: config!.url,
      profileId: profile.id,
      bearerToken: profile.teamEngineAuth,
    );
    (_gateways[profile.id] ??= {}).add(client);
    client.addCloseListener(() {
      final clients = _gateways[profile.id];
      clients?.remove(client);
      if (clients?.isEmpty ?? false) _gateways.remove(profile.id);
    });
    return client;
  }

  Future<PhoneEngineHealth> start(
    String profileId, {
    int port = 4098,
    String? notice,
  }) => _serial(profileId, () async {
    if (port < 1 || port > 65535) {
      throw const PhoneEngineException('endpointInvalid');
    }
    await _admissionStops[profileId];
    // Start rotates native credentials. Drain old producers/clients before
    // launch so an old heartbeat cannot retire the new daemon generation.
    _admissionRequired.add(profileId);
    await (_admissionStops[profileId] ??= _stopForAdmission(profileId)
        .whenComplete(() {
          _admissionStops.remove(profileId);
        }));
    final oldClients = _gateways.remove(profileId) ?? <PhoneEngineGateway>{};
    for (final client in oldClients.toList()) {
      await client.close();
    }
    try {
      final native = bridge;
      if (native is BuiltinPhoneProjectEngineBridge) {
        await native.prepareServerAuthentication(_profile(profileId));
      }
      await bridge.start(profileId, port: port, notice: notice);
    } on BuiltinLinuxException catch (error) {
      // Native emits static codes. Never retain its message/details or cause.
      throw PhoneEngineException(error.code ?? 'engineUnavailable');
    } on PhoneEngineException {
      rethrow;
    } catch (_) {
      throw const PhoneEngineException('engineUnavailable');
    }
    return _attach(profileId);
  });
  Future<PhoneEngineHealth> attach(String profileId) =>
      _serial(profileId, () => _attach(profileId));

  /// Roll back a failed setup without deleting the person's team data.
  /// Queue behind start so even a timed-out launch cannot outlive its cleanup.
  Future<void> stop(String profileId) => _serial(profileId, () async {
    await _admissionStops[profileId];
    _admissionRequired.add(profileId);
    await (_admissionStops[profileId] ??= _stopForAdmission(profileId)
        .whenComplete(() {
          _admissionStops.remove(profileId);
        }));
    final clients = _gateways.remove(profileId) ?? <PhoneEngineGateway>{};
    for (final client in clients.toList()) {
      await client.close();
    }
  });

  Future<PhoneEngineHealth> _attach(String profileId) async {
    final profile = _profile(profileId);
    late final ({String baseUrl, String bearerToken}) credentials;
    try {
      credentials = await bridge.credentials(profileId);
    } catch (_) {
      throw const PhoneEngineException('engineUnavailable');
    }
    KitRedact.registerKnownSecret(credentials.bearerToken);
    final client = _build(
      baseUrl: credentials.baseUrl,
      profileId: profileId,
      bearerToken: credentials.bearerToken,
    );
    try {
      final h = await client.probe();
      if (_closed) {
        throw const PhoneEngineException('engineClosed');
      }
      if (_blocked(profileId)) {
        throw const PhoneEngineException('profileDeleted');
      }
      final previousConfig = profile.orchestration;
      final previousAuth = profile.teamEngineAuth;
      profile.teamEngineAuth = credentials.bearerToken;
      profile.orchestration = OrchestrationConfig(
        provider: OrchestrationProvider.phoneEngine,
        url: credentials.baseUrl,
        hostMode: OrchestrationHostMode.phone,
        enabledAt: previousConfig?.enabledAt ?? DateTime.now(),
      );
      try {
        await store.upsert(profile);
      } catch (_) {
        profile.orchestration = previousConfig;
        profile.teamEngineAuth = previousAuth;
        throw const PhoneEngineException('authSaveFailed');
      }
      if (_blocked(profileId)) {
        throw const PhoneEngineException('profileDeleted');
      }
      await stopChatHeartbeat(profileId, publishUnknown: false);
      if (_closed) {
        throw const PhoneEngineException('engineClosed');
      }
      if (_blocked(profileId)) {
        throw const PhoneEngineException('profileDeleted');
      }
      _admissionSuspended.remove(profileId);
      _admissionRequired.remove(profileId);
      startChatHeartbeat(profileId);
      onAttached?.call(profileId);
      if (h.canExecute && !_closed && !_blocked(profileId)) {
        onReady?.call(profileId);
      }
      return h;
    } finally {
      await client.close();
    }
  }

  void startChatHeartbeat(String profileId) {
    if (_closed ||
        _blocked(profileId) ||
        _admissionSuspended.contains(profileId) ||
        _admissionStops.containsKey(profileId) ||
        chatSource == null ||
        (chatActive != null && !chatActive!(profileId)) ||
        _heartbeatStops.containsKey(profileId) ||
        _heartbeats.containsKey(profileId)) {
      return;
    }
    final client = gateway(_profile(profileId));
    final heartbeat = PhoneChatHeartbeat(
      gateway: client,
      source: () => chatSource!(profileId),
      schedule: chatSchedule,
      appInstance: _chatInstance,
      nextSequence: () => ++_chatSequence,
    );
    _heartbeats[profileId] = heartbeat;
    heartbeat.start();
  }

  Future<void> pushChatHeartbeat(
    String profileId, {
    bool force = false,
    bool requireDelivery = false,
  }) async {
    if (_closed || _blocked(profileId)) {
      return;
    }
    await _heartbeatStops[profileId];
    startChatHeartbeat(profileId);
    if (requireDelivery && _heartbeats[profileId] == null) {
      throw const PhoneEngineException('chatAdmissionUnavailable');
    }
    await _heartbeats[profileId]?.push(
      force: force,
      requireDelivery: requireDelivery,
    );
  }

  Future<void> stopChatHeartbeat(
    String profileId, {
    bool publishUnknown = true,
  }) {
    final existing = _heartbeatStops[profileId];
    if (existing != null) {
      return existing;
    }
    final producer = _heartbeats.remove(profileId);
    if (producer == null) {
      return Future.value();
    }
    return _heartbeatStops[profileId] = producer
        .stop(publishUnknown: publishUnknown)
        .whenComplete(() {
          _heartbeatStops.remove(profileId);
        });
  }

  /// Human chat wins: if a prior idle lease cannot be replaced, stop only
  /// this engine. Restart/resume is explicit; the OpenCode server stays alive.
  Future<void> beforePersonDispatch(String profileId) async {
    if (_closed || _blocked(profileId)) {
      throw const PhoneEngineException('engineClosed');
    }
    if (_admissionSuspended.contains(profileId)) {
      return;
    }
    final stopping = _admissionStops[profileId];
    if (stopping != null) {
      await stopping;
      return;
    }
    try {
      await pushChatHeartbeat(profileId, force: true, requireDelivery: true);
    } catch (_) {
      await suspendChatAdmission(profileId);
    }
  }

  Future<void> suspendChatAdmission(String profileId) {
    if (_admissionSuspended.contains(profileId)) {
      _admissionRequired.remove(profileId);
      return Future.value();
    }
    _admissionRequired.add(profileId);
    return _admissionStops[profileId] ??= _stopForAdmission(profileId)
        .whenComplete(() {
          _admissionStops.remove(profileId);
        });
  }

  Future<void> preparePhoneAliasDispatch(String profileId) async {
    // Another saved alias can still own a daemon after an app/profile switch.
    for (final profile in store.profiles) {
      if (profile.id != profileId &&
          BuiltinLinux.managesServerUrl(profile.baseUrl) &&
          (profile.orchestration?.provider ==
                  OrchestrationProvider.phoneEngine ||
              profile.teamEngineAuth.isNotEmpty)) {
        _admissionRequired.add(profile.id);
      }
    }
    for (final id in _admissionRequired.toList()) {
      if (id != profileId) {
        await suspendChatAdmission(id);
      }
    }
  }

  Future<void> _stopForAdmission(String profileId) async {
    await stopChatHeartbeat(profileId, publishUnknown: false);
    try {
      await bridge.stop(profileId);
    } catch (_) {
      throw const PhoneEngineException('engineStopFailed');
    }
    _admissionSuspended.add(profileId);
    _admissionRequired.remove(profileId);
  }

  Future<PhoneEngineHealth> probe(String profileId) =>
      _serial(profileId, () async {
        final client = gateway(_profile(profileId));
        try {
          return await client.probe();
        } finally {
          await client.close();
        }
      });
  Future<ProbeVerdict> orchestrationProbe(ServerProfile profile) async {
    PhoneEngineGateway? client;
    try {
      client = gateway(profile);
      final h = await client.probe();
      return ProbeFound(
        host: client.host,
        version: h.engineVersion,
        readOnly: !h.canExecute,
        capabilities: client.capabilities,
        boundaryTier: h.boundaryTier,
      );
    } catch (_) {
      return const ProbeUnreachable(error: 'Phone engine unavailable');
    } finally {
      await client?.close();
    }
  }

  Future<OrchestrationGateway> orchestrationGateway(
    ServerProfile profile,
  ) async {
    final client = gateway(profile);
    try {
      await client.probe();
      startChatHeartbeat(profile.id);
      return client;
    } catch (_) {
      await client.close();
      rethrow;
    }
  }

  /// Native deletion is authoritative even when a stopped daemon cannot answer.
  /// [linuxGone]: the in-app Linux was erased, so the engine inside it is
  /// gone too and the native cleanup has nothing left to reach; its failure
  /// is not a failure to delete.
  Future<void> deleteProfile(String profileId, {bool linuxGone = false}) {
    _deleted.add(profileId);
    for (final client in _gateways[profileId] ?? <PhoneEngineGateway>{}) {
      client.beginDeletion();
    }
    return _deletions[profileId] ??= _delete(profileId, linuxGone: linuxGone)
        .whenComplete(() {
          _deletions.remove(profileId);
        });
  }

  Future<void> _delete(String profileId, {bool linuxGone = false}) async {
    if (!await store.prefs.setBool(_tombstone(profileId), true)) {
      {
        throw const PhoneEngineException('deleteFailed');
      }
    }
    await stopChatHeartbeat(profileId, publishUnknown: false);
    await _tails[profileId];
    final clients = _gateways.remove(profileId) ?? <PhoneEngineGateway>{};
    final active = clients.where((client) => !client.isDisconnected).toList();
    // Deletion may precede any screen attaching after restart.
    if (active.isEmpty) {
      for (final profile in store.profiles) {
        if (profile.id == profileId &&
            profile.orchestration?.provider ==
                OrchestrationProvider.phoneEngine &&
            profile.teamEngineAuth.isNotEmpty) {
          try {
            active.add(
              _build(
                baseUrl: profile.orchestration!.url,
                profileId: profileId,
                bearerToken: profile.teamEngineAuth,
              ),
            );
          } catch (_) {
            /* Native cleanup remains authoritative. */
          }
        }
      }
    }
    for (final client in active) {
      try {
        await client.deleteLocalData();
      } catch (_) {
        await client.close();
      }
    }
    for (final client in clients) {
      await client.close();
    }
    try {
      try {
        await bridge.delete(profileId);
      } catch (_) {
        if (!linuxGone) rethrow;
      }
      _admissionRequired.remove(profileId);
      _admissionSuspended.add(profileId);
    } catch (_) {
      throw const PhoneEngineException('deleteFailed');
    }
    try {
      await store.clearTeamEngineAuth(profileId);
    } catch (_) {
      throw const PhoneEngineException('deleteFailed');
    }
  }

  Future<void> close() async {
    _closed = true;
    for (final id in _heartbeats.keys.toList()) {
      await stopChatHeartbeat(id);
    }
    await Future.wait(_heartbeatStops.values.toList());
    await Future.wait(
      _admissionStops.values
          .map(
            (f) => f.then<void>((_) {}, onError: (Object _, StackTrace _) {}),
          )
          .toList(),
    );
    await Future.wait(_tails.values.toList());
    final clients = _gateways.values.expand((v) => v).toList();
    for (final client in clients) {
      await client.close();
    }
    _gateways.clear();
  }
}
