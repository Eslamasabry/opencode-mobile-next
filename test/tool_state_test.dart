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
