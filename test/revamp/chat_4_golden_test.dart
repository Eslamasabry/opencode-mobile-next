// Gallery of unit chat-4 (map pages command-launcher-sheet and
// team-conversation): the command launcher with its actions and with a
// search that matches nothing, and a team task as a conversation while its
// worker starts and while a question waits for the person, at 412x915 and
// 1280x800, dark and light, with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/chat_4_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/team_chat_fixture.dart';
import 'chat_3_support.dart';
import '../support/composer_tools.dart';

typedef _Scene = Future<void> Function(WidgetTester tester);

Future<void> _openLauncher(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-commands')));
  await tester.pumpAndSettle();
}

final _launcherScenes = <String, _Scene>{
  'chat_4_command_launcher': _openLauncher,
  'chat_4_command_launcher_no_match': (tester) async {
    await _openLauncher(tester);
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'deploy',
    );
    await tester.pumpAndSettle();
  },
};

final _gate = OrchestrationGate(
  id: 'ma-gate-1',
  kind: GateKind.choice,
  title: 'Which default?',
  prompt: 'Should the toggle follow the system setting?',
  workId: 'ma-1',
  runId: 'ma-convoy-1',
  choices: const ['Follow the system', 'Always dark'],
  createdAt: DateTime.utc(2026, 9, 25, 19, 54),
);

final _teamScenes = <String, List<OrchestrationGate>>{
  'chat_4_team_conversation_working': const [],
  'chat_4_team_conversation_needs_you': [_gate],
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(chat3MockSecureStorage);

  for (final size in const [Size(412, 915), Size(1280, 800)]) {
    for (final light in [false, true]) {
      final sized = size.width == 412
          ? ''
          : '_${size.width.toInt()}x${size.height.toInt()}';
      final theme = light ? 'light' : 'dark';

      for (final MapEntry(key: name, value: scene) in _launcherScenes.entries) {
        final file = '$name${sized}_$theme';
        testWidgets(file, (tester) async {
          final conn = await chat3Controller();
          addTearDown(conn.dispose);
          final boundary = GlobalKey();
          try {
            await pumpChat3(
              tester,
              conn,
              size: size,
              light: light,
              boundary: boundary,
            );
            await scene(tester);
            expect(tester.takeException(), isNull);
            await expectLater(
              find.byKey(boundary),
              matchesGoldenFile('goldens/$file.png'),
            );
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump();
            debugDefaultTargetPlatformOverride = null;
          }
        });
      }

      for (final MapEntry(key: name, value: gates) in _teamScenes.entries) {
        final file = '$name${sized}_$theme';
        testWidgets(file, (tester) async {
          // Elapsed words ("for 4 min") read the pinned clock (TEST-11).
          await withClock(Clock.fixed(teamClock), () async {
            debugDefaultTargetPlatformOverride = TargetPlatform.android;
            tester.view.physicalSize = size;
            tester.view.devicePixelRatio = 1;
            addTearDown(tester.view.reset);
            final (team, _) = await bootTeam(gates: gates);
            final connection = await teamConnection(
              api: TeamChatApi(const {}),
              repository: TeamChatRepository(const []),
            );
            final boundary = GlobalKey();
            try {
              await tester.pumpWidget(
                RepaintBoundary(
                  key: boundary,
                  child: ProviderScope(
                    overrides: [connProvider.overrideWithValue(connection)],
                    child: MaterialApp(
                      debugShowCheckedModeBanner: false,
                      theme: captureTheme(light: light),
                      localizationsDelegates:
                          AppLocalizations.localizationsDelegates,
                      supportedLocales: AppLocalizations.supportedLocales,
                      builder: (context, child) => MediaQuery(
                        data: MediaQuery.of(
                          context,
                        ).copyWith(disableAnimations: true),
                        child: child!,
                      ),
                      home: TeamControllerScope(
                        team: team,
                        child: TeamConversationScreen(
                          team: team,
                          runId: 'ma-convoy-1',
                          now: () => teamClock,
                        ),
                      ),
                    ),
                  ),
                ),
              );
              await tester.pumpAndSettle();
              expect(tester.takeException(), isNull);
              await expectLater(
                find.byKey(boundary),
                matchesGoldenFile('goldens/$file.png'),
              );
            } finally {
              await tester.pumpWidget(const SizedBox.shrink());
              await tester.pump();
              debugDefaultTargetPlatformOverride = null;
            }
          });
        });
      }
    }
  }
}
