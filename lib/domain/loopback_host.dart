/// The loopback predicate, kept free of Flutter imports so the
/// orchestration adapters (and `dart run` tools such as
/// `tool/qa/gascity_read_proof.dart`) can use it outside a Flutter runtime.
/// `lib/state/profiles.dart` re-exports it for the rest of the app.
library;

import 'dart:io' show InternetAddress, InternetAddressType;

/// The hosts this app will speak cleartext HTTP to: this device, by every
/// name it has.
///
/// Kept as one predicate on purpose. The app used to have three loopback
/// checks that disagreed — the URL normalizer split on `:` and so read the
/// host of `[::1]:4096` as `[`, the validator's allowlist had no IPv6 entry
/// at all, and the connection controller's did. `::1` reached different
/// verdicts depending on which one you hit. Anything added here must also be
/// added to `android/app/src/main/res/xml/network_security_config.xml`, or
/// Android blocks the request after this code has allowed it.
bool isLoopbackHost(String host) {
  final normalized = host.toLowerCase();
  return normalized == 'localhost' ||
      normalized == '127.0.0.1' ||
      normalized == '::1';
}

/// True for an address that only exists inside a home, office or link-local
/// network: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16,
/// IPv6 fc00::/7 (unique local) and fe80::/10 (link-local), and `*.local`
/// mDNS names.
///
/// This is deliberately not [isLoopbackHost] and not a Tailscale test:
/// plain HTTP to one of these is allowed only after the person confirms
/// that the password and conversation travel unencrypted on that network.
/// Public addresses and every other host name are `false`, so they stay
/// HTTPS-only. Brackets, a trailing dot and an IPv6 zone are ignored; an
/// IPv4-mapped IPv6 address is judged by the IPv4 inside it.
bool isPrivateNetworkHost(String host) {
  var h = host.trim().toLowerCase();
  if (h.startsWith('[') && h.endsWith(']')) h = h.substring(1, h.length - 1);
  final zone = h.indexOf('%');
  if (zone >= 0) h = h.substring(0, zone);
  if (h.endsWith('.')) h = h.substring(0, h.length - 1);
  if (h.isEmpty) return false;
  final address = InternetAddress.tryParse(h);
  if (address == null) {
    // A name: only mDNS (`printer.local`), never a bare `local`.
    if (!h.endsWith('.local') || h.length <= '.local'.length) return false;
    return h
        .substring(0, h.length - '.local'.length)
        .split('.')
        .every((label) => label.isNotEmpty);
  }
  final b = address.rawAddress;
  if (address.type == InternetAddressType.IPv4 && b.length == 4) {
    return _privateIpv4(b[0], b[1]);
  }
  if (address.type == InternetAddressType.IPv6 && b.length == 16) {
    final mapped =
        b.take(10).every((x) => x == 0) && b[10] == 0xff && b[11] == 0xff;
    if (mapped) return _privateIpv4(b[12], b[13]);
    return (b[0] & 0xFE) == 0xFC || (b[0] == 0xFE && (b[1] & 0xC0) == 0x80);
  }
  return false;
}

bool _privateIpv4(int a, int b) =>
    a == 10 ||
    (a == 172 && b >= 16 && b <= 31) ||
    (a == 192 && b == 168) ||
    (a == 169 && b == 254);

/// True when [baseUrl] is plain HTTP to somewhere other than this device.
/// Such a client must never follow a redirect: the Basic header would ride
/// along to wherever the server points, in the clear or not.
bool isCleartextRemoteBase(String baseUrl) {
  final uri = Uri.tryParse(baseUrl.trim());
  return uri != null &&
      uri.scheme.toLowerCase() == 'http' &&
      !isLoopbackHost(uri.host);
}
