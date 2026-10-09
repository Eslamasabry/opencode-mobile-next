// Shared by the lists coverage ratchets (chats, projects and sessions lists):
// a loopback server that answers like an OpenCode server, so the app's real
// gateways parse the wire payloads, and a repository + connection the
// screens run on.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:opencode_mobile/ui/screens/running_work_sheet.dart';
import 'package:opencode_mobile/ui/screens/session_context_screen.dart';
import 'package:opencode_mobile/ui/screens/session_destination_sheet.dart';
import 'package:opencode_mobile/ui/screens/session_relations_screen.dart';
import 'paseo_coverage_support.dart' show screenText, writeCasePng;
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

class _RealHttp extends HttpOverrides {}

/// Runs [body] with the real network stack (the test binding fakes it).
Future<T> withRealHttp<T>(Future<T> Function() body) => HttpOverrides.runZoned(
  body,
  createHttpClient: (_) => _RealHttp().createHttpClient(null),
);

typedef WireRequest = ({String method, String path, Uri uri, String body});

/// A loopback server that answers every request through [handler]; a null
/// answer is an empty 204.
class WireServer {
  WireServer._(this.server) {
    server.listen((request) async {
      final text = await utf8.decoder.bind(request).join();
      final seen = (
        method: request.method,
        path: request.uri.path,
        uri: request.uri,
        body: text,
      );
      requests.add(seen);
      final answer = await handler?.call(seen);
      request.response.headers.contentType = ContentType.json;
      if (answer == null) {
        request.response.statusCode = HttpStatus.noContent;
      } else {
        request.response.write(jsonEncode(answer));
      }
      await request.response.close();
    });
  }

  final HttpServer server;
  final requests = <WireRequest>[];
  FutureOr<Object?> Function(WireRequest request)? handler;

  static Future<WireServer> start() async =>
      WireServer._(await HttpServer.bind(InternetAddress.loopbackIPv4, 0));

  String get baseUrl => 'http://${server.address.host}:${server.port}';

  Future<void> close() => server.close(force: true);
}

/// The product repository the list screens run on: answers from fixtures the
/// test sets, and records the calls it receives.
class ListsRepository
    implements
        ProductRepository,
        SessionNoteGateway,
        SessionImportGateway,
        SessionReadStateGateway {
  List<GlobalSessionResult> global = const [];
  List<Session> sessions = const [];
  List<WorkspaceProject> projects = const [];
  List<ProjectDirectoryInfo> directories = const [];
  List<WorkspaceInfo> workspaces = const [];
  final calls = <String>[];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async => ServerPage(items: global);

  @override
  Future<Session> getSessionDetails(String id) async {
    calls.add('getSessionDetails');
    for (final session in [...sessions, ...global.map((r) => r.session)]) {
      if (session.id == id) return session;
    }
    return Session(id: id);
  }

  @override
  Future<List<Session>> listSessionChildren(String id) async => {
    for (final session in [...sessions, ...global.map((r) => r.session)])
      if (session.parentID == id) session.id: session,
  }.values.toList();

  @override
  Future<List<WorkspaceProject>> listProjects() async => List.of(projects);

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => workspaces;

  /// The server keeps what was read (OpenCode 2); OpenCode 1 does not.
  @override
  Future<void> viewSession(String sessionID, int idle) async =>
      calls.add('viewSession');

  String? note;

  @override
  bool get sessionNotesSupported => true;

  @override
  Future<String?> loadSessionNote(String sessionID) async => note;

  @override
  Future<void> saveSessionNote(String sessionID, String value) async {
    calls.add('saveSessionNote');
    note = value;
  }

  @override
  Future<void> removeSessionNote(String sessionID) async {
    calls.add('removeSessionNote');
    note = null;
  }

  @override
  bool get sessionImportSupported => true;

  @override
  Future<Session> importSession(
    SessionImportDocument document,
    SessionImportDestination destination,
  ) async {
    calls.add('importSession');
    return Session(id: document.id, title: document.title);
  }

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) async {
    calls.add('renameProject');
    return WorkspaceProject(
      id: projectID,
      name: name,
      directory: projectDirectory,
      worktrees: const [],
      updatedAt: 2,
    );
  }

  @override
  Future<String> stealSessionIntoWorkspace(String sessionID) async {
    calls.add('stealSessionIntoWorkspace');
    return sessionID;
  }

  @override
  Future<WorkspaceProject?> loadCurrentProject() async =>
      projects.isEmpty ? null : projects.first;

  @override
  Future<ManagedShellList> loadRunningShells() async =>
      const ManagedShellList(supported: false);

  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String id) async =>
      directories;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// The v1 transport the list screens run on: statuses come from the test.
