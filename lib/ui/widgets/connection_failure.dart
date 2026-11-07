/// Turns a failed connection into something a person can act on.
///
/// The transport tells us *what* failed ("connection refused", "timed out",
/// HTTP 401). The address tells us *where* the server was supposed to be. Put
/// together they usually point at one thing the user can check, and one
/// button that gets them there. Retrying a server that is not running is
/// never that button.
library;

import '../../domain/server_probe.dart' show openCode2UpgradeCommand;
import '../../l10n/app_localizations.dart';
import '../../l10n/app_localizations_en.dart';

enum ConnectionFailureAction {
  retry,

  /// Start the app's own server on this phone, then connect.
  startPhoneServer,
  updatePassword,
  updateToken,
  changeServer,
  reviewCertificate,
}

class ConnectionFailure {
  const ConnectionFailure({
    required this.title,
    required this.explanation,
    required this.checks,
    required this.primary,
    required this.rawError,
    this.tailnet = false,
  });

  /// Short headline, e.g. "Nothing answered on this phone".
  final String title;

  /// One or two sentences that say what the failure means.
  final String explanation;

  /// Plain-language things to check, most likely first.
  final List<String> checks;

  /// The one action most likely to fix it.
  final ConnectionFailureAction primary;

  /// The verbatim error, for the Details expander.
  final String rawError;

  /// The address is on a Tailscale network (a 100.64.0.0/10 address or a
  /// `.ts.net` name) and nothing answered there: Tailscale being off on this
  /// phone is the likely cause, so the page offers to set it up.
  final bool tailnet;

  /// A Tailscale address: 100.64.0.0/10 or a MagicDNS `*.ts.net` name.
  static bool isTailnetHost(String host) {
    final lower = host.toLowerCase();
    if (lower.endsWith('.ts.net')) return true;
    final parts = lower.split('.');
    if (parts.length != 4) return false;
    final octets = parts.map(int.tryParse).toList();
    if (octets.any((octet) => octet == null || octet < 0 || octet > 255)) {
      return false;
    }
    return octets[0] == 100 && octets[1]! >= 64 && octets[1]! <= 127;
  }

  static bool _loopback(Uri? uri) {
    final host = uri?.host.toLowerCase() ?? '';
    return host == '127.0.0.1' ||
        host == 'localhost' ||
        host == '::1' ||
        host == '[::1]';
  }

