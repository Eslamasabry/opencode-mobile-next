import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Dart half of `oc/app_update` (lib/platform/app_installer.dart and
/// AppUpdateInstaller.kt).
class AppInstaller {
  AppInstaller._();

  static const _channel = MethodChannel('oc/app_update');

  @visibleForTesting
  static Future<List<String>> Function()? getSupportedAbisOverride;

  @visibleForTesting
  static Future<bool> Function()? canRequestPackageInstallsOverride;

  @visibleForTesting
  static Future<bool> Function()? openInstallPermissionSettingsOverride;

  @visibleForTesting
  static Future<bool> Function(String filePath)? installApkOverride;

  static bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Returns the ABIs supported by this device, in preference order (e.g.
  /// `['arm64-v8a', 'armeabi-v7a']`).
  static Future<List<String>> getSupportedAbis() async {
    final override = getSupportedAbisOverride;
    if (override != null) return override();
    if (!supported) return const [];
    try {
      final list = await _channel.invokeListMethod<String>('getSupportedAbis');
      return list ?? const [];
    } catch (_) {
      return const [];
    }
  }

  /// Whether the app is allowed to request package installs.
  static Future<bool> canRequestPackageInstalls() async {
    final override = canRequestPackageInstallsOverride;
    if (override != null) return override();
    if (!supported) return false;
    try {
      final can = await _channel.invokeMethod<bool>('canRequestPackageInstalls');
      return can ?? true;
    } catch (_) {
      return true;
    }
  }

  /// Opens the settings screen to allow installing unknown apps.
  static Future<bool> openInstallPermissionSettings() async {
    final override = openInstallPermissionSettingsOverride;
    if (override != null) return override();
    if (!supported) return false;
    try {
      final res = await _channel.invokeMethod<bool>(
        'openInstallPermissionSettings',
      );
      return res ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Hands the downloaded APK at [filePath] to the system package installer.
  static Future<bool> installApk(String filePath) async {
    final override = installApkOverride;
    if (override != null) return override(filePath);
    if (!supported) return false;
    try {
      final res = await _channel.invokeMethod<bool>('installApk', {
        'filePath': filePath,
      });
      return res ?? false;
    } catch (e) {
      debugPrint('AppInstaller.installApk failed: $e');
      return false;
    }
  }
}
