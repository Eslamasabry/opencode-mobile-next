import 'package:flutter/services.dart';

import 'platform_capabilities.dart';

/// `oc/app`: facts about this app itself and its Android settings page.
/// Android only; every call is a quiet no-answer elsewhere.
class AppInfoPlatform {
  const AppInfoPlatform._();

  static const _channel = MethodChannel('oc/app');

  /// The SHA-256 of the certificate this APK is signed with, upper-case hex;
  /// null off Android or when Android does not say.
  static Future<String?> signingCertificateSha256() async {
    if (!platformCapabilities.isAndroid) return null;
    try {
      return await _channel.invokeMethod<String>('getSigningCertificateSha256');
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  /// Opens this app's page in Android's settings; false when it could not.
  static Future<bool> openAppSettings() async {
    if (!platformCapabilities.isAndroid) return false;
    try {
      return await _channel.invokeMethod<bool>('openAppSettings') ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }
}
