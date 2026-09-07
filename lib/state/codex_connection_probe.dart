import '../codex/gateway.dart';
import '../codex/transport.dart';
import 'profiles.dart';

/// The product-facing result of checking a Codex app-server connection.
class CodexConnectionProbeResult {
  final bool ok;
  final String message;
  final String? version;

  const CodexConnectionProbeResult({
    required this.ok,
    required this.message,
    this.version,
  });
}

typedef CodexConnectionGatewayFactory =
    CodexGateway Function({
      required String baseUrl,
      required String token,
      required String directory,
    });

const _codexProbeSuccess = 'Codex connection verified.';
const _codexProbeFailure = 'Could not verify the Codex connection.';

/// Checks a Codex endpoint and project scope without creating a turn.
///
/// Validation runs before the gateway factory so malformed input cannot open a
/// socket. The health check proves authentication and the one-item session
/// page checks a scoped read. An empty list does not prove the folder exists.
/// The gateway is always closed after construction, including failed probes.
Future<CodexConnectionProbeResult> probeCodexConnection({
  required String baseUrl,
  required String token,
  required String directory,
  CodexConnectionGatewayFactory? gatewayFactory,
}) async {
  final normalizedUrl = normalizeCodexServerUrl(baseUrl);
  final urlError = validateCodexServerUrl(normalizedUrl);
  if (urlError != null) {
    return CodexConnectionProbeResult(ok: false, message: urlError);
  }
  final directoryError = validateCodexProjectDirectory(directory);
  if (directoryError != null) {
    return CodexConnectionProbeResult(ok: false, message: directoryError);
  }
  final tokenError = validateCodexConnectionToken(token);
  if (tokenError != null) {
    return CodexConnectionProbeResult(ok: false, message: tokenError);
  }

  CodexGateway? gateway;
  try {
    final createGateway =
        gatewayFactory ??
        ({
          required String baseUrl,
          required String token,
          required String directory,
        }) => CodexGateway.connect(
          baseUrl: baseUrl,
          token: token,
          directory: directory,
        );
    gateway = createGateway(
      baseUrl: normalizedUrl,
      token: token,
      directory: directory.trim(),
    );
    final health = await gateway.health();
    if (!health.healthy) {
      return const CodexConnectionProbeResult(
        ok: false,
        message: _codexProbeFailure,
      );
    }
    await gateway.sessionPage(limit: 1);
    return CodexConnectionProbeResult(
      ok: true,
      message: _codexProbeSuccess,
      version: health.version,
    );
  } on CodexFailure catch (error) {
    return CodexConnectionProbeResult(ok: false, message: error.message);
  } catch (_) {
    return const CodexConnectionProbeResult(
      ok: false,
      message: _codexProbeFailure,
    );
  } finally {
    try {
      gateway?.close();
    } catch (_) {
      // A cleanup failure must not replace the connection verdict.
    }
  }
}
