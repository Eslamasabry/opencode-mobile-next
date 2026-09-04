import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/running_work_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/managed_shell_fakes.dart';

Future<ConnectionController> connection(FakeManagedShellRepository repo) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'p1',
        'name': 'Test',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'p1',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return ConnectionController(store)
    ..repository = repo
    ..status = StreamStatus.connected
    ..sessionsById = {'ses_a': Session(id: 'ses_a', title: 'Main task')};
}

Future<void> pump(
  WidgetTester tester,
  Widget screen, {
  double scale = 1,
}) async {
  await tester.binding.setSurfaceSize(const Size(360, 800));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          size: const Size(360, 800),
          textScaler: TextScaler.linear(scale),
        ),
        child: child!,
      ),
      home: screen,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        ),
  );

  testWidgets(
    'a reconnect covered by a dialog reconciles when the viewer becomes visible',
    (tester) async {
      final repo = FakeManagedShellRepository();
      final conn = await connection(repo);
      addTearDown(conn.dispose);
      await pump(
        tester,
        ShellOutputScreen(controller: conn, shell: repo.shells.first),
      );
      await tester.tap(find.widgetWithText(TextButton, 'Stop command'));
      await tester.pumpAndSettle();
      final coveredReads = repo.outputReads;
      repo.identity = 'server-2';
      repo.shells = [];
      conn.dataRefreshRevision++;
      conn.notifyListeners();
      await tester.pump(const Duration(seconds: 2));
      expect(repo.outputReads, coveredReads);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(find.textContaining('The server restarted'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'running work filters other sessions and opens a linked subagent',
    (tester) async {
      final repo = FakeManagedShellRepository()
        ..shells.add(
          sampleShell(
            id: 'sh_other',
            sessionID: 'ses_other',
            command: 'PRIVATE OTHER COMMAND',
          ),
        );
      final conn = await connection(repo)
        ..sessionsById['ses_child'] = Session(
          id: 'ses_child',
          title: 'Review navigation',
          parentID: 'ses_a',
        )
        ..busySessions = {'ses_child'};
      addTearDown(conn.dispose);
      String? selected;
      await pump(
        tester,
        Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async => selected = await showRunningWorkSheet(
                context,
                controller: conn,
                sessionID: 'ses_a',
                shellIDs: {},
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.text('PRIVATE OTHER COMMAND'), findsNothing);
      expect(find.byKey(const Key('work-shell-sh_a')), findsOneWidget);
      await tester.tap(find.byKey(const Key('work-agent-ses_child')));
      await tester.pumpAndSettle();
      expect(selected, 'ses_child');
    },
  );

  testWidgets(
    'unsupported shells disappear while subagent navigation stays available',
    (tester) async {
      final repo = FakeManagedShellRepository()..supported = false;
      final conn = await connection(repo)
        ..sessionsById['ses_child'] = Session(
          id: 'ses_child',
          title: 'Child',
          parentID: 'ses_a',
        )
        ..busySessions = {'ses_child'};
      addTearDown(conn.dispose);
      await pump(
        tester,
        Scaffold(
          body: RunningWorkSheet(controller: conn, sessionID: 'ses_a'),
        ),
      );
      expect(find.text('Commands'), findsNothing);
      expect(find.byKey(const Key('work-agent-ses_child')), findsOneWidget);
      final unsupportedReads = repo.listReads;
      await tester.pump(const Duration(seconds: 6));
      expect(repo.listReads, unsupportedReads);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('Stop requires confirmation and retains already loaded output', (
    tester,
  ) async {
    final repo = FakeManagedShellRepository();
    final conn = await connection(repo);
    addTearDown(conn.dispose);
    await pump(
      tester,
      ShellOutputScreen(controller: conn, shell: repo.shells.first),
    );
    await tester.tap(find.widgetWithText(TextButton, 'Stop command'));
    await tester.pumpAndSettle();
    expect(repo.stopCalls, 0);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(repo.stopCalls, 0);
    await tester.tap(find.widgetWithText(TextButton, 'Stop command'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Stop command'));
    await tester.pumpAndSettle();
    expect(repo.stopCalls, 1);
    expect(find.text(repo.output), findsOneWidget);
    expect(find.widgetWithText(TextButton, 'Stop command'), findsNothing);
    expect(find.text('Follow output'), findsNothing);
    expect(find.text('Stopped'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('timeout replacement and clearing use the selected duration', (
    tester,
  ) async {
    final repo = FakeManagedShellRepository();
    final conn = await connection(repo);
    addTearDown(conn.dispose);
    await pump(
      tester,
      ShellOutputScreen(controller: conn, shell: repo.shells.first),
    );
    await tester.tap(find.text('Change timeout'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('5 minutes'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change timeout'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('No timeout'));
    await tester.pumpAndSettle();
    expect(repo.timeouts, [const Duration(minutes: 5), null]);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'output polling pauses behind a dialog, in background and after close',
    (tester) async {
      final repo = FakeManagedShellRepository();
      final conn = await connection(repo);
      addTearDown(conn.dispose);
      await pump(
        tester,
        ShellOutputScreen(controller: conn, shell: repo.shells.first),
      );
      final initial = repo.outputReads;
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
      expect(repo.outputReads, greaterThan(initial));
      await tester.tap(find.widgetWithText(TextButton, 'Stop command'));
      await tester.pumpAndSettle();
      final covered = repo.outputReads;
      await tester.pump(const Duration(seconds: 6));
      expect(repo.outputReads, covered);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      final paused = repo.outputReads;
      await tester.pump(const Duration(seconds: 6));
      expect(repo.outputReads, paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(repo.outputReads, greaterThan(paused));
      await tester.pumpWidget(const SizedBox.shrink());
      final closed = repo.outputReads;
      await tester.pump(const Duration(seconds: 6));
      expect(repo.outputReads, closed);
    },
  );

  testWidgets(
    'reconnect refreshes status and scope change disables old-server controls',
    (tester) async {
      final repo = FakeManagedShellRepository();
      final conn = await connection(repo);
      addTearDown(conn.dispose);
      await pump(
        tester,
        ShellOutputScreen(controller: conn, shell: repo.shells.first),
      );
      conn.status = StreamStatus.reconnecting;
      conn.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.textContaining('Reconnecting.'), findsOneWidget);
      repo.identity = 'server-2';
      repo.shells = [];
      conn.status = StreamStatus.connected;
      conn.locationRevision++;
      conn.dataRefreshRevision++;
      conn.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.textContaining('The server restarted'), findsOneWidget);
      expect(find.text('Running'), findsNothing);
      conn.directory = '/other';
      conn.locationRevision++;
      conn.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.textContaining('workspace changed'), findsOneWidget);
      final refresh = tester.widget<IconButton>(
        find.byWidgetPredicate(
          (widget) => widget is IconButton && widget.tooltip == 'Refresh',
        ),
      );
      expect(refresh.onPressed, isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'running work and output remain scrollable at 360 dp with 2.5x text',
    (tester) async {
      final repo = FakeManagedShellRepository()
        ..shells = [
          sampleShell(
            command:
                'flutter test --concurrency=1 test/a_very_long_file_name_test.dart',
          ),
        ];
      final conn = await connection(repo);
      addTearDown(conn.dispose);
      await pump(
        tester,
        Scaffold(
          body: RunningWorkSheet(controller: conn, sessionID: 'ses_a'),
        ),
        scale: 2.5,
      );
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      await pump(
        tester,
        ShellOutputScreen(controller: conn, shell: repo.shells.first),
        scale: 2.5,
      );
      expect(tester.takeException(), isNull);
      await tester.drag(
        find.byKey(const Key('shell-output-content')),
        const Offset(0, 600),
      );
      await tester.pumpAndSettle();
      expect(find.text('Change timeout'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
