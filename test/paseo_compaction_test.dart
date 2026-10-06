import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
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