class ListsApi extends CaptureApi {
  ListsApi([this.caps]);

  /// The server's abilities; OpenCode 2's for the OpenCode 2 cases.
  final ServerCapabilities? caps;

  @override
  ServerCapabilities get capabilities => caps ?? super.capabilities;

  Map<String, String> statuses = const {};
  Map<String, SessionRetryState> retries = const {};

  @override
  Future<Map<String, String>> sessionStatuses() async => statuses;

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => retries;

  @override
  Future<List<Session>> sessions() async => sessionsById.values.toList();

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: sampleTranscript());
}

/// The sample connection with the repository above.
class ListsController extends CaptureController {
  ListsController(super.store, this.operations) {
    repository = operations;
  }

  final ProductRepository operations;

  /// The fixture repository, when the test supplies answers by hand.
  ListsRepository get lists => operations as ListsRepository;

  @override
  Future<ProductRepository?> prepareActionRepository() async => operations;

  @override
  Future<ServerGateway?> prepareActionTransport() async => api;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    this.directory = directory;
    this.workspace = workspace;
    notifyListeners();
  }

  @override
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => selectLocation(directory: directory, workspace: workspace);
}

Future<ListsController> listsController({
  Iterable<Session> sessions = const [],
  Set<String> busy = const {},
  ProductRepository? repository,
  ServerGateway? api,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
      ),
    ],
  );
  final operations = repository ?? ListsRepository();
  final transport = api ?? ListsApi();
  if (transport is ListsApi) {
    transport.sessionsById.clear();
    for (final session in sessions) {
      transport.sessionsById[session.id] = session;
    }
  }
  final controller = ListsController(store, operations)
    ..api = transport
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = {for (final s in sessions) s.id: s}
    ..busySessions = Set.of(busy);
  return controller;
}

/// Lets real sockets answer, then paints.
Future<void> settle(WidgetTester tester, {int rounds = 6}) async {
  for (var i = 0; i < rounds; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 30)),
    );
    await tester.pump(const Duration(milliseconds: 120));
  }
}

