import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../domain/app_exit_history.dart';
import '../domain/diagnostics_error.dart';
import 'native_crash.dart';

/// Why the app's previous process ended, in the words the app speaks about
/// it (`oc/lifecycle`, both halves: this file and AppLifecycle.kt).
enum AppExitKind {
  /// It ended itself or Android gave no reason: nothing to say.
  normal,

  /// The app was updated or reinstalled: nothing to say either.
  update,

  /// Force-stopped: from Settings, by swiping it from Recents on a phone
  /// that force-stops then (Nubia/RedMagic, Xiaomi, …), or by the phone's
  /// battery manager.
  forceStop,

  /// Android reclaimed its memory.
  lowMemory,

  /// A crash or an "isn't responding".
  crash,

  /// Any other kill by the system (resources, a revoked permission, …).
  killed;

  /// Whether the person should hear about it when something was running.
  bool get notable => this != normal && this != update;
}

/// The `ApplicationExitInfo` constants this classification reads.
abstract final class AndroidExitReason {
  static const unknown = 0;
  static const exitSelf = 1;
  static const signaled = 2;
  static const lowMemory = 3;
  static const crash = 4;
  static const crashNative = 5;
  static const anr = 6;
  static const initializationFailure = 7;
  static const permissionChange = 8;
  static const excessiveResourceUsage = 9;
  static const userRequested = 10;
  static const userStopped = 11;
  static const dependencyDied = 12;
  static const other = 13;
  static const freezer = 14;
  static const packageStateChange = 15;
  static const packageUpdated = 16;

  // Subreasons (hidden API, read from the record's text).
  static const subTooManyCached = 2;
  static const subTooManyEmpty = 3;
  static const subTrimEmpty = 4;
  static const subLargeCached = 5;
  static const subMemoryPressure = 6;
  static const subRemoveLru = 16;
  static const subForceStop = 21;
  static const subRemoveTask = 22;
  static const subStopApp = 23;
  static const subPackageUpdate = 25;
}

/// One `ApplicationExitInfo` of the app's main process.
@immutable
class AppExitRecord {
  const AppExitRecord({
    required this.reason,
    required this.timestamp,
    this.subReason = -1,
    this.status = 0,
    this.importance = 0,
    this.description = '',
  });

  static AppExitRecord? fromMap(Object? raw) {
    if (raw is! Map) return null;
    int asInt(Object? value, [int fallback = 0]) =>
        value is num ? value.toInt() : fallback;
    final millis = asInt(raw['timestamp']);
    if (millis <= 0) return null;
    return AppExitRecord(
      reason: asInt(raw['reason']),
      subReason: asInt(raw['subReason'], -1),
      status: asInt(raw['status']),
      importance: asInt(raw['importance']),
      timestamp: DateTime.fromMillisecondsSinceEpoch(millis),
      description: (raw['description'] ?? '').toString(),
    );
  }

  final int reason;

  /// -1 when Android's record did not say.
  final int subReason;
  final int status;
  final int importance;
  final DateTime timestamp;
  final String description;

  AppExitKind get kind => classifyAppExit(this);

  /// For the trace and diagnostics: numbers only, no package names.
  Map<String, Object?> get traceAttrs => {
    'kind': kind.name,
    'reason': reason,
    'subReason': subReason,
    'importance': importance,
    'at': timestamp.toIso8601String(),
  };
}

/// What Android's record means for the person.
AppExitKind classifyAppExit(AppExitRecord record) {
  final sub = record.subReason;
  switch (record.reason) {
    case AndroidExitReason.packageUpdated:
    case AndroidExitReason.packageStateChange:
      return AppExitKind.update;
    case AndroidExitReason.userRequested:
    case AndroidExitReason.userStopped:
      // An update also ends the old process "because the user asked".
      if (sub == AndroidExitReason.subPackageUpdate) return AppExitKind.update;
      return AppExitKind.forceStop;
    case AndroidExitReason.lowMemory:
      return AppExitKind.lowMemory;
    case AndroidExitReason.crash:
    case AndroidExitReason.crashNative:
    case AndroidExitReason.anr:
    case AndroidExitReason.initializationFailure:
      return AppExitKind.crash;
    case AndroidExitReason.exitSelf:
    case AndroidExitReason.unknown:
      return AppExitKind.normal;
    default:
      if (sub == AndroidExitReason.subForceStop ||
          sub == AndroidExitReason.subStopApp ||
          sub == AndroidExitReason.subRemoveTask) {
        return AppExitKind.forceStop;
      }
      if (sub == AndroidExitReason.subPackageUpdate) return AppExitKind.update;
      if (const {
        AndroidExitReason.subTooManyCached,
        AndroidExitReason.subTooManyEmpty,
        AndroidExitReason.subTrimEmpty,
        AndroidExitReason.subLargeCached,
        AndroidExitReason.subMemoryPressure,
        AndroidExitReason.subRemoveLru,
      }.contains(sub)) {
        return AppExitKind.lowMemory;
      }
      return AppExitKind.killed;
  }
}

/// What the app learns at start about the process before this one.
@immutable
class AppLaunchReport {
  const AppLaunchReport({
    this.exit,
    this.previousServices = const [],
    this.lastCrash,
  });

  static const empty = AppLaunchReport();

  factory AppLaunchReport.fromMap(Object? raw) {
    if (raw is! Map) return empty;
    final services = raw['previousServices'];
    return AppLaunchReport(
      exit: AppExitRecord.fromMap(raw['exit']),
      lastCrash: NativeCrashRecord.fromMap(raw['lastCrash']),
      previousServices: [
        if (services is List)
          for (final name in services)
            if (name is String && name.isNotEmpty) name,
      ],
    );
  }

