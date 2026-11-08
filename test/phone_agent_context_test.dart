import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/phone_agent_context.dart';

/// Runs [script] with `sh` (dash on Ubuntu, as inside the phone's Ubuntu)
/// with every path under [root].
Future<void> _run(String script, Directory root) async {
  final result = await Process.run(
    'sh',
    ['-c', script],
    environment: {'OC_CTX_ROOT': root.path},
  );
  expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
}

void main() {
  late Directory root;
  setUp(() => root = Directory.systemTemp.createTempSync('oc-ctx-'));
  tearDown(() => root.deleteSync(recursive: true));

  File file(String path) => File('${root.path}$path');

  test('writes the phone context into both OpenCode generations\' global '
      'AGENTS.md', () async {
    await _run(PhoneAgentContext.builtinScript, root);
    for (final path in PhoneAgentContext.files) {
      final text = file(path).readAsStringSync();
      expect(text, PhoneAgentContext.block(PhoneAgentContext.builtinHost));
      // What the agent on the owner's phone got wrong (2026-09-24).
      expect(text, contains("person's Android phone"));
      expect(text, contains('no desktop app'));
      expect(text, contains('no adb connection'));
    }
  });

  test('replaces its own block on every start and keeps the person\'s '
      'text', () async {
    final global = file(PhoneAgentContext.files.first)
      ..createSync(recursive: true)
      ..writeAsStringSync(
        // The block an older build wrote for its server in Termux.
        '# My rules\n\nUse tabs.\n\n'
        '${PhoneAgentContext.block('Ubuntu running in Termux (under proot)')}',
      );
    await _run(PhoneAgentContext.builtinScript, root);
    // A second start swaps the block again, never adds a second.
    await _run(PhoneAgentContext.builtinScript, root);
    final text = global.readAsStringSync();
    expect(
      text,
      '# My rules\n\nUse tabs.\n\n'
      '${PhoneAgentContext.block(PhoneAgentContext.builtinHost)}',
    );
    expect(PhoneAgentContext.begin.allMatches(text), hasLength(1));
  });

  test(
    'text the person adds after the block survives the next start',
    () async {
      await _run(PhoneAgentContext.builtinScript, root);
      final global = file(PhoneAgentContext.files.last);
      global.writeAsStringSync('${global.readAsStringSync()}\nMore rules.\n');
      await _run(PhoneAgentContext.builtinScript, root);
      final text = global.readAsStringSync();
      expect(text, startsWith('More rules.\n\n${PhoneAgentContext.begin}'));
      expect(PhoneAgentContext.begin.allMatches(text), hasLength(1));
    },
  );

  test('the phone server writes it before OpenCode starts, and a failure '
      'never stops the server', () {
    for (final runtime in [OpenCodeRuntime.openCode2]) {
      final script = BuiltinLinux.serverScript(runtime: runtime);
      final context = script.indexOf(PhoneAgentContext.begin);
      expect(context, greaterThan(0), reason: '$runtime');
      expect(context, lessThan(script.indexOf('exec ')));
      expect(script, contains('|| true'));
    }
  });
}
