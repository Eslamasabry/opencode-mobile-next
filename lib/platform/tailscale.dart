import 'package:flutter/services.dart';

import 'platform_capabilities.dart';

/// Package presence is not VPN state. This bridge neither reads nor configures
/// a tailnet, account, VPN permission or tunnel.
enum TailscaleAppState { installed, missing, unavailable, unsupported }

class TailscaleBridge {
  const TailscaleBridge({
    MethodChannel channel = const MethodChannel('oc/tailscale'),
  }) : _channel = channel;
  final MethodChannel _channel;

  Future<TailscaleAppState> check() async {
    if (!platformCapabilities.supportsTailscaleHandoff) {
      return TailscaleAppState.unsupported;
    }
    try {
      return switch (await _channel
          .invokeMethod<String>('check')
          .timeout(const Duration(seconds: 5))) {
        'installed' => TailscaleAppState.installed,
        'missing' => TailscaleAppState.missing,
        _ => TailscaleAppState.unavailable,
      };
    } catch (_) {
      return TailscaleAppState.unavailable;
    }
  }

  Future<bool> open() async {
    if (!platformCapabilities.supportsTailscaleHandoff) return false;
    try {
      return await _channel
              .invokeMethod<bool>('open')
              .timeout(const Duration(seconds: 5)) ==
          true;
    } catch (_) {
      return false;
    }
  }
}
