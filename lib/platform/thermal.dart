import 'dart:async';

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

  /// Every status change, and a fresh headroom about every 30 seconds
  /// while listened to. Empty where there is no channel.
  Stream<ThermalReading> readings() => _events
      .receiveBroadcastStream()
      .map(ThermalReading.fromMap)
      .handleError((Object _) {}, test: (error) => true);

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
