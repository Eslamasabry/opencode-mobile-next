import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import '../api/opencode_api.dart';
import '../api/sse.dart';
import 'profiles.dart';

/// App-wide singletons that need async init before the UI can render.
class AppBootstrap {
  final ProfileStore store;
  AppBootstrap(this.store);

  static Future<AppBootstrap> create() async {
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    return AppBootstrap(store);
  }
}

final bootstrapProvider =
    FutureProvider<AppBootstrap>((ref) => AppBootstrap.create());

/// The live connection controller; overridden with a real instance in main().
final connProvider = Provider<ConnectionController>(
    (ref) => throw UnimplementedError('overridden in bootstrap'));

/// Everything the UI needs about the active server connection.
class ConnectionController extends ChangeNotifier {
  final ProfileStore store;

  OpenCodeApi? api;
  EventStream? _events;
  Timer? _poll;

  StreamStatus status = StreamStatus.disconnected;
  String? version;
  String? lastError;

  ProvidersResponse? providers;
  List<AgentInfo> agents = [];

  ModelRef? selectedModel;
  String selectedAgent = '';

  Map<String, Session> sessionsById = {};
  Set<String> busySessions = {};

  /// Outstanding permission asks keyed by sessionID.
  Map<String, PermissionRequest> permissions = {};

  /// Broadcast of every event for screen-scoped listeners (chat streaming).
  final _eventBus = StreamController<EventEnvelope>.broadcast();
  Stream<EventEnvelope> get events => _eventBus.stream;

  ConnectionController(this.store);

  bool get isConnected => status == StreamStatus.connected && api != null;

  ServerProfile? get profile {
    final id = store.activeId;
    if (id == null) return null;
    for (final p in store.profiles) {
      if (p.id == id) return p;
    }
    return null;
  }

  Future<void> connect(ServerProfile profile) async {
    await disconnect(keepActive: true, silent: true);
    await store.setActiveId(profile.id);
    api = OpenCodeApi(
      baseUrl: profile.baseUrl,
      username: profile.username,
      password: profile.password,
    );
    status = StreamStatus.connecting;
    lastError = null;
    notifyListeners();

    try {
      final health = await api!.health();
      version = health.version ?? '';
    } catch (e) {
      status = StreamStatus.disconnected;
      lastError = e is ApiException ? e.message : 'Cannot reach ${profile.baseUrl}: $e';
      api = null;
      notifyListeners();
      return;
    }

    // Restore per-profile selections.
    final saved = store.modelFor(profile.id);
    selectedModel =
        (saved.$1 != null && saved.$2 != null) ? ModelRef(providerID: saved.$1!, modelID: saved.$2!) : null;
    selectedAgent = store.agentFor(profile.id);

    unawaited(_loadCatalog());
    _startEvents();
    unawaited(refreshSessions());
    notifyListeners();
  }

  Future<void> _loadCatalog() async {
    try {
      providers = await api!.providers();
      if ((selectedModel?.modelID.isEmpty ?? true) && providers != null) {
        final p = providers!.defaultProviderID;
        final m = providers!.defaultModelID;
        if (p != null && m != null) selectedModel = ModelRef(providerID: p, modelID: m);
      }
      agents = await api!.agents();
      if (selectedAgent.isEmpty && agents.isNotEmpty) {
        selectedAgent = agents.first.name;
      }
      notifyListeners();
    } catch (_) {
      // Non-fatal; pickers show empty but chat still works.
    }
  }

  void _startEvents() {
    _events = EventStream(
      api: api!,
      onEvent: _onEvent,
      onStatus: (s) {
        status = s;
        notifyListeners();
      },
      onError: (e) {
        lastError = e.toString();
      },
    )..start();
  }

  Future<void> disconnect({bool keepActive = false, bool silent = false}) async {
    await _events?.dispose();
    _events = null;
    _poll?.cancel();
    _poll = null;
    sessionsById = {};
    busySessions = {};
    permissions = {};
    providers = null;
    agents = [];
    version = null;
    api = null;
    status = StreamStatus.disconnected;
    if (!keepActive) await store.setActiveId(null);
    if (!silent) notifyListeners();
  }

