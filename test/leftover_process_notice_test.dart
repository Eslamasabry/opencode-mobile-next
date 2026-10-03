// The leftover-process notice (chats-first rehouse): its wording, dismissal
// and Stop; it lives on the Conversations tab.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxBridgeException;
import 'package:opencode_mobile/termux/processes.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/termux_phone_tools.dart';
import 'package:opencode_mobile/ui/widgets/work_status_line.dart';

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
  tearDown(TermuxRunawayWatcher.resetDismissedForTesting);

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
}
