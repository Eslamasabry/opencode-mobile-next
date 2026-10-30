import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

void main() {
  test('a compaction is a notice in the transcript, not a reply', () {
    final message = paseoItemMessage(
      'agent-1',
      {'type': 'compaction', 'status': 'completed', 'trigger': 'manual'},
      id: 'i:7',
      provider: 'claude',
    )!;
    expect(message.info.role, 'user');
    expect(message.parts.single.type, 'v2:compaction');
    expect(message.parts.single.toolName, 'completed');
    expect(message.parts.single.text, 'Conversation compacted.');
  });

  test('an expired sign-in is a sign-in error, its text kept for Details', () {
    final message = paseoItemMessage(
      'agent-1',
      {
        'type': 'assistant_message',
        'text':
            'Failed to authenticate: OAuth session expired and could not be '
            'refreshed',
      },
      id: 'a:3',
      provider: 'claude',
    )!;
    expect(message.parts, isEmpty);
    expect(message.info.errorKind, MessageErrorKind.providerAuth);
    expect(message.info.errorText, contains('OAuth session expired'));
  });

  // Kilo 7.8.3 through Paseo, signed out (seen on the emulator 2026-10-25):
  // the agent's RPC error arrives as reply text and was drawn as three
  // paragraphs of raw error.
  const kiloSignedOut =
      '[System Error] Internal error: You need to sign in to use this model.'
      '\n\ncode: -32603\n\nInternal error: You need to sign in to use this '
      'model. | code=-32603 | data={"service":"session","errorName":"APIError"}';

  test('an agent asking to sign in is a sign-in error', () {
    final message = paseoItemMessage(
      'agent-1',
      {'type': 'assistant_message', 'text': kiloSignedOut},
      id: 'a:4',
      provider: 'kilo',
    )!;
    expect(message.parts, isEmpty);
    expect(message.info.errorKind, MessageErrorKind.providerAuth);
    expect(message.info.errorText, contains('code: -32603'));
  });

  test('a sign-in error keeps its kind on the live path', () {
    // Live replies reach the chat as JSON (message.updated); the kind must
    // survive it, or the row says "stopped because of an error" without
    // Sign in again.
    final message = paseoItemMessage(
      'agent-1',
      {'type': 'assistant_message', 'text': kiloSignedOut},
      id: 'a:6',
      provider: 'kilo',
    )!;
    final info = MessageInfo.fromJson(paseoMessageJson(message.info));
    expect(info.errorKind, MessageErrorKind.providerAuth);
    expect(info.errorText, contains('code: -32603'));
  });

  test("the helper's own error report is an error row, not reply text", () {
    final message = paseoItemMessage(
      'agent-1',
      {
        'type': 'assistant_message',
        'text':
            '[System Error] Internal error: model overloaded\n\n'
            'code: -32603',
      },
      id: 'a:5',
      provider: 'kilo',
    )!;
    expect(message.parts, isEmpty);
    expect(message.info.errorKind, isNot(MessageErrorKind.providerAuth));
    expect(message.info.errorText, contains('model overloaded'));
  });

  test('a busy refresh elsewhere is not a sign-in failure', () {
    expect(
      paseoSignInFailure.hasMatch(
        'Failed to refresh OAuth token: another Claude Code process is '
        'refreshing it or exited mid-refresh.',
      ),
      isFalse,
    );
  });
}
