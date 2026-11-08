// Shared fixtures for shared-servers-1's behaviour tests and goldens: a
// store with one current server and three saved ones, and a controller whose
// attention source answers with fixed snapshots.
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';

/// Work (current), Laptop, Lab box and a server whose password is needed
/// again. Nothing is read from secure storage.
class SwitcherStore extends ProfileStore {
  SwitcherStore({required super.prefs});

  final _profiles = [
    ServerProfile(
      id: 'work',
      name: 'Work server',
      baseUrl: 'https://work.example.test',
    ),
    ServerProfile(
      id: 'laptop',
      name: 'Laptop',
      baseUrl: 'https://laptop.example.test:4096',
    ),
    ServerProfile(
      id: 'lab',
      name: 'Lab box',
      baseUrl: 'https://lab.example.test',
    ),
    ServerProfile(
      id: 'locked',
      name: 'Home desk',
      baseUrl: 'https://home.example.test',
      requiresPasswordReentry: true,
    ),
  ];

  @override
  List<ServerProfile> get profiles => _profiles;

  @override
  String? get activeId => 'work';
}

class SwitcherController extends ConnectionController {
  SwitcherController(
    super.store, {
    Map<String, ProfileAttentionSnapshot> snapshots = const {},
  }) : _snapshots = snapshots;

  // The base constructor already reads the monitor, so it is made lazily.
  final Map<String, ProfileAttentionSnapshot> _snapshots;
  FixedMonitor? _monitor;
  int disconnects = 0;

  @override
  ProfileMonitor get profileMonitor =>
      _monitor ??= FixedMonitor(store, _snapshots);

  @override
  bool isProfileReadable(String id) => true;

  @override
  Future<void> disconnect({bool keepActive = false, bool silent = false}) {
    disconnects++;
    return super.disconnect(keepActive: keepActive, silent: silent);
  }
}

/// The other servers' watcher, answering with [fixed].
class FixedMonitor extends ProfileMonitor {
  FixedMonitor(ProfileStore store, this.fixed)
    : super(
        store: store,
        createGateway: (_) => throw UnimplementedError(),
        isReadable: (_) => true,
        networkWifi: () async => null,
        alert: (_, _, _, _) async => false,
        dismiss: (_) async => false,
      );

  final Map<String, ProfileAttentionSnapshot> fixed;

  @override
  ProfileAttentionSnapshot snapshotFor(String id) =>
      fixed[id] ??
      ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      );
}

ProfileAttentionSnapshot snapshot(
  String id, {
  int waiting = 0,
  int running = 0,
  bool current = true,
}) => ProfileAttentionSnapshot(
  profileID: id,
  status: current
      ? ProfileMonitorStatus.current
      : ProfileMonitorStatus.disabled,
  complete: true,
  runningCount: running,
  requests: [
    for (var i = 0; i < waiting; i++)
      MonitoredRequest(
        id: 'perm-$i',
        sessionID: 's-$i',
        kind: MonitoredRequestKind.permission,
      ),
  ],
);
