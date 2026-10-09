// What a Claude Code tool answered reaches the tool card, in each of the
// shapes Paseo 0.9.1 sends (claude/agent.js buildToolOutput and
// tool-call-detail-primitives.js).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

ToolState _tool(String name, Map<String, dynamic> detail) {
  final message = paseoItemMessage(
    'agent-1',
    {
      'type': 'tool_call',
      'callId': 'call-1',
      'name': name,
      'status': 'completed',
      'detail': detail,
    },
    id: 'call-1',
    provider: 'claude',
  );
  return message!.parts.single.toolState;
}

void main() {
  test('an MCP tool answering JSON keeps it as a value', () {
    final state = _tool('mcp__agent_cards__search_connectors', {
      'type': 'unknown',
      'input': {'query': 'github', 'limit': 5},
      'output': {
        'output': {
          'status': 'ok',
          'matches': [
            {'catalogId': 'io.github/github', 'name': 'GitHub'},
          ],
        },
      },
    });
    expect(state.outputValue, isA<Map>());
    expect((state.outputValue as Map)['status'], 'ok');
    expect(state.output, contains('GitHub'));
  });

  test('an MCP tool answering text shows the text', () {
    final state = _tool('mcp__drive__search', {
      'type': 'unknown',
      'input': {'query': 'notes'},
      'output': {'output': 'Found 3 files'},
    });
    expect(state.output, 'Found 3 files');
  });

  test('ToolSearch answering a list shows it', () {
    final state = _tool('ToolSearch', {
      'type': 'unknown',
      'input': {'query': 'github'},
      'output': {
        'output': [
          {'type': 'tool_reference', 'tool_name': 'mcp__github__search'},
        ],
      },
    });
    expect(state.output, contains('mcp__github__search'));
  });

  test('grep, glob, fetch and skills show what they found', () {
    expect(
      _tool('Grep', {
        'type': 'search',
        'query': 'todo',
        'content': 'a.dart:3: todo',
      }).output,
      'a.dart:3: todo',
    );
    expect(
      _tool('Glob', {
        'type': 'search',
        'query': '*.dart',
        'filePaths': ['a.dart', 'b.dart'],
      }).output,
      'a.dart\nb.dart',
    );
    expect(
      _tool('WebFetch', {
        'type': 'fetch',
        'url': 'https://example.com',
        'result': 'Example Domain',
      }).output,
      'Example Domain',
    );
    expect(
      _tool('Skill', {
        'type': 'plain_text',
        'label': 'review',
        'text': 'Reviewed.',
      }).output,
      'Reviewed.',
    );
  });

  test('shell and read keep working', () {
    expect(
      _tool('Bash', {
        'type': 'shell',
        'command': 'ls',
        'output': 'a\nb',
      }).output,
      'a\nb',
    );
    expect(
      _tool('Read', {
        'type': 'read',
        'filePath': '/a',
        'content': 'hello',
      }).output,
      'hello',
    );
  });

  test('a failed tool still says so in the app\'s words', () {
    final message = paseoItemMessage(
      'agent-1',
      {
        'type': 'tool_call',
        'callId': 'call-2',
        'name': 'mcp__x__y',
        'status': 'failed',
        'error': 'boom: stack',
        'detail': {
          'type': 'unknown',
          'output': {'output': 'secret detail'},
        },
      },
      id: 'call-2',
      provider: 'claude',
    );
    final state = message!.parts.single.toolState;
    expect(state.output, isNot(contains('secret detail')));
  });
}