  // ---------------- Event handling ----------------

  void _onEvent(EventEnvelope env) {
    final props = env.properties;
    switch (env.type) {
      case 'server.connected':
        final v = props['version']?.toString();
        if (v != null && v.isNotEmpty) version = v;
        break;

      case 'session.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final s = Session.fromJson(info);
          sessionsById[s.id] = s;
          notifyListeners();
        }
        break;

      case 'session.deleted':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          sessionsById.remove(info['id']?.toString());
          notifyListeners();
        }
        break;

      case 'message.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          if (msg.role == 'assistant') {
            final working = (msg.time == null || !msg.time!.isDone) && msg.errorText == null;
            if (working) {
              busySessions.add(msg.sessionID);
            } else {
              busySessions.remove(msg.sessionID);
            }
            notifyListeners();
          }
        }
        break;

      case 'permission.updated':
        _handlePermission(props);
        break;

      case 'session.error':
        final err = props['error'];
        if (err is Map<String, dynamic>) {
          lastError = err['message']?.toString() ?? lastError;
        }
        break;

      case 'session.idle':
        final sid = props['sessionID']?.toString();
        if (sid != null) {
          busySessions.remove(sid);
          unawaited(_refreshOneSession(sid));
        }
        break;

      default:
        break;
    }
    _eventBus.add(env);
  }

  void _handlePermission(Map<String, dynamic> props) {
    final inner = props['info'] is Map<String, dynamic>
        ? props['info'] as Map<String, dynamic>
        : props;
    final sessionID = inner['sessionID']?.toString();
    final permID = (inner['id'] ?? inner['permissionID'])?.toString();
    if (sessionID == null || sessionID.isEmpty || permID == null || permID.isEmpty) {
      return;
    }
    permissions[sessionID] = PermissionRequest(
      key: '$sessionID/$permID',
      sessionID: sessionID,
      permissionID: permID,
      type: (inner['type'] ?? '').toString(),
      title: (inner['title'] ?? inner['type'] ?? 'Permission').toString(),
    );
    notifyListeners();
  }

  Future<void> answerPermission(String sessionID, String response) async {
    final p = permissions.remove(sessionID);
    notifyListeners();
    if (p == null || api == null) return;
    try {
      await api!.respondPermission(sessionID, p.permissionID, response);
    } catch (_) {}
  }

  // ---------------- Sessions ----------------

  Future<void> refreshSessions() async {
    if (api == null) return;
    try {
      final list = await api!.sessions();
      sessionsById = {for (final s in list) s.id: s};
      try {
        final statuses = await api!.sessionStatuses();
        busySessions
          ..clear()
          ..addAll(statuses.entries.where((e) => e.value != 'idle').map((e) => e.key));
      } catch (_) {}
      notifyListeners();
    } catch (_) {}
  }

  Future<void> _refreshOneSession(String id) async {
    if (api == null) return;
    try {
      sessionsById[id] = await api!.session(id);
      notifyListeners();
    } catch (_) {}
  }

  /// Polling fallback when SSE is unavailable.
  void enablePollingFallback() {
    _poll?.cancel();
    _poll = Timer.periodic(const Duration(seconds: 5), (_) {
      if (!isConnected) return;
      refreshSessions();
    });
  }

  List<Session> sortedSessions() {
    final list = sessionsById.values.where((s) => s.parentID == null).toList()
      ..sort((a, b) {
        final au = a.time?.updated ?? a.time?.created ?? 0;
        final bu = b.time?.updated ?? b.time?.created ?? 0;
        return bu.compareTo(au);
      });
    return list;
  }

  // ---------------- Selection persistence ----------------

  Future<void> selectModel(ModelRef ref) async {
    selectedModel = ref;
    final p = profile;
    if (p != null) await store.setModel(p.id, ref.providerID, ref.modelID);
    notifyListeners();
  }

  Future<void> selectAgent(String name) async {
    selectedAgent = name;
    final p = profile;
    if (p != null) await store.setAgent(p.id, name);
    notifyListeners();
  }
}
