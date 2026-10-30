// Fixtures for Running on this phone: a stood-in native process list
// (PhoneProcesses) and the scenes the tests, goldens and owner samples use.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_process_watch.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/running_on_phone_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;

/// The native list, stood in for: [snapshot] is what `processes` returns,
/// and every `stopProcesses` call is recorded with its exact pids.
class FakePhoneProcesses extends PhoneProcesses {
  FakePhoneProcesses(this.snapshot);

  PhoneProcessSnapshot snapshot;
  Object? listError;
  int lists = 0;
  final stops = <List<int>>[];

  /// What a stop answers; by default every pid stopped.
  PhoneProcessStopResult Function(List<int> pids)? onStop;

  @override
  Future<PhoneProcessSnapshot> list() async {
    lists++;
    final error = listError;
    if (error != null) throw error;
    return snapshot;
  }

  @override
  Future<PhoneProcessStopResult> stop(Iterable<int> pids) async {
    final sent = pids.toList();
    stops.add(sent);
    return onStop?.call(sent) ?? PhoneProcessStopResult(stopped: sent);
  }
}

PhoneProcess proc(
  int pid,
  int ppid,
  String name, {
  PhoneProcessOwner owner = PhoneProcessOwner.opencode,
  double cpu = 0,
  int mb = 20,
  Duration elapsed = const Duration(minutes: 30),
  String? command,
  String? session,
}) => PhoneProcess(
  pid: pid,
  ppid: ppid,
  name: name,
  command: command ?? name,
  cpuPercent: cpu,
  rssBytes: mb * 1024 * 1024,
  elapsed: elapsed,
  owner: owner,
  session: session,
);

const chatSession = '4f1c2b7e-0d5a-4c1e-9a2e-6b3f1d0c8a11';
const deadSession = '9a2e5c1d-77b0-4f3e-8d21-0c6a9e4b1f20';

/// Conversation titles the app knows, by agent session.
String? fakeConversationTitle(String session) => switch (session) {
  chatSession => 'Fix the login screen',
  deadSession => 'Run the widget tests',
  _ => null,
};

/// The app, OpenCode running a test, one Claude Code conversation running
/// Flutter tests, and a terminal: nothing wrong.
List<PhoneProcess> normalProcesses({double testerCpu = 35}) => [
  proc(
    9000,
    600,
    'opencode_mobile',
    owner: PhoneProcessOwner.app,
    cpu: 4,
    mb: 180,
    elapsed: const Duration(hours: 2),
  ),
  proc(10100, 9000, 'proot', cpu: 1, mb: 6, elapsed: const Duration(hours: 2)),
  proc(
    10110,
    10100,
    'node',
    cpu: 6,
    mb: 210,
    elapsed: const Duration(hours: 2),
    command: 'node /usr/lib/node_modules/opencode/bin/opencode serve',
  ),
  proc(
    10120,
    10110,
    'bash',
    cpu: 0.4,
    mb: 4,
    elapsed: const Duration(minutes: 3),
    command: 'bash -c npm test',
  ),
  proc(
    10121,
    10120,
    'node',
    cpu: 12,
    mb: 96,
    elapsed: const Duration(minutes: 3),
    command: 'node /usr/bin/npm test',
  ),
  proc(
    10200,
    9000,
    'proot',
    owner: PhoneProcessOwner.paseo,
    cpu: 0.5,
    mb: 6,
    elapsed: const Duration(hours: 1),
  ),
  proc(
    10210,
    10200,
    'node',
    owner: PhoneProcessOwner.paseo,
    cpu: 2,
    mb: 120,
    elapsed: const Duration(hours: 1),
    command: 'node /root/.paseo/daemon.js --listen 127.0.0.1:6767',
  ),
  proc(
    10220,
    10210,
    'claude',
    owner: PhoneProcessOwner.paseo,
    cpu: 20,
    mb: 260,
    elapsed: const Duration(minutes: 12),
    command: 'claude --resume $chatSession --output-format stream-json',
    session: chatSession,
  ),
  proc(
    10230,
    10220,
    'bash',
    owner: PhoneProcessOwner.paseo,
    cpu: 0.2,
    mb: 4,
    elapsed: const Duration(minutes: 4),
    command: 'bash -lc flutter test test/login_test.dart',
  ),
  proc(
    10240,
    10230,
    'flutter_tester',
    owner: PhoneProcessOwner.paseo,
    cpu: testerCpu,
    mb: 340,
    elapsed: const Duration(minutes: 4),
    command: 'flutter_tester --enable-checked-mode test/login_test.dart',
  ),
  proc(
    10300,
    9000,
    'proot',
    owner: PhoneProcessOwner.terminal,
    cpu: 0,
    mb: 6,
    elapsed: const Duration(minutes: 40),
  ),
  proc(
    10310,
    10300,
    'bash',
    owner: PhoneProcessOwner.terminal,
    cpu: 0,
    mb: 5,
    elapsed: const Duration(minutes: 40),
    command: '-bash',
  ),
];