  static ConnectionFailure diagnose({
    required String error,
    required String baseUrl,
    bool usesConnectionToken = false,
    bool requiresTokenReentry = false,

    /// The address is OpenCode running inside this app (the built-in
    /// Ubuntu). Whatever went wrong, starting it again is the fix, and
    /// tunnels and passwords are not the person's business here.
    bool inAppServer = false,

    /// The app just tried to start that server and it did not answer.
    bool inAppStartFailed = false,
    int attempts = 1,
    AppLocalizations? l10n,
  }) {
    l10n ??= AppLocalizationsEn();
    if (error.contains('This server runs OpenCode 1')) {
      return ConnectionFailure(
        title: l10n.openCode1UnsupportedTitle,
        explanation: l10n.openCode1Unsupported,
        checks: const [openCode2UpgradeCommand],
        primary: ConnectionFailureAction.changeServer,
        rawError: openCode2UpgradeCommand,
      );
    }
    final uri = Uri.tryParse(baseUrl);
    final lower = error.toLowerCase();
    final port = uri?.hasPort == true ? uri!.port : 4096;
    final unauthorized =
        lower.contains('http 401') ||
        lower.contains('http 403') ||
        lower.contains('password') && lower.contains('reject');
    final certificate =
        lower.contains('certificate') || lower.contains('handshake');
    final timedOut = lower.contains('timed out');
    final nothingAnswered =
        lower.contains('refused') ||
        lower.contains('unreachable') ||
        lower.contains('no route') ||
        lower.contains('no response') ||
        lower.contains('host name not found') ||
        lower.contains('connection dropped');
    final serverError = RegExp(r'http 5\d\d').hasMatch(lower);
    final unhealthy = lower.contains('unhealthy');
    final loopback = _loopback(uri);
    final tokenRejected =
        usesConnectionToken &&
        (unauthorized ||
            lower.contains('authentication') ||
            lower.contains('token') &&
                (lower.contains('reject') || lower.contains('invalid')));

    final retried = attempts >= 3 ? l10n.e7ConnectionFailure1(attempts) : null;
    // Only a remote address that did not answer: a refusal or a sign-in
    // problem means Tailscale did its part.
    final tailnet =
        !loopback &&
        (nothingAnswered || timedOut) &&
        isTailnetHost(uri?.host ?? '');
    final tailnetCheck = tailnet ? l10n.connectionFailureTailnetCheck : null;

    if (inAppServer && !usesConnectionToken) {
      final stopped =
          nothingAnswered ||
          timedOut ||
          !unauthorized && !serverError && !unhealthy;
      return ConnectionFailure(
        title: inAppStartFailed
            ? l10n.inAppServerStartFailedTitle
            : stopped
            ? l10n.inAppServerStoppedTitle
            : l10n.inAppServerNotRespondingTitle,
        explanation: inAppStartFailed
            ? l10n.inAppServerStartFailedBody
            : stopped
            ? l10n.inAppServerStoppedBody
            : l10n.inAppServerNotRespondingBody,
        checks: const [],
        primary: ConnectionFailureAction.startPhoneServer,
        rawError: error,
      );
    }
    if (usesConnectionToken && requiresTokenReentry) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure2,
        explanation: l10n.e7ConnectionFailure3,
        checks: [l10n.e7ConnectionFailure4, ?retried],
        primary: ConnectionFailureAction.updateToken,
        rawError: error,
      );
    }
    if (tokenRejected) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure5,
        explanation: l10n.e7ConnectionFailure6,
        checks: [l10n.e7ConnectionFailure7, ?retried],
        primary: ConnectionFailureAction.updateToken,
        rawError: error,
      );
    }
    if (usesConnectionToken && (nothingAnswered || timedOut)) {
      final local = loopback;
      return ConnectionFailure(
        title: local ? l10n.e7ConnectionFailure8 : l10n.e7ConnectionFailure9,
        explanation: local
            ? l10n.connectionFailureLocalCodexBody
            : l10n.connectionFailureRemoteCodexBody,
        checks: local
            ? [l10n.e7ConnectionFailure12, l10n.e7ConnectionFailure13, ?retried]
            : [
                l10n.e7ConnectionFailure14,
                l10n.e7ConnectionFailure15,
                ?retried,
              ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    if (unauthorized) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure16,
        explanation: l10n.e7ConnectionFailure17,
        checks: [
          l10n.e7ConnectionFailure18,
          l10n.e7ConnectionFailure19,
          ?retried,
        ],
        primary: ConnectionFailureAction.updatePassword,
        rawError: error,
      );
    }
    if (lower.contains('certificate changed')) {
      return ConnectionFailure(
        title: l10n.serverTrustCertificateChangedTitle,
        explanation: l10n.serverTrustCertificateChangedBody,
        checks: [l10n.serverTrustCertificateRecheck],
        primary: ConnectionFailureAction.reviewCertificate,
        rawError: error,
      );
    }
    if (certificate) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure20,
        explanation: l10n.e7ConnectionFailure21,
        checks: [
          l10n.e7ConnectionFailure22,
          l10n.e7ConnectionFailure23,
          ?retried,
        ],
        primary: ConnectionFailureAction.reviewCertificate,
        rawError: error,
      );
    }
    if (!usesConnectionToken &&
        loopback &&
        (nothingAnswered || timedOut || !serverError && !unhealthy)) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure24,
        explanation: l10n.connectionFailureLoopbackBody,
        checks: [
          l10n.e7ConnectionFailure27,
          l10n.e7ConnectionFailure28,
          ?retried,
        ],
        // Loopback identifies an endpoint, not what hosts it (a tunnel, a
        // server started by hand). Retry is safe without guessing.
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    if (timedOut) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure29,
        explanation: l10n.connectionFailureTimedOutBody,
        checks: [
          ?tailnetCheck,
          l10n.e7ConnectionFailure31,
          l10n.e7ConnectionFailure32(port),
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
        tailnet: tailnet,
      );
    }
    if (nothingAnswered) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure33,
        explanation: l10n.connectionFailureNothingAnsweredBody,
        checks: [
          ?tailnetCheck,
          l10n.e7ConnectionFailure35,
          l10n.e7ConnectionFailure36,
          l10n.e7ConnectionFailure37,
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
        tailnet: tailnet,
      );
    }
    if (serverError || unhealthy) {
      return ConnectionFailure(
        title: l10n.e7ConnectionFailure38,
        explanation: l10n.e7ConnectionFailure39,
        checks: [
          l10n.e7ConnectionFailure40,
          l10n.e7ConnectionFailure41,
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    return ConnectionFailure(
      title: l10n.e7ConnectionFailure42,
      explanation: l10n.connectionFailureUnknownBody,
      checks: [
        usesConnectionToken
            ? l10n.e7ConnectionFailure44
            : l10n.e7ConnectionFailure45,
        ?retried,
      ],
      primary: ConnectionFailureAction.retry,
      rawError: error,
    );
  }
}
