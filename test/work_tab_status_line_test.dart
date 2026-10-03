// The Work tab's new parts (work-tab cleanup and design standard,
// 2026-09-24): the one status line and its priorities, the leftover-process
// line's wording and dismissal, the kit's loading bar, skeletons and button
// block. Old-code comparisons are in work_tab_cleanup_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxBridgeException;
import 'package:opencode_mobile/termux/processes.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/termux_phone_tools.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:opencode_mobile/ui/widgets/work_status_line.dart';

import 'support/work_tab_fixture.dart';

Widget _app(Widget home) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: home),
);

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

TermuxProcess _orphan({
  int pid = 4242,
  String name = 'node',
  String cwd = '/root/projects/',
  int cpuSeconds = 610,
}) => TermuxProcess(
  pid: pid,
  ppid: 1,
  group: TermuxProcessGroup.orphans,
  name: name,
  cmd: 'node $name',
  cpuPct: 12,
  cpuSeconds: cpuSeconds,
  rssKb: 1000,
  elapsedSeconds: 3600,
  cwd: cwd,
  orphanReason: TermuxOrphanReason.cpuNoOwner,
  protected: false,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() {
    TermuxRunawayWatcher.resetDismissedForTesting();
  });

  group('the leftover-process line (item 7)', () {
    test('names a project by its folder, and never shows a path', () {
      expect(runawayProjectName('/root/projects/'), isNull);
      expect(runawayProjectName('/root/projects'), isNull);
      expect(runawayProjectName('/root'), isNull);
      expect(runawayProjectName(''), isNull);
      expect(runawayProjectName('/root/projects/.cache/x'), isNull);
      expect(runawayProjectName('/root/projects/FinanceHub'), 'FinanceHub');
      expect(
        runawayProjectName('/root/projects/FinanceHub/node_modules/.bin'),
        'FinanceHub',
      );
      expect(
        runawayProjectName('/data/data/com.termux/files/home/projects/app'),
        'app',
      );
    });

    testWidgets('a leftover helper is named as one, not as OpenCode, and '
        'its dismissal lasts until the process changes', (tester) async {
      var report = TermuxProcessReport([_orphan()]);
      var scans = 0;
      await tester.pumpWidget(
        _app(
          TermuxRunawayWatcher(
            interval: const Duration(seconds: 60),
            scan: () async {
              scans++;
              return report;
            },
            builder: (context, notice) => Column(
              children: [
                if (notice != null)
                  KitStatusLine(
                    icon: Icons.memory,
                    message: notice
                        .status(lookupAppLocalizations(const Locale('en')))
                        .message,
                    onDismiss: notice.onDismiss,
                  ),
              ],
            ),
          ),
        ),
      );
      await _settle(tester);
      expect(
        find.text(
          'A leftover node process has been busy for 10 min with nothing to '
          'do',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('OpenCode'), findsNothing);
      expect(find.textContaining('/root'), findsNothing);
      expect(find.textContaining('CPU'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
      await _settle(tester);
      expect(find.byType(KitStatusLine), findsNothing);

      // The same process, scanned again: still dismissed.
      await tester.pump(const Duration(seconds: 61));
      await _settle(tester);
      expect(scans, greaterThan(1));
      expect(find.byType(KitStatusLine), findsNothing);

      // A different process: back, named by its project.
      report = TermuxProcessReport([
        _orphan(pid: 5151, cwd: '/root/projects/FinanceHub', cpuSeconds: 720),
      ]);
      await tester.pump(const Duration(seconds: 61));
      await _settle(tester);
      expect(
        find.text(
          'A leftover node process in FinanceHub has been busy for 12 min '
          'with nothing to do',
        ),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox());
    });

    test('OpenCode is named only when OpenCode itself is the one busy, and '
        'a path never shows', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      WorkRunawayNotice notice(String helper, {String? project}) =>
          WorkRunawayNotice(
            identity: helper,
            helper: helper,
            project: project,
            busyFor: '10 min',
            onStop: () {},
            onDismiss: () {},
          );
      expect(
        notice('opencode').status(l10n).message,
        'OpenCode has been busy for 10 min with nothing to do',
      );
      expect(
        notice('opencode', project: 'FinanceHub').status(l10n).message,
        'OpenCode has been busy in FinanceHub for 10 min with nothing to do',
      );
      expect(
        notice('/usr/bin/java').status(l10n).message,
        'A leftover java process has been busy for 10 min with nothing to do',
      );
    });

    test('the line offers See what\'s running when it can open the list', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      var opened = 0;
      final notice = WorkRunawayNotice(
        identity: 1,
        helper: 'node',
        busyFor: '10 min',
        onStop: () {},
        onDismiss: () {},
      );
      expect(notice.status(l10n).more, isEmpty);
      final more = notice.status(l10n, onSeeRunning: () => opened++).more;
      expect(more.map((action) => action.label), ["See what's running"]);
      more.single.onPressed!();
      expect(opened, 1);
    });

    // slice-R14: the line's one action stops the process it talks about.
    Widget stopLine({
      required TermuxProcessReport Function() report,
      required Future<TermuxProcessStopResult> Function(int pid) stop,
    }) => _app(
      TermuxRunawayWatcher(
        scan: () async => report(),
        stop: stop,
        builder: (context, notice) {
          if (notice == null) return const SizedBox.shrink();
          final status = notice.status(
            lookupAppLocalizations(const Locale('en')),
          );
          return KitStatusLine(
            icon: status.icon,
            tone: status.tone,
            message: status.message,
            action: status.action,
            onDismiss: status.onDismiss,
          );
        },
      ),
    );

    List<String> announcements(WidgetTester tester) {
      final said = <String>[];
      tester.binding.defaultBinaryMessenger
          .setMockDecodedMessageHandler<dynamic>(SystemChannels.accessibility, (
            message,
          ) async {
            final data = (message as Map)['data'] as Map?;
            if (message['type'] == 'announce') said.add('${data?['message']}');
            return null;
          });
      addTearDown(
        () => tester.binding.defaultBinaryMessenger
            .setMockDecodedMessageHandler<dynamic>(
              SystemChannels.accessibility,
              null,
            ),
      );
      return said;
    }

    testWidgets('Stop node asks first, stops exactly that process, hides the '
        'line and says so once', (tester) async {
      final said = announcements(tester);
      var report = TermuxProcessReport([_orphan(name: 'node')]);
      final stopped = <int>[];
      await tester.pumpWidget(
        stopLine(
          report: () => report,
          stop: (pid) async {
            stopped.add(pid);
            report = TermuxProcessReport(const []);
            return TermuxProcessStopResult(
              stopped: [pid],
              killed: const [],
              remaining: const [],
              refused: const [],
            );
          },
        ),
      );
      await _settle(tester);
      final stop = find.byKey(const ValueKey('work-status-runaway-stop'));
      expect(
        find.descendant(of: stop, matching: find.text('Stop node')),
        findsOneWidget,
      );
      expect(find.text("See what's running"), findsNothing);

      // Keep: nothing stops, the line stays.
      await tester.tap(stop);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('work-runaway-stop-confirm')),
        findsOneWidget,
      );
      expect(find.text('Stop node?'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
      await tester.pumpAndSettle();
      expect(stopped, isEmpty);
      expect(find.byType(KitStatusLine), findsOneWidget);

      await tester.tap(stop);
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('work-runaway-stop-confirm-stop')),
      );
      await tester.pumpAndSettle();
      expect(stopped, [4242]);
      expect(find.byType(KitStatusLine), findsNothing);
      expect(said, ['Stopped node']);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('a stop that did not end it keeps the line in plain failure '
        'words, with the same Stop to try again', (tester) async {
      final said = announcements(tester);
      await tester.pumpWidget(
        stopLine(
          report: () => TermuxProcessReport([_orphan(name: 'node')]),
          stop: (pid) async => throw const TermuxBridgeException(
            'procs-stop: kill(4242) EPERM',
            code: 'tool_failed',
          ),
        ),
      );
      await _settle(tester);
      await tester.tap(find.byKey(const ValueKey('work-status-runaway-stop')));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('work-runaway-stop-confirm-stop')),
      );
      await tester.pumpAndSettle();
      expect(
        find.text("Couldn't stop node. Try again, or stop it from Termux."),
        findsOneWidget,
      );
      expect(find.textContaining('EPERM'), findsNothing);
      expect(
        find.byKey(const ValueKey('work-status-runaway-stop')),
        findsOneWidget,
      );
      expect(said, ["Couldn't stop node. Try again, or stop it from Termux."]);
      await tester.pumpWidget(const SizedBox());
    });
  });

  group('one status line, most urgent first', () {
    Future<WorkController> pumpWork(
      WidgetTester tester, {
      StreamStatus status = StreamStatus.connected,
      String? error,
      bool phone = true,
      Future<void> Function()? restart,
    }) async {
      final controller =
          await workController(
              status: status,
              sessions: workLoadedSessions(),
              name: phone ? 'This device (Termux)' : 'Laptop',
              baseUrl: phone
                  ? 'http://127.0.0.1:4096'
                  : 'http://100.64.0.7:4096',
            )
            ..lastError = error;
      controller.notifyListeners();
      await tester.pumpWidget(
        _app(
          ListenableBuilder(
            listenable: controller,
            builder: (context, _) => AppConditionsScope(
              conditions: [
                connectionKitStatus(
                  context,
                  controller,
                  serverOnThisPhone: phone,
                  onRestartServer: restart,
                ),
              ],
              child: KitScreen(body: const SizedBox.expand()),
            ),
          ),
        ),
      );
      await _settle(tester);
      return controller;
    }

    Finder line(String id) => find.byKey(
      ValueKey(id == 'server' ? 'connection-status-banner' : 'work-status-$id'),
    );

    testWidgets('a normal start says connecting in the shared slot', (
      tester,
    ) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.connecting,
      );
      try {
        await tester.pump(const Duration(seconds: 7));
        expect(line('server'), findsOneWidget);
        expect(find.textContaining('Connecting to '), findsOneWidget);
        controller
          ..status = StreamStatus.connected
          ..notifyListeners();
        await tester.pump(const Duration(seconds: 5));
        await _settle(tester);
        expect(find.byType(KitStatusLine), findsNothing);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('a failed attempt says so at once', (tester) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.disconnected,
        error: 'Cannot reach http://127.0.0.1:4096: timed out',
      );
      try {
        expect(line('server'), findsOneWidget);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('Restart asks first, and only then restarts', (tester) async {
      var restarts = 0;
      final controller = await pumpWork(
        tester,
        status: StreamStatus.reconnecting,
        restart: () async => restarts++,
      );
      try {
        await tester.pump(const Duration(seconds: 9));
        await _settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('connection-banner-restart')),
        );
        await _settle(tester);
        expect(find.text('Restart OpenCode on this phone?'), findsOneWidget);
        await tester.tap(find.text('Cancel'));
        await _settle(tester);
        expect(restarts, 0);
        await tester.tap(
          find.byKey(const ValueKey('connection-banner-restart')),
        );
        await _settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('work-server-restart-confirm')),
        );
        await _settle(tester);
        expect(restarts, 1);
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });

    testWidgets('a remote server is named, with Try again and Details and '
        'no Restart', (tester) async {
      final controller = await pumpWork(
        tester,
        status: StreamStatus.reconnecting,
        phone: false,
      );
      try {
        await tester.pump(const Duration(seconds: 9));
        await _settle(tester);
        expect(find.text("Laptop isn't answering"), findsOneWidget);
        expect(
          find.byKey(const ValueKey('connection-banner-retry')),
          findsOneWidget,
        );
        expect(
          find.byKey(const ValueKey('connection-banner-restart')),
          findsNothing,
        );
        await tester.tap(find.byKey(const ValueKey('kit-status-more')));
        await _settle(tester);
        expect(
          find.byKey(const ValueKey('connection-banner-details')),
          findsOneWidget,
        );
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      }
    });
  });

  group('kit', () {
    testWidgets('the loading bar is labelled; skeleton rows are not read', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          const Column(
            children: [
              KitLoadingBar(loading: true, label: 'Loading'),
              KitSkeletonRows(),
            ],
          ),
        ),
      );
      expect(find.bySemanticsLabel('Loading'), findsOneWidget);
      expect(
        find.ancestor(
          of: find.byKey(const ValueKey('kit-skeleton-rows')),
          matching: find.byType(ExcludeSemantics),
        ),
        findsWidgets,
      );
      semantics.dispose();
    });

    testWidgets('the loading bar keeps its 2 dp when idle', (tester) async {
      await tester.pumpWidget(
        _app(const KitLoadingBar(loading: false, label: 'Loading')),
      );
      expect(tester.getSize(find.byType(KitLoadingBar)).height, 2);
      expect(find.byType(LinearProgressIndicator), findsNothing);
    });

    for (final (width, stacked) in [(412.0, true), (800.0, false)]) {
      testWidgets('actions at ${width.toInt()} dp: '
          '${stacked ? 'stacked, primary first' : 'one row, primary last'}', (
        tester,
      ) async {
        tester.view.physicalSize = Size(width, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          _app(
            KitActionBlock(
              primary: KitAction(label: 'Start', onPressed: () {}),
              secondary: KitAction(label: 'Try again', onPressed: () {}),
              tertiary: [
                KitAction(label: 'Change server', onPressed: () {}),
                KitAction(label: 'Setup', onPressed: () {}),
                KitAction(label: 'Help', onPressed: () {}),
              ],
            ),
          ),
        );
        final start = tester.getRect(find.text('Start'));
        final retry = tester.getRect(find.text('Try again'));
        final change = tester.getRect(find.text('Change server'));
        // The third tertiary action goes behind More.
        expect(find.text('Help'), findsNothing);
        expect(find.byKey(const ValueKey('kit-actions-more')), findsOneWidget);
        if (stacked) {
          expect(start.top, lessThan(retry.top));
          expect(retry.top, lessThan(change.top));
          expect(
            tester.getSize(find.widgetWithText(FilledButton, 'Start')).width,
            width,
          );
        } else {
          expect(start.left, greaterThan(retry.left));
          expect(retry.left, greaterThan(change.left));
        }
      });
    }
  });
}
