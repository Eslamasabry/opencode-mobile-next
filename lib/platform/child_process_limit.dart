/// Android 14+ Developer options › "Disable child process restrictions":
/// whether it is on, and a way to open Settings where it is.
///
/// Native side (`oc/background`: `childProcessLimit`, `openDeveloperOptions`)
/// needs a release. An older APK with only the Dart patch answers
/// notImplemented; then [ChildProcessLimitStatus.canOpenSettings] is false
/// and the card shows the steps as text.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'platform_capabilities.dart';

enum ChildProcessLimitState { enforced, lifted, unknown }

class ChildProcessLimitStatus {
  const ChildProcessLimitStatus({
    this.sdkInt,
    this.state = ChildProcessLimitState.unknown,
    this.developerOptionsEnabled,
    this.canOpenSettings = false,
  });

  final int? sdkInt;
  final ChildProcessLimitState state;
  final bool? developerOptionsEnabled;

  /// The native methods exist (this APK is new enough).
  final bool canOpenSettings;
}

class ChildProcessLimitPlatform {
  ChildProcessLimitPlatform({
    MethodChannel? channel,
    Future<int?> Function()? fallbackSdk,
  }) : _channel = channel ?? const MethodChannel('oc/background'),
       _fallbackSdk = fallbackSdk ?? _readSdkWithGetprop;

  final MethodChannel _channel;
  final Future<int?> Function() _fallbackSdk;

  Future<ChildProcessLimitStatus> read() async {
    if (!platformCapabilities.isAndroid) return const ChildProcessLimitStatus();
    try {
      final raw = await _channel.invokeMapMethod<String, Object?>(
        'childProcessLimit',
      );
      if (raw == null) return await _fallback();
      final off = raw['restrictionsOff'];
      return ChildProcessLimitStatus(
        sdkInt: raw['sdkInt'] as int?,
        state: off is bool
            ? (off
                  ? ChildProcessLimitState.lifted
                  : ChildProcessLimitState.enforced)
            : ChildProcessLimitState.unknown,
        developerOptionsEnabled: raw['developerOptionsEnabled'] as bool?,
        canOpenSettings: true,
      );
    } on MissingPluginException {
      return _fallback();
    } on PlatformException {
      return _fallback();
    }
  }

  Future<ChildProcessLimitStatus> _fallback() async {
    int? sdk;
    try {
      sdk = await _fallbackSdk();
    } catch (_) {}
    return ChildProcessLimitStatus(sdkInt: sdk);
  }

  /// "developer", "about" or "none" (also when the method is missing).
  Future<String> openDeveloperOptions() async {
    try {
      final raw = await _channel.invokeMapMethod<String, Object?>(
        'openDeveloperOptions',
      );
      final opened = raw?['opened'];
      return opened is String ? opened : 'none';
    } on MissingPluginException {
      return 'none';
    } on PlatformException {
      return 'none';
    }
  }

  static Future<int?> _readSdkWithGetprop() async {
    // The process that runs, not the platform a test pretends to be: only
    // a real Android has getprop (and a test host never waits on one).
    if (kIsWeb || !Platform.isAndroid) return null;
    try {
      final result = await Process.run('/system/bin/getprop', [
        'ro.build.version.sdk',
      ]).timeout(const Duration(seconds: 2));
      return int.tryParse('${result.stdout}'.trim());
    } catch (_) {
      return null;
    }
  }
}

/// Whether the person turned Android's child-process limit off, for the
/// process budget's ceiling (read at most once a minute; false when unknown,
/// so the stricter ceiling applies).
Future<bool> childProcessLimitLifted() async {
  final now = DateTime.now();
  final cached = _liftedCache;
  if (cached != null &&
      now.difference(cached.$1) < const Duration(minutes: 1)) {
    return cached.$2;
  }
  bool lifted;
  try {
    lifted =
        (await ChildProcessLimitPlatform().read()).state ==
        ChildProcessLimitState.lifted;
  } on Object {
    lifted = false;
  }
  _liftedCache = (now, lifted);
  return lifted;
}

(DateTime, bool)? _liftedCache;

final childProcessLimitPlatformProvider = Provider<ChildProcessLimitPlatform>(
  (ref) => ChildProcessLimitPlatform(),
);

enum ChildLimitCardKind {
  openSettings,
  enableDeveloperOptions,
  instructionsOnly,
}

/// Which card to show, if any. Never a nag: only on Android 14+, only while
/// the limit still applies, only after Android killed an agent
/// ([recentKill]) or the budget guard queued work often ([queuedOften]).
/// Hidden by the person ([hiddenAt]) until a newer kill ([lastKillAt]).
ChildLimitCardKind? childLimitCard({
  required ChildProcessLimitStatus status,
  required bool recentKill,
  required bool queuedOften,
  DateTime? hiddenAt,
  DateTime? lastKillAt,
}) {
  final sdk = status.sdkInt;
  if (sdk == null || sdk < 34) return null;
  if (status.state == ChildProcessLimitState.lifted) return null;
  if (!recentKill && !queuedOften) return null;
  if (hiddenAt != null &&
      !(lastKillAt != null && lastKillAt.isAfter(hiddenAt))) {
    return null;
  }
  if (!status.canOpenSettings) return ChildLimitCardKind.instructionsOnly;
  return status.developerOptionsEnabled == true
      ? ChildLimitCardKind.openSettings
      : ChildLimitCardKind.enableDeveloperOptions;
}

/// The device-wide "Hide" memory (not per profile).
class ChildLimitCardHide {
  const ChildLimitCardHide._();

  static const key = 'oc.childLimitCardHidden';

  static Future<DateTime?> read([SharedPreferences? prefs]) async {
    try {
      final p = prefs ?? await SharedPreferences.getInstance();
      final ms = p.getInt(key);
      return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms);
    } catch (_) {
      return null;
    }
  }

  static Future<void> write(DateTime at, [SharedPreferences? prefs]) async {
    try {
      final p = prefs ?? await SharedPreferences.getInstance();
      await p.setInt(key, at.millisecondsSinceEpoch);
    } catch (_) {}
  }
}
