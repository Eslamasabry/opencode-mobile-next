import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('release script enforces the fail-closed publish contract', () async {
    final result = await Process.run('bash', [
      'test/release_script_test.sh',
    ], workingDirectory: Directory.current.path);

    expect(
      result.exitCode,
      0,
      reason: 'stdout:\n${result.stdout}\nstderr:\n${result.stderr}',
    );
    expect(result.stdout, contains('PASS: release script safety contract'));
  });
}
