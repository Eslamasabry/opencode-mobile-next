import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/agent_handoff_record.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';

MessageWithParts message(
  String role,
  String text, {
  List<Part> extra = const [],
  String? error,
}) => MessageWithParts(
  info: MessageInfo(
    id: role,
    sessionID: 'source',
    role: role,
    errorText: error,
  ),
  parts: [
    Part(type: 'text', text: text),
    ...extra,
  ],
);

HandoffRecord record(List<MessageWithParts> messages, {int maxBytes = 98304}) =>
    buildAgentHandoffRecord(
      session: Session(
        id: 'source',
        title: 'Fix the parser',
        directory: '/repo',
      ),
      messages: messages,
      lastState: 'waiting for you',
      maxBytes: maxBytes,
    );

void main() {
  setUp(KitRedact.clearKnownSecrets);
  tearDown(KitRedact.clearKnownSecrets);

  test('the record stays the same when the writers mask it again', () {
    // A phone chat's file list: the first path is long enough to be masked,
    // and the quoted line break turns the next one into a 32-character run.
    final built = record([
      message(
        'assistant',
        'Listed the files',
        extra: [
          Part(
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState(
              status: 'completed',
              output:
                  'lib/state/app_state_premium.dart\n'
                  'lib/l10n/app_localizations_ar.dart\n'
                  'lib/l10n/app_localizations.dart\n',
            ),
          ),
        ],
      ),
    ]);
    expect(KitRedact.opaqueTokens(built.markdown), built.markdown);
    expect(built.redacted, isTrue);
    expect(built.byteLength, utf8.encode(built.markdown).length);
  });

  test('retains exact user text and assistant text in source order', () {
    const prompt = '  Fix this\n```dart\nfinal answer = 42;\n```\nالعربية 🚀';
    final result = record([
      message('user', prompt),
      message('assistant', 'I changed the parser.'),
      message('user', 'Now check empty input.'),
    ]);
    expect(result.markdown, contains(prompt));
    expect(
      result.markdown.indexOf(prompt),
      lessThan(result.markdown.indexOf('I changed the parser.')),
    );
    expect(
      result.markdown.indexOf('I changed the parser.'),
      lessThan(result.markdown.indexOf('Now check empty input.')),
    );
    expect(result.markdown, contains('````\n$prompt\n````'));
    expect(result.byteLength, utf8.encode(result.markdown).length);
    expect(result.omittedMessages, 0);
    expect(result.redacted, isFalse);
  });

  test('copies tool commands, paths and outcomes but no arbitrary fields', () {
    final result = record([
      message(
        'assistant',
        'Implemented.',
        extra: [
          Part(type: 'reasoning', text: 'private chain of thought'),
          Part(type: 'text', text: 'synthetic context', synthetic: true),
          Part(
            type: 'tool',
            toolName: 'bash',
            toolState: ToolState(
              status: 'error',
              input: {
                'command': 'dart test',
                'filePath': 'test/parser_test.dart',
                'cwd': '/repo',
                'config': {'unsafe': 'raw input must not leak'},
              },
              inputJson: 'raw input json must not leak',
              output: 'One assertion failed',
              outputValue: {'hidden': 'raw output must not leak'},
              metadata: {'exitCode': 1, 'private': 'metadata must not leak'},
            ),
          ),
        ],
      ),
    ]);
    for (final included in [
      'dart test',
      'test/parser_test.dart',
      '/repo',
      'exitCode: 1',
      'One assertion failed',
      'status: "error"',
    ]) {
      expect(result.markdown, contains(included));
    }
    for (final excluded in [
      'private chain of thought',
      'synthetic context',
      'raw input must not leak',
      'raw input json must not leak',
      'raw output must not leak',
      'metadata must not leak',
    ]) {
      expect(result.markdown, isNot(contains(excluded)));
    }
  });

  test('records current task and file evidence without source or patch', () {
    final result = buildAgentHandoffRecord(
      session: Session(id: 'source'),
      messages: [message('assistant', 'Stopped', error: 'Build failed')],
      todos: [
        Todo(content: 'Fix parsing', status: 'completed'),
        Todo(content: 'Check empty input', status: 'pending', priority: 'high'),
      ],
      files: [
        FileDiff(
          file: 'lib/parser.dart',
          additions: 3,
          deletions: 1,
          before: 'old source excluded',
          after: 'new source excluded',
          patch: 'patch excluded',
          status: 'modified',
        ),
      ],
      lastState: 'failed',
      historyComplete: false,
    );
    expect(result.markdown, contains('[x] "Fix parsing"'));
    expect(result.markdown, contains('[ ] "Check empty input"'));
    expect(result.markdown, contains('lib/parser.dart'));
    expect(result.markdown, contains('+3 -1'));
    expect(result.markdown, contains('Last state: "failed"'));
    expect(result.markdown, contains('Build failed'));
    expect(result.markdown, contains('History coverage: INCOMPLETE'));
    expect(result.markdown, isNot(contains('source excluded')));
    expect(result.markdown, isNot(contains('patch excluded')));
  });

  test(
    'drops whole older turns and counts UTF-8 bytes, keeping newest full',
    () {
      final old = List.filled(3000, 'a').join();
      final newest = List.filled(200, '界').join();
      final result = record([
        message('user', old),
        message('assistant', 'Old answer'),
        message('user', newest),
        message('assistant', 'Newest answer'),
      ], maxBytes: 2200);
      expect(result.omittedMessages, 2);
      expect(result.markdown, isNot(contains('Old answer')));
      expect(result.markdown, contains(newest));
      expect(result.markdown, contains('Newest answer'));
      expect(result.byteLength, lessThanOrEqualTo(2200));
      expect(result.byteLength, utf8.encode(result.markdown).length);
      expect(result.markdown, contains('Older messages omitted for size: 2.'));
    },
  );

  test('never silently truncates an oversized newest turn', () {
    expect(
      () => record([
        message('user', List.filled(2000, '界').join()),
        message('assistant', 'The answer belongs to that question.'),
      ], maxBytes: 2200),
      throwsA(
        isA<HandoffRecordTooLarge>()
            .having((e) => e.requiredBytes, 'requiredBytes', greaterThan(2200))
            .having((e) => e.maxBytes, 'maxBytes', 2200),
      ),
    );
  });

  test(
    'exact byte boundary fits and one byte less cannot lose newest turn',
    () {
      final messages = [message('user', 'Keep all of this.')];
      final length = record(messages).byteLength;
      expect(record(messages, maxBytes: length).byteLength, length);
      expect(
        () => record(messages, maxBytes: length - 1),
        throwsA(isA<HandoffRecordTooLarge>()),
      );
      expect(() => record([], maxBytes: 0), throwsArgumentError);
      expect(
        () => record([], maxBytes: 1),
        throwsA(isA<HandoffRecordTooLarge>()),
      );
    },
  );

  test('masks every exported untrusted field, including user prompts', () {
    const secret = 'registered-private-credential';
    const opaque = 'a1bcdefghijklmnopqrstuvwxyz0123456789';
    KitRedact.registerKnownSecret(secret);
    final result = buildAgentHandoffRecord(
      session: Session(
        id: secret,
        title: secret,
        directory: '/repo/$secret',
        agent: secret,
        model: secret,
      ),
      messages: [
        message('user', 'Use $secret and Bearer abc123 and $opaque'),
        message(
          'assistant',
          'api_key=abcdefghi',
          error: secret,
          extra: [
            Part(type: 'file', filename: secret, url: 'https://unsafe/$secret'),
            Part(
              type: 'tool',
              toolName: secret,
              toolState: ToolState(
                status: secret,
                input: {'command': secret, 'path': secret},
                output: 'sk-ant-123456789abcdef and $opaque and $secret',
              ),
            ),
          ],
        ),
      ],
      todos: [Todo(content: secret, status: secret, priority: secret)],
      files: [FileDiff(file: secret, status: secret)],
      lastState: secret,
    );
    expect(result.redacted, isTrue);
    for (final value in [
      secret,
      opaque,
      'abc123',
      'abcdefghi',
      '123456789abcdef',
    ]) {
      expect(result.markdown, isNot(contains(value)));
    }
    expect(result.markdown, contains(KitRedact.mask));
  });

  test('redacts before compacting long tool output', () {
    final secret = List.filled(700, 'a').join();
    final result = record([
      message(
        'assistant',
        'Tool failed',
        extra: [
          Part(
            type: 'tool',
            toolName: 'shell',
            toolState: ToolState(
              status: 'error',
              output: 'password="$secret"',
              input: {'command': List.filled(700, 'z').join()},
              executed: false,
              pruned: true,
            ),
          ),
        ],
      ),
    ]);
    expect(result.markdown, isNot(contains('aaaa')));
    expect(result.markdown, contains('[summary shortened]'));
    expect(result.markdown, contains('not executed'));
    expect(result.markdown, contains('original output was pruned'));
    expect(result.redacted, isTrue);
  });

  test(
    'uses newest completed task snapshot with allowlisted redacted fields',
    () {
      Part tasks(List<Object?> rows, {String status = 'completed'}) => Part(
        type: 'tool',
        toolName: 'TodoWrite',
        toolState: ToolState(status: status, input: {'todos': rows}),
      );
      final result = record([
        message(
          'assistant',
          '',
          extra: [
            tasks([
              {'content': 'Old task', 'status': 'pending'},
            ]),
          ],
        ),
        message(
          'assistant',
          '',
          extra: [
            tasks([
              {
                'content': 'Fix parser with api_key=abc12345',
                'status': 'in_progress',
                'priority': 'high',
                'raw': 'private map field',
              },
              {'content': 'Reviewed', 'status': 'completed'},
              {'content': 42, 'status': 'pending'},
            ]),
          ],
        ),
        message(
          'assistant',
          '',
          extra: [
            tasks([
              {'content': 'Not executed task', 'status': 'pending'},
            ], status: 'error'),
          ],
        ),
      ]);
      expect(
        result.markdown,
        contains('latest completed task-list tool snapshot'),
      );
      expect(result.markdown, contains('Fix parser with api_key='));
      expect(result.markdown, contains('in_progress'));
      expect(result.markdown, contains('[x] "Reviewed"'));
      for (final omitted in [
        'Old task',
        'Not executed task',
        'abc12345',
        'private map field',
      ]) {
        expect(result.markdown, isNot(contains(omitted)));
      }
      expect(result.redacted, isTrue);
    },
  );

  test('empty latest task snapshot clears older tasks', () {
    Part tasks(List<Object?> rows) => Part(
      type: 'tool',
      toolName: 'todowrite',
      toolState: ToolState(status: 'completed', input: {'todos': rows}),
    );
    final result = record([
      message(
        'assistant',
        '',
        extra: [
          tasks([
            {'content': 'Old task', 'status': 'pending'},
          ]),
        ],
      ),
      message('assistant', '', extra: [tasks([])]),
    ]);
    expect(result.markdown, contains('No tasks reported.'));
    expect(result.markdown, isNot(contains('Old task')));
  });

  test(
    'retains observed write paths from omitted turns without claiming ownership of diffs',
    () {
      final result = record([
        message('user', List.filled(5000, 'a').join()),
        message(
          'assistant',
          '',
          extra: [
            Part(
              type: 'tool',
              toolName: 'edit',
              toolState: ToolState(
                status: 'completed',
                input: {'filePath': 'lib/committed_parser.dart'},
              ),
            ),
            Part(
              type: 'tool',
              toolName: 'write',
              toolState: ToolState(
                status: 'error',
                input: {'filePath': 'lib/failed_write.dart'},
              ),
            ),
          ],
        ),
        message('user', 'Continue the remaining work.'),
      ], maxBytes: 2800);
      expect(result.omittedMessages, 2);
      expect(result.markdown, contains('lib/committed_parser.dart'));
      expect(result.markdown, isNot(contains('lib/failed_write.dart')));
      expect(
        result.markdown,
        contains('does not establish which conversation changed a file'),
      );
      expect(result.markdown, contains('including older turns omitted below'));
    },
  );

  test('bounds observed path metadata and masks credentials in paths', () {
    final result = record([
      message(
        'assistant',
        '',
        extra: [
          for (var i = 0; i < 257; i++)
            Part(
              type: 'tool',
              toolName: 'write',
              toolState: ToolState(
                status: 'completed',
                input: {'path': 'file_$i.dart'},
              ),
            ),
        ],
      ),
    ]);
    expect(result.markdown, contains('Older distinct tool paths omitted: 1.'));
    final header = result.markdown.split('## Conversation').first;
    expect(header, contains('file_256.dart'));
    expect(header, isNot(contains('"file_0.dart"')));
    KitRedact.registerKnownSecret('private-file-name');
    final masked = record([
      message(
        'assistant',
        '',
        extra: [
          Part(
            type: 'tool',
            toolName: 'edit',
            toolState: ToolState(
              status: 'completed',
              input: {'path': '/repo/private-file-name'},
            ),
          ),
        ],
      ),
    ]);
    expect(masked.markdown, isNot(contains('private-file-name')));
  });

  test('compaction notice does not split the newest actual user turn', () {
    final latestQuestion = List.filled(3000, 'a').join();
    expect(
      () => record([
        message('user', latestQuestion),
        MessageWithParts(
          info: MessageInfo(id: 'compact', sessionID: 'source', role: 'user'),
          parts: [Part(type: 'v2:compaction', text: 'Conversation compacted.')],
        ),
        message('assistant', 'The answer belongs to the latest question.'),
      ], maxBytes: 2200),
      throwsA(isA<HandoffRecordTooLarge>()),
    );
    final result = record([
      message('user', 'Keep this question.'),
      MessageWithParts(
        info: MessageInfo(id: 'compact', sessionID: 'source', role: 'user'),
        parts: [Part(type: 'v2:compaction')],
      ),
    ]);
    expect(result.markdown, contains('Source notice'));
    expect(result.markdown, contains('conversation compaction event'));
  });
}
