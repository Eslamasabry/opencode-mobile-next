// The phone's Work tab as the owner's screenshots showed it (FinanceHub3
// open on "This device (Termux)", FinanceHub and Tradebook in use), for the
// Work tab goldens and status-line tests.
import 'dart:async';

import 'package:clock/clock.dart';

import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart'
    show CaptureApi, CaptureController, CaptureRepository, SeededProfileStore;

const workRoot = '/root/projects';
const workCurrent = '$workRoot/FinanceHub3';
const workOther = '$workRoot/FinanceHub';
const workThird = '$workRoot/Tradebook';

/// The fixture's "now": the test's clock when first read (a golden test
/// pins it, so day groups and ages never depend on the hour it runs).
final workNow = clock.now().millisecondsSinceEpoch;
const workMinute = 60 * 1000;

Session workSession(
  String id,
  String title, {
  String directory = workCurrent,
  int ago = 5 * workMinute,
  int? idle,
}) => Session(
  id: id,
  title: title,
  directory: directory,
  time: SessionTime(
    created: workNow - ago - workMinute,
    updated: workNow - ago,
    idle: idle,
  ),
);

class WorkRepository extends CaptureRepository
    implements SessionReadStateGateway {
  Completer<void>? holdProjects;
  List<WorkspaceProject>? projects;
  final views = <String>[];

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    await holdProjects?.future;
    return List.of(
      projects ??
          const [
            WorkspaceProject(
              id: 'p-fh3',
              name: 'FinanceHub3',
              directory: workCurrent,
              worktrees: [],
              updatedAt: 3,
            ),
            WorkspaceProject(
              id: 'p-fh',
              name: 'FinanceHub',
              directory: workOther,
              worktrees: [],
              updatedAt: 2,
            ),
          ],
    );
  }

  @override
  Future<void> viewSession(String sessionID, int idle) async =>
      views.add(sessionID);
}

class WorkController extends CaptureController {
  WorkController(super.store);

  List<ProfileLocation> recents = const [];
  List<ElsewhereConversation> elsewhere = const [];
  bool holdSelection = false;
  final selected = <String?>[];
  int refreshes = 0;

  /// The AI Team plugin's controller, when a test shows the team section.
  OrchestrationController? team;

  @override
  OrchestrationController? get orchestration => team ?? super.orchestration;

  /// A stand-in for the other servers' watcher, when a test shows them.
  ProfileMonitor? monitor;

  @override
  ProfileMonitor get profileMonitor => monitor ?? super.profileMonitor;

  @override
  bool isProfileReadable(String id) =>
      monitor != null || super.isProfileReadable(id);

  @override
  List<ProfileLocation> get recentLocations => recents;

  @override
  Future<List<ElsewhereConversation>> conversationsElsewhere({
    int limit = 6,
  }) async => elsewhere;

  @override
  Future<void> refreshSessions() async => refreshes++;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> selectInitialLocation({
    String? directory,
    String? workspace,
  }) async {
    selected.add(directory);
    if (holdSelection) await Completer<void>().future;
  }

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    selected.add(directory);
    if (holdSelection) await Completer<void>().future;
    this.directory = directory;
    notifyListeners();
  }
}

Future<WorkController> workController({
  String? directory = workCurrent,
  StreamStatus status = StreamStatus.connected,
  Map<String, Session> sessions = const {},
  Set<String> busy = const {},
  bool savedLocation = true,
  String baseUrl = 'http://127.0.0.1:4096',
  String name = 'This device (Termux)',
  WorkRepository? repository,
  bool otherProjects = false,
  List<ServerProfile> otherServers = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(id: 'phone', name: name, baseUrl: baseUrl),
      ...otherServers,
    ],
  );
  if (savedLocation) await store.setLocation('phone', directory: workCurrent);
  final controller = WorkController(store)
    ..api = CaptureApi()
    ..repository = repository ?? WorkRepository()
    ..status = status
    ..directory = directory
    ..sessionsById = Map.of(sessions)
    ..busySessions = Set.of(busy);
  if (otherProjects) {
    controller
      ..recents = const [
        ProfileLocation(directory: workCurrent),
        ProfileLocation(directory: workOther),
        ProfileLocation(directory: workThird),
      ]
      ..elsewhere = [
        ElsewhereConversation(
          session: workSession(
            'e1',
            'building modern financehub',
            directory: workOther,
            ago: 62 * workMinute,
          ),
          directory: workOther,
          running: true,
        ),
        ElsewhereConversation(
          session: workSession(
            'e2',
            'Service-led sales research and demo',
            directory: workThird,
            ago: 26 * 60 * workMinute,
          ),
          directory: workThird,
          running: false,
        ),
      ];
  }
  return controller;
}

/// A loaded project: one waiting on a permission, one running, one
/// unreviewed result and an older one.
Map<String, Session> workLoadedSessions() => {
  'unreviewed': workSession(
    'unreviewed',
    'Reconcile the March ledger import',
    ago: 12 * workMinute,
    idle: workNow - 12 * workMinute,
  ),
  'waiting': workSession(
    'waiting',
    'Upgrade the charting library',
    ago: 3 * workMinute,
  ),
  'busy': workSession('busy', 'Add CSV export to reports', ago: workMinute),
  'older': workSession(
    'older',
    'Explain the budget rules engine',
    ago: 5 * 60 * workMinute,
  ),
};

PermissionRequest workPermission() => PermissionRequest(
  id: 'perm',
  sessionID: 'waiting',
  permission: 'bash',
  patterns: const ['npm install chart.js@5'],
);
