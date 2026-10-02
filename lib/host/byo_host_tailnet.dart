import 'dart:io';

import '../domain/byo_host.dart';

typedef ByoHostAddressLookup =
    Future<List<InternetAddress>> Function(String host);

/// Resolves once, rejects mixed routes, and returns a numeric SSH destination.
/// A hostname suffix is never evidence of tailnet membership. Range validation
/// also cannot prove VPN state: host pinning and the actual route remain needed.
class ByoHostTailnetResolver {
  ByoHostTailnetResolver({ByoHostAddressLookup? lookup})
    : _lookup = lookup ?? InternetAddress.lookup;

  final ByoHostAddressLookup _lookup;

  static bool isTailnetAddress(InternetAddress address) {
    final bytes = address.rawAddress;
    if (address.type == InternetAddressType.IPv4) {
      if (bytes.length != 4 ||
          bytes[0] != 100 ||
          bytes[1] < 64 ||
          bytes[1] > 127) {
        return false;
      }
      // Tailscale service/internal ranges are not assignable to machines.
      if (bytes[1] == 100 && (bytes[2] == 0 || bytes[2] == 100) ||
          bytes[1] == 115 && (bytes[2] == 92 || bytes[2] == 93) ||
          bytes[1] == 101 && bytes[2] == 102 && bytes[3] == 103) {
        return false;
      }
      return true;
    }
    if (address.type != InternetAddressType.IPv6 || bytes.length != 16) {
      return false;
    }
    const prefix = [0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0];
    for (var i = 0; i < prefix.length; i++) {
      if (bytes[i] != prefix[i]) return false;
    }
    // Quad100's IPv6 service endpoint is not a machine either.
    return !(bytes.sublist(6, 15).every((byte) => byte == 0) &&
        bytes[15] == 0x53);
  }

  Future<ByoHostTarget> resolve(ByoHostTarget target) async {
    try {
      final literal = InternetAddress.tryParse(target.host);
      final addresses = literal == null
          ? await _lookup(target.host).timeout(const Duration(seconds: 10))
          : [literal];
      if (addresses.isEmpty ||
          addresses.any((address) => !isTailnetAddress(address))) {
        throw const ByoHostFailure(ByoHostFailureCode.tailnetRequired);
      }
      final sorted = [...addresses]
        ..sort((a, b) {
          if (a.type != b.type) {
            return a.type == InternetAddressType.IPv4 ? -1 : 1;
          }
          return a.address.compareTo(b.address);
        });
      // Never hand OpenSSH the original hostname to resolve again.
      return ByoHostTarget(
        user: target.user,
        host: sorted.first.address,
        port: target.port,
      );
    } catch (_) {
      // DNS error text, answers and exception strings never enter diagnostics.
      throw const ByoHostFailure(ByoHostFailureCode.tailnetRequired);
    }
  }
}
