// "Stop after this step" (vision gaps, 2026-10-21; made discoverable the
// same day): a long-press on Stop sends the running turn a steering note
// asking the agent to stop once the step it is on is done; a tap on Stop
// still stops at once. The hold is never the only path: a tap on the live
// line shows the steps with a plain "Stop after this step", a tip points at
// the hold once a reply has run for 10 s, and once chosen the live line
// reads "Stopping after this step · Undo" until the turn ends.
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _sid = 'session-1';
final _en = lookupAppLocalizations(const Locale('en'));

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api({required this.steering, this.inbox = false, this.started})
    : super(baseUrl: 'http://localhost');

  final bool steering;
  final bool inbox;

  /// When the running reply began; null: no transcript at all.
  final DateTime? started;
  final sent = <({String text, PromptDelivery? delivery})>[];
  final aborted = <String>[];
  final cancelled = <String>[];
  Object? cancelError;

  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(promptSteering: steering, inbox: inbox);

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    final started = this.started;
    if (started == null) return const [];
    final at = started.millisecondsSinceEpoch;
    return [
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_user',
          sessionID: _sid,
          role: 'user',
          time: MsgTime(created: at),
        ),
        parts: [
          Part(
            id: 'p1',
            messageID: 'msg_user',
            type: 'text',
            text: 'Make hello.txt',
          ),
        ],
      ),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_reply',
          sessionID: _sid,
          role: 'assistant',
          time: MsgTime(created: at + 1000),
        ),
        parts: [
          Part(
            id: 'call_edit',
            messageID: 'msg_reply',
            callID: 'call_edit',
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState.fromJson({
              'status': 'running',
              'input': {'filePath': '/root/projects/neon-breaker/hello.txt'},
            }, toolName: 'edit'),
          ),
        ],
      ),
    ];
  }

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => const [];

  @override
  Future<void> cancelInboxItem(String sessionID, String inboxID) async {
    if (cancelError case final error?) throw error;
    cancelled.add(inboxID);
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async => sent.add((text: text, delivery: delivery));

  @override
  Future<void> abort(String sessionID) async => aborted.add(sessionID);
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

Future<(_Api, _Controller)> _pump(
  WidgetTester tester, {
  required bool steering,
  bool inbox = false,
  DateTime? started,
  Map<String, Object> prefs = const {},
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.nudges.firstReplySeen': true,
    ...prefs,
  });
  final preferences = await SharedPreferences.getInstance();
  final api = _Api(steering: steering, inbox: inbox, started: started);
  final controller = _Controller(_Store(prefs: preferences))
    ..api = api
    ..status = StreamStatus.connected
    ..directory = '/root/projects/neon-breaker'
    ..busySessions = {_sid};
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
        home: const ChatScreen(sessionID: _sid),
      ),
    ),
  );
  await _settle(tester);
  return (api, controller);
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// The note as the server's inbox reports it once admitted.
void _enqueueNote(ConnectionController controller) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.inbox.enqueued',
      properties: {
        'sessionID': _sid,
        'inboxID': 'msg_note',
        'item': {
          'type': 'user',
          'payload': {'text': _en.chatStopAfterStepPrompt},
          'delivery': 'steer',
        },
      },
    ),
  );
}

