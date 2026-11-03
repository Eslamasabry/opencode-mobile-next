import '../../termux/bridge.dart';
import '../../ui/kit/kit_redact.dart';
import '../builtin_linux.dart';

/// Setup-only transport for the shared component engine. Commands run inside
/// Termux's Ubuntu through RUN_COMMAND, never inside the app's built-in Linux.
///
/// The native host owns durable status and cancellation. Permission failures
/// remain failures; this adapter never grants permission or substitutes hosts.
/// Other [BuiltinLinux] operations are not part of this adapter's contract.
class TermuxSetupHost extends BuiltinLinux {
  /// Rejects credentials and unsupported configuration before scripts are built.
  /// Setup is installation metadata only; authentication belongs to profiles.
  static void validateParams(Map<String, Map<String, String>> params) {
    const allowed = {
      'opencode': {'runtime', 'version'},
      '_job': {'first', 'adding'},
    };
    for (final group in params.entries) {
      final keys = allowed[group.key];
      if (keys == null) _invalidParams();
      for (final entry in group.value.entries) {
        if (!keys.contains(entry.key) ||
            KitRedact.containsSecret(entry.value)) {
          _invalidParams();
        }
        final valid = switch (entry.key) {
          'runtime' => {'opencode2'}.contains(entry.value),
          'version' => RegExp(
            r'^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$',
          ).hasMatch(entry.value),
          'first' => {'0', '1'}.contains(entry.value),
          'adding' =>
            entry.value.isEmpty ||
                RegExp(
                  r'^[a-z][a-z0-9_-]*(?:,[a-z][a-z0-9_-]*)*$',
                ).hasMatch(entry.value),
          _ => false,
        };
        if (!valid) _invalidParams();
      }
    }
  }

  static Never _invalidParams() => throw const BuiltinLinuxException(
    'Termux setup parameters are invalid or contain credentials.',
    code: 'invalid_setup',
  );

  @override
  Future<BuiltinLinuxStatus> status() => _translate(() async {
    final installed = await TermuxBridge.setupHostInstalled();
    return BuiltinLinuxStatus(
      installed: installed,
      phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    );
  });

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) => _translate(() async {
    // Setup scripts never carry secrets; refuse rather than drop the input.
    if (input != null) {
      throw const BuiltinLinuxException(
        'This step is not available here.',
        code: 'termux_input_unsupported',
      );
    }
    final result = await TermuxBridge.setupRun(script, timeout: timeout);
    // A failed check is distinct from Termux failing to execute any command.
    if (result.errorCode != -1) {
      throw BuiltinLinuxException(
        KitRedact.text(result.failureMessage),
        code: 'termux_execution_failed',
      );
    }
    return BuiltinLinuxRunResult(
      exitCode: result.exitCode,
      output: result.stdout,
    );
  });

  @override
  Future<void> startSetup({
    required String jobId,
    required List<Map<String, Object?>> components,
    required Map<String, Map<String, String>> params,
    required Map<String, String> texts,
  }) => _translate(
    () => TermuxBridge.startSetup(
      jobId: jobId,
      components: components,
      params: params,
      texts: texts,
    ),
  );

  @override
  Future<String?> setupStatus() => _translate(TermuxBridge.setupStatus);

  @override
  Future<void> cancelSetup() => _translate(TermuxBridge.cancelSetup);

  @override
  Future<void> completeSetupStep({
    required String jobId,
    required String id,
    required bool ok,
    String? error,
    String? version,
  }) => _translate(
    () => TermuxBridge.completeSetupStep(
      jobId: jobId,
      id: id,
      ok: ok,
      error: error == null ? null : KitRedact.text(error),
      version: version == null ? null : KitRedact.text(version),
    ),
  );

  Future<T> _translate<T>(Future<T> Function() action) async {
    try {
      return await action();
    } on TermuxBridgeException catch (error) {
      throw BuiltinLinuxException(
        KitRedact.text(error.message),
        code: error.code,
      );
    }
  }
}
