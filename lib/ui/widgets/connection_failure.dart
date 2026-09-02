/// Turns a failed connection into something a person can act on.
///
/// The transport tells us *what* failed ("connection refused", "timed out",
/// HTTP 401). The address tells us *where* the server was supposed to be. Put
/// together they usually point at one thing the user can check, and one
/// button that gets them there. Retrying a server that is not running is
/// never that button.
library;

enum ConnectionFailureAction {
  retry,
  openTermuxSetup,
  updatePassword,
  changeServer,
}

class ConnectionFailure {
  const ConnectionFailure({
    required this.title,
    required this.explanation,
    required this.checks,
    required this.primary,
    required this.rawError,
  });

  /// Short headline, e.g. "Nothing is listening on this phone".
  final String title;

  /// One or two sentences that say what the failure means.
  final String explanation;

  /// Plain-language things to check, most likely first.
  final List<String> checks;

  /// The one action most likely to fix it.
  final ConnectionFailureAction primary;

  /// The verbatim error, for the Details expander.
  final String rawError;

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
    required bool supportsTermux,
    int attempts = 1,
  }) {
    final uri = Uri.tryParse(baseUrl);
    final lower = error.toLowerCase();
    final port = uri?.hasPort == true ? uri!.port : 4096;
    final hostLabel = uri?.host.isNotEmpty == true ? uri!.host : baseUrl;
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

    final retried = attempts >= 3
        ? 'Tried $attempts times. Retrying will not start a server that is '
              'not running.'
        : null;

    if (unauthorized) {
      return ConnectionFailure(
        title: 'Password rejected',
        explanation:
            'The server answered, but it did not accept the saved password. '
            'This happens when the server was restarted with a new password.',
        checks: [
          'Run opencode2 pair on the computer and paste the new code.',
          'If you set OPENCODE_SERVER_PASSWORD by hand, copy it again.',
          ?retried,
        ],
        primary: ConnectionFailureAction.updatePassword,
        rawError: error,
      );
    }
    if (certificate) {
      return ConnectionFailure(
        title: 'Certificate not trusted',
        explanation:
            'The server is there, but this device does not trust its HTTPS '
            'certificate, so the app refused to send the password.',
        checks: [
          'Use a certificate from a trusted authority, or a Tailscale Serve '
              'address.',
          'For a self-signed certificate, install it on this device first.',
          ?retried,
        ],
        primary: ConnectionFailureAction.changeServer,
        rawError: error,
      );
    }
    if (loopback &&
        (nothingAnswered || timedOut || !serverError && !unhealthy)) {
      return ConnectionFailure(
        title: 'Nothing is listening on this device',
        explanation:
            '$hostLabel:$port means the server should be running on this '
            'device, or reached through a tunnel that ends here. Neither '
            'answered.',
        checks: [
          if (supportsTermux)
            'Running OpenCode in Termux? Open Termux and check that '
                'opencode serve is still running. Android stops it when Termux '
                'is closed or swiped away.',
          'Using adb reverse or an SSH forward? It ends when the cable is '
              'unplugged or the SSH app is closed. Start it again.',
          'Connecting to another computer instead? Change the server to its '
              'HTTPS address or pair again.',
          ?retried,
        ],
        primary: supportsTermux
            ? ConnectionFailureAction.openTermuxSetup
            : ConnectionFailureAction.changeServer,
        rawError: error,
      );
    }
    if (timedOut) {
      return ConnectionFailure(
        title: 'The server did not answer in time',
        explanation:
            'Something is at $hostLabel, but it did not reply. Usually the '
            'network in between, not the server.',
        checks: [
          'Are you on the same network or VPN (for example Tailscale) as the '
              'computer?',
          'Is a firewall or captive portal blocking port $port?',
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    if (nothingAnswered) {
      return ConnectionFailure(
        title: 'Server not reachable',
        explanation:
            'Nothing answered at $hostLabel:$port. Either the server is not '
            'running or this device cannot reach that address.',
        checks: [
          'Is opencode serve still running on the computer?',
          'Are you on the same network or VPN as the computer?',
          'Did the address change? Pair again to pick up the new one.',
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    if (serverError || unhealthy) {
      return ConnectionFailure(
        title: 'The server answered with an error',
        explanation:
            'The server is running but reported itself unhealthy. Its own '
            'log will say why.',
        checks: [
          'Restart opencode serve and watch its output.',
          'Check that the server version is supported by this app.',
          ?retried,
        ],
        primary: ConnectionFailureAction.retry,
        rawError: error,
      );
    }
    return ConnectionFailure(
      title: 'Could not connect',
      explanation: 'The connection to $hostLabel failed. Details below.',
      checks: [
        'Is opencode serve running, and is this the right address?',
        ?retried,
      ],
      primary: ConnectionFailureAction.retry,
      rawError: error,
    );
  }
}
