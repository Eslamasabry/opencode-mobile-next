part of '../profiles.dart';

/// A server's name as people should read it: a bare IP address (what the
/// name defaulted to before) becomes "Computer at 192.168.1.5"; a host name
/// or a name the person typed is kept as is.
String plainServerName(String name) {
  final value = name.trim();
  // This device by any of its names is "This phone", never "Computer at
  // 127.0.0.1": a loopback address is not somewhere else.
  if (isLoopbackHost(value.replaceAll(RegExp(r'[\[\]]'), ''))) {
    return 'This phone';
  }
  final ipv4 = RegExp(r'^\d{1,3}(?:\.\d{1,3}){3}$').hasMatch(value);
  final ipv6 =
      value.contains(':') && RegExp(r'^[0-9a-fA-F:.\[\]]+$').hasMatch(value);
  if (ipv4 || ipv6) {
    return 'Computer at ${value.replaceAll(RegExp(r'[\[\]]'), '')}';
  }
  return name;
}

// `isLoopbackHost` lives in `lib/domain/loopback_host.dart` (Flutter-free)
// and is re-exported above for the callers that import it from here.

/// Splits a bare `host[:port]` into the host to judge and the authority to
/// put in a URL, or null when it is not a plausible single address.
///
/// IPv6 needs both halves: `[::1]:4096` carries its port outside the
/// brackets, while a bare `::1` has to gain brackets before it can appear in
/// a URL at all — `http://::1` does not parse.
({String host, String authority})? _bareAuthority(String raw) {
  if (raw.startsWith('[')) {
    final close = raw.indexOf(']');
    if (close < 2) return null;
    final rest = raw.substring(close + 1);
    if (rest.isNotEmpty && !RegExp(r'^:\d{1,5}$').hasMatch(rest)) return null;
    return (host: raw.substring(1, close), authority: raw);
  }
  final colons = ':'.allMatches(raw).length;
  if (colons >= 2) {
    // An unbracketed IPv6 literal: its last group cannot be told apart from
    // a port, so the whole value is the address and the brackets are ours.
    return (host: raw, authority: '[$raw]');
  }
  final host = colons == 1 ? raw.substring(0, raw.indexOf(':')) : raw;
  if (host.isEmpty) return null;
  return (host: host, authority: raw);
}

/// Validates the transport boundary used by both profile editing and connect.
/// Android only permits cleartext traffic to the Termux loopback names in
/// [isLoopbackHost]. Expands a pasted bare address into a full server URL so
/// setup does not require knowing URL syntax. `host[:port]` and bare IPs
/// (v4 and v6) gain a scheme: loopback becomes `http://` (the only place
/// HTTP is allowed) and everything else becomes `https://`. Values that
/// already carry a scheme, and values that are not a plausible single
/// address, come back unchanged for the validator to explain.
String normalizeServerProfileUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty || raw.contains('://')) return raw;
  final bare = RegExp(r'^\[?[A-Za-z0-9._\-:]+\]?(:\d{1,5})?$');
  if (!bare.hasMatch(raw)) return raw;
  final parsed = _bareAuthority(raw);
  if (parsed == null) return raw;
  final scheme = isLoopbackHost(parsed.host) ? 'http' : 'https';
  return '$scheme://${parsed.authority}';
}

String? validateServerProfileUrl(
  String value, {
  String username = '',
  String password = '',
}) {
  final raw = value.trim();
  if (raw.isEmpty) return 'Enter a server URL.';
  if (!raw.contains('://')) {
    return 'Include https://. Plain http:// works only on this device or a '
        'private network address.';
  }
  final uri = Uri.tryParse(raw);
  if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
    return 'Enter a complete server URL, such as https://server.example:4096.';
  }
  if (uri.scheme != 'https' && uri.scheme != 'http') {
    // Both builds allow http only on loopback; only one of them reaches that
    // loopback through Termux, so only one says so.
    return platformCapabilities.supportsTermux
        ? 'Server URLs must use https://, or http:// for local Termux.'
        : 'Server URLs must use https://, or http:// for a local server.';
  }
  if (uri.userInfo.isNotEmpty) {
    return 'Do not put credentials in the URL. Use the fields below.';
  }
  if (uri.query.isNotEmpty || uri.fragment.isNotEmpty) {
    return 'Remove query parameters and fragments from the server URL.';
  }
  if (uri.path.isNotEmpty && uri.path != '/') {
    return 'Remove the path from the server URL. Enter only its origin.';
  }
  if (uri.scheme == 'http' &&
      !isLoopbackHost(uri.host) &&
      !isPrivateNetworkHost(uri.host)) {
    if (username.trim().isNotEmpty || password.isNotEmpty) {
      return 'HTTPS is required outside this device. Basic credentials must never be sent over HTTP.';
    }
    return 'HTTP is allowed only for localhost, 127.0.0.1, [::1], or a '
        'private network address. Use HTTPS or Tailscale for other servers.';
  }
  return null;
}

/// What a connect says when a saved plain-HTTP server was never confirmed.
const cleartextUnconfirmedMessage =
    'This server uses plain HTTP on your network, and you have not confirmed '
    'that yet. Edit the server and choose Use it anyway, or use HTTPS.';

