/// Optional protocol facet: implemented only where the CLI attach syntax is
/// verified. It neither starts a process nor reads or copies a password.
abstract interface class SessionCommandHandoffGateway {
  SessionCommandHandoff createSessionCommandHandoff({
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  });
}

enum SessionCommandUnavailable {
  unsafeAddress,
  localAddress,
  missingDirectory,
  unsupportedWorkspace,
  invalidReference,
}

class SessionCommandHandoff {
  final String? command;
  final SessionCommandUnavailable? unavailable;

  const SessionCommandHandoff._(this.command, this.unavailable);
  const SessionCommandHandoff.unavailable(SessionCommandUnavailable reason)
    : this._(null, reason);

  /// OpenCode 1 attach flags verified from the installed 1.18.29 CLI help.
  /// Values are POSIX-shell quoted; option assignments also prevent values
  /// starting with '-' from becoming another CLI flag.
  factory SessionCommandHandoff.openCode1({
    required String serverURL,
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  }) {
    if (workspaceID != null && workspaceID.isNotEmpty) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.unsupportedWorkspace,
      );
    }
    final uri = Uri.tryParse(serverURL);
    if (uri == null ||
        !uri.hasAuthority ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        _unsafeText(serverURL)) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.unsafeAddress,
      );
    }
    final host = uri.host.toLowerCase().replaceFirst(RegExp(r'\.$'), '');
    // URL clients can reinterpret legacy numeric IPv4 (octal/hex components)
    // as loopback. A command handoff accepts only canonical decimal spelling.
    final numericHost = RegExp(
      r'^(?:0x[0-9a-f]+|[0-9]+)(?:\.(?:0x[0-9a-f]+|[0-9]+))*$',
    );
    final canonicalIPv4 = host.split('.');
    if (numericHost.hasMatch(host) &&
        (canonicalIPv4.length != 4 ||
            canonicalIPv4.any(
              (part) =>
                  !RegExp(r'^(0|[1-9][0-9]{0,2})$').hasMatch(part) ||
                  (int.tryParse(part) ?? 256) > 255,
            ))) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.unsafeAddress,
      );
    }
    if (host == 'localhost' ||
        host.endsWith('.localhost') ||
        host == '::1' ||
        host == '[::1]' ||
        host == '::' ||
        host == '[::]' ||
        host == '0.0.0.0' ||
        host.startsWith('127.') ||
        RegExp(r'^(?:[0-9]+|0x[0-9a-f]+)$').hasMatch(host) ||
        host.contains('ffff:127.') ||
        RegExp(r'ffff:7f[0-9a-f]{2}:').hasMatch(host) ||
        (host.contains(':') &&
            ['', '1'].contains(host.replaceAll(RegExp(r'[\[\]:0]'), '')))) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.localAddress,
      );
    }
    if (uri.scheme != 'https') {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.unsafeAddress,
      );
    }
    if (directory == null || directory.isEmpty || _unsafeText(directory)) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.missingDirectory,
      );
    }
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(sessionID) ||
        _unsafeText(username)) {
      return const SessionCommandHandoff.unavailable(
        SessionCommandUnavailable.invalidReference,
      );
    }
    final command =
        'opencode attach ${_quote(uri.toString())}'
        ' --session=${_quote(sessionID)} --dir=${_quote(directory)}'
        ' --username=${_quote(username.isEmpty ? 'opencode' : username)}';
    return SessionCommandHandoff._(command, null);
  }

  static bool _unsafeText(String value) =>
      RegExp(r'[\x00-\x1f\x7f]').hasMatch(value);

  static String _quote(String value) => "'${value.replaceAll("'", "'\\''")}'";
}