/// What a dead Claude Code conversation left behind on 2026-10-27: its
/// Flutter tests and Dart spinning at about 100% with nothing above them.
List<PhoneProcess> leftBehindProcesses() => [
  proc(
    15001,
    1,
    'claude',
    owner: PhoneProcessOwner.orphan,
    cpu: 0.3,
    mb: 180,
    elapsed: const Duration(minutes: 52),
    command: 'claude --resume $deadSession --output-format stream-json',
    session: deadSession,
  ),
  proc(
    15002,
    15001,
    'bash',
    owner: PhoneProcessOwner.orphan,
    cpu: 0,
    mb: 4,
    elapsed: const Duration(minutes: 50),
    command: 'bash -lc flutter test',
  ),
  for (final (i, name) in [
    'flutter_tester',
    'dart',
    'flutter_tester',
    'dart',
  ].indexed)
    proc(
      15003 + i,
      15002,
      name,
      owner: PhoneProcessOwner.orphan,
      cpu: 99.0 + (i % 2),
      mb: 300 + i * 20,
      elapsed: const Duration(minutes: 49),
      command: '$name --enable-checked-mode test/widget_test.dart',
    ),
];

/// [normalProcesses] with what was left behind, and its Flutter test heavy.
List<PhoneProcess> hotProcesses() => [
  ...normalProcesses(testerCpu: 96),
  ...leftBehindProcesses(),
];

/// 29 of Android's 32: OpenCode running many workers.
List<PhoneProcess> nearLimitProcesses({int workers = 18}) => [
  ...normalProcesses(),
  for (var i = 0; i < workers; i++)
    proc(
      11000 + i,
      10121,
      'node',
      cpu: 3,
      mb: 60,
      elapsed: const Duration(minutes: 2),
      command: 'node /root/projects/shop/node_modules/.bin/jest --worker $i',
    ),
];

/// A clock the test moves.
class FakeClock {
  DateTime now = DateTime.utc(2026, 10, 27, 10);

  void advance(Duration by) => now = now.add(by);
}

/// A watch over [processes] whose readings so far span [history] at
/// 5-second steps, so a process that stays busy is already heavy.
PhoneProcessWatch watchWithHistory(
  FakePhoneProcesses processes,
  FakeClock clock, {
  Duration history = Duration.zero,
}) {
  final watch = PhoneProcessWatch(processes: processes, clock: () => clock.now);
  const step = Duration(seconds: 5);
  for (var t = Duration.zero; t < history; t += step) {
    watch.record(processes.snapshot, clock.now);
    clock.advance(step);
  }
  return watch;
}

/// Running on this phone over [watch], with the app's providers it reads.
Future<void> pumpRunning(
  WidgetTester tester,
  PhoneProcessWatch watch, {
  bool light = false,
  Size size = const Size(412, 915),
  GlobalKey? boundary,
  double textScale = 1,
  Locale? locale,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: ProviderScope(
        overrides: [phoneProcessWatchProvider.overrideWithValue(watch)],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          locale: locale,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              disableAnimations: true,
              textScaler: TextScaler.linear(textScale),
            ),
            child: child!,
          ),
          home: const RunningOnPhoneScreen(
            conversationTitle: fakeConversationTitle,
          ),
        ),
      ),
    ),
  );
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// Ends a test that mounted the page: its 5-second reading stops.
Future<void> unmountRunning(WidgetTester tester) =>
    tester.pumpWidget(const SizedBox.shrink());

/// The words of a row's supporting line.
String rowLine(WidgetTester tester, int pid) {
  final text = tester.widget<Text>(find.byKey(Key('running-proc-line-$pid')));
  return text.textSpan?.toPlainText() ?? text.data ?? '';
}
