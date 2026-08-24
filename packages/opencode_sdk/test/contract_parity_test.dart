import 'dart:io';

import 'package:test/test.dart';

void main() {
  test('generated SDK exactly matches the canonical contract matrix', () async {
    final root = _workspaceRoot();
    final result = await Process.run(Platform.resolvedExecutable, [
      'run',
      '${root.path}/tool/sdk/verify_contract_matrix.dart',
      '${root.path}/contracts/opencode-openapi-03bba464.json',
      '${root.path}/contracts/opencode-sdk-manifest.json',
      '${root.path}/packages/opencode_sdk',
      '${root.path}/contracts/opencode-sdk-matrix.json',
      '${root.path}/contracts/opencode-sdk-matrix.md',
    ]);
    expect(
      result.exitCode,
      0,
      reason: 'stdout:\n${result.stdout}\nstderr:\n${result.stderr}',
    );
    expect('${result.stdout}', contains('"verified":true'));
  });

  test('independent artifact parser proves generated parity', () async {
    final root = _workspaceRoot();
    final result = await Process.run(Platform.resolvedExecutable, [
      'run',
      '${root.path}/tool/sdk/verify_artifacts_independent.dart',
      '${root.path}/contracts/opencode-openapi-03bba464.json',
      '${root.path}/contracts/opencode-sdk-manifest.json',
      '${root.path}/packages/opencode_sdk',
      '${root.path}/contracts/opencode-sdk-matrix.json',
      '${root.path}/contracts/opencode-sdk-matrix.md',
    ]);
    expect(
      result.exitCode,
      0,
      reason: 'stdout:\n${result.stdout}\nstderr:\n${result.stderr}',
    );
    expect('${result.stdout}', contains('"independentlyVerified":true'));
  });
}

Directory _workspaceRoot() {
  var directory = Directory.current.absolute;
  while (true) {
    if (File(
      '${directory.path}/contracts/opencode-openapi-03bba464.json',
    ).existsSync()) {
      return directory;
    }
    final parent = directory.parent;
    if (parent.path == directory.path) {
      throw StateError('Could not locate the OpenCode workspace root.');
    }
    directory = parent;
  }
}
