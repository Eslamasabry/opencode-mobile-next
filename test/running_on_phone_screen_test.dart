import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_process_watch.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/running_on_phone_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/running_on_phone_text.dart';

import 'revamp/screen_phone_1_fixtures.dart';
import 'support/running_on_phone_fakes.dart';

void main() {
  late FakeClock clock;

  setUp(() => clock = FakeClock());

  FakePhoneProcesses fake(List<PhoneProcess> processes) =>
      FakePhoneProcesses(PhoneProcessSnapshot(processes: processes));

  Finder inGroup(String id, Finder finder) => find.descendant(
    of: find.byKey(ValueKey('running-panel-$id')),
    matching: finder,
  );

  double startInset(WidgetTester tester, int pid) {
    final row = tester.widget<KitRow>(find.byKey(Key('running-proc-$pid')));
    return (row.padding! as EdgeInsetsDirectional).start;
  }

  testWidgets('groups processes under what started them, parents first', (
    tester,
  ) async {
    final processes = fake(hotProcesses());
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));

    // Each group is named by what started it; a conversation by its title.
    expect(
      tester
          .widgetList<KitRowGroup>(find.byType(KitRowGroup))
          .map((g) => g.label)
          .toList(),
      [
        'Left behind',
        'OpenCode',
        'Claude Code · Fix the login screen',
        'Agent helper',
        'Terminal',
        'This app',
      ],
    );
    expect(
      inGroup(
        'claudeCode:$chatSession',
        find.byKey(const Key('running-proc-10240')),
      ),
      findsOneWidget,
    );
    expect(
      inGroup('leftBehind', find.byKey(const Key('running-proc-15003'))),
      findsOneWidget,
    );
    // Plain names; the command line only under Details.
    expect(
      tester
          .widget<Text>(find.byKey(const Key('running-proc-title-10240')))
          .data,
      'Flutter test',
    );
    expect(
      tester
          .widget<Text>(find.byKey(const Key('running-proc-title-10230')))
          .data,
      'Command',
    );
    expect(find.textContaining('enable-checked-mode'), findsNothing);
    // Parent → child is the indentation.
    expect(startInset(tester, 10230), greaterThan(startInset(tester, 10220)));
    expect(startInset(tester, 10240), greaterThan(startInset(tester, 10230)));
    expect(rowLine(tester, 10240), contains('MB'));
    expect(rowLine(tester, 10240), contains('running 4 min'));
    // A left-behind process says which conversation started it.
    expect(
      find.text('Started by Claude Code · Run the widget tests'),
      findsOneWidget,
    );
    // Server roots get no Stop: the phone refuses them.
    expect(find.byKey(const Key('running-proc-stop-10100')), findsNothing);
    expect(find.byKey(const Key('running-proc-stop-10200')), findsNothing);
    await unmountRunning(tester);
  });

  // a11y pass 2026-10-30 (emulator, TalkBack order): each group's figures
  // ("Idle · 0% · 356 MB") were read in one blob with the page summary at
  // the top, away from the group names they belong to.
  testWidgets("a group's figures are read with the group, not the summary", (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await pumpRunning(
      tester,
      PhoneProcessWatch(processes: fake(hotProcesses())),
    );
    final summary = tester.getSemantics(
      find.byKey(const Key('running-summary')),
    );
    expect(summary.label, isNot(contains('%')));
    handle.dispose();
    await unmountRunning(tester);
  });

  testWidgets('an unknown conversation is just "Claude Code"', (tester) async {
    final processes = fake([
      for (final p in normalProcesses())
        p.session == null
            ? p
            : proc(
                p.pid,
                p.ppid,
                p.name,
                owner: p.owner,
                session: 'not-a-known-one',
              ),
    ]);
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    expect(find.text('Claude Code'), findsWidgets);
    expect(find.textContaining('Fix the login screen'), findsNothing);
    await unmountRunning(tester);
  });

  testWidgets('heavy needs readings two minutes apart, not one', (
    tester,
  ) async {
    final processes = fake(normalProcesses(testerCpu: 96));
    final watch = PhoneProcessWatch(
      processes: processes,
      clock: () => clock.now,
    );
    await pumpRunning(tester, watch);
    expect(rowLine(tester, 10240), isNot(contains('Very busy')));
    expect(find.byKey(const Key('running-proc-stop-10240')), findsNothing);

    // The page reads every 5 seconds while open.
    for (var i = 0; i < 23; i++) {
      clock.advance(const Duration(seconds: 5));
      await tester.pump(const Duration(seconds: 5));
    }
    expect(rowLine(tester, 10240), isNot(contains('Very busy')));
    for (var i = 0; i < 2; i++) {
      clock.advance(const Duration(seconds: 5));
      await tester.pump(const Duration(seconds: 5));
    }
    expect(rowLine(tester, 10240), startsWith('Busy · 96%'));
    // Heavy puts its Stop in reach.
    expect(find.byKey(const Key('running-proc-stop-10240')), findsOneWidget);
    expect(processes.lists, greaterThanOrEqualTo(25));
    await unmountRunning(tester);
    final before = processes.lists;
    await tester.pump(const Duration(seconds: 30));
    expect(processes.lists, before, reason: 'no reading once the page closed');
  });

  testWidgets('Stop sends exactly the pid it names', (tester) async {
    final processes = fake(hotProcesses());
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    final stop = find.byKey(const Key('running-proc-stop-15003'));
    await tester.ensureVisible(stop);
    await tester.pump();
    await tester.tap(stop);
    await tester.pumpAndSettle();
    expect(find.text('Stop Flutter test?'), findsOneWidget);
    await tester.tap(find.byKey(const Key('running-confirm-stop')));
    await tester.pumpAndSettle();
    expect(processes.stops, [
      [15003],
    ]);
    expect(find.text('Stopped 1'), findsOneWidget);
    await unmountRunning(tester);
  });

  testWidgets('Stop all left behind sends exactly those, once', (tester) async {
    final processes = fake(hotProcesses());
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    final all = find.byKey(const Key('running-stop-left-behind'));
    await tester.ensureVisible(all);
    await tester.pump();
    expect(find.text('Stop all 6 left behind'), findsOneWidget);
    await tester.tap(all);
    await tester.pumpAndSettle();
    expect(find.text('Stop everything left behind?'), findsOneWidget);
    await tester.tap(find.byKey(const Key('running-confirm-stop')));
    await tester.pumpAndSettle();
    expect(processes.stops.single, [15001, 15002, 15003, 15004, 15005, 15006]);
    await unmountRunning(tester);
  });

  testWidgets('what did not stop is said plainly', (tester) async {
    final processes = fake(hotProcesses())
      ..onStop = (pids) => const PhoneProcessStopResult(
        stopped: [15001, 15002, 15004, 15005],
        refused: {
          15003: PhoneProcessStopRefusal.stillRunning,
          15006: PhoneProcessStopRefusal.notFound,
        },
      );
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    final all = find.byKey(const Key('running-stop-left-behind'));
    await tester.ensureVisible(all);
    await tester.pump();
    await tester.tap(all);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('running-confirm-stop')));
    await tester.pumpAndSettle();
    expect(find.text('Not everything stopped'), findsOneWidget);
    expect(find.text('Stopped 4 · 1 had already ended'), findsOneWidget);
    expect(
      find.text(
        'Flutter test: it kept running after a forced stop. Try again, or '
        'restart the phone.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('stillRunning'), findsNothing);
    expect(find.textContaining('notFound'), findsNothing);
    await unmountRunning(tester);
  });

  testWidgets('a stop that fails says so in words', (tester) async {
    final processes = fake(hotProcesses())
      ..onStop = (_) => throw PlatformException(
        code: 'E',
        message: 'java.lang.IllegalStateException: proc gone',
      );
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    final stop = find.byKey(const Key('running-proc-stop-15004'));
    await tester.ensureVisible(stop);
    await tester.pump();
    await tester.tap(stop);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('running-confirm-stop')));
    await tester.pumpAndSettle();
    expect(find.text("Couldn't stop it. Try again."), findsOneWidget);
    expect(find.textContaining('IllegalStateException'), findsNothing);
    await unmountRunning(tester);
  });

  testWidgets('warns from 28 processes that Android may stop the servers', (
    tester,
  ) async {
    await pumpRunning(
      tester,
      PhoneProcessWatch(processes: fake(nearLimitProcesses())),
    );
    expect(find.byKey(const ValueKey('running-near-limit')), findsOneWidget);
    expect(find.text('Servers may be stopped'), findsOneWidget);
    expect(
      find.textContaining('Developer options › Disable child process'),
      findsOneWidget,
    );
    await unmountRunning(tester);

    await pumpRunning(
      tester,
      PhoneProcessWatch(processes: fake(nearLimitProcesses(workers: 16))),
    );
    expect(find.byKey(const ValueKey('running-near-limit')), findsNothing);
    await unmountRunning(tester);
  });

  testWidgets('a list that cannot be read says so, with Try again', (
    tester,
  ) async {
    final processes = fake(normalProcesses())
      ..listError = PlatformException(code: 'E', message: 'binder died');
    await pumpRunning(tester, PhoneProcessWatch(processes: processes));
    expect(find.byKey(const ValueKey('running-load-error')), findsOneWidget);
    expect(find.text("Couldn't read what's running"), findsOneWidget);
    expect(find.textContaining('binder'), findsNothing);
    await unmountRunning(tester);
  });

  testWidgets("This phone's row says what was left behind and opens the page", (
    tester,
  ) async {
    useNoTermuxJob();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    final processes = fake(hotProcesses());
    final watch = PhoneProcessWatch(processes: processes);
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
      linux: PhoneLinux(running: true),
      profiles: [inAppProfile],
      overrides: [phoneProcessWatchProvider.overrideWithValue(watch)],
    );
    final row = find.byKey(const ValueKey('this-phone-running'));
    await tester.scrollUntilVisible(
      row,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    final status = tester.widget<Text>(
      find.byKey(const Key('running-row-status')),
    );
    expect(
      status.textSpan!.toPlainText(),
      '6 left behind, still using the phone',
    );
    await tester.tap(row);
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.byType(RunningOnPhoneScreen), findsOneWidget);
    await unmountPhone(tester);
  });

  for (final locale in const [Locale('en'), Locale('ar')]) {
    for (final light in const [false, true]) {
      testWidgets('fits 320 dp at 2.0x text (${locale.languageCode}, '
          '${light ? 'light' : 'dark'})', (tester) async {
        final processes = fake(hotProcesses());
        await pumpRunning(
          tester,
          PhoneProcessWatch(processes: processes),
          light: light,
          size: const Size(320, 800),
          textScale: 2,
          locale: locale,
        );
        final scroll = find.byType(Scrollable).first;
        await tester.dragUntilVisible(
          find.byKey(const Key('running-stop-left-behind')),
          scroll,
          const Offset(0, -200),
        );
        expect(tester.takeException(), isNull);
        // The heading carries the one warning; rows under it carry none.
        expect(
          find.descendant(
            of: find.byKey(const Key('running-proc-10100')),
            matching: find.byType(KitIcon),
          ),
          findsNothing,
        );
        await unmountRunning(tester);
      });
    }
  }

  test('rows use plain names for the kernel names seen on a phone', () {
    // Read from a device: Node's kernel name is its main thread's, the
    // server binary is "opencode2", Paseo renames its processes.
    String? kind(String name, String command) =>
        runningNameKind(proc(1, 0, name, command: command));
    expect(
      kind(
        'MainThread',
        '/opt/node/bin/node /root/.oc-genui/openCode2/server.cjs',
      ),
      'openCode',
    );
    expect(
      kind('MainThread', 'node /home/oc/.local/bin/paseo daemon run'),
      'agentHelper',
    );
    expect(kind('opencode2', 'opencode2 serve --port 4097'), 'openCode');
    expect(kind('Paseo Superviso', 'Paseo Supervisor'), 'agentHelper');
    expect(kind('Paseo Daemon', 'Paseo Daemon'), 'agentHelper');
    expect(kind('libproot.so', 'libproot.so --root-id'), 'linux');
  });
}
