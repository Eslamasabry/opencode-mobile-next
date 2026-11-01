import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter/services.dart';

/// How a hand-off to Android went.
enum LocalFileOpenResult {
  /// Android's chooser or installer is showing.
  opened,

  /// This build cannot hand files over (the native half ships in 2.2.0), or
  /// nothing on the phone can open the file.
  unavailable,
}

/// Hands a file on this phone's shared storage to Android: the app chooser
/// ("Open with…") or the package installer for an .apk. The installer
/// confirms with the person; nothing is installed silently.
///
/// Rides the `oc/share` channel (`openLocalFile`). Builds whose Kotlin half
/// predates it answer "not implemented", which reads as [unavailable].
class LocalFileOpener {
  LocalFileOpener({@visibleForTesting MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/share');

  final MethodChannel _channel;

  static bool? _supported;

  /// Forgets the cached answer (tests only).
  @visibleForTesting
  static void resetSupportCache() => _supported = null;

  /// Whether this build can hand a file to Android at all (the Kotlin half
  /// answers `supportsOpenLocalFile` true from 2.2.0). Asked once per app
  /// run; a build without it, or any failure, reads as false, so the app
  /// never shows an Install… or Open with… that cannot work.
  Future<bool> isSupported() async {
    final known = _supported;
    if (known != null) return known;
    bool answer;
    try {
      answer =
          await _channel.invokeMethod<bool>('supportsOpenLocalFile') == true;
    } on MissingPluginException {
      answer = false;
    } on PlatformException {
      answer = false;
    }
    return _supported = answer;
  }

  Future<LocalFileOpenResult> open(String path, {required bool install}) async {
    try {
      final ok = await _channel.invokeMethod<bool>('openLocalFile', {
        'path': path,
        'install': install,
      });
      return ok == true
          ? LocalFileOpenResult.opened
          : LocalFileOpenResult.unavailable;
    } on MissingPluginException {
      return LocalFileOpenResult.unavailable;
    } on PlatformException {
      return LocalFileOpenResult.unavailable;
    }
  }
}
