import 'dart:async';

import 'package:flutter/services.dart';

/// What Android says about an APK file (`inspectApk`).
class ApkInfo {
  const ApkInfo({
    required this.packageName,
    required this.versionCode,
    required this.signerSha256,
  });

  final String packageName;
  final int versionCode;
  final String signerSha256;
}

enum AppInstallState { pendingUser, success, failure, aborted }

class AppInstallEvent {
  const AppInstallEvent(this.state, [this.message]);

  final AppInstallState state;

  /// Android's own words; technical, shown only under Details.
  final String? message;
}

/// The native side of in-app updates (channel `oc/app_update`; contract in
/// docs/design/app-updates-2026-10-31.md).
abstract interface class AppUpdatePlatform {
  Future<bool> canInstall();
  Future<void> openInstallPermission();
  Future<ApkInfo> inspectApk(String path);
  Future<String> ownSignerSha256();
  Future<List<String>> supportedAbis();

  /// Starts the install. Android shows its own confirm dialog; the stream
  /// ends after its last event.
  Stream<AppInstallEvent> install(String path);
}

class ChannelAppUpdatePlatform implements AppUpdatePlatform {
  ChannelAppUpdatePlatform([MethodChannel? channel])
    : _channel = channel ?? const MethodChannel('oc/app_update');

  final MethodChannel _channel;

  @override
  Future<bool> canInstall() async =>
      await _channel.invokeMethod<bool>('canInstall') ?? false;

  @override
  Future<void> openInstallPermission() =>
      _channel.invokeMethod<void>('openInstallPermission');

  @override
  Future<ApkInfo> inspectApk(String path) async {
    final raw = await _channel.invokeMapMethod<String, dynamic>('inspectApk', {
      'path': path,
    });
    if (raw == null) throw StateError('inspectApk gave no answer');
    return ApkInfo(
      packageName: raw['packageName'] as String,
      versionCode: (raw['versionCode'] as num).toInt(),
      signerSha256: raw['signerSha256'] as String,
    );
  }

  @override
  Future<String> ownSignerSha256() async =>
      (await _channel.invokeMethod<String>('ownSignerSha256')) ?? '';

  @override
  Future<List<String>> supportedAbis() async =>
      (await _channel.invokeListMethod<String>('supportedAbis')) ?? const [];

  @override
  Stream<AppInstallEvent> install(String path) {
    final events = StreamController<AppInstallEvent>();
    _channel.setMethodCallHandler((call) async {
      if (call.method != 'installState') return;
      final args = Map<String, dynamic>.from(call.arguments as Map);
      final state = AppInstallState.values.asNameMap()[args['state']];
      if (state == null) return;
      events.add(AppInstallEvent(state, args['message'] as String?));
      if (state != AppInstallState.pendingUser) {
        unawaited(events.close());
      }
    });
    _channel.invokeMethod<void>('install', {'path': path}).catchError((
      Object e,
    ) {
      if (!events.isClosed) {
        events.add(AppInstallEvent(AppInstallState.failure, '$e'));
        unawaited(events.close());
      }
    });
    return events.stream;
  }
}
