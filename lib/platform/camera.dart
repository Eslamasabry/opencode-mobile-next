import 'package:flutter/services.dart';

import 'platform_capabilities.dart';

/// The camera permission, and whether there is a camera at all.
///
/// Deliberately the same shape as `lib/voice/device.dart`'s microphone seam,
/// and for the same reason: `mobile_scanner` reports only "permission
/// denied", but Android distinguishes a denial the user can undo from one
/// that will never prompt again, and those need different recoveries — retry
/// versus a trip to app settings. Only the native side can tell them apart
/// (`shouldShowRequestPermissionRationale`), so the three-state answer is
/// computed there and this is the typed seam over it.
enum CameraPermission { granted, denied, permanentlyDenied }

abstract interface class CameraPlatform {
  /// True when the device actually has a camera. A tablet or emulator
  /// without one must be told so, not walked into a preview that never
  /// renders.
  Future<bool> hasCamera();

  Future<CameraPermission> requestCameraPermission();

  /// Opens this app's system settings page, the only route back from a
  /// permanently denied permission.
  Future<void> openAppSettings();
}

class AndroidCameraPlatform implements CameraPlatform {
  const AndroidCameraPlatform();

  static const _channel = MethodChannel('oc/camera');

  @override
  Future<bool> hasCamera() async {
    if (!platformCapabilities.supportsQrPairing) return false;
    try {
      return await _channel.invokeMethod<bool>('hasCamera') ?? false;
    } on MissingPluginException {
      // Android with no handler registered (unit tests, a detached engine).
      // Claiming a camera here would send the user into a preview that
      // cannot open, so answer no.
      return false;
    }
  }

  @override
  Future<CameraPermission> requestCameraPermission() async {
    // Nothing routes here off Android — the scan affordance is gated — so
    // this is the backstop, and it says no rather than claiming a grant the
    // platform never made.
    if (!platformCapabilities.supportsQrPairing) {
      return CameraPermission.permanentlyDenied;
    }
    try {
      final status = await _channel.invokeMethod<String>(
        'requestCameraPermission',
      );
      return switch (status) {
        'granted' => CameraPermission.granted,
        'permanentlyDenied' => CameraPermission.permanentlyDenied,
        _ => CameraPermission.denied,
      };
    } on MissingPluginException {
      return CameraPermission.denied;
    }
  }

  @override
  Future<void> openAppSettings() async {
    if (!platformCapabilities.supportsQrPairing) return;
    try {
      await _channel.invokeMethod<void>('openAppSettings');
    } on MissingPluginException {
      // The recovery action is Android-only and unavailable in unit tests.
    }
  }
}

/// The active camera platform. Production leaves this at
/// [AndroidCameraPlatform]; tests replace it and restore it in tearDown.
CameraPlatform cameraPlatform = const AndroidCameraPlatform();
