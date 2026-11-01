import 'dart:async';
import 'dart:io' show File;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android's thermal status (PowerManager, API 29+), mildest first.
///
/// Android does not close apps when the phone is hot: it slows the CPU
/// down and lets the heat build. [severe] is where it throttles hard;
/// [critical] and above is where the phone is about to shut itself down.
enum ThermalStatus {
  /// Before Android 10, or a status this app does not know.
  unknown,
  none,
  light,
  moderate,
  severe,
  critical,
  emergency,
  shutdown;

  static ThermalStatus parse(Object? value) {
    for (final status in values) {
      if (status.name == value) return status;
    }
    return unknown;
  }

  bool atLeast(ThermalStatus other) => index >= other.index;
}

/// One reading of how hot the phone is.
@immutable
class ThermalReading {
  const ThermalReading({required this.status, this.headroom});

  static const unknown = ThermalReading(status: ThermalStatus.unknown);

  factory ThermalReading.fromMap(Object? raw) {
    if (raw is! Map) return unknown;
    final headroom = raw['headroom'];
    final value = headroom is num ? headroom.toDouble() : null;
    return ThermalReading(
      status: ThermalStatus.parse(raw['status']),
      // Android answers a negative number or NaN when it has no forecast.
      headroom: value == null || value.isNaN || value < 0 ? null : value,
    );
  }

  final ThermalStatus status;

  /// `getThermalHeadroom(30)` (API 30+): the forecast 30 s ahead, where 1.0
  /// means Android throttles hard. Null when the phone gives none.
  final double? headroom;

  Map<String, Object?> get traceAttrs => {
    'status': status.name,
    if (headroom != null) 'headroom': headroom!.toStringAsFixed(2),
  };

  @override
  bool operator ==(Object other) =>
      other is ThermalReading &&
      other.status == status &&
      other.headroom == headroom;

  @override
  int get hashCode => Object.hash(status, headroom);

  @override
  String toString() => 'ThermalReading(${status.name}, $headroom)';
}

/// One reading of the battery: its level (0–100) and whether it charges.
/// Either is null when the phone does not say.
@immutable
class BatteryReading {
  const BatteryReading({this.level, this.charging});

  static const unknown = BatteryReading();

  factory BatteryReading.fromMap(Object? raw) {
    if (raw is! Map) return unknown;
    final level = raw['level'];
    final charging = raw['charging'];
    return BatteryReading(
      level: level is int && level >= 0 && level <= 100 ? level : null,
      charging: charging is bool ? charging : null,
    );
  }

  /// From the kernel's power-supply files (`capacity`, `status`), the
  /// fallback on builds whose native side has no `battery` call yet.
  static BatteryReading fromSysfs(String? capacity, String? status) {
    final level = int.tryParse(capacity?.trim() ?? '');
    final state = status?.trim().toLowerCase();
    return BatteryReading(
      level: level != null && level >= 0 && level <= 100 ? level : null,
      charging: switch (state) {
        'charging' || 'full' => true,
        'discharging' || 'not charging' => false,
        _ => null,
      },
    );
  }

  final int? level;
  final bool? charging;

  @override
  bool operator ==(Object other) =>
      other is BatteryReading &&
      other.level == level &&
      other.charging == charging;

  @override
  int get hashCode => Object.hash(level, charging);
}

/// The Dart half of `oc/thermal` (ThermalMonitor.kt). Every call answers
/// something safe when the channel is absent (desktop, tests) or fails.
class ThermalBridge {
  ThermalBridge({MethodChannel? channel, EventChannel? events})
    : _channel = channel ?? const MethodChannel(channelName),
      _events = events ?? const EventChannel(eventsName);

  static const channelName = 'oc/thermal';
  static const eventsName = 'oc/thermal/events';

  final MethodChannel _channel;
  final EventChannel _events;

  Future<ThermalReading> current() async {
    try {
      return ThermalReading.fromMap(
        await _channel.invokeMethod<Object?>('current'),
      );
    } on MissingPluginException {
      return ThermalReading.unknown;
    } on PlatformException {
      return ThermalReading.unknown;
    }
  }

  /// The battery now. Builds whose native side predates the `battery` call
  /// read the kernel's power-supply files instead; unknown when neither
  /// answers (the budget then does not wait on the battery).
  Future<BatteryReading> battery() async {
    try {
      final raw = await _channel.invokeMethod<Object?>('battery');
      return BatteryReading.fromMap(raw);
    } on MissingPluginException {
      return _sysfsBattery();
    } on PlatformException {
      return _sysfsBattery();
    }
  }

  static Future<BatteryReading> _sysfsBattery() async {
    if (kIsWeb) return BatteryReading.unknown;
    Future<String?> read(String name) async {
      try {
        return await File(
          '/sys/class/power_supply/battery/$name',
        ).readAsString().timeout(const Duration(seconds: 1));
      } on Object {
        return null;
      }
    }

    return BatteryReading.fromSysfs(
      await read('capacity'),
      await read('status'),
    );
  }

  /// Every status change, and a fresh headroom about every 30 seconds
  /// while listened to. Empty where there is no channel.
  Stream<ThermalReading> readings() => _events
      .receiveBroadcastStream()
      .map(ThermalReading.fromMap)
      .handleError((Object _) {}, test: (error) => true);

  /// Moves the agent helper's programs to Android's background class
  /// (`normal`, `slow` or `hold`); null on a build whose native side
  /// predates it (the caller then falls back to renice). The answer counts
  /// processes and threads set; no names.
  Future<Map<String, Object?>?> agentPriority(String level) async {
    try {
      final raw = await _channel.invokeMethod<Object?>('agentPriority', {
        'level': level,
      });
      return raw is Map ? Map<String, Object?>.from(raw) : null;
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  /// Whether Android's battery saver is on; false when unknown.
  Future<bool> powerSave() async {
    try {
      return await _channel.invokeMethod<bool>('powerSave') == true;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  /// Posts one notification on the AI Team's existing status channel;
  /// false when that channel does not exist or is silenced.
  Future<bool> notify({required String title, String text = ''}) async {
    try {
      return await _channel.invokeMethod<bool>('notify', {
            'title': title,
            'text': text,
          }) ==
          true;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
