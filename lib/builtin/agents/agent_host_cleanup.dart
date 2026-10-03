import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../../domain/phone_agent_host.dart';
import '../builtin_linux.dart';

/// Drains only this profile's host/auth processes, then erases its Linux home.
final class ChannelAgentHostCleanup {
  ChannelAgentHostCleanup({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(BuiltinLinux.channelName);
  final MethodChannel _channel;
  static bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
  Future<void> call(String profileId) async {
    try {
      await _channel.invokeMethod<void>('deleteAgentHost', {
        'profileId': profileId,
      });
    } catch (_) {
      throw const AgentHostException(AgentHostFailure.storage);
    }
  }
}
