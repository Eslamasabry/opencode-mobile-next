import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

void main() {
  test('Claude\'s sub-agent tool is the app\'s sub-agent card', () {
    expect(paseoToolName('Agent', const {'type': 'unknown'}), 'task');
    expect(paseoToolName('Task', const {'type': 'unknown'}), 'task');
  });

  test('other tools keep their names', () {
    expect(paseoToolName('Bash', const {'type': 'shell'}), 'bash');
    expect(paseoToolName('TodoWrite', const {'type': 'unknown'}), 'todowrite');
    expect(paseoToolName('', const {}), 'tool');
  });
}
