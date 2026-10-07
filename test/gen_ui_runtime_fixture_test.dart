import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

const _scope = GenUiScope(
  profileID: 'fixture-profile',
  sourceId: 'fixture-paseo-source',
  directory: '/root/projects/fixture',
);
const _sessionID = 'fixture-session';

Map<String, dynamic> _capturedTool() =>
    jsonDecode(
          File(
            'test/fixtures/genui/runtime_paseo_claude_tool.json',
          ).readAsStringSync(),
        )
        as Map<String, dynamic>;

void main() {
  test('captured Paseo Claude tool becomes a scoped confirm card', () {
    final messages = paseoTimelineMessages(
      _sessionID,
      _capturedTool(),
      busy: false,
    );
    expect(messages, hasLength(1));
    final message = messages.single;
    expect(message.info.role, 'assistant');
    expect(message.info.sessionID, _sessionID);
    expect(message.parts, hasLength(1));
    final part = message.parts.single;
    expect(part.toolName, 'mcp__oc-ui__show');
    expect(part.toolState.status, 'completed');
    expect(part.toolState.executed, isTrue);
    expect(part.callID, 'call_runtime_probe');

    final parsed = genUiFromPart(
      part,
      scope: _scope,
      sessionID: _sessionID,
      messageID: message.info.id,
    );
    expect(parsed, isA<GenUiParsed>());
    final card = (parsed! as GenUiParsed).card;
    expect(card.scope, _scope);
    expect(card.sessionID, _sessionID);
    expect(card.callID, 'call_runtime_probe');
    expect(card.messageID, message.info.id);
    expect(card.id, 'runtime-probe');
    expect(card.title, 'Runtime probe');
    expect(card.body, isEmpty);
    expect(card.ask, isA<GenUiConfirmAsk>());
    expect(
      genUiStateFor(card, messages, tailComplete: true),
      GenUiCardState.waiting,
    );
    expect(
      genUiStateFor(card, messages, tailComplete: false),
      GenUiCardState.unknown,
    );
  });

  test('running form of captured tool stays an ordinary tool part', () {
    final payload = _capturedTool();
    final entry = (payload['entries'] as List).single as Map<String, dynamic>;
    final item = entry['item'] as Map<String, dynamic>;
    item['status'] = 'running';
    final message = paseoTimelineMessages(
      _sessionID,
      payload,
      busy: true,
    ).single;

    expect(message.parts.single.toolName, 'mcp__oc-ui__show');
    expect(
      genUiFromPart(
        message.parts.single,
        scope: _scope,
        sessionID: _sessionID,
        messageID: message.info.id,
      ),
      isNull,
    );
  });
}
