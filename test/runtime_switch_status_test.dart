// While the app switches this phone's own server between OpenCode versions
// the header names the version being switched to, in one calm progress
// state: never "Reconnecting to … OpenCode 2", "Server password changed" or
// "isn't answering" for the server it is deliberately leaving.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

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

  Finder line() => find.byKey(const ValueKey('connection-status-banner'));

  testWidgets('a switch is said once, in the server pill, in every phase', (
    tester,
  ) async {
    await pumpShell(tester);
    // The confirmed switch starts OpenCode 1 on this phone.
    unawaited(shell.starter.start(phoneOne));
    // The old server reconnects, refuses its old password, stops answering:
    // all of it is the switch itself. One indicator, never two spinners.
    for (final phase in [
      ConnectionStatusPhase.reconnecting,
      ConnectionStatusPhase.credentialsRequired,
      ConnectionStatusPhase.notAnswering,
      ConnectionStatusPhase.connecting,
    ]) {
      shell.controller.show(phase);
      await settle(tester);
      expect(pill('OpenCode 1'), findsOneWidget, reason: '$phase');
      expect(pill('Switching…'), findsOneWidget, reason: '$phase');
      expect(pill('OpenCode 2'), findsNothing, reason: '$phase');
      expect(line(), findsNothing, reason: '$phase');
      expect(find.textContaining('Switching to'), findsNothing);
      expect(find.text('Server password changed — reconnect.'), findsNothing);
      expect(find.textContaining("isn't answering"), findsNothing);
      expect(find.textContaining('Reconnecting to'), findsNothing);
    }
    await tearDownShell(tester);
  });

  testWidgets('a page without the pill says the switch in its status line', (
    tester,
  ) async {
    await pumpShell(tester);
    unawaited(shell.starter.start(phoneOne));
    unawaited(
      shell.navigator.currentState!.push(
        KitPageRoute<void>(
          builder: (_) => const KitScreen(body: SizedBox.shrink()),
        ),
      ),
    );
    await settle(tester);
    await tester.pump(const Duration(milliseconds: 500));
    expect(pill('Switching…'), findsNothing);
    expect(
      find.descendant(
        of: line(),
        matching: find.text('Switching to OpenCode 1…'),
      ),
      findsOneWidget,
    );
    await tearDownShell(tester);
  });

  testWidgets('a plain reconnect is said once too; a real problem keeps its '
      'line and its action', (tester) async {
    await pumpShell(tester);
    // Restarting the same server is no switch: it reconnects, said by the
    // pill alone.
    unawaited(shell.starter.start(phoneTwo));
    await settle(tester);
    expect(pill('Reconnecting'), findsOneWidget);
    expect(pill('Switching'), findsNothing);
    expect(line(), findsNothing);
    shell.linux.finish();
    await settle(tester);
    shell.controller.show(ConnectionStatusPhase.connecting);
    await settle(tester);
    expect(pill('Connecting'), findsOneWidget);
    expect(line(), findsNothing);
    // Not answering needs the person: the line comes back with its act.
    shell.controller.show(ConnectionStatusPhase.notAnswering);
    await settle(tester);
    expect(line(), findsOneWidget);
    expect(
      find.descendant(of: line(), matching: find.byType(TextButton)),
      findsWidgets,
    );
    shell.controller.show(ConnectionStatusPhase.credentialsRequired);
    await settle(tester);
    expect(find.text('Server password changed — reconnect.'), findsOneWidget);
    await tearDownShell(tester);
  });
}
