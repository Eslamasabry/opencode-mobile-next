// Sending and stopping around a slow or returning connection (fix/perf-chat
// 2026-10-27): Stop calls off a send still waiting for the connection; a
// Stop waits at most five seconds for it; a send made while the connection
// is coming back goes out once it is back; a turn that ends reads the
// newest history again; a read-only sub-agent page offers no Stop and its
// stall line points at the main conversation.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

const _prompt = 'Run the full test suite';

class _Api extends CaptureApi {
  _Api({this.subagentReadOnly = false}) {
    busy = {};
  }

  final bool subagentReadOnly;
  List<MessageWithParts> history = const [];
  int reads = 0;
  int aborts = 0;

  @override
  ServerCapabilities get capabilities => subagentReadOnly
      ? const ServerCapabilities(subagentReplies: false, subagentSessions: true)
      : super.capabilities;

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
  }) async {
    prompts.add(text);
  }

  @override
  Future<void> abort(String sessionID) async {
    aborts += 1;
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    if (cursor == null) reads += 1;
    return ServerPage(items: cursor == null ? history : const []);
  }
}

/// A connection whose action transport answers only when the test says so,
/// and whose stall watchdog reports [stall].
class _Controller extends CaptureController {
  _Controller(super.store);

  Completer<ServerGateway?> transport = Completer();
  int transportCalls = 0;
  TurnStallDiagnosis? stall;

  @override
  Future<ServerGateway?> prepareActionTransport() {
    transportCalls += 1;
    return transport.future;
  }

  @override
  TurnStallDiagnosis? turnStallFor(String sessionId) => stall;
}

Future<_Controller> _open(
  WidgetTester tester,
  _Api api, {
  Set<String> busy = const {},
  String? parentID,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller =
      _Controller(
          SeededProfileStore(
            prefs: prefs,
            seeded: [
              ServerProfile(
                id: 'laptop',
                name: 'Laptop',
                baseUrl: 'http://192.168.1.20:4096',
              ),
            ],
          ),
        )
        ..api = api
        ..repository = CaptureRepository()
        ..status = StreamStatus.connected
        ..directory = projectDirectory
        ..sessionsById = {
          ...api.sessionsById,
          checkoutSessionID: Session(
            id: checkoutSessionID,
            directory: projectDirectory,
            parentID: parentID,
          ),
        }
        ..busySessions = Set.of(busy);
  addTearDown(() async {
    if (!controller.transport.isCompleted) controller.transport.complete(null);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  await _frames(tester);
  return controller;
}

Future<void> _frames(WidgetTester tester, [int count = 6]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

String _composerText(WidgetTester tester) => tester
    .widget<EditableText>(
      find.descendant(
        of: find.byKey(const Key('chat-composer-field')),
        matching: find.byType(EditableText),
      ),
    )
    .controller
    .text;

Future<void> _typeAndSend(WidgetTester tester) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), _prompt);
  await tester.pump();
  await tester.tap(find.byKey(const Key('chat-send-button')));
  await tester.pump();
}

void _status(_Controller controller, Object status) =>
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: {'sessionID': checkoutSessionID, 'status': status},
      ),
    );

final _stop = find.byKey(const Key('chat-stop-button'));

void main() {
  testWidgets('Stop while a send waits for the connection calls it off: '
      'nothing is sent and the words are back in the box', (tester) async {
    final api = _Api();
    final controller = await _open(tester, api);
    await _typeAndSend(tester);
    expect(find.text(_prompt), findsOneWidget); // the bubble
    expect(_composerText(tester), isEmpty);

    // Past the moment a late tap meant for Send is ignored.
    await tester.pump(const Duration(milliseconds: 900));
    expect(_stop, findsOneWidget);
    await tester.tap(_stop);
    await tester.pump();
    expect(_composerText(tester), _prompt);
    expect(find.text(_prompt), findsOneWidget); // the box, not a bubble

    // The connection comes back afterwards: the send stays called off.
    controller.transport.complete(api);
    await _frames(tester);
    expect(api.prompts, isEmpty);
    expect(api.aborts, 0);
    expect(_composerText(tester), _prompt);
  });

  testWidgets('a Stop waits at most five seconds for the connection', (
    tester,
  ) async {
    final api = _Api();
    final controller = await _open(tester, api, busy: {checkoutSessionID});
    expect(_stop, findsOneWidget);
    final before = controller.transportCalls;
    await tester.tap(_stop);
    await tester.pump();
    expect(controller.transportCalls, before + 1);
    expect(find.textContaining('Try again shortly'), findsNothing);
    // Five seconds on, the Stop gives up and says so in plain words.
    await tester.pump(const Duration(seconds: 6));
    await _frames(tester);
    expect(find.textContaining('Try again shortly'), findsOneWidget);
    expect(api.aborts, 0);
  });

  testWidgets('a send while the connection is coming back goes out once it '
      'is back, not to the queue', (tester) async {
    final api = _Api();
    final controller = await _open(tester, api);
    controller.status = StreamStatus.reconnecting;
    await tester.pump();
    await _typeAndSend(tester);
    expect(api.prompts, isEmpty);

    controller.status = StreamStatus.connected;
    controller.transport.complete(api);
    await _frames(tester);
    expect(api.prompts, [_prompt]);
    expect(find.byKey(const Key('queued-draft-notice')), findsNothing);
    // The quiet-reconnect clock the status change started runs out.
    await tester.pump(const Duration(seconds: 16));
  });

  testWidgets('a turn that ran across a resume reads the newest history '
      'again when it ends', (tester) async {
    final api = _Api();
    final controller = await _open(tester, api);
    _status(controller, {'type': 'busy'});
    await _frames(tester);
    // The phone slept and woke while the turn ran: a new connection.
    controller.connectionRevision += 1;
    _status(controller, {'type': 'busy'});
    await _frames(tester);
    final before = api.reads;
    _status(controller, 'idle');
    await _frames(tester);
    expect(api.reads, before + 1);
  });

  testWidgets('a read-only sub-agent page offers no Stop; its stall line '
      'opens the main conversation instead', (tester) async {
    final api = _Api(subagentReadOnly: true)
      ..history = [
        MessageWithParts(
          info: messageInfo('msg_user', 'user', created: 1),
          parts: [textPart('part_user', 'Look into the flaky test')],
        ),
      ];
    final controller = await _open(
      tester,
      api,
      busy: {checkoutSessionID},
      parentID: 'parent-session',
    );
    controller.stall = const TurnStallDiagnosis(
      kind: TurnStallKind.modelSlow,
      evidence: TurnStallEvidence(
        transportConnected: true,
        endpointReachable: true,
      ),
      silentFor: Duration(seconds: 52),
    );
    _status(controller, {'type': 'busy'});
    await _frames(tester);

    expect(_stop, findsNothing);
    expect(find.byKey(const ValueKey('turn-stall')), findsOneWidget);
    expect(find.byKey(const Key('turn-stall-stop')), findsNothing);
    expect(find.byKey(const Key('turn-stall-open-parent')), findsOneWidget);
    expect(find.textContaining('stop the reply there'), findsOneWidget);
    expect(find.textContaining('Wait, or stop the reply'), findsNothing);
  });
}
