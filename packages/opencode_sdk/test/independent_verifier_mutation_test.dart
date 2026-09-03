import 'dart:io';

import 'package:test/test.dart';

void main() {
  late Directory root;
  late Directory sandbox;

  setUpAll(() async {
    root = _workspaceRoot();
    sandbox = await Directory.systemTemp.createTemp('opencode-sdk-verifier-');
    await _copyDirectory(
      Directory('${root.path}/packages/opencode_sdk/lib'),
      Directory('${sandbox.path}/lib'),
    );
  });

  tearDownAll(() async {
    if (await sandbox.exists()) await sandbox.delete(recursive: true);
  });

  test(
    'semantic verifier rejects count-preserving artifact mutations',
    () async {
      final mutations = <_Mutation>[
        _Mutation(
          'wrapper descriptor substitution',
          'lib/src/model/event.dart',
          'EventModels-devRefreshed',
          'EventIntegrationUpdated',
          'normalized descriptor',
        ),
        _Mutation(
          'query wire-key substitution',
          'lib/src/api/sessions_api.dart',
          "r'limit': serializeOpenCodeQueryParameter(limit)",
          "r'after': serializeOpenCodeQueryParameter(limit)",
          'omits query wire key limit',
        ),
        _Mutation(
          'error key substitution',
          'lib/src/http/error_contracts.g.dart',
          'operationId: "auth.set"',
          'operationId: "auth.remove"',
          'key and value descriptors diverge',
        ),
        _Mutation(
          'decoder identity substitution',
          'lib/src/deserialize.dart',
          "case 'BadRequestError':",
          "case 'BadRequestErrorMutated':",
          'BadRequestError has no endpoint deserializer case',
        ),
        _Mutation(
          'mixed additionalProperties loss',
          'lib/src/model/config_agent.dart',
          'if (!knownKeys.contains(entry.key))',
          'if (knownKeys.contains(entry.key))',
          'does not losslessly preserve unknown entries',
        ),
      ];

      for (final mutation in mutations) {
        final file = File('${sandbox.path}/${mutation.relativePath}');
        final original = await file.readAsString();
        expect(original, contains(mutation.before), reason: mutation.name);
        await file.writeAsString(
          original.replaceFirst(mutation.before, mutation.after),
        );
        final result = await _verify(root, sandbox);
        expect(result.exitCode, isNot(0), reason: mutation.name);
        expect(
          '${result.stderr}${result.stdout}',
          contains(mutation.diagnostic),
          reason: mutation.name,
        );
        await file.writeAsString(original);
      }
    },
    timeout: const Timeout(Duration(minutes: 3)),
  );
}

Future<ProcessResult> _verify(Directory root, Directory package) =>
    Process.run(Platform.resolvedExecutable, [
      'run',
      '${root.path}/tool/sdk/verify_artifacts_independent.dart',
      '--skip-source-hashes',
      '${root.path}/contracts/opencode-openapi-f12e14cf.json',
      '${root.path}/contracts/opencode-sdk-manifest.json',
      package.path,
      '${root.path}/contracts/opencode-sdk-matrix.json',
      '${root.path}/contracts/opencode-sdk-matrix.md',
    ]);

Future<void> _copyDirectory(Directory source, Directory destination) async {
  await destination.create(recursive: true);
  await for (final entity in source.list(recursive: true, followLinks: false)) {
    final relative = entity.path.substring(source.path.length + 1);
    if (entity is Directory) {
      await Directory('${destination.path}/$relative').create(recursive: true);
    } else if (entity is File) {
      final target = File('${destination.path}/$relative');
      await target.parent.create(recursive: true);
      await entity.copy(target.path);
    }
  }
}

Directory _workspaceRoot() {
  var directory = Directory.current.absolute;
  while (true) {
    if (File(
      '${directory.path}/contracts/opencode-openapi-f12e14cf.json',
    ).existsSync()) {
      return directory;
    }
    if (directory.parent.path == directory.path) {
      throw StateError('Could not locate the OpenCode workspace root.');
    }
    directory = directory.parent;
  }
}

class _Mutation {
  const _Mutation(
    this.name,
    this.relativePath,
    this.before,
    this.after,
    this.diagnostic,
  );

  final String name;
  final String relativePath;
  final String before;
  final String after;
  final String diagnostic;
}
