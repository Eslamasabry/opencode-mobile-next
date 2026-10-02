// Local initialize-only probe. Does not authenticate, prompt or install agents.
// Example: dart run tool/acp_smoke.dart -- gemini --acp
import 'dart:async';
import 'dart:io';

import 'package:opencode_mobile/acp/acp_client.dart';

Future<void> main(List<String> args) async {
  if (args.length < 2 || args.first != '--') {
    stderr.writeln(
      'Usage: dart run tool/acp_smoke.dart -- <agent> [arguments]',
    );
    exitCode = 64;
    return;
  }
  final directory = await Directory.systemTemp.createTemp('oc-acp-smoke-');
  Process? process;
  AcpClient? client;
  StreamSubscription<List<int>>? errors;
  try {
    // Explicit argv, never a shell. Empty cwd avoids repository agent hooks.
    process = await Process.start(
      args[1],
      args.skip(2).toList(),
      workingDirectory: directory.path,
      runInShell: false,
      environment: {'GEMINI_CLI_NO_RELAUNCH': 'true'},
    );
    errors = process.stderr.listen((_) {}, onError: (Object _) {});
    final stdin = process.stdin;
    client = AcpClient(process.stdout, (bytes) async {
      stdin.add(bytes);
      await stdin.flush();
    }, requestTimeout: const Duration(seconds: 20));
    final result = await client.initialize();
    // Output only locally derived primitive metadata, never agent strings.
    stdout.writeln(
      'ACP handshake passed (protocol ${result.protocolVersion}).',
    );
    stdout.writeln(
      'Details: loadSession=${result.capabilities.loadSession}; '
      'authMethods=${result.authMethods.length}; no authentication or prompt sent.',
    );
  } on AcpException catch (error) {
    stderr.writeln('ACP handshake failed. Details: ${error.failure.name}.');
    exitCode = 1;
  } catch (_) {
    stderr.writeln(
      'Could not run the local agent. Details: process or I/O failure.',
    );
    exitCode = 1;
  } finally {
    await client?.dispose();
    if (process != null) {
      // Kill only the exact child we created. No command-pattern process kills.
      process.kill(ProcessSignal.sigterm);
      try {
        await process.exitCode.timeout(const Duration(seconds: 2));
      } catch (_) {
        process.kill(ProcessSignal.sigkill);
        try {
          await process.exitCode.timeout(const Duration(seconds: 2));
        } catch (_) {
          stderr.writeln(
            'Details: local agent termination could not be confirmed.',
          );
          exitCode = 1;
        }
      }
      try {
        await process.stdin.close().timeout(const Duration(seconds: 2));
      } catch (_) {}
    }
    await errors?.cancel();
    try {
      await directory.delete(recursive: true);
    } catch (_) {
      stderr.writeln('Details: temporary directory cleanup failed.');
      exitCode = 1;
    }
  }
}
