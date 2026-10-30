// What a Claude Code tool answered reaches the tool card, in each of the
// shapes Paseo 0.9.1 sends (claude/agent.js buildToolOutput and
// tool-call-detail-primitives.js).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
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

  group('a call the person declined at its permission card', () {
    ToolState failed(Object error) => paseoItemMessage(
      'agent-1',
      {
        'type': 'tool_call',
        'callId': 'call-3',
        'name': 'Write',
        'status': 'failed',
        'error': error,
        'detail': {'type': 'write', 'filePath': 'index.html'},
      },
      id: 'call-3',
      provider: 'claude',
    )!.parts.single.toolState;

    for (final error in <Object>[
      {'message': 'Permission denied'},
      'Permission request denied',
      "The user doesn't want to proceed with this tool use. STOP.",
    ]) {
      test('reads as declined, not failed: $error', () {
        expect(failed(error).declined, isTrue);
      });
    }

    test('a real failure stays a failure', () {
      expect(failed({'message': 'ENOSPC: disk full'}).declined, isFalse);
    });
  });

  // Owner phone, 2026-10-27: Paseo keeps how a background task ended in the
  // call's metadata, and repeats Claude's sentence as the call's text.
  test('a background-task notice carries how it ended, its sentence once', () {
    const sentence =
        'Background agent "Fix login" didn\'t finish before the previous '
        'session ended';
    final state = paseoItemMessage(
      'agent-1',
      {
        'type': 'tool_call',
        'callId': 'task_notification_1',
        'name': 'task_notification',
        'status': 'completed',
        'detail': {
          'type': 'plain_text',
          'label': sentence,
          'icon': 'wrench',
          'text': sentence,
        },
        'metadata': {
          'synthetic': true,
          'source': 'claude_task_notification',
          'taskId': 'a1b2c3d4e5',
          'status': 'stopped',
        },
      },
      id: 'task_notification_1',
      provider: 'claude',
    )!.parts.single.toolState;
    expect(state.input, {
      'summary': sentence,
      'status': 'stopped',
      // Which task ended, so the chat can stop saying it still runs.
      'taskId': 'a1b2c3d4e5',
    });
    expect(state.output, isEmpty);
  });

  test("a notice's raw envelope gives only a finished agent's result", () {
    final state = paseoItemMessage(
      'agent-1',
      {
        'type': 'tool_call',
        'callId': 'task_notification_2',
        'name': 'task_notification',
        'status': 'completed',
        'detail': {
          'type': 'plain_text',
          'label': 'Background task completed',
          'text':
              '<task-notification><task-id>t1</task-id>'
              '<status>completed</status><result>All 12 suites pass.</result>'
              '</task-notification>',
        },
        'metadata': {'status': 'completed'},
      },
      id: 'task_notification_2',
      provider: 'claude',
    )!.parts.single.toolState;
    expect(state.input, {'status': 'completed'});
    expect(state.output, 'All 12 suites pass.');
  });
}
