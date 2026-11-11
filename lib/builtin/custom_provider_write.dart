import '../domain/server_gateway.dart';
import 'builtin_linux.dart';
import 'custom_provider_script.dart';

export 'custom_provider_script.dart' show CustomProviderWriteOutcome;

/// Writes a provider declaration into the phone engine's own config file.
///
/// Kept behind an interface for the same reason as the Gen UI installer: the
/// connection controller must not reach an Android channel that a widget test
/// cannot answer.
abstract interface class CustomProviderWriter {
  Future<CustomProviderWriteOutcome> write({
    required String providerID,
    required Map<String, Object?> block,
  });
}

/// The real writer: one fixed script in the in-app Ubuntu.
final class BuiltinCustomProviderWriter implements CustomProviderWriter {
  BuiltinCustomProviderWriter({BuiltinLinux? linux})
    : _linux = linux ?? BuiltinLinux();

  final BuiltinLinux _linux;

  @override
  Future<CustomProviderWriteOutcome> write({
    required String providerID,
    required Map<String, Object?> block,
  }) async {
    final script = customProviderWriteScript(
      providerID: providerID,
      block: block,
    );
    final result = await _linux.run(script);
    // The exit status is the whole result protocol; stdout is never parsed.
    return customProviderWriteOutcome(result.exitCode);
  }
}

/// The person-facing reason a write left the config unchanged. A saved write
/// throws nothing: only a config already carrying the provider is a conflict.
ProductException customProviderWriteFailure(CustomProviderWriteOutcome value) =>
    switch (value) {
      CustomProviderWriteOutcome.saved => throw ArgumentError(
        'A saved write is not a failure',
      ),
      CustomProviderWriteOutcome.notSetUp => const ProductException(
        'The phone server is not set up yet. Finish setup, then try again.',
      ),
      CustomProviderWriteOutcome.unusableConfig => const ProductException(
        'The server configuration could not be read as JSON, so nothing was '
        'changed. Fix it on the server, then try again.',
      ),
      CustomProviderWriteOutcome.unsafePath => const ProductException(
        'The server configuration is not owned by the server account, so '
        'nothing was changed.',
      ),
      CustomProviderWriteOutcome.busy => const ProductException(
        'Another configuration change is in progress. Try again in a moment.',
      ),
      CustomProviderWriteOutcome.alreadyExists => const ProductException(
        'A provider with this ID is already declared on the server.',
      ),
    };
