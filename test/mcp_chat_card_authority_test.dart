import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/delayed_answers.dart';
import 'package:opencode_mobile/state/gen_ui_state.dart';

const scope = GenUiScope(
  profileID: 'p',
  sourceId: 'opencode',
  directory: '/work',
);

class Gateway implements ServerGateway {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

MessageWithParts message({String reason = 'Find references'}) =>
    MessageWithParts(
      info: MessageInfo(id: 'm', sessionID: 's', role: 'assistant'),
      parts: [
        Part(
          id: 'p',
          type: 'tool',
          messageID: 'm',
          callID: 'c',
          toolName: 'oc-ui_show',
          toolState: ToolState(
            status: 'completed',
            input: {
              'v': 1,
              'id': 'suggest',
              'title': 'Suggested connector',
              'body': <Object>[],
              'connector': {
                'catalogId': 'com.example/design',
                'reason': reason,
              },
            },
          ),
        ),
      ],
    );
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late GenUiStateController state;
  late DelayedAnswers delayed;
  late bool live;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    delayed = DelayedAnswers();
    state = GenUiStateController(
      await SharedPreferences.getInstance(),
      delayedAnswers: delayed,
    );
    live = true;
    state.register(
      scope,
      Gateway(),
      endpoint: 'endpoint',
      current: () => live,
      ready: true,
    );
    state.observe(scope, 's', [message()], tailComplete: true);
  });
  tearDown(() {
    state.dispose();
    delayed.dispose();
  });
  GenUiCard card() =>
      (genUiFromPart(
                message().parts.single,
                scope: scope,
                sessionID: 's',
                messageID: 'm',
              )
              as GenUiParsed)
          .card;
  test(
    'retained payload is available during suspension but cannot authorize new requests',
    () {
      final original = card();
      expect(state.state(original), GenUiCardState.report);
      live = false;
      expect(state.state(original), GenUiCardState.unknown);
      expect(state.registeredReady(scope), isTrue);
      expect(
        state.observedCard(original)?.connector?.catalogId,
        'com.example/design',
      );
      expect(state.observedState(original), GenUiCardState.report);
    },
  );
  test('replaced or deleted payload cannot reuse prior consent', () async {
    final original = card();
    state.observe(scope, 's', [
      message(reason: 'Changed recommendation'),
    ], tailComplete: true);
    expect(state.observedCard(original), isNull);
    expect(state.observedState(original), GenUiCardState.unknown);
    await state.removeSession(scope, 's');
    expect(state.observedCard(original), isNull);
    expect(state.observedState(original), GenUiCardState.unknown);
  });
  test('incomplete recovery is different from a complete removed card', () {
    final original = card();
    state.observe(scope, 's', [], tailComplete: false);
    expect(state.observedState(original), isNull);
    state.observe(scope, 's', [], tailComplete: true);
    expect(state.observedState(original), GenUiCardState.unknown);
  });
}
