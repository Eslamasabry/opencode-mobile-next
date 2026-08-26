import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';

void main() {
  test('completed string output preserves leading and trailing whitespace', () {
    const output = '  first line\nsecond line  \n';

    final state = ToolState.fromJson(const {
      'status': 'completed',
      'output': output,
    });

    expect(state.output, output);
  });

  test('completed JSON object output remains pretty printed', () {
    final state = ToolState.fromJson(const {
      'status': 'completed',
      'output': {
        'message': 'ok',
        'details': {'count': 2},
      },
    });

    expect(
      state.output,
      '{\n'
      '  "message": "ok",\n'
      '  "details": {\n'
      '    "count": 2\n'
      '  }\n'
      '}',
    );
    expect(state.outputValue, isA<Map<String, dynamic>>());
  });

  test(
    'structured OpenCode input and metadata remain available to renderers',
    () {
      final state = ToolState.fromJson(const {
        'status': 'completed',
        'input': {
          'filePath': '/workspace/lib/main.dart',
          'offset': 12,
          'limit': 30,
        },
        'output': 'done',
        'metadata': {
          'display': {'type': 'file', 'lineStart': 12, 'lineEnd': 41},
        },
      }, toolName: 'read');

      expect(state.input['filePath'], '/workspace/lib/main.dart');
      expect(state.input['offset'], 12);
      expect(state.metadata?['display'], isA<Map>());
    },
  );

  test('ordinary URLs and path-like prose are not invented as files', () {
    final state = ToolState.fromJson(const {
      'status': 'completed',
      'output': {
        'url': 'https://opencode.ai/docs/tools',
        'message': 'See /tmp/opencode/not-an-artifact.png for background.',
      },
    }, toolName: 'websearch');

    expect(state.outputFiles, isEmpty);
  });

  test('explicit filePath output remains a downloadable artifact', () {
    final state = ToolState.fromJson(const {
      'status': 'completed',
      'output': {'filePath': '/tmp/opencode/shots/captcha-r1.png'},
    }, toolName: 'browser screenshot');

    expect(state.outputFiles, hasLength(1));
    expect(state.outputFiles.single.path, '/tmp/opencode/shots/captcha-r1.png');
  });

  test('read attachments inherit the requested file name', () {
    final state = ToolState.fromJson(const {
      'status': 'completed',
      'input': {'filePath': '/workspace/assets/diagram.png'},
      'output': 'Image read successfully',
      'attachments': [
        {'url': 'data:image/png;base64,aGVsbG8=', 'mime': 'image/png'},
      ],
    }, toolName: 'read');

    expect(state.outputFiles, hasLength(1));
    expect(state.outputFiles.single.displayName, 'diagram.png');
  });

  test('pending state exposes raw input unchanged', () {
    const raw = ' {"command":"git status"';

    final state = ToolState.fromJson(const {
      'status': 'pending',
      'raw': raw,
      'input': {'command': 'ignored'},
    });

    expect(state.inputJson, raw);
    expect(state.output, isEmpty);
  });

  test('error text preserves leading and trailing whitespace', () {
    const error = '\n  permission denied  \n';

    final state = ToolState.fromJson(const {
      'status': 'error',
      'error': error,
      'output': 'ignored',
    });

    expect(state.output, error);
  });
}