final _stopButton = find.byKey(const Key('chat-stop-button'));
final _tipBubble = find.byKey(const ValueKey('kit-composer-stop-tip-bubble'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a long-press on Stop sends a steer that stops after this '
      'step, does not stop now, and the live line says it waits', (
    tester,
  ) async {
    final (api, _) = await _pump(
      tester,
      steering: true,
      started: DateTime.now().subtract(const Duration(seconds: 3)),
    );
    await tester.longPress(_stopButton);
    await _settle(tester);
    expect(find.text('Stop after this step'), findsOneWidget);
    await tester.tap(find.byKey(const Key('composer-stop-after-step')));
    await _settle(tester);
    expect(api.sent, hasLength(1));
    expect(api.sent.single.delivery, PromptDelivery.steer);
    expect(api.sent.single.text, contains('stop after the step'));
    expect(api.aborted, isEmpty);
    expect(find.textContaining('Stopping after this step'), findsOneWidget);
    // Stop says it waits, and its menu is gone (no second request).
    expect(
      tester.getSemantics(_stopButton).label,
      contains('stopping after this step'),
    );
    await tester.longPress(_stopButton);
    await _settle(tester);
    expect(find.byKey(const Key('composer-stop-after-step')), findsNothing);
  });

  testWidgets('a tap on Stop still stops at once', (tester) async {
    final (api, _) = await _pump(tester, steering: true);
    await tester.tap(_stopButton);
    await _settle(tester);
    expect(api.aborted, [_sid]);
    expect(api.sent, isEmpty);
    expect(find.text('Stop after this step'), findsNothing);
  });

  testWidgets('without steering Stop has no menu', (tester) async {
    final (api, _) = await _pump(tester, steering: false);
    expect(tester.widget<GestureDetector>(_stopButton).onLongPress, isNull);
    await tester.longPress(_stopButton);
    await _settle(tester);
    expect(find.text('Stop after this step'), findsNothing);
    expect(api.sent, isEmpty);
  });

  testWidgets('Stop names "Stop after this step" as a screen-reader action', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    final (api, _) = await _pump(tester, steering: true);
    final node = tester.getSemantics(_stopButton);
    final actions = node.getSemanticsData().customSemanticsActionIds ?? [];
    final labels = [
      for (final id in actions) CustomSemanticsAction.getAction(id)?.label,
    ];
    expect(labels, contains('Stop after this step'));
    final id = actions.firstWhere(
      (id) =>
          CustomSemanticsAction.getAction(id)?.label == 'Stop after this step',
    );
    node.owner!.performAction(node.id, SemanticsAction.customAction, id);
    await _settle(tester);
    expect(api.sent, hasLength(1));
    expect(api.aborted, isEmpty);
    handle.dispose();
  });

  testWidgets('a tap on the live line shows the steps with a plain "Stop '
      'after this step"', (tester) async {
    final (api, _) = await _pump(
      tester,
      steering: true,
      started: DateTime.now().subtract(const Duration(seconds: 3)),
    );
    final line = find.byKey(const ValueKey('kit-turn-live-tap'));
    expect(line, findsOneWidget);
    expect(find.byKey(const Key('live-stop-after-step')), findsNothing);
    await tester.tap(line);
    await _settle(tester);
    // The running step is drawn, and the visible way to stop after it.
    expect(find.byKey(const Key('live-stop-after-step')), findsOneWidget);
    await tester.tap(find.byKey(const Key('live-stop-after-step')));
    await _settle(tester);
    expect(api.sent.single.delivery, PromptDelivery.steer);
    expect(api.aborted, isEmpty);
    expect(find.textContaining('Stopping after this step'), findsOneWidget);
    expect(find.byKey(const Key('live-stop-after-step')), findsNothing);
  });

  testWidgets('Undo cancels the note while it waits in the inbox, and the '
      'waiting strip does not repeat it', (tester) async {
    final (api, controller) = await _pump(
      tester,
      steering: true,
      inbox: true,
      started: DateTime.now().subtract(const Duration(seconds: 3)),
    );
    await tester.longPress(_stopButton);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('composer-stop-after-step')));
    await _settle(tester);
    // Before the inbox reports the note there is nothing to take back.
    expect(find.byKey(const Key('live-stop-after-step-undo')), findsNothing);
    _enqueueNote(controller);
    await _settle(tester);
    expect(find.text(_en.chatStopAfterStepPrompt), findsNothing);
    await tester.tap(find.byKey(const Key('live-stop-after-step-undo')));
    await _settle(tester);
    expect(api.cancelled, ['msg_note']);
    expect(find.textContaining('Stopping after this step'), findsNothing);
    expect(find.text(_en.chatStopAfterStepUndone), findsOneWidget);
  });

  testWidgets('Undo after the agent read the note says it is too late', (
    tester,
  ) async {
    final (api, controller) = await _pump(
      tester,
      steering: true,
      inbox: true,
      started: DateTime.now().subtract(const Duration(seconds: 3)),
    );
    api.cancelError = ApiException('already delivered', statusCode: 409);
    await tester.longPress(_stopButton);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('composer-stop-after-step')));
    await _settle(tester);
    _enqueueNote(controller);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('live-stop-after-step-undo')));
    await _settle(tester);
    expect(find.text(_en.chatStopAfterStepTooLate), findsOneWidget);
    expect(find.textContaining('Stopping after this step'), findsOneWidget);
  });

  testWidgets('the waiting look clears when the turn ends', (tester) async {
    final (_, controller) = await _pump(
      tester,
      steering: true,
      started: DateTime.now().subtract(const Duration(seconds: 3)),
    );
    await tester.longPress(_stopButton);
    await _settle(tester);
    await tester.tap(find.byKey(const Key('composer-stop-after-step')));
    await _settle(tester);
    expect(find.textContaining('Stopping after this step'), findsOneWidget);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: {
          'sessionID': _sid,
          'status': {'type': 'idle'},
        },
      ),
    );
    controller.busySessions = {};
    await _settle(tester);
    expect(find.textContaining('Stopping after this step'), findsNothing);
    controller.busySessions = {_sid};
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: {
          'sessionID': _sid,
          'status': {'type': 'busy'},
        },
      ),
    );
    await _settle(tester);
    // A new turn starts fresh: Stop has its menu again.
    await tester.longPress(_stopButton);
    await _settle(tester);
    expect(find.byKey(const Key('composer-stop-after-step')), findsOneWidget);
  });

  group('the tip', () {
    testWidgets('shows once a reply has run for 10 s, then never again once '
        'the person used the hold', (tester) async {
      final (_, controller) = await _pump(
        tester,
        steering: true,
        started: DateTime.now().subtract(const Duration(seconds: 12)),
      );
      expect(_tipBubble, findsOneWidget);
      expect(find.text(_en.tipStopAfterStep), findsOneWidget);
      expect(controller.tips.record(TeachingIds.stopAfterStep).count, 1);
      // It fades by itself.
      await tester.pump(const Duration(seconds: 5));
      await _settle(tester);
      expect(find.text(_en.tipStopAfterStep), findsNothing);
      await tester.longPress(_stopButton);
      await _settle(tester);
      await tester.tap(find.byKey(const Key('composer-stop-after-step')));
      await _settle(tester);
      expect(controller.tips.record(TeachingIds.stopAfterStep).done, isTrue);
    });

    testWidgets('waits for the 10 s mark', (tester) async {
      await _pump(
        tester,
        steering: true,
        started: DateTime.now().subtract(const Duration(seconds: 2)),
      );
      expect(find.text(_en.tipStopAfterStep), findsNothing);
    });

    testWidgets('is announced politely to a screen reader', (tester) async {
      final announced = <String>[];
      tester.binding.defaultBinaryMessenger
          .setMockDecodedMessageHandler<dynamic>(SystemChannels.accessibility, (
            message,
          ) async {
            final data = (message as Map)['data'] as Map;
            announced.add('${data['message']}');
            return null;
          });
      addTearDown(
        () => tester.binding.defaultBinaryMessenger
            .setMockDecodedMessageHandler<dynamic>(
              SystemChannels.accessibility,
              null,
            ),
      );
      await _pump(
        tester,
        steering: true,
        started: DateTime.now().subtract(const Duration(seconds: 12)),
      );
      expect(announced, contains(_en.tipStopAfterStep));
    });

    testWidgets('shows at most twice', (tester) async {
      await _pump(
        tester,
        steering: true,
        started: DateTime.now().subtract(const Duration(seconds: 12)),
        prefs: {
          'oc.tips':
              '{"stop-after-step":{"count":2,"last":"2026-01-01T00:00:00Z"}}',
        },
      );
      expect(find.text(_en.tipStopAfterStep), findsNothing);
    });

    testWidgets('waits for the first reply ever (no tips while setting up)', (
      tester,
    ) async {
      await _pump(
        tester,
        steering: true,
        started: DateTime.now().subtract(const Duration(seconds: 12)),
        prefs: {'oc.nudges.firstReplySeen': false},
      );
      expect(find.text(_en.tipStopAfterStep), findsNothing);
    });

    testWidgets('does not show without the hold to teach', (tester) async {
      await _pump(
        tester,
        steering: false,
        started: DateTime.now().subtract(const Duration(seconds: 12)),
      );
      expect(find.text(_en.tipStopAfterStep), findsNothing);
    });
  });
}
