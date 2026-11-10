import 'package:flutter/services.dart';

class SchedulePlatformStatus {
  const SchedulePlatformStatus({
    this.supported = false,
    this.exactAllowed = false,
    this.batteryExempt = false,
    this.notificationsAllowed = false,
  });
  final bool supported, exactAllowed, batteryExempt, notificationsAllowed;
}

class ScheduledPromptsPlatform {
  const ScheduledPromptsPlatform();
  static const channel = MethodChannel('oc/scheduled_prompts');

  /// Read-only admission check; failure must not be treated as no lease.
  Future<bool> activePhoneLease() async {
    final active = await channel
        .invokeMethod<bool>('activePhoneLease')
        .timeout(const Duration(seconds: 2));
    if (active == null) {
      throw PlatformException(code: 'unavailable');
    }
    return active;
  }

  void setOpenHandler(Future<void> Function()? handler) {
    channel.setMethodCallHandler(
      handler == null
          ? null
          : (call) async {
              if (call.method == 'openAvailable') await handler();
            },
    );
  }

  Future<SchedulePlatformStatus> status() async {
    try {
      final value = await channel.invokeMapMethod<String, dynamic>('status');
      return SchedulePlatformStatus(
        supported: value?['supported'] == true,
        exactAllowed: value?['exactAllowed'] == true,
        batteryExempt: value?['batteryExempt'] == true,
        notificationsAllowed: value?['notificationsAllowed'] == true,
      );
    } on MissingPluginException {
      return const SchedulePlatformStatus();
    }
  }

  Future<void> replaceProfile(
    String profileId,
    List<Map<String, Object?>> entries, {
    DateTime? digestAt,
  }) => channel.invokeMethod<void>('replaceProfile', {
    'profileId': profileId,
    'entries': entries,
    'digestAtMs': digestAt?.millisecondsSinceEpoch,
  });
  Future<void> requestExactAlarm() =>
      channel.invokeMethod<void>('requestExactAlarm');
  Future<void> restoreProfile(String profileId) async {
    try {
      await channel.invokeMethod<void>('restoreProfile', {
        'profileId': profileId,
      });
    } on MissingPluginException {
      /* Non-Android has no alarms. */
    }
  }

  Future<void> cancelProfile(String profileId) async {
    try {
      await channel.invokeMethod<void>('cancelProfile', {
        'profileId': profileId,
      });
    } on MissingPluginException {
      /* Non-Android has no alarms. */
    }
  }

  Future<Map<String, dynamic>?> consumeOpen() async {
    try {
      return await channel.invokeMapMethod<String, dynamic>('consumeOpen');
    } on MissingPluginException {
      return null;
    }
  }

  Future<List<Map<String, dynamic>>> readReceipts(String profileId) async {
    final rows = await channel.invokeListMethod<dynamic>('readReceipts', {
      'profileId': profileId,
    });
    return [
      for (final row in rows ?? const []) Map<String, dynamic>.from(row as Map),
    ];
  }

  Future<void> acknowledgeReceipts(String profileId, List<String> ids) =>
      channel.invokeMethod<void>('acknowledgeReceipts', {
        'profileId': profileId,
        'ids': ids,
      });
}
