// While the app switches this phone's own server between OpenCode versions
// the header names the version being switched to, in one calm progress
// state: never "Reconnecting to … OpenCode 2", "Server password changed" or
// "isn't answering" for the server it is deliberately leaving.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/ui/app_theme.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/runtime_switch_shell.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  late RuntimeSwitchShell shell;

  setUp(() async => shell = await RuntimeSwitchShell.create());

  Future<void> pumpShell(WidgetTester tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(shell.app(theme: AppTheme.dark()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  }

  Future<void> settle(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  }

  Future<void> tearDownShell(WidgetTester tester) async {
    if (shell.starter.starting) shell.linux.finish();
    await tester.pump();
    await tester.pumpWidget(const SizedBox.shrink());
    shell.dispose();
  }

  Finder pill(String text) => find.descendant(
    of: find.byKey(const ValueKey('server-switcher-button')),
    matching: find.textContaining(text),
  );

  testWidgets('a switch names the version it goes to, in every phase', (
    tester,
  ) async {
    await pumpShell(tester);
    // Before the switch: the honest reconnecting words for OpenCode 2.
    expect(
      find.text('Reconnecting to In-app Ubuntu · OpenCode 2…'),
      findsOneWidget,
    );

    // The confirmed switch starts OpenCode 1 on this phone.
    unawaited(shell.starter.start(phoneOne));
    await settle(tester);
    final line = find.byKey(const ValueKey('connection-status-banner'));
    expect(
      find.descendant(
        of: line,
        matching: find.text('Switching to OpenCode 1…'),
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Reconnecting to'), findsNothing);
    // The shell's server pill names the target too, as progress.
    expect(pill('OpenCode 1'), findsOneWidget);
    expect(pill('Switching…'), findsOneWidget);
    expect(pill('OpenCode 2'), findsNothing);

    // The old server's password no longer works and it stops answering:
    // both are the switch itself, not failures to show.
    for (final phase in [
      ConnectionStatusPhase.credentialsRequired,
      ConnectionStatusPhase.notAnswering,
      ConnectionStatusPhase.connecting,
    ]) {
      shell.controller.show(phase);
      await settle(tester);
      expect(
        find.text('Switching to OpenCode 1…'),
        findsOneWidget,
        reason: '$phase',
      );
      expect(find.text('Server password changed — reconnect.'), findsNothing);
      expect(find.textContaining("isn't answering"), findsNothing);
      expect(find.textContaining('Reconnecting to'), findsNothing);
    }
    await tearDownShell(tester);
  });

  testWidgets('restarting the same server still says reconnecting', (
    tester,
  ) async {
    await pumpShell(tester);
    unawaited(shell.starter.start(phoneTwo));
    await settle(tester);
    expect(
      find.text('Reconnecting to In-app Ubuntu · OpenCode 2…'),
      findsOneWidget,
    );
    expect(find.textContaining('Switching'), findsNothing);
    await tearDownShell(tester);
  });
}
