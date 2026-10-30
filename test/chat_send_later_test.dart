// "Send later or repeat" (scheduled prompts from the chat, 2026-10-20): a
// long-press on Send offers to schedule the words in the box for this
// conversation, where the server can run scheduled prompts.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/fake_event_channel.dart';
import 'support/fake_server_gateway.dart';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api({required this.schedules}) : super(baseUrl: 'http://localhost');

  final bool schedules;

  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(scheduledPrompts: schedules);
}

class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost'),
  ];
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');
}

Future<_Controller> _pump(
  WidgetTester tester, {
  required bool schedules,
}) async {
  SharedPreferences.setMockInitialValues({'oc.nudges.firstReplySeen': true});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(_Store(prefs: prefs))
    ..api = _Api(schedules: schedules)
    ..status = StreamStatus.connected
    ..directory = '/root/projects/neon-breaker';
  addTearDown(controller.dispose);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ChatScreen(sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byKey(const Key('chat-composer-field')),
    'Run the nightly checks',
  );
  await tester.pumpAndSettle();
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a long-press on Send offers Send later or repeat, without '
      'sending', (tester) async {
    await _pump(tester, schedules: true);
    await tester.longPress(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-send-later')), findsOneWidget);
    expect(find.text('Send later or repeat'), findsOneWidget);
    // Nothing went: the words are still in the box.
    expect(find.text('Run the nightly checks'), findsOneWidget);
  });

  testWidgets('a server that cannot schedule offers no such menu', (
    tester,
  ) async {
    await _pump(tester, schedules: false);
    expect(
      tester
          .widget<GestureDetector>(find.byKey(const Key('chat-send-button')))
          .onLongPress,
      isNull,
    );
    expect(find.text('Send later or repeat'), findsNothing);
    expect(find.text('Hold Send to send later or repeat'), findsNothing);
  });

  testWidgets('the hold is taught once words wait in the box, and never '
      'again once Send later was opened', (tester) async {
    final controller = await _pump(tester, schedules: true);
    expect(
      find.byKey(const ValueKey('kit-composer-send-tip-bubble')),
      findsOneWidget,
    );
    expect(find.text('Hold Send to send later or repeat'), findsOneWidget);
    expect(controller.tips.record(TeachingIds.sendLater).count, 1);
    await tester.longPress(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();
    // Any touch takes the tip away.
    expect(find.text('Hold Send to send later or repeat'), findsNothing);
    await tester.tap(find.byKey(const Key('composer-send-later')));
    await tester.pump();
    expect(controller.tips.record(TeachingIds.sendLater).done, isTrue);
  });
}
