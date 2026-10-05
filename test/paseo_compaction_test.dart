import 'package:flutter_test/flutter_test.dart';
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
}