  /// The newest exit not reported before; null on the first start after
  /// install, before Android 11, or when there was none.
  final AppExitRecord? exit;

  /// Last captured JVM exception, also available when Android's historical
  /// exit record is missing. Contains only bounded, safe native metadata.
  final NativeCrashRecord? lastCrash;

  /// The built-in Ubuntu's services that ran when that process ended
  /// (`server`, `aiteam`). A stop the person asked for clears them, so
  /// anything here was ended by Android, not by the person.
  final List<String> previousServices;
}

/// The phone, for keep-alive advice.
@immutable
class KeepAliveInfo {
  const KeepAliveInfo({
    this.manufacturer = '',
    this.brand = '',
    this.batteryOptimizationIgnored = false,
  });

  factory KeepAliveInfo.fromMap(Object? raw) {
    if (raw is! Map) return const KeepAliveInfo();
    return KeepAliveInfo(
      manufacturer: (raw['manufacturer'] ?? '').toString(),
      brand: (raw['brand'] ?? '').toString(),
      batteryOptimizationIgnored: raw['batteryOptimizationIgnored'] == true,
    );
  }

  final String manufacturer;
  final String brand;
  final bool batteryOptimizationIgnored;
}

/// A settings screen the keep-alive advice can open.
enum KeepAliveSetting {
  /// Android's "don't optimise battery" for this app.
  battery,

  /// The maker's auto-start / background-launch list.
  autostart,

  /// This app's own info page (Battery, background activity).
  appDetails,
}

/// The Dart half of `oc/lifecycle`. Every call answers something safe when
/// the channel is absent (desktop, tests) or fails.
class AppLifecycleBridge {
  AppLifecycleBridge({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName = 'oc/lifecycle';

  final MethodChannel _channel;

  /// Recent main-process exits, independent of startup recovery consumption.
  Future<AppExitHistory> exitHistory({int limit = 10}) async {
    if (limit < 1 || limit > 50) {
      return AppExitHistory(
        supported: false,
        error: DiagnosticsError.invalidLimit,
      );
    }
    AppExitHistory unavailable() =>
        AppExitHistory(supported: true, error: DiagnosticsError.unavailable);
    try {
      final raw = await _channel.invokeMethod<Object?>('exitHistory', {
        'limit': limit,
      });
      if (raw is! Map || raw['supported'] is! bool) return unavailable();
      if (raw['error'] != null) {
        return AppExitHistory(
          supported: raw['supported'] == true,
          error: raw['error'] == 'invalidLimit'
              ? DiagnosticsError.invalidLimit
              : DiagnosticsError.unavailable,
        );
      }
      if (raw['supported'] == false) {
        return const AppExitHistory.unsupported();
      }
      final values = raw['entries'];
      if (values is! List || values.length > 50) return unavailable();
      final entries = <AppExitEntry>[];
      for (final value in values) {
        if (value is! Map ||
            value['reason'] is! int ||
            value['importance'] is! int ||
            value['timestamp'] is! int) {
          return unavailable();
        }
        final millis = value['timestamp'] as int;
        if (millis <= 0 || millis > 8640000000000000) return unavailable();
        final subReason = value['subReason'];
        if (subReason != null && subReason is! int) return unavailable();
        // Deliberately do not read description, category, trace or other text
        // from the channel. Classification uses the same numeric recovery API.
        final record = AppExitRecord(
          reason: value['reason'] as int,
          importance: value['importance'] as int,
          subReason: subReason as int? ?? -1,
          timestamp: DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true),
        );
        entries.add(
          AppExitEntry(
            reason: record.reason,
            importance: record.importance,
            at: record.timestamp,
            category: switch (classifyAppExit(record)) {
              AppExitKind.normal => AppExitCategory.normal,
              AppExitKind.update => AppExitCategory.update,
              AppExitKind.forceStop => AppExitCategory.forceStop,
              AppExitKind.lowMemory => AppExitCategory.lowMemory,
              AppExitKind.crash => AppExitCategory.crash,
              AppExitKind.killed => AppExitCategory.killed,
            },
          ),
        );
      }
      entries.sort((a, b) => b.at.compareTo(a.at));
      return AppExitHistory(supported: true, entries: entries.take(limit));
    } on MissingPluginException {
      return const AppExitHistory.unsupported();
    } on PlatformException {
      return unavailable();
    } on FormatException {
      return unavailable();
    }
  }

  Future<AppLaunchReport> launchReport() async {
    try {
      return AppLaunchReport.fromMap(
        await _channel.invokeMethod<Object?>('launchReport'),
      );
    } on MissingPluginException {
      return AppLaunchReport.empty;
    } on PlatformException {
      return AppLaunchReport.empty;
    }
  }

  Future<KeepAliveInfo> keepAliveInfo() async {
    try {
      return KeepAliveInfo.fromMap(
        await _channel.invokeMethod<Object?>('keepAliveInfo'),
      );
    } on MissingPluginException {
      return const KeepAliveInfo();
    } on PlatformException {
      return const KeepAliveInfo();
    }
  }

  /// False when no screen could be opened on this phone.
  Future<bool> openKeepAliveSetting(KeepAliveSetting setting) async {
    try {
      return await _channel.invokeMethod<bool>('openKeepAliveSetting', {
            'setting': setting.name,
          }) ==
          true;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
