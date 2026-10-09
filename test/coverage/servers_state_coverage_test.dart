// Coverage ratchet for what the app itself knows about a server and shows
// beside the server's own answers (see paseo_coverage_support.dart for the
// rules): the connection status line, and what the monitor learned about the
// servers that are not open. The monitor's snapshot is built by the app's
// real ProfileMonitor from what a fake server answers; the status by the
// real banner; both are drawn by the real Servers screen, Inbox and status
// line.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/domain/form_request.dart' show Api2FormInfo;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/notification_preferences.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

/// A connection that reports a chosen status snapshot.
class _StatusConnection extends ServersConnection {
  _StatusConnection(super.store, this.snapshot);

  final ConnectionStatusSnapshot snapshot;

  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;
}

/// What a server answers the monitor with: sessions with titles, waiting
/// requests and the busy sessions.
class _MonitoredServer implements ServerGateway, ServerOperationsGateway {
  _MonitoredServer(this.record, {this.fails = false});

  final Map<String, dynamic> record;
  final bool fails;

  List<Map> get _requests =>
      ((record['requests'] as List?) ?? const []).cast<Map>();

  @override
  bool isClosed = false;
  @override
  String? directory;
  @override
  String? workspace;

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(forms: true);

  @override
  void setLocation({String? directory, String? workspace}) {
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  void close() => isClosed = true;

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    if (fails) throw StateError('unreachable');
    final sessions = <String, Session>{
      for (final r in _requests)
        r['sessionID'] as String: Session(
          id: r['sessionID'] as String,
          title: r['title'] as String?,
          directory: r['directory'] as String?,
          workspaceID: r['workspace'] as String?,
        ),
      for (final i
          in ((record['busyIntervals'] as List?) ?? const []).cast<Map>())
        i['sessionID'] as String: Session(
          id: i['sessionID'] as String,
          title: i['title'] as String?,
          directory: i['directory'] as String?,
          workspaceID: i['workspace'] as String?,
        ),
    };
    return ServerPage(items: sessions.values.toList());
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [
    for (final r in _requests)
      if (r['kind'] == 'permission')
        PermissionRequest(
          id: r['id'] as String,
          sessionID: r['sessionID'] as String,
          permission: 'edit',
        ),
  ];

  @override
  Future<List<PendingQuestion>> listQuestions() async => [
    for (final r in _requests)
      if (r['kind'] == 'question')
        PendingQuestion.fromJson({
          'id': r['id'],
          'sessionID': r['sessionID'],
          'questions': [
            {
              'question': 'Which one?',
              'header': 'Choice',
              'options': [
                {'label': 'First', 'description': 'The first'},
              ],
            },
          ],
        }),
  ];

  @override
  Future<List<Api2FormInfo>> pendingForms() async => [
    for (final r in _requests)
      if (r['kind'] == 'form')
        Api2FormInfo.fromJson({
          'id': r['id'],
          'sessionID': r['sessionID'],
          'title': r['title'],
          'metadata': <String, dynamic>{},
          'fields': <Object>[],
        })!,
  ];

  @override
  Future<Map<String, String>> sessionStatuses() async => {
    for (final r in _requests) r['sessionID'] as String: 'busy',
    for (final i
        in ((record['busyIntervals'] as List?) ?? const []).cast<Map>())
      i['sessionID'] as String: 'busy',
    for (
      var n = 0;
      n < ((record['runningCount'] as int?) ?? 0) - _requests.length;
      n++
    )
      'ses_other_$n': 'busy',
  };

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        ),
  );

  final family = CoverageFamily('servers_state', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases.where((c) => c['kind'] == 'connection')) {
    final id = variant['id'] as String;
    testWidgets('connection status · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final value = Map<String, dynamic>.from(variant['payload'] as Map);
      final snapshot = ConnectionStatusSnapshot(
        phase: ConnectionStatusPhase.values.byName(value['phase'] as String),
        profileId: value['profileId'] as String,
        serverName: value['serverName'] as String,
        since: DateTime.parse(value['since'] as String),
        usesToken: value['usesToken'] as bool,
        retrying: value['retrying'] as bool,
        quiet: value['quiet'] as bool,
        attemptRevision: value['attemptRevision'] as int,
      );
      final (store, base) = await serversState();
      base.dispose();
      final controller = _StatusConnection(store, snapshot);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary,
          controller: controller,
          store: store,
          home: Builder(
            builder: (context) => Material(
              child: AppConditionsScope(
                conditions: [connectionKitStatus(context, controller)],
                child: const KitScreen(body: SizedBox.expand()),
              ),
            ),
          ),
        ),
      );
      await frames(tester, 6);
      await writeCasePng(tester, boundary, 'srvstate_$id');
      final problems = checkCase(
        family,
        variant,
        screenText(tester).join('\n'),
      );
      // A status that is retrying offers no second Reconnect; a hidden or
      // quiet one draws no line at all.
      final retry = find.byKey(const ValueKey('connection-banner-retry'));
      final line = find.byType(KitStatusLine);
      if (value['quiet'] == true ||
          value['phase'] == 'connected' ||
          value['phase'] == 'hidden') {
        if (line.evaluate().isNotEmpty) {
          problems.add('a quiet or connected status drew a line');
        }
      }
      if (value['retrying'] == true && retry.evaluate().isNotEmpty) {
        problems.add('retrying still offers Reconnect');
      }
      expect(problems, isEmpty);
    });
  }

  for (final variant in family.cases.where((c) => c['kind'] == 'attention')) {
    final id = variant['id'] as String;
    testWidgets('what the monitor learned · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final record = Map<String, dynamic>.from(variant['payload'] as Map);
      final other = record['profileID'] as String;
      final due = ((record['busyIntervals'] as List?) ?? const []).isNotEmpty;
      final unavailable = record['status'] == 'unavailable';
      final waiting = record['status'] == 'waiting';
      final (store, unused) = await serversState(
        profiles: [
          ServerProfile(
            id: 'srv_open',
            name: 'Open server',
            baseUrl: 'https://open.example.net:4096',
          ),
          ServerProfile(
            id: other,
            name: 'Lab OpenCode',
            baseUrl: 'https://lab.example.net:4097',
          ),
        ],
      );
      unused.dispose();
      store.selectFirst = false;
      await store.prefs.setString(
        ProfileMonitor.rulesKey(other),
        jsonEncode({'enabled': true}),
      );
      // Check-ins are one setting for every server.
      await store.prefs.setInt(NotificationPreferences.versionKey, 1);
      await store.prefs.setInt(NotificationPreferences.checkInKey, 30);
      if (due) {
        final now = DateTime.now();
        final session = (record['busyIntervals'] as List).first as Map;
        await store.prefs.setString(
          ProfileMonitor.busyIntervalsKey(other),
          jsonEncode([
            ObservedBusyInterval(
              sessionID: session['sessionID'] as String,
              firstObservedBusyAt: now.subtract(const Duration(minutes: 41)),
              lastObservedBusyAt: now.subtract(const Duration(minutes: 1)),
              title: session['title'] as String?,
              directory: session['directory'] as String?,
              workspace: session['workspace'] as String?,
            ).toJson(),
          ]),
        );
      }
      final server = _MonitoredServer(record, fails: unavailable);
      final controller = ServersConnection(
        store,
        monitorGatewayFactory: (profile) =>
            (gateway: server, operations: server),
      );
      if (!waiting) {
        await controller.profileMonitor.refresh();
        await frames(tester, 2);
      }
      final snapshot = controller.profileMonitor.snapshotFor(other);
      final boundary = GlobalKey();
      await tester.pumpWidget(serversApp(boundary, store, controller));
      await frames(tester, 8);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'srvstate_$id');
      await tester.pumpWidget(
        serversApp(
          boundary,
          store,
          controller,
          home: Material(
            child: KitScreen(
              body: ListView(
                children: [ProfileMonitorInbox(controller: controller)],
              ),
            ),
          ),
        ),
      );
      await frames(tester, 8);
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary, 'srvstate_${id}_inbox');
      final problems = checkCase(family, variant, seen.join('\n'));
      if (waiting && snapshot.isCurrent) {
        problems.add('expected a monitor that has not checked yet');
      }
      if (unavailable && snapshot.status != ProfileMonitorStatus.unavailable) {
        problems.add('expected a monitor that could not check');
      }
      if ((waiting || unavailable) && seen.join().contains('Needs you')) {
        problems.add('a check that is not current still spoke');
      }
      // The monitor's own timer must end with the test.
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      expect(
        problems,
        isEmpty,
        reason: 'screen text:\n${flat(seen.join('\n'))}',
      );
    });
  }
}
