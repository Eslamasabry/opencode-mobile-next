import '../paseo/gateway.dart';
import '../paseo/transport.dart';
import 'codex_connection_probe.dart';
import 'profiles.dart';

typedef PaseoConnectionGatewayFactory =
    PaseoGateway Function({
      required String baseUrl,
      required String password,
      required String directory,
    });

const _paseoProbeFailure = 'Could not verify the Paseo daemon.';

/// Checks a Paseo daemon and project scope without starting an agent.
///
/// Validation runs before the gateway factory so malformed input cannot open a
/// socket. The handshake proves the address, host allowlist and password; the
/// one-item agent page proves a scoped read; the runtime list says which agent
/// CLIs the daemon can drive. An empty list does not prove the folder exists.
/// The result reuses the Codex probe shape so the editor shows one verdict.
Future<CodexConnectionProbeResult> probePaseoConnection({
  required String baseUrl,
  required String password,
  required String directory,
  PaseoConnectionGatewayFactory? gatewayFactory,
}) async {
  final normalizedUrl = normalizePaseoServerUrl(baseUrl);
  final error =
      validatePaseoServerUrl(normalizedUrl) ??
      validateCodexProjectDirectory(directory.trim()) ??
      validatePaseoPassword(password);
  if (error != null) {
    return CodexConnectionProbeResult(ok: false, message: error);
  }

  PaseoGateway? gateway;
  try {
    final createGateway =
        gatewayFactory ??
        ({
          required String baseUrl,
          required String password,
          required String directory,
        }) => PaseoGateway.connect(
          baseUrl: baseUrl,
          password: password,
          directory: directory,
        );
    gateway = createGateway(
      baseUrl: normalizedUrl,
      password: password,
      directory: directory.trim(),
    );
    await gateway.health();
    await gateway.sessionPage(limit: 1);
    final runtimes = (await gateway.providers()).providers
        .map((provider) => provider.name)
        .toList();
    return CodexConnectionProbeResult(
      ok: true,
      message: runtimes.isEmpty
          ? 'Paseo daemon verified. It reports no ready agent runtimes yet.'
          : 'Paseo daemon verified. Ready: ${runtimes.join(', ')}.',
      // The daemon's own number, not the gateway's display line.
      version: gateway.transport.serverVersion,
      runtimes: runtimes,
    );
  } on PaseoFailure catch (error) {
    return CodexConnectionProbeResult(ok: false, message: error.message);
  } catch (_) {
    return const CodexConnectionProbeResult(
      ok: false,
      message: _paseoProbeFailure,
    );
  } finally {
    try {
      gateway?.close();
    } catch (_) {
      // A cleanup failure must not replace the connection verdict.
    }
  }
}
