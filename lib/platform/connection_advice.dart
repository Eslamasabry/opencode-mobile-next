import '../state/profiles.dart'
    show isLoopbackHost, normalizeServerProfileUrl, validateServerProfileUrl;

/// Synchronous, local-only verdict. Never retains or returns input or errors.
enum ConnectionAdvice {
  empty,
  malformed,
  credentials,
  queryOrFragment,
  path,
  unsupportedScheme,
  remoteHttp,
  https,
  loopback,
}

ConnectionAdvice explainConnectionAddress(String input) {
  if (input.trim().isEmpty) return ConnectionAdvice.empty;
  if (input.length > 4096) return ConnectionAdvice.malformed;
  try {
    final normalized = normalizeServerProfileUrl(input);
    final rejection = validateServerProfileUrl(normalized);
    final uri = Uri.tryParse(normalized);
    if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
      return ConnectionAdvice.malformed;
    }
    // Only the shared validator can grant acceptance.
    if (rejection == null) {
      return isLoopbackHost(uri.host)
          ? ConnectionAdvice.loopback
          : ConnectionAdvice.https;
    }
    if (uri.userInfo.isNotEmpty) return ConnectionAdvice.credentials;
    if (uri.hasQuery || uri.hasFragment) {
      return ConnectionAdvice.queryOrFragment;
    }
    if (uri.scheme != 'https' && uri.scheme != 'http') {
      return ConnectionAdvice.unsupportedScheme;
    }
    if (uri.path.isNotEmpty && uri.path != '/') return ConnectionAdvice.path;
    if (uri.scheme == 'http' && !isLoopbackHost(uri.host)) {
      return ConnectionAdvice.remoteHttp;
    }
    return ConnectionAdvice.malformed;
  } on FormatException {
    return ConnectionAdvice.malformed;
  } on ArgumentError {
    return ConnectionAdvice.malformed;
  }
}