/// The app around [home] on [controller], with the real chats host.
Widget listsApp(
  ListsController controller,
  Widget home, {
  GlobalKey? boundary,
  GlobalKey<NavigatorState>? navigatorKey,
  Map<String, WidgetBuilder> routes = const {},
}) => RepaintBoundary(
  key: boundary,
  child: ProviderScope(
    overrides: [
      bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
      connProvider.overrideWithValue(controller),
      chatsHostProvider.overrideWithValue(ConnectionChatsHost(controller)),
    ],
    child: MaterialApp(
      navigatorKey: navigatorKey,
      debugShowCheckedModeBanner: false,
      theme: captureTheme(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routes: routes,
      home: Material(child: home),
    ),
  ),
);

/// [body] with every `time` map moved so the newest moment in it is an hour
/// ago (lists read "1h ago", not a date), keeping the distances between them.
Object? shiftTimes(Object? body, {Duration newest = const Duration(hours: 1)}) {
  int? latest(Object? node) {
    int? best;
    void walk(Object? value, bool inTime) {
      if (value is Map) {
        for (final entry in value.entries) {
          final isTime = entry.key == 'time' && entry.value is Map;
          if (inTime && entry.value is num) {
            final at = (entry.value as num).toInt();
            if (best == null || at > best!) best = at;
          }
          walk(entry.value, isTime);
        }
      } else if (value is List) {
        for (final item in value) {
          walk(item, false);
        }
      }
    }

    walk(node, false);
    return best;
  }

  final newestAt = latest(body);
  if (newestAt == null) return body;
  final shift =
      DateTime.now().millisecondsSinceEpoch - newest.inMilliseconds - newestAt;
  Object? move(Object? value, bool inTime) {
    if (value is Map) {
      return {
        for (final entry in value.entries)
          entry.key: inTime && entry.value is num
              ? (entry.value as num).toInt() + shift
              : move(entry.value, entry.key == 'time' && entry.value is Map),
      };
    }
    if (value is List) return [for (final item in value) move(item, false)];
    return value;
  }

  return move(body, false);
}

/// Draws the list screens one after another over [controller] and keeps the
/// text a person could read on each (the data gate checks it, the picture of
/// the last one goes to the contact sheet).
class ListsScreens {
  ListsScreens(this.tester, this.controller, this.boundary, this.name);

  /// Names the pictures: build/coverage/`lists-NAME-N.png`.
  final String name;
  final WidgetTester tester;
  final ListsController controller;
  final GlobalKey boundary;
  final seen = <String>[];

  String get text => seen.join('\n');

  Future<void> show(Widget screen, {Future<void> Function()? then}) async {
    await tester.pumpWidget(listsApp(controller, screen, boundary: boundary));
    await settle(tester);
    if (then != null) {
      await then();
      await settle(tester);
    }
    seen.add(screenText(tester).join('\n'));
    await writeCasePng(tester, boundary, 'lists-$name-${seen.length}');
  }

  /// Home, as the app fills it: the feed reads the finder, the projects and
  /// the statuses through the connection.
  Future<void> home({Future<void> Function()? then}) async {
    await tester.runAsync(controller.refreshChatFeed);
    await show(const ChatsHomeScreen(), then: then);
  }

  Future<void> projectSheet() async {
    await tester.tap(find.byKey(const ValueKey('chats-filter-project')));
  }

  Future<void> finder({bool archived = false}) =>
      show(GlobalSessionsScreen(controller: controller, archived: archived));

  /// Everything that lists or describes one conversation.
  Future<void> conversation(Session detail, {bool archived = false}) async {
    await home();
    await finder(archived: archived);
    await show(
      SessionRelationsScreen(controller: controller, sessionID: detail.id),
    );
    await show(
      SessionContextScreen(
        controller: controller,
        sessionID: detail.id,
        initialMessages: const [],
        initialHasOlder: false,
      ),
      then: () async {
        final more = find.text('Details');
        if (more.evaluate().isNotEmpty) await tester.tap(more.first);
      },
    );
    await show(
      Scaffold(
        body: SingleChildScrollView(
          child: RunningWorkSheet(
            controller: controller,
            sessionID: detail.parentID ?? detail.id,
          ),
        ),
      ),
    );
  }

  /// Only the Context page of one conversation, its details open.
  Future<void> contextOnly(Session session) => show(
    SessionContextScreen(
      controller: controller,
      sessionID: session.id,
      initialMessages: const [],
      initialHasOlder: false,
    ),
    then: () async {
      final more = find.text('Details');
      if (more.evaluate().isNotEmpty) await tester.tap(more.first);
    },
  );

  Future<void> projects() =>
      show(ProjectsScreen(controller: controller, selectedProjectID: null));

  /// The sheet that moves a conversation to another folder of its project.
  Future<void> destination(String sessionID) => show(
    Builder(
      builder: (context) => TextButton(
        onPressed: () => showSessionDestinationSheet(
          context,
          controller: controller,
          sessionID: sessionID,
          mode: SessionDestinationMode.move,
        ),
        child: const SizedBox(width: 8, height: 8),
      ),
    ),
    then: () async {
      await tester.tap(find.byType(TextButton));
    },
  );
}

final _iso = RegExp(r'^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?Z$');

/// [body] with every ISO time string moved so the newest is an hour ago.
Object? shiftIsoTimes(
  Object? body, {
  Duration newest = const Duration(hours: 1),
}) {
  DateTime? latest;
  void scan(Object? value) {
    if (value is String && _iso.hasMatch(value)) {
      final at = DateTime.parse(value);
      if (latest == null || at.isAfter(latest!)) latest = at;
    } else if (value is Map) {
      value.values.forEach(scan);
    } else if (value is List) {
      value.forEach(scan);
    }
  }

  scan(body);
  if (latest == null) return body;
  final shift = DateTime.now().toUtc().subtract(newest).difference(latest!);
  Object? move(Object? value) {
    if (value is String && _iso.hasMatch(value)) {
      return DateTime.parse(value).add(shift).toIso8601String();
    }
    if (value is Map) {
      return {for (final e in value.entries) e.key: move(e.value)};
    }
    if (value is List) return [for (final item in value) move(item)];
    return value;
  }

  return move(body);
}