/// True when [url] is plain HTTP to a private network address: accepted by
/// [validateServerProfileUrl], but only after the person has confirmed that
/// the password and conversation travel unencrypted there. Loopback, and
/// everything over HTTPS, never need it.
bool serverUrlNeedsCleartextConfirmation(String url) {
  final uri = Uri.tryParse(url.trim());
  return uri != null &&
      uri.scheme.toLowerCase() == 'http' &&
      uri.host.isNotEmpty &&
      !isLoopbackHost(uri.host) &&
      isPrivateNetworkHost(uri.host);
}

/// The origin a cleartext confirmation is recorded against, so a changed
/// address asks again. Null when [url] is not a usable URL.
String? cleartextOriginOf(String url) {
  final uri = Uri.tryParse(url.trim());
  if (uri == null || uri.host.isEmpty) return null;
  final host = uri.host.contains(':') ? '[${uri.host}]' : uri.host;
  return '${uri.scheme.toLowerCase()}://${host.toLowerCase()}:${uri.port}';
}

/// Normalizes a bare Codex WebSocket authority without changing already
/// explicit URLs. Loopback is intentionally the only cleartext origin.
String normalizeCodexServerUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty || raw.contains('://')) return raw;
  final bare = RegExp(r'^\[?[A-Za-z0-9._\-:]+\]?(:\d{1,5})?$');
  if (!bare.hasMatch(raw)) return raw;
  final parsed = _bareAuthority(raw);
  if (parsed == null) return raw;
  final scheme = isLoopbackHost(parsed.host) ? 'ws' : 'wss';
  return '$scheme://${parsed.authority}';
}

/// Validates a Codex origin and returns fixed, non-sensitive copy on failure.
String? validateCodexServerUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty) return 'Enter a Codex server URL.';
  final uri = Uri.tryParse(raw);
  if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
    return 'Enter a complete Codex server URL.';
  }
  if (uri.scheme != 'wss' && uri.scheme != 'ws') {
    return 'Codex server URLs must use wss://, or ws:// for a local server.';
  }
  if (uri.userInfo.isNotEmpty) {
    return 'Do not put credentials in the Codex URL.';
  }
  if (uri.query.isNotEmpty || uri.fragment.isNotEmpty) {
    return 'Remove query parameters and fragments from the Codex URL.';
  }
  if (uri.path.isNotEmpty && uri.path != '/') {
    return 'Remove the path from the Codex server URL.';
  }
  if (uri.scheme == 'ws' && !isLoopbackHost(uri.host)) {
    return 'Plain WebSocket is allowed only for a local Codex server.';
  }
  return null;
}

/// Validates the Codex project directory without interpreting or logging it.
bool _containsControlCharacter(String value) => value.runes.any(
  (character) => character <= 0x1f || (character >= 0x7f && character <= 0x9f),
);

String? validateCodexProjectDirectory(String value) {
  if (value.isEmpty ||
      value.length > 4096 ||
      _containsControlCharacter(value) ||
      (!value.startsWith('/') && !RegExp(r'^[A-Za-z]:[\\/]').hasMatch(value))) {
    return 'Enter an absolute Codex project directory.';
  }
  return null;
}

/// Validates a Codex connection token without returning the token in errors.
String? validateCodexConnectionToken(String value) {
  if (value.isEmpty ||
      value.length > 16384 ||
      _containsControlCharacter(value) ||
      RegExp(r'\s').hasMatch(value)) {
    return 'Enter a valid Codex connection token.';
  }
  return null;
}

/// Normalizes a bare Paseo daemon authority. Cleartext is the default only
/// where [validatePaseoServerUrl] allows it.
String normalizePaseoServerUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty || raw.contains('://')) return raw;
  final host = Uri.tryParse('ws://$raw')?.host ?? '';
  return isLoopbackHost(host) || isTailnetHost(host)
      ? 'ws://$raw'
      : 'wss://$raw';
}

/// A Paseo daemon is reached on this device, a Tailscale address or an
/// SSH tunnel. TLS does not make an open-internet endpoint private.
String? validatePaseoServerUrl(String value) {
  final raw = value.trim();
  if (raw.isEmpty) return 'Enter the Paseo daemon address.';
  final uri = Uri.tryParse(raw);
  if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
    return 'Enter a complete address, such as ws://100.64.0.1:6767.';
  }
  if (uri.scheme != 'wss' && uri.scheme != 'ws') {
    return 'Paseo addresses must use ws:// or wss://.';
  }
  if (uri.userInfo.isNotEmpty) {
    return 'Do not put credentials in the address. Use the password field.';
  }
  if (uri.query.isNotEmpty || uri.fragment.isNotEmpty) {
    return 'Remove query parameters and fragments from the address.';
  }
  if (uri.path.isNotEmpty && uri.path != '/' && uri.path != '/ws') {
    return 'Remove the path from the address.';
  }
  if (!isLoopbackHost(uri.host) && !isTailnetHost(uri.host)) {
    return 'Use this device, a Tailscale address, or your SSH tunnel.';
  }
  return null;
}

/// An empty password is valid: a daemon on a private network may run without
/// one. The value travels in a WebSocket subprotocol, so no spaces or commas.
String? validatePaseoPassword(String value) {
  if (value.isEmpty) return null;
  if (value.length > 4096 ||
      _containsControlCharacter(value) ||
      RegExp(r'[\s,]').hasMatch(value)) {
    return 'The password cannot contain spaces or commas.';
  }
  return null;
}
